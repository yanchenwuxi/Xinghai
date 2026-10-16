package com.zaka.xinghai.net;

import android.content.Context;

import com.zaka.xinghai.core.App;
import com.zaka.xinghai.util.U;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.HttpURLConnection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 自研本地 HLS 代理 + 分片缓存。
 *
 * 干什么：
 *   - 播放器请求 http://127.0.0.1:port/pl?u=<原始列表>，代理把列表抓下来洗净广告段、
 *     相对地址补绝对、分片指回本地，再喂给播放器
 *   - 分片走 /seg?u=<原始分片>，落盘缓存，二次播放直接命中（这就是"缓冲提速"的来源）
 *
 * 为什么必须指回本地：列表里如果直接写源站地址，播放器就绕开了缓存与预取，
 * 代理等于白做。这条踩过坑。
 */
public final class HlsProxy {

    private static ServerSocket server;
    private static int port = 0;
    private static Thread accept;
    private static final AtomicBoolean running = new AtomicBoolean(false);
    private static ExecutorService pool;
    private static File cacheDir;
    private static final long MAX_CACHE = 300L * 1024 * 1024;
    /** 正在下载的分片，避免同一片被并发拉两次 */
    private static final Map<String, Object> locks = new LinkedHashMap<String, Object>();

    private HlsProxy() {
    }

    public static String base() {
        return port > 0 ? "http://127.0.0.1:" + port : "";
    }

    public static boolean isRunning() {
        return running.get() && port > 0;
    }

    /** 最近下载字节采样（算网速用） */
    private static final java.util.LinkedList<long[]> samples = new java.util.LinkedList<long[]>();
    public static long totalBytes = 0L;
    public static int hitCount = 0;

    public static void noteBytes(long n) {
        totalBytes += n;
        synchronized (samples) {
            samples.add(new long[]{System.currentTimeMillis(), n});
            long cut = System.currentTimeMillis() - 4000;
            while (samples.size() > 0 && samples.getFirst()[0] < cut) samples.removeFirst();
        }
    }

    /** 近 4 秒的平均下载速度 KB/s（0 = 没数据） */
    public static int rateKbps() {
        synchronized (samples) {
            if (samples.size() == 0) return 0;
            long bytes = 0, from = samples.getFirst()[0];
            for (long[] s : samples) bytes += s[1];
            long span = Math.max(500, System.currentTimeMillis() - from);
            return (int) (bytes / 1024.0 * 1000 / span);
        }
    }

    public static synchronized int start(Context ctx) {
        if (isRunning()) return port;
        try {
            cacheDir = new File(ctx.getCacheDir(), "seg");
            if (!cacheDir.exists()) cacheDir.mkdirs();
            server = new ServerSocket(0, 40, InetAddress.getByName("127.0.0.1"));
            port = server.getLocalPort();
            pool = Executors.newFixedThreadPool(8);
            running.set(true);
            accept = new Thread(new Runnable() {
                @Override
                public void run() {
                    while (running.get()) {
                        try {
                            final Socket s = server.accept();
                            pool.execute(new Runnable() {
                                @Override
                                public void run() {
                                    handle(s);
                                }
                            });
                        } catch (Throwable t) {
                            if (running.get()) continue;
                            break;
                        }
                    }
                }
            }, "hls-proxy");
            accept.setDaemon(true);
            accept.start();
            return port;
        } catch (Throwable t) {
            stop();
            return 0;
        }
    }

    public static synchronized void stop() {
        running.set(false);
        try {
            if (server != null) server.close();
        } catch (Throwable ignored) {
        }
        server = null;
        port = 0;
        if (pool != null) {
            pool.shutdownNow();
            pool = null;
        }
        synchronized (locks) {
            locks.clear();
        }
        synchronized (samples) {
            samples.clear();
        }
    }

    public static File cacheDir() {
        return cacheDir;
    }

    // ------------------------------------------------------------ 请求处理
    private static void handle(Socket s) {
        try {
            s.setSoTimeout(25000);
            InputStream in = s.getInputStream();
            StringBuilder head = new StringBuilder();
            int c;
            while ((c = in.read()) >= 0) {
                head.append((char) c);
                if (head.length() > 4 && head.substring(head.length() - 4).equals("\r\n\r\n")) break;
                if (head.length() > 8192) break;
            }
            String first = head.toString().split("\r\n")[0];
            String[] parts = first.split(" ");
            if (parts.length < 2) {
                s.close();
                return;
            }
            String path = parts[1];
            String body;
            String ctype;
            byte[] bytes = null;
            if (path.startsWith("/pl")) {
                String u = param(path, "u");
                String text = playlist(u);
                if (text == null) {
                    write(s, 502, "text/plain", "proxy fail".getBytes());
                    s.close();
                    return;
                }
                ctype = "application/vnd.apple.mpegurl";
                bytes = text.getBytes("UTF-8");
            } else if (path.startsWith("/seg")) {
                String u = param(path, "u");
                bytes = segment(u);
                if (bytes == null) {
                    write(s, 404, "text/plain", "miss".getBytes());
                    s.close();
                    return;
                }
                ctype = u.contains(".ts") ? "video/mp2t" : "application/octet-stream";
            } else if (path.startsWith("/ping")) {
                write(s, 200, "text/plain", "ok".getBytes());
                s.close();
                return;
            } else {
                write(s, 404, "text/plain", "no".getBytes());
                s.close();
                return;
            }
            write(s, 200, ctype, bytes);
            s.close();
        } catch (Throwable t) {
            try {
                s.close();
            } catch (Throwable ignored) {
            }
        }
    }

