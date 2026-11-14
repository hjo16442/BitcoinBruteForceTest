package com.example.bruteforcetest;

import android.os.AsyncTask;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.LegacyAddress;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.crypto.DeterministicKeyChain;
import org.bitcoinj.crypto.DeterministicSeed;
import org.bitcoinj.crypto.MnemonicCode;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.wallet.KeyPurpose;

import java.security.SecureRandom;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private EditText etAddress;
    private Button btnStart, btnStop;
    private TextView tvStatus, tvStats, tvLog;
    private BruteForceTask task;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etAddress = findViewById(R.id.etAddress);
        btnStart = findViewById(R.id.btnStart);
        btnStop = findViewById(R.id.btnStop);
        tvStatus = findViewById(R.id.tvStatus);
        tvStats = findViewById(R.id.tvStats);
        tvLog = findViewById(R.id.tvLog);

        btnStart.setOnClickListener(v -> {
            String target = etAddress.getText().toString().trim();
            if (target.isEmpty()) {
                tvStatus.setText("لطفاً آدرس را وارد کنید");
                return;
            }
            startBruteForce(target);
        });

        btnStop.setOnClickListener(v -> {
            if (task != null && task.getStatus() == AsyncTask.Status.RUNNING) {
                task.cancel(true);
            }
        });
    }

    private void startBruteForce(String targetAddress) {
        if (task != null && task.getStatus() == AsyncTask.Status.RUNNING) {
            task.cancel(true);
        }
        tvLog.setText("");
        tvStats.setText("");
        task = new BruteForceTask(targetAddress);
        task.execute();
    }

    private class BruteForceTask extends AsyncTask<Void, String, String> {

        private String targetAddress;
        private SecureRandom random = new SecureRandom();
        private NetworkParameters params = MainNetParams.get();
        private boolean running = true;

        BruteForceTask(String target) {
            this.targetAddress = target;
        }

        @Override
        protected void onPreExecute() {
            tvStatus.setText("در حال اجرا...");
            btnStart.setEnabled(false);
            btnStop.setEnabled(true);
        }

        @Override
        protected String doInBackground(Void... voids) {
            try {
                int attempts = 0;
                long startTime = System.currentTimeMillis();
                StringBuilder logBuilder = new StringBuilder();

                while (running && !isCancelled()) {
                    attempts++;

                    // 1. تولید 128 بیت entropy تصادفی (معادل 12 کلمه)
                    byte[] entropy = new byte[16];
                    random.nextBytes(entropy);

                    // 2. تبدیل به عبارت بازیابی (12 کلمه)
                    List<String> words = MnemonicCode.INSTANCE.toMnemonic(entropy);
                    
                    // 3. تبدیل کلمات به Seed (64 بایت)
                    byte[] seedBytes = MnemonicCode.toSeed(words, "");

                    // 4. ساخت زنجیره کلید استاندارد (BIP32/BIP44) - روش صحیح کیف پول‌ها
                    DeterministicSeed detSeed = new DeterministicSeed(seedBytes, "", "", 0);
                    DeterministicKeyChain chain = DeterministicKeyChain.builder().seed(detSeed).build();
                    
                    // 5. دریافت اولین کلید دریافت (آدرس اول کیف پول)
                    ECKey key = chain.getKey(KeyPurpose.RECEIVE_FUNDS);
                    
                    // 6. تولید آدرس بیت‌کوین از کلید
                    String generatedAddress = LegacyAddress.fromKey(params, key).toString();

                    // 7. بررسی تطابق
                    if (generatedAddress.equals(targetAddress)) {
                        String seedPhrase = String.join(" ", words);
                        return "✅ پیدا شد!\n\nعبارت بازیابی:\n" + seedPhrase + 
                               "\n\nآدرس: " + generatedAddress;
                    }

                    // نمایش پیشرفت هر 100 تست
                    if (attempts % 100 == 0) {
                        long elapsed = System.currentTimeMillis() - startTime;
                        double speed = attempts / (elapsed / 1000.0);
                        
                        String progress = String.format("تست: %,d | سرعت: %.1f/ثانیه", attempts, speed);
                        publishProgress(progress);

                        // نمایش آدرس‌های تست شده
                        if (attempts % 500 == 0) {
                            logBuilder.insert(0, String.format("#%,d: %s\n", attempts, generatedAddress));
                            if (logBuilder.length() > 3000) {
                                logBuilder.setLength(3000);
                            }
                            publishProgress("LOG:" + logBuilder.toString());
                        }
                    }
                }

                long elapsed = System.currentTimeMillis() - startTime;
                double speed = attempts / (elapsed / 1000.0);
                double yearsNeeded = 5.4e39 / speed / 31536000;
                
                return String.format(
                    "❌ متوقف شد\n\n" +
                    "تعداد تست: %,d\n" +
                    "زمان: %.2f ثانیه\n" +
                    "سرعت: %.1f عبارت/ثانیه\n\n" +
                    "تعداد کل حالت‌های ممکن: 2^132 ≈ 5.4 × 10^39\n" +
                    "زمان لازم برای بررسی همه: %.2e سال\n" +
                    "(بیشتر از عمر کیهان!)",
                    attempts, elapsed / 1000.0, speed, yearsNeeded
                );

            } catch (Exception e) {
                return "خطا: " + e.getMessage();
            }
        }

        @Override
        protected void onProgressUpdate(String... values) {
            if (values[0].startsWith("LOG:")) {
                tvLog.setText(values[0].substring(4));
            } else {
                tvStatus.setText(values[0]);
            }
        }

        @Override
        protected void onPostExecute(String result) {
            tvStatus.setText("پایان");
            tvStats.setText(result);
            btnStart.setEnabled(true);
            btnStop.setEnabled(false);
        }

        @Override
        protected void onCancelled() {
            running = false;
            tvStatus.setText("متوقف شد");
            btnStart.setEnabled(true);
            btnStop.setEnabled(false);
        }
    }

    @Override
    protected void onDestroy() {
        if (task != null) task.cancel(true);
        super.onDestroy();
    }
}
