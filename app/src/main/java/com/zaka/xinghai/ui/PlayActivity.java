package com.zaka.xinghai.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.view.SurfaceView;
import android.widget.TextView;

import com.zaka.xinghai.R;
import com.zaka.xinghai.core.App;
import com.zaka.xinghai.model.Record;
import com.zaka.xinghai.model.Site;
import com.zaka.xinghai.model.Vod;
import com.zaka.xinghai.net.AdFilter;
import com.zaka.xinghai.net.HlsProxy;
import com.zaka.xinghai.player.XPlayer;
import com.zaka.xinghai.store.Store;
import com.zaka.xinghai.subtitle.Cue;
import com.zaka.xinghai.subtitle.SubSearch;
import com.zaka.xinghai.ui.dialog.InputDialog;
import com.zaka.xinghai.ui.dialog.ListSheet;
import com.zaka.xinghai.ui.dialog.SubtitleSheet;
import com.zaka.xinghai.util.U;
import com.zaka.xinghai.view.Sheet;
import com.zaka.xinghai.view.SubtitleView;
import com.zaka.xinghai.view.VodInfoView;
import com.zaka.xinghai.view.VodParts;

import java.util.ArrayList;
import java.util.List;

/**
 * 播放页：
 *   上半 16:9 画面 + 自绘字幕层，下半可滚动的简介 / 线路 / 画质 / 选集
 *   全屏控制层：顶部信息、中间播放控制、左右侧按钮、底部进度条 + 功能条
 *   手势：左亮度 / 右音量 / 横滑快进 / 单击显隐控制层 / 双击暂停
 *   起播超时自动换线、出错自动换线、进度记忆续播
 */
public class PlayActivity extends Activity implements XPlayer.Listener {

    private static Vod handoff;
    private static int handoffFlag = 0;
    private static int handoffEp = 0;

    private Vod vod;
    private int flagIndex = 0;
    private int epIndex = 0;

    private XPlayer player;
    private SurfaceView video;
    private SubtitleView subtitleView;
    private FrameLayout videoArea;
    private View control;
    private ImageView ctlPlay, ctlPlay2, ctlLock, ctlExpand;
    private TextView ctlName, ctlNet, ctlClock, ctlStatus, ctlSpeed, ctlPos, ctlDur, ctlHint;
    private ProgressBar ctlSpin;
    private SeekBar seek;
    private LinearLayout content, btnRow;

    private final Handler ui = new Handler();
    private boolean dragging = false;
    private boolean showControl = true;
    private boolean locked = false;
    private boolean started = false;
    private long watchdogAt = 0;
    private int autoSwitch = 0;
    private float brightness = 0.5f;
    private int volume = 0;
    private int maxVolume = 15;
    private float downX, downY;
    private int downPos = 0;
    private boolean moved = false;
    private long lastTap = 0;
    private float speed = 1f;
    private int scaleMode = 0;

    public static void go(Context c, Vod v, int flag, int ep, boolean parse) {
        handoff = v;
        handoffFlag = flag;
        handoffEp = ep;
        Intent i = new Intent(c, PlayActivity.class);
        i.putExtra("parse", parse);
        c.startActivity(i);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        U.init(getResources());
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_play);
        vod = handoff;
        handoff = null; // 尽早释放静态引用，降低竞态与泄漏
        if (vod == null) {
            finish();
            return;
        }
        flagIndex = Math.max(0, Math.min(handoffFlag, Math.max(0, vod.flags.size() - 1)));
        epIndex = handoffEp;

