package com.zaka.xinghai.ui.dialog;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.zaka.xinghai.R;
import com.zaka.xinghai.model.Filter;
import com.zaka.xinghai.util.U;
import com.zaka.xinghai.view.FlowLayout;
import com.zaka.xinghai.view.Sheet;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 筛选面板（顶部拖动条 + 标题 + 重置 + 各组标签，跟参照的筛选弹窗同构）。
 * 选完点「确定」把筛选条件回给调用方。
 */
public class FilterSheet {

    public interface OnDone {
        void onDone(Map<String, String> filters);
    }

    public static Sheet show(Context c, String title, List<Filter> groups,
                             Map<String, String> current, final OnDone cb) {
        final Sheet sheet = new Sheet(c, "筛选影片");
        final Map<String, String> picked = new HashMap<String, String>();
        if (current != null) picked.putAll(current);

        for (final Filter g : groups) {
            TextView label = U.text(c, g.name, 13, 0x8fffffff);
            label.setPadding(0, U.dp(8), 0, U.dp(6));
            sheet.add(label);

            final FlowLayout flow = new FlowLayout(c);
            flow.setGap(U.dp(8), U.dp(8));
            for (final String v : g.values) {
                final TextView chip = U.text(c, v, 12, 0xffffffff);
                chip.setGravity(Gravity.CENTER);
                chip.setBackgroundResource(R.drawable.bg_chip_sel);
                chip.setPadding(U.dp(12), U.dp(6), U.dp(12), U.dp(6));
                boolean on = picked.containsKey(g.key) ? v.equals(picked.get(g.key))
                        : ("全部".equals(v) || "全部地区".equals(v));
                chip.setSelected(on);
                if (on) chip.setTextColor(0xffffb40a);
                chip.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        for (int i = 0; i < flow.getChildCount(); i++) {
                            View ch = flow.getChildAt(i);
                            ch.setSelected(ch == chip);
                            ((TextView) ch).setTextColor(ch == chip ? 0xffffb40a : 0xffffffff);
                        }
                        if ("全部".equals(v) || "全部地区".equals(v)) picked.remove(g.key);
                        else picked.put(g.key, v);
                    }
                });
                flow.addView(chip);
            }
            sheet.add(flow);
        }

        LinearLayout btns = new LinearLayout(c);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        btns.setPadding(0, U.dp(14), 0, 0);

        TextView reset = U.text(c, "重置", 14, 0xffffffff);
        reset.setGravity(Gravity.CENTER);
        reset.setBackgroundResource(R.drawable.bg_btn_gray);
        reset.setPadding(0, U.dp(11), 0, U.dp(11));
        reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                picked.clear();
                sheet.dismiss();
                if (cb != null) cb.onDone(picked);
            }
        });
        TextView ok = U.text(c, "确定", 14, 0xff001a37);
        ok.setGravity(Gravity.CENTER);
        ok.setBackgroundResource(R.drawable.bg_btn_accent);
        ok.setPadding(0, U.dp(11), 0, U.dp(11));
        ok.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sheet.dismiss();
                if (cb != null) cb.onDone(picked);
            }
        });
        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp1.rightMargin = U.dp(10);
        btns.addView(reset, lp1);
        btns.addView(ok, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 2f));
        sheet.add(btns);
        sheet.show();
        return sheet;
    }
}
