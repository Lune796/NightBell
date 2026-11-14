package com.lune.nightbell;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import org.json.JSONObject;

public class ReplyReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        try {
            String act = i.getAction();
            if (act == null || !act.equals(ChatBridge.REPLY_ACT)) return;
            boolean ok = i.getBooleanExtra("success", false);
            String text = i.getStringExtra("ai_response");
            String err = i.getStringExtra("error");
            String rid = i.getStringExtra("request_id");
            Shared.log("[bridge] in ok=" + ok + " len=" + (text == null ? 0 : text.length()) + " err=" + err);
            if (text != null && text.length() > 0) {
                JSONObject o = new JSONObject();
                o.put("id", rid == null ? "" : rid);
                o.put("text", text);
                o.put("ts", System.currentTimeMillis());
                Shared.write(Shared.REPLY, o.toString());
            }
        } catch (Throwable t) {
            Shared.log("[bridge] recv fail " + t);
        }
    }
}
