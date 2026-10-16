package com.zaka.xinghai.view;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridView;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.zaka.xinghai.R;
import com.zaka.xinghai.model.Vod;
import com.zaka.xinghai.util.U;

import java.util.ArrayList;
import java.util.List;

/**
 * 线路 / 画质 / 选集 / 推荐 四块（详情页与播放页共用）。
 * 选集每 50 集分一组，跟参照一样是「分组 Tab + 网格」。
 */
public class VodParts {

    public interface OnEpisode {
        void onEpisode(int flagIndex, int epIndex);
    }

    public interface OnFlag {
        void onFlag(int flagIndex);
    }

    /** 线路（或画质）横向标签行 */
    public static LinearLayout flags(Context c, String label, final List<String> names,
                                     int selected, final OnFlag cb) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(U.dp(12), U.dp(6), U.dp(12), U.dp(2));

        TextView t = U.text(c, label, 14, 0xffffffff);
        t.setPadding(U.dp(12), U.dp(4), U.dp(12), U.dp(4));
        box.addView(t);

        HorizontalScrollView hs = new HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout bar = new LinearLayout(c);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(U.dp(6), U.dp(2), U.dp(6), U.dp(2));

        for (int i = 0; i < names.size(); i++) {
            final int idx = i;
            TextView tv = U.text(c, names.get(i), 13, 0xffffffff);
            tv.setGravity(Gravity.CENTER);
            tv.setBackgroundResource(R.drawable.bg_flag_sel);
            tv.setPadding(U.dp(10), 0, U.dp(10), 0);
            tv.setSelected(idx == selected);
            if (idx == selected) tv.setTextColor(0xffffb40a);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, U.dp(30));
            lp.rightMargin = U.dp(8);
            tv.setLayoutParams(lp);
            tv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ViewGroup p = (ViewGroup) v.getParent();
                    for (int k = 0; k < p.getChildCount(); k++) {
                        View ch = p.getChildAt(k);
                        ch.setSelected(ch == v);
                        ((TextView) ch).setTextColor(ch == v ? 0xffffb40a : 0xffffffff);
                    }
                    if (cb != null) cb.onFlag(idx);
                }
            });
            bar.addView(tv);
        }
        hs.addView(bar);
        box.addView(hs);
        return box;
    }

    /** 选集：分组 Tab + 剧集网格 */
    public static class Eps {
        public LinearLayout root;
        private final Context c;
        private final Tabs tabs;
        private final GridView grid;
        private final OnEpisode cb;
        private List<Vod.Episode> list = new ArrayList<Vod.Episode>();
        private int flagIndex = 0;
        private int epIndex = 0;
        private int groupSize = 50;

        public Eps(Context c, final OnEpisode cb) {
            this.c = c;
            this.cb = cb;
            root = new LinearLayout(c);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(U.dp(12), U.dp(6), U.dp(12), U.dp(6));

            TextView t = U.text(c, "选集", 14, 0xffffffff);
            t.setPadding(U.dp(12), U.dp(4), U.dp(12), U.dp(4));
            root.addView(t);

            HorizontalScrollView hs = new HorizontalScrollView(c);
            hs.setHorizontalScrollBarEnabled(false);
            LinearLayout bar = new LinearLayout(c);
            bar.setOrientation(LinearLayout.HORIZONTAL);
            bar.setPadding(U.dp(4), 0, U.dp(4), 0);
            hs.addView(bar);
            LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, U.dp(26));
            root.addView(hs, hlp);
            tabs = new Tabs(hs, bar);
            tabs.setTextSize(13f);
            tabs.setOnSelect(new Tabs.OnSelect() {
                @Override
                public void onSelect(int index) {
                    showGroup(index);
                }
            });

            grid = new GridView(c);
            grid.setNumColumns(5);
            grid.setHorizontalSpacing(U.dp(5));
            grid.setVerticalSpacing(U.dp(5));
            grid.setPadding(0, U.dp(6), 0, U.dp(6));
            grid.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            root.addView(grid);
        }

        public void set(int flagIndex, List<Vod.Episode> eps, int currentEp) {
            this.flagIndex = flagIndex;
            this.list = eps == null ? new ArrayList<Vod.Episode>() : eps;
            this.epIndex = currentEp;
            List<String> groups = new ArrayList<String>();
            if (list.size() <= groupSize) {
                groups.add("共 " + list.size() + " 集");
            } else {
                int n = (list.size() + groupSize - 1) / groupSize;
                for (int i = 0; i < n; i++) {
                    int from = i * groupSize + 1;
                    int to = Math.min(list.size(), (i + 1) * groupSize);
                    groups.add(from + "-" + to);
                }
            }
            tabs.set(groups);
            int g = currentEp / groupSize;
            if (g > 0 && g < groups.size()) tabs.select(g, true);
            else showGroup(0);
        }

        private void showGroup(int group) {
            final int from = group * groupSize;
            final int to = Math.min(list.size(), from + groupSize);
            List<Vod.Episode> sub = list.subList(Math.min(from, list.size()), to);
            grid.setAdapter(new EpsAdapter(c, sub, from, epIndex, new EpsAdapter.OnClick() {
                @Override
                public void onClick(int realIndex) {
                    epIndex = realIndex;
                    if (cb != null) cb.onEpisode(flagIndex, realIndex);
                    grid.invalidateViews();
                }
            }));
            grid.setVisibility(sub.size() == 0 ? View.GONE : View.VISIBLE);
        }

        public int epIndex() {
            return epIndex;
        }
    }

    /** 推荐横滑 */
    public static LinearLayout recommend(Context c, List<Vod> list, final OnVod cb) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, U.dp(10), 0, U.dp(6));

        TextView t = U.text(c, "相关推荐", 14, 0xffffffff);
        t.setPadding(U.dp(12), U.dp(4), U.dp(12), U.dp(6));
        box.addView(t);

        HorizontalScrollView hs = new HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(U.dp(8), 0, U.dp(8), 0);
        for (final Vod v : list) {
            LinearLayout item = new LinearLayout(c);
            item.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                    U.dp(96), ViewGroup.LayoutParams.WRAP_CONTENT);
            ilp.rightMargin = U.dp(10);
            item.setLayoutParams(ilp);

            com.zaka.xinghai.view.Poster p = new com.zaka.xinghai.view.Poster(c);
            p.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
            p.setBackgroundResource(R.drawable.bg_card);
            com.zaka.xinghai.util.Img.load(p, v.pic);
            item.addView(p, new LinearLayout.LayoutParams(
                    U.dp(96), U.dp(122)));

            TextView n = U.text(c, v.name, 11, 0xffffffff);
            n.setMaxLines(1);
            n.setPadding(0, U.dp(4), 0, 0);
            item.addView(n);

            item.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (cb != null) cb.onVod(v);
                }
            });
            row.addView(item);
        }
        hs.addView(row);
        box.addView(hs);
        return box;
    }

    public interface OnVod {
        void onVod(Vod v);
    }

    /** 剧集网格适配器：选中项橙色描边 */
    public static class EpsAdapter extends android.widget.BaseAdapter {
        public interface OnClick {
            void onClick(int realIndex);
        }

        private final Context c;
        private final List<Vod.Episode> list;
        private final int offset;
        private final int selected;
        private final OnClick cb;

        public EpsAdapter(Context c, List<Vod.Episode> list, int offset, int selected, OnClick cb) {
            this.c = c;
            this.list = list;
            this.offset = offset;
            this.selected = selected;
            this.cb = cb;
        }

        @Override
        public int getCount() {
            return list.size();
        }

        @Override
        public Object getItem(int position) {
            return list.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(final int position, View v, ViewGroup parent) {
            TextView tv;
            if (v instanceof TextView) tv = (TextView) v;
            else {
                tv = new TextView(c);
                tv.setGravity(Gravity.CENTER);
                tv.setTextSize(13);
                tv.setPadding(U.dp(4), U.dp(9), U.dp(4), U.dp(9));
                tv.setMaxLines(2);
                tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
            }
            Vod.Episode e = list.get(position);
            String nm = U.empty(e.name) ? ("第" + (offset + position + 1) + "集") : e.name;
            tv.setText(nm);
            boolean on = (offset + position) == selected;
            tv.setBackgroundResource(on ? R.drawable.bg_flag_on : R.drawable.bg_flag);
            tv.setTextColor(on ? 0xffffb40a : 0xffffffff);
            tv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (cb != null) cb.onClick(offset + position);
                }
            });
            return tv;
        }
    }
}
