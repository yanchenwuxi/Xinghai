package com.zaka.xinghai.ui.page;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.GridView;
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

    private GridView list;
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
        list = (GridView) v.findViewById(R.id.short_list);
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
        public View getView(final int position, View convertView, ViewGroup parent) {
            View v = convertView;
            if (v == null) {
                v = LayoutInflater.from(act).inflate(R.layout.item_vod, parent, false);
            }
            final Vod vod = data.get(position);
            TextView title = (TextView) v.findViewById(R.id.title);
            TextView note = (TextView) v.findViewById(R.id.note);
            ImageView pic = (ImageView) v.findViewById(R.id.pic);
            if (title != null) title.setText(vod.name);
            if (note != null) {
                if (U.empty(vod.note)) note.setVisibility(View.GONE);
                else { note.setVisibility(View.VISIBLE); note.setText(vod.note); }
            }
            if (pic != null) {
                if (!U.empty(vod.pic)) Img.load(pic, vod.pic);
                else pic.setImageDrawable(null);
            }
            v.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    DetailActivity.go(act, vod);
                }
            });
            return v;
        }
    }
}
