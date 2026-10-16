package com.zaka.xinghai.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.zaka.xinghai.R;
import com.zaka.xinghai.model.Vod;
import com.zaka.xinghai.util.Img;
import com.zaka.xinghai.util.U;

import java.util.ArrayList;
import java.util.List;

/** 海报网格 / 横滑推荐 共用的适配器。 */
public class VodAdapter extends BaseAdapter {

    public interface OnClick {
        void onClick(Vod v, int position);
    }

    public interface OnLongClick {
        void onLongClick(Vod v, int position);
    }

    private final Context ctx;
    private final List<Vod> list = new ArrayList<Vod>();
    /** 历史模式：带进度条与看到第几集 */
    private final boolean history;
    private OnClick click;
    private OnLongClick longClick;

    public VodAdapter(Context ctx, boolean history) {
        this.ctx = ctx;
        this.history = history;
    }

    public void setOnClick(OnClick c) {
        click = c;
    }

    public void setOnLongClick(OnLongClick c) {
        longClick = c;
    }

    public List<Vod> data() {
        return list;
    }

    public void set(List<Vod> data) {
        list.clear();
        if (data != null) list.addAll(data);
        notifyDataSetChanged();
    }

    public void add(List<Vod> data) {
        if (data != null) list.addAll(data);
        notifyDataSetChanged();
    }

    public void clear() {
        list.clear();
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return list.size();
    }

    @Override
    public Object getItem(int position) {
        return list.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(final int position, View v, ViewGroup parent) {
        if (v == null) v = LayoutInflater.from(ctx).inflate(R.layout.item_vod, parent, false);
        final Vod item = list.get(position);
        ImageView pic = (ImageView) v.findViewById(R.id.pic);
        TextView title = (TextView) v.findViewById(R.id.title);
        TextView note = (TextView) v.findViewById(R.id.note);
        TextView ep = (TextView) v.findViewById(R.id.ep);
        TextView sub = (TextView) v.findViewById(R.id.sub);
        ProgressBar pb = (ProgressBar) v.findViewById(R.id.progress);
        ImageView playIcon = (ImageView) v.findViewById(R.id.play_icon);

        title.setText(item.name);
        if (!U.empty(item.pic)) Img.load(pic, item.pic);
        else pic.setImageDrawable(null);

        if (!U.empty(item.note)) {
            note.setVisibility(View.VISIBLE);
            note.setText(item.note);
        } else {
            note.setVisibility(View.GONE);
        }

        String line = item.year;
        if (U.empty(line)) line = item.type;
        if (sub != null) {
            if (!U.empty(line) && !history) {
                sub.setVisibility(View.VISIBLE);
                sub.setText(line);
            } else {
                sub.setVisibility(View.GONE);
            }
        }

        if (history) {
            String epName = item.actor;
            long pos = 0, dur = 0;
            pos = parseLong(item.director);
            dur = parseLong(item.score);
            if (!U.empty(epName)) {
                ep.setVisibility(View.VISIBLE);
                int start = (int) (pos / 1000);
                ep.setText(epName + (start > 5 ? "  已看 " + U.time(pos) : ""));
            } else {
                ep.setVisibility(View.GONE);
            }
            if (dur > 0 && pos > 0) {
                pb.setVisibility(View.VISIBLE);
                pb.setProgress((int) (pos * 1000 / dur));
            } else {
                pb.setVisibility(View.GONE);
            }
            playIcon.setVisibility(View.VISIBLE);
        } else {
            ep.setVisibility(View.GONE);
            pb.setVisibility(View.GONE);
            playIcon.setVisibility(View.GONE);
        }

        v.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (click != null) click.onClick(item, position);
            }
        });
        v.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                if (longClick != null) {
                    longClick.onLongClick(item, position);
                    return true;
                }
                return false;
            }
        });
        return v;
    }

    private long parseLong(String s) {
        try {
            return Long.parseLong(s);
        } catch (Throwable t) {
            return 0;
        }
    }
}
