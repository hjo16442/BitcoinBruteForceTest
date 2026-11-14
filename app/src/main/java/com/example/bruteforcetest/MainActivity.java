package com.example.bruteforcetest;

import android.os.Bundle;
import android.os.SystemClock;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.LegacyAddress;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.crypto.MnemonicCode;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.wallet.DeterministicKeyChain;
import org.bitcoinj.wallet.DeterministicSeed;
import org.bitcoinj.wallet.KeyChain;

import java.security.SecureRandom;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends AppCompatActivity {

    private EditText etAddress;
    private Button btnStart, btnStop;
    private TextView tvStatus, tvStats;
    private Thread[] workerThreads;
    private AtomicBoolean running = new AtomicBoolean(false);
    private AtomicLong totalAttempts = new AtomicLong(0);
    private String targetAddress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etAddress = findViewById(R.id.etAddress);
        btnStart = findViewById(R.id.btnStart);
        btnStop = findViewById(R.id.btnStop);
        tvStatus = findViewById(R.id.tvStatus);
        tvStats = findViewById(R.id.tvStats);

        findViewById(R.id.tvLog).setVisibility(android.view.View.GONE);

        btnStart.setOnClickListener(v -> {
            targetAddress = etAddress.getText().toString().trim();
            if (targetAddress.isEmpty()) {
                tvStatus.setText("لطفاً آدرس را وارد کنید");
                return;
            }
            startMultiThreadedBruteForce();
        });

        btnStop.setOnClickListener(v -> stopBruteForce());
    }

    private void startMultiThreadedBruteForce() {
        if (running.get()) return;
        
        running.set(true);
        totalAttempts.set(0);
        tvStatus.setText("در حال اجرا با حداکثر سرعت (Multi-Thread)...");
        btnStart.setEnabled(false);
        btnStop.setEnabled(true);

        // استفاده از تمام هسته‌های CPU
        int numCores = Runtime.getRuntime().availableProcessors();
        workerThreads = new Thread[numCores];

        long startTime = SystemClock.elapsedRealtime();

        for (int i = 0; i < numCores; i++) {
            workerThreads[i] = new Thread(new WorkerRunnable(i));
            workerThreads[i].setPriority(Thread.MAX_PRIORITY);
            workerThreads[i].start();
        }

        // Thread برای به‌روزرسانی UI
        new Thread(() -> {
            while (running.get()) {
                try {
                    Thread.sleep(1000);
                    long attempts = totalAttempts.get();
                    long elapsed = (SystemClock.elapsedRealtime() - startTime) / 1000;
                    if (elapsed > 0) {
                        double speed = (double) attempts / elapsed;
                        runOnUiThread(() -> tvStatus.setText(String.format(
                            "تست: %,d | سرعت: %.0f/ثانیه | هسته‌ها: %d", 
                            attempts, speed, numCores
                        )));
                    }
                } catch (InterruptedException e) {
                    break;
                }
            }
        }).start();
    }

    private void stopBruteForce() {
        running.set(false);
        if (workerThreads != null) {
            for (Thread t : workerThreads) {
                if (t != null) t.interrupt();
            }
        }
        btnStart.setEnabled(true);
        btnStop.setEnabled(false);
        tvStatus.setText("متوقف شد");
    }

    private class WorkerRunnable implements Runnable {
        private final int threadId;
        private final SecureRandom random = new SecureRandom();
        private final NetworkParameters params = MainNetParams.get();

        WorkerRunnable(int id) {
            this.threadId = id;
        }

        @Override
        public void run() {
            try {
                while (running.get() && !Thread.currentThread().isInterrupted()) {
                    // تولید entropy
                    byte[] entropy = new byte[16];
                    random.nextBytes(entropy);

                    // تبدیل به کلمات
                    List<String> words = MnemonicCode.INSTANCE.toMnemonic(entropy);
                    
                    // ساخت Seed و زنجیره کلید
                    DeterministicSeed detSeed = new DeterministicSeed(entropy, words, 0L);
                    DeterministicKeyChain chain = DeterministicKeyChain.builder().seed(detSeed).build();
                    
                    // دریافت آدرس
                    ECKey key = chain.getKey(KeyChain.KeyPurpose.RECEIVE_FUNDS);
                    String generatedAddress = LegacyAddress.fromKey(params, key).toString();

                    // بررسی تطابق
                    if (generatedAddress.equals(targetAddress)) {
                        running.set(false);
                        runOnUiThread(() -> {
                            tvStats.setText("✅ پیدا شد!\nعبارت: " + String.join(" ", words) + "\nآدرس: " + generatedAddress);
                            tvStatus.setText("پایان - پیدا شد!");
                            btnStart.setEnabled(true);
                            btnStop.setEnabled(false);
                        });
                        return;
                    }

                    // افزایش شمارنده
                    totalAttempts.incrementAndGet();
                }
            } catch (Exception e) {
                // خطا را نادیده بگیر و ادامه بده
            }
        }
    }

    @Override
    protected void onDestroy() {
        stopBruteForce();
        super.onDestroy();
    }
}
