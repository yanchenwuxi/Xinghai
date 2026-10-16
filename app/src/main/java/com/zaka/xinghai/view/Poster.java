package com.zaka.xinghai.view;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ImageView;

/** 海报图：固定 220:280 比例（跟参照一致），宽度跟随父布局。 */
public class Poster extends ImageView {

    public Poster(Context c) {
        super(c);
    }

    public Poster(Context c, AttributeSet a) {
        super(c, a);
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec);
        if (w <= 0) {
            super.onMeasure(wSpec, hSpec);
            return;
        }
        int h = (int) (w * 280f / 220f + 0.5f);
        setMeasuredDimension(w, h);
    }
}
