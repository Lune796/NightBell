package com.lune.nightbell;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import org.json.JSONObject;

public class BellService extends Service {

    public static volatile boolean running = false;
    private static final String CH = "nightbell";
    private Thread worker;
    private volatile boolean alive = false;
    private String lastKey = "";
    private long lastTs = 0;

    @Override
    public IBinder onBind(Intent i) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (running) return START_STICKY;
        Shared.ensureDir();
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel c = new NotificationChannel(CH, "夜铃待命", NotificationManager.IMPORTANCE_MIN);
                c.setShowBadge(false);
                nm.createNotificationChannel(c);
            }
            Notification n = new Notification.Builder(this, CH)
                    .setContentTitle("夜铃在等")
                    .setContentText("来电会直接弹到屏幕上")
                    .setSmallIcon(android.R.drawable.ic_menu_call)
                    .setOngoing(true)
                    .build();
            startForeground(101, n);
        } catch (Throwable t) {
            Shared.log("foreground fail " + t);
        }

        running = true;
        alive = true;
        Shared.log("service start");
        worker = new Thread(new Runnable() {
            public void run() {
                loop();
            }
        });
        worker.setDaemon(true);
        worker.start();
        return START_STICKY;
    }

    private void loop() {
        while (alive) {
            try {
                Thread.sleep(1200);
            } catch (Throwable t) {
                break;
            }
            if (!alive) break;
            try {
                poll();
            } catch (Throwable t) {
                Shared.log("poll err " + t);
            }
        }
        Shared.log("loop exit");
    }

    private void poll() {
        String raw = Shared.read(Shared.INCOMING);
        if (raw == null || raw.length() == 0) return;
        JSONObject o;
        try {
            o = new JSONObject(raw);
        } catch (Throwable t) {
            return;
        }
        long ts = o.optLong("ts", 0);
        if (ts <= 0 || ts <= lastTs) return;
        lastTs = ts;
        String id = o.optString("id", String.valueOf(ts));
        String name = o.optString("name", "");
        String line = o.optString("line", "");
        if (name.length() == 0) name = Shared.cfg(this, "name", "");
        if (name.length() == 0) name = "未知来电";
        long now = System.currentTimeMillis();
        if (now - ts > 300000L) {
            Shared.log("skip stale ts=" + ts);
            return;
        }
        String key = id + "|" + ts;
        if (key.equals(lastKey)) return;
        lastKey = key;
        if (CallActivity.alive) {
            Shared.log("busy drop " + key);
            return;
        }
        Shared.log("ring " + name + " | " + line);
        Intent i = new Intent(this, CallActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        i.putExtra("id", id);
        i.putExtra("name", name);
        i.putExtra("line", line);
        try {
            startActivity(i);
        } catch (Throwable t) {
            Shared.log("start fail " + t);
        }
    }

    @Override
    public void onDestroy() {
        alive = false;
        running = false;
        Shared.log("service destroy");
        try {
            if (worker != null) worker.interrupt();
        } catch (Throwable t) {
        }
        super.onDestroy();
    }
}