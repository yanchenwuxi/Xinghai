package com.zaka.xinghai.ui.dialog;

import android.app.Dialog;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.zaka.xinghai.R;
import com.zaka.xinghai.util.U;

/** 简介弹窗（详情页 / 播放页那个「简介」按钮）。 */
public class IntroDialog {

    public static void show(Context c, String title, String content) {
        final Dialog d = new Dialog(c, R.style.TransparentDialog);
        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundResource(R.drawable.bg_dialog);
        root.setPadding(U.dp(16), U.dp(14), U.dp(16), U.dp(10));

        TextView t = U.text(c, U.empty(title) ? "简介" : title, 16, 0xffffffff);
        t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        root.addView(t);

        ScrollView sv = new ScrollView(c);
        TextView body = new TextView(c);
        body.setText(U.empty(content) ? "暂无简介" : content.replace("<p>", "").replace("</p>", "\n")
                .replace("<br>", "\n").replace("&nbsp;", " "));
        body.setTextColor(0xccffffff);
        body.setTextSize(13);
        body.setLineSpacing(U.dp(4), 1f);
        sv.addView(body);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, U.dp(300));
        slp.topMargin = U.dp(10);
        root.addView(sv, slp);

        TextView close = U.text(c, "知道了", 14, 0xffffb40a);
        close.setGravity(Gravity.CENTER);
        close.setBackgroundResource(R.drawable.bg_card_sel);
        close.setPadding(0, U.dp(10), 0, U.dp(10));
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
            }
        });
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.topMargin = U.dp(6);
        root.addView(close, clp);

        d.setContentView(root);
        Window w = d.getWindow();
        if (w != null) {
            w.setGravity(Gravity.CENTER);
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.width = (int) (c.getResources().getDisplayMetrics().widthPixels * 0.88f);
            w.setAttributes(lp);
        }
        d.show();
    }

    /** 简版：直接给标题+内容 */
    public void show(Context c, String title) {
        show(c, title, "");
    }
}
