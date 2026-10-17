package com.zaka.xinghai.ui.page;

import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.GridView;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.zaka.xinghai.R;
import com.zaka.xinghai.adapter.VodAdapter;
import com.zaka.xinghai.model.Cate;
import com.zaka.xinghai.model.Filter;
import com.zaka.xinghai.model.Site;
import com.zaka.xinghai.model.Vod;
import com.zaka.xinghai.net.Cms;
import com.zaka.xinghai.store.Store;
import com.zaka.xinghai.ui.MainActivity;
import com.zaka.xinghai.ui.Page;
import com.zaka.xinghai.ui.SearchActivity;
import com.zaka.xinghai.ui.dialog.FilterSheet;
import com.zaka.xinghai.ui.dialog.ListSheet;
import com.zaka.xinghai.util.U;
import com.zaka.xinghai.view.Tabs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 主页：搜索栏 + 分类 Tab + 筛选条 + 3 列海报网格 + 触底翻页。
 * 分类拿不到时从列表数据聚合兜底，保证任何源都有分类 Tab。
 */
public class PageHome extends Page {

    private GridView grid;
    private VodAdapter adapter;
    private Tabs tabs;
    private TextView foot, searchHint, emptyText;
    private LinearLayout empty, filterBar;
    private HorizontalScrollView filterScroll;
    private ProgressBar loading;
    private View retry;

    private final List<Cate> cates = new ArrayList<Cate>();
    private int cateIndex = 0;
    private int pageNo = 1;
    private boolean loadingMore = false;
    private boolean noMore = false;
    private long seq = 0;
    private final Map<String, String> filters = new HashMap<String, String>();
    private boolean firstLoad = true;

    public PageHome(MainActivity act) {
        super(act);
    }

    @Override
    protected View onCreate() {
        View v = LayoutInflater.from(act).inflate(R.layout.page_home, null);
        grid = (GridView) v.findViewById(R.id.grid);
        foot = (TextView) v.findViewById(R.id.foot);
        empty = (LinearLayout) v.findViewById(R.id.empty);
        emptyText = (TextView) v.findViewById(R.id.empty_text);
        retry = v.findViewById(R.id.retry);
        loading = (ProgressBar) v.findViewById(R.id.loading);
        searchHint = (TextView) v.findViewById(R.id.search_hint);
        filterBar = (LinearLayout) v.findViewById(R.id.filter_bar);
        filterScroll = (HorizontalScrollView) v.findViewById(R.id.filter_scroll);

        adapter = new VodAdapter(act, false);
        adapter.setOnClick(new VodAdapter.OnClick() {
            @Override
            public void onClick(Vod vod, int position) {
                com.zaka.xinghai.ui.DetailActivity.go(act, vod);
            }
        });
        grid.setAdapter(adapter);

        grid.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int state) {
            }

