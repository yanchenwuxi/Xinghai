#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
星海 · 资源生成器
  1) 生成全部形状类 drawable（圆角卡片 / 标签 / 按钮 / 遮罩 / 进度条 …）
  2) 生成矢量图标（单色，运行时染色，未选中/选中同一套）
  3) 生成启动图标（各密度 PNG）
运行：python3 build/gen_res.py
"""
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")
DRW = os.path.join(RES, "drawable")


def w(name, body):
    p = os.path.join(DRW, name + ".xml")
    with open(p, "w", encoding="utf-8") as f:
        f.write('<?xml version="1.0" encoding="utf-8"?>\n' + body)
    return p


def shape(name, radius=None, solid=None, stroke=None, stroke_w=None,
          gradient=None, corners=None, oval=False):
    """corners: dict  tl,tr,bl,br 各自半径（覆盖 radius）"""
    out = ['<shape xmlns:android="http://schemas.android.com/apk/res/android"']
    if oval:
        out.append(' android:shape="oval">')
    else:
        out.append(' android:shape="rectangle">')
    if gradient:
        a, b, ang = gradient
        out.append('\n  <gradient android:startColor="%s" android:endColor="%s" android:angle="%d" />' % (a, b, ang))
    elif solid:
        out.append('\n  <solid android:color="%s" />' % solid)
    if stroke:
        out.append('\n  <stroke android:width="%s" android:color="%s" />' % (stroke_w or "1dip", stroke))
    if oval:
        out.append('\n  <size android:width="12dip" android:height="12dip" />')
    elif corners or radius:
        if corners:
            out.append('\n  <corners'
                       ' android:topLeftRadius="%s" android:topRightRadius="%s"'
                       ' android:bottomLeftRadius="%s" android:bottomRightRadius="%s" />'
                       % (corners.get("tl", "0dip"), corners.get("tr", "0dip"),
                          corners.get("bl", "0dip"), corners.get("br", "0dip")))
        else:
            out.append('\n  <corners android:radius="%s" />' % radius)
    out.append('\n</shape>\n')
    return w(name, "".join(out))


def selector(name, items):
    """items: [(state_attr or '', drawable)]  state_attr 例 'android:state_pressed="true"'"""
    out = ['<selector xmlns:android="http://schemas.android.com/apk/res/android">\n']
    for st, d in items:
        if st:
            out.append('  <item %s android:drawable="@drawable/%s" />\n' % (st, d))
        else:
            out.append('  <item android:drawable="@drawable/%s" />\n' % d)
    out.append('</selector>\n')
    return w(name, "".join(out))


def layer(name, items):
    out = ['<layer-list xmlns:android="http://schemas.android.com/apk/res/android">\n']
    for it in items:
        if isinstance(it, tuple):
            out.append('  <item android:id="@android:id/%s" %s>\n' % it[1])
        out.append(it if isinstance(it, str) else it[0])
    out.append('</layer-list>\n')
    return w(name, "".join(out))


# ---------------------------------------------------------------- 形状
def gen_shapes():
    # 搜索栏（主页 / 搜索页两种）
    shape("bg_search_bar", radius="18dip", solid="#1affffff")
    shape("bg_search_bar2", radius="10dip", solid="#1affffff")
    # 卡片
    shape("bg_card", radius="8dip", solid="#14ffffff")
    shape("bg_card_press", radius="8dip", solid="#33ffffff")
    selector("bg_card_sel", [('android:state_pressed="true"', "bg_card_press"), ("", "bg_card")])
    # 面板 / 弹窗
    shape("bg_panel", solid="#f21b1d24", corners={"tl": "14dip", "tr": "14dip", "bl": "0dip", "br": "0dip"})
    shape("bg_dialog", radius="12dip", solid="#f21b1d24")
    shape("bg_dialog_bottom", solid="#f21b1d24", corners={"tl": "14dip", "tr": "14dip", "bl": "0dip", "br": "0dip"})
    # 标签 / 芯片
    shape("bg_tag", radius="12dip", solid="#14ffffff")
    shape("bg_chip", radius="14dip", solid="#1affffff")
    shape("bg_chip_on", radius="14dip", solid="#33ffb40a")
    selector("bg_chip_sel", [('android:state_selected="true"', "bg_chip_on"), ("", "bg_chip")])
    # 线路 / 剧集按钮
    shape("bg_flag", radius="4dip", solid="#0fffffff")
    shape("bg_flag_on", radius="4dip", solid="#1fffffb4", stroke="#ffffb40a", stroke_w="1dip")
    selector("bg_flag_sel", [('android:state_selected="true"', "bg_flag_on"), ("", "bg_flag")])
    # 按钮
    shape("bg_btn_accent", radius="4dip", solid="#ffffb40a")
    shape("bg_btn_gray", radius="4dip", solid="#33ffffff")
    shape("bg_btn_dark", radius="6dip", solid="#66000000")
    shape("bg_btn_line", radius="4dip", solid="#00000000", stroke="#66ffffff", stroke_w="1dip")
    # 圆形播放按钮
    shape("bg_circle_black44", solid="#8f000000", oval=True)
    shape("bg_circle_black60", solid="#66000000", oval=True)
    shape("bg_circle_accent", solid="#ffffb40a", oval=True)
    # 收藏按钮（金色渐变胶囊）
    shape("bg_collect", radius="15dip", gradient=("#ffedc75f", "#fffee5a2", 0))
    shape("bg_collect_off", radius="15dip", gradient=("#ffedc75f", "#fffee5a2", 0))
    # 提示条
    shape("bg_note", radius="4dip", solid="#b3000000")
    shape("bg_note_accent", radius="4dip", solid="#cc1b1d24", stroke="#ffffb40a", stroke_w="1dip")
    # 遮罩
    shape("bg_scrim_top", gradient=("#b3000000", "#00000000", 270))
    shape("bg_scrim_bottom", gradient=("#00000000", "#cc000000", 270))
    shape("bg_scrim_right", gradient=("#1a000000", "#cc000000", 0))
    # 分割线
    shape("bg_divider", solid="#1fffffff")
    # 浮动球 / 悬浮条
    shape("bg_float", radius="20dip", solid="#cc1b1d24")
    shape("bg_hint_pill", radius="12dip", solid="#8f000000")
    # 设置项
    shape("bg_setting_item", radius="8dip", solid="#14ffffff")
    shape("bg_head", gradient=("#3d00509b", "#00001a37", 90))

    # 播放进度条
    layer("seek_progress", [
        '<item android:id="@android:id/background"><shape android:shape="rectangle">'
        '<corners android:radius="2dip" /><solid android:color="#33ffffff" /></shape></item>\n',
        '<item android:id="@android:id/secondaryProgress"><clip>'
        '<shape android:shape="rectangle"><corners android:radius="2dip" />'
        '<solid android:color="#66ffffff" /></shape></clip></item>\n',
        '<item android:id="@android:id/progress"><clip>'
        '<shape android:shape="rectangle"><corners android:radius="2dip" />'
        '<solid android:color="#ffffb40a" /></shape></clip></item>\n',
    ])
    shape("seek_thumb", solid="#ffffb40a", oval=True)
    shape("progress_h", radius="2dip", solid="#ffffb40a")
    shape("progress_bg_h", radius="2dip", solid="#33ffffff")


# ---------------------------------------------------------------- 图标
# 24x24 viewport，描边风格（运行时染色）
S = 'android:strokeColor="#ffffffff" android:strokeWidth="1.7" ' \
    'android:strokeLineCap="round" android:strokeLineJoin="round" android:fillColor="#00000000"'
F = 'android:fillColor="#ffffffff"'
T = 'android:strokeWidth="1.7" android:strokeLineCap="round" android:strokeLineJoin="round"'


def icon(name, paths):
    body = ['<vector xmlns:android="http://schemas.android.com/apk/res/android"'
            ' android:width="24dip" android:height="24dip"'
            ' android:viewportWidth="24" android:viewportHeight="24">\n']
    for d, style in paths:
        body.append('  <path android:pathData="%s" %s />\n' % (d, style))
    body.append('</vector>\n')
    return w(name, "".join(body))


def gen_icons():
    stroke = S
    fill = F
    both = S.replace('android:fillColor="#00000000"', 'android:fillColor="#ffffffff"')

    icon("ic_tab_home", [
        ("M3.6 11.2 L12 4 L20.4 11.2", stroke),
        ("M6.4 11.2 V20 H17.6 V11.2", stroke),
        ("M10 20 V15 H14 V20", stroke),
    ])
    icon("ic_tab_live", [
        ("M3 8 H21 V20 H3 Z", stroke),
        ("M8 8 L12 4 L16 8", stroke),
        ("M12 12.4 A2.6 2.6 0 1 1 11.99 12.4 Z", stroke),
    ])
    icon("ic_tab_short", [
        ("M4 5 H20 V19 H4 Z", stroke),
        ("M10.4 9 L16 12 L10.4 15 Z", fill),
    ])
    icon("ic_tab_history", [
        ("M12 12 m-8 0 a8 8 0 1 0 16 0 a8 8 0 1 0 -16 0", stroke),
        ("M12 7.4 V12.3 L15.6 14.4", stroke),
    ])
    icon("ic_tab_mine", [
        ("M12 11.4 A3.9 3.9 0 1 0 11.99 11.4 Z", stroke),
        ("M4.6 20.4 C4.6 16.9 7.9 14.6 12 14.6 C16.1 14.6 19.4 16.9 19.4 20.4", stroke),
    ])
    icon("ic_search", [
        ("M11 11 m-6.2 0 a6.2 6.2 0 1 0 12.4 0 a6.2 6.2 0 1 0 -12.4 0", stroke),
        ("M15.6 15.6 L20.4 20.4", stroke),
    ])
    icon("ic_search_gray", [
        ("M11 11 m-6.2 0 a6.2 6.2 0 1 0 12.4 0 a6.2 6.2 0 1 0 -12.4 0", stroke),
        ("M15.6 15.6 L20.4 20.4", stroke),
    ])
    icon("ic_refresh", [
        ("M20 12 A8 8 0 1 1 17.6 6.2", stroke),
        ("M20 3.6 V7.4 H16.2", stroke),
    ])
    icon("ic_back", [("M15.2 4.4 L7.6 12 L15.2 19.6", stroke)])
    icon("ic_play", [("M8 4.8 L19 12 L8 19.2 Z", fill)])
    icon("ic_pause", [
        ("M8 5 H10.6 V19 H8 Z", fill),
        ("M13.4 5 H16 V19 H13.4 Z", fill),
    ])
    icon("ic_next", [
        ("M6 5 L14.6 12 L6 19 Z", fill),
        ("M17 5 H18.6 V19 H17 Z", fill),
    ])
    icon("ic_prev", [
        ("M18 5 L9.4 12 L18 19 Z", fill),
        ("M5.4 5 H7 V19 H5.4 Z", fill),
    ])
    icon("ic_expand", [
        ("M9 4 H4 V9", stroke), ("M15 4 H20 V9", stroke),
        ("M20 15 V20 H15", stroke), ("M4 15 V20 H9", stroke),
    ])
    icon("ic_shrink", [
        ("M4 9 H9 V4", stroke), ("M20 9 H15 V4", stroke),
        ("M15 20 V15 H20", stroke), ("M9 20 V15 H4", stroke),
    ])
    icon("ic_lock", [
        ("M6.6 10.6 H17.4 V19.4 H6.6 Z", stroke),
        ("M9.2 10.6 V8.2 A2.8 2.8 0 0 1 14.8 8.2 V10.6", stroke),
    ])
    icon("ic_unlock", [
        ("M6.6 10.6 H17.4 V19.4 H6.6 Z", stroke),
        ("M9.2 10.6 V8.2 A2.8 2.8 0 0 1 14.8 8.2", stroke),
    ])
    icon("ic_subtitle", [
        ("M3.4 5.4 H20.6 V18.6 H3.4 Z", stroke),
        ("M7 11.4 H11.4", stroke), ("M13.6 11.4 H17", stroke),
        ("M8.4 14.6 H12", stroke), ("M14.4 14.6 H17", stroke),
    ])
    icon("ic_danmaku", [
        ("M3.4 5.4 H20.6 V17.4 H10.4 L6 20.4 V17.4 H3.4 Z", stroke),
        ("M7 9.4 H17", stroke), ("M7 13 H14", stroke),
    ])
    icon("ic_setting", [
        ("M4 7.4 H20", stroke), ("M4 12 H20", stroke), ("M4 16.6 H20", stroke),
        ("M9 7.4 m-1.9 0 a1.9 1.9 0 1 0 3.8 0 a1.9 1.9 0 1 0 -3.8 0", again_solid(both)),
        ("M15.4 12 m-1.9 0 a1.9 1.9 0 1 0 3.8 0 a1.9 1.9 0 1 0 -3.8 0", again_solid(both)),
        ("M8 16.6 m-1.9 0 a1.9 1.9 0 1 0 3.8 0 a1.9 1.9 0 1 0 -3.8 0", again_solid(both)),
    ])
    icon("ic_filter", [("M4 5.4 H20 L14 12.6 V19.6 L10 17.4 V12.6 Z", stroke)])
    icon("ic_close", [
        ("M6 6 L18 18", stroke), ("M18 6 L6 18", stroke),
    ])
    icon("ic_star", [
        ("M12 3.6 L14.7 9.2 L20.8 10 L16.4 14.2 L17.4 20.4 L12 17.5 L6.6 20.4 L7.6 14.2 L3.2 10 L9.3 9.2 Z", fill),
    ])
    icon("ic_star_off", [
        ("M12 3.6 L14.7 9.2 L20.8 10 L16.4 14.2 L17.4 20.4 L12 17.5 L6.6 20.4 L7.6 14.2 L3.2 10 L9.3 9.2 Z",
         'android:strokeColor="#ffffffff" android:strokeWidth="1.5" android:fillColor="#00000000"'),
    ])
    icon("ic_arrow_right", [("M9.4 5 L16.4 12 L9.4 19", stroke)])
    icon("ic_arrow_down", [("M5 9.4 L12 16.4 L19 9.4", stroke)])
    icon("ic_arrow_up", [("M5 14.6 L12 7.6 L19 14.6", stroke)])
    icon("ic_clear", [
        ("M12 12 m-8.2 0 a8.2 8.2 0 1 0 16.4 0 a8.2 8.2 0 1 0 -16.4 0", stroke),
        ("M9 9 L15 15", stroke), ("M15 9 L9 15", stroke),
    ])
    icon("ic_site", [
        ("M4 4.6 H20 V9.4 H4 Z", stroke), ("M4 14.6 H20 V19.4 H4 Z", stroke),
        ("M7 7 H7.01", stroke), ("M7 17 H7.01", stroke),
    ])
    icon("ic_speed", [
        ("M12 12 m-8.2 0 a8.2 8.2 0 1 0 16.4 0 a8.2 8.2 0 1 0 -16.4 0", stroke),
        ("M12 12 L16.4 7.6", stroke),
    ])
    icon("ic_track", [
        ("M9 17.4 V6.4 L18 4.6 V15.6", stroke),
        ("M9 17.4 A2.4 2.4 0 1 1 6.6 15 A2.4 2.4 0 0 1 9 17.4 Z", stroke),
        ("M18 15.6 A2.4 2.4 0 1 1 15.6 13.2 A2.4 2.4 0 0 1 18 15.6 Z", stroke),
    ])
    icon("ic_scale", [
        ("M4 7 H20 V17 H4 Z", stroke),
        ("M9 12 H15", stroke), ("M12 7 V17", stroke),
    ])
    icon("ic_info", [
        ("M12 12 m-8.4 0 a8.4 8.4 0 1 0 16.8 0 a8.4 8.4 0 1 0 -16.8 0", stroke),
        ("M12 10.6 V16.4", stroke), ("M12 7.6 V7.7", stroke),
    ])
    icon("ic_download", [
        ("M12 4 V15", stroke), ("M7.4 10.6 L12 15.2 L16.6 10.6", stroke),
        ("M5 19.4 H19", stroke),
    ])
    icon("ic_parse", [("M13.4 3 L6 13.4 H11.4 L10.6 21 L18 10.6 H12.6 Z", stroke)])
    icon("ic_cast", [
        ("M3.4 6.4 H20.6 V17.6 H12", stroke),
        ("M3.4 20.6 A5.6 5.6 0 0 1 3.4 15", stroke),
        ("M3.4 16.6 A1.6 1.6 0 0 1 3.4 19.8", stroke),
        ("M3.4 12 A9 9 0 0 1 12 21", stroke),
    ])
    icon("ic_player", [
        ("M3.4 5.4 H20.6 V18.6 H3.4 Z", stroke),
        ("M10 9.2 L15.6 12 L10 14.8 Z", fill),
    ])
    icon("ic_check", [("M5 12.4 L10 17.4 L19 6.6", 'android:strokeColor="#ffffb40a" '
                       'android:strokeWidth="2" android:strokeLineCap="round" android:fillColor="#00000000"')])
    icon("ic_delete", [
        ("M5.4 7.4 H18.6", stroke), ("M8.4 7.4 V19.4 H15.6 V7.4", stroke),
        ("M10.4 7.4 V4.8 H13.6 V7.4", stroke),
    ])
    icon("ic_edit", [
        ("M5 19 H8.4 L19 8.4 L15.6 5 L5 15.6 Z", stroke),
    ])
    icon("ic_hot", [
        ("M12 21 C8.1 21 6 18.6 6 15.4 C6 11.8 9 10 10.2 7 C10.7 5.8 11 4.6 11 3 "
         "C13.4 5 16.4 8 16.4 11 C17.5 12 18 13.4 18 15.4 C18 18.6 15.9 21 12 21 Z",
         'android:strokeColor="#ffffffff" android:strokeWidth="1.6" android:fillColor="#00000000"'),
    ])
    icon("ic_history2", [
        ("M3.6 12 A8.4 8.4 0 1 0 6.6 5.6", stroke),
        ("M3.4 4 V8 H7.4", stroke),
        ("M12 8 V12.6 L15.6 14.6", stroke),
    ])
    icon("ic_more", [
        ("M6 12 m-1.6 0 a1.6 1.6 0 1 0 3.2 0 a1.6 1.6 0 1 0 -3.2 0", fill),
        ("M12 12 m-1.6 0 a1.6 1.6 0 1 0 3.2 0 a1.6 1.6 0 1 0 -3.2 0", fill),
        ("M18 12 m-1.6 0 a1.6 1.6 0 1 0 3.2 0 a1.6 1.6 0 1 0 -3.2 0", fill),
    ])
    icon("ic_sort", [
        ("M4 6.6 H20", stroke), ("M4 12 H14", stroke), ("M4 17.4 H9", stroke),
    ])
    icon("ic_folder", [
        ("M3.4 6.4 H9.6 L11.6 9 H20.6 V18.6 H3.4 Z", stroke),
    ])
    icon("ic_tv", [
        ("M3.4 6.4 H20.6 V17.4 H3.4 Z", stroke),
        ("M8.4 20.6 H15.6", stroke),
    ])
    icon("ic_float", [
        ("M4.4 6.4 H19.6 V15.4 H4.4 Z", stroke),
        ("M9.4 18.6 H14.6", stroke),
        ("M9.6 9.4 H14.6 V12.4 H9.6 Z", stroke),
    ])
    icon("ic_brightness", [
        ("M12 12 m-3.6 0 a3.6 3.6 0 1 0 7.2 0 a3.6 3.6 0 1 0 -7.2 0", stroke),
        ("M12 2.6 V5", stroke), ("M12 19 V21.4", stroke),
        ("M2.6 12 H5", stroke), ("M19 12 H21.4", stroke),
    ])
    icon("ic_volume", [
        ("M4 9.4 H7.4 L12 5.6 V18.4 L7.4 14.6 H4 Z", stroke),
        ("M15 9 A4.4 4.4 0 0 1 15 15", stroke),
        ("M17.6 6.6 A8 8 0 0 1 17.6 17.4", stroke),
    ])
    icon("ic_folder_open", [
        ("M3.4 18.6 V6.4 H9.6 L11.6 9 H18 V11.4", stroke),
        ("M3.4 18.6 L6.6 11.4 H21 L17.8 18.6 Z", stroke),
    ])
    icon("ic_push", [
        ("M12 3.4 L20.6 8 V16 L12 20.6 L3.4 16 V8 Z", stroke),
        ("M12 8.6 V15.4", stroke),
    ])
    icon("ic_web", [
        ("M12 12 m-8.4 0 a8.4 8.4 0 1 0 16.8 0 a8.4 8.4 0 1 0 -16.8 0", stroke),
        ("M3.6 12 H20.4", stroke),
        ("M12 3.6 A11 11 0 0 1 12 20.4 A11 11 0 0 1 12 3.6", stroke),
    ])
    icon("ic_live", [
        ("M12 12 m-2.4 0 a2.4 2.4 0 1 0 4.8 0 a2.4 2.4 0 1 0 -4.8 0", stroke),
        ("M7.4 7.4 A6.4 6.4 0 0 0 7.4 16.6", stroke),
        ("M16.6 7.4 A6.4 6.4 0 0 1 16.6 16.6", stroke),
        ("M4.6 4.6 A10 10 0 0 0 4.6 19.4", stroke),
        ("M19.4 4.6 A10 10 0 0 1 19.4 19.4", stroke),
    ])
    icon("ic_short_video", [
        ("M6 4 H18 A2 2 0 0 1 20 6 V18 A2 2 0 0 1 18 20 H6 A2 2 0 0 1 4 18 V6 A2 2 0 0 1 6 4 Z", stroke),
        ("M10.4 9 L15.6 12 L10.4 15 Z", fill),
    ])


def again_solid(style):
    return style


# ---------------------------------------------------------------- 启动图标
def gen_launcher():
    from PIL import Image, ImageDraw
    sizes = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
    for d, s in sizes.items():
        img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
        dr = ImageDraw.Draw(img)
        # 深蓝圆角底 + 橙色播放三角
        r = int(s * 0.22)
        dr.rounded_rectangle([0, 0, s - 1, s - 1], radius=r, fill=(0, 26, 55, 255))
        dr.rounded_rectangle([0, 0, s - 1, s - 1], radius=r, outline=(255, 180, 10, 220),
                             width=max(1, s // 32))
        m = s * 0.3
        dr.polygon([(m, s * 0.26), (s - m * 0.75, s * 0.5), (m, s * 0.74)], fill=(255, 180, 10, 255))
        p = os.path.join(RES, "mipmap-" + d, "ic_launcher.png")
        img.save(p)


if __name__ == "__main__":
    os.makedirs(DRW, exist_ok=True)
    gen_shapes()
    gen_icons()
    gen_launcher()
    n = len([f for f in os.listdir(DRW) if f.endswith(".xml")])
    print("drawable: %d 个" % n)
