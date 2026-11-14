package com.lune.nightbell;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.util.Base64;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ImageView;

import org.json.JSONObject;

public class CallActivity extends Activity {

    public static volatile boolean alive = false;

    private String callId = "";
    private String peer = "";
    private String firstLine = "";
    private String uiNum = "";
    private String uiLoc = "";
    private String uiAvatar = "";
    private String uiBg = "";
    private String uiRing = "";
    private String uiMode = "light";
    private String uiChat = "";

    private Handler h = new Handler();
    private MediaPlayer ring;
    private Vibrator vib;
    private PowerManager.WakeLock wl;

    private TextView tvState;
    private TextView tvTimer;
    private TextView tvLine;
    private LinearLayout btns;
    private FrameLayout root;

    private boolean accepted = false;
    private boolean closed = false;
    private long startAt = 0;
    private long lastReplyTs = 0;

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        alive = true;
        callId = getIntent().getStringExtra("id");
        if (callId == null) callId = "";
        peer = getIntent().getStringExtra("name");
        if (peer == null || peer.length() == 0) peer = "未知来电";
        firstLine = getIntent().getStringExtra("line");
        if (firstLine == null) firstLine = "";
        loadAppCfg();

        try {
            if (Build.VERSION.SDK_INT >= 27) {
                setShowWhenLocked(true);
                setTurnScreenOn(true);
            }
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                    | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            if (Build.VERSION.SDK_INT >= 21) {
                boolean dk = !"light".equalsIgnoreCase(uiMode);
                getWindow().setStatusBarColor(dk ? 0xFF0C0C0E : 0xFFF6F6F8);
                getWindow().setNavigationBarColor(dk ? 0xFF0C0C0E : 0xFFF6F6F8);
                if (!dk && Build.VERSION.SDK_INT >= 23) {
                    getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
                }
            }
        } catch (Throwable t) {
        }

