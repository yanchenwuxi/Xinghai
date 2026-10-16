package com.zaka.xinghai.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import com.zaka.xinghai.util.U;

/** 流式布局（搜索历史标签、筛选标签用）。 */
public class FlowLayout extends ViewGroup {

    private int hGap = U.dp(8);
    private int vGap = U.dp(8);

    public FlowLayout(Context c) {
        super(c);
    }

    public FlowLayout(Context c, AttributeSet a) {
        super(c, a);
    }

    public void setGap(int h, int v) {
        hGap = h;
        vGap = v;
        requestLayout();
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int width = MeasureSpec.getSize(wSpec);
        int lineW = 0, lineH = 0, totalH = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View ch = getChildAt(i);
            if (ch.getVisibility() == GONE) continue;
            measureChild(ch, wSpec, hSpec);
            int cw = ch.getMeasuredWidth(), chh = ch.getMeasuredHeight();
            if (lineW + cw > width && lineW > 0) {
                totalH += lineH + vGap;
                lineW = 0;
                lineH = 0;
            }
            lineW += cw + hGap;
            lineH = Math.max(lineH, chh);
        }
        totalH += lineH;
        setMeasuredDimension(width, totalH + getPaddingTop() + getPaddingBottom());
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int width = getWidth() - getPaddingLeft() - getPaddingRight();
        int x = getPaddingLeft(), y = getPaddingTop(), lineH = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View ch = getChildAt(i);
            if (ch.getVisibility() == GONE) continue;
            int cw = ch.getMeasuredWidth(), chh = ch.getMeasuredHeight();
            if (x - getPaddingLeft() + cw > width && x > getPaddingLeft()) {
                x = getPaddingLeft();
                y += lineH + vGap;
                lineH = 0;
            }
            ch.layout(x, y, x + cw, y + chh);
            x += cw + hGap;
            lineH = Math.max(lineH, chh);
        }
    }
}
