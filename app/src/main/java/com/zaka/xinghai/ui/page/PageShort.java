package com.zaka.xinghai.ui.page;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.zaka.xinghai.R;
import com.zaka.xinghai.model.Site;
import com.zaka.xinghai.model.Vod;
import com.zaka.xinghai.net.Cms;
import com.zaka.xinghai.store.Store;
import com.zaka.xinghai.ui.DetailActivity;
import com.zaka.xinghai.ui.MainActivity;
import com.zaka.xinghai.ui.Page;
import com.zaka.xinghai.util.Img;
import com.zaka.xinghai.util.U;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** 短剧：竖向大卡片流，点开进详情（数据从当前接口按「短剧」取）。 */
public class PageShort extends Page {

    private ListView list;
    private TextView empty;
    private ProgressBar loading;
    private final List<Vod> data = new ArrayList<Vod>();
    private int seed = 0;

    public PageShort(MainActivity act) {
        super(act);
    }

    @Override
    protected View onCreate() {
        View v = LayoutInflater.from(act).inflate(R.layout.page_short, null);
        list = (ListView) v.findViewById(R.id.short_list);
        empty = (TextView) v.findViewById(R.id.short_empty);
        loading = (ProgressBar) v.findViewById(R.id.short_loading);
        v.findViewById(R.id.short_refresh).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                seed++;
                load(true);
            }
        });
        v.findViewById(R.id.tv_switch).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                seed++;
                load(true);
            }
        });
        return v;
    }

    @Override
    protected void onFirstShow() {
        load(false);
    }

    private void load(final boolean force) {
        final Site site = Store.currentSite();
        if (site == null) {
            empty.setText("还没配置接口");
            return;
        }
        if (!force && data.size() > 0) return;
        loading.setVisibility(View.VISIBLE);
        empty.setText("加载中…");
        bg(new Runnable() {
            @Override
            public void run() {
                List<Vod> all = new ArrayList<Vod>();
                // 先从「短剧」分类取，取不到退回全站最新
                try {
                    List<com.zaka.xinghai.model.Cate> cates = Cms.types(site);
                    String cid = "";
                    for (com.zaka.xinghai.model.Cate c : cates) {
                        if (c.name.contains("短剧") || c.name.contains("爽剧") || c.name.contains("微短")) {
                            cid = c.id;
                            break;
                        }
                    }
                    all = Cms.list(site, cid, 1 + (seed % 3), null);
                } catch (Throwable ignored) {
                }
                if (all.size() == 0) {
                    try {
                        all = Cms.search(site, "短剧");
                    } catch (Throwable ignored) {
                    }
                }
                final List<Vod> out = all;
                ui(new Runnable() {
                    @Override
                    public void run() {
                        loading.setVisibility(View.GONE);
                        data.clear();
                        data.addAll(out);
                        if (data.size() == 0) {
                            empty.setText("这个接口没取到短剧数据\n换个接口或点右上角刷新");
                            return;
                        }
                        empty.setVisibility(View.GONE);
                        list.setAdapter(new ShortAdapter());
                    }
                });
            }
        });
    }

    private class ShortAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return data.size();
        }

        @Override
        public Object getItem(int position) {
            return data.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View v, ViewGroup parent) {
            if (v == null) {
                android.widget.FrameLayout box = new android.widget.FrameLayout(act);
                box.setLayoutParams(new android.widget.AbsListView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, U.dp(190)));
                box.setBackgroundResource(R.drawable.bg_card);

                ImageView bg = new ImageView(act);
                bg.setId(1001);
                bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
                bg.setLayoutParams(new android.widget.FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                box.addView(bg);

                View scrim = new View(act);
                scrim.setBackgroundResource(R.drawable.bg_scrim_bottom);
                scrim.setLayoutParams(new android.widget.FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                box.addView(scrim);

                android.widget.LinearLayout info = new android.widget.LinearLayout(act);
                info.setOrientation(android.widget.LinearLayout.VERTICAL);
                info.setGravity(android.view.Gravity.BOTTOM);
                info.setPadding(U.dp(12), U.dp(12), U.dp(12), U.dp(12));
                android.widget.FrameLayout.LayoutParams ilp =
                        new android.widget.FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
                box.addView(info, ilp);

                TextView title = new TextView(act);
                title.setId(1002);
                title.setTextSize(16);
                title.setTextColor(Color.WHITE);
                title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                title.setMaxLines(2);
                info.addView(title);

                TextView note = new TextView(act);
                note.setId(1003);
                note.setTextSize(11);
                note.setTextColor(0xccffffff);
                note.setPadding(0, U.dp(6), 0, 0);
                note.setMaxLines(2);
                info.addView(note);

                TextView play = new TextView(act);
                play.setId(1004);
                play.setText("立即观看");
                play.setTextSize(12);
                play.setTextColor(0xff001a37);
                play.setGravity(android.view.Gravity.CENTER);
                play.setBackgroundResource(R.drawable.bg_btn_accent);
                android.widget.LinearLayout.LayoutParams plp =
                        new android.widget.LinearLayout.LayoutParams(U.dp(88), U.dp(30));
                plp.topMargin = U.dp(10);
                play.setLayoutParams(plp);
                info.addView(play);

                v = box;
            }
            final Vod vod = data.get(position);
            Img.load((ImageView) v.findViewById(1001), vod.pic);
            ((TextView) v.findViewById(1002)).setText(vod.name);
            StringBuilder sb = new StringBuilder();
            if (!U.empty(vod.note)) sb.append(vod.note);
            if (!U.empty(vod.type)) sb.append(sb.length() > 0 ? "  ·  " : "").append(vod.type);
            if (!U.empty(vod.year)) sb.append(sb.length() > 0 ? "  ·  " : "").append(vod.year);
            ((TextView) v.findViewById(1003)).setText(sb.toString());
            View.OnClickListener go = new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    DetailActivity.go(act, vod);
                }
            };
            v.findViewById(1004).setOnClickListener(go);
            v.setOnClickListener(go);
            return v;
        }
    }
}
