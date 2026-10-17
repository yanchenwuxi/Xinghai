package com.zaka.xinghai.ui.page;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.zaka.xinghai.R;
import com.zaka.xinghai.core.App;
import com.zaka.xinghai.model.Vod;
import com.zaka.xinghai.net.Http;
import com.zaka.xinghai.net.M3u;
import com.zaka.xinghai.store.Store;
import com.zaka.xinghai.ui.MainActivity;
import com.zaka.xinghai.ui.Page;
import com.zaka.xinghai.ui.PlayActivity;
import com.zaka.xinghai.util.U;

import java.util.ArrayList;
import java.util.List;

/** 直播：左侧分组 + 右侧频道网格（点开直接播）。 */
public class PageLive extends Page {

    private ListView groups;
    private GridView channels;
    private TextView empty, count;
    private final List<M3u.Group> data = new ArrayList<M3u.Group>();
    private int groupIndex = 0;

    public PageLive(MainActivity act) {
        super(act);
    }

    @Override
    protected View onCreate() {
        View v = LayoutInflater.from(act).inflate(R.layout.page_live, null);
        groups = (ListView) v.findViewById(R.id.group_list);
        channels = (GridView) v.findViewById(R.id.channel_grid);
        empty = (TextView) v.findViewById(R.id.live_empty);
        count = (TextView) v.findViewById(R.id.live_count);
        v.findViewById(R.id.live_refresh).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                load(true);
            }
        });
        groups.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(android.widget.AdapterView<?> parent, View view, int position, long id) {
                groupIndex = position;
                ((BaseAdapter) groups.getAdapter()).notifyDataSetChanged();
                showGroup();
            }
        });
        return v;
    }

    @Override
    protected void onFirstShow() {
        load(false);
    }

    private void load(boolean force) {
        empty.setVisibility(View.VISIBLE);
        empty.setText("正在加载直播源…");
        final String url = U.empty(Store.liveUrl()) ? M3u.BUILTIN : Store.liveUrl();
        App.run(new Runnable() {
            @Override
            public void run() {
                String text = null;
                if (!force) text = Store.liveRaw();
                if (U.empty(text)) {
                    try {
                        text = Http.get(url, Http.headers("User-Agent", Http.UA), 15000);
                        Store.setLiveRaw(text);
                    } catch (Throwable t) {
                        text = null;
                    }
                }
                final List<M3u.Group> list = text == null ? new ArrayList<M3u.Group>() : M3u.parse(text);
                App.onUi(new Runnable() {
                    @Override
                    public void run() {
                        data.clear();
                        data.addAll(list);
                        int total = 0;
                        for (M3u.Group g : data) total += g.channels.size();
                        count.setText(total > 0 ? total + " 个频道" : "");
                        if (data.size() == 0) {
                            empty.setVisibility(View.VISIBLE);
                            empty.setText("直播源没取到\n到「我的 → 直播源」换成你自己的 m3u 地址");
                            groups.setAdapter(null);
                            channels.setAdapter(null);
                            return;
                        }
                        empty.setVisibility(View.GONE);
                        groupIndex = 0;
                        groups.setAdapter(new GroupAdapter());
                        showGroup();
                    }
                });
            }
        });
    }

    private void showGroup() {
        if (groupIndex < 0 || groupIndex >= data.size()) return;
        channels.setAdapter(new ChannelAdapter(data.get(groupIndex).channels));
    }

    private class GroupAdapter extends BaseAdapter {
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
        public View getView(int position, View convertView, ViewGroup parent) {
            TextView tv;
            if (convertView instanceof TextView) {
                tv = (TextView) convertView;
            } else {
                tv = new TextView(act);
                tv.setTextSize(13);
                tv.setPadding(U.dp(12), U.dp(14), U.dp(8), U.dp(14));
                tv.setMaxLines(2);
            }
            M3u.Group g = data.get(position);
            tv.setText(g.name + "\n" + g.channels.size() + " 个");
            boolean on = position == groupIndex;
            tv.setTextColor(on ? 0xffffffff : 0x99ffffff);
            tv.setBackgroundColor(on ? 0x22ff6a00 : 0x00000000);
            return tv;
        }
    }

    private class ChannelAdapter extends BaseAdapter {
        private final List<M3u.Channel> list;

        ChannelAdapter(List<M3u.Channel> l) {
            list = l;
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
        public View getView(final int position, View convertView, ViewGroup parent) {
            android.widget.LinearLayout box;
            if (convertView instanceof android.widget.LinearLayout) {
                box = (android.widget.LinearLayout) convertView;
            } else {
                box = new android.widget.LinearLayout(act);
                box.setOrientation(android.widget.LinearLayout.HORIZONTAL);
                box.setGravity(android.view.Gravity.CENTER_VERTICAL);
                box.setBackgroundResource(R.drawable.bg_card_sel);
                box.setPadding(U.dp(12), U.dp(14), U.dp(12), U.dp(14));

                ImageView iv = new ImageView(act);
                iv.setLayoutParams(new android.widget.LinearLayout.LayoutParams(U.dp(22), U.dp(22)));
                iv.setImageResource(R.drawable.ic_live);
                iv.setAlpha(0.85f);
                box.addView(iv);

                TextView tv = new TextView(act);
                android.widget.LinearLayout.LayoutParams tp =
                        new android.widget.LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1);
                tp.leftMargin = U.dp(10);
                tv.setLayoutParams(tp);
                tv.setTextSize(13);
                tv.setTextColor(0xffffffff);
                tv.setMaxLines(2);
                box.addView(tv);
            }
            M3u.Channel c = list.get(position);
            ((TextView) box.getChildAt(1)).setText(c.name);
            box.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Vod vod = new Vod();
                    vod.id = "live_" + position;
                    vod.name = list.get(position).name;
                    vod.siteKey = "live";
                    vod.flags.add(new Vod.Flag(0, "直播"));
                    List<Vod.Episode> eps = new ArrayList<Vod.Episode>();
                    eps.add(new Vod.Episode("直播", list.get(position).url));
                    vod.episodes.add(eps);
                    PlayActivity.go(act, vod, 0, 0, false);
                }
            });
            return box;
        }
    }
}
