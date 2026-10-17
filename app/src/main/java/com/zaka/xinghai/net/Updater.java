package com.zaka.xinghai.net;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import com.zaka.xinghai.core.App;
import com.zaka.xinghai.provider.ApkProvider;
import com.zaka.xinghai.util.U;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 应用内更新。
 * 检查顺序：1) 仓库 update.json  2) GitHub Releases API
 * 安装：PackageInstaller，失败则 ACTION_VIEW + ApkProvider。
 */
public final class Updater {

    /** 稳定、可缓存的更新描述（CI 每次发布写入） */
    public static final String UPDATE_JSON =
            "https://raw.githubusercontent.com/yanchenwuxi/Xinghai/main/update.json";
    public static final String RELEASE_API =
            "https://api.github.com/repos/yanchenwuxi/Xinghai/releases/latest";

    public interface Listener {
        void onChecking();
        void onLatest(String current);
        void onAvailable(Info info);
        void onProgress(int percent, long read, long total);
        void onInstalling();
        void onError(String msg);
    }

    public static final class Info {
        public int versionCode;
        public String versionName = "";
        public String apkUrl = "";
        public String notes = "";
        public long size;
    }

    private Updater() {
    }

    public static int currentCode(Context ctx) {
        try {
            PackageInfo pi = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
            return pi.versionCode;
        } catch (Throwable t) {
            return 0;
        }
    }

