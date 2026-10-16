package com.zaka.xinghai.model;

import java.util.ArrayList;
import java.util.List;

/** 影片数据（列表项 / 详情共用）。 */
public class Vod {

    public String id = "";
    public String name = "";
    public String pic = "";
    /** 更新到第几集 / 备注 */
    public String note = "";
    public String year = "";
    public String area = "";
    public String type = "";
    public String actor = "";
    public String director = "";
    public String content = "";
    /** 详情里带的评分，列表页一般没有 */
    public String score = "";
    /** 扩展信息（播放页简介区用） */
    public String sourceName = "";
    /** 来源站点 key */
    public String siteKey = "";

    public List<Flag> flags = new ArrayList<Flag>();
    /** 与 flags 下标对应：每条线路的剧集 */
    public List<List<Episode>> episodes = new ArrayList<List<Episode>>();
    /** 详情里解析出的分类筛选扩展 */
    public List<Cate> subCates = new ArrayList<Cate>();

    public boolean hasDetail() {
        return flags.size() > 0;
    }

    public List<Episode> eps(int flagIndex) {
        if (flagIndex < 0 || flagIndex >= episodes.size()) return new ArrayList<Episode>();
        return episodes.get(flagIndex);
    }

    public String scoreNum() {
        if (score == null || score.length() == 0) return "0";
        try {
            return String.format(java.util.Locale.US, "%.1f", Double.parseDouble(score));
        } catch (Throwable t) {
            return score;
        }
    }

    public float stars() {
        try {
            float v = Float.parseFloat(score);
            if (v <= 10) return Math.max(0f, Math.min(5f, v / 2f));
            return Math.max(0f, Math.min(5f, v / 2f));
        } catch (Throwable t) {
            return 0f;
        }
    }

    /** 线路 */
    public static class Flag {
        public String name = "";
        public int index = 0;

        public Flag(int index, String name) {
            this.index = index;
            this.name = name;
        }
    }

    /** 剧集 */
    public static class Episode {
        public String name = "";
        public String url = "";

        public Episode(String name, String url) {
            this.name = name;
            this.url = url;
        }

        public Episode() {
        }
    }
}
