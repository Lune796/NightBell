package com.lune.nightbell;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        try {
            String a = intent == null ? "" : intent.getAction();
            Shared.log("boot " + a);
            if ("1".equals(Shared.cfg(ctx, "armer", ""))) {
                ctx.startForegroundService(new Intent(ctx, BellService.class));
            }
        } catch (Throwable t) {
        }
    }
}