package com.zaka.xinghai.ui.dialog;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.GridView;
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

    /** 多列分类网格（全部分类面板） */
    public static Sheet showGrid(Context c, String title, final List<String> names, int selected,
                                 final int columns, final OnPick cb) {
        final Sheet sheet = new Sheet(c, title);
        if (names == null || names.size() == 0) {
            TextView empty = U.text(c, "没有分类", 13, 0x8fffffff);
            empty.setGravity(Gravity.CENTER);
            sheet.add(empty);
            sheet.show();
            return sheet;
        }
        GridView grid = new GridView(c);
        grid.setNumColumns(Math.max(2, columns));
        grid.setHorizontalSpacing(U.dp(8));
        grid.setVerticalSpacing(U.dp(8));
        grid.setPadding(U.dp(4), U.dp(4), U.dp(4), U.dp(4));
        grid.setAdapter(new android.widget.BaseAdapter() {
            @Override
            public int getCount() { return names.size(); }
            @Override
            public Object getItem(int position) { return names.get(position); }
            @Override
            public long getItemId(int position) { return position; }
            @Override
            public View getView(int position, View convertView, android.view.ViewGroup parent) {
                TextView tv;
                if (convertView instanceof TextView) {
                    tv = (TextView) convertView;
                } else {
                    tv = U.text(c, "", 13, 0xffffffff);
                    tv.setGravity(Gravity.CENTER);
                    tv.setPadding(U.dp(6), U.dp(12), U.dp(6), U.dp(12));
                    tv.setBackgroundResource(R.drawable.bg_chip);
                    tv.setSingleLine(true);
                }
                tv.setText(names.get(position));
                boolean on = position == selected;
                tv.setBackgroundResource(on ? R.drawable.bg_chip_sel : R.drawable.bg_chip);
                tv.setTextColor(on ? 0xffffffff : 0xffcccccc);
                return tv;
            }
        });
        grid.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(android.widget.AdapterView<?> parent, View view, int position, long id) {
                sheet.dismiss();
                if (cb != null) cb.onPick(position, names.get(position));
            }
        });
        // 给网格一个合理高度
        int rows = (names.size() + columns - 1) / columns;
        int gh = Math.min(U.dp(52) * rows + U.dp(16), U.dp(420));
        android.widget.LinearLayout.LayoutParams lp =
                new android.widget.LinearLayout.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT, gh);
        grid.setLayoutParams(lp);
        sheet.add(grid);
        sheet.show();
        return sheet;
    }

}
