package com.zaka.xinghai.net;

import com.zaka.xinghai.util.U;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 直播源解析：吃 m3u / m3u8 列表（EXTINF 的 group-title 分组）。 */
public final class M3u {

    public static class Channel {
        public String name = "";
        public String url = "";
        public String logo = "";
    }

    public static class Group {
        public String name = "";
        public List<Channel> channels = new ArrayList<Channel>();
    }

    private M3u() {
    }

    public static List<Group> parse(String text) {
        List<Group> out = new ArrayList<Group>();
        if (U.empty(text) || !text.contains("#EXTM3U")) return out;
        Map<String, Group> map = new LinkedHashMap<String, Group>();
        String curName = null, curLogo = null, curGroup = "默认";
        for (String line : text.split("\n")) {
            String l = line.trim();
            if (l.length() == 0) continue;
            if (l.startsWith("#EXTINF")) {
                int comma = l.lastIndexOf(',');
                curName = comma > 0 ? l.substring(comma + 1).trim() : "频道";
                curGroup = attr(l, "group-title");
                if (U.empty(curGroup)) curGroup = "默认";
                curLogo = attr(l, "tvg-logo");
                continue;
            }
            if (l.startsWith("#")) continue;
            if (l.startsWith("http")) {
                Group g = map.get(curGroup);
                if (g == null) {
                    g = new Group();
                    g.name = curGroup;
                    map.put(curGroup, g);
                }
                Channel c = new Channel();
                c.name = U.empty(curName) ? ("频道" + (g.channels.size() + 1)) : curName;
                c.url = l;
                c.logo = curLogo == null ? "" : curLogo;
                g.channels.add(c);
                curName = null;
            }
        }
        out.addAll(map.values());
        return out;
    }

    private static String attr(String line, String key) {
        int i = line.indexOf(key + "=\"");
        if (i < 0) return "";
        int s = i + key.length() + 2;
        int e = line.indexOf('"', s);
        return e > s ? line.substring(s, e) : "";
    }

    /** 内置一份公共直播源（可被用户自己的地址覆盖） */
    public static final String BUILTIN =
            "https://raw.githubusercontent.com/iptv-org/iptv/master/streams/cn.m3u";
}
