package com.zaka.xinghai.subtitle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 自研字幕解析：SRT / ASS(SSA) / VTT 三种全吃。
 * 不做花哨样式，只把「时间轴 + 文本」解析干净——偏移调节靠这套时间轴。
 */
public final class SubParse {

    private SubParse() {
    }

    public static String detectFormat(String text, String ext) {
        if (text == null) return "srt";
        if (text.contains("[Script Info]") || text.contains("[V4+ Styles]") || text.contains("Dialogue:")) return "ass";
        if (text.trim().startsWith("WEBVTT")) return "vtt";
        if (ext != null) {
            String e = ext.toLowerCase();
            if (e.contains("ass") || e.contains("ssa")) return "ass";
            if (e.contains("vtt")) return "vtt";
        }
        return "srt";
    }

    public static List<Cue> parse(String text, String ext) {
        String fmt = detectFormat(text, ext);
        if ("ass".equals(fmt)) return parseAss(text);
        if ("vtt".equals(fmt)) return parseVtt(text);
        return parseSrt(text);
    }

    // ------------------------------------------------------------ SRT
    private static final Pattern TIME_SRT = Pattern.compile(
            "(\\d{1,2}):(\\d{1,2}):(\\d{1,2})[,.](\\d{1,3})\\s*-->\\s*(\\d{1,2}):(\\d{1,2}):(\\d{1,2})[,.](\\d{1,3})");

    public static List<Cue> parseSrt(String text) {
        List<Cue> out = new ArrayList<Cue>();
        if (text == null || text.length() == 0) return out;
        String t = stripBom(text).replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = t.split("\n");
        int i = 0;
        while (i < lines.length) {
            // 找时间行
            Matcher m = TIME_SRT.matcher(lines[i]);
            if (!m.find()) {
                i++;
                continue;
            }
            long start = ms(m.group(1), m.group(2), m.group(3), m.group(4));
            long end = ms(m.group(5), m.group(6), m.group(7), m.group(8));
            StringBuilder sb = new StringBuilder();
            i++;
            while (i < lines.length && lines[i].trim().length() > 0) {
                if (sb.length() > 0) sb.append("\n");
                sb.append(clean(lines[i]));
                i++;
            }
            if (sb.length() > 0) out.add(new Cue(start, end, sb.toString()));
        }
        sort(out);
        return out;
    }

    // ------------------------------------------------------------ VTT
    public static List<Cue> parseVtt(String text) {
        List<Cue> out = new ArrayList<Cue>();
        if (text == null) return out;
        String t = stripBom(text).replace("\r\n", "\n").replace('\r', '\n');
        for (String block : t.split("\n\n")) {
            String[] lines = block.split("\n");
            int ti = -1;
            for (int i = 0; i < lines.length; i++) {
                if (lines[i].contains("-->")) {
                    ti = i;
                    break;
                }
            }
            if (ti < 0) continue;
            Matcher m = TIME_SRT.matcher(lines[ti]);
            if (!m.find()) continue;
            long start = ms(m.group(1), m.group(2), m.group(3), m.group(4));
            long end = ms(m.group(5), m.group(6), m.group(7), m.group(8));
            StringBuilder sb = new StringBuilder();
            for (int i = ti + 1; i < lines.length; i++) {
                if (lines[i].trim().length() == 0) continue;
                if (sb.length() > 0) sb.append("\n");
                sb.append(clean(lines[i]));
            }
            if (sb.length() > 0) out.add(new Cue(start, end, sb.toString()));
        }
        sort(out);
        return out;
    }

    // ------------------------------------------------------------ ASS / SSA
    private static final Pattern TIME_ASS = Pattern.compile(
            "(\\d{1,2}):(\\d{1,2}):(\\d{1,2})[.:](\\d{1,2})");

    public static List<Cue> parseAss(String text) {
        List<Cue> out = new ArrayList<Cue>();
        if (text == null) return out;
        String t = stripBom(text).replace("\r\n", "\n").replace('\r', '\n');
        boolean inEvents = false;
        int startIdx = 9, endIdx = 1, textIdx = 9;
        for (String line : t.split("\n")) {
            String l = line.trim();
            if (l.startsWith("[")) {
                inEvents = l.toLowerCase().startsWith("[events]");
                continue;
            }
            if (!inEvents) continue;
            if (l.toLowerCase().startsWith("format:")) {
                String[] cols = l.substring(7).split(",");
                for (int i = 0; i < cols.length; i++) {
                    String c = cols[i].trim().toLowerCase();
                    if (c.equals("start")) startIdx = i;
                    else if (c.equals("end")) endIdx = i;
                    else if (c.equals("text")) textIdx = i;
                }
                continue;
            }
            if (!l.toLowerCase().startsWith("dialogue:")) continue;
            String body = l.substring(l.indexOf(':') + 1);
            String[] parts = body.split(",", textIdx + 1);
            if (parts.length <= Math.max(startIdx, endIdx)) continue;
            long start = assTime(parts[startIdx].trim());
            long end = assTime(parts[endIdx].trim());
            String txt = parts.length > textIdx ? parts[textIdx] : "";
            txt = ASS_TAG.matcher(txt).replaceAll("")
                    .replace("\\N", "\n").replace("\\n", "\n").replace("\\h", " ");
            txt = clean(txt);
            if (txt.length() > 0) out.add(new Cue(start, end, txt));
        }
        sort(out);
        return out;
    }

    private static final Pattern ASS_TAG = Pattern.compile("\\{[^}]*}");

    private static long assTime(String s) {
        Matcher m = TIME_ASS.matcher(s);
        if (!m.find()) return 0;
        long h = Long.parseLong(m.group(1));
        long mi = Long.parseLong(m.group(2));
        long se = Long.parseLong(m.group(3));
        long cs = Long.parseLong(m.group(4));
        return (h * 3600 + mi * 60 + se) * 1000L + cs * 10L;
    }

    // ------------------------------------------------------------ 工具
    private static long ms(String h, String m, String s, String frac) {
        long hh = Long.parseLong(h), mm = Long.parseLong(m), ss = Long.parseLong(s);
        String f = frac;
        while (f.length() < 3) f = f + "0";
        long ff = Long.parseLong(f.substring(0, 3));
        return (hh * 3600 + mm * 60 + ss) * 1000L + ff;
    }

    private static String clean(String s) {
        String t = s.replaceAll("<[^>]+>", "");
        t = t.replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">");
        return t.trim();
    }

    private static String stripBom(String s) {
        if (s.length() > 0 && s.charAt(0) == '\uFEFF') return s.substring(1);
        return s;
    }

    private static void sort(List<Cue> out) {
        Collections.sort(out, new Comparator<Cue>() {
            @Override
            public int compare(Cue a, Cue b) {
                return Long.compare(a.start, b.start);
            }
        });
    }
}
