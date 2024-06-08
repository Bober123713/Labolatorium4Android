package com.example.labolatorium4;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 1;
    private EditText urlEditText;
    private TextView fileInfoTextView;
    private TextView progressTextView;
    private ProgressBar progressBar;
    private Button downloadButton;
    private Button downloadFileButton;
    private PostepInfo postepInfo;
    private BroadcastReceiver progressReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        urlEditText = findViewById(R.id.urlEditText);
        fileInfoTextView = findViewById(R.id.fileInfoTextView);
        progressTextView = findViewById(R.id.progressTextView);
        progressBar = findViewById(R.id.progressBar);
        downloadButton = findViewById(R.id.downloadButton);
        downloadFileButton = findViewById(R.id.downloadFileButton);

        //Ustawia nasłuchiwacze kliknięć dla przycisków,
        //które uruchamiają pobieranie pliku lub sprawdzają jego informacje.

        downloadButton.setOnClickListener(view -> {
            String urlString = urlEditText.getText().toString();
            new DownloadFileInfoTask().execute(urlString);
        });

        downloadFileButton.setOnClickListener(view -> {
            if (checkPermission()) {
                String urlString = urlEditText.getText().toString();
                Intent intent = new Intent(MainActivity.this, DownloadService.class);
                intent.putExtra("url", urlString);
                startService(intent);
            } else {
                requestPermission();
            }
        });

        //Tworzy kanał powiadomień dla powiadomień o pobieraniu
        //i rejestruje odbiornik rozgłoszeniowy,
        //aby odbierać aktualizacje postępu.

        createNotificationChannel();

        progressReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                postepInfo = intent.getParcelableExtra("progress_info");
                updateUI();
            }
        };

        registerReceiver(progressReceiver, new IntentFilter("com.example.labolatorium4.PROGRESS_UPDATE"));

        //Przywraca stan interfejsu użytkownika, jeśli istnieje zapisany stan instancji.

        if (savedInstanceState != null) {
            postepInfo = savedInstanceState.getParcelable("progress_info");
            updateUI();
        }
    }


    //Wyrejestrowuje odbiornik rozgłoszeniowy, gdy aktywność jest niszczona.
    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(progressReceiver);
    }


    //Zapisuje bieżące informacje o postępie do stanu instancji.
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable("progress_info", postepInfo);
    }


    //Przywraca zapisane informacje o postępie i aktualizuje interfejs użytkownika.
    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        if (savedInstanceState != null) {
            postepInfo = savedInstanceState.getParcelable("progress_info");
            updateUI();
        }
    }


    //Aktualizuje elementy interfejsu użytkownika za pomocą bieżących informacji o postępie.
    private void updateUI() {
        if (postepInfo != null) {
            fileInfoTextView.setText("Rozmiar pliku: " + postepInfo.mRozmiar + "\nTyp pliku: " + postepInfo.mStatus);
            progressTextView.setText("Pobrano bajtów: " + postepInfo.mPobranychBajtow);
            progressBar.setMax(postepInfo.mRozmiar);
            progressBar.setProgress(postepInfo.mPobranychBajtow);
        }
    }


    //AsyncTask, który pobiera informacje o pliku i aktualizuje interfejs użytkownika.
    private class DownloadFileInfoTask extends AsyncTask<String, Void, String> {

        @Override
        protected String doInBackground(String... urls) {
            String urlString = urls[0];
            try {
                URL url = new URL(urlString);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("HEAD");
                connection.connect();

                int fileSize = connection.getContentLength();
                String fileType = connection.getContentType();

                postepInfo = new PostepInfo(0, fileSize, fileType);

                return "Rozmiar pliku: " + fileSize + " bytes\nTyp pliku: " + fileType;
            } catch (IOException e) {
                return "Error: " + e.getMessage();
            }
        }

        @Override
        protected void onPostExecute(String result) {
            fileInfoTextView.setText(result);
            updateUI();
        }
    }

    private boolean checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        } else {
            int result = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
            return result == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                startActivityForResult(intent, PERMISSION_REQUEST_CODE);
            } catch (Exception e) {
                Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.addCategory("android.intent.category.DEFAULT");
                intent.setData(Uri.parse(String.format("package:%s", getApplicationContext().getPackageName())));
                startActivityForResult(intent, PERMISSION_REQUEST_CODE);
            }
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permission granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "DownloadChannel",
                    "Download Notifications",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }


    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(progressReceiver, new IntentFilter("com.example.labolatorium4.PROGRESS_UPDATE"));
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(progressReceiver);
    }

}
