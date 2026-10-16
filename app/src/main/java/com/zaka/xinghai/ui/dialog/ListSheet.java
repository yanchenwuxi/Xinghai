package com.zaka.xinghai.ui.dialog;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.zaka.xinghai.R;
import com.zaka.xinghai.util.U;
import com.zaka.xinghai.view.Sheet;

import java.util.List;

/** 通用列表选择面板（线路、倍速、音轨、设置项都用它）。 */
public class ListSheet {

    public interface OnPick {
        void onPick(int index, String value);
    }

    public static Sheet show(Context c, String title, List<String> items, int selected,
                             final OnPick cb) {
        final Sheet sheet = new Sheet(c, title);
        for (int i = 0; i < items.size(); i++) {
            final int idx = i;
            String name = items.get(i);
            LinearLayout row = new LinearLayout(c);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackgroundResource(R.drawable.bg_card_sel);
            row.setPadding(U.dp(14), U.dp(13), U.dp(14), U.dp(13));

            TextView tv = U.text(c, name, 14, idx == selected ? 0xffffb40a : 0xffffffff);
            tv.setSingleLine(false);
            tv.setMaxLines(2);
            if (idx == selected) tv.setTypeface(Typeface.DEFAULT_BOLD);
            row.addView(tv, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            if (idx == selected) {
                TextView ok = U.text(c, "✓", 15, 0xffffb40a);
                row.addView(ok);
            }

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = U.dp(8);
            row.setLayoutParams(lp);
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    sheet.dismiss();
                    if (cb != null) cb.onPick(idx, ((TextView) ((ViewGroup) v).getChildAt(0)).getText().toString());
                }
            });
            sheet.add(row);
        }
        sheet.show();
        return sheet;
    }

    /** 单选 + 每行带说明（字幕、站点列表用） */
    public interface OnPick2 {
        void onPick(int index);
    }

    public static Sheet list(Context c, String title, List<String> names, List<String> metas,
                             final OnPick2 cb) {
        final Sheet sheet = new Sheet(c, title);
        for (int i = 0; i < names.size(); i++) {
            final int idx = i;
            LinearLayout row = new LinearLayout(c);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setBackgroundResource(R.drawable.bg_card_sel);
            row.setPadding(U.dp(14), U.dp(11), U.dp(14), U.dp(11));

            TextView tv = U.text(c, names.get(i), 14, 0xffffffff);
            tv.setSingleLine(false);
            tv.setMaxLines(2);
            row.addView(tv);

            if (metas != null && i < metas.size() && !U.empty(metas.get(i))) {
                TextView meta = U.text(c, metas.get(i), 11, 0x8fffffff);
                meta.setPadding(0, U.dp(4), 0, 0);
                row.addView(meta);
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = U.dp(8);
            row.setLayoutParams(lp);
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    sheet.dismiss();
                    if (cb != null) cb.onPick(idx);
                }
            });
            sheet.add(row);
        }
        if (names.size() == 0) {
            TextView empty = U.text(c, "没有内容", 13, 0x8fffffff);
            empty.setGravity(Gravity.CENTER);
            sheet.add(empty);
        }
        sheet.show();
        return sheet;
    }
}
