package com.lune.nightbell;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import org.json.JSONObject;

public class RingReceiver extends BroadcastReceiver {
    public static final String ACT = "com.lune.nightbell.RING";

    @Override
    public void onReceive(Context c, Intent i) {
        try {
            if (i == null || !ACT.equals(i.getAction())) return;
            String name = i.getStringExtra("name");
            String line = i.getStringExtra("line");
            if (name == null || name.length() == 0) name = Shared.cfg(c, "name", "AI");
            if (line == null) line = "";
            String id = String.valueOf(System.currentTimeMillis());
            Shared.ensureDir();
            try {
                JSONObject o = new JSONObject();
                o.put("id", id);
                o.put("name", name);
                o.put("line", line);
                o.put("ts", System.currentTimeMillis());
                Shared.write(Shared.INCOMING, o.toString());
            } catch (Throwable t) {
            }
            Shared.log("ring-bc " + name + " | " + line);
            if (CallActivity.alive) {
                Shared.log("ring-bc busy");
                return;
            }
            Intent it = new Intent(c, CallActivity.class);
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            it.putExtra("id", id);
            it.putExtra("name", name);
            it.putExtra("line", line);
            it.putExtra("number", Shared.cfg(c, "number", ""));
            it.putExtra("location", Shared.cfg(c, "location", ""));
            c.startActivity(it);
        } catch (Throwable t) {
            Shared.log("ring-bc fail " + t);
        }
    }
}
