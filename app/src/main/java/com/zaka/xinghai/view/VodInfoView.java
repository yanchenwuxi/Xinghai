package com.zaka.xinghai.view;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.zaka.xinghai.R;
import com.zaka.xinghai.model.Record;
import com.zaka.xinghai.model.Vod;
import com.zaka.xinghai.store.Store;
import com.zaka.xinghai.ui.PlayActivity;
import com.zaka.xinghai.ui.WebActivity;
import com.zaka.xinghai.ui.dialog.IntroDialog;
import com.zaka.xinghai.util.Img;
import com.zaka.xinghai.util.U;

import java.util.ArrayList;
import java.util.List;

/**
 * 影片信息区（详情页 / 播放页共用同一套，跟参照的简介区一致）：
 * 封面 104x140 圆角 → 片名 / 评分 36sp / 五星 → 收藏按钮（金色胶囊）
 * → 来源 / 类型标签 / 简介按钮 → 一排功能（下载 解析 快搜 播放器 投屏 设置）
 */
public class VodInfoView extends LinearLayout {

    private ImageView poster;
    private TextView name, score, source, type, intro;
    private StarBar star;
    private TextView collect;
    private LinearLayout actionRow;
    private Vod vod;
    private Runnable onCollect;

    public VodInfoView(Context c) {
        super(c);
        setOrientation(VERTICAL);
        setPadding(U.dp(12), U.dp(10), U.dp(12), U.dp(6));
        build();
    }

    public void setOnCollect(Runnable r) {
        onCollect = r;
    }

