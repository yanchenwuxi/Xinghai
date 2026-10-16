package com.zaka.xinghai.util;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.zaka.xinghai.core.App;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.util.List;
import java.util.Locale;

/** 一堆杂活：单位换算、格式化、文本清洗、颜色处理。 */
public final class U {

    private static float density = 2.0f;

    private U() {
    }

    public static void init(Resources r) {
        density = r.getDisplayMetrics().density;
    }

    public static int dp(float v) {
        return (int) (v * density + 0.5f);
    }

    public static float dip(float v) {
        return v * density;
    }

    public static void toast(String s) {
        if (empty(s)) return;
        Toast.makeText(App.ctx(), s, Toast.LENGTH_SHORT).show();
    }

    public static boolean empty(String s) {
        return s == null || s.length() == 0 || "null".equalsIgnoreCase(s.trim());
    }

    public static String s(String v) {
        return v == null ? "" : v.trim();
    }

    /** 毫秒 -> hh:mm:ss / mm:ss */
    public static String time(long ms) {
        if (ms < 0) ms = 0;
        long total = ms / 1000;
        long h = total / 3600, m = (total % 3600) / 60, sec = total % 60;
        if (h > 0) return String.format(Locale.US, "%d:%02d:%02d", h, m, sec);
        return String.format(Locale.US, "%02d:%02d", m, sec);
    }

    /** 秒 -> hh:mm:ss（片头片尾用） */
    public static long sec(String v) {
        try {
            String[] p = v.trim().split(":");
            if (p.length == 1) return (long) (Double.parseDouble(p[0]) * 1000);
            if (p.length == 2) return (Long.parseLong(p[0]) * 60 + Long.parseLong(p[1])) * 1000L;
            return (Long.parseLong(p[0]) * 3600 + Long.parseLong(p[1]) * 60 + Long.parseLong(p[2])) * 1000L;
        } catch (Throwable t) {
            return 0L;
        }
    }

    public static String size(long bytes) {
        if (bytes < 1024) return bytes + "B";
        if (bytes < 1024 * 1024) return String.format(Locale.US, "%.1fKB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format(Locale.US, "%.1fMB", bytes / 1048576.0);
        return String.format(Locale.US, "%.2fGB", bytes / 1073741824.0);
    }

    public static String urlEncode(String s) {
        try {
            return URLEncoder.encode(s == null ? "" : s, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return s == null ? "" : s;
        }
    }

    public static String urlDecode(String s) {
        try {
            return URLDecoder.decode(s == null ? "" : s, "UTF-8");
        } catch (Throwable e) {
            return s;
        }
    }

    public static String md5(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] b = md.digest(s.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte x : b) sb.append(String.format(Locale.US, "%02x", x));
            return sb.toString();
        } catch (Throwable t) {
            return String.valueOf(s == null ? 0 : s.hashCode());
        }
    }

    /** 片名清洗：去掉清晰度、集数后缀、括号注释等，用于搜字幕/搜同款 */
    public static String cleanName(String name) {
        if (empty(name)) return "";
        String s = name;
        s = s.replaceAll("(?i)\\.(mp4|mkv|avi|rmvb|ts|m3u8|flv|wmv|mov|webm)$", "");
        s = s.replaceAll("【[^】]*】", " ");
        s = s.replaceAll("\\[[^\\]]*]", " ");
        s = s.replaceAll("（[^）]*）", " ");
        s = s.replaceAll("\\([^)]*\\)", " ");
        s = s.replaceAll("(?i)\\b(1080p|720p|2160p|4k|hdr|bluray|web-?dl|bd|hd|hdtv|remux)\\b", " ");
        s = s.replaceAll("(第\\s*\\d+\\s*[季部集话话]|S\\d{1,2}E\\d{1,3}|EP?\\d{1,3})", " ");
        s = s.replaceAll("[._]+", " ");
        s = s.replaceAll("\\s{2,}", " ").trim();
        return s;
    }

    public static boolean netOk() {
        try {
            ConnectivityManager cm = (ConnectivityManager) App.ctx()
                    .getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return true;
            NetworkInfo info = cm.getActiveNetworkInfo();
            return info != null && info.isConnected();
        } catch (Throwable t) {
            return true;
        }
    }

    public static int color(String hex) {
        try {
            return Color.parseColor(hex);
        } catch (Throwable t) {
            return Color.WHITE;
        }
    }

    public static void tint(Drawable d, int color) {
        if (d != null) d.setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN);
    }

    public static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        return bos.toByteArray();
    }

    public static String join(List<String> list, String sep) {
        StringBuilder sb = new StringBuilder();
        for (String s : list) {
            if (sb.length() > 0) sb.append(sep);
            sb.append(s);
        }
        return sb.toString();
    }

    public static boolean contains(String src, String sub) {
        return src != null && sub != null && sub.length() > 0 && src.contains(sub);
    }

    public static TextView text(Context ctx, String s, int sizeSp, int color) {
        TextView tv = new TextView(ctx);
        tv.setText(s);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        tv.setTextColor(color);
        tv.setSingleLine(true);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        tv.setGravity(Gravity.CENTER_VERTICAL);
        return tv;
    }

    public static TextView bold(TextView tv) {
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        return tv;
    }

    public static LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }
}
