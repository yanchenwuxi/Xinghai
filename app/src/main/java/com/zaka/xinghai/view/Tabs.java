package com.zaka.xinghai.view;

import android.content.Context;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.zaka.xinghai.util.U;

import java.util.ArrayList;
import java.util.List;

/**
 * 可横向滚动的文字标签条（分类 Tab / 选集分组 Tab）。
 * 选中项橙色加粗并自动滚到可见位置，跟参照的行为一致。
 */
public class Tabs {

    public interface OnSelect {
        void onSelect(int index);
    }

    private final HorizontalScrollView scroll;
    private final LinearLayout bar;
    private final List<TextView> items = new ArrayList<TextView>();
    private int selected = -1;
    private OnSelect cb;
    private float sizeSp = 14f;
    private int padH = U.dp(10);

    public Tabs(HorizontalScrollView scroll, LinearLayout bar) {
        this.scroll = scroll;
        this.bar = bar;
    }

    public void setOnSelect(OnSelect cb) {
        this.cb = cb;
    }

    public void setTextSize(float sp) {
        sizeSp = sp;
    }

    public void setPadH(int px) {
        padH = px;
    }

    public int selected() {
        return selected;
    }

    public void clear() {
        bar.removeAllViews();
        items.clear();
        selected = -1;
    }

    public void set(List<String> names) {
        clear();
        if (names == null) return;
        Context ctx = bar.getContext();
        for (int i = 0; i < names.size(); i++) {
            final int idx = i;
            TextView tv = new TextView(ctx);
            tv.setText(names.get(i));
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
            tv.setGravity(Gravity.CENTER);
            tv.setSingleLine(true);
            tv.setPadding(padH, U.dp(4), padH, U.dp(4));
            tv.setTextColor(0x8fffffff);
            tv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    select(idx, true);
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = U.dp(2);
            bar.addView(tv, lp);
            items.add(tv);
        }
        if (names.size() > 0) select(0, false);
    }

    public void select(int index, boolean notify) {
        if (index < 0 || index >= items.size()) return;
        selected = index;
        for (int i = 0; i < items.size(); i++) {
            TextView tv = items.get(i);
            boolean on = i == index;
            tv.setTextColor(on ? 0xffffb40a : 0x8fffffff);
            tv.setTypeface(on ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            tv.setBackgroundDrawable(null);
        }
        scrollTo(index);
        if (notify && cb != null) cb.onSelect(index);
    }

    private void scrollTo(int index) {
        if (index < 0 || index >= items.size() || scroll == null) return;
        final View v = items.get(index);
        scroll.post(new Runnable() {
            @Override
            public void run() {
                int x = v.getLeft() - U.dp(30);
                scroll.smoothScrollTo(Math.max(0, x), 0);
            }
        });
    }

    public int count() {
        return items.size();
    }

    public static Tabs wrap(HorizontalScrollView scroll, LinearLayout bar) {
        return new Tabs(scroll, bar);
    }
}
