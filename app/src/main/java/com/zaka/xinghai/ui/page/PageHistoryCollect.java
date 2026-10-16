package com.zaka.xinghai.ui.page;

import android.graphics.Typeface;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.zaka.xinghai.R;
import com.zaka.xinghai.adapter.VodAdapter;
import com.zaka.xinghai.model.Record;
import com.zaka.xinghai.model.Vod;
import com.zaka.xinghai.store.Store;
import com.zaka.xinghai.ui.MainActivity;
import com.zaka.xinghai.ui.Page;
import com.zaka.xinghai.util.U;

import java.util.ArrayList;
import java.util.List;

/** 历史 / 收藏：顶部双 Tab（跟参照一样），历史带搜索框。 */
public class PageHistoryCollect extends Page {

    private LinearLayout tabRow;
    private FrameLayout content;
    private int index = 0;

    private View historyView, collectView;
    private GridView historyGrid, collectGrid;
    private VodAdapter historyAdapter, collectAdapter;
    private TextView historyEmpty, collectEmpty;
    private TextView historySearch;
    private String keyword = "";

    public PageHistoryCollect(MainActivity act) {
        super(act);
    }

    @Override
    protected View onCreate() {
        View v = LayoutInflater.from(act).inflate(R.layout.page_history_collect, null);
        tabRow = (LinearLayout) v.findViewById(R.id.hc_tabs);
        content = (FrameLayout) v.findViewById(R.id.hc_content);

        String[] names = {"收藏", "播放历史"};
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            TextView tv = U.text(act, names[i], 15, 0x8fffffff);
            tv.setGravity(Gravity.CENTER);
            tv.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.MATCH_PARENT, 1f));
            tv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    select(idx);
                }
            });
            tabRow.addView(tv);
        }

        historyView = LayoutInflater.from(act).inflate(R.layout.page_grid_history, null);
        collectView = makeCollectView();

        content.addView(historyView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        content.addView(collectView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        historyGrid = (GridView) historyView.findViewById(R.id.history_grid);
        historyEmpty = (TextView) historyView.findViewById(R.id.history_empty);
        historySearch = (TextView) historyView.findViewById(R.id.history_search);
        historySearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                com.zaka.xinghai.ui.dialog.InputDialog.show(act, "搜索历史记录", "输入片名", keyword, false,
                        new com.zaka.xinghai.ui.dialog.InputDialog.OnOk() {
                            @Override
                            public void onOk(String text) {
                                keyword = text;
                                historySearch.setText(U.empty(text) ? "搜索历史记录" : text);
                                fill();
                            }
                        });
            }
        });

        collectGrid = (GridView) collectView.findViewById(R.id.history_grid);
        collectEmpty = (TextView) collectView.findViewById(R.id.history_empty);
        collectView.findViewById(R.id.history_search).setVisibility(View.GONE);

        historyAdapter = new VodAdapter(act, true);
        historyAdapter.setOnClick(new VodAdapter.OnClick() {
            @Override
            public void onClick(Vod vod, int position) {
                openRecord(vod);
            }
        });
        historyAdapter.setOnLongClick(new VodAdapter.OnLongClick() {
            @Override
            public void onLongClick(Vod vod, int position) {
                Store.removeHistory(vod.siteKey + "#" + vod.id);
                fill();
                U.toast("已从历史移除");
            }
        });
        collectAdapter = new VodAdapter(act, false);
        collectAdapter.setOnClick(new VodAdapter.OnClick() {
            @Override
            public void onClick(Vod vod, int position) {
                openRecord(vod);
            }
        });
        collectAdapter.setOnLongClick(new VodAdapter.OnLongClick() {
            @Override
            public void onLongClick(Vod vod, int position) {
                Store.uncollect(vod.siteKey + "#" + vod.id);
                fill();
                U.toast("已取消收藏");
            }
        });
        historyGrid.setAdapter(historyAdapter);
        collectGrid.setAdapter(collectAdapter);
        return v;
    }

    private View makeCollectView() {
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);

        TextView search = new TextView(act);
        search.setId(R.id.history_search);
        search.setVisibility(View.GONE);
        box.addView(search);

        FrameLayout frame = new FrameLayout(act);
        GridView grid = new GridView(act);
        grid.setId(R.id.history_grid);
        grid.setNumColumns(3);
        grid.setHorizontalSpacing(U.dp(2));
        grid.setVerticalSpacing(U.dp(6));
        grid.setPadding(U.dp(4), U.dp(6), U.dp(4), 0);
        frame.addView(grid, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        TextView empty = new TextView(act);
        empty.setId(R.id.history_empty);
        empty.setText("还没有收藏的影片");
        empty.setTextColor(0x8fffffff);
        empty.setTextSize(13);
        empty.setVisibility(View.GONE);
        FrameLayout.LayoutParams elp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        elp.gravity = Gravity.CENTER;
        frame.addView(empty, elp);

        box.addView(frame, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        return box;
    }

    private void openRecord(Vod v) {
        com.zaka.xinghai.ui.PlayActivity.go(act, v, 0, 0, false);
    }

    @Override
    protected void onFirstShow() {
        select(0);
    }

    @Override
    public void onShow() {
        super.onShow();
        fill();
    }

    private void select(int idx) {
        index = idx;
        for (int i = 0; i < tabRow.getChildCount(); i++) {
            TextView tv = (TextView) tabRow.getChildAt(i);
            boolean on = i == idx;
            tv.setTextColor(on ? 0xffffb40a : 0x8fffffff);
            tv.setTypeface(on ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        }
        historyView.setVisibility(idx == 1 ? View.VISIBLE : View.GONE);
        collectView.setVisibility(idx == 0 ? View.VISIBLE : View.GONE);
        fill();
    }

    private void fill() {
        List<Record> cols = Store.records(true);
        Store.sortByTime(cols);
        collectAdapter.set(toVods(cols));
        collectEmpty.setVisibility(cols.size() == 0 ? View.VISIBLE : View.GONE);

        List<Record> his = Store.records(false);
        Store.sortByTime(his);
        List<Vod> hv = new ArrayList<Vod>();
        for (Record r : his) {
            if (!U.empty(keyword) && (r.name == null || !r.name.contains(keyword))) continue;
            hv.add(toVod(r));
        }
        historyAdapter.set(hv);
        historyEmpty.setVisibility(hv.size() == 0 ? View.VISIBLE : View.GONE);
    }

    private List<Vod> toVods(List<Record> list) {
        List<Vod> out = new ArrayList<Vod>();
        for (Record r : list) out.add(toVod(r));
        return out;
    }

    /** Record -> Vod（Vod 的 actor/director/score 三个字段在历史模式里承载进度信息） */
    private Vod toVod(Record r) {
        Vod v = new Vod();
        v.id = r.id;
        v.name = r.name;
        v.pic = r.pic;
        v.note = r.note;
        v.siteKey = r.siteKey;
        v.sourceName = r.siteName;
        v.actor = U.empty(r.epName) ? "" : r.epName;
        v.director = String.valueOf(r.position);
        v.score = String.valueOf(r.duration);
        return v;
    }
}
