package com.zaka.xinghai.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.zaka.xinghai.R;
import com.zaka.xinghai.adapter.VodAdapter;
import com.zaka.xinghai.core.App;
import com.zaka.xinghai.model.Site;
import com.zaka.xinghai.model.Vod;
import com.zaka.xinghai.net.Cms;
import com.zaka.xinghai.net.Http;
import com.zaka.xinghai.store.Store;
import com.zaka.xinghai.ui.dialog.IntroDialog;
import com.zaka.xinghai.util.U;
import com.zaka.xinghai.view.FlowLayout;

import java.util.ArrayList;
import java.util.List;

/** 搜索页：最近热搜 + 搜索历史 + 多站点并发结果。 */
public class SearchActivity extends Activity {

    private EditText input;
    private GridView grid;
    private VodAdapter adapter;
    private LinearLayout home;
    private FlowLayout flow;
    private TextView tip, clear;
    private ProgressBar loading;
    private View hotRow1, hotRow2;

    public static void go(Context c, String kw) {
        Intent i = new Intent(c, SearchActivity.class);
        i.putExtra("kw", kw);
        c.startActivity(i);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        U.init(getResources());
        setContentView(R.layout.activity_search);
        input = (EditText) findViewById(R.id.search_input);
        grid = (GridView) findViewById(R.id.result_grid);
        adapter = new VodAdapter(this, false);
        adapter.setOnClick(new VodAdapter.OnClick() {
            @Override
            public void onClick(Vod vod, int position) {
                DetailActivity.go(SearchActivity.this, vod);
            }
        });
        grid.setAdapter(adapter);
        home = (LinearLayout) findViewById(R.id.search_home);
        flow = (FlowLayout) findViewById(R.id.history_flow);
        tip = (TextView) findViewById(R.id.result_tip);
        clear = (TextView) findViewById(R.id.search_clear);
        loading = (ProgressBar) findViewById(R.id.result_loading);
        hotRow1 = findViewById(R.id.hot_row1);
        hotRow2 = findViewById(R.id.hot_row2);

        findViewById(R.id.search_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        findViewById(R.id.search_go).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doSearch(input.getText().toString());
            }
        });
        clear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                input.setText("");
                clear.setVisibility(View.GONE);
            }
        });
        findViewById(R.id.history_clear).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Store.clearSearch();
                fillHistory();
                U.toast("搜索历史已清空");
            }
        });
        input.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    doSearch(input.getText().toString());
                    return true;
                }
                return false;
            }
        });
        input.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {
            }

            @Override
            public void onTextChanged(CharSequence s, int st, int b, int c) {
                clear.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
            }
        });

        fillHistory();
        fillHot();

        String kw = getIntent().getStringExtra("kw");
        if (!U.empty(kw)) {
            input.setText(kw);
            clear.setVisibility(View.VISIBLE);
            doSearch(kw);
        }
    }

    private void fillHistory() {
        flow.removeAllViews();
        List<String> list = Store.searchHistory();
        for (final String s : list) {
            TextView chip = U.text(this, s, 12, 0xffffffff);
            chip.setBackgroundResource(R.drawable.bg_chip);
            chip.setPadding(U.dp(12), U.dp(6), U.dp(12), U.dp(6));
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    input.setText(s);
                    doSearch(s);
                }
            });
            flow.addView(chip);
        }
    }

    /** 最近热搜：从当前接口最新片里挑几个（不是写死的假数据） */
    private void fillHot() {
        ((android.view.ViewGroup) hotRow1).removeAllViews();
        ((android.view.ViewGroup) hotRow2).removeAllViews();
        final Site site = Store.currentSite();
        if (site == null) return;
        App.run(new Runnable() {
            @Override
            public void run() {
                final List<String> hot = new ArrayList<String>();
                try {
                    List<Vod> list = Cms.list(site, "", 1, null);
                    for (Vod v : list) {
                        if (hot.size() >= 6) break;
                        hot.add(U.cleanName(v.name));
                    }
                } catch (Throwable ignored) {
                }
                App.onUi(new Runnable() {
                    @Override
                    public void run() {
                        for (int i = 0; i < hot.size(); i++) {
                            final String kw = hot.get(i);
                            LinearLayout row = (LinearLayout) (i < 3 ? hotRow1 : hotRow2);
                            TextView tv = U.text(SearchActivity.this, (i % 3 + 1) + "  " + kw, 13,
                                    i % 3 == 0 ? 0xffffb40a : 0xffffffff);
                            tv.setGravity(Gravity.CENTER_VERTICAL);
                            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                            tv.setPadding(0, U.dp(4), 0, U.dp(4));
                            tv.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    input.setText(kw);
                                    doSearch(kw);
                                }
                            });
                            row.addView(tv, lp);
                        }
                    }
                });
            }
        });
    }

    private void doSearch(final String kw) {
        if (U.empty(kw)) {
            U.toast("输入片名再搜");
            return;
        }
        hideKeyboard();
        home.setVisibility(View.GONE);
        grid.setVisibility(View.GONE);
        tip.setVisibility(View.VISIBLE);
        tip.setText("正在搜「" + kw + "」…");
        loading.setVisibility(View.VISIBLE);
        Store.putSearch(kw);

        App.run(new Runnable() {
            @Override
            public void run() {
                final List<Vod> all = new ArrayList<Vod>();
                List<Site> sites = Store.sites();
                // 多站点并发搜，谁先回来先显示
                List<String> urls = new ArrayList<String>();
                for (Site s : sites) if (s.enabled) urls.add(s.key);
                final Object lock = new Object();
                List<Thread> threads = new ArrayList<Thread>();
                for (final Site s : sites) {
                    if (!s.enabled) continue;
                    Thread t = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                List<Vod> r = Cms.search(s, kw);
                                if (r != null && r.size() > 0) {
                                    synchronized (lock) {
                                        all.addAll(r);
                                    }
                                    App.onUi(new Runnable() {
                                        @Override
                                        public void run() {
                                            showResult(kw, all);
                                        }
                                    });
                                }
                            } catch (Throwable ignored) {
                            }
                        }
                    });
                    t.start();
                    threads.add(t);
                }
                for (Thread t : threads) {
                    try {
                        t.join(9000);
                    } catch (Throwable ignored) {
                    }
                }
                App.onUi(new Runnable() {
                    @Override
                    public void run() {
                        loading.setVisibility(View.GONE);
                        showResult(kw, all);
                    }
                });
            }
        });
    }

    private void showResult(String kw, List<Vod> list) {
        loading.setVisibility(View.GONE);
        adapter.set(list);
        if (list.size() == 0) {
            tip.setVisibility(View.VISIBLE);
            tip.setText("没搜到「" + kw + "」\n换个关键词，或到「我的 → 接口管理」多加几个接口");
            grid.setVisibility(View.GONE);
        } else {
            tip.setVisibility(View.VISIBLE);
            tip.setText("「" + kw + "」共 " + list.size() + " 个结果");
            grid.setVisibility(View.VISIBLE);
        }
    }

    private void hideKeyboard() {
        try {
            InputMethodManager im = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (im != null) im.hideSoftInputFromWindow(input.getWindowToken(), 0);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onBackPressed() {
        if (grid.getVisibility() == View.VISIBLE) {
            grid.setVisibility(View.GONE);
            tip.setVisibility(View.GONE);
            home.setVisibility(View.VISIBLE);
            fillHistory();
            return;
        }
        super.onBackPressed();
    }
}
