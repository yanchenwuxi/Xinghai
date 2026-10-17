package com.zaka.xinghai.player;

import android.media.MediaPlayer;
import android.os.Build;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/**
 * MediaPlayer + SurfaceView。支持跨页面交接（详情/直播 → 横屏全屏不重载）。
 */
public class XPlayer implements SurfaceHolder.Callback,
        MediaPlayer.OnPreparedListener, MediaPlayer.OnErrorListener,
        MediaPlayer.OnCompletionListener, MediaPlayer.OnInfoListener,
        MediaPlayer.OnBufferingUpdateListener {

    public interface Listener {
        void onPrepared(int width, int height);

        void onError(int what, int extra);

        void onCompletion();

        void onInfo(String msg);

        void onBuffer(int percent);
    }

    /** 页面间临时交接，接收方在 onCreate 取走并置空 */
    public static XPlayer pendingHandoff;

    private SurfaceView surface;
    private MediaPlayer mp;
    private Listener listener;
    private String url = "";
    private boolean prepared = false;
    private float speed = 1f;
    private int videoW = 0, videoH = 0;
    private boolean wantPlaying = false;
    /** 交接中：surfaceDestroyed 不 pause、不清 display 逻辑由 take 接管 */
    private boolean handingOff = false;

    public XPlayer(SurfaceView sv) {
        bindSurface(sv);
    }

    private void bindSurface(SurfaceView sv) {
        if (surface != null) {
            try {
                surface.getHolder().removeCallback(this);
            } catch (Throwable ignored) {
            }
        }
        surface = sv;
        if (surface != null) {
            surface.getHolder().addCallback(this);
            if (mp != null && surface.getHolder().getSurface() != null
                    && surface.getHolder().getSurface().isValid()) {
                try {
                    mp.setDisplay(surface.getHolder());
                } catch (Throwable ignored) {
                }
            }
        }
    }

    public void setListener(Listener l) {
        listener = l;
    }

    public String url() {
        return url;
    }

    public boolean isPlaying() {
        try {
            return mp != null && mp.isPlaying();
        } catch (Throwable t) {
            return false;
        }
    }

    public boolean isPrepared() {
        return prepared;
    }

    public void start() {
        wantPlaying = true;
        try {
            if (mp != null && prepared && !mp.isPlaying()) mp.start();
        } catch (Throwable ignored) {
        }
    }

    public void pause() {
        wantPlaying = false;
        try {
            if (mp != null && prepared && mp.isPlaying()) mp.pause();
        } catch (Throwable ignored) {
        }
    }

    public void toggle() {
        if (isPlaying()) pause();
        else start();
    }

    public void seekTo(int ms) {
        try {
            if (mp != null && prepared) {
                if (Build.VERSION.SDK_INT >= 26) mp.seekTo(ms, MediaPlayer.SEEK_CLOSEST);
                else mp.seekTo(ms);
            }
        } catch (Throwable ignored) {
        }
    }

    public int position() {
        try {
            return mp == null || !prepared ? 0 : mp.getCurrentPosition();
        } catch (Throwable t) {
            return 0;
        }
    }

    public int duration() {
        try {
            return mp == null || !prepared ? 0 : mp.getDuration();
        } catch (Throwable t) {
            return 0;
        }
    }

    public int videoWidth() {
        return videoW;
    }

    public int videoHeight() {
        return videoH;
    }

    public void setSpeed(float s) {
        speed = s;
        try {
            if (mp != null && prepared && Build.VERSION.SDK_INT >= 23) {
                android.media.PlaybackParams p = mp.getPlaybackParams();
                p.setSpeed(speed);
                mp.setPlaybackParams(p);
            }
        } catch (Throwable ignored) {
        }
    }

    public void play(final String u) {
        url = u == null ? "" : u;
        wantPlaying = true;
        prepared = false;
        videoW = 0;
        videoH = 0;
        try {
            releasePlayer(false);
            mp = new MediaPlayer();
            mp.setOnPreparedListener(this);
            mp.setOnErrorListener(this);
            mp.setOnCompletionListener(this);
            mp.setOnInfoListener(this);
            mp.setOnBufferingUpdateListener(this);
            if (surface != null && surface.getHolder().getSurface() != null
                    && surface.getHolder().getSurface().isValid()) {
                mp.setDisplay(surface.getHolder());
            }
            mp.setDataSource(url);
            mp.prepareAsync();
        } catch (Throwable t) {
            if (listener != null) listener.onError(0, 0);
        }
    }

    /**
     * 发起交接：解绑当前 Surface，保持 MediaPlayer 继续播（有声无画一瞬间可接受），
     * 接收页 attachSurface 后恢复画面。
     */
    public void beginHandoff() {
        handingOff = true;
        wantPlaying = true;
        try {
            if (mp != null) mp.setDisplay(null);
        } catch (Throwable ignored) {
        }
        if (surface != null) {
            try {
                surface.getHolder().removeCallback(this);
            } catch (Throwable ignored) {
            }
        }
        surface = null;
        pendingHandoff = this;
    }

    /** 全屏页接管播放器并绑到新 SurfaceView */
    public void attachSurface(SurfaceView sv) {
        handingOff = false;
        bindSurface(sv);
        wantPlaying = true;
        try {
            if (mp != null && surface != null
                    && surface.getHolder().getSurface() != null
                    && surface.getHolder().getSurface().isValid()) {
                mp.setDisplay(surface.getHolder());
                if (prepared) start();
            }
        } catch (Throwable ignored) {
        }
    }

    public void release() {
        handingOff = false;
        if (pendingHandoff == this) pendingHandoff = null;
        releasePlayer(true);
        if (surface != null) {
            try {
                surface.getHolder().removeCallback(this);
            } catch (Throwable ignored) {
            }
            surface = null;
        }
    }

    private void releasePlayer(boolean full) {
        prepared = false;
        try {
            if (mp != null) {
                try {
                    mp.setDisplay(null);
                } catch (Throwable ignored) {
                }
                mp.reset();
                mp.release();
            }
        } catch (Throwable ignored) {
        }
        mp = null;
    }

    @Override
    public void onPrepared(MediaPlayer m) {
        prepared = true;
        try {
            videoW = m.getVideoWidth();
            videoH = m.getVideoHeight();
            if (Build.VERSION.SDK_INT >= 23 && speed != 1f) {
                android.media.PlaybackParams p = m.getPlaybackParams();
                p.setSpeed(speed);
                m.setPlaybackParams(p);
            }
            if (wantPlaying) m.start();
        } catch (Throwable ignored) {
        }
        if (listener != null) listener.onPrepared(videoW, videoH);
    }

    @Override
    public boolean onError(MediaPlayer m, int what, int extra) {
        prepared = false;
        if (listener != null) listener.onError(what, extra);
        return true;
    }

    @Override
    public void onCompletion(MediaPlayer m) {
        if (listener != null) listener.onCompletion();
    }

    @Override
    public boolean onInfo(MediaPlayer m, int what, int extra) {
        if (listener != null) {
            if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START) listener.onInfo("缓冲中…");
            else if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END) listener.onInfo("");
            else if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) listener.onInfo("");
        }
        return false;
    }

    @Override
    public void onBufferingUpdate(MediaPlayer m, int percent) {
        if (listener != null) listener.onBuffer(percent);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (mp == null) return;
        try {
            mp.setDisplay(holder);
            if (prepared && wantPlaying) start();
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        // 尺寸变化时重新 setDisplay，避免部分机型有声无画
        if (mp != null && w > 0 && h > 0) {
            try {
                mp.setDisplay(holder);
            } catch (Throwable ignored) {
            }
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (handingOff) return;
        try {
            if (mp != null) mp.setDisplay(null);
        } catch (Throwable ignored) {
        }
        // 不 pause：切页/旋转时 surface 会短暂销毁，停掉会导致体验差；仅解绑画面
    }
}