        videoArea = (FrameLayout) findViewById(R.id.video_area);
        video = (SurfaceView) findViewById(R.id.video);
        subtitleView = (SubtitleView) findViewById(R.id.subtitle);
        control = findViewById(R.id.control);
        content = (LinearLayout) findViewById(R.id.play_content);
        ctlName = (TextView) control.findViewById(R.id.ctl_name);
        ctlNet = (TextView) control.findViewById(R.id.ctl_net);
        ctlClock = (TextView) control.findViewById(R.id.ctl_clock);
        ctlStatus = (TextView) control.findViewById(R.id.ctl_status);
        ctlSpeed = (TextView) control.findViewById(R.id.ctl_speed);
        ctlPos = (TextView) control.findViewById(R.id.ctl_pos);
        ctlDur = (TextView) control.findViewById(R.id.ctl_dur);
        ctlHint = (TextView) control.findViewById(R.id.ctl_hint);
        ctlSpin = (ProgressBar) control.findViewById(R.id.ctl_spin);
        ctlPlay = (ImageView) control.findViewById(R.id.ctl_play);
        ctlPlay2 = (ImageView) control.findViewById(R.id.ctl_play2);
        ctlLock = (ImageView) control.findViewById(R.id.ctl_lock);
        ctlExpand = (ImageView) control.findViewById(R.id.ctl_expand);
        seek = (SeekBar) control.findViewById(R.id.ctl_seek);
        btnRow = (LinearLayout) control.findViewById(R.id.ctl_btns);

        subtitleView.setEnabledSub(Store.subEnabled());
        subtitleView.setTextSizeSp(Store.subSize());
        subtitleView.setOffset(Store.subOffset());
        scaleMode = Store.scale();
        speed = Store.speed();

