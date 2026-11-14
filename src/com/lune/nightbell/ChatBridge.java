package com.lune.nightbell;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

public class ChatBridge {
    public static final String PKG = "com.ai.assistance.operit";
    public static final String RCV = "com.ai.assistance.operit.integrations.intent.ExternalChatReceiver";
    public static final String ACT = "com.ai.assistance.operit.EXTERNAL_CHAT";
    public static final String REPLY_ACT = "com.lune.nightbell.REPLY";

    public static void send(Context c, String rid, String msg, String chatId) {
        try {
            Intent i = new Intent(ACT);
            i.setComponent(new ComponentName(PKG, RCV));
            i.putExtra("request_id", rid);
            i.putExtra("message", msg);
            if (chatId != null && chatId.length() > 0) i.putExtra("chat_id", chatId);
            i.putExtra("reply_action", REPLY_ACT);
            i.putExtra("reply_package", "com.lune.nightbell");
            i.putExtra("return_tool_status", false);
            i.putExtra("show_floating", false);
            i.setFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            c.sendBroadcast(i);
            Shared.log("[bridge] out " + rid);
        } catch (Throwable t) {
            Shared.log("[bridge] send fail " + t);
        }
    }
}
