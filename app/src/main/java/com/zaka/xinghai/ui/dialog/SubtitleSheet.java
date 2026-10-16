package com.zaka.xinghai.ui.dialog;

import android.app.Activity;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.zaka.xinghai.R;
import com.zaka.xinghai.core.App;
import com.zaka.xinghai.store.Store;
import com.zaka.xinghai.subtitle.Cue;
import com.zaka.xinghai.subtitle.SubSearch;
import com.zaka.xinghai.util.U;
import com.zaka.xinghai.view.Sheet;
import com.zaka.xinghai.view.SubtitleView;

import java.util.List;

/**
 * 字幕面板：开关 + 在线搜索 + 时间轴偏移（毫秒级，改完立刻生效）+ 字号。
 * 全部走自研链路：搜索 → 下载 → 解析 → 自绘渲染。
 */
public class SubtitleSheet {

    public static Sheet show(final Activity act, final String vodName, final String vodType,
                             final SubtitleView sv, final Runnable onChanged) {
        final Sheet sheet = new Sheet(act, "字幕");
        final TextView value = U.text(act, "", 14, 0xffffb40a);

        // 开关
        final TextView toggle = U.text(act, sv.isEnabledSub() ? "字幕已开启 · 点击关闭" : "字幕已关闭 · 点击开启",
                14, sv.isEnabledSub() ? 0xffffb40a : 0x8fffffff);
        toggle.setBackgroundResource(R.drawable.bg_card_sel);
        toggle.setPadding(U.dp(14), U.dp(12), U.dp(14), U.dp(12));
        toggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean now = !sv.isEnabledSub();
                sv.setEnabledSub(now);
                Store.setSubEnabled(now);
                toggle.setText(now ? "字幕已开启 · 点击关闭" : "字幕已关闭 · 点击开启");
                toggle.setTextColor(now ? 0xffffb40a : 0x8fffffff);
                if (onChanged != null) onChanged.run();
            }
        });
        sheet.add(toggle);

        // 偏移
        LinearLayout offRow = new LinearLayout(act);
        offRow.setGravity(Gravity.CENTER_VERTICAL);
        offRow.setPadding(0, U.dp(12), 0, 0);
        TextView lab = U.text(act, "字幕偏移", 14, 0xffffffff);
        offRow.addView(lab, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        value.setText(fmt(Store.subOffset()));
        offRow.addView(value);
        TextView reset = U.text(act, "归零", 12, 0xffffffff);
        reset.setBackgroundResource(R.drawable.bg_chip);
        reset.setPadding(U.dp(10), U.dp(5), U.dp(10), U.dp(5));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.leftMargin = U.dp(10);
        reset.setLayoutParams(rlp);
        reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                apply(0, sv, value, onChanged);
            }
        });
        offRow.addView(reset);
        sheet.add(offRow);

        // 偏移微调
        int[] vals = {-5000, -1000, -100, 100, 1000, 5000};
        String[] names = {"-5s", "-1s", "-0.1s", "+0.1s", "+1s", "+5s"};
        LinearLayout btns = new LinearLayout(act);
        btns.setPadding(0, U.dp(10), 0, 0);
        for (int i = 0; i < vals.length; i++) {
            final int delta = vals[i];
            TextView b = U.text(act, names[i], 12, 0xffffffff);
            b.setGravity(Gravity.CENTER);
            b.setBackgroundResource(R.drawable.bg_chip);
            b.setPadding(0, U.dp(8), 0, U.dp(8));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.rightMargin = i == vals.length - 1 ? 0 : U.dp(6);
            b.setLayoutParams(lp);
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    apply(Store.subOffset() + delta, sv, value, onChanged);
                }
            });
            btns.addView(b);
        }
        sheet.add(btns);

        // 字号
        sheet.add(Sheet.label(act, "字幕字号"));
        final int[] sizes = {12, 16, 20, 24};
        String[] sizeNames = {"小", "标准", "大", "超大"};
        LinearLayout sizeRow = new LinearLayout(act);
        for (int i = 0; i < sizes.length; i++) {
            final int sz = sizes[i];
            boolean on = Store.subSize() == sz;
            TextView b = U.text(act, sizeNames[i], 12, on ? 0xffffb40a : 0xffffffff);
            b.setGravity(Gravity.CENTER);
            b.setBackgroundResource(on ? R.drawable.bg_chip_on : R.drawable.bg_chip);
            b.setPadding(0, U.dp(8), 0, U.dp(8));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.rightMargin = i == sizes.length - 1 ? 0 : U.dp(6);
            b.setLayoutParams(lp);
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Store.setSubSize(sz);
                    sv.setTextSizeSp(sz);
                }
            });
            sizeRow.addView(b);
        }
        sheet.add(sizeRow);

        // 在线搜索
        sheet.add(Sheet.label(act, "在线字幕（搜不到就换个关键词）"));
        final EditText kw = new EditText(act);
        kw.setText(U.cleanName(vodName));
        kw.setHint("片名");
        kw.setTextColor(0xffffffff);
        kw.setHintTextColor(0x66ffffff);
        kw.setTextSize(13);
        kw.setSingleLine(true);
        kw.setBackgroundResource(R.drawable.bg_search_bar2);
        kw.setPadding(U.dp(12), U.dp(9), U.dp(12), U.dp(9));
        sheet.add(kw);

        final LinearLayout result = new LinearLayout(act);
        result.setOrientation(LinearLayout.VERTICAL);
        result.setPadding(0, U.dp(10), 0, 0);
        sheet.add(result);

        TextView searchBtn = U.text(act, "搜索字幕", 14, 0xff001a37);
        searchBtn.setGravity(Gravity.CENTER);
        searchBtn.setBackgroundResource(R.drawable.bg_btn_accent);
        searchBtn.setPadding(0, U.dp(11), 0, U.dp(11));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = U.dp(10);
        searchBtn.setLayoutParams(slp);
        final TextView status = U.text(act, "", 12, 0x8fffffff);
        searchBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String k = kw.getText().toString().trim();
                status.setText("正在搜索…");
                result.removeAllViews();
                App.run(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            final List<SubSearch.Item> list = SubSearch.search(k);
                            final List<SubSearch.Item> smart = list.size() > 0 ? list
                                    : SubSearch.searchSmart(k, vodType);
                            App.onUi(new Runnable() {
                                @Override
                                public void run() {
                                    fillResult(act, result, smart, sv, status, sheet, onChanged);
                                }
                            });
                        } catch (Throwable t) {
                            App.onUi(new Runnable() {
                                @Override
                                public void run() {
                                    status.setText("没搜到，换个关键词试试");
                                }
                            });
                        }
                    }
                });
            }
        });
        sheet.add(searchBtn);
        sheet.add(status);

        sheet.show();
        // 打开面板先自动搜一轮（省得每次手点）
        searchBtn.post(new Runnable() {
            @Override
            public void run() {
                if (SubSearch.class != null) searchBtn.performClick();
            }
        });
        return sheet;
    }

    private static void fillResult(final Activity act, LinearLayout box, final List<SubSearch.Item> list,
                                   final SubtitleView sv, final TextView status, final Sheet sheet,
                                   final Runnable onChanged) {
        box.removeAllViews();
        if (list == null || list.size() == 0) {
            status.setText("没搜到匹配字幕，换个关键词或等片源自带");
            return;
        }
        status.setText("共 " + list.size() + " 条，点一条加载：");
        for (final SubSearch.Item it : list) {
            LinearLayout row = new LinearLayout(act);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setBackgroundResource(R.drawable.bg_card_sel);
            row.setPadding(U.dp(12), U.dp(10), U.dp(12), U.dp(10));

            TextView name = U.text(act, it.name, 13, 0xffffffff);
            name.setSingleLine(false);
            name.setMaxLines(2);
            row.addView(name);

            TextView meta = U.text(act, it.meta(), 11, 0x8fffffff);
            meta.setPadding(0, U.dp(4), 0, 0);
            row.addView(meta);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = U.dp(8);
            row.setLayoutParams(lp);
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    status.setText("正在下载并加载…");
                    App.run(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                final List<Cue> cues = SubSearch.load(it);
                                App.onUi(new Runnable() {
                                    @Override
                                    public void run() {
                                        sv.setCues(cues);
                                        sv.setOffset(Store.subOffset());
                                        sv.setTextSizeSp(Store.subSize());
                                        sv.setEnabledSub(true);
                                        Store.setSubEnabled(true);
                                        status.setText("已加载 " + cues.size() + " 条字幕，偏移 "
                                                + fmt(Store.subOffset()));
                                        if (onChanged != null) onChanged.run();
                                    }
                                });
                            } catch (Throwable t) {
                                App.onUi(new Runnable() {
                                    @Override
                                    public void run() {
                                        status.setText("这条字幕下载失败，换一条");
                                    }
                                });
                            }
                        }
                    });
                }
            });
            box.addView(row);
        }
    }

    private static void apply(long v, SubtitleView sv, TextView value, Runnable onChanged) {
        Store.setSubOffset(v);
        sv.setOffset(v);
        value.setText(fmt(v));
        if (onChanged != null) onChanged.run();
    }

    private static String fmt(long ms) {
        return String.format(java.util.Locale.US, "%+.1fs", ms / 1000f);
    }
}
