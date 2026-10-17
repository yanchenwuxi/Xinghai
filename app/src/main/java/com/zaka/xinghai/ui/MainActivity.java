package com.zaka.xinghai.ui;

import com.zaka.xinghai.ui.dialog.ConfigDialog;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.zaka.xinghai.R;
import com.zaka.xinghai.core.App;
import com.zaka.xinghai.store.Store;
import com.zaka.xinghai.ui.page.PageHistoryCollect;
import com.zaka.xinghai.ui.page.PageHome;
import com.zaka.xinghai.ui.page.PageLive;
import com.zaka.xinghai.ui.page.PageMine;
import com.zaka.xinghai.ui.page.PageShort;
import com.zaka.xinghai.util.U;

import java.util.ArrayList;
import java.util.List;

/**
 * 主壳：底部 5 项导航（主页 / 直播 / 短剧 / 历史收藏 / 我的）+ 内容容器。
 * 55dip 高、12sp 文字、图标在上，跟参照一致。
 */
public class MainActivity extends Activity {

    private static final String[] TITLES = {"主页", "直播", "短剧", "历史/收藏", "我的"};
    private static final int[] ICONS = {
            R.drawable.ic_tab_home, R.drawable.ic_tab_live, R.drawable.ic_tab_short,
            R.drawable.ic_tab_history, R.drawable.ic_tab_mine};

    private FrameLayout container;
    private LinearLayout bar;
    private final List<Page> pages = new ArrayList<Page>();
    private final List<View> tabs = new ArrayList<View>();
    private int current = -1;

    public static MainActivity instance;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        U.init(getResources());
        setContentView(R.layout.activity_main);
        instance = this;
        container = (FrameLayout) findViewById(R.id.page_container);
        bar = (LinearLayout) findViewById(R.id.tab_bar);

        pages.add(new PageHome(this));
        pages.add(new PageLive(this));
        pages.add(new PageShort(this));
        pages.add(new PageHistoryCollect(this));
        pages.add(new PageMine(this));

        for (int i = 0; i < TITLES.length; i++) {
            bar.addView(makeTab(i));
        }
        select(0);
    }

    private View makeTab(final int index) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(android.view.Gravity.CENTER);
        item.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 1f));

        ImageView iv = new ImageView(this);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(U.dp(24), U.dp(24));
        iv.setLayoutParams(ilp);
        iv.setImageResource(ICONS[index]);

        TextView tv = new TextView(this);
        tv.setText(TITLES[index]);
        tv.setTextSize(12);
        tv.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = U.dp(2);
        tv.setLayoutParams(tlp);

        item.addView(iv);
        item.addView(tv);
        item.setTag(new View[]{iv, tv});
        item.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                select(index);
            }
        });
        tabs.add(item);
        return item;
    }

    public void select(int index) {
        if (index == current) return;
        if (current >= 0 && current < pages.size()) pages.get(current).onHide();
        current = index;
        Page p = pages.get(index);
        container.removeAllViews();
        container.addView(p.view(), new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        p.onShow();
        for (int i = 0; i < tabs.size(); i++) {
            View item = tabs.get(i);
            View[] pair = (View[]) item.getTag();
            ImageView iv = (ImageView) pair[0];
            TextView tv = (TextView) pair[1];
            boolean on = i == index;
            U.tint(iv.getDrawable(), on ? 0xffffb40a : 0x8fffffff);
            iv.invalidate();
            tv.setTextColor(on ? 0xffffb40a : 0x8fffffff);
        }
    }

    public void gotoPage(int index) {
        select(index);
    }

    @Override
    protected void onResume() {
        super.onResume();
        instance = this;
        if (current >= 0 && current < pages.size()) pages.get(current).onShow();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String from = intent.getStringExtra("goto");
        if ("site".equals(from)) select(4);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (instance == this) instance = null;
    }

    /** 从播放页回来时刷新历史/收藏页 */
    public void notifyDataChanged() {
        for (Page p : pages) p.onLeave();
    }

    public String siteName() {
        try {
            return Store.currentSite() == null ? "未选择接口" : Store.currentSite().name;
        } catch (Throwable t) {
            return "";
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == ConfigDialog.REQ_FILE && resultCode == RESULT_OK && data != null) {
            ConfigDialog.importUri(this, data.getData(), null);
        }
    }
}
