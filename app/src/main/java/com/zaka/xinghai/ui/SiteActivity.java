package com.zaka.xinghai.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
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
import com.zaka.xinghai.net.Cfg;
import com.zaka.xinghai.net.Cms;
import com.zaka.xinghai.net.Http;
import com.zaka.xinghai.store.Store;
import com.zaka.xinghai.ui.dialog.InputDialog;
import com.zaka.xinghai.util.U;

import java.util.ArrayList;
import java.util.List;

/** 接口管理：增删改、测速、切换、导入配置订阅。 */
public class SiteActivity extends Activity {

    private ListView list;
    private List<Site> sites = new ArrayList<Site>();
    private Adapter adapter;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        U.init(getResources());
        setContentView(R.layout.activity_site);
        list = (ListView) findViewById(R.id.site_list);
        adapter = new Adapter();
        list.setAdapter(adapter);
        findViewById(R.id.site_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        findViewById(R.id.site_add).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addSite();
            }
        });
        findViewById(R.id.site_import).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                importCfg();
            }
        });
        ((TextView) findViewById(R.id.site_tip)).setText(
                "支持苹果 CMS 采集接口。可从网络、粘贴文本或本地文件导入 TVBox 配置。点一行切换，点测速探测连通。");
        reload();
    }

    private void reload() {
        sites = Store.sites();
        adapter.notifyDataSetChanged();
    }

    private void addSite() {
        InputDialog.show(this, "添加接口", "名称，例如：某某资源", "", false, new InputDialog.OnOk() {
            @Override
            public void onOk(final String name) {
                if (U.empty(name)) return;
                InputDialog.show(SiteActivity.this, "接口地址", "https://xxx/api.php/provide/vod/",
                        "", false, new InputDialog.OnOk() {
                            @Override
                            public void onOk(String api) {
                                if (U.empty(api)) return;
                                List<Site> list = Store.sites();
                                list.add(new Site(name, api.trim()));
                                Store.saveSites(list);
                                reload();
                                U.toast("已添加");
                            }
                        });
            }
        });
    }

    private static final int REQ_LOCAL_CFG = 4101;

    private void importCfg() {
        new AlertDialog.Builder(this)
                .setTitle("导入接口")
                .setItems(new String[]{"从网络地址导入", "粘贴本地配置文本", "选择本地文件"}, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        if (which == 0) importFromUrl();
                        else if (which == 1) importFromPaste();
                        else importFromFile();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void importFromUrl() {
        InputDialog.show(this, "导入接口配置", "粘贴配置地址（json / xml）", "", false,
                new InputDialog.OnOk() {
                    @Override
                    public void onOk(final String url) {
                        if (U.empty(url)) return;
                        fetchAndImport(url.trim(), url.trim());
                    }
                });
    }

    private void importFromPaste() {
        InputDialog.show(this, "粘贴本地配置", "粘贴 TVBox / 采集 的 json 或 xml 全文", "", true,
                new InputDialog.OnOk() {
                    @Override
                    public void onOk(final String body) {
                        if (U.empty(body)) return;
                        applyImport(body, "local-paste");
                    }
                });
    }

    private void importFromFile() {
        try {
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.setType("*/*");
            i.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(Intent.createChooser(i, "选择接口配置文件"), REQ_LOCAL_CFG);
        } catch (Throwable t) {
            U.toast("当前系统无法打开文件选择器，请改用粘贴导入");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_LOCAL_CFG || resultCode != RESULT_OK || data == null) return;
        final Uri uri = data.getData();
        if (uri == null) return;
        U.toast("正在读取文件…");
        App.run(new Runnable() {
            @Override
            public void run() {
                try {
                    java.io.InputStream in = getContentResolver().openInputStream(uri);
                    if (in == null) throw new Exception("无法打开文件");
                    byte[] buf = U.readAll(in);
                    in.close();
                    final String body = Http.decodeText(buf);
                    App.onUi(new Runnable() {
                        @Override
                        public void run() {
                            applyImport(body, uri.toString());
                        }
                    });
                } catch (final Throwable t) {
                    App.onUi(new Runnable() {
                        @Override
                        public void run() {
                            U.toast("读取失败：" + (t.getMessage() == null ? "未知错误" : t.getMessage()));
                        }
                    });
                }
            }
        });
    }

    private void fetchAndImport(final String url, final String base) {
        U.toast("正在导入…");
        App.run(new Runnable() {
            @Override
            public void run() {
                try {
                    String body = Http.get(url, Http.headers("User-Agent", Http.UA), 15000);
                    final List<Site> got = Cfg.load(body, base);
                    App.onUi(new Runnable() {
                        @Override
                        public void run() {
                            mergeSites(got);
                        }
                    });
                } catch (final Throwable t) {
                    App.onUi(new Runnable() {
                        @Override
                        public void run() {
                            U.toast("导入失败：" + (t.getMessage() == null ? "网络错误" : t.getMessage()));
                        }
                    });
                }
            }
        });
    }

    private void applyImport(String body, String base) {
        try {
            List<Site> got = Cfg.load(body, base);
            mergeSites(got);
        } catch (Throwable t) {
            U.toast("解析失败，请确认是 TVBox/采集 配置格式");
        }
    }

    private void mergeSites(List<Site> got) {
        if (got == null || got.size() == 0) {
            U.toast("这个配置里没解析出可用接口");
            return;
        }
        List<Site> all = Store.sites();
        int add = 0;
        for (Site s : got) {
            boolean dup = false;
            for (Site e : all) if (e.api.equals(s.api)) dup = true;
            if (!dup) {
                all.add(s);
                add++;
            }
        }
        Store.saveSites(all);
        reload();
        U.toast("导入完成，新增 " + add + " 个接口");
    }


    private void test(final Site s) {
        U.toast("正在测速 " + s.name + "…");
        App.run(new Runnable() {
            @Override
            public void run() {
                long t0 = System.currentTimeMillis();
                long cost = 0;
                try {
                    Http.get(s.api + "?ac=list", Http.headers("User-Agent", Http.UA), 8000);
                    cost = System.currentTimeMillis() - t0;
                } catch (Throwable t) {
                    cost = -1;
                }
                final long c = cost;
                List<Site> all = Store.sites();
                for (Site e : all) {
                    if (e.key.equals(s.key)) e.speed = c < 0 ? 0 : c;
                }
                Store.saveSites(all);
                App.onUi(new Runnable() {
                    @Override
                    public void run() {
                        reload();
                        U.toast(c < 0 ? (s.name + " 连不上") : (s.name + " 响应 " + c + "ms"));
                    }
                });
            }
        });
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
        public View getView(int position, View v, ViewGroup parent) {
            if (v == null) v = LayoutInflater.from(SiteActivity.this)
                    .inflate(R.layout.item_site, parent, false);
            final Site s = sites.get(position);
            ((TextView) v.findViewById(R.id.site_name)).setText(s.name);
            ((TextView) v.findViewById(R.id.site_api)).setText(s.api);
            TextView speed = (TextView) v.findViewById(R.id.site_speed);
            if (s.speed > 0) {
                speed.setVisibility(View.VISIBLE);
                speed.setText(s.speed + "ms");
            } else {
                speed.setVisibility(View.GONE);
            }
            boolean cur = s.key.equals(Store.currentKey());
            v.findViewById(R.id.site_use).setVisibility(cur ? View.VISIBLE : View.GONE);
            v.findViewById(R.id.site_check).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    test(s);
                }
            });
            v.findViewById(R.id.site_edit).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    InputDialog.show(SiteActivity.this, "修改接口地址", "接口地址", s.api, false,
                            new InputDialog.OnOk() {
                                @Override
                                public void onOk(String text) {
                                    if (U.empty(text)) return;
                                    List<Site> all = Store.sites();
                                    for (Site e : all) {
                                        if (e.key.equals(s.key)) {
                                            e.api = text.trim();
                                            e.key = U.md5(e.api);
                                        }
                                    }
                                    Store.saveSites(all);
                                    Cms.clearCache();
                                    reload();
                                    U.toast("已保存");
                                }
                            });
                }
            });
            v.findViewById(R.id.site_del).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    List<Site> all = Store.sites();
                    List<Site> out = new ArrayList<Site>();
                    for (Site e : all) if (!e.key.equals(s.key)) out.add(e);
                    Store.saveSites(out);
                    if (s.key.equals(Store.currentKey()) && out.size() > 0) {
                        Store.setCurrent(out.get(0).key);
                    }
                    reload();
                    U.toast("已删除");
                }
            });
            v.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Store.setCurrent(s.key);
                    Cms.clearCache();
                    reload();
                    U.toast("已切换到 " + s.name);
                }
            });
            return v;
        }
    }
}
