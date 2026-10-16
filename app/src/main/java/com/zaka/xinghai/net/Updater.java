package com.zaka.xinghai.net;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import com.zaka.xinghai.core.App;
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
 * 应用内更新：检查 GitHub Release / 自定义 JSON → 下载 APK → PackageInstaller 安装。
 * 默认源：https://api.github.com/repos/yanchenwuxi/Xinghai/releases/latest
 */
public final class Updater {

    public static final String DEFAULT_API =
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

    /** 后台检查；结果回调到主线程 */
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
                    final Info info = fetchInfo(U.empty(apiUrl) ? DEFAULT_API : apiUrl);
                    final int cur = currentCode(ctx);
                    App.onUi(new Runnable() {
                        @Override
                        public void run() {
                            if (l == null) return;
                            if (info == null || U.empty(info.apkUrl)) {
                                l.onError("未找到可用的更新包");
                            } else if (info.versionCode > cur) {
                                l.onAvailable(info);
                            } else {
                                l.onLatest(currentName(ctx));
                            }
                        }
                    });
                } catch (final Throwable t) {
                    App.onUi(new Runnable() {
                        @Override
                        public void run() {
                            if (l != null) l.onError("检查失败：" + (t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage()));
                        }
                    });
                }
            }
        });
    }

    /** 解析 GitHub release 或自定义 JSON */
    public static Info fetchInfo(String api) throws Exception {
        String body = Http.get(api, Http.headers(
                "User-Agent", "Xinghai-Updater",
                "Accept", "application/vnd.github+json"), 15000);
        if (U.empty(body)) throw new Exception("空响应");
        JSONObject root = new JSONObject(body.trim());

        // 自定义：{versionCode, versionName, apkUrl, notes}
        if (root.has("apkUrl") || root.has("url")) {
            Info i = new Info();
            i.versionCode = root.optInt("versionCode", 0);
            i.versionName = root.optString("versionName", root.optString("version", ""));
            i.apkUrl = root.optString("apkUrl", root.optString("url", ""));
            i.notes = root.optString("notes", root.optString("body", ""));
            i.size = root.optLong("size", 0);
            if (i.versionCode <= 0 && i.versionName.length() > 0) {
                i.versionCode = parseCodeFromName(i.versionName);
            }
            return i;
        }

        // GitHub Releases API
        Info i = new Info();
        String tag = root.optString("tag_name", "");
        i.versionName = tag.startsWith("v") ? tag.substring(1) : tag;
        i.notes = root.optString("body", "");
        i.versionCode = root.optInt("versionCode", 0);
        if (i.versionCode <= 0) i.versionCode = parseCodeFromName(i.versionName);
        // 也可从 release name 读 versionCode-xxx
        String name = root.optString("name", "");
        if (i.versionCode <= 0) i.versionCode = parseCodeFromName(name);

        JSONArray assets = root.optJSONArray("assets");
        if (assets != null) {
            for (int k = 0; k < assets.length(); k++) {
                JSONObject a = assets.optJSONObject(k);
                if (a == null) continue;
                String n = a.optString("name", "").toLowerCase();
                if (n.endsWith(".apk")) {
                    i.apkUrl = a.optString("browser_download_url", "");
                    i.size = a.optLong("size", 0);
                    // asset name like xinghai-v2.0.1-4.apk
                    int c = parseCodeFromName(n);
                    if (c > i.versionCode) i.versionCode = c;
                    break;
                }
            }
        }
        return i;
    }

    /** 从 2.0.1 / v2.0.1 / xinghai-2.0.1-4.apk 推断 versionCode */
    static int parseCodeFromName(String s) {
        if (s == null) return 0;
        // 优先匹配尾部 -数字
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?:^|[-_v])(\\d{1,6})(?:\\.apk)?$").matcher(s.trim());
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (Throwable ignored) {
            }
        }
        // 2.0.1 -> 201 粗略
        m = java.util.regex.Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)").matcher(s);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1)) * 10000
                        + Integer.parseInt(m.group(2)) * 100
                        + Integer.parseInt(m.group(3));
            } catch (Throwable ignored) {
            }
        }
        return 0;
    }

    /** 下载并安装 */
    public static void downloadAndInstall(final Activity act, final Info info, final Listener l) {
        if (info == null || U.empty(info.apkUrl)) {
            if (l != null) l.onError("下载地址为空");
            return;
        }
        if (Build.VERSION.SDK_INT >= 26) {
            if (!act.getPackageManager().canRequestPackageInstalls()) {
                try {
                    Intent i = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:" + act.getPackageName()));
                    act.startActivity(i);
                    if (l != null) l.onError("请先允许「安装未知应用」，然后再点检查更新");
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
                                if (l != null) l.onError("安装失败：" + t.getMessage());
                            }
                        }
                    });
                } catch (final Throwable t) {
                    if (apk != null) try {
                        apk.delete();
                    } catch (Throwable ignored) {
                    }
                    App.onUi(new Runnable() {
                        @Override
                        public void run() {
                            if (l != null) l.onError("下载失败：" + (t.getMessage() == null ? "网络错误" : t.getMessage()));
                        }
                    });
                }
            }
        });
    }

    private static void download(String url, File out, long expect, final Listener l) throws Exception {
        HttpURLConnection conn = null;
        InputStream in = null;
        FileOutputStream fos = null;
        try {
            URL u = new URL(url);
            conn = (HttpURLConnection) u.openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(60000);
            conn.setRequestProperty("User-Agent", "Xinghai-Updater");
            conn.setRequestProperty("Accept", "*/*");
            int code = conn.getResponseCode();
            // GitHub 可能 302
            if (code >= 400) throw new Exception("HTTP " + code);
            long total = conn.getContentLength();
            if (total <= 0 && expect > 0) total = expect;
            in = conn.getInputStream();
            fos = new FileOutputStream(out);
            byte[] buf = new byte[8192];
            long read = 0;
            int n;
            int lastPct = -1;
            while ((n = in.read(buf)) > 0) {
                fos.write(buf, 0, n);
                read += n;
                if (l != null && total > 0) {
                    final int pct = (int) (read * 100 / total);
                    final long r = read, t = total;
                    if (pct != lastPct) {
                        lastPct = pct;
                        App.onUi(new Runnable() {
                            @Override
                            public void run() {
                                l.onProgress(pct, r, t);
                            }
                        });
                    }
                }
            }
            fos.flush();
            if (read < 1024) throw new Exception("文件过小，可能不是有效 APK");
        } finally {
            try {
                if (fos != null) fos.close();
            } catch (Throwable ignored) {
            }
            try {
                if (in != null) in.close();
            } catch (Throwable ignored) {
            }
            if (conn != null) conn.disconnect();
        }
    }

    private static void install(Activity act, File apk) throws Exception {
        if (Build.VERSION.SDK_INT >= 21) {
            installSession(act, apk);
        } else {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(Uri.fromFile(apk), "application/vnd.android.package-archive");
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            act.startActivity(i);
        }
    }

    /** PackageInstaller 会话安装（无需 FileProvider） */
    private static void installSession(Activity act, File apk) throws Exception {
        PackageInstaller installer = act.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params =
                new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
            } catch (Throwable ignored) {
            }
        }
        int sessionId = installer.createSession(params);
        PackageInstaller.Session session = installer.openSession(sessionId);
        try {
            long size = apk.length();
            OutputStream os = session.openWrite("pkg", 0, size);
            FileInputStream is = new FileInputStream(apk);
            byte[] buf = new byte[65536];
            int n;
            while ((n = is.read(buf)) > 0) {
                os.write(buf, 0, n);
            }
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
    }
}
