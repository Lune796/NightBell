package com.lune.nightbell;

import android.webkit.JavascriptInterface;
import org.json.JSONObject;

public class TtsBridge {
    @JavascriptInterface
    public String speak(String j) {
        try {
            JSONObject p = new JSONObject(j == null ? "{}" : j);
            Tts.start(p.optString("text", ""), p.optString("base", ""), p.optString("key", ""), p.optString("model", ""), p.optString("voice", ""));
            return "{\"ok\":true,\"pending\":true}";
        } catch (Throwable t) {
            return "{\"ok\":false}";
        }
    }
    @JavascriptInterface
    public String poll() {
        try {
            JSONObject o = new JSONObject();
            if ("done".equals(Tts.state) && Tts.dataUrl != null) {
                o.put("ok", true);
                o.put("dataUrl", Tts.dataUrl);
                return o.toString();
            }
            if ("error".equals(Tts.state)) {
                o.put("ok", false);
                o.put("error", Tts.lastError == null ? "err" : Tts.lastError);
                return o.toString();
            }
            o.put("ok", true);
            o.put("pending", true);
            return o.toString();
        } catch (Throwable t) {
            return "{\"ok\":false}";
        }
    }
    @JavascriptInterface
    public String ttsStream(String j) {
        try {
            JSONObject p = new JSONObject(j == null ? "{}" : j);
            Tts.startStream(p.optString("url", ""), p.optString("key", ""), p.optString("body", "{}"), p.optInt("rate", 16000));
            return "{\"ok\":true}";
        } catch (Throwable t) {
            return "{\"ok\":false}";
        }
    }
    @JavascriptInterface
    public String ttsPoll() {
        try {
            JSONObject o = new JSONObject();
            o.put("ok", true);
            o.put("state", Tts.streamState == null ? "idle" : Tts.streamState);
            if (Tts.streamErr != null) o.put("error", Tts.streamErr);
            return o.toString();
        } catch (Throwable t) {
            return "{\"ok\":false}";
        }
    }
    @JavascriptInterface
    public void stop() {
        Tts.reset();
        Tts.stopStream();
    }
}
