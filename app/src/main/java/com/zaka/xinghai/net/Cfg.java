package com.zaka.xinghai.net;

import com.zaka.xinghai.model.Site;
import com.zaka.xinghai.util.U;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 接口订阅配置解析：TVBox JSON（sites）/ XML；识别 CMS / JS / PY。 */
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
                if (arr == null) arr = root.optJSONArray("site");
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.optJSONObject(i);
                        if (o == null) continue;
                        Site site = fromTvBox(o);
                        if (site != null) out.add(site);
                    }
                }
            } else if (s.startsWith("[")) {
                JSONArray arr = new JSONArray(s);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.optJSONObject(i);
                    if (o == null) continue;
                    Site site = fromTvBox(o);
                    if (site != null) out.add(site);
                }
            } else if (s.startsWith("<")) {
                Matcher m = Pattern.compile("<site\\s+([^>]*)/?>").matcher(s);
                while (m.find()) {
                    String attrs = m.group(1);
                    String api = attr(attrs, "api");
                    String name = attr(attrs, "name");
                    if (U.empty(api) || !api.startsWith("http")) continue;
                    Site site = new Site(U.empty(name) ? "接口" + (out.size() + 1) : name, api);
                    tagScript(site);
                    out.add(site);
                }
            } else if (s.startsWith("http")) {
                // 单行纯地址
                Site site = new Site("导入接口", s.split("\\s+")[0].trim());
                tagScript(site);
                out.add(site);
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static Site fromTvBox(JSONObject o) {
        String api = o.optString("api", "");
        if (U.empty(api)) api = o.optString("ext", "");
        String name = o.optString("name", "");
        String type = String.valueOf(o.opt("type"));
        // jar/csp 无 http 的无法在纯自研端运行
        if (api.startsWith("csp_") || api.startsWith("csp:")) return null;
        if (!api.startsWith("http") && !api.startsWith("./") && !api.startsWith("/")) return null;
        // 相对路径拼不了 base 时跳过
        if (!api.startsWith("http")) return null;
        Site site = new Site(U.empty(name) ? "接口" : name, api);
        site.ext = o.optString("ext", "");
        site.ua = o.optString("header", o.optString("ua", ""));
        if ("3".equals(type) || type.contains("Spider") || type.contains("spider")) {
            // 可能是挂了 jar 的 http 壳，仍标记
            site.type = "csp";
        }
        tagScript(site);
        if (site.isJs()) site.type = "js";
        else if (site.isPy()) site.type = "py";
        else if (!"csp".equals(site.type)) site.type = "cms";
        return site;
    }

    private static void tagScript(Site site) {
        String a = site.api.toLowerCase();
        if (a.contains(".js")) site.type = "js";
        else if (a.contains(".py")) site.type = "py";
    }

    private static String attr(String attrs, String key) {
        Matcher m = Pattern.compile(key + "\\s*=\\s*\"([^\"]*)\"").matcher(attrs);
        if (m.find()) return m.group(1);
        return "";
    }

    public static boolean looksLikeCfg(String url) {
        if (U.empty(url)) return false;
        String u = url.toLowerCase();
        return u.endsWith(".json") || u.endsWith(".xml") || u.endsWith(".txt") || u.startsWith("http");
    }
}
