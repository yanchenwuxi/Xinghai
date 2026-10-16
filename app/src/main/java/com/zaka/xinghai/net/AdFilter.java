package com.zaka.xinghai.net;

import com.zaka.xinghai.util.U;

import java.util.ArrayList;
import java.util.List;

/**
 * 自研去广告引擎（四层）：
 *  1. 线路名 / 剧集名里的推广条目清洗（保守判定：宁可漏过，不误杀，全判广告时至少留一条）
 *  2. 播放地址上的推广 / 统计参数剥离（保留 t / pg / ac 这类采集参数）
 *  3. HLS 播放列表里的插播广告段剔除（SCTE-35 CUE-OUT/IN 区间 + 广告域分片）
 *  4. 中转跳转页解开成真实地址
 */
public final class AdFilter {

    /** 线路名 / 剧集名里的推广词 */
    private static final String[] AD_NAME_WORDS = {
            "广告", "推广", "加群", "加微信", "微信号", "威信", "微信", "薇信",
            "qq群", "扣扣群", "粉丝群", "交流群", "资源群", "分享群",
            "扫码", "二维码", "公众号", "关注公众", "关注我",
            "下载app", "app下载", "请下载", "安装app", "客户端下载", "下载客户端",
            "福利", "小姐姐", "同城", "约炮", "成人片", "黄片", "彩票", "赌博", "真人荷官",
            "防失联", "永不失联", "不失联", "请收藏", "收藏本站", "收藏网址",
            "最新地址", "最新网址", "发布页", "备用网址",
            "跳转", "点击进入", "点击这里", "更多资源", "更多精彩", "全网影视",
            "无法播放请", "线路调整", "维护中", "测试线路", "请不要相信"
    };

    /** 播放地址上的广告 / 统计参数 */
    private static final String[] AD_QUERY_KEYS = {
            "ad", "ads", "adv", "adid", "ad_id", "adfrom", "adslot", "adtype", "adpos",
            "gg", "promo", "promotion", "promoid",
            "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
            "spm", "scm", "from_source", "referrer_ad"
    };

    /** 分片 URL 里的广告特征 */
    private static final String[] AD_SEG_HINTS = {
            "/ad/", "/ads/", "/adv/", "/advert", "/ad-", "-ad.", "_ad_", "/gg/",
            "/promo", "/preroll", "/midroll", "/commercial", "ad_slice", "adsense"
    };

    /** 中转页里的真实地址参数 */
    private static final String[] REDIRECT_PARAMS = {"url", "u", "target", "to", "href", "link", "jump", "go"};

    public static int blockedLines = 0;
    public static int blockedSegs = 0;

    private AdFilter() {
    }

    /** 名称是不是推广条目 */
    public static boolean isAdName(String name) {
        if (U.empty(name)) return true;
        String s = name.replace(" ", "").toLowerCase();
        if (s.length() <= 1) return true;
        for (String w : AD_NAME_WORDS) {
            if (s.contains(w)) return true;
        }
        if (s.contains("http://") || s.contains("https://") || s.contains("www.")) return true;
        return false;
    }

    /** 清洗一整条线路的剧集名：过滤广告条目 */
    public static List<String> cleanEpNames(List<String> names) {
        List<String> out = new ArrayList<String>();
        for (String n : names) {
            if (!isAdName(n)) out.add(n);
        }
        if (out.size() == 0) return names;
        return out;
    }

    /** 剥掉地址上的推广/统计参数 */
    public static String cleanUrl(String url) {
        if (U.empty(url)) return url;
        try {
            int q = url.indexOf('?');
            if (q < 0) return url;
            String base = url.substring(0, q);
            String query = url.substring(q + 1);
            String[] parts = query.split("&");
            StringBuilder keep = new StringBuilder();
            for (String p : parts) {
                int eq = p.indexOf('=');
                String key = (eq > 0 ? p.substring(0, eq) : p).toLowerCase();
                boolean drop = false;
                for (String ad : AD_QUERY_KEYS) {
                    if (key.equals(ad)) {
                        drop = true;
                        break;
                    }
                }
                if (U.contains(key, "ad_") || U.contains(key, "_ad")) drop = true;
                if (!drop && p.length() > 0) {
                    if (keep.length() > 0) keep.append("&");
                    keep.append(p);
                }
            }
            return keep.length() == 0 ? base : base + "?" + keep;
        } catch (Throwable t) {
            return url;
        }
    }

    /** 中转页解开真实地址（最多 3 层） */
    public static String unwrap(String url) {
        String cur = url;
        for (int i = 0; i < 3; i++) {
            if (U.empty(cur)) return url;
            int q = cur.indexOf('?');
            if (q < 0) return cur;
            String query = cur.substring(q + 1);
            String found = null;
            for (String p : query.split("&")) {
                int eq = p.indexOf('=');
                if (eq <= 0) continue;
                String key = p.substring(0, eq).toLowerCase();
                boolean hit = false;
                for (String r : REDIRECT_PARAMS) {
                    if (key.equals(r)) {
                        hit = true;
                        break;
                    }
                }
                if (!hit) continue;
                String val = U.urlDecode(p.substring(eq + 1));
                if (val.startsWith("http://") || val.startsWith("https://")) {
                    found = val;
                    break;
                }
            }
            if (found == null) return cur;
            cur = found;
        }
        return cur;
    }

