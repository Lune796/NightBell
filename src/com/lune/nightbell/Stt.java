package com.lune.nightbell;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import org.json.JSONObject;

import com.k2fsa.sherpa.onnx.FeatureConfig;
import com.k2fsa.sherpa.onnx.OfflineModelConfig;
import com.k2fsa.sherpa.onnx.OfflineRecognizer;
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig;
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig;
import com.k2fsa.sherpa.onnx.OfflineStream;

import java.io.File;
import java.util.Arrays;

/** 本地离线语音识别。sherpa-onnx(sense-voice)，模型目录 <DIR>/stt-sense */
public class Stt {

    private static final String SDIR = Shared.DIR + "/stt-sense";
    private static final int SR = 16000;
    private static final int MAX_SAMPLES = SR * 120;

    private static volatile int mode = 0; // 1=sherpa
    private static OfflineRecognizer asr = null;

    private static volatile boolean running = false;
    private static volatile int amp = 0;
    private static AudioRecord ar = null;
    private static Thread th = null;
    private static final Object LOCK = new Object();

    private static float[] samples = new float[SR * 8];
    private static int sLen = 0;

    public static int amp() {
        return amp;
    }

    public static synchronized boolean ensure() {
        if (mode != 0) return true;

        try {
            File m = new File(SDIR + "/model.int8.onnx");
            File t = new File(SDIR + "/tokens.txt");
            if (m.exists() && t.exists()) {
                long t0 = System.currentTimeMillis();
                FeatureConfig fc = new FeatureConfig();
                fc.setSampleRate(SR);
                fc.setFeatureDim(80);
                OfflineSenseVoiceModelConfig sv = new OfflineSenseVoiceModelConfig();
                sv.setModel(m.getAbsolutePath());
                sv.setLanguage("auto");
                sv.setUseInverseTextNormalization(true);
                OfflineModelConfig mc = new OfflineModelConfig();
                mc.setSenseVoice(sv);
                mc.setTokens(t.getAbsolutePath());
                mc.setNumThreads(2);
                mc.setProvider("cpu");
                mc.setModelType("sense_voice");
                OfflineRecognizerConfig cfg = new OfflineRecognizerConfig();
                cfg.setFeatConfig(fc);
                cfg.setModelConfig(mc);
                cfg.setDecodingMethod("greedy_search");
                asr = new OfflineRecognizer(null, cfg);
                mode = 1;
                Shared.log("stt sherpa ok " + (System.currentTimeMillis() - t0) + "ms");
                return true;
            }
            Shared.log("stt sherpa model missing");
        } catch (Throwable e) {
            Shared.log("stt sherpa fail " + e);
            asr = null;
        }

        mode = 0;
        return false;
    }

    /** 随包分发的模型文件名与字节数，用来判断本地副本是否完整 */
    private static final String[] MODEL_FILES = {"model.int8.onnx", "tokens.txt"};
    private static final long[] MODEL_SIZES = {239233841L, 315894L};
    private static volatile boolean modelDone = false;
    private static volatile int modelPct = -1;

    public static int modelPct() {
        return modelPct;
    }

    public static void preload(final android.content.Context ctx) {
        new Thread(new Runnable() {
            public void run() {
                ensureModel(ctx);
                ensure();
            }
        }).start();
    }

