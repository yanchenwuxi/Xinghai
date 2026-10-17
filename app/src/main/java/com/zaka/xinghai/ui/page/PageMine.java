package com.zaka.xinghai.ui.page;

import android.app.AlertDialog;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.zaka.xinghai.R;
import com.zaka.xinghai.core.App;
import com.zaka.xinghai.net.HlsProxy;
import com.zaka.xinghai.net.Http;
import com.zaka.xinghai.net.Updater;
import com.zaka.xinghai.net.M3u;
import com.zaka.xinghai.store.Store;
import com.zaka.xinghai.subtitle.SubStore;
import com.zaka.xinghai.ui.MainActivity;
import com.zaka.xinghai.ui.Page;
import com.zaka.xinghai.ui.SiteActivity;
import com.zaka.xinghai.ui.dialog.ConfigDialog;
import com.zaka.xinghai.ui.dialog.InputDialog;
import com.zaka.xinghai.ui.dialog.IntroDialog;
import com.zaka.xinghai.ui.dialog.ListSheet;
import com.zaka.xinghai.util.Img;
import com.zaka.xinghai.util.U;

/** 我的：头部信息 + 4 列功能网格 + 设置列表（跟参照的「我的」页同构）。 */
public class PageMine extends Page {

    private static final String[] GRID_NAMES = {"播放历史", "我的收藏", "仓库管理", "播放设置",
            "直播源", "字幕设置", "清除缓存", "关于"};
    private static final int[] GRID_ICONS = {
            R.drawable.ic_history2, R.drawable.ic_star, R.drawable.ic_site, R.drawable.ic_player,
            R.drawable.ic_live, R.drawable.ic_subtitle, R.drawable.ic_delete, R.drawable.ic_info};

    private TextView name, sub, version;

    public PageMine(MainActivity act) {
        super(act);
    }

