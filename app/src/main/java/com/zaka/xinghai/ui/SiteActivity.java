package com.zaka.xinghai.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.zaka.xinghai.R;
import com.zaka.xinghai.core.App;
import com.zaka.xinghai.model.Site;
import com.zaka.xinghai.net.Cms;
import com.zaka.xinghai.net.Http;
import com.zaka.xinghai.net.Spider;
import com.zaka.xinghai.store.Store;
import com.zaka.xinghai.ui.dialog.ConfigDialog;
import com.zaka.xinghai.ui.dialog.InputDialog;
import com.zaka.xinghai.util.U;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** 仓库管理面板：列表 / 切换 / 测速 / 启用停用 / 删除 / 置顶 / 导入配置。 */
public class SiteActivity extends Activity {

    private ListView list;
    private List<Site> sites = new ArrayList<Site>();
    private Adapter adapter;
    private TextView tip;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        U.init(getResources());
        setContentView(R.layout.activity_site);
        list = (ListView) findViewById(R.id.site_list);
        tip = (TextView) findViewById(R.id.site_tip);
        adapter = new Adapter();
        list.setAdapter(adapter);
        tip.setText("仓库：点名称切换当前源；测速 / 启停 / 删除见右侧与长按菜单。支持 CMS / JS / PY 标记。");

        findViewById(R.id.site_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        findViewById(R.id.site_add).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ConfigDialog.show(SiteActivity.this, new ConfigDialog.OnDone() {
                    @Override
                    public void onDone() {
                        reload();
                    }
                });
            }
        });
        View imp = findViewById(R.id.site_import);
        if (imp != null) {
            imp.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ConfigDialog.show(SiteActivity.this, new ConfigDialog.OnDone() {
                        @Override
                        public void onDone() {
                            reload();
                        }
                    });
                }
            });
        }
        reload();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == ConfigDialog.REQ_FILE && resultCode == RESULT_OK && data != null) {
            ConfigDialog.importUri(this, data.getData(), new ConfigDialog.OnDone() {
                @Override
                public void onDone() {
                    reload();
                }
            });
        }
    }

    private void reload() {
        sites = Store.sites();
        adapter.notifyDataSetChanged();
    }

    private void save() {
        Store.saveSites(sites);
        reload();
    }

    private void speedTest(final int index) {
        final Site s = sites.get(index);
        U.toast("测速中…");
        App.run(new Runnable() {
            @Override
            public void run() {
                long t0 = System.currentTimeMillis();
                long cost = -1;
                try {
                    if (!Spider.canUseAsCms(s) && s.isScript()) {
                        cost = -2;
                    } else {
                        Http.get(s.api, Http.headers("User-Agent", Http.UA), 8000);
                        cost = System.currentTimeMillis() - t0;
                    }
                } catch (Throwable e) {
                    cost = -1;
                }
                final long c = cost;
                App.onUi(new Runnable() {
                    @Override
                    public void run() {
                        s.speed = c;
                        save();
                        if (c == -2) U.toast(Spider.unsupportedHint(s));
                        else if (c < 0) U.toast(s.name + " 不通");
                        else U.toast(s.name + " " + c + "ms");
                    }
                });
            }
        });
    }

    private void more(final int index) {
        final Site s = sites.get(index);
        String[] items = new String[]{
                s.enabled ? "停用" : "启用",
                "置顶",
                "重命名",
                "删除",
                "复制地址"
        };
        new AlertDialog.Builder(this)
                .setTitle(s.name + " · " + s.typeLabel())
                .setItems(items, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        if (which == 0) {
                            s.enabled = !s.enabled;
                            save();
                        } else if (which == 1) {
                            sites.remove(index);
                            sites.add(0, s);
                            save();
                        } else if (which == 2) {
                            InputDialog.show(SiteActivity.this, "重命名", "名称", s.name, false,
                                    new InputDialog.OnOk() {
                                        @Override
                                        public void onOk(String text) {
                                            if (!U.empty(text)) {
                                                s.name = text.trim();
                                                save();
                                            }
                                        }
                                    });
                        } else if (which == 3) {
                            sites.remove(index);
                            save();
                            U.toast("已删除");
                        } else if (which == 4) {
                            android.content.ClipboardManager cm =
                                    (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                            if (cm != null) {
                                cm.setPrimaryClip(android.content.ClipData.newPlainText("api", s.api));
                                U.toast("已复制");
                            }
                        }
                    }
                })
                .show();
    }

    private class Adapter extends BaseAdapter {
        @Override
        public int getCount() {
            return sites.size();
        }

        @Override
        public Object getItem(int position) {
            return sites.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            View v = convertView;
            if (v == null) {
                v = LayoutInflater.from(SiteActivity.this).inflate(R.layout.item_site, parent, false);
            }
            final Site s = sites.get(position);
            TextView name = (TextView) v.findViewById(R.id.site_name);
            TextView api = (TextView) v.findViewById(R.id.site_api);
            TextView use = (TextView) v.findViewById(R.id.site_use);
            name.setText(s.name + (s.enabled ? "" : "（停用）"));
            String speed = s.speed > 0 ? " · " + s.speed + "ms" : (s.speed == -1 ? " · 失败" : "");
            api.setText("[" + s.typeLabel() + "] " + s.api + speed);
            boolean cur = s.key.equals(Store.currentKey());
            if (use != null) {
                use.setVisibility(cur ? View.VISIBLE : View.GONE);
            }
            name.setTextColor(s.enabled ? 0xffffffff : 0x66ffffff);
            v.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (!s.enabled) {
                        U.toast("已停用，长按菜单可启用");
                        return;
                    }
                    if (s.isScript() && !Spider.canUseAsCms(s)) {
                        U.toast(Spider.unsupportedHint(s));
                    }
                    Store.setCurrent(s.key);
                    Cms.clearCache();
                    notifyDataSetChanged();
                    U.toast("已切换到 " + s.name);
                }
            });
            v.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View view) {
                    more(position);
                    return true;
                }
            });
            View check = v.findViewById(R.id.site_check);
            if (check != null) check.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) { speedTest(position); }
            });
            View edit = v.findViewById(R.id.site_edit);
            if (edit != null) edit.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) { more(position); }
            });
            View del = v.findViewById(R.id.site_del);
            if (del != null) del.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    sites.remove(position);
                    save();
                    U.toast("已删除");
                }
            });
            return v;
        }
    }
}
