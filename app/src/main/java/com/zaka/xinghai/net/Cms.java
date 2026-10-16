package com.zaka.xinghai.net;

import com.zaka.xinghai.model.Cate;
import com.zaka.xinghai.model.Filter;
import com.zaka.xinghai.model.Site;
import com.zaka.xinghai.model.Vod;
import com.zaka.xinghai.util.U;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 采集接口解析（苹果 CMS 通用格式）。
 * 分类、列表、详情、搜索四条链路，JSON / XML 两种返回都吃。
 * 关键坑：很多源直接请求 ac=list 返回的是数据列表不是分类，
 * 分类要从 ac=list&t=1 的 class 字段拿，拿不到再从列表数据的 type_id 聚合兜底。
 */
public final class Cms {

    private static final Map<String, List<Cate>> cateCache = new LinkedHashMap<String, List<Cate>>();

    private Cms() {
    }

    // ------------------------------------------------------------ 分类
    public static List<Cate> types(Site site) throws Exception {
        List<Cate> cached = cateCache.get(site.key);
        long now = System.currentTimeMillis();
        if (cached != null && cached.size() > 0 && cacheTime.containsKey(site.key)
                && now - cacheTime.get(site.key) < 30 * 60 * 1000L) {
            return cached;
        }
        String base = api(site);
        List<Cate> out = new ArrayList<Cate>();
        // 1) ac=list&t=1：class 字段里是分类
        try {
            String body = Http.get(base + "?ac=list&t=1", ua(site), 10000);
            out = parseTypes(body);
        } catch (Throwable ignored) {
        }
        // 2) ac=list 另一种写法
        if (out.size() == 0) {
            try {
                String body = Http.get(base + "?ac=list", ua(site), 10000);
                out = parseTypes(body);
                if (out.size() == 0) out = aggregateTypes(body);
            } catch (Throwable ignored) {
            }
        }
        // 3) 从首页列表数据聚合
        if (out.size() == 0) {
            try {
                String body = Http.get(base + "?ac=videolist&pg=1", ua(site), 10000);
                out = aggregateTypes(body);
            } catch (Throwable ignored) {
            }
        }
        if (out.size() > 0) {
            cateCache.put(site.key, out);
            cacheTime.put(site.key, now);
        }
        return out;
    }

    private static final Map<String, Long> cacheTime = new LinkedHashMap<String, Long>();

