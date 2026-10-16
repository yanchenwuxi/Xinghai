package com.zaka.xinghai.net;

import com.zaka.xinghai.util.U;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

/**
 * 自研网络层：
 *  - 连接复用（HttpURLConnection 自带 keep-alive）
 *  - gzip / deflate 双兜底（有些源站回 deflate，不兜会拿到乱码）
 *  - 可配 UA / Referer
 *  - 多地址并发竞争：谁先出有效结果用谁
 */
public final class Http {

    public static final String UA =
            "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/110 Mobile Safari/537.36";

    /** 播放器/采集用 UA，跟参照客户端一致 */
    public static final String UA_PLAYER = "okhttp/3.12.13";

    public static final int TIMEOUT = 12000;

    private Http() {
    }

    public static class Head {
        public String key;
        public String value;

        public Head(String k, String v) {
            key = k;
            value = v;
        }
    }

    public static List<Head> headers(String... kv) {
        List<Head> out = new ArrayList<Head>();
        for (int i = 0; i + 1 < kv.length; i += 2) out.add(new Head(kv[i], kv[i + 1]));
        return out;
    }

    public static String get(String url) throws Exception {
        return get(url, null, TIMEOUT);
    }

    public static String get(String url, List<Head> heads, int timeout) throws Exception {
        byte[] b = request(url, heads, timeout);
        return b == null ? "" : decodeText(b);
    }

    public static byte[] getBytes(String url, List<Head> heads, int timeout) throws Exception {
        return request(url, heads, timeout);
    }

    /** 探测一条地址是否活着（只看头部/少量数据） */
    public static boolean alive(String url, int timeout) {
        try {
            byte[] b = request(url, headers("Range", "bytes=0-2048"), timeout);
            return b != null && b.length > 0;
        } catch (Throwable t) {
            return false;
        }
    }

    private static byte[] request(String url, List<Head> heads, int timeout) throws Exception {
        HttpURLConnection conn = null;
        try {
            URL u = new URL(url);
            conn = (HttpURLConnection) u.openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(Math.max(3000, Math.min(timeout, 8000)));
            conn.setReadTimeout(Math.max(3000, timeout));
            conn.setRequestProperty("User-Agent", UA);
            conn.setRequestProperty("Accept-Encoding", "gzip, deflate");
            conn.setRequestProperty("Accept", "*/*");
            conn.setRequestProperty("Connection", "keep-alive");
            if (heads != null) {
                for (Head h : heads) conn.setRequestProperty(h.key, h.value);
            }
            int code = conn.getResponseCode();
            if (code >= 400) throw new Exception("HTTP " + code);
            InputStream in = conn.getInputStream();
            byte[] raw;
            String enc = conn.getContentEncoding();
            if (enc != null && enc.toLowerCase().contains("gzip")) {
                raw = U.readAll(new GZIPInputStream(in));
            } else if (enc != null && enc.toLowerCase().contains("deflate")) {
                raw = U.readAll(new InflaterInputStream(in, new Inflater(true)));
            } else {
                raw = U.readAll(in);
            }
            in.close();
            // 有些源站声明不老实，按魔数再兜一层
            raw = fixMagic(raw);
            if (raw.length > 3 && (raw[0] & 0xff) == 0x1f && (raw[1] & 0xff) == 0x8b) {
                raw = U.readAll(new GZIPInputStream(new java.io.ByteArrayInputStream(raw)));
            } else if (raw.length > 2 && (raw[0] & 0xff) == 0x78) {
                try {
                    raw = U.readAll(new InflaterInputStream(new java.io.ByteArrayInputStream(raw), new Inflater(false)));
                } catch (Throwable ignored) {
                }
            }
            return raw;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static byte[] fixMagic(byte[] raw) {
        if (raw == null || raw.length < 2) return raw == null ? new byte[0] : raw;
        if ((raw[0] & 0xff) == 0x1f && (raw[1] & 0xff) == 0x8b) {
            try {
                return U.readAll(new GZIPInputStream(new java.io.ByteArrayInputStream(raw)));
            } catch (Throwable ignored) {
            }
        }
        if ((raw[0] & 0xff) == 0x78) {
            try {
                return U.readAll(new InflaterInputStream(new java.io.ByteArrayInputStream(raw)));
            } catch (Throwable ignored) {
            }
        }
        return raw;
    }

    /** 字节 -> 文本，自动判定 utf-8 / gbk */
    public static String decodeText(byte[] raw) {
        if (raw == null) return "";
        String s = new String(raw, java.nio.charset.Charset.forName("UTF-8"));
        if (s.indexOf('\uFFFD') >= 0) {
            try {
                s = new String(raw, java.nio.charset.Charset.forName("GBK"));
            } catch (Throwable ignored) {
            }
        }
        return s;
    }

    /** 分段下载（带进度回调） */
    public interface Progress {
        void on(long read, long total);
    }

    public static byte[] download(String url, Progress cb, int timeout) throws Exception {
        HttpURLConnection conn = null;
        try {
            URL u = new URL(url);
            conn = (HttpURLConnection) u.openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(timeout);
            conn.setRequestProperty("User-Agent", UA_PLAYER);
            conn.setRequestProperty("Accept-Encoding", "identity");
            int code = conn.getResponseCode();
            if (code >= 400) throw new Exception("HTTP " + code);
            long total = conn.getContentLength();
            InputStream in = conn.getInputStream();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[32768];
            long read = 0;
            int n;
            while ((n = in.read(buf)) > 0) {
                bos.write(buf, 0, n);
                read += n;
                if (cb != null) cb.on(read, total);
            }
            in.close();
            return bos.toByteArray();
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /** 多地址并发竞争：返回第一个有效结果（加速起播/探测用） */
    public static <T> T race(List<String> urls, final Handler<T> handler, int timeout) {
        if (urls == null || urls.size() == 0) return null;
        if (urls.size() == 1) {
            try {
                return handler.handle(urls.get(0));
            } catch (Throwable t) {
                return null;
            }
        }
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(urls.size(), 4));
        ExecutorCompletionService<T> ecs = new ExecutorCompletionService<T>(pool);
        final AtomicBoolean done = new AtomicBoolean(false);
        for (final String url : urls) {
            ecs.submit(new Callable<T>() {
                @Override
                public T call() {
                    if (done.get()) return null;
                    try {
                        T t = handler.handle(url);
                        if (t != null) done.set(true);
                        return t;
                    } catch (Throwable e) {
                        return null;
                    }
                }
            });
        }
        try {
            for (int i = 0; i < urls.size(); i++) {
                try {
                    T t = ecs.take().get();
                    if (t != null) return t;
                } catch (Throwable ignored) {
                }
            }
        } finally {
            pool.shutdownNow();
        }
        return null;
    }

    public interface Handler<T> {
        T handle(String url) throws Exception;
    }
}
