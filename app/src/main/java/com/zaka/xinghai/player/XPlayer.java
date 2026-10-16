package com.zaka.xinghai.player;

import android.media.MediaPlayer;
import android.os.Build;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.util.ArrayList;
import java.util.List;

/**
 * 自研播放内核封装：MediaPlayer + SurfaceView。
 * 为什么不用现成的 VideoView：倍速、画面比例、音轨信息、错误换线这些都得自己控，
 * 包一层之后这些都能拿到，也不受第三方播放器 SDK 牵制。
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

    private final SurfaceView surface;
    private MediaPlayer mp;
    private Listener listener;
    private String url = "";
    private boolean prepared = false;
    private float speed = 1f;
    private int videoW = 0, videoH = 0;
    private boolean wantPlaying = false;

    public XPlayer(SurfaceView sv) {
        surface = sv;
        surface.getHolder().addCallback(this);
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

    /** 倍速（API 23+ 才有，低版本如实返回 false） */
    public boolean setSpeed(float s) {
        speed = s;
        if (mp == null || !prepared) return false;
        try {
            if (Build.VERSION.SDK_INT >= 23) {
                android.media.PlaybackParams p = mp.getPlaybackParams();
                p.setSpeed(s);
                mp.setPlaybackParams(p);
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    public float speed() {
        return speed;
    }

    /** 音轨列表（内核给不出来就是空列表，不编造） */
    public List<String> tracks() {
        List<String> out = new ArrayList<String>();
        try {
            if (mp == null) return out;
            MediaPlayer.TrackInfo[] infos = mp.getTrackInfo();
            if (infos == null) return out;
            int i = 0;
            for (MediaPlayer.TrackInfo t : infos) {
                if (t.getTrackType() == MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_AUDIO) {
                    i++;
                    String lang = t.getLanguage();
                    if (lang == null || lang.length() == 0 || "und".equals(lang)) lang = "默认";
                    out.add("音轨 " + i + "（" + lang + "）");
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    public int videoWidth() {
        return videoW;
    }

    public int videoHeight() {
        return videoH;
    }

    /** 开始播放一个地址 */
    public void play(final String u) {
        url = u == null ? "" : u;
        wantPlaying = true;
        try {
            releasePlayer();
            mp = new MediaPlayer();
            mp.setDataSource(url);
            if (surface.getHolder().getSurface() != null
                    && surface.getHolder().getSurface().isValid()) {
                mp.setDisplay(surface.getHolder());
            }
            mp.setOnPreparedListener(this);
            mp.setOnErrorListener(this);
            mp.setOnCompletionListener(this);
            mp.setOnInfoListener(this);
            mp.setOnBufferingUpdateListener(this);
            mp.setAudioStreamType(android.media.AudioManager.STREAM_MUSIC);
            mp.prepareAsync();
        } catch (Throwable t) {
            if (listener != null) listener.onError(-1, 0);
        }
    }

    public void release() {
        releasePlayer();
    }

    private void releasePlayer() {
        prepared = false;
        try {
            if (mp != null) {
                mp.reset();
                mp.release();
            }
        } catch (Throwable ignored) {
        }
        mp = null;
    }

    // ------------------------------------------------------------ 回调
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

    // SurfaceView 生命周期：切后台回来接着播，不重开播放器
    // 关键：surface 销毁后必须重新 setDisplay，否则常见黑屏有声
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
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        try {
            if (mp != null) mp.setDisplay(null);
        } catch (Throwable ignored) {
        }
        pause();
    }
}
