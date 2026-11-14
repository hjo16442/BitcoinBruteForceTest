package com.example.bruteforcetest;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import java.security.SecureRandom;
import java.util.Locale;

public class MainActivity extends Activity {
    private TextView statusText;
    private TextView resultsText;
    private int testCount = 0;
    private final SecureRandom random = new SecureRandom();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusText = findViewById(R.id.statusText);
        resultsText = findViewById(R.id.resultsText);
        Button startButton = findViewById(R.id.startButton);
        Button clearButton = findViewById(R.id.clearButton);

        startButton.setOnClickListener(v -> runLocalSimulation());
        clearButton.setOnClickListener(v -> {
            testCount = 0;
            statusText.setText(R.string.status_ready);
            resultsText.setText("");
        });
    }

    private void runLocalSimulation() {
        // Educational local simulation: synthetic labels only.
        // It does not create Bitcoin private keys or real wallet addresses.
        final String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sample = new StringBuilder(24);
        for (int i = 0; i < 24; i++) {
            sample.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }

        testCount++;
        statusText.setText(getString(R.string.status_completed, testCount));
        String entry = String.format(Locale.US,
                "آزمایش #%d\nشناسه ساختگی: %s\nنتیجه: فقط داده آزمایشی؛ هیچ آدرس یا موجودی واقعی بررسی نشده است.\n\n",
                testCount, sample.toString());
        resultsText.append(entry);
    }
}
