package com.zaka.xinghai.model;

import com.zaka.xinghai.util.U;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** 采集站点（接口）。 */
public class Site {

    public String key = "";
    public String name = "";
    /** 采集接口地址，苹果 CMS 格式 */
    public String api = "";
    /** 接口类型：json / xml */
    public String type = "json";
    public String ext = "";
    public String ua = "";
    public boolean enabled = true;
    /** 该站是否走去广告 */
    public boolean adFilter = true;
    /** 最近一次测速耗时 ms，0 = 没测过 */
    public long speed = 0L;

    public Site() {
    }

    public Site(String name, String api) {
        this.name = name;
        this.api = api;
        this.key = U.md5(api);
    }

    /** TVBox: 0/1=cms 3=csp/jar 跳过；api 以 .js/.py 结尾视为脚本站 */
    public boolean isScript() {
        String a = api == null ? "" : api.toLowerCase();
        String tp = type == null ? "" : type.toLowerCase();
        return a.endsWith(".js") || a.endsWith(".py") || "js".equals(tp) || "py".equals(tp)
                || "3".equals(tp) || tp.contains("csp");
    }

    public boolean isJs() {
        String a = api == null ? "" : api.toLowerCase();
        return a.endsWith(".js") || "js".equalsIgnoreCase(type);
    }

    public boolean isPy() {
        String a = api == null ? "" : api.toLowerCase();
        return a.endsWith(".py") || "py".equalsIgnoreCase(type);
    }

    public String typeLabel() {
        if (isJs()) return "JS";
        if (isPy()) return "PY";
        if (isScript()) return "脚本";
        return "CMS";
    }

    public String host() {
        try {
            String a = api;
            int i = a.indexOf("://");
            if (i > 0) a = a.substring(i + 3);
            int j = a.indexOf('/');
            if (j > 0) a = a.substring(0, j);
            return a;
        } catch (Throwable t) {
            return api;
        }
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("key", key);
            o.put("name", name);
            o.put("api", api);
            o.put("type", type);
            o.put("ext", ext);
            o.put("ua", ua);
            o.put("enabled", enabled);
            o.put("adFilter", adFilter);
            o.put("speed", speed);
        } catch (Throwable ignored) {
        }
        return o;
    }

    public static Site from(JSONObject o) {
        Site s = new Site();
        s.key = o.optString("key", "");
        s.name = o.optString("name", "");
        s.api = o.optString("api", "");
        s.type = o.optString("type", "json");
        s.ext = o.optString("ext", "");
        s.ua = o.optString("ua", "");
        s.enabled = o.optBoolean("enabled", true);
        s.adFilter = o.optBoolean("adFilter", true);
        s.speed = o.optLong("speed", 0L);
        if (s.key.length() == 0) s.key = U.md5(s.api);
        return s;
    }

    public static List<Site> defaults() {
        List<Site> list = new ArrayList<Site>();
        list.add(new Site("电影天堂资源", "https://caiji.dyttzyapi.com/api.php/provide/vod/"));
        list.add(new Site("量子资源", "https://cj.lziapi.com/api.php/provide/vod/"));
        list.add(new Site("百度云资源", "https://api.apibdzy.com/api.php/provide/vod/"));
        return list;
    }
}