    private static void write(Socket s, int code, String type, byte[] body) throws Exception {
        OutputStream os = s.getOutputStream();
        StringBuilder h = new StringBuilder();
        h.append("HTTP/1.1 ").append(code).append(code == 200 ? " OK" : " ERR").append("\r\n");
        h.append("Content-Type: ").append(type).append("\r\n");
        h.append("Content-Length: ").append(body.length).append("\r\n");
        h.append("Connection: close\r\n");
        h.append("Accept-Ranges: none\r\n\r\n");
        os.write(h.toString().getBytes("UTF-8"));
        os.write(body);
        os.flush();
    }

    private static String param(String path, String key) {
        int q = path.indexOf('?');
        if (q < 0) return "";
        for (String p : path.substring(q + 1).split("&")) {
            int eq = p.indexOf('=');
            if (eq <= 0) continue;
            if (p.substring(0, eq).equals(key)) return U.urlDecode(p.substring(eq + 1));
        }
        return "";
    }

    // ------------------------------------------------------------ 列表
    private static String playlist(String url) {
        try {
            if (U.empty(url)) return null;
            String body = Http.get(url, Http.headers("User-Agent", Http.UA_PLAYER), 15000);
            if (U.empty(body)) return null;
            if (body.contains("#EXTM3U")) {
                AdFilter.PlResult r = AdFilter.cleanPlaylist(body, url, base());
                return r.text;
            }
            // 不是列表（有些源直接给一个 mp4 地址），原样回
            return body;
        } catch (Throwable t) {
            return null;
        }
    }

    // ------------------------------------------------------------ 分片（带盘缓存）
    private static byte[] segment(String url) {
        Object lock = null;
        try {
            if (U.empty(url)) return null;
            File f = new File(cacheDir, U.md5(url) + ".seg");
            if (f.exists() && f.length() > 0) {
                hitCount++;
                return readFile(f);
            }
            synchronized (locks) {
                lock = locks.get(url);
                if (lock == null) {
                    lock = new Object();
                    locks.put(url, lock);
                }
            }
            synchronized (lock) {
                if (f.exists() && f.length() > 0) return readFile(f);
                byte[] data = fetch(url);
                if (data == null) return null;
                noteBytes(data.length);
                FileOutputStream fos = new FileOutputStream(f);
                fos.write(data);
                fos.close();
                trimCache();
                return data;
            }
        } catch (Throwable t) {
            return null;
        } finally {
            if (lock != null) {
                synchronized (locks) {
                    locks.remove(url);
                }
            }
        }
    }

    /** 预取下一个分片（起播后立刻拉，减少卡顿） */
    public static void prefetch(final String url) {
        if (U.empty(url) || cacheDir == null) return;
        File f = new File(cacheDir, U.md5(url) + ".seg");
        if (f.exists()) return;
        App.run(new Runnable() {
            @Override
            public void run() {
                try {
                    segment(url);
                } catch (Throwable ignored) {
                }
            }
        });
    }

    private static byte[] fetch(String url) {
        HttpURLConnection conn = null;
        try {
            URL u = new URL(url);
            conn = (HttpURLConnection) u.openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(20000);
            conn.setRequestProperty("User-Agent", Http.UA_PLAYER);
            conn.setRequestProperty("Accept-Encoding", "identity");
            int code = conn.getResponseCode();
            if (code >= 400) return null;
            InputStream in = conn.getInputStream();
            byte[] data = U.readAll(in);
            in.close();
            return data;
        } catch (Throwable t) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static byte[] readFile(File f) {
        try {
            java.io.FileInputStream in = new java.io.FileInputStream(f);
            byte[] d = U.readAll(in);
            in.close();
            return d;
        } catch (Throwable t) {
            return null;
        }
    }

    private static void trimCache() {
        try {
            File[] fs = cacheDir.listFiles();
            if (fs == null) return;
            long total = 0;
            for (File f : fs) total += f.length();
            if (total <= MAX_CACHE) return;
            java.util.Arrays.sort(fs, new java.util.Comparator<File>() {
                @Override
                public int compare(File a, File b) {
                    return Long.compare(a.lastModified(), b.lastModified());
                }
            });
            for (File f : fs) {
                if (total <= MAX_CACHE * 3 / 4) break;
                total -= f.length();
                f.delete();
            }
        } catch (Throwable ignored) {
        }
    }

    public static void clearCache() {
        if (cacheDir == null) return;
        File[] fs = cacheDir.listFiles();
        if (fs != null) for (File f : fs) f.delete();
    }
}
