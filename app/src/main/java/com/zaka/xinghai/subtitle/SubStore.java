package com.zaka.xinghai.subtitle;

import android.content.Context;

import com.zaka.xinghai.util.U;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/** 字幕本地缓存：下过的字幕落盘，同一部片第二次打开直接命中；搜索结果也缓存。 */
public final class SubStore {

    private static File dir;
    private static File searchDir;

    private SubStore() {
    }

    public static void init(Context ctx) {
        dir = new File(ctx.getCacheDir(), "sub");
        searchDir = new File(ctx.getCacheDir(), "subsearch");
        if (!dir.exists()) dir.mkdirs();
        if (!searchDir.exists()) searchDir.mkdirs();
    }

    public static File dir() {
        return dir;
    }

    private static File file(String url) {
        return new File(dir, U.md5(url) + ".txt");
    }

    public static String readText(String url) {
        try {
            File f = file(url);
            if (!f.exists() || f.length() == 0) return null;
            FileInputStream in = new FileInputStream(f);
            byte[] b = U.readAll(in);
            in.close();
            return new String(b, "UTF-8");
        } catch (Throwable t) {
            return null;
        }
    }

    public static void saveText(String url, String text) {
        try {
            FileOutputStream fos = new FileOutputStream(file(url));
            fos.write(text.getBytes("UTF-8"));
            fos.close();
        } catch (Throwable ignored) {
        }
    }

    public static void remove(String url) {
        file(url).delete();
    }

    // ------------------------------------------------------------ 搜索结果缓存
    public static List<SubSearch.Item> hit(String kw) {
        try {
            File f = new File(searchDir, U.md5(kw) + ".json");
            if (!f.exists()) return null;
            if (System.currentTimeMillis() - f.lastModified() > 12 * 3600 * 1000L) return null;
            FileInputStream in = new FileInputStream(f);
            byte[] b = U.readAll(in);
            in.close();
            JSONArray arr = new JSONArray(new String(b, "UTF-8"));
            List<SubSearch.Item> out = new ArrayList<SubSearch.Item>();
            for (int i = 0; i < arr.length(); i++) {
                SubSearch.Item it = SubSearch.Item.from(arr.optJSONObject(i));
                if (it != null) out.add(it);
            }
            return out;
        } catch (Throwable t) {
            return null;
        }
    }

    public static void put(String kw, List<SubSearch.Item> list) {
        try {
            JSONArray arr = new JSONArray();
            for (SubSearch.Item it : list) arr.put(it.toJson());
            FileOutputStream fos = new FileOutputStream(new File(searchDir, U.md5(kw) + ".json"));
            fos.write(arr.toString().getBytes("UTF-8"));
            fos.close();
        } catch (Throwable ignored) {
        }
    }

    public static long size() {
        return size(dir) + size(searchDir);
    }

    private static long size(File d) {
        long n = 0;
        if (d == null || !d.exists()) return 0;
        File[] fs = d.listFiles();
        if (fs == null) return 0;
        for (File f : fs) n += f.length();
        return n;
    }

    public static void clear() {
        clear(dir);
        clear(searchDir);
    }

    private static void clear(File d) {
        if (d == null || !d.exists()) return;
        File[] fs = d.listFiles();
        if (fs != null) for (File f : fs) f.delete();
    }

    /** 已缓存的字幕文件（"本地字幕"里列出来用） */
    public static List<String> localNames() {
        List<String> out = new ArrayList<String>();
        if (dir == null) return out;
        File[] fs = dir.listFiles();
        if (fs == null) return out;
        for (File f : fs) out.add(f.getName());
        return out;
    }
}
