package com.lune.nightbell;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;

import org.json.JSONObject;

import java.io.File;

public class MainActivity extends Activity {

    private WebView wv;
    private ValueCallback<Uri[]> fileCb = null;
    private static final int REQ_FILE = 4321;
    private static final int REQ_RING = 4322;
    private volatile String ringPicked = null;
    private volatile long lastAskTs = 0;
    private volatile long lastPolledTs = 0;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 9001);
        }
        Stt.preload(this);
        Shared.ensureDir();
        try {
            getWindow().setStatusBarColor(0xFFF6F6F8);
            getWindow().setNavigationBarColor(0xFFF6F6F8);
        } catch (Throwable t) {
        }

        wv = new WebView(this);
        wv.setBackgroundColor(0xFFF6F6F8);
        WebSettings s = wv.getSettings();
        s.setJavaScriptEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowFileAccessFromFileURLs(true);
        s.setAllowUniversalAccessFromFileURLs(true);
        wv.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (fileCb != null) {
                    fileCb.onReceiveValue(null);
                }
                fileCb = cb;
                try {
                    Intent i = params.createIntent();
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    if (i.getType() == null) i.setType("*/*");
                    startActivityForResult(Intent.createChooser(i, "选择图片"), REQ_FILE);
                    return true;
                } catch (Throwable t) {
                    Shared.log("chooser fail " + t);
                    fileCb = null;
                    return false;
                }
            }
        });
        wv.addJavascriptInterface(new Bridge(), "NB");
        wv.addJavascriptInterface(new TtsBridge(), "NBT");
        wv.loadUrl("file:///android_asset/app.html");
        setContentView(wv);
    }

    /* ---- 通话缩小成小窗 ---- */
    private View miniView = null;
    private TextView miniTimeV = null;
    private android.os.Handler miniH = null;
    private long miniStartTs = 0;
    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }
    private void showMini(String name, long start) {
        try {
            if (miniView != null) return;
            if (!Settings.canDrawOverlays(this)) return;
            miniStartTs = start > 0 ? start : System.currentTimeMillis();
            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.HORIZONTAL);
            box.setGravity(Gravity.CENTER_VERTICAL);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0xF2202024);
            bg.setCornerRadius(dp(24));
            box.setBackground(bg);
            box.setPadding(dp(12), dp(8), dp(16), dp(8));
            ImageView iv = new ImageView(this);
            try { iv.setImageResource(getApplicationInfo().icon); } catch (Throwable t) { }
            box.addView(iv, new LinearLayout.LayoutParams(dp(30), dp(30)));
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setPadding(dp(9), 0, 0, 0);
            TextView nm = new TextView(this);
            nm.setText(name == null || name.length() == 0 ? "通话中" : name);
            nm.setTextColor(0xFFFFFFFF);
            nm.setTextSize(12);
            TextView tm = new TextView(this);
            tm.setTextColor(0xFF9EE7AE);
            tm.setTextSize(11);
            tm.setText("00:00");
            col.addView(nm);
            col.addView(tm);
            box.addView(col);
            miniTimeV = tm;
            WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.END;
            lp.x = dp(10);
            lp.y = dp(72);
            wm.addView(box, lp);
            miniView = box;
            box.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    hideMini();
                    try {
                        Intent i2 = new Intent(MainActivity.this, MainActivity.class);
                        i2.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                        startActivity(i2);
                    } catch (Throwable t) { }
                }
            });
            if (miniH == null) miniH = new android.os.Handler();
            miniH.post(tickMini);
        } catch (Throwable t) {
            Shared.log("mini fail " + t);
        }
    }
    private final Runnable tickMini = new Runnable() {
        public void run() {
            if (miniTimeV == null) return;
            long s2 = (System.currentTimeMillis() - miniStartTs) / 1000;
            String t2 = String.format("%02d:%02d", s2 / 60, s2 % 60);
            miniTimeV.setText(t2);
            if (miniH != null) miniH.postDelayed(this, 1000);
        }
    };
    private void hideMini() {
        try {
            if (miniH != null) miniH.removeCallbacksAndMessages(null);
            if (miniView != null) {
                WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
                wm.removeView(miniView);
            }
        } catch (Throwable t) { }
        miniView = null;
        miniTimeV = null;
    }
    private void js(final String code) {
        runOnUiThread(new Runnable() {
            public void run() {
                try {
                    if (wv != null) wv.evaluateJavascript(code, null);
                } catch (Throwable t) {
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideMini();
        autoArm();
        if (getIntent() != null && "call".equals(getIntent().getStringExtra("resume"))) {
            getIntent().removeExtra("resume");
            String _ln = getIntent().getStringExtra("line");
            getIntent().removeExtra("line");
            pendingLine = _ln == null ? "" : _ln;
            js("window.__nbResumeCall=1;try{answerIncoming()}catch(e){}");
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req == REQ_FILE) {
            Uri[] out = null;
            if (res == RESULT_OK && data != null) {
                try {
                    if (data.getClipData() != null) {
                        int n = data.getClipData().getItemCount();
                        out = new Uri[n];
                        for (int k = 0; k < n; k++) out[k] = data.getClipData().getItemAt(k).getUri();
                    } else if (data.getData() != null) {
                        out = new Uri[]{data.getData()};
                    }
                } catch (Throwable t) {
                    out = null;
                }
            }
            if (fileCb != null) {
                fileCb.onReceiveValue(out);
                fileCb = null;
            }
            return;
        }
        if (req == REQ_RING) {
            if (res == RESULT_OK && data != null) {
                try {
                    Uri u = data.getParcelableExtra(android.media.RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
                    ringPicked = u == null ? "" : u.toString();
                    Shared.log("ring picked " + ringPicked);
                } catch (Throwable t) {
                    ringPicked = "";
                }
            } else {
                ringPicked = "__cancel";
            }
            return;
        }
        super.onActivityResult(req, res, data);
    }

    @Override
    protected void onNewIntent(Intent it) {
        super.onNewIntent(it);
        setIntent(it);
        if (it != null && "call".equals(it.getStringExtra("resume"))) {
            String _ln2 = it.getStringExtra("line");
            pendingLine = _ln2 == null ? "" : _ln2;
            js("try{answerIncoming()}catch(e){}");
        }
    }

    private void autoArm() {
        try {
            boolean ov = Settings.canDrawOverlays(this);
            boolean fl = true;
            if (Build.VERSION.SDK_INT >= 30) fl = Environment.isExternalStorageManager();
            if (ov && fl && !BellService.running) {
                startService(new Intent(this, BellService.class));
            }
        } catch (Throwable t) {
        }
    }

    static volatile String pendingLine = null;

    public class Bridge {

        @JavascriptInterface
        public void log(String m) {
            Shared.log("[web] " + m);
        }

        @JavascriptInterface
        public String state() {
            try {
                JSONObject o = new JSONObject();
                o.put("overlay", Settings.canDrawOverlays(MainActivity.this));
                boolean fl = true;
                if (Build.VERSION.SDK_INT >= 30) fl = Environment.isExternalStorageManager();
                o.put("files", fl);
                o.put("service", BellService.running);
                o.put("name", Shared.cfg(MainActivity.this, "name", ""));
                o.put("dir", Shared.DIR);
                return o.toString();
            } catch (Throwable t) {
                return "{}";
            }
        }

        @JavascriptInterface
        public void openOverlay() {
            runOnUiThread(new Runnable() {
                public void run() {
                    try {
                        startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
                    } catch (Throwable t) {
                    }
                }
            });
        }

        @JavascriptInterface
        public void openFiles() {
            runOnUiThread(new Runnable() {
                public void run() {
                    try {
                        if (Build.VERSION.SDK_INT >= 30) {
                            startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + getPackageName())));
                        } else {
                            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
                        }
                    } catch (Throwable t) {
                    }
                }
            });
        }

        @JavascriptInterface
        public void saveProfile(String name, String number, String location, String avatar, String bg) {
            try {
                Shared.setCfg(MainActivity.this, "name", name == null ? "" : name);
                Shared.setCfg(MainActivity.this, "number", number == null ? "" : number);
                Shared.setCfg(MainActivity.this, "location", location == null ? "" : location);
                Shared.setCfg(MainActivity.this, "avatar", avatar == null ? "" : avatar);
                Shared.setCfg(MainActivity.this, "bg", bg == null ? "" : bg);
            } catch (Throwable t) {
            }
        }

        @JavascriptInterface
        public String takePendingLine() {
            try {
                String s = pendingLine;
                pendingLine = null;
                return s == null ? "__none__" : s;
            } catch (Throwable t) {
                return "__none__";
            }
        }

        @JavascriptInterface
        public String dataRead(String key) {
            try {
                if ("incoming".equals(key)) {
                    String raw = Shared.read(Shared.INCOMING);
                    if (raw == null) return "{\"ok\":false}";
                    String val = Base64.encodeToString(raw.getBytes("UTF-8"), Base64.NO_WRAP);
                    JSONObject o = new JSONObject();
                    o.put("ok", true);
                    o.put("val", val);
                    return o.toString();
                }
                String v = Shared.read(Shared.DIR + "/data/" + key + ".txt");
                if (v.length() == 0) return "{\"ok\":false}";
                JSONObject o = new JSONObject();
                o.put("ok", true);
                o.put("val", v);
                return o.toString();
            } catch (Throwable t) {
                return "{\"ok\":false}";
            }
        }

        @JavascriptInterface
        public String dataWrite(String key, String val) {
            try {
                if ("incoming".equals(key)) {
                    String raw = new String(Base64.decode(val, Base64.DEFAULT), "UTF-8");
                    Shared.write(Shared.INCOMING, raw);
                    return "{\"ok\":true}";
                }
                Shared.write(Shared.DIR + "/data/" + key + ".txt", val);
                return "{\"ok\":true}";
            } catch (Throwable t) {
                return "{\"ok\":false}";
            }
        }

        @JavascriptInterface
        public void ring(String name, String line, int timeout) {
            try {
                JSONObject o = new JSONObject();
                o.put("id", String.valueOf(System.currentTimeMillis()));
                o.put("name", name == null ? "" : name);
                o.put("line", line == null ? "" : line);
                o.put("ts", System.currentTimeMillis());
                Shared.write(Shared.INCOMING, o.toString());
            } catch (Throwable t) {
            }
            autoArm();
        }

        @JavascriptInterface
        public void pickRing() {
            try {
                Intent i = new Intent(android.media.RingtoneManager.ACTION_RINGTONE_PICKER);
                i.putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TYPE, android.media.RingtoneManager.TYPE_RINGTONE);
                i.putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TITLE, "选择来电铃声");
                i.putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
                i.putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false);
                startActivityForResult(i, REQ_RING);
            } catch (Throwable t) {
                Shared.log("pickRing fail " + t);
            }
        }
        @JavascriptInterface
        public String ringPicked() {
            String s = ringPicked;
            ringPicked = null;
            if (s == null) return "__wait";
            return s;
        }
        @JavascriptInterface
        public String ringResult() {
            String raw = Shared.read(Shared.OUTGOING);
            return raw == null ? "" : raw;
        }

        @JavascriptInterface
        public String ask(String jsonStr) {
            try {
                JSONObject p = new JSONObject(jsonStr == null ? "{}" : jsonStr);
                long now = System.currentTimeMillis();
                JSONObject o = new JSONObject();
                o.put("id", String.valueOf(now));
                o.put("message", p.optString("message", ""));
                o.put("card", p.optString("character_card_name", ""));
                o.put("chat_id", p.optString("chat_id", ""));
                o.put("ts", now);
                Shared.write(Shared.DIR + "/ask.json", o.toString());
                lastAskTs = now;
                ChatBridge.send(MainActivity.this, String.valueOf(now), o.optString("message", ""), p.optString("chat_id", ""));
                Shared.log("[web] ask " + o.optString("message", "").substring(0, Math.min(40, o.optString("message", "").length())));
                JSONObject r = new JSONObject();
                r.put("ok", true);
                r.put("pending", true);
                return r.toString();
            } catch (Throwable t) {
                return "{\"ok\":false}";
            }
        }

        @JavascriptInterface
        public String pollAsk() {
            try {
                JSONObject r = new JSONObject();
                String raw = Shared.read(Shared.REPLY);
                if (raw != null && raw.length() > 0) {
                    JSONObject o = new JSONObject(raw);
                    long ts = o.optLong("ts", 0);
                    String id = o.optString("id", "");
                    if (ts > lastAskTs && ts > lastPolledTs && (id.length() == 0 || lastAskTs == 0 || true)) {
                        lastPolledTs = ts;
                        String text = o.optString("text", "");
                        JSONObject d = new JSONObject();
                        d.put("text", text);
                        r.put("ok", true);
                        r.put("data", d);
                        return r.toString();
                    }
                }
                r.put("ok", true);
                r.put("pending", true);
                return r.toString();
            } catch (Throwable t) {
                return "{\"ok\":true,\"pending\":true}";
            }
        }

        @JavascriptInterface
        public String mini(String j) {
            try {
                JSONObject p = new JSONObject(j == null ? "{}" : j);
                final String nm = p.optString("name", "通话中");
                final long st = p.optLong("start", 0);
                runOnUiThread(new Runnable() {
                    public void run() {
                        showMini(nm, st);
                        try { moveTaskToBack(true); } catch (Throwable t) { }
                    }
                });
                return "{\"ok\":true}";
            } catch (Throwable t) {
                return "{\"ok\":false}";
            }
        }
        @JavascriptInterface
        public void miniClose() {
            runOnUiThread(new Runnable() {
                public void run() { hideMini(); }
            });
        }
        @JavascriptInterface
        public void httpGet(final String url, final String cbId) {
            new Thread(new Runnable() {
                public void run() {
                    String out;
                    java.net.HttpURLConnection c = null;
                    try {
                        c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
                        c.setConnectTimeout(5000);
                        c.setReadTimeout(6000);
                        c.setRequestMethod("GET");
                        c.setRequestProperty("User-Agent", "NightBell/1.0");
                        int code = c.getResponseCode();
                        java.io.InputStream is = code >= 400 ? c.getErrorStream() : c.getInputStream();
                        StringBuilder sb = new StringBuilder();
                        if (is != null) {
                            java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(is, "UTF-8"));
                            String ln;
                            while ((ln = br.readLine()) != null) sb.append(ln);
                            br.close();
                        }
                        JSONObject o = new JSONObject();
                        o.put("ok", code >= 200 && code < 300);
                        o.put("status", code);
                        o.put("body", sb.toString());
                        out = o.toString();
                    } catch (Throwable t) {
                        try {
                            JSONObject o = new JSONObject();
                            o.put("ok", false);
                            o.put("error", String.valueOf(t));
                            out = o.toString();
                        } catch (Throwable t2) {
                            out = "{\"ok\":false}";
                        }
                    } finally {
                        try { if (c != null) c.disconnect(); } catch (Throwable t) { }
                    }
                    final String res = out;
                    try {
                        js("window.__nbHttp && window.__nbHttp(" + JSONObject.quote(cbId) + "," + JSONObject.quote(res) + ")");
                    } catch (Throwable t) { }
                }
            }).start();
        }

        @JavascriptInterface
        public String asrBegin(String opts) {
            return Stt.begin();
        }

        @JavascriptInterface
        public String asrEnd() {
            return Stt.end();
        }

        @JavascriptInterface
        public void asrCancel() {
            Stt.cancel();
        }

        @JavascriptInterface
        public int asrAmp() {
            return Stt.amp();
        }

        @JavascriptInterface
        public void toast(String s) {
            final String m = s;
            runOnUiThread(new Runnable() {
                public void run() {
                    android.widget.Toast.makeText(MainActivity.this, m, android.widget.Toast.LENGTH_SHORT).show();
                }
            });
        }
    }
}