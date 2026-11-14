package com.example.bruteforcetest;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private EditText etAddress;
    private Button btnStart, btnStop;
    private static final int PERMISSION_REQUEST_CODE = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etAddress = findViewById(R.id.etAddress);
        btnStart = findViewById(R.id.btnStart);
        btnStop = findViewById(R.id.btnStop);

        // درخواست مجوز Notification (برای اندروید 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) 
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, 
                    new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 
                    PERMISSION_REQUEST_CODE);
            }
        }

        btnStart.setOnClickListener(v -> {
            String target = etAddress.getText().toString().trim();
            if (target.isEmpty()) {
                return;
            }
            startBruteForceService(target);
        });

        btnStop.setOnClickListener(v -> stopBruteForceService());
    }

    private void startBruteForceService(String targetAddress) {
        Intent serviceIntent = new Intent(this, BruteForceService.class);
        serviceIntent.putExtra("TARGET_ADDRESS", targetAddress);
        
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
