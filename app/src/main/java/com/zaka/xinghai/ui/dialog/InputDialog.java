package com.zaka.xinghai.ui.dialog;

import android.app.Dialog;
import android.content.Context;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.zaka.xinghai.R;
import com.zaka.xinghai.util.U;

/** 输入框弹窗（新增/编辑接口、搜索、重命名都用它）。 */
public class InputDialog {

    public interface OnOk {
        void onOk(String text);
    }

    public static void show(Context c, String title, String hint, String def,
                            boolean multiLine, final OnOk cb) {
        final Dialog d = new Dialog(c, R.style.TransparentDialog);
        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundResource(R.drawable.bg_dialog);
        root.setPadding(U.dp(16), U.dp(14), U.dp(16), U.dp(14));

        TextView t = U.text(c, title, 16, 0xffffffff);
        t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        root.addView(t);

        final EditText et = new EditText(c);
        et.setHint(hint);
        et.setText(def == null ? "" : def);
        et.setHintTextColor(0x66ffffff);
        et.setTextColor(0xffffffff);
        et.setTextSize(13);
        et.setBackgroundResource(R.drawable.bg_search_bar2);
        et.setPadding(U.dp(12), U.dp(10), U.dp(12), U.dp(10));
        et.setInputType(multiLine
                ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                : InputType.TYPE_CLASS_TEXT);
        if (multiLine) {
            et.setSingleLine(false);
            et.setMinLines(4);
            et.setGravity(Gravity.TOP | Gravity.LEFT);
        } else {
            et.setSingleLine(true);
        }
        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, multiLine
                ? U.dp(120) : ViewGroup.LayoutParams.WRAP_CONTENT);
        elp.topMargin = U.dp(12);
        root.addView(et, elp);

        LinearLayout btns = new LinearLayout(c);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        btns.setGravity(Gravity.RIGHT);

        TextView cancel = U.text(c, "取消", 14, 0x8fffffff);
        cancel.setPadding(U.dp(18), U.dp(10), U.dp(18), U.dp(10));
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
            }
        });
        TextView ok = U.text(c, "确定", 14, 0xffffb40a);
        ok.setPadding(U.dp(18), U.dp(10), U.dp(18), U.dp(10));
        ok.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String s = et.getText().toString().trim();
                d.dismiss();
                if (cb != null) cb.onOk(s);
            }
        });
        btns.addView(cancel);
        btns.addView(ok);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.topMargin = U.dp(6);
        root.addView(btns, blp);

        d.setContentView(root);
        Window w = d.getWindow();
        if (w != null) {
            w.setGravity(Gravity.CENTER);
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.width = (int) (c.getResources().getDisplayMetrics().widthPixels * 0.88f);
            w.setAttributes(lp);
        }
        d.show();
        et.requestFocus();
    }
}
