package com.zaka.xinghai.net;

import com.zaka.xinghai.model.Cate;
import com.zaka.xinghai.model.Site;
import com.zaka.xinghai.model.Vod;
import com.zaka.xinghai.util.U;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 脚本站扩展：不内嵌 V8/Python，但对常见 TVBox JS/PY 写法做「静态抽取」：
 * 1) api 本身是 CMS 路径 → 当 CMS 用
 * 2) 下载 .js/.py 文本，正则提取 host / 可访问的 provide/vod API
 * 3) 若脚本里带 JSON 接口声明，尝试直接请求
 */
public final class Spider {

    private Spider() {
    }

    public static boolean canUseAsCms(Site site) {
        if (site == null || U.empty(site.api)) return false;
        String a = site.api.toLowerCase();
        if (a.contains("provide/vod") || a.contains("api.php") || a.contains("/api/")) return true;
        if (a.endsWith(".js") || a.endsWith(".py")) return false;
        return a.startsWith("http") && !site.isScript();
    }

    public static String unsupportedHint(Site site) {
        if (site == null) return "站点无效";
        if (site.isJs()) return "「" + site.name + "」JS 源：未抽到可用采集地址";
        if (site.isPy()) return "「" + site.name + "」PY 源：未抽到可用采集地址";
        return "「" + site.name + "」脚本源暂不可用";
    }

    /** 解析脚本站为可用的 CMS 基址（可能改写 site.api） */
    public static String resolveCmsBase(Site site) throws Exception {
        if (site == null) return null;
        if (canUseAsCms(site)) return site.api;
        if (!site.api.startsWith("http")) return null;
        String body = Http.get(site.api, Http.headers("User-Agent", Http.UA), 15000);
        if (U.empty(body)) return null;
        // 缓存脚本正文到 ext（截断）
        if (U.empty(site.ext) && body.length() > 0) {
            site.ext = body.length() > 2000 ? body.substring(0, 2000) : body;
        }
        String found = extractApi(body);
        if (!U.empty(found)) {
            // 不永久改 api，返回解析基址
            return found;
        }
        return null;
    }

    /** 从 JS/PY 文本抽取可能的采集 API */
    public static String extractApi(String src) {
        if (U.empty(src)) return null;
        // host = "https://xxx.com"
        Matcher m = Pattern.compile("host\\s*=\\s*[\"'](https?://[^\"']+)[\"']", Pattern.CASE_INSENSITIVE).matcher(src);
        if (m.find()) {
            String host = m.group(1);
            if (!host.endsWith("/")) host = host + "/";
            // 常见拼接
            return host + "api.php/provide/vod/";
        }
        m = Pattern.compile("https?://[a-zA-Z0-9._\\-:/]+api\\.php/provide/vod/?", Pattern.CASE_INSENSITIVE).matcher(src);
        if (m.find()) {
            String u = m.group();
            if (!u.endsWith("/")) u = u + "/";
            return u;
        }
        m = Pattern.compile("https?://[a-zA-Z0-9._\\-:/]+/api/[^\\s\"']+", Pattern.CASE_INSENSITIVE).matcher(src);
        if (m.find()) return m.group();
        // site: { api: "..." }
        m = Pattern.compile("api\\s*[:=]\\s*[\"'](https?://[^\"']+)[\"']").matcher(src);
        if (m.find()) return m.group(1);
        return null;
    }

    /** 分类：优先 CMS，否则尝试脚本抽取后的基址 */
    public static List<Cate> types(Site site) throws Exception {
        if (canUseAsCms(site)) return Cms.types(site);
        String base = resolveCmsBase(site);
        if (U.empty(base)) throw new Exception(unsupportedHint(site));
        Site proxy = copyWithApi(site, base);
        return Cms.types(proxy);
    }

    public static List<Vod> list(Site site, String typeId, int page, Map<String, String> filters) throws Exception {
        if (canUseAsCms(site)) return Cms.list(site, typeId, page, filters);
        String base = resolveCmsBase(site);
        if (U.empty(base)) throw new Exception(unsupportedHint(site));
        return Cms.list(copyWithApi(site, base), typeId, page, filters);
    }

    public static List<Vod> search(Site site, String kw) throws Exception {
        if (canUseAsCms(site)) return Cms.search(site, kw);
        String base = resolveCmsBase(site);
        if (U.empty(base)) throw new Exception(unsupportedHint(site));
        return Cms.search(copyWithApi(site, base), kw);
    }

    public static Vod detail(Site site, Vod v) throws Exception {
        if (canUseAsCms(site)) return Cms.detail(site, v);
        String base = resolveCmsBase(site);
        if (U.empty(base)) throw new Exception(unsupportedHint(site));
        return Cms.detail(copyWithApi(site, base), v);
    }

    private static Site copyWithApi(Site s, String api) {
        Site n = new Site(s.name, api);
        n.key = s.key;
        n.type = "cms";
        n.ua = s.ua;
        n.adFilter = s.adFilter;
        return n;
    }
}
