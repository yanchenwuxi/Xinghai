package com.zaka.xinghai.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import com.zaka.xinghai.util.U;

/** 五星评分（详情页 / 播放页简介区用的那个）。 */
public class StarBar extends View {

    private float rating = 0f;   // 0~5
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path star = new Path();
    private int starW, starH, gap;

    public StarBar(Context c) {
        this(c, null);
    }

    public StarBar(Context c, AttributeSet a) {
        super(c, a);
        p.setStyle(Paint.Style.FILL);
        starW = U.dp(11);
        starH = U.dp(11);
        gap = U.dp(3);
        buildStar();
    }

    public void setRating(float r) {
        rating = Math.max(0f, Math.min(5f, r));
        invalidate();
    }

    private void buildStar() {
        star.reset();
        float cx = starW / 2f, cy = starH / 2f;
        float outer = Math.min(starW, starH) / 2f;
        float inner = outer * 0.44f;
        for (int i = 0; i < 10; i++) {
            double ang = Math.PI / 2 * 3 + i * Math.PI / 5;
            float r = (i % 2 == 0) ? outer : inner;
            float x = cx + (float) Math.cos(ang) * r;
            float y = cy - (float) Math.sin(ang) * r;
            if (i == 0) star.moveTo(x, y);
            else star.lineTo(x, y);
        }
        star.close();
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = starW * 5 + gap * 4;
        setMeasuredDimension(w, starH);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        for (int i = 0; i < 5; i++) {
            float x = i * (starW + gap);
            canvas.save();
            canvas.translate(x, 0);
            if (i + 1 <= rating) {
                p.setColor(0xffffb40a);
                canvas.drawPath(star, p);
            } else if (i < rating) {
                p.setColor(0xffffb40a);
                canvas.save();
                canvas.clipRect(0, 0, starW * (rating - i), starH);
                canvas.drawPath(star, p);
                canvas.restore();
                p.setColor(0x55ffffff);
                canvas.drawPath(star, p);
            } else {
                p.setColor(0x55ffffff);
                canvas.drawPath(star, p);
            }
            canvas.restore();
        }
    }
}
