package com.zaka.xinghai.net;

import com.zaka.xinghai.model.Site;
import com.zaka.xinghai.util.U;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 接口订阅配置解析：TVBox 那套 JSON（含 sites 数组）和 XML 两种都吃。 */
public final class Cfg {

    private Cfg() {
    }

    public static List<Site> load(String content, String baseUrl) {
        List<Site> out = new ArrayList<Site>();
        if (U.empty(content)) return out;
        String s = content.trim();
        try {
            if (s.startsWith("{")) {
                JSONObject root = new JSONObject(s);
                JSONArray arr = root.optJSONArray("sites");
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.optJSONObject(i);
                        if (o == null) continue;
                        String api = o.optString("api", o.optString("ext", ""));
                        String name = o.optString("name", "");
                        String type = o.optString("type", "1");
                        // type 3 = 外部 jar 的 csp 站点，纯自研不跑 jar，跳过
                        if ("3".equals(type) || "4".equals(type)) continue;
                        if (U.empty(api) || (!api.startsWith("http") && !api.startsWith("csp"))) continue;
                        if (api.startsWith("csp")) continue;
                        if (api.contains("$") && !api.startsWith("http")) continue;
                        Site site = new Site(U.empty(name) ? "接口" + (out.size() + 1) : name, api);
                        site.ext = o.optString("ext", "");
                        out.add(site);
                    }
                }
            } else if (s.startsWith("<")) {
                Matcher m = Pattern.compile("<site\\s+([^>]*)/?>").matcher(s);
                while (m.find()) {
                    String attrs = m.group(1);
                    String api = attr(attrs, "api");
                    String name = attr(attrs, "name");
                    if (U.empty(api) || !api.startsWith("http")) continue;
                    out.add(new Site(U.empty(name) ? "接口" + (out.size() + 1) : name, api));
                }
                if (out.size() == 0) {
                    Matcher m2 = Pattern.compile("<api\\s+([^>]*)/?>").matcher(s);
                    while (m2.find()) {
                        String api = attr(m2.group(1), "api");
                        String name = attr(m2.group(1), "name");
                        if (U.empty(api) || !api.startsWith("http")) continue;
                        out.add(new Site(U.empty(name) ? "接口" + (out.size() + 1) : name, api));
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static String attr(String attrs, String key) {
        Matcher m = Pattern.compile(key + "\\s*=\\s*\"([^\"]*)\"").matcher(attrs);
        if (m.find()) return m.group(1);
        return "";
    }

    /** 判断是不是一个配置地址 */
    public static boolean looksLikeCfg(String url) {
        if (U.empty(url)) return false;
        String u = url.toLowerCase();
        return u.endsWith(".json") || u.endsWith(".xml") || u.startsWith("http");
    }
}