        build();
        Shared.log("call page open " + peer + " id=" + callId);
        startRing();
        h.postDelayed(timeout, 45000);
    }

    private void loadAppCfg() {
        try {
            String b = Shared.read(Shared.DIR + "/data/app_cfg.txt");
            if (b == null || b.length() == 0) {
                Shared.log("cfg empty");
                return;
            }
            String js = new String(Base64.decode(b.replaceAll("[^A-Za-z0-9+/=]", ""), Base64.DEFAULT), "UTF-8");
            JSONObject o = new JSONObject(js);
            String n = o.optString("name", "");
            if (n.length() > 0) peer = n;
            uiNum = o.optString("number", "");
            uiLoc = o.optString("location", "");
            uiAvatar = o.optString("avatar", "");
            uiBg = o.optString("customBg", "");
            uiMode = o.optString("mode", "light");
            uiChat = o.optString("chatId", "");
            uiRing = o.optString("ring", "");
            Shared.log("cfg " + peer + " num=" + uiNum + " loc=" + uiLoc + " mode=" + uiMode + " av=" + uiAvatar.length() + " bg=" + uiBg.length());
        } catch (Throwable t) {
            Shared.log("cfg fail " + t);
        }
    }

    private void build() {
        boolean dark = !"light".equalsIgnoreCase(uiMode);
        int cBg = dark ? 0xFF0C0C0E : 0xFFF6F6F8;
        int cName = dark ? 0xFFFFFFFF : 0xFF111114;
        int cSub = dark ? 0xB3FFFFFF : 0xFF737378;
        int cRing = dark ? 0x14FFFFFF : 0x0F000000;
        int cStroke = dark ? 0x59FFFFFF : 0x33000000;
        int cMark = dark ? 0xFFFFFFFF : 0xFF111114;
        int cLabel = dark ? 0xCCFFFFFF : 0xFF66666E;
        int cBar = dark ? 0xE6FFFFFF : 0x59000000;
        int cMask = dark ? 0xB8121214 : 0xC8F6F6F8;

        root = new FrameLayout(this);
        root.setBackgroundColor(cBg);
        try {
            Bitmap bgm = loadBitmap(uiBg, 220);
            if (bgm != null) {
                BitmapDrawable bd = new BitmapDrawable(getResources(), bgm);
                bd.setGravity(Gravity.FILL);
                root.setBackground(bd);
                View mask = new View(this);
                mask.setBackgroundColor(cMask);
                root.addView(mask, new FrameLayout.LayoutParams(-1, -1));
            }
        } catch (Throwable t) {
            Shared.log("bg fail " + t);
        }

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(dp(26), dp(34), dp(26), dp(18));
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));

        FrameLayout topRow = new FrameLayout(this);
        col.addView(topRow, new LinearLayout.LayoutParams(-1, dp(32)));
        FrameLayout info = new FrameLayout(this);
        GradientDrawable ig = new GradientDrawable();
        ig.setShape(GradientDrawable.OVAL);
        ig.setColor(cRing);
        ig.setStroke(dp(1), cStroke);
        info.setBackground(ig);
        TextView ii = new TextView(this);
        ii.setText("i");
        ii.setTextColor(cMark);
        ii.setTextSize(14);
        ii.setTypeface(null, Typeface.BOLD);
        ii.setGravity(Gravity.CENTER);
        info.addView(ii, new FrameLayout.LayoutParams(-1, -1));
        topRow.addView(info, new FrameLayout.LayoutParams(dp(28), dp(28), Gravity.RIGHT | Gravity.CENTER_VERTICAL));

        col.addView(new View(this), new LinearLayout.LayoutParams(-1, 0, 0.40f));

        FrameLayout av = new FrameLayout(this);
        Bitmap ab = loadBitmap(uiAvatar, 320);
        if (ab != null) {
            ImageView ivp = new ImageView(this);
            ivp.setImageBitmap(circle(ab));
            av.addView(ivp, new FrameLayout.LayoutParams(-1, -1));
        } else {
            GradientDrawable ag = new GradientDrawable();
            ag.setShape(GradientDrawable.OVAL);
            ag.setColor(dark ? 0x2EFFFFFF : 0xFFE3E3E8);
            av.setBackground(ag);
            TextView ch = new TextView(this);
            ch.setText(peer.length() > 0 ? peer.substring(0, 1) : "?");
            ch.setTextColor(dark ? 0xFFFFFFFF : 0xFF8A8A92);
            ch.setTextSize(42);
            ch.setGravity(Gravity.CENTER);
            av.addView(ch, new FrameLayout.LayoutParams(-1, -1));
        }
        col.addView(av, new LinearLayout.LayoutParams(dp(104), dp(104)));

        TextView name = new TextView(this);
        name.setText(peer);
        name.setTextColor(cName);
        name.setTextSize(30);
        name.setGravity(Gravity.CENTER);
        name.setSingleLine(true);
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(-1, -2);
        nl.topMargin = dp(16);
        col.addView(name, nl);

        String sub = ((uiLoc.length() > 0 ? uiLoc + "  " : "") + uiNum).trim();
        TextView subT = new TextView(this);
        subT.setText(sub);
        subT.setTextColor(cSub);
        subT.setTextSize(14);
        subT.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-1, -2);
        sl.topMargin = dp(6);
        col.addView(subT, sl);

        tvTimer = new TextView(this);
        tvTimer.setText("");
        tvTimer.setTextColor(cSub);
        tvTimer.setTextSize(14);
        tvTimer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1, -2);
        tlp.topMargin = dp(7);
        col.addView(tvTimer, tlp);

        tvLine = new TextView(this);
        tvLine.setText("");
        tvLine.setTextColor(cLabel);
        tvLine.setTextSize(15);
        tvLine.setGravity(Gravity.CENTER);
        tvLine.setLineSpacing(dp(5), 1.15f);
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(-1, -2);
        ll.topMargin = dp(12);
        col.addView(tvLine, ll);

        tvState = new TextView(this);
        tvState.setText("");
        tvState.setVisibility(View.GONE);
        col.addView(tvState);

        col.addView(new View(this), new LinearLayout.LayoutParams(-1, 0, 1f));

        btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        btns.setGravity(Gravity.CENTER);
        col.addView(btns, new LinearLayout.LayoutParams(-1, -2));

        col.addView(new View(this), new LinearLayout.LayoutParams(-1, dp(30)));
        View bar = new View(this);
        GradientDrawable bgd = new GradientDrawable();
        bgd.setCornerRadius(dp(3));
        bgd.setColor(cBar);
        bar.setBackground(bgd);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(130), dp(5));
        blp.gravity = Gravity.CENTER_HORIZONTAL;
        col.addView(bar, blp);

        setContentView(root);
        renderButtons();
    }

    private class Handset extends View {
        private float rot;

        Handset(Context c, float rot) {
            super(c);
            this.rot = rot;
        }

        protected void onDraw(Canvas c) {
            float w = getWidth();
            float h = getHeight();
            float s = Math.min(w, h);
            float cx = w / 2f;
            float cy = h / 2f;
            c.save();
            c.rotate(rot, cx, cy);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(0xFFFFFFFF);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(s * 0.15f);
            p.setStrokeCap(Paint.Cap.ROUND);
            Path path = new Path();
            path.moveTo(cx - s * 0.30f, cy + s * 0.24f);
            path.quadTo(cx - s * 0.06f, cy - s * 0.24f, cx + s * 0.16f, cy - s * 0.32f);
            path.quadTo(cx + s * 0.34f, cy - s * 0.39f, cx + s * 0.40f, cy - s * 0.20f);
            c.drawPath(path, p);
            p.setStrokeWidth(s * 0.20f);
            c.drawLine(cx - s * 0.38f, cy + s * 0.14f, cx - s * 0.20f, cy + s * 0.33f, p);
            c.drawLine(cx + s * 0.31f, cy - s * 0.31f, cx + s * 0.44f, cy - s * 0.10f, p);
            c.restore();
        }
    }

    private void renderButtons() {
        btns.removeAllViews();
        if (!accepted) {
            btns.addView(roundBtn(0xFFFF3B30, 45f, new View.OnClickListener() {
                public void onClick(View v) {
                    finishWith("reject");
                }
            }));
            btns.addView(new View(this), new LinearLayout.LayoutParams(dp(96), 1));
            btns.addView(roundBtn(0xFF34C759, 280f, new View.OnClickListener() {
                public void onClick(View v) {
                    accept();
                }
            }));
        } else {
            btns.addView(roundBtn(0xFFFF3B30, 45f, new View.OnClickListener() {
                public void onClick(View v) {
                    finishWith("hangup");
                }
            }));
        }
    }

    private View roundBtn(int color, float rotate, View.OnClickListener l) {
        FrameLayout f = new FrameLayout(this);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        f.setBackground(g);
        ImageView iv = new ImageView(this);
        try {
            iv.setImageResource(android.R.drawable.sym_action_call);
            iv.setColorFilter(0xFFFFFFFF);
        } catch (Throwable t) {
        }
        iv.setRotation(rotate);
        f.addView(iv, new FrameLayout.LayoutParams(dp(31), dp(31), Gravity.CENTER));
        f.setOnClickListener(l);
        f.setLayoutParams(new LinearLayout.LayoutParams(dp(72), dp(72)));
        return f;
    }

    private Bitmap loadBitmap(String src, int maxW) {
        if (src == null || src.length() == 0) return null;
        try {
            byte[] data = null;
            if (src.startsWith("data:")) {
                int i = src.indexOf("base64,");
                if (i < 0) return null;
                data = Base64.decode(src.substring(i + 7), Base64.DEFAULT);
            } else {
                String p2 = src.startsWith("file:") ? Uri.parse(src).getPath() : src;
                if (p2 != null && new File(p2).exists()) data = bytesOf(p2);
            }
            if (data == null) return null;
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, o);
            int sample = 1;
            while (o.outWidth > 0 && o.outWidth / sample > maxW * 2) sample *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = sample;
            return BitmapFactory.decodeByteArray(data, 0, data.length, o2);
        } catch (Throwable t) {
            return null;
        }
    }

    private byte[] bytesOf(String path) {
        try {
            FileInputStream in = new FileInputStream(path);
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            return bo.toByteArray();
        } catch (Throwable t) {
            return null;
        }
    }

    private Bitmap circle(Bitmap src) {
        int size = Math.min(src.getWidth(), src.getHeight());
        if (size <= 0) return src;
        int out = dp(220);
        Bitmap b = Bitmap.createBitmap(out, out, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        float sc = (float) out / size;
        c.scale(sc, sc);
        c.drawCircle(size / 2f, size / 2f, size / 2f, p);
        return b;
    }

    private static String stripTags(String s) {
        if (s == null) return "";
        return s.replaceAll("(?i)\\[(sighs?|pause[sd]?|soft(?:ly)?|laughs?|chuckles?|giggles?|whispers?|whispering|breath(?:e|es|ing)?|exhales?|inhales?|coughs?|sniffs?|gulps?|sarcastic|deadpan|sad|happy|angry|excited|curious|tired|nervous|surprised|shy|serious|calm|gentle|warm(?:ly)?|dramatic|flatly|quietly|loudly|singing)\\]", " ").replaceAll("\\s{2,}", " ").trim();
    }

    private void accept() {
        if (accepted || closed) return;
        accepted = true;
        h.removeCallbacks(timeout);
        stopRing();
        tvState.setText("通话中");
        startAt = System.currentTimeMillis();
        tvLine.setText(firstLine.length() == 0 ? "" : stripTags(firstLine));
        writeOut("accept");
        Shared.log("accept");
        renderButtons();
        h.postDelayed(tick, 500);
        h.postDelayed(replyPoll, 700);
        h.postDelayed(new Runnable() {
            public void run() {
                try {
                    Intent i = new Intent(CallActivity.this, MainActivity.class);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    i.putExtra("resume", "call");
                    i.putExtra("line", firstLine);
                    startActivity(i);
                } catch (Throwable t) {
                    Shared.log("to page fail " + t);
                }
                closed = true;
                finish();
            }
        }, 260);
    }

    private Runnable tick = new Runnable() {
        public void run() {
            if (closed || !accepted) return;
            long s = (System.currentTimeMillis() - startAt) / 1000L;
            String t = String.format("%02d:%02d", s / 60, s % 60);
            tvTimer.setText(t);
            h.postDelayed(this, 1000);
        }
    };

    private Runnable replyPoll = new Runnable() {
        public void run() {
            if (closed || !accepted) return;
            try {
                String raw = Shared.read(Shared.REPLY);
                if (raw != null && raw.length() > 0) {
                    JSONObject o = new JSONObject(raw);
                    long ts = o.optLong("ts", 0);
                    String id = o.optString("id", "");
                    if (ts > lastReplyTs && (id.length() == 0 || id.equals(callId))) {
                        lastReplyTs = ts;
                        String text = o.optString("text", "");
if (text.length() > 0) {
                                tvLine.setText(stripTags(text));
                            if (vib != null) vib.vibrate(40);
                        }
                    }
                }
            } catch (Throwable t) {
            }
            h.postDelayed(this, 1200);
        }
    };

    private Runnable timeout = new Runnable() {
        public void run() {
            if (!accepted && !closed) {
                Shared.log("call timeout");
                finishWith("timeout");
            }
        }
    };

    private void finishWith(String act) {
        if (closed) return;
        closed = true;
        h.removeCallbacksAndMessages(null);
        stopRing();
        writeOut(act);
        Shared.log("close " + act);
        finish();
    }

    private void writeOut(String act) {
        try {
            JSONObject o = new JSONObject();
            o.put("act", act);
            o.put("id", callId);
            o.put("name", peer);
            o.put("ts", System.currentTimeMillis());
            Shared.write(Shared.OUTGOING, o.toString());
        } catch (Throwable t) {
        }
        try {
            long now = System.currentTimeMillis();
            if ("reject".equals(act) || "timeout".equals(act)) {
                ChatBridge.send(this, String.valueOf(now), "［对方已拒绝］", uiChat);
                Shared.log("mark " + act);
            }
        } catch (Throwable t) {
        }
        try {
        } catch (Throwable t) {
        }
    }

    private void startRing() {
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            wl = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "nightbell:call");
            wl.acquire(60000);
        } catch (Throwable t) {
        }
        try {
            Uri u = null;
            if (uiRing != null && uiRing.length() > 0) {
                try { u = Uri.parse(uiRing); } catch (Throwable t2) { u = null; }
            }
            if (u == null) u = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            if (u == null) u = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            ring = new MediaPlayer();
            ring.setAudioStreamType(AudioManager.STREAM_RING);
            ring.setDataSource(this, u);
            ring.setLooping(true);
            ring.prepare();
            ring.start();
        } catch (Throwable t) {
            Shared.log("ring fail " + t);
        }
        try {
            vib = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            long[] p = new long[]{0, 700, 900};
            vib.vibrate(p, 0);
        } catch (Throwable t) {
        }
    }

    private void stopRing() {
        try {
            if (ring != null) {
                ring.stop();
                ring.release();
            }
        } catch (Throwable t) {
        }
        ring = null;
        try {
            if (vib != null) vib.cancel();
        } catch (Throwable t) {
        }
        vib = null;
        try {
            if (wl != null && wl.isHeld()) wl.release();
        } catch (Throwable t) {
        }
        wl = null;
    }

    @Override
    public void onBackPressed() {
        if (accepted) finishWith("hangup");
        else finishWith("reject");
    }

    @Override
    protected void onDestroy() {
        alive = false;
        closed = true;
        h.removeCallbacksAndMessages(null);
        stopRing();
        super.onDestroy();
    }
}