    public static String currentName(Context ctx) {
        try {
            return ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).versionName;
        } catch (Throwable t) {
            return "?";
        }
    }

    public static void check(final Context ctx, final String apiUrl, final Listener l) {
        if (l != null) App.onUi(new Runnable() {
            @Override
            public void run() {
                l.onChecking();
            }
        });
        App.run(new Runnable() {
            @Override
            public void run() {
                try {
                    Info info = null;
                    String err = null;
                    if (!U.empty(apiUrl)) {
                        try {
                            info = fetchInfo(apiUrl);
                        } catch (Throwable t) {
                            err = t.getMessage();
                        }
                    } else {
                        try {
                            info = fetchInfo(UPDATE_JSON);
                        } catch (Throwable t1) {
                            err = t1.getMessage();
                            try {
                                info = fetchInfo(RELEASE_API);
                                err = null;
                            } catch (Throwable t2) {
                                err = (err == null ? "" : err + " / ") + t2.getMessage();
                            }
                        }
                    }
                    final Info out = info;
                    final String e = err;
                    final int cur = currentCode(ctx);
                    final String curName = currentName(ctx);
                    App.onUi(new Runnable() {
                        @Override
                        public void run() {
                            if (l == null) return;
                            if (out == null || U.empty(out.apkUrl)) {
                                l.onError("检查失败：" + (e == null ? "无更新源" : e));
                                return;
                            }
                            boolean newer = out.versionCode > cur
                                    || (out.versionCode <= 0 && compareName(out.versionName, curName) > 0);
                            if (newer) l.onAvailable(out);
                            else l.onLatest(curName + " (" + cur + ")，远端 "
                                    + out.versionName + " (" + out.versionCode + ")");
                        }
                    });
                } catch (final Throwable t) {
                    App.onUi(new Runnable() {
                        @Override
                        public void run() {
                            if (l != null) l.onError("检查失败：" + t.getMessage());
                        }
                    });
                }
            }
        });
    }

    public static Info fetchInfo(String api) throws Exception {
        String body = getString(api);
        if (U.empty(body)) throw new Exception("空响应");
        JSONObject root = new JSONObject(body.trim());

        if (root.has("apkUrl") || root.has("url")) {
            Info i = new Info();
            i.versionCode = root.optInt("versionCode", 0);
            i.versionName = root.optString("versionName", root.optString("version", ""));
            i.apkUrl = root.optString("apkUrl", root.optString("url", ""));
            i.notes = root.optString("notes", root.optString("body", ""));
            i.size = root.optLong("size", 0);
            return i;
        }

        Info i = new Info();
        String tag = root.optString("tag_name", "");
        i.versionName = tag.startsWith("v") ? tag.substring(1) : tag;
        i.notes = root.optString("body", "");
        JSONArray assets = root.optJSONArray("assets");
        if (assets != null) {
            for (int k = 0; k < assets.length(); k++) {
                JSONObject a = assets.optJSONObject(k);
                if (a == null) continue;
                String n = a.optString("name", "");
                if (!n.toLowerCase().endsWith(".apk")) continue;
                i.apkUrl = a.optString("browser_download_url", "");
                i.size = a.optLong("size", 0);
                java.util.regex.Matcher m = java.util.regex.Pattern
                        .compile("-(\\d{1,6})\\.apk$", java.util.regex.Pattern.CASE_INSENSITIVE)
                        .matcher(n);
                if (m.find()) i.versionCode = Integer.parseInt(m.group(1));
                break;
            }
        }
        if (U.empty(i.apkUrl)) throw new Exception("Release 无 APK");
        return i;
    }

    static int compareName(String a, String b) {
        if (a == null) a = "";
        if (b == null) b = "";
        if (a.startsWith("v") || a.startsWith("V")) a = a.substring(1);
        if (b.startsWith("v") || b.startsWith("V")) b = b.substring(1);
        String[] pa = a.split("[^0-9]+");
        String[] pb = b.split("[^0-9]+");
        int n = Math.max(pa.length, pb.length);
        for (int i = 0; i < n; i++) {
            int x = 0, y = 0;
            try {
                if (i < pa.length && pa[i].length() > 0) x = Integer.parseInt(pa[i]);
            } catch (Throwable ignored) {
            }
            try {
                if (i < pb.length && pb[i].length() > 0) y = Integer.parseInt(pb[i]);
            } catch (Throwable ignored) {
            }
            if (x != y) return x - y;
        }
        return 0;
    }

    public static void downloadAndInstall(final Activity act, final Info info, final Listener l) {
        if (info == null || U.empty(info.apkUrl)) {
            if (l != null) l.onError("下载地址为空");
            return;
        }
        if (Build.VERSION.SDK_INT >= 26) {
            if (!act.getPackageManager().canRequestPackageInstalls()) {
                try {
                    act.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:" + act.getPackageName())));
                    if (l != null) l.onError("请允许「安装未知应用」后重试");
                } catch (Throwable t) {
                    if (l != null) l.onError("无法打开安装权限设置");
                }
                return;
            }
        }
        App.run(new Runnable() {
            @Override
            public void run() {
                File apk = null;
                try {
                    File dir = new File(act.getCacheDir(), "update");
                    if (!dir.exists()) dir.mkdirs();
                    apk = new File(dir, "xinghai-update.apk");
                    if (apk.exists()) apk.delete();
                    download(info.apkUrl, apk, info.size, l);
                    final File f = apk;
                    App.onUi(new Runnable() {
                        @Override
                        public void run() {
                            if (l != null) l.onInstalling();
                            try {
                                install(act, f);
                            } catch (Throwable t) {
                                try {
                                    installIntent(act, f);
                                } catch (Throwable t2) {
                                    if (l != null) l.onError("安装失败：" + t2.getMessage());
                                }
                            }
                        }
                    });
                } catch (final Throwable t) {
                    App.onUi(new Runnable() {
                        @Override
                        public void run() {
                            if (l != null) l.onError("下载失败：" + t.getMessage());
                        }
                    });
                }
            }
        });
    }

    private static String getString(String url) throws Exception {
        HttpURLConnection conn = open(url);
        int code = conn.getResponseCode();
        if (code >= 400) throw new Exception("HTTP " + code);
        InputStream in = conn.getInputStream();
        byte[] raw = U.readAll(in);
        in.close();
        conn.disconnect();
        return new String(raw, "UTF-8");
    }

    private static HttpURLConnection open(String url) throws Exception {
        URL u = new URL(url);
        HttpURLConnection conn = (HttpURLConnection) u.openConnection();
        conn.setInstanceFollowRedirects(true);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(60000);
        conn.setRequestProperty("User-Agent", "Xinghai-Updater");
        conn.setRequestProperty("Accept", "*/*");
        // 手动跟随有限次重定向（部分机型 setInstanceFollowRedirects 对 HTTPS 不生效）
        for (int i = 0; i < 5; i++) {
            int code = conn.getResponseCode();
            if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                String loc = conn.getHeaderField("Location");
                conn.disconnect();
                if (U.empty(loc)) throw new Exception("重定向无 Location");
                conn = (HttpURLConnection) new URL(loc).openConnection();
                conn.setInstanceFollowRedirects(true);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(60000);
                conn.setRequestProperty("User-Agent", "Xinghai-Updater");
                continue;
            }
            break;
        }
        return conn;
    }

    private static void download(String url, File out, long expect, final Listener l) throws Exception {
        HttpURLConnection conn = open(url);
        int code = conn.getResponseCode();
        if (code >= 400) throw new Exception("HTTP " + code);
        long total = conn.getContentLength();
        if (total <= 0 && expect > 0) total = expect;
        InputStream in = conn.getInputStream();
        FileOutputStream fos = new FileOutputStream(out);
        byte[] buf = new byte[8192];
        long read = 0;
        int n, last = -1;
        while ((n = in.read(buf)) > 0) {
            fos.write(buf, 0, n);
            read += n;
            if (l != null && total > 0) {
                final int pct = (int) (read * 100 / total);
                final long r = read, tt = total;
                if (pct != last) {
                    last = pct;
                    App.onUi(new Runnable() {
                        @Override
                        public void run() {
                            l.onProgress(pct, r, tt);
                        }
                    });
                }
            }
        }
        fos.close();
        in.close();
        conn.disconnect();
        if (read < 1024) throw new Exception("文件过小");
    }

    private static void install(Activity act, File apk) throws Exception {
        if (Build.VERSION.SDK_INT >= 21) {
            PackageInstaller installer = act.getPackageManager().getPackageInstaller();
            PackageInstaller.SessionParams params =
                    new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            int sessionId = installer.createSession(params);
            PackageInstaller.Session session = installer.openSession(sessionId);
            try {
                OutputStream os = session.openWrite("pkg", 0, apk.length());
                FileInputStream is = new FileInputStream(apk);
                byte[] buf = new byte[65536];
                int n;
                while ((n = is.read(buf)) > 0) os.write(buf, 0, n);
                session.fsync(os);
                is.close();
                os.close();
                Intent callback = new Intent(act, act.getClass());
                callback.setAction("com.zaka.xinghai.UPDATE_INSTALL");
                int flags = PendingIntent.FLAG_UPDATE_CURRENT;
                if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
                PendingIntent pi = PendingIntent.getActivity(act, sessionId, callback, flags);
                session.commit(pi.getIntentSender());
            } catch (Throwable t) {
                session.abandon();
                throw t;
            } finally {
                session.close();
            }
        } else {
            installIntent(act, apk);
        }
    }

    private static void installIntent(Activity act, File apk) throws Exception {
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (Build.VERSION.SDK_INT >= 24) {
            Uri uri = ApkProvider.apkUri();
            i.setDataAndType(uri, "application/vnd.android.package-archive");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } else {
            i.setDataAndType(Uri.fromFile(apk), "application/vnd.android.package-archive");
        }
        act.startActivity(i);
    }
}
