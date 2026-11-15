package com.example.bruteforcetest;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    // نام متغیر باید دقیقاً با XML یکی باشد: etAddresses
    private EditText etAddresses;
    private Button btnStart, btnStop;
    private TextView tvResult;
    private static final int PERMISSION_REQUEST_CODE = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // اینجا هم باید R.id.etAddresses باشد
        etAddresses = findViewById(R.id.etAddresses);
        btnStart = findViewById(R.id.btnStart);
        btnStop = findViewById(R.id.btnStop);
        tvResult = findViewById(R.id.tvResult);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) 
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, 
                    new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 
                    PERMISSION_REQUEST_CODE);
            }
        }

        if (getIntent().getBooleanExtra("FOUND", false)) {
            showFoundResult();
        }

        btnStart.setOnClickListener(v -> {
            String addresses = etAddresses.getText().toString().trim();
            if (addresses.isEmpty()) {
                return;
            }
            startBruteForceService(addresses);
        });

        btnStop.setOnClickListener(v -> stopBruteForceService());
    }

    private void showFoundResult() {
        SharedPreferences prefs = getSharedPreferences("BruteForceResult", MODE_PRIVATE);
        String seedPhrase = prefs.getString("seed_phrase", "");
        String address = prefs.getString("address", "");

        if (!seedPhrase.isEmpty()) {
            tvResult.setVisibility(View.VISIBLE);
            tvResult.setText("✅ آدرس پیدا شد!\n\n" +
                           "عبارت بازیابی:\n" + seedPhrase + "\n\n" +
                           "آدرس: " + address);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent.getBooleanExtra("FOUND", false)) {
            showFoundResult();
        }
    }

    private void startBruteForceService(String addresses) {
        Intent serviceIntent = new Intent(this, BruteForceService.class);
        serviceIntent.putExtra("TARGET_ADDRESSES", addresses);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        
        btnStart.setEnabled(false);
        btnStop.setEnabled(true);
    }

    private void stopBruteForceService() {
        Intent serviceIntent = new Intent(this, BruteForceService.class);
        stopService(serviceIntent);
        
        btnStart.setEnabled(true);
        btnStop.setEnabled(false);
    }
}
