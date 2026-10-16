package com.zaka.xinghai.subtitle;

/** 一条字幕。 */
public class Cue {

    public long start;
    public long end;
    public String text;

    public Cue(long start, long end, String text) {
        this.start = start;
        this.end = end;
        this.text = text == null ? "" : text;
    }
}
