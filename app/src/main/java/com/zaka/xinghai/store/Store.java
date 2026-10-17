package com.zaka.xinghai.store;

import android.content.Context;
import android.content.SharedPreferences;

import com.zaka.xinghai.model.Record;
import com.zaka.xinghai.model.Site;
import com.zaka.xinghai.util.U;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** 本地存储：站点、当前站点、历史、收藏、搜索历史、各项设置。 */
public final class Store {

    private static final String SP = "xinghai";
    private static SharedPreferences sp;

    private Store() {
    }

    public static void init(Context ctx) {
        sp = ctx.getApplicationContext().getSharedPreferences(SP, Context.MODE_PRIVATE);
        if (!sp.contains("sites")) {
            saveSites(Site.defaults());
            List<Site> l = sites();
            if (l.size() > 0) sp.edit().putString("site_current", l.get(0).key).apply();
        }
    }

    private static SharedPreferences p() {
        return sp;
    }

    // ------------------------------------------------------------ 站点
    public static List<Site> sites() {
        List<Site> out = new ArrayList<Site>();
        String s = p().getString("sites", "");
        if (U.empty(s)) return out;
        try {
            JSONArray arr = new JSONArray(s);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null) out.add(Site.from(o));
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    public static void saveSites(List<Site> list) {
        JSONArray arr = new JSONArray();
        for (Site s : list) arr.put(s.toJson());
        p().edit().putString("sites", arr.toString()).apply();
    }

    public static Site site(String key) {
        for (Site s : sites()) if (s.key.equals(key)) return s;
        return null;
    }

    public static String currentKey() {
        List<Site> l = sites();
        String k = p().getString("site_current", "");
        if (k.length() > 0 && site(k) != null) return k;
        return l.size() > 0 ? l.get(0).key : "";
    }

    public static Site currentSite() {
        Site s = site(currentKey());
        if (s == null) {
            List<Site> l = sites();
            s = l.size() > 0 ? l.get(0) : null;
        }
        return s;
    }

    public static void setCurrent(String key) {
        p().edit().putString("site_current", key).apply();
    }

    // ------------------------------------------------------------ 历史 / 收藏
    public static List<Record> records(boolean collect) {
        List<Record> out = new ArrayList<Record>();
        String s = p().getString(collect ? "collect" : "history", "");
        if (U.empty(s)) return out;
        try {
            JSONArray arr = new JSONArray(s);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null) out.add(Record.from(o));
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static void saveRecords(List<Record> list, boolean collect) {
        JSONArray arr = new JSONArray();
        for (Record r : list) arr.put(r.toJson());
        p().edit().putString(collect ? "collect" : "history", arr.toString()).apply();
    }

    /** 记录播放进度（同时刷新到历史最前） */
    public static void putHistory(Record r) {
        if (r == null || U.empty(r.name)) return;
        List<Record> list = records(false);
        List<Record> out = new ArrayList<Record>();
        out.add(r);
        for (Record o : list) {
            if (o.key().equals(r.key()) && o.epIndex == r.epIndex) continue;
            if (o.key().equals(r.key())) continue;
            out.add(o);
        }
        int max = historyMax();
        while (out.size() > max) out.remove(out.size() - 1);
        saveRecords(out, false);
    }

    /** 只更新进度，不打乱顺序（暂停/退出时调用） */
    public static void updatePosition(Record r) {
        if (r == null) return;
        List<Record> list = records(false);
        for (Record o : list) {
            if (o.key().equals(r.key()) && o.epIndex == r.epIndex) {
                o.position = r.position;
                o.duration = r.duration;
                o.time = System.currentTimeMillis();
                saveRecords(list, false);
                return;
            }
        }
    }

    public static Record findHistory(String key, int epIndex) {
        for (Record o : records(false)) {
            if (o.key().equals(key) && o.epIndex == epIndex) return o;
        }
        return null;
    }

    public static void removeHistory(String key) {
        List<Record> out = new ArrayList<Record>();
        for (Record o : records(false)) if (!o.key().equals(key)) out.add(o);
        saveRecords(out, false);
    }

    public static void clearHistory() {
        saveRecords(new ArrayList<Record>(), false);
    }

    public static boolean isCollected(String key) {
        for (Record o : records(true)) if (o.key().equals(key)) return true;
        return false;
    }

    public static void collect(Record r) {
        if (r == null) return;
        List<Record> list = records(true);
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).key().equals(r.key())) {
                list.remove(i);
                break;
            }
        }
        r.collect = true;
        r.time = System.currentTimeMillis();
        list.add(0, r);
        saveRecords(list, true);
    }

    public static void uncollect(String key) {
        List<Record> out = new ArrayList<Record>();
        for (Record o : records(true)) if (!o.key().equals(key)) out.add(o);
        saveRecords(out, true);
    }

    public static void clearCollect() {
        saveRecords(new ArrayList<Record>(), true);
    }

    // ------------------------------------------------------------ 搜索历史
    public static List<String> searchHistory() {
        List<String> out = new ArrayList<String>();
        String s = p().getString("search_history", "");
        if (U.empty(s)) return out;
        try {
            JSONArray arr = new JSONArray(s);
            for (int i = 0; i < arr.length(); i++) out.add(arr.optString(i));
        } catch (Throwable ignored) {
        }
        return out;
    }

    public static void putSearch(String kw) {
        if (U.empty(kw)) return;
        List<String> list = searchHistory();
        list.remove(kw);
        list.add(0, kw);
        while (list.size() > 20) list.remove(list.size() - 1);
        JSONArray arr = new JSONArray();
        for (String s : list) arr.put(s);
        p().edit().putString("search_history", arr.toString()).apply();
    }

    public static void clearSearch() {
        p().edit().putString("search_history", "").apply();
    }

    // ------------------------------------------------------------ 设置
    public static boolean adFilter() {
        return p().getBoolean("ad_filter", true);
    }

    public static void setAdFilter(boolean v) {
        p().edit().putBoolean("ad_filter", v).apply();
    }

    /** 播放加速（本地代理 + 分片缓存） */
    public static boolean boost() {
        return p().getBoolean("boost", true);
    }

    public static void setBoost(boolean v) {
        p().edit().putBoolean("boost", v).apply();
    }

    public static boolean subEnabled() {
        return p().getBoolean("sub_on", true);
    }

    public static void setSubEnabled(boolean v) {
        p().edit().putBoolean("sub_on", v).apply();
    }

    public static boolean subAuto() {
        return p().getBoolean("sub_auto", true);
    }

    public static void setSubAuto(boolean v) {
        p().edit().putBoolean("sub_auto", v).apply();
    }

    /** 字幕时间轴偏移（毫秒，正数=字幕往后挪） */
    public static long subOffset() {
        return p().getLong("sub_offset", 0L);
    }

    public static void setSubOffset(long v) {
        p().edit().putLong("sub_offset", v).apply();
    }

    /** 字幕字号 */
    public static int subSize() {
        return p().getInt("sub_size", 16);
    }

    public static void setSubSize(int v) {
        p().edit().putInt("sub_size", v).apply();
    }

    /** 解码方式：0 硬解 1 软解 */
    public static int decode() {
        return p().getInt("decode", 0);
    }

    public static void setDecode(int v) {
        p().edit().putInt("decode", v).apply();
    }

    /** 画面比例：0 自适应 1 16:9 2 4:3 3 铺满 4 原始 */
    public static int scale() {
        return p().getInt("scale", 0);
    }

    public static void setScale(int v) {
        p().edit().putInt("scale", v).apply();
    }

    public static float speed() {
        return p().getFloat("speed", 1.0f);
    }

    public static void setSpeed(float v) {
        p().edit().putFloat("speed", v).apply();
    }

    public static int historyMax() {
        return p().getInt("history_max", 100);
    }

    public static void setHistoryMax(int v) {
        p().edit().putInt("history_max", v).apply();
    }

    /** 播放超时（秒），到点没起播自动换线 */
    public static int playTimeout() {
        return p().getInt("play_timeout", 9);
    }

    public static void setPlayTimeout(int v) {
        p().edit().putInt("play_timeout", v).apply();
    }

    /** 片头片尾设置（秒） */
    public static long head() {
        return p().getLong("head", 0L);
    }

    public static void setHead(long v) {
        p().edit().putLong("head", v).apply();
    }

    public static long tail() {
        return p().getLong("tail", 0L);
    }

    public static void setTail(long v) {
        p().edit().putLong("tail", v).apply();
    }

    public static String nick() {
        return p().getString("nick", "星海用户");
    }

    // ------------------------------------------------------------ 直播源
    public static String liveUrl() {
        return p().getString("live_url", "");
    }

    public static void setLiveUrl(String v) {
        p().edit().putString("live_url", v == null ? "" : v.trim()).apply();
    }

    /** 直播源原文缓存（避免每次切页都重新拉） */
    public static String liveRaw() {
        return p().getString("live_raw", "");
    }

    public static void setLiveRaw(String v) {
        p().edit().putString("live_raw", v == null ? "" : v).apply();
    }

    public static void setNick(String v) {
        p().edit().putString("nick", v).apply();
    }

    /** 已看过的剧集标记，形如 "key#flag#ep" */
    public static boolean watched(String k) {
        return p().getStringSet("watched", new java.util.HashSet<String>()).contains(k);
    }

    public static void markWatched(String k) {
        java.util.Set<String> set = new java.util.HashSet<String>(
                p().getStringSet("watched", new java.util.HashSet<String>()));
        set.add(k);
        p().edit().putStringSet("watched", set).apply();
    }

    /** 缓存目录清理 */
    public static long cacheSize(java.io.File dir) {
        long n = 0;
        if (dir == null || !dir.exists()) return 0;
        java.io.File[] fs = dir.listFiles();
        if (fs == null) return 0;
        for (java.io.File f : fs) n += f.isDirectory() ? cacheSize(f) : f.length();
        return n;
    }

    public static void clearCache(java.io.File dir) {
        if (dir == null || !dir.exists()) return;
        java.io.File[] fs = dir.listFiles();
        if (fs == null) return;
        for (java.io.File f : fs) {
            if (f.isDirectory()) clearCache(f);
            f.delete();
        }
    }

    public static void sortByTime(List<Record> list) {
        Collections.sort(list, new Comparator<Record>() {
            @Override
            public int compare(Record a, Record b) {
                return Long.compare(b.time, a.time);
            }
        });
    }

    public static String cfgUrl() {
        return p().getString("cfg_url", "");
    }

    public static void setCfgUrl(String v) {
        p().edit().putString("cfg_url", v == null ? "" : v.trim()).apply();
    }

}
