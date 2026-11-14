package com.lune.nightbell;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Base64;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class Tts {
    public static volatile String state = "idle";
    public static volatile String dataUrl = null;
    public static volatile String lastError = null;

    public static volatile String streamState = "idle";
    public static volatile String streamErr = null;
    static volatile int streamGen = 0;
    static volatile AudioTrack streamTrack = null;
    static volatile android.media.MediaPlayer streamPlayer = null;

    public static void reset() {
        state = "idle";
        dataUrl = null;
        lastError = null;
    }

    public static void stopStream() {
        streamGen++;
        try { segQ.clear(); } catch (Throwable t) { }
        AudioTrack at = streamTrack;
        streamTrack = null;
        if (at != null) {
            try { at.pause(); } catch (Throwable t) { }
            try { at.flush(); } catch (Throwable t) { }
        }
        android.media.MediaPlayer mp = streamPlayer;
        streamPlayer = null;
        if (mp != null) {
            try { mp.stop(); } catch (Throwable t) { }
            try { mp.release(); } catch (Throwable t) { }
        }
        streamState = "idle";
        streamErr = null;
    }

            static final java.util.concurrent.LinkedBlockingQueue<String> segQ = new java.util.concurrent.LinkedBlockingQueue<String>();
    static void playerLoop(final int g) {
        new Thread(new Runnable() {
            public void run() {
                while (true) {
                    String p = null;
                    try { p = segQ.poll(20, java.util.concurrent.TimeUnit.SECONDS); } catch (Throwable t) { }
                    if (p == null) {
                        if (g != streamGen) return;
                        continue;
                    }
                    if ("__END__".equals(p)) {
                        if (g == streamGen) streamState = "done";
                        return;
                    }
                    if (g != streamGen) {
                        try { new java.io.File(p).delete(); } catch (Throwable t) { }
                        return;
                    }
                    android.media.MediaPlayer mp = null;
                    try {
                        mp = new android.media.MediaPlayer();
                        mp.setAudioAttributes(new android.media.AudioAttributes.Builder()
                                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build());
                        mp.setDataSource(p);
                        mp.prepare();
                        if (g != streamGen) throw new Exception("stopped");
                        final Object lk = new Object();
                        final boolean[] fin = new boolean[] { false };
                        mp.setOnCompletionListener(new android.media.MediaPlayer.OnCompletionListener() {
                            public void onCompletion(android.media.MediaPlayer q) {
                                synchronized (lk) { fin[0] = true; lk.notifyAll(); }
                            }
                        });
                        streamPlayer = mp;
                        streamState = "playing";
                        mp.start();
                        synchronized (lk) {
                            if (!fin[0]) { try { lk.wait(15000); } catch (Throwable t) { } }
                        }
                    } catch (Throwable t) {
                        if (g == streamGen) Shared.log("[tts] seg err " + t);
                    } finally {
                        if (streamPlayer == mp) streamPlayer = null;
                        if (mp != null) { try { mp.release(); } catch (Throwable t) { } }
                        try { new java.io.File(p).delete(); } catch (Throwable t) { }
                    }
                }
            }
        }).start();
    }
    static void flushSeg(byte[] data, int len, int sr, int g, int idx) {
        try {
            String path = "/sdcard/Download/Operit/app_data/nightbell/seg_" + g + "_" + idx + ".wav";
            java.io.RandomAccessFile raf = new java.io.RandomAccessFile(path, "rw");
            raf.setLength(0);
            raf.write(new byte[44]);
            raf.write(data, 0, len);
            writeWav(raf, len, sr, 1);
            raf.close();
            segQ.offer(path);
        } catch (Throwable t) { Shared.log("[tts] flush err " + t); }
    }
            static volatile java.net.ServerSocket proxySs = null;
    static volatile int proxyPort = 0;
    static volatile String proxyUrl = null;
    static volatile String proxyKey = null;
    static volatile String proxyBody = null;
    static int ensureProxy() throws Exception {
        java.net.ServerSocket ss = proxySs;
        if (ss != null && proxyPort > 0) return proxyPort;
        synchronized (Tts.class) {
            if (proxySs != null && proxyPort > 0) return proxyPort;
            ss = new java.net.ServerSocket(0, 8, java.net.InetAddress.getByName("127.0.0.1"));
            proxySs = ss;
            proxyPort = ss.getLocalPort();
            final java.net.ServerSocket fss = ss;
            new Thread(new Runnable() {
                public void run() {
                    while (true) {
                        try {
                            final java.net.Socket s = fss.accept();
                            new Thread(new Runnable() { public void run() { serve(s); } }).start();
                        } catch (Throwable t) { return; }
                    }
                }
            }).start();
            return proxyPort;
        }
    }
    static void serve(java.net.Socket s) {
        final int myGen = streamGen;
        HttpURLConnection c = null;
        try {
            java.io.InputStream in = s.getInputStream();
            java.io.OutputStream out = s.getOutputStream();
            StringBuilder sb = new StringBuilder();
            int guard = 0;
            while (guard++ < 8192) {
                int r = in.read();
                if (r < 0) break;
                sb.append((char) r);
                int L = sb.length();
                if (L >= 4 && sb.charAt(L - 1) == '\n' && sb.charAt(L - 2) == '\r' && sb.charAt(L - 3) == '\n' && sb.charAt(L - 4) == '\r') break;
            }
            String u = proxyUrl, k = proxyKey, bd = proxyBody;
            if (u == null || u.length() == 0) {
                out.write("HTTP/1.1 500 no\r\nConnection: close\r\n\r\n".getBytes("UTF-8"));
                out.flush();
                return;
            }
            c = (HttpURLConnection) new URL(u).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(20000);
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            c.setRequestProperty("Accept", "audio/mpeg");
            c.setRequestProperty("xi-api-key", k == null ? "" : k);
            OutputStream os = c.getOutputStream();
            os.write((bd == null ? "{}" : bd).getBytes("UTF-8"));
            os.flush();
            os.close();
            int code = c.getResponseCode();
            if (code >= 400) {
                Shared.log("[tts] up http " + code);
                out.write("HTTP/1.1 502 up\r\nConnection: close\r\n\r\n".getBytes("UTF-8"));
                out.flush();
                return;
            }
            String ct = c.getContentType();
            if (ct == null || ct.length() == 0) ct = "audio/mpeg";
            out.write(("HTTP/1.0 200 OK\r\nContent-Type: " + ct + "\r\nConnection: close\r\n\r\n").getBytes("UTF-8"));
            out.flush();
            InputStream is = c.getInputStream();
            byte[] buf = new byte[8192];
            int n, total = 0;
            while (myGen == streamGen && (n = is.read(buf)) > 0) {
                out.write(buf, 0, n);
                out.flush();
                if (total == 0) Shared.log("[tts] up first bytes");
                total += n;
            }
            try { is.close(); } catch (Throwable t) { }
            Shared.log("[tts] up done " + total);
        } catch (Throwable t) {
            Shared.log("[tts] up err " + t);
        } finally {
            if (c != null) { try { c.disconnect(); } catch (Throwable t) { } }
            try { s.close(); } catch (Throwable t) { }
        }
    }
    /* 流式：本地小代理把 ElevenLabs 的流转给系统播放器，边下边播 */
    public static void startStream(final String url, final String key, final String body, final int rate) {
        stopStream();
        final int g = streamGen;
        streamState = "pending";
        streamErr = null;
        if (url == null || url.length() == 0 || key == null || key.length() == 0) {
            streamState = "error";
            streamErr = "no url or key";
            return;
        }
        new Thread(new Runnable() {
            public void run() {
                android.media.MediaPlayer mp = null;
                try {
                    int port = ensureProxy();
                    proxyUrl = url;
                    proxyKey = key;
                    proxyBody = body;
                    Shared.log("[tts] proxy " + port);
                    mp = new android.media.MediaPlayer();
                    mp.setAudioAttributes(new android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build());
                    mp.setDataSource("http://127.0.0.1:" + port + "/tts");
                    mp.prepare();
                    if (g != streamGen) throw new Exception("stopped");
                    streamPlayer = mp;
                    streamState = "playing";
                    mp.setOnCompletionListener(new android.media.MediaPlayer.OnCompletionListener() {
                        public void onCompletion(android.media.MediaPlayer p) {
                            if (streamPlayer == p) { streamPlayer = null; streamState = "done"; }
                            Shared.log("[tts] play end");
                            try { p.release(); } catch (Throwable t) { }
                        }
                    });
                    mp.start();
                    Shared.log("[tts] play start");
                } catch (Throwable t) {
                    if (g == streamGen) {
                        streamState = "error";
                        streamErr = String.valueOf(t);
                    }
                    Shared.log("[tts] stream err " + t);
                    if (mp != null && streamPlayer != mp) { try { mp.release(); } catch (Throwable t2) { } }
                }
            }
        }).start();
    }
    static void writeWav(java.io.RandomAccessFile raf, int dataLen, int sr, int ch) throws Exception {
        int byteRate = sr * ch * 2;
        byte[] h = new byte[44];
        h[0] = 'R'; h[1] = 'I'; h[2] = 'F'; h[3] = 'F';
        int chunk = 36 + dataLen;
        h[4] = (byte) (chunk & 0xff); h[5] = (byte) ((chunk >> 8) & 0xff); h[6] = (byte) ((chunk >> 16) & 0xff); h[7] = (byte) ((chunk >> 24) & 0xff);
        h[8] = 'W'; h[9] = 'A'; h[10] = 'V'; h[11] = 'E';
        h[12] = 'f'; h[13] = 'm'; h[14] = 't'; h[15] = ' ';
        h[16] = 16; h[17] = 0; h[18] = 0; h[19] = 0;
        h[20] = 1; h[21] = 0;
        h[22] = (byte) ch; h[23] = 0;
        h[24] = (byte) (sr & 0xff); h[25] = (byte) ((sr >> 8) & 0xff); h[26] = (byte) ((sr >> 16) & 0xff); h[27] = (byte) ((sr >> 24) & 0xff);
        h[28] = (byte) (byteRate & 0xff); h[29] = (byte) ((byteRate >> 8) & 0xff); h[30] = (byte) ((byteRate >> 16) & 0xff); h[31] = (byte) ((byteRate >> 24) & 0xff);
        int ba = ch * 2;
        h[32] = (byte) ba; h[33] = 0;
        h[34] = 16; h[35] = 0;
        h[36] = 'd'; h[37] = 'a'; h[38] = 't'; h[39] = 'a';
        h[40] = (byte) (dataLen & 0xff); h[41] = (byte) ((dataLen >> 8) & 0xff); h[42] = (byte) ((dataLen >> 16) & 0xff); h[43] = (byte) ((dataLen >> 24) & 0xff);
        raf.seek(0);
        raf.write(h);
    }

    static String readAll(InputStream is) {
        try {
            if (is == null) return "";
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] b = new byte[4096];
            int n;
            while ((n = is.read(b)) > 0) bo.write(b, 0, n);
            is.close();
            return new String(bo.toByteArray(), "UTF-8");
        } catch (Throwable t) { return ""; }
    }

    static String cut(String s, int n) {
        if (s == null) return "";
        return s.length() > n ? s.substring(0, n) : s;
    }

    public static void start(final String text, final String base, final String key, final String model, final String voice) {
        reset();
        if (text == null || text.length() == 0 || key == null || key.length() == 0) {
            state = "error";
            lastError = "no key";
            return;
        }
        state = "pending";
        new Thread(new Runnable() {
            public void run() {
                try {
                    String prov = guess(base);
                    byte[] a;
                    if ("moss".equals(prov)) a = moss(base, key, model, voice, text);
                    else if ("eleven".equals(prov)) a = eleven(base, key, model, voice, text);
                    else if ("minimax".equals(prov)) a = minimax(base, key, model, voice, text);
                    else if ("volcano".equals(prov)) throw new Exception("\u706b\u5c71\u8fd8\u5dee appid/token/cluster\uff0c\u8bbe\u7f6e\u91cc\u6ca1\u8fd9\u51e0\u9879\uff0c\u5148\u522b\u9009");
                    else a = ali(base, key, model, voice, text);
                    if (a == null || a.length == 0) {
                        state = "error";
                        lastError = "empty audio";
                        return;
                    }
                    dataUrl = "data:audio/mpeg;base64," + Base64.encodeToString(a, Base64.NO_WRAP);
                    state = "done";
                    Shared.log("[tts] ok " + a.length + " " + prov);
                } catch (Throwable t) {
                    state = "error";
                    lastError = String.valueOf(t);
                    Shared.log("[tts] fail " + t);
                }
            }
        }).start();
    }
    static String guess(String base) {
        String b = base == null ? "" : base.toLowerCase();
        if (b.indexOf("elevenlabs") >= 0) return "eleven";
        if (b.indexOf("minimax") >= 0) return "minimax";
        if (b.indexOf("mosi") >= 0) return "moss";
        if (b.indexOf("openspeech") >= 0) return "volcano";
        return "ali";
    }
    static String trim(String s) {
        String r = s == null ? "" : s.trim();
        while (r.endsWith("/")) r = r.substring(0, r.length() - 1);
        return r;
    }
    static byte[] moss(String base, String key, String model, String voice, String text) throws Exception {
        String u = trim(base) + "/v1/audio/speech";
        JSONObject b = new JSONObject();
        b.put("model", model.length() > 0 ? model : "moss-tts");
        b.put("input", text);
        if (voice.length() > 0) b.put("voice", voice);
        return send(u, "Authorization", "Bearer " + key, b.toString());
    }
    static byte[] eleven(String base, String key, String model, String voice, String text) throws Exception {
        String u = trim(base) + "/v1/text-to-speech/" + voice;
        JSONObject b = new JSONObject();
        b.put("text", text);
        b.put("model_id", model.length() > 0 ? model : "eleven_multilingual_v2");
        JSONObject vs = new JSONObject();
        vs.put("stability", 0.5);
        vs.put("similarity_boost", 0.75);
        b.put("voice_settings", vs);
        return send(u, "xi-api-key", key, b.toString());
    }
    static byte[] ali(String base, String key, String model, String voice, String text) throws Exception {
        String m = model.length() > 0 ? model : "qwen-tts";
        String lm = m.toLowerCase();
        JSONObject in = new JSONObject();
        in.put("text", text);
        if (voice.length() > 0) in.put("voice", voice);
        else if (lm.indexOf("qwen-tts") == 0) in.put("voice", "Cherry");
        JSONObject b = new JSONObject();
        b.put("model", m);
        b.put("input", in);
        String u = lm.indexOf("cosy") == 0
                ? trim(base) + "/api/v1/services/aigc/text2speech/synthesis"
                : trim(base) + "/api/v1/services/aigc/multimodal-generation/generation";
        byte[] raw = send(u, "Authorization", "Bearer " + key, b.toString());
        String s = new String(raw, "UTF-8");
        if (s.length() > 0 && s.charAt(0) != '{') return raw;
        JSONObject o = new JSONObject(s);
        JSONObject out = o.optJSONObject("output");
        if (out == null) throw new Exception("ali " + cut(s, 160));
        JSONObject au = out.optJSONObject("audio");
        if (au != null) {
            String d = au.optString("data", "");
            if (d.length() > 0) return Base64.decode(d, Base64.DEFAULT);
            String url = au.optString("url", "");
            if (url.length() > 0) return send(url, null, null, null);
        }
        throw new Exception("ali " + cut(s, 160));
    }

    static byte[] minimax(String base, String key, String model, String voice, String text) throws Exception {
        String k = key.trim();
        String group = "";
        int ci = k.indexOf(':');
        if (ci > 0) {
            group = k.substring(ci + 1).trim();
            k = k.substring(0, ci).trim();
        }
        String u = trim(base) + "/v1/t2a_v2" + (group.length() > 0 ? "?GroupId=" + group : "");
        JSONObject vs = new JSONObject();
        vs.put("voice_id", voice.length() > 0 ? voice : "female-shaonv");
        vs.put("speed", 1.0);
        vs.put("vol", 1.0);
        vs.put("pitch", 0);
        JSONObject au = new JSONObject();
        au.put("sample_rate", 32000);
        au.put("bitrate", 128000);
        au.put("format", "mp3");
        JSONObject b = new JSONObject();
        b.put("model", model.length() > 0 ? model : "speech-01-turbo");
        b.put("text", text);
        b.put("stream", false);
        b.put("voice_setting", vs);
        b.put("audio_setting", au);
        byte[] raw = send(u, "Authorization", "Bearer " + k, b.toString());
        String s = new String(raw, "UTF-8");
        JSONObject o = new JSONObject(s);
        JSONObject br = o.optJSONObject("base_resp");
        if (br != null) {
            int sc = br.optInt("status_code", 0);
            if (sc != 0) throw new Exception("minimax " + sc + " " + br.optString("status_msg", ""));
        }
        JSONObject d = o.optJSONObject("data");
        String hex = d == null ? "" : d.optString("audio", "");
        if (hex.length() > 0) return hexToBytes(hex);
        throw new Exception("minimax \u6ca1\u6709\u97f3\u9891 " + cut(s, 140));
    }

    static byte[] hexToBytes(String h) {
        int n = h.length() / 2;
        byte[] out = new byte[n];
        for (int i = 0; i < n; i++) {
            out[i] = (byte) Integer.parseInt(h.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    static byte[] send(String url, String hk, String hv, String body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(25000);
        c.setRequestProperty("Content-Type", "application/json");
        if (hk != null && hv != null) c.setRequestProperty(hk, hv);
        if (body != null) {
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            OutputStream os = c.getOutputStream();
            os.write(body.getBytes("UTF-8"));
            os.flush();
            os.close();
        }
        int code = c.getResponseCode();
        InputStream is = code >= 400 ? c.getErrorStream() : c.getInputStream();
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        if (is != null) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) bo.write(buf, 0, n);
            is.close();
        }
        byte[] out = bo.toByteArray();
        c.disconnect();
        if (code >= 400) {
            String es = new String(out, "UTF-8");
            throw new Exception("http " + code + " " + (es.length() > 140 ? es.substring(0, 140) : es));
        }
        return out;
    }
}
