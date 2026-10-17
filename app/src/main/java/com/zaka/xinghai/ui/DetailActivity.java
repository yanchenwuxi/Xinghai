package com.zaka.xinghai.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.zaka.xinghai.R;
import com.zaka.xinghai.core.App;
import com.zaka.xinghai.model.Site;
import com.zaka.xinghai.model.Vod;
import com.zaka.xinghai.net.AdFilter;
import com.zaka.xinghai.net.Cms;
import com.zaka.xinghai.net.Spider;
import com.zaka.xinghai.player.XPlayer;
import com.zaka.xinghai.store.Store;
import com.zaka.xinghai.util.U;
import com.zaka.xinghai.view.VodInfoView;
import com.zaka.xinghai.view.VodParts;

import java.util.ArrayList;
import java.util.List;

/**
 * 点播详情：顶部内嵌播放器 + 下方信息/线路/选集。
 * 点选集在上方直接播，不跳转竖屏 PlayActivity；全屏按钮再进 PlayActivity。
 */
public class DetailActivity extends Activity implements XPlayer.Listener {

    public static Vod handoff;

    private Vod vod;
    private LinearLayout content;
    private ProgressBar loading, spin;
    private FrameLayout playerBox;
    private SurfaceView surface;
    private ImageView playIcon, expand;
    private TextView title;
    private XPlayer player;
    private VodParts.Eps epsView;
    private int flagIndex = 0;
    private int epIndex = 0;
    private final List<Vod> recommend = new ArrayList<Vod>();
    private LinearLayout recommendBox;