            @Override
            public void onScroll(AbsListView view, int first, int visible, int total) {
                if (loadingMore || noMore || total == 0) return;
                if (first + visible >= total - 4) load(pageNo + 1);
            }
        });

        v.findViewById(R.id.search_box).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                SearchActivity.go(act, "");
            }
        });
        v.findViewById(R.id.site_switch).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                pickSite();
            }
        });
        v.findViewById(R.id.refresh).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Cms.clearCache();
                loadTypes(true);
            }
        });
        v.findViewById(R.id.cate_menu).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                pickCate();
            }
        });
        retry.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                loadTypes(true);
            }
        });

        HorizontalScrollView hs = (HorizontalScrollView) v.findViewById(R.id.cate_scroll);
        LinearLayout bar = (LinearLayout) v.findViewById(R.id.cate_bar);

        // 左右滑动切换分类
        final GestureDetector gd = new GestureDetector(act, new GestureDetector.SimpleOnGestureListener() {
            final int MIN_DX = 120;
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                if (e1 == null || e2 == null || cates.size() == 0) return false;
                float dx = e2.getX() - e1.getX();
                float dy = e2.getY() - e1.getY();
                if (Math.abs(dx) < MIN_DX || Math.abs(dx) < Math.abs(dy) * 1.2f) return false;
                if (dx < 0 && cateIndex < cates.size() - 1) {
                    tabs.select(cateIndex + 1, true);
                    return true;
                }
                if (dx > 0 && cateIndex > 0) {
                    tabs.select(cateIndex - 1, true);
                    return true;
                }
                return false;
            }
        });
        grid.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                gd.onTouchEvent(event);
                return false; // 不拦截，保留滚动与点击
            }
        });

        tabs = new Tabs(hs, bar);
        tabs.setOnSelect(new Tabs.OnSelect() {
            @Override
            public void onSelect(int index) {
                if (index == cateIndex && !firstLoad) return;
                cateIndex = index;
                load(1);
            }
        });
        return v;
    }

    @Override
    protected void onFirstShow() {
        loadTypes(false);
    }

    private void pickSite() {
        final List<Site> list = Store.sites();
        if (list.size() == 0) {
            U.toast("还没有接口，去「我的 → 接口管理」加一个");
            return;
        }
        List<String> names = new ArrayList<String>();
        for (Site s : list) names.add(s.name + (s.enabled ? "" : "（已停用）"));
        String cur = Store.currentKey();
        int sel = 0;
        for (int i = 0; i < list.size(); i++) if (list.get(i).key.equals(cur)) sel = i;
        ListSheet.show(act, "切换接口", names, sel, new ListSheet.OnPick() {
            @Override
            public void onPick(int index, String value) {
                Store.setCurrent(list.get(index).key);
                Cms.clearCache();
                loadTypes(true);
            }
        });
    }

    private void pickCate() {
        List<String> names = new ArrayList<String>();
        for (Cate c : cates) names.add(c.name);
        if (names.size() == 0) {
            U.toast("这个源没返回分类");
            return;
        }
        ListSheet.showGrid(act, "全部分类", names, cateIndex, 3, new ListSheet.OnPick() {
            @Override
            public void onPick(int index, String value) {
                tabs.select(index, true);
            }
        });
    }

    // ------------------------------------------------------------ 加载
    private void loadTypes(final boolean forceReload) {
        final Site site = Store.currentSite();
        if (site == null) {
            empty.setVisibility(View.VISIBLE);
            emptyText.setText("还没配置接口，去「我的 → 接口管理」添加");
            return;
        }
        searchHint.setText(site.name);
        loading.setVisibility(View.VISIBLE);
        empty.setVisibility(View.GONE);
        final long my = ++seq;
        bg(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<Cate> list = Cms.types(site);
                    ui(new Runnable() {
                        @Override
                        public void run() {
                            if (my != seq) return;
                            cates.clear();
                            cates.addAll(list);
                            List<String> names = new ArrayList<String>();
                            for (Cate c : cates) names.add(c.name);
                            firstLoad = false;
                            if (names.size() == 0) {
                                tabs.clear();
                            } else {
                                tabs.set(names);
                            }
                            load(1);
                        }
                    });
                } catch (final Throwable t) {
                    ui(new Runnable() {
                        @Override
                        public void run() {
                            if (my != seq) return;
                            loading.setVisibility(View.GONE);
                            empty.setVisibility(View.VISIBLE);
                            emptyText.setText("这个接口连不上：" + t.getClass().getSimpleName()
                                    + "\n换个接口或点这里重试");
                        }
                    });
                }
            }
        });
    }

    private void load(final int page) {
        final Site site = Store.currentSite();
        if (site == null) return;
        if (page == 1) {
            noMore = false;
            loading.setVisibility(adapter.getCount() == 0 ? View.VISIBLE : View.GONE);
        }
        loadingMore = true;
        final long my = seq;
        final String cateId = cates.size() > cateIndex ? cates.get(cateIndex).id : "";
        final Map<String, String> f = new HashMap<String, String>(filters);
        bg(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<Vod> list = Cms.list(site, cateId, page, f);
                    ui(new Runnable() {
                        @Override
                        public void run() {
                            if (my != seq) return;
                            loadingMore = false;
                            loading.setVisibility(View.GONE);
                            pageNo = page;
                            if (page == 1) adapter.set(list);
                            else adapter.add(list);
                            if (list.size() < 12) noMore = true;
                            boolean none = adapter.getCount() == 0;
                            empty.setVisibility(none ? View.VISIBLE : View.GONE);
                            if (none) emptyText.setText("这个分类下暂时没有数据");
                            foot.setVisibility(none ? View.GONE : View.VISIBLE);
                            foot.setText(noMore ? act.getString(R.string.no_more) : act.getString(R.string.load_more));
                        }
                    });
                } catch (final Throwable t) {
                    ui(new Runnable() {
                        @Override
                        public void run() {
                            if (my != seq) return;
                            loadingMore = false;
                            loading.setVisibility(View.GONE);
                            if (adapter.getCount() == 0) {
                                empty.setVisibility(View.VISIBLE);
                                emptyText.setText("加载失败，点这里重试");
                            } else {
                                U.toast("这一页没加载出来，稍后再试");
                            }
                        }
                    });
                }
            }
        });
    }

    /** 筛选条：把已选条件显示出来，点「筛选」开面板 */
    private void refreshFilterBar() {
        filterBar.removeAllViews();
        filterScroll.setVisibility(View.VISIBLE);
        final List<Filter> groups = Cms.filters();
        TextView btn = U.text(act, "筛选", 12, 0xffffffff);
        btn.setBackgroundResource(R.drawable.bg_chip);
        btn.setPadding(U.dp(12), U.dp(5), U.dp(12), U.dp(5));
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FilterSheet.show(act, "筛选影片", groups, filters, new FilterSheet.OnDone() {
                    @Override
                    public void onDone(Map<String, String> result) {
                        filters.clear();
                        filters.putAll(result);
                        refreshFilterBar();
                        load(1);
                    }
                });
            }
        });
        filterBar.addView(btn);
        for (Map.Entry<String, String> e : filters.entrySet()) {
            final String key = e.getKey();
            TextView chip = U.text(act, e.getValue(), 12, 0xffffb40a);
            chip.setBackgroundResource(R.drawable.bg_chip_on);
            chip.setPadding(U.dp(12), U.dp(5), U.dp(12), U.dp(5));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = U.dp(8);
            chip.setLayoutParams(lp);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    filters.remove(key);
                    refreshFilterBar();
                    load(1);
                }
            });
            filterBar.addView(chip);
        }
    }

    @Override
    public void onShow() {
        super.onShow();
        refreshFilterBar();
    }

    @Override
    public void onLeave() {
        super.onLeave();
    }
}
