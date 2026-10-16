package com.zaka.xinghai.view;

import android.app.Dialog;
import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.zaka.xinghai.R;
import com.zaka.xinghai.util.U;

/** 底部面板外壳：拖动条 + 标题 + 关闭 + 可滚动内容区，跟参照的面板形态一致。 */
public class Sheet {

    private final Dialog dialog;
    private final View root;
    private final LinearLayout content;

    public Sheet(Context ctx, String title) {
        dialog = new Dialog(ctx, R.style.SheetDialog);
        root = LayoutInflater.from(ctx).inflate(R.layout.dialog_sheet, null);
        dialog.setContentView(root);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setGravity(Gravity.BOTTOM);
            w.setBackgroundDrawableResource(android.R.color.transparent);
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.width = WindowManager.LayoutParams.MATCH_PARENT;
            lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
            w.setAttributes(lp);
        }
        content = (LinearLayout) root.findViewById(R.id.sheet_content);
        TextView t = (TextView) root.findViewById(R.id.sheet_title);
        t.setText(title);
        root.findViewById(R.id.sheet_close).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });
        // 高度最多占屏幕 78%，内容长了面板内滚动
        root.post(new Runnable() {
            @Override
            public void run() {
                int h = root.getHeight();
                int limit = (int) (root.getResources().getDisplayMetrics().heightPixels * 0.78f);
                if (h > limit) {
                    View s = root.findViewById(R.id.sheet_scroll);
                    ViewGroup.LayoutParams lp = s.getLayoutParams();
                    lp.height = limit - U.dp(60);
                    s.setLayoutParams(lp);
                }
            }
        });
    }

    public Sheet setTitle(String s) {
        ((TextView) root.findViewById(R.id.sheet_title)).setText(s);
        return this;
    }

    public LinearLayout content() {
        return content;
    }

    public void add(View v) {
        content.addView(v);
    }

    public void show() {
        dialog.show();
    }

    public void dismiss() {
        try {
            dialog.dismiss();
        } catch (Throwable ignored) {
        }
    }

    public Dialog dialog() {
        return dialog;
    }

    public static TextView label(Context ctx, String s) {
        TextView tv = U.text(ctx, s, 13, 0x8fffffff);
        tv.setPadding(0, U.dp(10), 0, U.dp(6));
        return tv;
    }

    /** 面板里那种整行可点的按钮 */
    public static TextView row(Context ctx, String s, boolean accent) {
        TextView tv = U.text(ctx, s, 14, accent ? 0xffffb40a : 0xffffffff);
        tv.setBackgroundResource(R.drawable.bg_card_sel);
        tv.setPadding(U.dp(14), U.dp(12), U.dp(14), U.dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = U.dp(8);
        tv.setLayoutParams(lp);
        return tv;
    }
}
