package com.zaka.xinghai.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;
import android.widget.ImageView;

import com.zaka.xinghai.core.App;
import com.zaka.xinghai.net.Http;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 自研图片加载：内存 LRU + 磁盘缓存 + 并发限流。
 * 不引第三方库，逻辑全在这。
 */
public final class Img {

    private static LruCache<String, Bitmap> mem;
    private static File disk;
    /** 正在下载的 URL -> 等待回调列表（同一张图多个 ImageView 都能收到） */
    private static final Map<String, List<Runnable>> pending = new LinkedHashMap<String, List<Runnable>>();
    private static final ArrayList<String> queue = new ArrayList<String>();
    private static int running = 0;
    private static final int MAX_PARALLEL = 4;

    private Img() {
    }

    public static void init(Context ctx) {
        int cache = (int) (Runtime.getRuntime().maxMemory() / 8);
        mem = new LruCache<String, Bitmap>(cache) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getByteCount();
            }
        };
        disk = new File(ctx.getCacheDir(), "img");
        if (!disk.exists()) disk.mkdirs();
    }

    public static File diskDir() {
        return disk;
    }

    public static Bitmap cached(String url) {
        if (url == null || url.length() == 0) return null;
        Bitmap b = mem.get(url);
        if (b != null) return b;
        File f = file(url);
        if (f.exists()) {
            Bitmap d = BitmapFactory.decodeFile(f.getAbsolutePath());
            if (d != null) {
                mem.put(url, d);
                return d;
            }
        }
        return null;
    }

    private static File file(String url) {
        return new File(disk, U.md5(url) + ".img");
    }

    /** 加载图片到 ImageView（带占位与 tag 防错位） */
    public static void load(final ImageView iv, final String url) {
        if (iv == null) return;
        iv.setTag(url);
        if (url == null || url.length() == 0) {
            iv.setImageDrawable(null);
            return;
        }
        final Bitmap c = cached(url);
        if (c != null) {
            iv.setImageBitmap(c);
            return;
        }
        iv.setImageDrawable(null);
        enqueue(url, new Runnable() {
            @Override
            public void run() {
                if (!url.equals(iv.getTag())) return;
                final Bitmap b = cached(url);
                if (b == null) return;
                App.onUi(new Runnable() {
                    @Override
                    public void run() {
                        if (url.equals(iv.getTag())) iv.setImageBitmap(b);
                    }
                });
            }
        });
    }

    private static void enqueue(final String url, final Runnable cb) {
        synchronized (pending) {
            List<Runnable> list = pending.get(url);
            if (list != null) {
                list.add(cb);
                return;
            }
            list = new ArrayList<Runnable>();
            list.add(cb);
            pending.put(url, list);
            queue.add(url);
        }
        pump();
    }

    private static void pump() {
        synchronized (pending) {
            while (running < MAX_PARALLEL && queue.size() > 0) {
                final String url = queue.remove(0);
                if (!pending.containsKey(url)) continue;
                running++;
                App.image(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            fetch(url);
                        } catch (Throwable ignored) {
                        }
                        final List<Runnable> cbs;
                        synchronized (pending) {
                            cbs = pending.remove(url);
                            running--;
                        }
                        if (cbs != null) {
                            for (Runnable cb : cbs) {
                                try {
                                    cb.run();
                                } catch (Throwable ignored) {
                                }
                            }
                        }
                        pump();
                    }
                });
            }
        }
    }

    /** 真正下载并落盘 */
    public static Bitmap fetch(String url) {
        Bitmap m = mem.get(url);
        if (m != null) return m;
        File f = file(url);
        if (f.exists()) {
            Bitmap d = BitmapFactory.decodeFile(f.getAbsolutePath());
            if (d != null) {
                mem.put(url, d);
                return d;
            }
            f.delete();
        }
        try {
            byte[] raw = Http.getBytes(url, null, 15000);
            if (raw == null || raw.length < 64) return null;
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(raw, 0, raw.length, o);
            int scale = 1;
            while (o.outWidth / (scale * 2) > 720) scale *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = scale;
            Bitmap b = BitmapFactory.decodeByteArray(raw, 0, raw.length, o2);
            if (b != null) {
                mem.put(url, b);
                FileOutputStream fos = new FileOutputStream(f);
                b.compress(Bitmap.CompressFormat.JPEG, 85, fos);
                fos.close();
            }
            return b;
        } catch (Throwable t) {
            return null;
        }
    }

    public static void clear() {
        if (mem != null) mem.evictAll();
        if (disk != null) {
            File[] fs = disk.listFiles();
            if (fs != null) for (File f : fs) f.delete();
        }
    }

    /** 预取（详情页预热封面用） */
    public static void prefetch(final String url) {
        if (U.empty(url) || cached(url) != null) return;
        App.image(new Runnable() {
            @Override
            public void run() {
                try {
                    fetch(url);
                } catch (Throwable ignored) {
                }
            }
        });
    }

    /** 下载原图到本地（保存图片用） */
    public static boolean saveTo(String url, File out) {
        InputStream in = null;
        try {
            byte[] raw = Http.getBytes(url, null, 20000);
            FileOutputStream fos = new FileOutputStream(out);
            fos.write(raw);
            fos.close();
            return true;
        } catch (Throwable t) {
            return false;
        } finally {
            try {
                if (in != null) in.close();
            } catch (Throwable ignored) {
            }
        }
    }
}
