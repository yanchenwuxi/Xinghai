package com.zaka.xinghai.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.zaka.xinghai.R;
import com.zaka.xinghai.core.App;
import com.zaka.xinghai.net.HlsProxy;
import com.zaka.xinghai.store.Store;
import com.zaka.xinghai.subtitle.SubStore;
import com.zaka.xinghai.util.Img;
import com.zaka.xinghai.util.U;

import java.util.ArrayList;
import java.util.List;

/**
 * 启动页：并行把该准备的准备好（存储、图片缓存、字幕缓存、加速通道预热），
 * 顺手把站点连通性摸一下，别等用户点进去才发现全挂。
 */
public class SplashActivity extends Activity {

    private TextView tip;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_splash);
        U.init(getResources());
        tip = (TextView) findViewById(R.id.splash_tip);
        App.run(new Runnable() {
            @Override
            public void run() {
                final List<String> steps = new ArrayList<String>();
                try {
                    Store.init(SplashActivity.this);
                    steps.add("存储就绪");
                } catch (Throwable ignored) {
                }
                try {
                    Img.init(SplashActivity.this);
                    steps.add("图片缓存就绪");
                } catch (Throwable ignored) {
                }
                try {
                    SubStore.init(SplashActivity.this);
                    steps.add("字幕缓存就绪");
                } catch (Throwable ignored) {
                }
                if (Store.boost()) {
                    try {
                        HlsProxy.start(SplashActivity.this);
                    } catch (Throwable ignored) {
                    }
                }
                App.onUi(new Runnable() {
                    @Override
                    public void run() {
                        tip.setText("准备完成，正在进入…");
                        App.onUi(new Runnable() {
                            @Override
                            public void run() {
                                startActivity(new Intent(SplashActivity.this, MainActivity.class));
                                overridePendingTransition(0, 0);
                                finish();
                            }
                        }, 320);
                    }
                });
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        View v = findViewById(R.id.splash_tip);
        if (v != null) v.setVisibility(View.GONE);
    }
}
