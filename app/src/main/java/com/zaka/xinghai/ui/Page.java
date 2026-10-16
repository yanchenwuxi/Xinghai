package com.zaka.xinghai.ui;

import android.view.View;
import android.widget.FrameLayout;

import com.zaka.xinghai.core.App;

/** 页面基类：五个 Tab 各自一个实例，切页不重建（跟参照的 ViewPager2 keep 行为对齐）。 */
public abstract class Page {

    protected final MainActivity act;
    protected FrameLayout host;
    protected View root;
    private boolean firstShow = true;

    public Page(MainActivity act) {
        this.act = act;
    }

    /** 由子类提供内容 View */
    protected abstract View onCreate();

    public View view() {
        if (root == null) {
            root = onCreate();
        }
        return root;
    }

    /** 每次切到该页都会调，第一次是 onFirstShow */
    public void onShow() {
        if (firstShow) {
            firstShow = false;
            onFirstShow();
        }
    }

    public void onHide() {
    }

    protected void onFirstShow() {
    }

    /** 切到别的页时停掉播放之类的动作 */
    public void onLeave() {
    }

    protected void ui(Runnable r) {
        App.onUi(r);
    }

    protected void bg(Runnable r) {
        App.run(r);
    }
}
