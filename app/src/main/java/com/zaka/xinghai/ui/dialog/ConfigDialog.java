package com.zaka.xinghai.ui.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.zaka.xinghai.R;
import com.zaka.xinghai.core.App;
import com.zaka.xinghai.model.Site;
import com.zaka.xinghai.net.Cfg;
import com.zaka.xinghai.net.Http;
import com.zaka.xinghai.store.Store;
import com.zaka.xinghai.util.U;

import java.util.List;

/**
 * 配置地址弹窗（对标影视仓）：输入订阅 URL / 粘贴配置 / 选本地文件 / 添加单站。
 */
public final class ConfigDialog {

    public interface OnDone {
        void onDone();
    }

    public static final int REQ_FILE = 4202;

    private ConfigDialog() {
    }

    public static void show(final Activity act, final OnDone done) {
        final Dialog d = new Dialog(act, R.style.TransparentDialog);
        LinearLayout root = new LinearLayout(act);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundResource(R.drawable.bg_dialog);
        root.setPadding(U.dp(16), U.dp(16), U.dp(16), U.dp(12));

        TextView title = U.text(act, "配置地址", 17, 0xffffffff);
        title.setPadding(0, 0, 0, U.dp(12));
        root.addView(title);

        final EditText et = new EditText(act);
        et.setHint("订阅 URL / 接口 API / 粘贴 JSON");
        et.setHintTextColor(0x66ffffff);
        et.setTextColor(0xffffffff);
        et.setTextSize(14);
        et.setMinLines(3);
        et.setGravity(Gravity.TOP | Gravity.START);
        et.setBackgroundResource(R.drawable.bg_search_bar);
        et.setPadding(U.dp(12), U.dp(10), U.dp(12), U.dp(10));
        String last = Store.cfgUrl();
        if (!U.empty(last)) et.setText(last);
        root.addView(et, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, U.dp(100)));

        TextView tip = U.text(act, "支持：TVBox 订阅 JSON、苹果 CMS 的 api 地址、本地粘贴全文。JS/PY 源会入库并标记，纯脚本暂不执行。", 11, 0x88ffffff);
        tip.setPadding(0, U.dp(8), 0, U.dp(12));
        root.addView(tip);

        // 按钮行
        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(btn(act, "本地文件", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                    i.setType("*/*");
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    act.startActivityForResult(Intent.createChooser(i, "选择配置文件"), REQ_FILE);
                } catch (Throwable t) {
                    U.toast("无法打开文件选择器");
                }
            }
        }));
        row.addView(btn(act, "添加单站", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
                addSingle(act, done);
            }
        }));
        root.addView(row);

        LinearLayout row2 = new LinearLayout(act);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setPadding(0, U.dp(8), 0, 0);
        row2.addView(btn(act, "取消", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
            }
        }));
        row2.addView(btn(act, "确定导入", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String text = et.getText().toString().trim();
                if (U.empty(text)) {
                    U.toast("请输入配置");
                    return;
                }
                d.dismiss();
                importText(act, text, done);
            }
        }));
        root.addView(row2);

        d.setContentView(root);
        Window w = d.getWindow();
        if (w != null) {
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.CENTER);
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.width = (int) (act.getResources().getDisplayMetrics().widthPixels * 0.92f);
            w.setAttributes(lp);
        }
        d.show();
    }

    private static TextView btn(Activity act, String t, View.OnClickListener c) {
        TextView tv = U.text(act, t, 14, 0xffffffff);
        tv.setGravity(Gravity.CENTER);
        tv.setBackgroundResource(R.drawable.bg_chip);
        tv.setPadding(U.dp(12), U.dp(10), U.dp(12), U.dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        lp.leftMargin = U.dp(4);
        lp.rightMargin = U.dp(4);
        tv.setLayoutParams(lp);
        tv.setOnClickListener(c);
        return tv;
    }

    private static void addSingle(final Activity act, final OnDone done) {
        InputDialog.show(act, "添加接口", "名称", "", false, new InputDialog.OnOk() {
            @Override
            public void onOk(final String name) {
                if (U.empty(name)) return;
                InputDialog.show(act, "接口地址", "https://xxx/api.php/provide/vod/", "", false,
                        new InputDialog.OnOk() {
                            @Override
                            public void onOk(String api) {
                                if (U.empty(api)) return;
                                List<Site> list = Store.sites();
                                list.add(new Site(name.trim(), api.trim()));
                                Store.saveSites(list);
                                U.toast("已添加");
                                if (done != null) done.onDone();
                            }
                        });
            }
        });
    }

    public static void importText(final Activity act, final String text, final OnDone done) {
        // 单行 http 且不像 json：当订阅 URL 或单站 api
        final String t = text.trim();
        if (t.startsWith("http") && !t.contains("{") && t.length() < 500 && !t.contains("\n")) {
            Store.setCfgUrl(t);
            if (Cfg.looksLikeCfg(t) && (t.contains(".json") || t.contains("config") || t.contains("txt"))) {
                U.toast("正在拉取订阅…");
                App.run(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            String body = Http.get(t, Http.headers("User-Agent", Http.UA), 20000);
                            final List<Site> got = Cfg.load(body, t);
                            App.onUi(new Runnable() {
                                @Override
                                public void run() {
                                    merge(got, done);
                                }
                            });
                        } catch (final Throwable e) {
                            App.onUi(new Runnable() {
                                @Override
                                public void run() {
                                    // 当单站 API
                                    List<Site> one = new java.util.ArrayList<Site>();
                                    one.add(new Site("订阅站", t));
                                    merge(one, done);
                                }
                            });
                        }
                    }
                });
                return;
            }
            List<Site> one = new java.util.ArrayList<Site>();
            one.add(new Site("手动接口", t));
            merge(one, done);
            return;
        }
        // 全文配置
        List<Site> got = Cfg.load(t, "paste");
        merge(got, done);
    }

    public static void importUri(final Activity act, final Uri uri, final OnDone done) {
        if (uri == null) return;
        U.toast("正在读取…");
        App.run(new Runnable() {
            @Override
            public void run() {
                try {
                    java.io.InputStream in = act.getContentResolver().openInputStream(uri);
                    byte[] raw = U.readAll(in);
                    in.close();
                    final String body = Http.decodeText(raw);
                    App.onUi(new Runnable() {
                        @Override
                        public void run() {
                            importText(act, body, done);
                        }
                    });
                } catch (final Throwable t) {
                    App.onUi(new Runnable() {
                        @Override
                        public void run() {
                            U.toast("读取失败");
                        }
                    });
                }
            }
        });
    }

    private static void merge(List<Site> got, OnDone done) {
        if (got == null || got.size() == 0) {
            U.toast("没有解析出可用接口");
            return;
        }
        List<Site> all = Store.sites();
        int add = 0, js = 0, py = 0;
        for (Site s : got) {
            boolean dup = false;
            for (Site e : all) if (e.api.equals(s.api)) {
                dup = true;
                break;
            }
            if (!dup) {
                all.add(s);
                add++;
                if (s.isJs()) js++;
                if (s.isPy()) py++;
            }
        }
        Store.saveSites(all);
        String extra = "";
        if (js + py > 0) extra = "（含 JS " + js + " / PY " + py + "，脚本源需 CMS 兼容地址才能播）";
        U.toast("导入完成，新增 " + add + " 个" + extra);
        if (done != null) done.onDone();
    }
}
