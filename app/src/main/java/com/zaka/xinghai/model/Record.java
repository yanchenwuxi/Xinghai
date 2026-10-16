package com.zaka.xinghai.model;

import org.json.JSONObject;

/** 播放记录 / 收藏。 */
public class Record {

    public String id = "";
    public String name = "";
    public String pic = "";
    public String note = "";
    public String siteKey = "";
    public String siteName = "";
    public int flagIndex = 0;
    public int epIndex = 0;
    public String flagName = "";
    public String epName = "";
    public String epUrl = "";
    /** 播放进度 ms */
    public long position = 0L;
    public long duration = 0L;
    /** 最后观看时间戳 */
    public long time = 0L;
    public boolean collect = false;

    public float percent() {
        if (duration <= 0) return 0f;
        return Math.max(0f, Math.min(1f, position * 1f / duration));
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("id", id);
            o.put("name", name);
            o.put("pic", pic);
            o.put("note", note);
            o.put("siteKey", siteKey);
            o.put("siteName", siteName);
            o.put("flagIndex", flagIndex);
            o.put("epIndex", epIndex);
            o.put("flagName", flagName);
            o.put("epName", epName);
            o.put("epUrl", epUrl);
            o.put("position", position);
            o.put("duration", duration);
            o.put("time", time);
            o.put("collect", collect);
        } catch (Throwable ignored) {
        }
        return o;
    }

    public static Record from(JSONObject o) {
        Record r = new Record();
        r.id = o.optString("id", "");
        r.name = o.optString("name", "");
        r.pic = o.optString("pic", "");
        r.note = o.optString("note", "");
        r.siteKey = o.optString("siteKey", "");
        r.siteName = o.optString("siteName", "");
        r.flagIndex = o.optInt("flagIndex", 0);
        r.epIndex = o.optInt("epIndex", 0);
        r.flagName = o.optString("flagName", "");
        r.epName = o.optString("epName", "");
        r.epUrl = o.optString("epUrl", "");
        r.position = o.optLong("position", 0L);
        r.duration = o.optLong("duration", 0L);
        r.time = o.optLong("time", 0L);
        r.collect = o.optBoolean("collect", false);
        return r;
    }

    public String key() {
        return siteKey + "#" + id;
    }
}