    @Override
    protected View onCreate() {
        View v = LayoutInflater.from(act).inflate(R.layout.page_mine, null);
        name = (TextView) v.findViewById(R.id.mine_name);
        sub = (TextView) v.findViewById(R.id.mine_sub);
        version = (TextView) v.findViewById(R.id.mine_version);
        Img.load((ImageView) v.findViewById(R.id.mine_avatar), null);

        GridView grid = (GridView) v.findViewById(R.id.mine_grid);
        grid.setAdapter(new GridAdapter());
        grid.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(android.widget.AdapterView<?> parent, View view, int position, long id) {
                action(position);
            }
        });

        v.findViewById(R.id.mine_edit).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                InputDialog.show(act, "修改昵称", "给自己起个名字", Store.nick(), false,
                        new InputDialog.OnOk() {
                            @Override
                            public void onOk(String text) {
                                if (!U.empty(text)) {
                                    Store.setNick(text);
                                    refreshHead();
                                }
                            }
                        });
            }
        });

        LinearLayout list = (LinearLayout) v.findViewById(R.id.mine_list);
        addRow(list, "配置地址", R.drawable.ic_edit, new Runnable() {
            @Override
            public void run() {
                ConfigDialog.show(act, new ConfigDialog.OnDone() {
                    @Override
                    public void onDone() {
                    }
                });
            }
        });
        addRow(list, "仓库管理", R.drawable.ic_site, new Runnable() {
            @Override
            public void run() {
                act.startActivity(new android.content.Intent(act, SiteActivity.class));
            }
        });
        addRow(list, "去广告与加速", R.drawable.ic_filter, new Runnable() {
            @Override
            public void run() {
                showAdOptions();
            }
        });
        addRow(list, "播放超时自动换线", R.drawable.ic_speed, new Runnable() {
            @Override
            public void run() {
                showTimeoutOptions();
            }
        });
        addRow(list, "播放历史条数", R.drawable.ic_history2, new Runnable() {
            @Override
            public void run() {
                showHistoryOptions();
            }
        });
        addRow(list, "清除缓存", R.drawable.ic_delete, new Runnable() {
            @Override
            public void run() {
                clearCache();
            }
        });
        addRow(list, "检查更新", R.drawable.ic_refresh, new Runnable() {
            @Override
            public void run() {
                checkUpdate(true);
            }
        });
        addRow(list, "关于星海", R.drawable.ic_info, new Runnable() {
            @Override
            public void run() {
                IntroDialog.show(act, "星海 · 纯自研影视客户端",
                        "一套从零写的影视客户端：界面、解析、播放器控制、字幕层、去广告与加速通道全是自研实现，\n"
                                + "不依赖第三方 UI 库与商业 SDK。\n\n"
                                + "接口：任何苹果 CMS 格式的采集接口都能用（我的 → 接口管理 里加）。\n"
                                + "播放：框架原生播放内核 + 本地 HLS 代理（洗广告、分片缓存、首片预取）。\n"
                                + "字幕：自研解析与自绘渲染，支持整体时间轴偏移。\n\n"
                                + "版本 " + versionName());
            }
        });
        return v;
    }

    private void addRow(LinearLayout parent, String title, int icon, final Runnable cb) {
        View row = LayoutInflater.from(act).inflate(R.layout.item_menu, parent, false);
        ((ImageView) row.findViewById(R.id.icon)).setImageResource(icon);
        ((TextView) row.findViewById(R.id.title)).setText(title);
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                cb.run();
            }
        });
        parent.addView(row);
    }

    @Override
    protected void onFirstShow() {
        refreshHead();
    }

    @Override
    public void onShow() {
        super.onShow();
        refreshHead();
    }

    private void refreshHead() {
        name.setText(Store.nick());
        String s = MainActivity.instance != null ? MainActivity.instance.siteName() : "";
        int h = Store.records(false).size(), c = Store.records(true).size();
        sub.setText("当前接口：" + (U.empty(s) ? "未选择" : s) + "   ·   历史 " + h + " · 收藏 " + c);
        version.setText("星海 v" + versionName() + " · 纯自研客户端");
    }

    private String versionName() {
        try {
            return act.getPackageManager().getPackageInfo(act.getPackageName(), 0).versionName;
        } catch (Throwable t) {
            return "2.0.0";
        }
    }

    private void action(int position) {
        switch (position) {
            case 0:
                act.gotoPage(3);
                break;
            case 1:
                act.gotoPage(3);
                break;
            case 2:
                act.startActivity(new android.content.Intent(act, SiteActivity.class));
                break;
            case 3:
                showPlayerOptions();
                break;
            case 4:
                InputDialog.show(act, "直播源地址", "粘贴 m3u/m3u8 直播源地址", Store.liveUrl(), false,
                        new InputDialog.OnOk() {
                            @Override
                            public void onOk(String text) {
                                Store.setLiveUrl(text);
                                Store.setLiveRaw("");
                                U.toast(U.empty(text) ? "已恢复内置源" : "直播源已保存");
                            }
                        });
                break;
            case 5:
                showSubOptions();
                break;
            case 6:
                clearCache();
                break;
            default:
                IntroDialog.show(act, "关于星海", "星海 v" + versionName() + "\n纯自研影视客户端");
                break;
        }
    }

    private void showAdOptions() {
        final boolean[] ad = {Store.adFilter()};
        final boolean[] boost = {Store.boost()};
        final com.zaka.xinghai.view.Sheet sheet = new com.zaka.xinghai.view.Sheet(act, "去广告与加速");
        sheet.add(switchRow("线路 / 地址广告清洗", ad[0], new Switcher() {
            @Override
            public void on(boolean on) {
                Store.setAdFilter(on);
                U.toast(on ? "已开启广告清洗" : "已关闭广告清洗");
            }
        }));
        sheet.add(switchRow("播放加速（本地代理 + 分片缓存）", boost[0], new Switcher() {
            @Override
            public void on(boolean on) {
                Store.setBoost(on);
                if (on) HlsProxy.start(act);
                else HlsProxy.stop();
                U.toast(on ? "加速已开启（下次播放生效）" : "加速已关闭");
            }
        }));
        sheet.show();
    }

    private interface Switcher {
        void on(boolean on);
    }

    private View switchRow(String title, boolean on, final Switcher cb) {
        LinearLayout row = new LinearLayout(act);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.bg_card);
        row.setPadding(U.dp(14), U.dp(12), U.dp(14), U.dp(12));
        TextView tv = U.text(act, title, 14, 0xffffffff);
        tv.setSingleLine(false);
        tv.setMaxLines(2);
        row.addView(tv, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final TextView flag = U.text(act, on ? "已开启" : "已关闭", 13, on ? 0xffffb40a : 0x8fffffff);
        flag.setBackgroundResource(on ? R.drawable.bg_chip_on : R.drawable.bg_chip);
        flag.setPadding(U.dp(12), U.dp(5), U.dp(12), U.dp(5));
        flag.setTag(on);
        flag.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean now = !(Boolean) flag.getTag();
                flag.setTag(now);
                flag.setText(now ? "已开启" : "已关闭");
                flag.setTextColor(now ? 0xffffb40a : 0x8fffffff);
                flag.setBackgroundResource(now ? R.drawable.bg_chip_on : R.drawable.bg_chip);
                cb.on(now);
            }
        });
        row.addView(flag);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = U.dp(8);
        row.setLayoutParams(lp);
        return row;
    }

    private void showPlayerOptions() {
        final String[] speeds = {"0.5x", "0.75x", "1.0x", "1.25x", "1.5x", "2.0x", "3.0x"};
        final String[] scales = {"自适应", "16:9", "4:3", "铺满", "原始"};
        final String[] decodes = {"硬解（省电）", "软解（兼容好）"};

        final com.zaka.xinghai.view.Sheet sheet = new com.zaka.xinghai.view.Sheet(act, "播放设置");
        sheet.add(com.zaka.xinghai.view.Sheet.label(act, "倍速"));
        sheet.add(chips(speeds, String.valueOf(Store.speed()) + "x", new Pick() {
            @Override
            public void on(int i) {
                Store.setSpeed(Float.parseFloat(speeds[i].replace("x", "")));
                U.toast("默认倍速 " + speeds[i]);
            }
        }));
        sheet.add(com.zaka.xinghai.view.Sheet.label(act, "画面比例"));
        sheet.add(chips(scales, scales[Store.scale()], new Pick() {
            @Override
            public void on(int i) {
                Store.setScale(i);
                U.toast("画面比例 " + scales[i]);
            }
        }));
        sheet.add(com.zaka.xinghai.view.Sheet.label(act, "解码方式"));
        sheet.add(chips(decodes, decodes[Math.min(1, Store.decode())], new Pick() {
            @Override
            public void on(int i) {
                Store.setDecode(i);
                U.toast(i == 0 ? "已设为硬解" : "已设为软解");
            }
        }));
        sheet.show();
    }

    private interface Pick {
        void on(int index);
    }

    private LinearLayout chips(String[] names, String selected, final Pick cb) {
        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            boolean on = names[i].equals(selected);
            TextView tv = U.text(act, names[i], 12, on ? 0xffffb40a : 0xffffffff);
            tv.setGravity(Gravity.CENTER);
            tv.setBackgroundResource(on ? R.drawable.bg_chip_on : R.drawable.bg_chip);
            tv.setPadding(U.dp(10), U.dp(6), U.dp(10), U.dp(6));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = U.dp(8);
            tv.setLayoutParams(lp);
            tv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    cb.on(idx);
                }
            });
            row.addView(tv);
        }
        LinearLayout wrap = new LinearLayout(act);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.addView(row);
        return wrap;
    }

    private void showTimeoutOptions() {
        final String[] opts = {"5 秒", "9 秒", "15 秒", "不自动换线"};
        final int[] vals = {5, 9, 15, 0};
        ListSheet.show(act, "起播超时自动换线", java.util.Arrays.asList(opts),
                Store.playTimeout() == 5 ? 0 : Store.playTimeout() == 15 ? 2 : Store.playTimeout() == 0 ? 3 : 1,
                new ListSheet.OnPick() {
                    @Override
                    public void onPick(int index, String value) {
                        Store.setPlayTimeout(vals[index]);
                        U.toast("已设为 " + value);
                    }
                });
    }

    private void showHistoryOptions() {
        final String[] opts = {"50 条", "100 条", "200 条", "500 条"};
        final int[] vals = {50, 100, 200, 500};
        int sel = 1;
        for (int i = 0; i < vals.length; i++) if (vals[i] == Store.historyMax()) sel = i;
        ListSheet.show(act, "播放历史条数", java.util.Arrays.asList(opts), sel, new ListSheet.OnPick() {
            @Override
            public void onPick(int index, String value) {
                Store.setHistoryMax(vals[index]);
                U.toast("已设为 " + value);
            }
        });
    }

    private void showSubOptions() {
        final com.zaka.xinghai.view.Sheet sheet = new com.zaka.xinghai.view.Sheet(act, "字幕设置");
        sheet.add(switchRow("自动匹配字幕", Store.subAuto(), new Switcher() {
            @Override
            public void on(boolean on) {
                Store.setSubAuto(on);
            }
        }));
        sheet.add(switchRow("显示字幕", Store.subEnabled(), new Switcher() {
            @Override
            public void on(boolean on) {
                Store.setSubEnabled(on);
            }
        }));
        final String[] sizes = {"小", "标准", "大", "超大"};
        final int[] vals = {12, 16, 20, 24};
        int sel = 1;
        for (int i = 0; i < vals.length; i++) if (vals[i] == Store.subSize()) sel = i;
        sheet.add(com.zaka.xinghai.view.Sheet.label(act, "字幕字号"));
        sheet.add(chips(sizes, sizes[sel], new Pick() {
            @Override
            public void on(int i) {
                Store.setSubSize(vals[i]);
                U.toast("字幕字号：" + sizes[i]);
            }
        }));
        sheet.add(com.zaka.xinghai.view.Sheet.label(act, "已缓存字幕：" + SubStore.localNames().size() + " 条"));
        TextView clear = com.zaka.xinghai.view.Sheet.row(act, "清空字幕缓存", true);
        clear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SubStore.clear();
                U.toast("字幕缓存已清空");
            }
        });
        sheet.add(clear);
        sheet.show();
    }

    private void clearCache() {
        new AlertDialog.Builder(act)
                .setTitle("清除缓存")
                .setMessage("会清掉图片缓存、分片缓存与字幕缓存，历史与收藏不受影响。")
                .setNegativeButton("取消", null)
                .setPositiveButton("清除", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        App.run(new Runnable() {
                            @Override
                            public void run() {
                                Img.clear();
                                HlsProxy.clearCache();
                                SubStore.clear();
                                App.onUi(new Runnable() {
                                    @Override
                                    public void run() {
                                        U.toast("缓存已清空");
                                        refreshHead();
                                    }
                                });
                            }
                        });
                    }
                })
                .show();
    }

    private class GridAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return GRID_NAMES.length;
        }

        @Override
        public Object getItem(int position) {
            return GRID_NAMES[position];
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View v, ViewGroup parent) {
            LinearLayout box;
            if (v instanceof LinearLayout) box = (LinearLayout) v;
            else {
                box = new LinearLayout(act);
                box.setOrientation(LinearLayout.VERTICAL);
                box.setGravity(Gravity.CENTER);
                box.setPadding(0, U.dp(10), 0, U.dp(10));
                box.setLayoutParams(new GridView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, U.dp(78)));

                ImageView iv = new ImageView(act);
                iv.setLayoutParams(new LinearLayout.LayoutParams(U.dp(30), U.dp(30)));
                box.addView(iv);

                TextView tv = new TextView(act);
                tv.setTextSize(12);
                tv.setTextColor(0xffffffff);
                tv.setGravity(Gravity.CENTER);
                tv.setPadding(0, U.dp(5), 0, 0);
                tv.setTypeface(Typeface.DEFAULT);
                box.addView(tv);
                v = box;
            }
            ((ImageView) box.getChildAt(0)).setImageResource(GRID_ICONS[position]);
            ((TextView) box.getChildAt(1)).setText(GRID_NAMES[position]);
            return v;
        }
    }

    private void checkUpdate(final boolean fromUser) {
        Updater.check(act, null, new Updater.Listener() {
            @Override
            public void onChecking() {
                if (fromUser) U.toast("正在检查更新…");
            }

            @Override
            public void onLatest(String current) {
                if (fromUser) U.toast("已是最新版 v" + current);
            }

            @Override
            public void onAvailable(final Updater.Info info) {
                String msg = "发现新版本 v" + info.versionName
                        + "（" + info.versionCode + "）\n当前 v" + versionName() + "\n\n"
                        + (U.empty(info.notes) ? "是否下载并安装？" : info.notes);
                new AlertDialog.Builder(act)
                        .setTitle("应用更新")
                        .setMessage(msg)
                        .setPositiveButton("下载安装", new android.content.DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(android.content.DialogInterface d, int w) {
                                startDownload(info);
                            }
                        })
                        .setNegativeButton("稍后", null)
                        .show();
            }

            @Override
            public void onProgress(int percent, long read, long total) {
            }

            @Override
            public void onInstalling() {
                U.toast("正在调起安装…");
            }

            @Override
            public void onError(String msg) {
                if (fromUser) U.toast(msg);
            }
        });
    }

    private void startDownload(final Updater.Info info) {
        final android.app.ProgressDialog pd = new android.app.ProgressDialog(act);
        pd.setTitle("下载更新");
        pd.setMessage("准备下载…");
        pd.setProgressStyle(android.app.ProgressDialog.STYLE_HORIZONTAL);
        pd.setMax(100);
        pd.setCancelable(false);
        pd.show();
        Updater.downloadAndInstall(act, info, new Updater.Listener() {
            @Override
            public void onChecking() {
            }

            @Override
            public void onLatest(String current) {
            }

            @Override
            public void onAvailable(Updater.Info info) {
            }

            @Override
            public void onProgress(int percent, long read, long total) {
                pd.setProgress(percent);
                pd.setMessage(percent + "%  (" + (read / 1024) + "KB)");
            }

            @Override
            public void onInstalling() {
                try {
                    pd.dismiss();
                } catch (Throwable ignored) {
                }
                U.toast("正在安装，请按系统提示确认");
            }

            @Override
            public void onError(String msg) {
                try {
                    pd.dismiss();
                } catch (Throwable ignored) {
                }
                U.toast(msg);
            }
        });
    }

}
