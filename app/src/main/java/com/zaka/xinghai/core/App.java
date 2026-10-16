package com.zaka.xinghai.core;

import android.app.Application;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.zaka.xinghai.store.Store;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 全局上下文与线程池。
 * 解析、下载、图片分开用不同池，谁也别拖累谁。
 */
public class App extends Application {

    private static App app;
    private static Handler ui;

    /** 解析 / 网络任务（并发抓取用） */
    private static ExecutorService io;
    /** 图片加载 */
    private static ExecutorService img;
    /** 单线程串行任务（写盘、日志） */
    private static ExecutorService single;

    public static int threadCount = 4;

    @Override
    public void onCreate() {
        super.onCreate();
        app = this;
        ui = new Handler(Looper.getMainLooper());
        io = new ThreadPoolExecutor(0, 64, 30L, TimeUnit.SECONDS,
                new SynchronousQueue<Runnable>(), new ThreadPoolExecutor.CallerRunsPolicy());
        img = Executors.newFixedThreadPool(4);
        single = Executors.newSingleThreadExecutor();
        com.zaka.xinghai.util.U.init(getResources());
        Store.init(this);
    }

    public static App get() {
        return app;
    }

    public static Context ctx() {
        return app;
    }

    public static Handler ui() {
        return ui;
    }

    public static void run(Runnable r) {
        io.execute(r);
    }

    public static void image(Runnable r) {
        img.execute(r);
    }

    public static void once(Runnable r) {
        single.execute(r);
    }

    public static void onUi(Runnable r) {
        if (Looper.myLooper() == Looper.getMainLooper()) r.run();
        else ui.post(r);
    }

    public static void onUi(Runnable r, long delay) {
        ui.postDelayed(r, delay);
    }
}
