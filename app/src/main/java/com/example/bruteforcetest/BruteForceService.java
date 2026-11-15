package com.example.bruteforcetest;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class BruteForceService extends Service {

    private static final String CHANNEL_ID = "BruteForceChannel";
    private static final String FOUND_CHANNEL_ID = "FoundChannel";
    private static final int NOTIFICATION_ID = 1;
    private static final int FOUND_NOTIFICATION_ID = 999;

    private PowerManager.WakeLock wakeLock;
    private Thread[] workerThreads;
    private AtomicBoolean running = new AtomicBoolean(false);
    private AtomicLong totalAttempts = new AtomicLong(0);
    private Set<String> targetAddresses = new HashSet<>();
    private long startTime;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String addressesString = intent.getStringExtra("TARGET_ADDRESSES");
            if (addressesString != null && !addressesString.isEmpty()) {
                String[] addresses = addressesString.split("\n");
                for (String addr : addresses) {
                    String trimmed = addr.trim();
                    if (!trimmed.isEmpty()) {
                        targetAddresses.add(trimmed);
                    }
                }
            }
        }

        if (targetAddresses.isEmpty()) {
            stopSelf();
            return START_NOT_STICKY;
        }

        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "BruteForce::WakeLock"
        );
        wakeLock.acquire();

        Notification notification = buildNotification();
        startForeground(NOTIFICATION_ID, notification);

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
                            String.format("تست: %,d | سرعت: %.0f/ثانیه | آدرس‌ها: %d", 
                                attempts, speed, targetAddresses.size())
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
            
            NotificationChannel foundChannel = new NotificationChannel(
                FOUND_CHANNEL_ID,
                "پیدا شد!",
                NotificationManager.IMPORTANCE_HIGH
            );
            foundChannel.setDescription("وقتی آدرس پیدا شد");
            foundChannel.enableVibration(true);
            foundChannel.setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI, null);
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
            manager.createNotificationChannel(foundChannel);
        }
    }

    private void showFoundNotification(String seedPhrase, String address) {
        SharedPreferences prefs = getSharedPreferences("BruteForceResult", MODE_PRIVATE);
        prefs.edit()
            .putString("seed_phrase", seedPhrase)
            .putString("address", address)
            .apply();

        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("FOUND", true);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, intent, 
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        Notification notification = new NotificationCompat.Builder(this, FOUND_CHANNEL_ID)
            .setContentTitle("✅ آدرس پیدا شد!")
            .setContentText("کلیک کنید تا عبارت بازیابی را ببینید")
            .setStyle(new NotificationCompat.BigTextStyle()
                .bigText("عبارت بازیابی:\n" + seedPhrase + "\n\nآدرس: " + address))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVibrate(new long[]{0, 500, 200, 500})
            .build();

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify(FOUND_NOTIFICATION_ID, notification);
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

                    // چک کردن همه آدرس‌ها همزمان
                    if (targetAddresses.contains(generatedAddress)) {
                        running.set(false);
                        String seedPhrase = String.join(" ", words);
                        showFoundNotification(seedPhrase, generatedAddress);
                        stopSelf();
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