    /** 分片地址像不像广告 */
    public static boolean looksLikeAdSeg(String segUrl) {
        if (U.empty(segUrl)) return false;
        String s = segUrl.toLowerCase();
        for (String h : AD_SEG_HINTS) {
            if (s.contains(h)) return true;
        }
        return false;
    }

    // ------------------------------------------------------------ 播放列表清洗
    public static class PlResult {
        public String text = "";
        public int removed = 0;
        public boolean ok = true;
    }

    /**
     * 清洗 HLS 播放列表。
     *
     * @param text      原始播放列表
     * @param baseUrl   列表地址（补相对路径用）
     * @param proxyBase 本地代理基址；非空则把分片指到本地（缓存才生效）
     */
    public static PlResult cleanPlaylist(String text, String baseUrl, String proxyBase) {
        PlResult r = new PlResult();
        if (U.empty(text)) {
            r.ok = false;
            return r;
        }
        String[] lines = text.split("\n");
        StringBuilder out = new StringBuilder();
        boolean inAd = false;
        int removed = 0;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.length() == 0) {
                out.append("\n");
                continue;
            }
            // SCTE-35 插播标记
            String up = line.toUpperCase();
            if (up.contains("SCTE35-OUT") || up.contains("SCTE-35-OUT") || up.contains("CUE-OUT")) {
                inAd = true;
                removed++;
                continue;
            }
            if (up.contains("SCTE35-IN") || up.contains("SCTE-35-IN") || up.contains("CUE-IN")) {
                inAd = false;
                continue;
            }
            if (inAd) {
                removed++;
                continue;
            }
            if (line.startsWith("#EXTINF")) {
                // 看下一行是不是广告分片，是的话这两行一起丢（不留孤儿标签）
                String next = nextUrl(lines, i + 1);
                if (next != null && looksLikeAdSeg(next)) {
                    removed++;
                    // 跳过 EXTINF 自身
                    while (i + 1 < lines.length && lines[i + 1].trim().length() == 0) i++;
                    i++; // 跳到分片行，下面 continue 掉它
                    continue;
                }
                out.append(line).append("\n");
                continue;
            }
            // 标签行里的 URI
            if (line.startsWith("#EXT-X-KEY") || line.startsWith("#EXT-X-MAP")
                    || line.startsWith("#EXT-X-MEDIA") || line.startsWith("#EXT-X-STREAM-INF")) {
                out.append(rewriteTags(line, baseUrl, proxyBase)).append("\n");
                continue;
            }
            if (line.startsWith("#")) {
                out.append(line).append("\n");
                continue;
            }
            // 普通 URI 行（分片或子列表）
            if (looksLikeAdSeg(line)) {
                removed++;
                continue;
            }
            out.append(toLocal(absolute(line, baseUrl), baseUrl, proxyBase)).append("\n");
        }
        r.text = out.toString();
        r.removed = removed;
        blockedSegs += removed;
        return r;
    }

    private static String nextUrl(String[] lines, int from) {
        for (int i = from; i < lines.length && i < from + 4; i++) {
            String l = lines[i].trim();
            if (l.length() == 0) continue;
            if (l.startsWith("#")) continue;
            return l;
        }
        return null;
    }

    private static String rewriteTags(String line, String baseUrl, String proxyBase) {
        StringBuilder sb = new StringBuilder(line);
        try {
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("URI=\"([^\"]+)\"").matcher(line);
            while (m.find()) {
                String uri = m.group(1);
                String abs = absolute(uri, baseUrl);
                String local = toLocal(abs, baseUrl, proxyBase);
                int idx = sb.indexOf("\"" + uri + "\"");
                if (idx >= 0) sb.replace(idx, idx + uri.length() + 2, "\"" + local + "\"");
            }
        } catch (Throwable ignored) {
        }
        return sb.toString();
    }

    /** 相对路径补成绝对 */
    public static String absolute(String path, String base) {
        if (U.empty(path)) return path;
        if (path.startsWith("http://") || path.startsWith("https://")) return path;
        if (U.empty(base)) return path;
        int q = base.indexOf('?');
        String b = q > 0 ? base.substring(0, q) : base;
        if (path.startsWith("//")) return "https:" + path;
        if (path.startsWith("/")) {
            int s = b.indexOf("://");
            if (s < 0) return path;
            int e = b.indexOf('/', s + 3);
            return e < 0 ? b + path : b.substring(0, e) + path;
        }
        int slash = b.lastIndexOf('/');
        return slash > 0 ? b.substring(0, slash + 1) + path : path;
    }

    private static String toLocal(String abs, String baseUrl, String proxyBase) {
        if (U.empty(proxyBase)) return abs;
        // 子列表（m3u8）继续走代理，分片落到 /seg
        if (abs.contains(".m3u8")) return proxyBase + "/pl?u=" + U.urlEncode(abs);
        return proxyBase + "/seg?u=" + U.urlEncode(abs);
    }

    public static void reset() {
        blockedLines = 0;
        blockedSegs = 0;
    }
}
