package com.lune.nightbell;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.ByteArrayOutputStream;

public class Shared {
    public static final String DIR = "/sdcard/Download/Operit/app_data/nightbell";
    public static final String INCOMING = DIR + "/incoming.json";
    public static final String OUTGOING = DIR + "/outgoing.json";
    public static final String REPLY = DIR + "/reply.json";
    public static final String DEBUG = DIR + "/debug.log";

    public static void ensureDir() {
        try {
            new File(DIR).mkdirs();
            new File(DIR + "/data").mkdirs();
        } catch (Throwable t) {
        }
    }

    public static String read(String path) {
        FileInputStream in = null;
        try {
            File f = new File(path);
            if (!f.exists()) return null;
            in = new FileInputStream(f);
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            return new String(bo.toByteArray(), "UTF-8");
        } catch (Throwable t) {
            return null;
        } finally {
            try {
                if (in != null) in.close();
            } catch (Throwable t) {
            }
        }
    }

    public static boolean write(String path, String body) {
        FileOutputStream out = null;
        try {
            ensureDir();
            out = new FileOutputStream(path);
            out.write(body.getBytes("UTF-8"));
            out.flush();
            return true;
        } catch (Throwable t) {
            return false;
        } finally {
            try {
                if (out != null) out.close();
            } catch (Throwable t) {
            }
        }
    }

    public static void log(String s) {
        try {
            ensureDir();
            FileOutputStream out = new FileOutputStream(DEBUG, true);
            String line = new java.text.SimpleDateFormat("MM-dd HH:mm:ss").format(new java.util.Date()) + " " + s + "\n";
            out.write(line.getBytes("UTF-8"));
            out.close();
            if (new File(DEBUG).length() > 200000) new File(DEBUG).delete();
        } catch (Throwable t) {
        }
    }

    public static String cfg(Context c, String k, String d) {
        try {
            SharedPreferences sp = c.getSharedPreferences("nightbell", Context.MODE_PRIVATE);
            return sp.getString(k, d);
        } catch (Throwable t) {
            return d;
        }
    }

    public static void setCfg(Context c, String k, String v) {
        try {
            c.getSharedPreferences("nightbell", Context.MODE_PRIVATE).edit().putString(k, v).commit();
        } catch (Throwable t) {
        }
    }
}