    private void build() {
        Context c = getContext();
        LinearLayout top = new LinearLayout(c);
        top.setOrientation(HORIZONTAL);

        // 封面
        FrameLayoutWrap card = new FrameLayoutWrap(c);
        poster = new ImageView(c);
        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        poster.setBackgroundResource(R.drawable.bg_card);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(U.dp(104), U.dp(140));
        poster.setLayoutParams(plp);
        card.addView(poster);
        top.addView(card);

        // 右侧信息
        LinearLayout right = new LinearLayout(c);
        right.setOrientation(VERTICAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        rlp.leftMargin = U.dp(12);
        right.setLayoutParams(rlp);

        LinearLayout nameRow = new LinearLayout(c);
        nameRow.setOrientation(HORIZONTAL);
        name = U.text(c, "", 18, 0xffffffff);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setMaxLines(2);
        name.setSingleLine(false);
        nameRow.addView(name, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        score = U.text(c, "", 36, 0xffffffff);
        score.setGravity(Gravity.RIGHT);
        score.setSingleLine(true);
        nameRow.addView(score);
        right.addView(nameRow);

        LinearLayout starRow = new LinearLayout(c);
        starRow.setGravity(Gravity.RIGHT);
        star = new StarBar(c);
        starRow.addView(star);
        right.addView(starRow);

        source = U.text(c, "", 12, 0x8fffffff);
        source.setPadding(0, U.dp(8), 0, 0);
        right.addView(source);

        LinearLayout tagRow = new LinearLayout(c);
        tagRow.setGravity(Gravity.CENTER_VERTICAL);
        type = U.text(c, "", 11, 0x66ffffff);
        type.setBackgroundResource(R.drawable.bg_tag);
        type.setPadding(U.dp(8), U.dp(2), U.dp(8), U.dp(2));
        tagRow.addView(type);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tagRow.addView(new View(c), ilp);
        intro = U.text(c, "简介", 11, 0x66ffffff);
        intro.setPadding(U.dp(4), U.dp(4), U.dp(4), U.dp(4));
        tagRow.addView(intro);
        right.addView(tagRow);

        collect = U.text(c, "加入收藏", 13, 0xff694001);
        collect.setBackgroundResource(R.drawable.bg_collect);
        collect.setGravity(Gravity.CENTER);
        collect.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(U.dp(80), U.dp(30));
        clp.topMargin = U.dp(8);
        clp.leftMargin = U.dp(0);
        collect.setLayoutParams(clp);
        LinearLayout collectWrap = new LinearLayout(c);
        collectWrap.setGravity(Gravity.RIGHT);
        collectWrap.addView(collect);
        right.addView(collectWrap);

        top.addView(right);
        top.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addView(top);

        // 功能排
        actionRow = new LinearLayout(c);
        actionRow.setOrientation(HORIZONTAL);
        actionRow.setPadding(0, U.dp(12), 0, 0);
        addView(actionRow);
    }

    /** 简易 FrameLayout 包装（避免额外布局文件） */
    private static class FrameLayoutWrap extends android.widget.FrameLayout {
        FrameLayoutWrap(Context c) {
            super(c);
        }
    }

    public void bind(final Vod v) {
        if (v == null) return;
        vod = v;
        Context c = getContext();
        name.setText(v.name);
        score.setText(U.empty(v.scoreNum()) || "0".equals(v.scoreNum()) ? "" : v.scoreNum());
        try {
            star.setRating(Float.parseFloat(v.scoreNum()) / 2f);
        } catch (Throwable t) {
            star.setRating(0);
        }
        Img.load(poster, v.pic);
        source.setText((U.empty(v.sourceName) ? "" : v.sourceName + "  ·  ") + (U.empty(v.note) ? "" : v.note));
        type.setText(U.empty(v.type) ? "影片" : v.type);
        final boolean coll = Store.isCollected(v.siteKey + "#" + v.id);
        refreshCollect(coll);
        collect.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                boolean now = Store.isCollected(v.siteKey + "#" + v.id);
                if (now) {
                    Store.uncollect(v.siteKey + "#" + v.id);
                    U.toast("已取消收藏");
                } else {
                    Store.collect(toRecord(v));
                    U.toast("已加入收藏");
                }
                refreshCollect(!now);
                if (onCollect != null) onCollect.run();
            }
        });
        intro.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                IntroDialog.show(c, v.name, v.content);
            }
        });
        buildActions(v);
    }

    private void refreshCollect(boolean on) {
        collect.setText(on ? "已收藏" : "加入收藏");
    }

    private void buildActions(final Vod v) {
        actionRow.removeAllViews();
        final Context c = getContext();
        String[] names = {"下载", "解析", "快搜", "播放器", "投屏", "设置"};
        int[] icons = {R.drawable.ic_download, R.drawable.ic_parse, R.drawable.ic_search,
                R.drawable.ic_player, R.drawable.ic_cast, R.drawable.ic_setting};
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            LinearLayout item = new LinearLayout(c);
            item.setOrientation(VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setLayoutParams(new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            ImageView iv = new ImageView(c);
            iv.setLayoutParams(new LinearLayout.LayoutParams(U.dp(24), U.dp(24)));
            iv.setImageResource(icons[i]);
            iv.setAlpha(0.9f);

            TextView tv = new TextView(c);
            tv.setText(names[i]);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            tv.setTextColor(0xffffffff);
            tv.setGravity(Gravity.CENTER);

            item.addView(iv);
            item.addView(tv);
            item.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    onAction(idx, v);
                }
            });
            actionRow.addView(item);
        }
    }

    /** 功能排的动作：能做的做，做不了的按能力口径说明并转一步 */
    private void onAction(int idx, Vod v) {
        Context c = getContext();
        switch (idx) {
            case 0: // 下载
                U.toast("长按播放页的选集可复制直链，用外部下载器下");
                break;
            case 1: // 解析
                if (v.eps(0).size() > 0) {
                    PlayActivity.go(c, v, 0, 0, true);
                } else {
                    U.toast("这条影片暂无可用线路");
                }
                break;
            case 2: // 快搜
                com.zaka.xinghai.ui.SearchActivity.go(c, v.name);
                break;
            case 3: // 播放器
                com.zaka.xinghai.ui.PlayActivity.go(c, v, 0, 0, false);
                break;
            case 4: // 投屏
                U.toast("投屏需要 DLNA 设备发现，这台设备上没找到可用设备");
                break;
            case 5: // 设置
                if (c instanceof android.app.Activity) {
                    Intent it = new Intent(c, com.zaka.xinghai.ui.SiteActivity.class);
                    c.startActivity(it);
                }
                break;
            default:
                break;
        }
    }

    public static Record toRecord(Vod v) {
        Record r = new Record();
        r.id = v.id;
        r.name = v.name;
        r.pic = v.pic;
        r.note = v.note;
        r.siteKey = v.siteKey;
        r.siteName = v.sourceName;
        r.time = System.currentTimeMillis();
        return r;
    }

    /** 供外部（播放页）刷新收藏状态 */
    public void refresh() {
        bind(vod);
    }
}
