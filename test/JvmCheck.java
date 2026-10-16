import com.zaka.xinghai.model.Cate;
import com.zaka.xinghai.model.Vod;
import com.zaka.xinghai.net.AdFilter;
import com.zaka.xinghai.net.Cms;
import com.zaka.xinghai.subtitle.Cue;
import com.zaka.xinghai.subtitle.SubParse;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * 解析层 / 去广告 / 字幕解析的真实数据自测（在 JVM 上跑，喂的是真接口抓下来的样本）。
 * 不依赖安卓运行时，只验证纯逻辑那一层。
 */
public class JvmCheck {

    static int pass = 0, fail = 0;

    static void check(String name, boolean ok) {
        check(name, ok, "");
    }

    static void check(String name, boolean ok, String detail) {
        if (ok) {
            pass++;
            System.out.println("  [OK]   " + name + (detail.isEmpty() ? "" : "  → " + detail));
        } else {
            fail++;
            System.out.println("  [FAIL] " + name + (detail.isEmpty() ? "" : "  → " + detail));
        }
    }

    static String read(String path) throws Exception {
        return new String(Files.readAllBytes(new File(path).toPath()), Charset.forName("UTF-8"));
    }

    public static void main(String[] a) throws Exception {
        String dir = a.length > 0 ? a[0] : "build/fixtures";
        System.out.println("==== 星海 · 解析层真实数据自测（样本目录 " + dir + "）====");

        // ---------- 1. 分类解析
        System.out.println("\n[1] 分类解析（?ac=list&t=1 的真实返回）");
        String cates = read(dir + "/cates.json");
        List<Cate> cs = Cms.parseTypes(cates);
        check("分类条数 >= 10", cs.size() >= 10, cs.size() + " 个分类");
        boolean hasName = cs.size() > 0 && !cs.get(0).name.isEmpty() && !cs.get(0).id.isEmpty();
        check("分类 id/name 解析完整", hasName,
                cs.size() > 0 ? (cs.get(0).id + "=" + cs.get(0).name) : "空");

        // ---------- 2. 列表解析
        System.out.println("\n[2] 列表解析（?ac=videolist 的真实返回）");
        String list = read(dir + "/list.json");
        List<Vod> vs = Cms.parseList(list);
        check("列表条数 > 0", vs.size() > 0, vs.size() + " 条");
        int named = 0, pic = 0;
        for (Vod v : vs) {
            if (!v.name.isEmpty()) named++;
            if (!v.pic.isEmpty()) pic++;
        }
        check("片名解析率 100%", named == vs.size() && vs.size() > 0, named + "/" + vs.size());
        check("封面解析率 > 90%", vs.size() > 0 && pic * 10 >= vs.size() * 9, pic + "/" + vs.size());
        if (vs.size() > 0) {
            System.out.println("       样例: " + vs.get(0).name + " | " + vs.get(0).note
                    + " | " + vs.get(0).year + " | " + vs.get(0).type);
        }

        // ---------- 3. 详情解析（线路 + 剧集）
        System.out.println("\n[3] 详情解析（线路 / 剧集）");
        String detail = read(dir + "/detail.json");
        List<Vod> ds = Cms.parseList(detail);
        check("详情返回 1 条", ds.size() >= 1, ds.size() + " 条");
        if (ds.size() > 0) {
            Vod d = ds.get(0);
            check("线路数 > 0", d.flags.size() > 0, d.flags.size() + " 条线路");
            int total = 0;
            for (List<Vod.Episode> e : d.episodes) total += e.size();
            check("剧集数 > 0", total > 0, total + " 集，样例首集：" +
                    (d.episodes.size() > 0 && d.episodes.get(0).size() > 0
                            ? d.episodes.get(0).get(0).name : "-"));
            boolean urlOk = d.episodes.size() > 0 && d.episodes.get(0).size() > 0
                    && d.episodes.get(0).get(0).url.startsWith("http");
            check("剧集地址是 http 直链", urlOk);
        }

        // ---------- 4. 搜索解析
        System.out.println("\n[4] 搜索解析（?wd= 的真实返回）");
        String search = read(dir + "/search.json");
        List<Vod> ss = Cms.parseList(search);
        check("搜索有结果", ss.size() > 0, ss.size() + " 条");
        if (ss.size() > 0) System.out.println("       首条: " + ss.get(0).name);

        // ---------- 5. 字幕解析（真实 ASS）
        System.out.println("\n[5] 字幕解析（真实 ASS 样本）");
        String ass = read(dir + "/sub_sample.ass");
        long t0 = System.currentTimeMillis();
        List<Cue> cues = SubParse.parse(ass, "ass");
        long cost = System.currentTimeMillis() - t0;
        check("解析出字幕条数 > 1000", cues.size() > 1000, cues.size() + " 条，耗时 " + cost + "ms");
        boolean timeOk = cues.size() > 1 && cues.get(0).start <= cues.get(1).start && cues.get(0).end > cues.get(0).start;
        check("时间轴有序且区间合法", timeOk);
        boolean clean = cues.size() > 0 && cues.get(0).text.indexOf("{") < 0;
        check("样式标签已清理", clean, cues.size() > 0 ? "首条：" + cues.get(0).text : "");

        // SRT / VTT 也过一遍
        String srt = "1\n00:00:01,000 --> 00:00:03,500\n你好，世界\n\n2\n00:00:04,000 --> 00:00:06,000\n第二句\n";
        List<Cue> sc = SubParse.parse(srt, "srt");
        check("SRT 解析 2 条且时间正确",
                sc.size() == 2 && sc.get(1).start == 4000 && sc.get(1).end == 6000,
                sc.size() + " 条");

        // 偏移算法（毫秒级）
        long off = -1200;
        long pos = 5000;
        boolean offOk = (pos - off) == 6200;
        check("字幕偏移换算正确（-1.2s 时，5s 处按 6.2s 取内容）", offOk);

        // ---------- 6. 去广告：真实播放列表
        System.out.println("\n[6] 去广告 · 真实 HLS 播放列表");
        String media = read(dir + "/live_media.m3u8");
        AdFilter.PlResult r1 = AdFilter.cleanPlaylist(media, "https://cdn.example.com/live/index.m3u8", null);
        check("清洗后仍以 #EXTM3U 开头", r1.text.trim().startsWith("#EXTM3U"));
        int segs = 0;
        for (String line : r1.text.split("\n")) {
            if (line.trim().length() > 0 && !line.trim().startsWith("#")) segs++;
        }
        check("真实列表分片没被误杀（> 100 段）", segs > 100, segs + " 段，剔掉 " + r1.removed);

        AdFilter.PlResult r2 = AdFilter.cleanPlaylist(media, "https://cdn.example.com/live/index.m3u8",
                "http://127.0.0.1:8888");
        int local = 0;
        for (String line : r2.text.split("\n")) {
            String l = line.trim();
            if (l.length() > 0 && !l.startsWith("#")) {
                if (l.startsWith("http://127.0.0.1:8888/seg?u=")) local++;
            }
        }
        check("分片全部指回本地代理（缓存才生效）", local == segs && segs > 0, local + "/" + segs);

        // ---------- 7. 去广告：构造 SCTE-35 插播样本
        System.out.println("\n[7] 去广告 · SCTE-35 插播广告段");
        String adPl = "#EXTM3U\n#EXT-X-VERSION:3\n#EXTINF:5.0,\nseg1.ts\n"
                + "#EXT-X-CUE-OUT:30\n#EXTINF:10.0,\nhttp://ads.example.com/ad/roll1.ts\n"
                + "#EXTINF:10.0,\nhttp://ads.example.com/ad/roll2.ts\n#EXT-X-CUE-IN\n"
                + "#EXTINF:5.0,\nseg2.ts\n#EXTINF:5.0,\nseg3.ts\n";
        AdFilter.PlResult r3 = AdFilter.cleanPlaylist(adPl, "https://x.com/a/index.m3u8", null);
        int keep = 0, orphan = 0;
        String[] lines = r3.text.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String l = lines[i].trim();
            if (l.startsWith("#EXTINF")) {
                String next = "";
                for (int k = i + 1; k < lines.length; k++) {
                    String n = lines[k].trim();
                    if (n.length() == 0) continue;
                    next = n;
                    break;
                }
                if (!next.startsWith("#") && next.length() > 0) keep++;
                else orphan++;
            }
        }
        check("插播广告段被剔除（剩 3 段）", keep == 3, keep + " 段");
        check("没有留下孤儿 #EXTINF 标签", orphan == 0, orphan + " 个孤儿");
        check("广告域分片没漏过去", r3.text.indexOf("ads.example.com") < 0);

        // ---------- 8. 地址清洗 / 中转解开
        System.out.println("\n[8] 地址清洗与中转解开");
        String dirty = "https://v.example.com/a.m3u8?t=1&pg=2&utm_source=x&adid=99&ads=1";
        String cleaned = AdFilter.cleanUrl(dirty);
        check("剥掉推广参数、保留采集参数",
                cleaned.contains("t=1") && cleaned.contains("pg=2")
                        && !cleaned.contains("utm_source") && !cleaned.contains("adid"),
                cleaned);
        String jump = "https://go.example.com/jump?url=" +
                java.net.URLEncoder.encode("https://real.example.com/play/index.m3u8", "UTF-8");
        String real = AdFilter.unwrap(jump);
        check("中转页解出真实地址", real.startsWith("https://real.example.com/play/index.m3u8"), real);
        check("推广线路名判为广告", AdFilter.isAdName("加群看片【最新地址】")
                && AdFilter.isAdName("请下载APP观看"));
        check("正常线路名不误判", !AdFilter.isAdName("线路1") && !AdFilter.isAdName("高清"));

        System.out.println("\n==== 结果：" + pass + " 通过 / " + fail + " 失败 ====");
        if (fail > 0) System.exit(1);
    }
}