    public static List<Cate> parseTypes(String body) {
        List<Cate> out = new ArrayList<Cate>();
        if (U.empty(body)) return out;
        String t = body.trim();
        try {
            if (t.startsWith("{")) {
                JSONObject root = new JSONObject(t);
                JSONArray arr = root.optJSONArray("class");
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.optJSONObject(i);
                        if (o == null) continue;
                        String id = o.optString("type_id", "");
                        String name = o.optString("type_name", "");
                        if (id.length() > 0 && name.length() > 0) out.add(new Cate(id, name));
                    }
                }
            } else {
                Matcher m = Pattern.compile("<ty\\s+id=\"(\\d+)\"[^>]*>([^<]+)</ty>").matcher(t);
                while (m.find()) out.add(new Cate(m.group(1), m.group(2).trim()));
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    /** 从列表数据里聚合 type_id / type_name */
    public static List<Cate> aggregateTypes(String body) {
        List<Cate> out = new ArrayList<Cate>();
        if (U.empty(body)) return out;
        try {
            String t = body.trim();
            if (t.startsWith("{")) {
                JSONObject root = new JSONObject(t);
                JSONArray arr = root.optJSONArray("list");
                if (arr == null) return out;
                HashSet<String> seen = new HashSet<String>();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.optJSONObject(i);
                    if (o == null) continue;
                    String id = o.optString("type_id", "");
                    String name = o.optString("type_name", "");
                    if (id.length() > 0 && name.length() > 0 && !seen.contains(id)) {
                        seen.add(id);
                        out.add(new Cate(id, name));
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    // ------------------------------------------------------------ 列表
    public static List<Vod> list(Site site, String typeId, int page, Map<String, String> filters) throws Exception {
        StringBuilder url = new StringBuilder(api(site));
        url.append("?ac=videolist&pg=").append(page);
        if (!U.empty(typeId)) url.append("&t=").append(typeId);
        if (filters != null) {
            for (Map.Entry<String, String> e : filters.entrySet()) {
                if (U.empty(e.getValue())) continue;
                url.append("&").append(e.getKey()).append("=").append(U.urlEncode(e.getValue()));
            }
        }
        String body = Http.get(url.toString(), ua(site), 12000);
        List<Vod> out = parseList(body);
        out = localFilter(out, filters);
        for (Vod v : out) v.siteKey = site.key;
        return out;
    }

    public static List<Vod> parseList(String body) {
        List<Vod> out = new ArrayList<Vod>();
        if (U.empty(body)) return out;
        String t = body.trim();
        try {
            if (t.startsWith("{")) {
                JSONObject root = new JSONObject(t);
                JSONArray arr = root.optJSONArray("list");
                if (arr == null) return out;
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.optJSONObject(i);
                    Vod v = fromJson(o);
                    if (v != null) out.add(v);
                }
            } else if (t.startsWith("<")) {
                Matcher m = Pattern.compile("<video>(.*?)</video>", Pattern.DOTALL).matcher(t);
                while (m.find()) {
                    Vod v = fromXml(m.group(1));
                    if (v != null) out.add(v);
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static Vod fromJson(JSONObject o) {
        if (o == null) return null;
        Vod v = new Vod();
        v.id = o.optString("vod_id", "");
        v.name = o.optString("vod_name", "");
        v.pic = o.optString("vod_pic", "");
        v.note = o.optString("vod_remarks", "");
        v.year = o.optString("vod_year", "");
        v.area = o.optString("vod_area", "");
        v.type = o.optString("type_name", o.optString("vod_class", ""));
        v.actor = o.optString("vod_actor", "");
        v.director = o.optString("vod_director", "");
        v.content = o.optString("vod_content", o.optString("vod_blurb", ""));
        v.score = o.optString("vod_score", "");
        String from = o.optString("vod_play_from", "");
        String urls = o.optString("vod_play_url", "");
        parsePlay(v, from, urls);
        if (U.empty(v.name)) return null;
        return v;
    }

    private static Vod fromXml(String xml) {
        Vod v = new Vod();
        v.id = tag(xml, "id");
        v.name = tag(xml, "name");
        v.pic = tag(xml, "pic");
        v.note = tag(xml, "note");
        v.year = tag(xml, "year");
        v.area = tag(xml, "area");
        v.type = tag(xml, "type");
        v.actor = tag(xml, "actor");
        v.director = tag(xml, "director");
        v.content = tag(xml, "des");
        v.score = tag(xml, "score");
        parsePlay(v, tag(xml, "dl").length() > 0 ? "" : "", "");
        String dl = tag(xml, "dl");
        if (dl.length() > 0) parseXmlPlay(v, dl);
        if (U.empty(v.name)) return null;
        return v;
    }

    private static void parseXmlPlay(Vod v, String dl) {
        try {
            Matcher m = Pattern.compile("<dd\\s+flag=\"([^\"]*)\">(.*?)</dd>", Pattern.DOTALL).matcher(dl);
            while (m.find()) {
                String flag = m.group(1);
                String body = m.group(2);
                List<Vod.Episode> eps = new ArrayList<Vod.Episode>();
                Matcher e = Pattern.compile("<\\!\\[CDATA\\[(.*?)\\]\\]>").matcher(body);
                while (e.find()) {
                    String one = e.group(1);
                    int i = one.indexOf('$');
                    if (i < 0) {
                        eps.add(new Vod.Episode("第" + (eps.size() + 1) + "集", one.trim()));
                    } else {
                        eps.add(new Vod.Episode(one.substring(0, i).trim(), one.substring(i + 1).trim()));
                    }
                }
                if (eps.size() > 0) {
                    v.flags.add(new Vod.Flag(v.flags.size(), U.empty(flag) ? "线路" + (v.flags.size() + 1) : flag));
                    v.episodes.add(eps);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static String tag(String xml, String name) {
        Matcher m = Pattern.compile("<" + name + ">(.*?)</" + name + ">", Pattern.DOTALL).matcher(xml);
        if (m.find()) return m.group(1).replace("<![CDATA[", "").replace("]]>", "").trim();
        return "";
    }

    /** vod_play_from / vod_play_url 拆线路与剧集；顺带做线路名/剧集名广告清洗 */
    public static void parsePlay(Vod v, String from, String urls) {
        if (U.empty(urls)) return;
        String[] groups = urls.split("\\$\\$\\$");
        String[] names = U.empty(from) ? new String[0] : from.split("\\$\\$\\$");
        for (int i = 0; i < groups.length; i++) {
            String g = groups[i];
            if (U.empty(g)) continue;
            List<Vod.Episode> eps = new ArrayList<Vod.Episode>();
            List<Vod.Episode> raw = new ArrayList<Vod.Episode>();
            String[] items = g.split("#");
            for (String it : items) {
                if (U.empty(it)) continue;
                int p = it.indexOf('$');
                String en, eu;
                if (p < 0) {
                    en = "播放";
                    eu = it.trim();
                } else {
                    en = it.substring(0, p).trim();
                    eu = it.substring(p + 1).trim();
                }
                Vod.Episode ep = new Vod.Episode(en, eu);
                raw.add(ep);
                if (!AdFilter.isAdName(en)) eps.add(ep);
            }
            if (eps.size() == 0) eps = raw;
            if (eps.size() == 0) continue;
            String fn = i < names.length ? names[i] : ("线路" + (v.flags.size() + 1));
            if (AdFilter.isAdName(fn)) fn = "线路" + (v.flags.size() + 1);
            v.flags.add(new Vod.Flag(v.flags.size(), U.empty(fn) ? ("线路" + (v.flags.size() + 1)) : fn));
            v.episodes.add(eps);
        }
    }

    // ------------------------------------------------------------ 详情
    public static Vod detail(Site site, Vod v) throws Exception {
        if (v == null) return null;
        String url = api(site) + "?ac=videolist&ids=" + v.id;
        String body = Http.get(url, ua(site), 12000);
        List<Vod> list = parseList(body);
        if (list.size() == 0) return v;
        Vod d = list.get(0);
        if (U.empty(d.pic)) d.pic = v.pic;
        if (U.empty(d.note)) d.note = v.note;
        if (U.empty(d.name)) d.name = v.name;
        d.siteKey = site.key;
        return d;
    }

    // ------------------------------------------------------------ 搜索
    public static List<Vod> search(Site site, String kw) throws Exception {
        String url = api(site) + "?ac=videolist&wd=" + U.urlEncode(kw) + "&pg=1";
        String body = Http.get(url, ua(site), 12000);
        List<Vod> out = parseList(body);
        for (Vod v : out) v.siteKey = site.key;
        return out;
    }

    // ------------------------------------------------------------ 工具
    private static String api(Site site) {
        String a = site.api;
        if (a.endsWith("/")) a = a.substring(0, a.length() - 1);
        return a;
    }

    private static List<Http.Head> ua(Site site) {
        List<Http.Head> h = Http.headers("Accept-Encoding", "gzip, deflate");
        if (!U.empty(site.ua)) h.add(new Http.Head("User-Agent", site.ua));
        return h;
    }

    /** 本机筛选兜底：接口不认筛选参数时，拿回来的数据本地过滤 */
    private static List<Vod> localFilter(List<Vod> list, Map<String, String> filters) {
        if (filters == null || filters.size() == 0 || list.size() == 0) return list;
        List<Vod> out = new ArrayList<Vod>();
        for (Vod v : list) {
            boolean ok = true;
            for (Map.Entry<String, String> e : filters.entrySet()) {
                String val = e.getValue();
                if (U.empty(val) || "全部".equals(val) || "全部地区".equals(val)) continue;
                String k = e.getKey();
                String hay = "class".equals(k) || "t".equals(k) ? (v.type + " " + v.name)
                        : "area".equals(k) ? (v.area + " " + v.note + " " + v.name)
                        : "year".equals(k) ? (v.year + " " + v.note + " " + v.name)
                        : (v.name + " " + v.type + " " + v.note);
                if (!hay.contains(val)) {
                    ok = false;
                    break;
                }
            }
            if (ok) out.add(v);
        }
        // 本地过滤把整页干光了，就返回原列表（宁可多给，不给空白）
        return out.size() == 0 ? list : out;
    }

    /** 生成筛选组（本地兜底用） */
    public static List<Filter> filters() {
        List<Filter> out = new ArrayList<Filter>();
        List<String> types = new ArrayList<String>();
        for (String s : new String[]{"全部", "电影", "电视剧", "动漫", "综艺", "纪录片", "短剧"}) types.add(s);
        out.add(new Filter("class", "类型", types));

        List<String> areas = new ArrayList<String>();
        for (String s : new String[]{"全部地区", "大陆", "香港", "台湾", "美国", "韩国", "日本", "泰国", "英国", "法国", "印度"})
            areas.add(s);
        out.add(new Filter("area", "地区", areas));

        List<String> years = new ArrayList<String>();
        years.add("全部");
        int y = 2026;
        for (int i = 0; i < 16; i++) years.add(String.valueOf(y - i));
        out.add(new Filter("year", "年份", years));

        List<String> sorts = new ArrayList<String>();
        for (String s : new String[]{"最新", "最热", "评分"}) sorts.add(s);
        out.add(new Filter("by", "排序", sorts));
        return out;
    }

    public static void clearCache() {
        cateCache.clear();
        cacheTime.clear();
    }
}