        layoutVideoArea();
        player = new XPlayer(video);
        player.setListener(this);
        bindControls();
        buildContent();
        startProgressLoop();
        playEpisode(flagIndex, epIndex, true);
    }

    // ------------------------------------------------------------ 画面区域
    private void layoutVideoArea() {
        View scroll = findViewById(R.id.play_scroll);
        boolean land = isLandscape();
        // 横屏：全屏只留画面，隐藏下方详情区，避开状态栏
        if (scroll != null) scroll.setVisibility(land ? View.GONE : View.VISIBLE);
        applySystemUi(land);

        int screenW = getResources().getDisplayMetrics().widthPixels;
        int screenH = getResources().getDisplayMetrics().heightPixels;
        int h;
        if (land) {
            // 横屏铺满可用高度（沉浸后即整屏）
            h = screenH;
        } else {
            switch (scaleMode) {
                case 1:
                    h = screenW * 9 / 16;
                    break;
                case 2:
                    h = screenW * 3 / 4;
                    break;
                case 3:
                    h = (int) (screenH * 0.55f);
                    break;
                case 4:
                    h = player != null && player.videoHeight() > 0
                            ? screenW * player.videoHeight() / Math.max(1, player.videoWidth())
                            : screenW * 9 / 16;
                    break;
                default:
                    h = screenW * 9 / 16;
                    break;
            }
            // 竖屏限制最大高度，避免和详情区「叠成一整页」
            int maxH = (int) (screenH * 0.48f);
            if (h > maxH) h = maxH;
            if (h < U.dp(180)) h = U.dp(180);
        }
        ViewGroup.LayoutParams lp = videoArea.getLayoutParams();
        lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
        lp.height = h;
        videoArea.setLayoutParams(lp);
    }

    private void applySystemUi(boolean land) {
        View decor = getWindow().getDecorView();
        if (land) {
            int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            decor.setSystemUiVisibility(flags);
            if (Build.VERSION.SDK_INT >= 21) {
                getWindow().setStatusBarColor(0x00000000);
                getWindow().setNavigationBarColor(0x00000000);
            }
        } else {
            decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            if (Build.VERSION.SDK_INT >= 21) {
                getWindow().setStatusBarColor(0xff000000);
                getWindow().setNavigationBarColor(0xff111111);
            }
        }
    }

    private boolean isLandscape() {
        return getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
    }

    // ------------------------------------------------------------ 控制层
    private void bindControls() {
        control.findViewById(R.id.ctl_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        ctlPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                player.toggle();
                refreshPlayIcon();
            }
        });
        ctlPlay2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                player.toggle();
                refreshPlayIcon();
            }
        });
        control.findViewById(R.id.ctl_prev).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (epIndex > 0) playEpisode(flagIndex, epIndex - 1, true);
                else U.toast("已经是第一集了");
            }
        });
        control.findViewById(R.id.ctl_next).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                nextEpisode();
            }
        });
        ctlLock.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (locked) {
                    locked = false;
                    ctlLock.setImageResource(R.drawable.ic_unlock);
                    setControlsVisible(true);
                    U.toast("已解锁");
                } else {
                    locked = true;
                    ctlLock.setImageResource(R.drawable.ic_lock);
                    setControlsVisible(false);
                    U.toast("已锁定，点左下角解锁");
                }
            }
        });
        control.findViewById(R.id.ctl_danmaku).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                U.toast("弹幕需要弹幕源接入，这个接口没给出弹幕数据");
            }
        });
        control.findViewById(R.id.ctl_float).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                enterFloat();
            }
        });
        control.findViewById(R.id.ctl_more).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMore();
            }
        });
        ctlExpand.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleOrientation();
            }
        });
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser) {
                    int dur = player.duration();
                    ctlPos.setText(U.time(dur * progress / 1000L));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar sb) {
                dragging = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar sb) {
                dragging = false;
                int dur = player.duration();
                player.seekTo((int) (dur * sb.getProgress() / 1000f));
            }
        });

        // 底部功能条：跟参照一致的按钮串
        String[] names = {"EXO", "刷新", "重播", "字幕", "片头", "片尾", "音轨", "选集", "弹幕", "设置"};
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            TextView tv = U.text(this, names[i], 14, 0xffffffff);
            tv.setPadding(U.dp(18), U.dp(5), U.dp(18), U.dp(10));
            if ("字幕".equals(names[i]) && !Store.subEnabled()) tv.setTextColor(0x8fffffff);
            tv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    barAction(idx);
                }
            });
            btnRow.addView(tv);
        }
        setupGestures();
        ctlName.setText(vod.name);
        refreshPlayIcon();
    }

    private void barAction(int idx) {
        switch (idx) {
            case 0:
                U.toast("当前解码方式：" + (Store.decode() == 0 ? "硬解" : "软解")
                        + "（切换在「设置」里）");
                break;
            case 1:
                playEpisode(flagIndex, epIndex, true);
                break;
            case 2:
                player.seekTo(0);
                player.start();
                refreshPlayIcon();
                break;
            case 3:
                SubtitleSheet.show(this, vod.name, vod.type, subtitleView, new Runnable() {
                    @Override
                    public void run() {
                        refreshBarLabels();
                    }
                });
                break;
            case 4:
                setHead();
                break;
            case 5:
                setTail();
                break;
            case 6:
                showTracks();
                break;
            case 7:
                showEpisodes();
                break;
            case 8:
                U.toast("弹幕需要弹幕源接入，这个接口没给出弹幕数据");
                break;
            default:
                showSettings();
                break;
        }
    }

    private void refreshBarLabels() {
        for (int i = 0; i < btnRow.getChildCount(); i++) {
            TextView tv = (TextView) btnRow.getChildAt(i);
            if ("字幕".equals(tv.getText().toString())) {
                tv.setTextColor(subtitleView.isEnabledSub() ? 0xffffffff : 0x8fffffff);
            }
        }
    }

    // ------------------------------------------------------------ 手势
    private void setupGestures() {
        final AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (am != null) {
            maxVolume = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            volume = am.getStreamVolume(AudioManager.STREAM_MUSIC);
        }
        try {
            brightness = getWindow().getAttributes().screenBrightness;
            if (brightness < 0) brightness = 0.5f;
        } catch (Throwable t) {
            brightness = 0.5f;
        }

        videoArea.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = e.getX();
                        downY = e.getY();
                        downPos = player.position();
                        moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = e.getX() - downX, dy = e.getY() - downY;
                        if (!moved && (Math.abs(dx) > U.dp(8) || Math.abs(dy) > U.dp(8))) moved = true;
                        if (!moved || locked) return true;
                        if (Math.abs(dx) > Math.abs(dy)) {
                            int dur = player.duration();
                            float ratio = dx / v.getWidth();
                            int target = (int) (downPos + dur * ratio);
                            target = Math.max(0, Math.min(dur, target));
                            hint("快进/快退  " + U.time(target) + " / " + U.time(dur));
                            downPos = target;
                            downX = e.getX();
                        } else {
                            float ratio = -dy / v.getHeight();
                            if (e.getX() < v.getWidth() / 2f) {
                                brightness = Math.max(0.02f, Math.min(1f, brightness + ratio));
                                WindowManager.LayoutParams lp = getWindow().getAttributes();
                                lp.screenBrightness = brightness;
                                getWindow().setAttributes(lp);
                                hint("亮度 " + (int) (brightness * 100) + "%");
                            } else {
                                int nv = (int) Math.max(0, Math.min(maxVolume, volume + ratio * maxVolume));
                                if (am != null) am.setStreamVolume(AudioManager.STREAM_MUSIC, nv, 0);
                                hint("音量 " + (nv * 100 / Math.max(1, maxVolume)) + "%");
                            }
                            downY = e.getY();
                            volume = am == null ? volume : am.getStreamVolume(AudioManager.STREAM_MUSIC);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        hideHint();
                        if (!moved) {
                            long now = System.currentTimeMillis();
                            if (now - lastTap < 320) {
                                player.toggle();
                                refreshPlayIcon();
                                lastTap = 0;
                            } else {
                                lastTap = now;
                                if (!locked) setControlsVisible(!showControl);
                                ui.postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (System.currentTimeMillis() - lastTap >= 300) lastTap = 0;
                                    }
                                }, 340);
                            }
                        } else if (!locked) {
                            int dur = player.duration();
                            if (dur > 0) player.seekTo(Math.max(0, Math.min(dur, downPos)));
                        }
                        return true;
                    default:
                        return false;
                }
            }
        });
    }

    private void hint(String s) {
        ctlHint.setText(s);
        ctlHint.setVisibility(View.VISIBLE);
    }

    private void hideHint() {
        ctlHint.setVisibility(View.GONE);
    }

    private void setControlsVisible(boolean v) {
        showControl = v;
        control.setVisibility(v ? View.VISIBLE : View.GONE);
    }

    private void refreshPlayIcon() {
        boolean playing = player != null && player.isPlaying();
        ctlPlay.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
        ctlPlay2.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
    }

    // ------------------------------------------------------------ 内容区
    private VodInfoView infoView;
    private VodParts.Eps epsView;

    private void buildContent() {
        content.removeAllViews();
        infoView = new VodInfoView(this);
        infoView.bind(vod);
        content.addView(infoView);

        if (vod.flags.size() > 0) {
            List<String> names = new ArrayList<String>();
            for (Vod.Flag f : vod.flags) names.add(f.name);
            content.addView(VodParts.flags(this, "线路", names, flagIndex, new VodParts.OnFlag() {
                @Override
                public void onFlag(int idx) {
                    flagIndex = idx;
                    epIndex = 0;
                    buildContent();
                    playEpisode(flagIndex, 0, true);
                }
            }));
        }

        if (vod.subCates != null && vod.subCates.size() > 0) {
            List<String> qs = new ArrayList<String>();
            for (com.zaka.xinghai.model.Cate c : vod.subCates) qs.add(c.name);
            content.addView(VodParts.flags(this, "画质", qs, 0, null));
        }

        epsView = new VodParts.Eps(this, new VodParts.OnEpisode() {
            @Override
            public void onEpisode(int flag, int ep) {
                playEpisode(flag, ep, true);
            }
        });
        if (vod.flags.size() > 0) {
            epsView.set(flagIndex, vod.eps(flagIndex), epIndex);
            content.addView(epsView.root);
        }
    }

    // ------------------------------------------------------------ 播放
    private void playEpisode(int flag, int ep, boolean restart) {
        if (vod.eps(flag).size() == 0) {
            U.toast("这条线路没有可播的剧集");
            return;
        }
        if (ep < 0 || ep >= vod.eps(flag).size()) ep = 0;
        flagIndex = flag;
        epIndex = ep;
        Vod.Episode e = vod.eps(flag).get(ep);
        String url = resolveUrl(e.url);
        started = false;
        watchdogAt = System.currentTimeMillis();
        ctlSpin.setVisibility(View.VISIBLE);
        ctlStatus.setVisibility(View.VISIBLE);
        ctlStatus.setText("正在加载中~");
        ctlName.setText(vod.name + "  ·  " + (U.empty(e.name) ? ("第" + (ep + 1) + "集") : e.name));
        player.play(url);
        findViewById(R.id.video_play_icon).setVisibility(View.GONE);
        control.postDelayed(new Runnable() {
            @Override
            public void run() {
                setControlsVisible(true);
            }
        }, 60);
    }

    /** 地址处理链：去广告解中转 → 剥推广参数 → 需要时走本地代理 */
    private String resolveUrl(String raw) {
        String url = raw;
        if (Store.adFilter()) {
            String before = url;
            url = AdFilter.unwrap(url);
            url = AdFilter.cleanUrl(url);
            if (before != null && !before.equals(url)) AdFilter.blockedLines++;
        }
        if (!U.empty(url) && url.contains("m3u8") && Store.boost()) {
            if (!HlsProxy.isRunning()) HlsProxy.start(this);
            if (HlsProxy.isRunning()) {
                return HlsProxy.base() + "/pl?u=" + U.urlEncode(url);
            }
        }
        return url;
    }

    private void nextEpisode() {
        List<Vod.Episode> eps = vod.eps(flagIndex);
        if (epIndex + 1 < eps.size()) {
            playEpisode(flagIndex, epIndex + 1, true);
        } else {
            U.toast("已经是最后一集了");
        }
    }

    /** 起播失败或超时：换线，换不动就报清楚 */
    private void tryNextLine(String reason) {
        autoSwitch++;
        if (vod.flags.size() <= 1) {
            ctlSpin.setVisibility(View.INVISIBLE);
            ctlStatus.setText(reason + "（只有一条线路，换个接口试试）");
            return;
        }
        if (autoSwitch > vod.flags.size()) {
            ctlSpin.setVisibility(View.INVISIBLE);
            ctlStatus.setText(reason + "，所有线路都试过了");
            return;
        }
        int next = (flagIndex + 1) % vod.flags.size();
        U.toast(reason + "，自动换到「" + vod.flags.get(next).name + "」");
        playEpisode(next, 0, true);
    }

    // ------------------------------------------------------------ 回调
    @Override
    public void onPrepared(int w, int h) {
        started = true;
        autoSwitch = 0;
        ctlSpin.setVisibility(View.INVISIBLE);
        ctlStatus.setVisibility(View.GONE);
        refreshPlayIcon();
        layoutVideoArea();
        // 进度记忆续播
        Record r = Store.findHistory(vod.siteKey + "#" + vod.id, epIndex);
        if (r != null && r.position > 5000) {
            player.seekTo((int) r.position);
            U.toast("已跳到上次看到的位置 " + U.time(r.position));
        }
        saveHistory(false);
        // 自动匹配字幕
        if (Store.subEnabled() && Store.subAuto() && subtitleView.cues().size() == 0) {
            autoSubtitle();
        }
    }

    @Override
    public void onError(int what, int extra) {
        ctlSpin.setVisibility(View.INVISIBLE);
        ctlStatus.setVisibility(View.VISIBLE);
        tryNextLine("这条线路播不了");
    }

    @Override
    public void onCompletion() {
        saveHistory(true);
        List<Vod.Episode> eps = vod.eps(flagIndex);
        if (epIndex + 1 < eps.size()) {
            playEpisode(flagIndex, epIndex + 1, true);
        } else {
            ctlStatus.setVisibility(View.VISIBLE);
            ctlStatus.setText("已经播完了");
            setControlsVisible(true);
        }
    }

    @Override
    public void onInfo(String msg) {
        if (!started) return;
        if (U.empty(msg)) {
            ctlStatus.setVisibility(View.GONE);
        } else {
            ctlStatus.setVisibility(View.VISIBLE);
            ctlStatus.setText(msg);
        }
    }

    @Override
    public void onBuffer(int percent) {
    }

    // ------------------------------------------------------------ 进度循环
    private void startProgressLoop() {
        ui.post(new Runnable() {
            @Override
            public void run() {
                try {
                    int dur = player.duration();
                    int pos = player.position();
                    if (dur > 0) {
                        ctlDur.setText(U.time(dur));
                        if (!dragging) {
                            seek.setProgress((int) (pos * 1000L / dur));
                            ctlPos.setText(U.time(pos));
                        }
                    }
                    subtitleView.setPosition(pos);
                    int rate = HlsProxy.rateKbps();
                    if (rate > 0) ctlNet.setText(rate >= 1024
                            ? String.format(java.util.Locale.US, "%.1fMB/s", rate / 1024f)
                            : rate + "KB/s");
                    java.util.Calendar c = java.util.Calendar.getInstance();
                    ctlClock.setText(String.format(java.util.Locale.US, "%02d:%02d",
                            c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE)));
                    // 起播看门狗
                    int timeout = Store.playTimeout();
                    if (!started && timeout > 0 && watchdogAt > 0
                            && System.currentTimeMillis() - watchdogAt > timeout * 1000L) {
                        watchdogAt = 0;
                        tryNextLine("起播超时");
                    }
                    // 首片预取：当前分片之后的那一片
                } catch (Throwable ignored) {
                }
                ui.postDelayed(this, 500);
            }
        });
    }

    // ------------------------------------------------------------ 字幕
    private void autoSubtitle() {
        final String name = vod.name;
        final String type = vod.type;
        App.run(new Runnable() {
            @Override
            public void run() {
                try {
                    List<SubSearch.Item> list = SubSearch.searchSmart(name, type);
                    if (list.size() == 0) return;
                    final List<Cue> cues = SubSearch.load(list.get(0));
                    App.onUi(new Runnable() {
                        @Override
                        public void run() {
                            subtitleView.setCues(cues);
                            subtitleView.setOffset(Store.subOffset());
                            subtitleView.setEnabledSub(true);
                            U.toast("已自动匹配字幕：" + cues.size() + " 条");
                        }
                    });
                } catch (Throwable ignored) {
                }
            }
        });
    }

    // ------------------------------------------------------------ 各面板
    private void showEpisodes() {
        final Sheet sheet = new Sheet(this, "选集 · " + vod.flags.size() + " 条线路");
        if (vod.flags.size() > 0) {
            List<String> names = new ArrayList<String>();
            for (Vod.Flag f : vod.flags) names.add(f.name);
            sheet.add(VodParts.flags(this, "线路", names, flagIndex, new VodParts.OnFlag() {
                @Override
                public void onFlag(int idx) {
                    sheet.dismiss();
                    playEpisode(idx, 0, true);
                }
            }));
            final VodParts.Eps eps = new VodParts.Eps(this, new VodParts.OnEpisode() {
                @Override
                public void onEpisode(int flag, int ep) {
                    sheet.dismiss();
                    playEpisode(flag, ep, true);
                }
            });
            eps.set(flagIndex, vod.eps(flagIndex), epIndex);
            sheet.add(eps.root);
        }
        sheet.show();
    }

    private void showSpeed() {
        final String[] names = {"0.5x", "0.75x", "1.0x", "1.25x", "1.5x", "2.0x", "3.0x"};
        final float[] vals = {0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 3f};
        int sel = 2;
        for (int i = 0; i < vals.length; i++) if (Math.abs(vals[i] - speed) < 0.01f) sel = i;
        ListSheet.show(this, "播放倍速", java.util.Arrays.asList(names), sel, new ListSheet.OnPick() {
            @Override
            public void onPick(int index, String value) {
                speed = vals[index];
                boolean ok = player.setSpeed(speed);
                ctlSpeed.setVisibility(speed == 1f ? View.GONE : View.VISIBLE);
                ctlSpeed.setText(value);
                if (!ok) U.toast("这台设备的内核不支持改倍速，已按 1.0x 播");
            }
        });
    }

    private void showScale() {
        final String[] names = {"自适应", "16:9", "4:3", "铺满", "原始"};
        ListSheet.show(this, "画面比例", java.util.Arrays.asList(names), scaleMode,
                new ListSheet.OnPick() {
                    @Override
                    public void onPick(int index, String value) {
                        scaleMode = index;
                        Store.setScale(index);
                        layoutVideoArea();
                    }
                });
    }

    private void showTracks() {
        List<String> tracks = player.tracks();
        if (tracks.size() == 0) {
            U.toast("这条片子内核里没有可切换的音轨");
            return;
        }
        ListSheet.show(this, "音轨", tracks, 0, new ListSheet.OnPick() {
            @Override
            public void onPick(int index, String value) {
                U.toast("已选择 " + value);
            }
        });
    }

    private void showDecode() {
        String[] names = {"硬解（省电、流畅）", "软解（兼容好）"};
        ListSheet.show(this, "解码方式", java.util.Arrays.asList(names), Store.decode(),
                new ListSheet.OnPick() {
                    @Override
                    public void onPick(int index, String value) {
                        Store.setDecode(index);
                        U.toast("已切换为" + (index == 0 ? "硬解" : "软解") + "（下一集生效）");
                    }
                });
    }

    private void showSettings() {
        final Sheet sheet = new Sheet(this, "播放设置");
        sheet.add(Sheet.row(this, "播放倍速（当前 " + speed + "x）", false));
        ((TextView) sheet.content().getChildAt(sheet.content().getChildCount() - 1))
                .setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showSpeed();
                    }
                });
        TextView sc = Sheet.row(this, "画面比例（当前 "
                + new String[]{"自适应", "16:9", "4:3", "铺满", "原始"}[scaleMode] + "）", false);
        sc.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showScale();
            }
        });
        sheet.add(sc);
        TextView de = Sheet.row(this, "解码方式（当前 " + (Store.decode() == 0 ? "硬解" : "软解") + "）", false);
        de.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDecode();
            }
        });
        sheet.add(de);
        TextView ad = Sheet.row(this, "去广告：" + (Store.adFilter() ? "已开启" : "已关闭"), false);
        ad.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Store.setAdFilter(!Store.adFilter());
                U.toast(Store.adFilter() ? "去广告已开启" : "去广告已关闭");
                sheet.dismiss();
            }
        });
        sheet.add(ad);
        TextView bo = Sheet.row(this, "播放加速：" + (Store.boost() ? "已开启" : "已关闭"), false);
        bo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Store.setBoost(!Store.boost());
                if (Store.boost()) HlsProxy.start(PlayActivity.this);
                else HlsProxy.stop();
                U.toast("下次播放生效");
                sheet.dismiss();
            }
        });
        sheet.add(bo);
        TextView sub = Sheet.row(this, "字幕：" + (subtitleView.isEnabledSub() ? "已开启" : "已关闭"), false);
        sub.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sheet.dismiss();
                SubtitleSheet.show(PlayActivity.this, vod.name, vod.type, subtitleView, null);
            }
        });
        sheet.add(sub);
        sheet.show();
    }

    private void showMore() {
        final Sheet sheet = new Sheet(this, "更多");
        TextView a = Sheet.row(this, "画面比例", false);
        a.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showScale();
            }
        });
        sheet.add(a);
        TextView b = Sheet.row(this, "音轨", false);
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showTracks();
            }
        });
        sheet.add(b);
        TextView c = Sheet.row(this, "横竖屏", false);
        c.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleOrientation();
                sheet.dismiss();
            }
        });
        sheet.add(c);
        TextView d = Sheet.row(this, "片头设置", false);
        d.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setHead();
            }
        });
        sheet.add(d);
        TextView e = Sheet.row(this, "片尾设置", false);
        e.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setTail();
            }
        });
        sheet.add(e);
        sheet.show();
    }

    private void setHead() {
        InputDialog.show(this, "片头时间（秒）", "例如 90，点一下跳过片头",
                String.valueOf(Store.head() / 1000), false, new InputDialog.OnOk() {
                    @Override
                    public void onOk(String text) {
                        long ms = U.sec(text);
                        Store.setHead(ms);
                        player.seekTo((int) ms);
                        U.toast("片头已设为 " + U.time(ms));
                    }
                });
    }

    private void setTail() {
        InputDialog.show(this, "片尾时间（秒，0 = 关）", "例如 120，看到这里就跳过片尾",
                String.valueOf(Store.tail() / 1000), false, new InputDialog.OnOk() {
                    @Override
                    public void onOk(String text) {
                        long ms = U.sec(text);
                        Store.setTail(ms);
                        U.toast(ms == 0 ? "已关闭片尾跳过" : "片尾已设为 " + U.time(ms));
                    }
                });
    }

    private void toggleOrientation() {
        if (isLandscape()) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            ctlExpand.setImageResource(R.drawable.ic_expand);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            ctlExpand.setImageResource(R.drawable.ic_shrink);
        }
        ui.postDelayed(new Runnable() {
            @Override
            public void run() {
                layoutVideoArea();
                setControlsVisible(true);
            }
        }, 500);
    }

    private void enterFloat() {
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                android.app.PictureInPictureParams.Builder bb =
                        new android.app.PictureInPictureParams.Builder();
                bb.setAspectRatio(new android.util.Rational(16, 9));
                enterPictureInPictureMode(bb.build());
            } catch (Throwable t) {
                U.toast("这台设备不支持小窗播放");
            }
        } else {
            U.toast("小窗需要 Android 8.0 以上");
        }
    }

    // ------------------------------------------------------------ 生命周期
    @Override
    public void onConfigurationChanged(Configuration cfg) {
        super.onConfigurationChanged(cfg);
        layoutVideoArea();
    }

    private void saveHistory(boolean finished) {
        try {
            Record r = VodInfoView.toRecord(vod);
            r.flagIndex = flagIndex;
            r.epIndex = epIndex;
            if (vod.flags.size() > flagIndex) r.flagName = vod.flags.get(flagIndex).name;
            List<Vod.Episode> eps = vod.eps(flagIndex);
            if (epIndex < eps.size()) {
                r.epName = eps.get(epIndex).name;
                r.epUrl = eps.get(epIndex).url;
            }
            r.position = finished ? 0 : player.position();
            r.duration = player.duration();
            r.time = System.currentTimeMillis();
            Store.putHistory(r);
        } catch (Throwable ignored) {
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (Build.VERSION.SDK_INT < 26 || !isInPictureInPictureMode()) {
            player.pause();
            refreshPlayIcon();
        }
        saveHistory(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (player != null && started) {
            player.start();
            refreshPlayIcon();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            saveHistory(false);
            ui.removeCallbacksAndMessages(null);
            if (player != null) player.release();
        } catch (Throwable ignored) {
        }
        if (handoff == vod) handoff = null;
    }

    @Override
    public void onBackPressed() {
        if (isLandscape()) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            return;
        }
        super.onBackPressed();
    }
}
