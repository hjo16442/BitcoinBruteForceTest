package com.example.bruteforcetest;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class BruteForceService extends Service {

    private static final String CHANNEL_ID = "BruteForceChannel";
    private static final int NOTIFICATION_ID = 1;

    private PowerManager.WakeLock wakeLock;
    private Thread[] workerThreads;
    private AtomicBoolean running = new AtomicBoolean(false);
    private AtomicLong totalAttempts = new AtomicLong(0);
    private String targetAddress;
    private long startTime;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            targetAddress = intent.getStringExtra("TARGET_ADDRESS");
        }

        if (targetAddress == null || targetAddress.isEmpty()) {
            stopSelf();
            return START_NOT_STICKY;
        }

        // گرفتن WakeLock برای جلوگیری از خاموش شدن CPU
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "BruteForce::WakeLock"
        );
        wakeLock.acquire();

        // ساخت Notification
        Notification notification = buildNotification();
        startForeground(NOTIFICATION_ID, notification);

        // شروع اجرای Multi-Thread
        startBruteForce();

        return START_STICKY;
    }

    private void startBruteForce() {
        running.set(true);
        totalAttempts.set(0);
        startTime = SystemClock.elapsedRealtime();

        int numCores = Runtime.getRuntime().availableProcessors();
        workerThreads = new Thread[numCores];

        for (int i = 0; i < numCores; i++) {
            workerThreads[i] = new Thread(new WorkerRunnable(i));
            workerThreads[i].setPriority(Thread.MAX_PRIORITY);
            workerThreads[i].start();
        }

        // Thread برای به‌روزرسانی Notification
        new Thread(() -> {
            NotificationManager notificationManager = 
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            
            while (running.get()) {
                try {
                    Thread.sleep(2000);
                    long attempts = totalAttempts.get();
                    long elapsed = (SystemClock.elapsedRealtime() - startTime) / 1000;
                    if (elapsed > 0) {
                        double speed = (double) attempts / elapsed;
                        Notification notification = buildNotification(
                            String.format("تست: %,d | سرعت: %.0f/ثانیه", attempts, speed)
                        );
                        notificationManager.notify(NOTIFICATION_ID, notification);
                    }
                } catch (InterruptedException e) {
                    break;
                }
            }
        }).start();
    }

    private Notification buildNotification() {
        return buildNotification("در حال اجرا...");
    }

    private Notification buildNotification(String text) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BruteForce در حال اجرا")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setOngoing(true)
            .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "BruteForce Service",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("نمایش وضعیت BruteForce");
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    @Override
    public void onDestroy() {
        running.set(false);
        if (workerThreads != null) {
            for (Thread t : workerThreads) {
                if (t != null) t.interrupt();
            }
        }
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private class WorkerRunnable implements Runnable {
        private final SecureRandom random = new SecureRandom();
        private final NetworkParameters params = MainNetParams.get();

        WorkerRunnable(int id) {}

        @Override
        public void run() {
            try {
                while (running.get() && !Thread.currentThread().isInterrupted()) {
                    byte[] entropy = new byte[16];
                    random.nextBytes(entropy);

                    List<String> words = MnemonicCode.INSTANCE.toMnemonic(entropy);
                    DeterministicSeed detSeed = new DeterministicSeed(entropy, words, 0L);
                    DeterministicKeyChain chain = DeterministicKeyChain.builder().seed(detSeed).build();
                    ECKey key = chain.getKey(KeyChain.KeyPurpose.RECEIVE_FUNDS);
                    String generatedAddress = LegacyAddress.fromKey(params, key).toString();

                    if (generatedAddress.equals(targetAddress)) {
                        running.set(false);
                        // اگر پیدا شد، می‌توانید اینجا Notification نشان دهید
                        return;
                    }

                    totalAttempts.incrementAndGet();
                }
            } catch (Exception e) {
                // نادیده گرفتن خطا
            }
        }
    }
}
