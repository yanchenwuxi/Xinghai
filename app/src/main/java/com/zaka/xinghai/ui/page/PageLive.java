package com.zaka.xinghai.ui.page;

import android.view.LayoutInflater;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.zaka.xinghai.R;
import com.zaka.xinghai.core.App;
import com.zaka.xinghai.net.Http;
import com.zaka.xinghai.net.M3u;
import com.zaka.xinghai.player.XPlayer;
import com.zaka.xinghai.store.Store;
import com.zaka.xinghai.ui.MainActivity;
import com.zaka.xinghai.ui.Page;
import com.zaka.xinghai.util.U;

import java.util.ArrayList;
import java.util.List;

/** 直播：上方播放器 + 下方分组/频道列表。 */
public class PageLive extends Page implements XPlayer.Listener {

    private ListView groups;
    private GridView channels;
    private TextView empty, count, now;
    private ProgressBar spin;
    private XPlayer player;
    private final List<M3u.Group> data = new ArrayList<M3u.Group>();
    private int groupIndex = 0;
    private int channelIndex = -1;

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
        now = (TextView) v.findViewById(R.id.live_now);
        spin = (ProgressBar) v.findViewById(R.id.live_spin);
        SurfaceView surface = (SurfaceView) v.findViewById(R.id.live_surface);
        player = new XPlayer(surface);
        player.setListener(this);

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
                channelIndex = -1;
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

    @Override
    protected void onHide() {
        if (player != null) player.pause();
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
                        count.setText(total > 0 ? total + " 频道" : "");
                        if (data.size() == 0) {
                            empty.setVisibility(View.VISIBLE);
                            empty.setText("直播源没取到\n到「我的」粘贴 m3u 地址");
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

    private void playChannel(int index) {
        if (groupIndex < 0 || groupIndex >= data.size()) return;
        List<M3u.Channel> list = data.get(groupIndex).channels;
        if (index < 0 || index >= list.size()) return;
        channelIndex = index;
        M3u.Channel c = list.get(index);
        now.setText("正在播放：" + c.name);
        spin.setVisibility(View.VISIBLE);
        player.play(c.url);
        if (channels.getAdapter() instanceof BaseAdapter) {
            ((BaseAdapter) channels.getAdapter()).notifyDataSetChanged();
        }
    }

    @Override
    public void onPrepared(int width, int height) {
        spin.setVisibility(View.GONE);
    }

    @Override
    public void onError(int what, int extra) {
        spin.setVisibility(View.GONE);
        now.setText("播放失败，换个频道试试");
    }

    @Override
    public void onCompletion() {
    }

    @Override
    public void onInfo(String msg) {
    }

    @Override
    public void onBuffer(int percent) {
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
            if (convertView instanceof TextView) tv = (TextView) convertView;
            else {
                tv = new TextView(act);
                tv.setTextSize(13);
                tv.setPadding(U.dp(10), U.dp(14), U.dp(8), U.dp(14));
                tv.setMaxLines(2);
            }
            M3u.Group g = data.get(position);
            tv.setText(g.name);
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
                box.setPadding(U.dp(10), U.dp(12), U.dp(10), U.dp(12));
                ImageView iv = new ImageView(act);
                iv.setLayoutParams(new android.widget.LinearLayout.LayoutParams(U.dp(20), U.dp(20)));
                iv.setImageResource(R.drawable.ic_live);
                box.addView(iv);
                TextView tv = new TextView(act);
                android.widget.LinearLayout.LayoutParams tp =
                        new android.widget.LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
                tp.leftMargin = U.dp(8);
                tv.setLayoutParams(tp);
                tv.setTextSize(13);
                tv.setTextColor(0xffffffff);
                tv.setMaxLines(2);
                box.addView(tv);
            }
            boolean on = position == channelIndex;
            box.setBackgroundColor(on ? 0x33ff6a00 : 0x00000000);
            box.setBackgroundResource(R.drawable.bg_card_sel);
            ((TextView) box.getChildAt(1)).setText(list.get(position).name);
            ((TextView) box.getChildAt(1)).setTextColor(on ? 0xffff9800 : 0xffffffff);
            box.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    playChannel(position);
                }
            });
            return box;
        }
    }
}