    /** 首次启动把 assets 里的模型拷到数据目录，已存在且大小对得上就跳过 */
    private static void ensureModel(android.content.Context ctx) {
        if (modelDone || ctx == null) return;
        synchronized (Stt.class) {
            if (modelDone) return;
            try {
                File d = new File(SDIR);
                d.mkdirs();
                long total = 0;
                for (long v : MODEL_SIZES) total += v;
                long done = 0;
                for (int i = 0; i < MODEL_FILES.length; i++) {
                    File f = new File(d, MODEL_FILES[i]);
                    if (f.exists() && f.length() == MODEL_SIZES[i]) {
                        done += MODEL_SIZES[i];
                        continue;
                    }
                    long t0 = System.currentTimeMillis();
                    File tmp = new File(d, MODEL_FILES[i] + ".tmp");
                    java.io.InputStream in = ctx.getAssets().open("models/stt-sense/" + MODEL_FILES[i]);
                    java.io.FileOutputStream out = new java.io.FileOutputStream(tmp);
                    byte[] buf = new byte[65536];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        out.write(buf, 0, n);
                        done += n;
                        modelPct = (int) (done * 100 / total);
                    }
                    out.flush();
                    out.close();
                    in.close();
                    if (tmp.length() != MODEL_SIZES[i]) {
                        tmp.delete();
                        throw new RuntimeException("size mismatch " + MODEL_FILES[i] + " " + tmp.length());
                    }
                    if (f.exists()) f.delete();
                    if (!tmp.renameTo(f)) throw new RuntimeException("rename fail " + MODEL_FILES[i]);
                    Shared.log("stt model " + MODEL_FILES[i] + " " + f.length() + " "
                            + (System.currentTimeMillis() - t0) + "ms");
                }
                modelDone = true;
                modelPct = 100;
                Shared.log("stt model ready");
            } catch (Throwable e) {
                modelPct = -1;
                Shared.log("stt model fail " + e);
            }
        }
    }

    private static void addSamples(byte[] buf, int n) {
        int need = sLen + n / 2;
        if (need > MAX_SAMPLES) need = MAX_SAMPLES;
        if (need > samples.length) {
            int cap = samples.length;
            while (cap < need) cap *= 2;
            if (cap > MAX_SAMPLES) cap = MAX_SAMPLES;
            float[] t = new float[cap];
            System.arraycopy(samples, 0, t, 0, sLen);
            samples = t;
        }
        for (int i = 0; i + 1 < n && sLen < MAX_SAMPLES; i += 2) {
            short v = (short) ((buf[i] & 0xff) | (buf[i + 1] << 8));
            samples[sLen++] = v / 32768.0f;
        }
    }

    public static String begin() {
        synchronized (LOCK) {
            if (running) return "{\"ok\":true}";
            if (!ensure()) return "{\"ok\":false,\"err\":\"model\"}";
            try {
                int min = AudioRecord.getMinBufferSize(SR, AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT);
                ar = new AudioRecord(MediaRecorder.AudioSource.MIC, SR,
                        AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                        Math.max(min * 4, 12800));
                if (ar.getState() != AudioRecord.STATE_INITIALIZED) {
                    Shared.log("stt ar state " + ar.getState());
                    try { ar.release(); } catch (Throwable t) { }
                    ar = null;
                    return "{\"ok\":false,\"err\":\"mic\"}";
                }
            } catch (Throwable t) {
                Shared.log("stt init fail " + t);
                return "{\"ok\":false,\"err\":\"init\"}";
            }
            sLen = 0;
            running = true;
            amp = 0;
            final AudioRecord a = ar;
            final int m = mode;
            th = new Thread(new Runnable() {
                public void run() {
                    byte[] buf = new byte[3200];
                    try { a.startRecording(); } catch (Throwable t) { Shared.log("stt start " + t); }
                    while (running) {
                        int n;
                        try {
                            n = a.read(buf, 0, buf.length);
                        } catch (Throwable t) {
                            break;
                        }
                        if (n > 0) {
                            long sum = 0;
                            int c = 0;
                            for (int i = 0; i + 1 < n; i += 2) {
                                int v = (short) ((buf[i] & 0xff) | (buf[i + 1] << 8));
                                sum += (long) v * v;
                                c++;
                            }
                            if (c > 0) {
                                int rms = (int) Math.sqrt(sum / (double) c);
                                amp = Math.min(30000, rms * 10);
                            }
                            try {
                                if (m == 1) addSamples(buf, n);
                            } catch (Throwable t) { }
                        } else {
                            try { Thread.sleep(20); } catch (Exception e) { }
                        }
                    }
                    amp = 0;
                    try { a.stop(); } catch (Throwable t) { }
                    try { a.release(); } catch (Throwable t) { }
                }
            });
            th.start();
            return "{\"ok\":true}";
        }
    }

    private static String clean(String s) {
        if (s == null) return "";
        String t = s.trim();
        String d = t.replaceAll("[\\s\\p{Punct}，。、；：！？…—～·“”‘’（）《》【】「」]", "");
        return d.isEmpty() ? "" : t;
    }

    private static String pick(String json) {
        try {
            JSONObject o = new JSONObject(json);
            String s = o.optString("text", "");
            return s == null ? "" : s.trim();
        } catch (Throwable t) {
            return "";
        }
    }

    public static String end() {
        Thread t1;
        int m;
        synchronized (LOCK) {
            if (!running) return "{\"ok\":true,\"text\":\"\"}";
            running = false;
            m = mode;
            t1 = th;
            th = null;
            ar = null;
        }
        if (t1 != null) {
            try { t1.join(1500); } catch (Exception e) { }
        }

        String text = "";
        if (m == 1) {
            OfflineRecognizer r = asr;
            int n = sLen;
            if (r != null && n > 0) {
                long t0 = System.currentTimeMillis();
                OfflineStream st = null;
                try {
                    st = r.createStream();
                    st.acceptWaveform(Arrays.copyOf(samples, n), SR);
                    r.decode(st);
                    String s = r.getResult(st).getText();
                    text = s == null ? "" : s.trim();
                    Shared.log("stt decode " + (n / (double) SR) + "s " + (System.currentTimeMillis() - t0) + "ms " + text);
                } catch (Throwable t) {
                    Shared.log("stt decode fail " + t);
                } finally {
                    try { if (st != null) st.release(); } catch (Throwable t) { }
                }
            }
            sLen = 0;
        }

        try {
            text = clean(text);
            return "{\"ok\":true,\"text\":" + JSONObject.quote(text) + "}";
        } catch (Throwable t) {
            return "{\"ok\":true,\"text\":\"\"}";
        }
    }

    public static void cancel() {
        Thread t1;
        synchronized (LOCK) {
            if (!running) return;
            running = false;
            t1 = th;
            th = null;
            ar = null;
        }
        if (t1 != null) {
            try { t1.join(1200); } catch (Exception e) { }
        }
        sLen = 0;
        amp = 0;
    }
}