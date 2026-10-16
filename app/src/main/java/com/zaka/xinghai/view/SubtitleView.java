package com.zaka.xinghai.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.View;

import com.zaka.xinghai.subtitle.Cue;
import com.zaka.xinghai.util.U;

import java.util.ArrayList;
import java.util.List;

/**
 * 自研字幕渲染层。
 * 不用播放内核的字幕通道（那玩意儿在这类内核里常常是空壳），自己按播放进度匹配 cue 绘制：
 *   - 支持整体时间轴偏移（毫秒级，改了立刻生效，不用重新加载字幕）
 *   - 支持开关，关掉就什么都不画、也不去搜字幕
 *   - 描边 + 阴影，深色画面也看得清
 */
public class SubtitleView extends View {

    private List<Cue> cues = new ArrayList<Cue>();
    private long position = 0L;
    private long offset = 0L;
    private boolean enabled = true;
    private float sizeSp = 16f;
    /** 距底部距离占比 0~1 */
    private float bottomRatio = 0.12f;
    private int index = 0;

    private final TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint stroke = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);

    public SubtitleView(Context c) {
        this(c, null);
    }

    public SubtitleView(Context c, AttributeSet a) {
        super(c, a);
        setWillNotDraw(false);
        paint.setColor(Color.WHITE);
        paint.setTextSize(U.dip(sizeSp));
        paint.setStyle(Paint.Style.FILL);
        stroke.setColor(0xcc000000);
        stroke.setTextSize(U.dip(sizeSp));
        stroke.setStyle(Paint.Style.FILL_AND_STROKE);
        stroke.setStrokeWidth(U.dip(3f));
        bg.setColor(0x33000000);
    }

    public void setCues(List<Cue> list) {
        cues = list == null ? new ArrayList<Cue>() : list;
        index = 0;
        invalidate();
    }

    public List<Cue> cues() {
        return cues;
    }

    public void setOffset(long ms) {
        offset = ms;
        invalidate();
    }

    public long offset() {
        return offset;
    }

    public void setEnabledSub(boolean v) {
        enabled = v;
        if (!v) invalidate();
    }

    public boolean isEnabledSub() {
        return enabled;
    }

    public void setTextSizeSp(float sp) {
        sizeSp = sp;
        paint.setTextSize(U.dip(sp));
        stroke.setTextSize(U.dip(sp));
        invalidate();
    }

    public void setPosition(long ms) {
        position = ms;
        if (enabled && cues.size() > 0) invalidate();
    }

    private Cue current() {
        if (cues.size() == 0) return null;
        long p = position - offset;
        // 二分找位置，再线性扫相邻（字幕有重叠时也能兜住）
        if (index >= cues.size()) index = cues.size() - 1;
        while (index > 0 && cues.get(index).start > p) index--;
        while (index + 1 < cues.size() && cues.get(index).end < p) index++;
        for (int i = Math.max(0, index - 1); i < Math.min(cues.size(), index + 2); i++) {
            Cue c = cues.get(i);
            if (p >= c.start && p <= c.end) return c;
        }
        return null;
    }

    public String currentText() {
        Cue c = current();
        return c == null ? "" : c.text;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!enabled) return;
        Cue c = current();
        if (c == null || U.empty(c.text)) return;

        int w = getWidth();
        if (w <= 0) return;
        int maxW = (int) (w * 0.86f);
        StaticLayout layout = new StaticLayout(c.text, paint, maxW, Layout.Alignment.ALIGN_CENTER,
                1.15f, U.dip(2), false);
        stroke.set(paint);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        stroke.setStrokeWidth(U.dip(3.2f));
        stroke.setColor(0xcc000000);
        int lh = layout.getHeight();
        int top = (int) (getHeight() * (1f - bottomRatio)) - lh;
        if (top < 0) top = 0;
        int left = (w - maxW) / 2;

        bg.setColor(0x40000000);
        canvas.drawRect(left - U.dip(8), top - U.dip(4), left + maxW + U.dip(8),
                top + lh + U.dip(4), bg);

        canvas.save();
        canvas.translate(left, top);
        // 描边层：黑边一圈，再压白字，深色画面也看得清
        new StaticLayout(c.text, stroke, maxW, Layout.Alignment.ALIGN_CENTER,
                1.15f, U.dip(2), false).draw(canvas);
        paint.setColor(Color.WHITE);
        layout.draw(canvas);
        canvas.restore();
    }
}
