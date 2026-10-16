package com.zaka.xinghai.subtitle;

import com.zaka.xinghai.net.Http;
import com.zaka.xinghai.util.U;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

/**
 * 在线字幕源：公开接口，免登录。
 * 搜索 -> 下载 -> 转 UTF-8 -> 落盘缓存 -> 自绘字幕层渲染。
 */
public final class SubSearch {

    private static final String API = "https://api-shoulei-ssl.xunlei.com/oracle/subtitle";

    private SubSearch() {
    }

    public static class Item {
        public String name = "";
        public String simple = "";
        public String url = "";
        public String ext = "srt";
        public String lang = "";
        public long duration = 0L;
        public int score = 0;

        public String meta() {
            StringBuilder sb = new StringBuilder();
            if (!U.empty(ext)) sb.append(ext.toUpperCase());
            if (duration > 0) {
                if (sb.length() > 0) sb.append("  ·  ");
                sb.append(U.time(duration));
            }
            if (score > 0) {
                if (sb.length() > 0) sb.append("  ·  ");
                sb.append("评分 ").append(score);
            }
            if (!U.empty(lang)) {
                if (sb.length() > 0) sb.append("  ·  ");
                sb.append(lang);
            }
            return sb.toString();
        }

        public JSONObject toJson() {
            JSONObject o = new JSONObject();
            try {
                o.put("name", name);
                o.put("simple", simple);
                o.put("url", url);
                o.put("ext", ext);
                o.put("lang", lang);
                o.put("duration", duration);
                o.put("score", score);
            } catch (Throwable ignored) {
            }
            return o;
        }

        public static Item from(JSONObject o) {
            if (o == null) return null;
            Item it = new Item();
            it.name = o.optString("name", "");
            it.simple = o.optString("simple", "");
            it.url = o.optString("url", "");
            it.ext = o.optString("ext", "srt");
            it.lang = o.optString("lang", "");
            it.duration = o.optLong("duration", 0L);
            it.score = o.optInt("score", 0);
            return U.empty(it.url) ? null : it;
        }
    }

    public static List<Item> search(String keyword) throws Exception {
        String kw = U.cleanName(keyword);
        if (U.empty(kw)) return new ArrayList<Item>();
        List<Item> hit = SubStore.hit(kw);
        if (hit != null) return hit;
        String url = API + "?name=" + U.urlEncode(kw) + "&size=";
        String body = Http.get(url, null, 12000);
        List<Item> out = parse(body, kw);
        if (out.size() > 0) SubStore.put(kw, out);
        return out;
    }

    /**
     * 降级搜索：完整片名 -> 主标题 -> 类型。
     * 带「第 2 季 / S01」这类后缀直接搜往往打不中，降一级命中率明显上来。
     */
    public static List<Item> searchSmart(String fullName, String type) {
        List<String> kws = new ArrayList<String>();
        String base = U.cleanName(fullName);
        if (!U.empty(base)) kws.add(base);
        String main = mainTitle(base);
        if (!U.empty(main) && !kws.contains(main)) kws.add(main);
        if (!U.empty(type)) {
            String t = U.cleanName(type);
            if (!U.empty(t) && t.length() >= 2 && !kws.contains(t)) kws.add(t);
        }
        for (String kw : kws) {
            try {
                List<Item> r = search(kw);
                if (r != null && r.size() > 0) return r;
            } catch (Throwable ignored) {
            }
        }
        return new ArrayList<Item>();
    }

    private static String mainTitle(String name) {
        if (U.empty(name)) return "";
        String s = name;
        s = s.replaceAll("(?i)\\s*season\\s*\\d+\\s*$", "");
        s = s.replaceAll("(?i)\\s*s\\d{1,2}\\s*$", "");
        s = s.replaceAll("\\s*第\\s*[一二三四五六七八九十\\d]+\\s*[季部集话]\\s*$", "");
        s = s.replaceAll("[（(]\\s*[^）)]{0,10}[）)]\\s*$", "");
        return s.trim();
    }

    public static List<Item> parse(String body, final String keyword) throws Exception {
        List<Item> out = new ArrayList<Item>();
        if (U.empty(body)) return out;
        JSONObject root = new JSONObject(body.trim());
        JSONArray arr = root.optJSONArray("data");
        if (arr == null) {
            JSONArray alt = root.optJSONArray("list");
            arr = alt;
        }
        if (arr == null) return out;
        HashSet<String> seen = new HashSet<String>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            String u = o.optString("url", "");
            if (U.empty(u) || !seen.add(u)) continue;
            Item it = new Item();
            it.url = u;
            it.name = o.optString("name", "");
            it.simple = o.optString("simple_name", "");
            if (U.empty(it.name)) it.name = it.simple;
            if (U.empty(it.name)) it.name = "subtitle";
            it.ext = o.optString("ext", "srt");
            it.duration = o.optLong("duration", 0L);
            it.score = o.optInt("score", 0);
            JSONArray la = o.optJSONArray("languages");
            if (la != null && la.length() > 0) it.lang = la.optString(0, "");
            out.add(it);
        }
        Collections.sort(out, new Comparator<Item>() {
            @Override
            public int compare(Item a, Item b) {
                int ra = rank(a, keyword), rb = rank(b, keyword);
                if (ra != rb) return ra - rb;
                return a.name.compareTo(b.name);
            }
        });
        return out;
    }

    private static int rank(Item it, String kw) {
        int r = 0;
        String n = it.name == null ? "" : it.name;
        String s = it.simple == null ? "" : it.simple;
        if (n.contains("弹幕")) r += 40;
        if (n.contains("机器") || n.contains("机翻")) r += 30;
        if (!U.empty(kw) && s.contains(kw)) r -= 5;
        if (n.length() > 90) r += 5;
        if ("zh".equalsIgnoreCase(it.lang)) r -= 2;
        return r;
    }

    /** 下载字幕文本（带缓存，自动处理编码） */
    public static String fetchText(Item it) throws Exception {
        if (it == null || U.empty(it.url)) throw new RuntimeException("empty url");
        String cached = SubStore.readText(it.url);
        if (cached != null && cached.length() > 0) return cached;
        byte[] raw = Http.getBytes(it.url, Http.headers("Referer", "https://www.xunlei.com/"), 20000);
        String text = Http.decodeText(raw);
        if (!U.empty(text)) SubStore.saveText(it.url, text);
        return text;
    }

    /** 下载并解析成字幕条目 */
    public static List<Cue> load(Item it) throws Exception {
        String text = fetchText(it);
        return SubParse.parse(text, it == null ? "srt" : it.ext);
    }
}
