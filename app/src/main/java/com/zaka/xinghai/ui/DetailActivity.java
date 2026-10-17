package com.zaka.xinghai.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.zaka.xinghai.R;
import com.zaka.xinghai.core.App;
import com.zaka.xinghai.model.Site;
import com.zaka.xinghai.model.Vod;
import com.zaka.xinghai.net.AdFilter;
import com.zaka.xinghai.net.Cms;
import com.zaka.xinghai.store.Store;
import com.zaka.xinghai.util.U;
import com.zaka.xinghai.view.VodInfoView;
import com.zaka.xinghai.view.VodParts;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 详情页：信息区 + 线路 + 画质 + 选集 + 相关推荐（横滑）。
 * 数据：先用列表页带过来的快速渲染，再异步拉详情补全线路与剧集。
 */
public class DetailActivity extends Activity {

    /** 跨页传对象（不引 Parcelable，直接静态持有，页面级生命周期） */
    public static Vod handoff;

    private Vod vod;
    private LinearLayout content;
    private ProgressBar loading;
    private VodParts.Eps epsView;
    private int flagIndex = 0;
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
        TextView title = (TextView) findViewById(R.id.detail_title);
        title.setText(vod.name);
        findViewById(R.id.detail_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        // 内容整体下移 44dip，给顶栏留位置
        content.setPadding(0, U.dp(44), 0, 0);
        render(false);
        loadDetail();
        loadRecommend();
    }

    private void render(boolean detail) {
        content.removeAllViews();

        VodInfoView info = new VodInfoView(this);
        info.bind(vod);
        content.addView(info);

        // 线路
        if (vod.flags.size() > 0) {
            List<String> names = new ArrayList<String>();
            for (Vod.Flag f : vod.flags) names.add(f.name);
            content.addView(VodParts.flags(this, "线路", names, flagIndex, new VodParts.OnFlag() {
                @Override
                public void onFlag(int idx) {
                    flagIndex = idx;
                    render(true);
                }
            }));
        }

        // 画质（源站给了才显示）
        if (vod.subCates != null && vod.subCates.size() > 0) {
            List<String> qs = new ArrayList<String>();
            for (com.zaka.xinghai.model.Cate c : vod.subCates) qs.add(c.name);
            content.addView(VodParts.flags(this, "画质", qs, 0, null));
        }

        // 选集
        epsView = new VodParts.Eps(this, new VodParts.OnEpisode() {
            @Override
            public void onEpisode(int flagIndex, int epIndex) {
                // 进入播放页并关闭详情，避免「播放浮在详情上」叠两层
                PlayActivity.go(DetailActivity.this, vod, flagIndex, epIndex, false);
                finish();
            }
        });
        if (vod.flags.size() > 0) {
            epsView.set(flagIndex, vod.eps(flagIndex), 0);
            content.addView(epsView.root);
        } else if (detail) {
            TextView t = U.text(this, "这个源没给出播放线路", 13, 0x8fffffff);
            t.setPadding(U.dp(12), U.dp(10), U.dp(12), U.dp(10));
            content.addView(t);
        }

        // 相关推荐
        recommendBox = new LinearLayout(this);
        recommendBox.setOrientation(LinearLayout.VERTICAL);
        content.addView(recommendBox);
        renderRecommend();

        if (vod.flags.size() > 0 && detail) {
            // 详情到手：自动预热首集（点播放几乎立刻起播）
            final Vod.Episode first = vod.eps(flagIndex).size() > 0 ? vod.eps(flagIndex).get(0) : null;
            if (first != null) com.zaka.xinghai.net.HlsProxy.prefetch(first.url);
        }
    }

    private void renderRecommend() {
        if (recommendBox == null) return;
        recommendBox.removeAllViews();
        if (recommend.size() == 0) return;
        recommendBox.addView(VodParts.recommend(this, recommend, new VodParts.OnVod() {
            @Override
            public void onVod(Vod v) {
                handoff = v;
                recreate();
            }
        }));
    }

    private void loadDetail() {
        final Site site = Store.site(vod.siteKey);
        if (site == null) return;
        loading.setVisibility(View.VISIBLE);
        App.run(new Runnable() {
            @Override
            public void run() {
                try {
                    final Vod d = Cms.detail(site, vod);
                    if (d != null) {
                        d.sourceName = site.name;
                        App.onUi(new Runnable() {
                            @Override
                            public void run() {
                                loading.setVisibility(View.GONE);
                                vod = d;
                                render(true);
                            }
                        });
                    } else {
                        App.onUi(new Runnable() {
                            @Override
                            public void run() {
                                loading.setVisibility(View.GONE);
                            }
                        });
                    }
                } catch (Throwable t) {
                    App.onUi(new Runnable() {
                        @Override
                        public void run() {
                            loading.setVisibility(View.GONE);
                            U.toast("详情没拉到，网络问题");
                        }
                    });
                }
            }
        });
    }

    /** 相关推荐：片名搜同款，不够用同分类补，去重去自己 */
    private void loadRecommend() {
        final Site site = Store.currentSite();
        if (site == null) return;
        App.run(new Runnable() {
            @Override
            public void run() {
                List<Vod> out = new ArrayList<Vod>();
                try {
                    out = Cms.search(site, U.cleanName(vod.name));
                } catch (Throwable ignored) {
                }
                if (out.size() < 6 && vod.episodes.size() > 0) {
                    try {
                        List<Vod> more = Cms.list(site, "", 1, null);
                        out.addAll(more);
                    } catch (Throwable ignored) {
                    }
                }
                final List<Vod> list = new ArrayList<Vod>();
                List<String> seen = new ArrayList<String>();
                for (Vod v : out) {
                    if (v.id.equals(vod.id)) continue;
                    if (seen.contains(v.id)) continue;
                    seen.add(v.id);
                    list.add(v);
                    if (list.size() >= 12) break;
                }
                App.onUi(new Runnable() {
                    @Override
                    public void run() {
                        recommend.clear();
                        recommend.addAll(list);
                        renderRecommend();
                    }
                });
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (handoff == vod) handoff = null;
    }
}