    public static void go(Context c, Vod v) {
        handoff = v;
        c.startActivity(new Intent(c, DetailActivity.class));
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        U.init(getResources());
        setContentView(R.layout.activity_detail);
        vod = handoff;
        if (vod == null) {
            finish();
            return;
        }
        content = (LinearLayout) findViewById(R.id.detail_content);
        loading = (ProgressBar) findViewById(R.id.detail_loading);
        spin = (ProgressBar) findViewById(R.id.detail_spin);
        playerBox = (FrameLayout) findViewById(R.id.detail_player_box);
        surface = (SurfaceView) findViewById(R.id.detail_surface);
        playIcon = (ImageView) findViewById(R.id.detail_play_icon);
        expand = (ImageView) findViewById(R.id.detail_expand);
        title = (TextView) findViewById(R.id.detail_title);
        title.setText(vod.name);

        player = new XPlayer(surface);
        player.setListener(this);

        // 16:9 高度
        int h = getResources().getDisplayMetrics().widthPixels * 9 / 16;
        ViewGroup.LayoutParams lp = playerBox.getLayoutParams();
        lp.height = h;
        playerBox.setLayoutParams(lp);

        findViewById(R.id.detail_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        expand.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 全屏：移交到 PlayActivity（带当前集）
                PlayActivity.go(DetailActivity.this, vod, flagIndex, epIndex, true);
            }
        });
        playerBox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (player != null) {
                    player.toggle();
                    refreshIcon();
                }
            }
        });

        render(false);
        loadDetail();
        loadRecommend();
    }

    private void render(boolean detail) {
        content.removeAllViews();

        VodInfoView info = new VodInfoView(this);
        info.bind(vod);
        content.addView(info);

        if (vod.flags.size() > 0) {
            List<String> names = new ArrayList<String>();
            for (Vod.Flag f : vod.flags) names.add(f.name);
            content.addView(VodParts.flags(this, "线路", names, flagIndex, new VodParts.OnFlag() {
                @Override
                public void onFlag(int idx) {
                    flagIndex = idx;
                    epIndex = 0;
                    render(true);
                }
            }));
        }

        if (vod.subCates != null && vod.subCates.size() > 0) {
            List<String> qs = new ArrayList<String>();
            for (com.zaka.xinghai.model.Cate c : vod.subCates) qs.add(c.name);
            content.addView(VodParts.flags(this, "画质", qs, 0, null));
        }

        epsView = new VodParts.Eps(this, new VodParts.OnEpisode() {
            @Override
            public void onEpisode(int fi, int ei) {
                flagIndex = fi;
                epIndex = ei;
                playHere();
            }
        });
        if (vod.flags.size() > 0) {
            epsView.set(flagIndex, vod.eps(flagIndex), epIndex);
            content.addView(epsView.root);
        } else if (detail) {
            TextView t = U.text(this, "这个源没给出播放线路", 13, 0x8fffffff);
            t.setPadding(U.dp(12), U.dp(10), U.dp(12), U.dp(10));
            content.addView(t);
        }

        if (recommend.size() > 0) {
            recommendBox = new LinearLayout(this);
            recommendBox.setOrientation(LinearLayout.VERTICAL);
            content.addView(recommendBox);
            // 简化：文字提示
            TextView rh = U.text(this, "相关推荐", 14, 0xffffffff);
            rh.setPadding(U.dp(12), U.dp(14), U.dp(12), U.dp(6));
            content.addView(rh);
            for (int i = 0; i < Math.min(6, recommend.size()); i++) {
                final Vod r = recommend.get(i);
                TextView tv = U.text(this, "· " + r.name, 13, 0xffcccccc);
                tv.setPadding(U.dp(14), U.dp(6), U.dp(12), U.dp(6));
                tv.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        DetailActivity.go(DetailActivity.this, r);
                        finish();
                    }
                });
                content.addView(tv);
            }
        }
    }

    private void playHere() {
        List<Vod.Episode> eps = vod.eps(flagIndex);
        if (eps == null || epIndex < 0 || epIndex >= eps.size()) {
            U.toast("无效剧集");
            return;
        }
        Vod.Episode e = eps.get(epIndex);
        String url = e.url;
        if (U.empty(url)) {
            U.toast("本集无地址");
            return;
        }
        String before = url;
        try {
            url = AdFilter.unwrap(url);
            url = AdFilter.cleanUrl(url);
        } catch (Throwable ignored) {
        }
        title.setText(vod.name + " · " + e.name);
        spin.setVisibility(View.VISIBLE);
        playIcon.setVisibility(View.GONE);
        player.play(url);
        if (epsView != null) epsView.set(flagIndex, eps, epIndex);
    }

    private void loadDetail() {
        final Site site = Store.site(vod.siteKey);
        if (site == null) return;
        loading.setVisibility(View.VISIBLE);
        App.run(new Runnable() {
            @Override
            public void run() {
                try {
                    final Vod d = (site.isScript() && !Spider.canUseAsCms(site))
                            ? Spider.detail(site, vod) : Cms.detail(site, vod);
                    App.onUi(new Runnable() {
                        @Override
                        public void run() {
                            loading.setVisibility(View.GONE);
                            if (d != null) {
                                if (!U.empty(d.name)) vod.name = d.name;
                                if (d.flags.size() > 0) {
                                    vod.flags = d.flags;
                                    vod.episodes = d.episodes;
                                }
                                if (!U.empty(d.content)) vod.content = d.content;
                                title.setText(vod.name);
                                render(true);
                            }
                        }
                    });
                } catch (final Throwable t) {
                    App.onUi(new Runnable() {
                        @Override
                        public void run() {
                            loading.setVisibility(View.GONE);
                        }
                    });
                }
            }
        });
    }

    private void loadRecommend() {
        final Site site = Store.site(vod.siteKey);
        if (site == null) return;
        App.run(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<Vod> list = Cms.list(site, "", 1, null);
                    App.onUi(new Runnable() {
                        @Override
                        public void run() {
                            recommend.clear();
                            if (list != null) {
                                for (Vod v : list) {
                                    if (v.id != null && v.id.equals(vod.id)) continue;
                                    recommend.add(v);
                                    if (recommend.size() >= 8) break;
                                }
                            }
                            render(true);
                        }
                    });
                } catch (Throwable ignored) {
                }
            }
        });
    }

    private void refreshIcon() {
        boolean playing = player != null && player.isPlaying();
        playIcon.setVisibility(playing ? View.GONE : View.VISIBLE);
        playIcon.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
    }

    @Override
    public void onPrepared(int width, int height) {
        spin.setVisibility(View.GONE);
        refreshIcon();
        layoutSurface(width, height);
    }

    @Override
    public void onError(int what, int extra) {
        spin.setVisibility(View.GONE);
        playIcon.setVisibility(View.VISIBLE);
        U.toast("播放失败");
    }

    @Override
    public void onCompletion() {
        // 自动下一集
        List<Vod.Episode> eps = vod.eps(flagIndex);
        if (eps != null && epIndex + 1 < eps.size()) {
            epIndex++;
            playHere();
        }
    }

    @Override
    public void onInfo(String msg) {
    }

    @Override
    public void onBuffer(int percent) {
    }

    /** 按视频比例在播放框内居中适配 */
    private void layoutSurface(int vw, int vh) {
        if (vw <= 0 || vh <= 0) return;
        int aw = playerBox.getWidth();
        int ah = playerBox.getHeight();
        if (aw <= 0 || ah <= 0) {
            aw = getResources().getDisplayMetrics().widthPixels;
            ah = aw * 9 / 16;
        }
        float video = vw * 1f / vh;
        float area = aw * 1f / ah;
        int w, h;
        if (video > area) {
            w = aw;
            h = (int) (aw / video);
        } else {
            h = ah;
            w = (int) (ah * video);
        }
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, h);
        lp.gravity = android.view.Gravity.CENTER;
        surface.setLayoutParams(lp);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (player != null) player.pause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (player != null) player.release();
    }
}
