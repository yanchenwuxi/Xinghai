#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
布局 / 代码 ID 交叉检查：
  代码里 R.id.xxx 用到的每个 id，必须在某个 layout XML 里存在；
  每个 layout 里的 @+id/xxx 也顺便统计出来。
这类问题编译器不报错，运行时 findViewById 返回 null 直接空指针，必须查。
"""
import os
import re
import sys
from collections import defaultdict

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res", "layout")
SRC = os.path.join(ROOT, "app", "src", "main", "java")

layout_ids = defaultdict(list)   # id -> [layout]
all_ids = set()

for f in os.listdir(RES):
    if not f.endswith(".xml"):
        continue
    text = open(os.path.join(RES, f), encoding="utf-8").read()
    for m in re.finditer(r'@\+id/([A-Za-z_][A-Za-z0-9_]*)', text):
        layout_ids[m.group(1)].append(f)
        all_ids.add(m.group(1))

used = defaultdict(list)         # id -> [(file, line)]
for dirpath, _, files in os.walk(SRC):
    for f in files:
        if not f.endswith(".java"):
            continue
        p = os.path.join(dirpath, f)
        for i, line in enumerate(open(p, encoding="utf-8"), 1):
            for m in re.finditer(r'R\.id\.([A-Za-z_][A-Za-z0-9_]*)', line):
                used[m.group(1)].append((os.path.relpath(p, ROOT), i))

missing = {k: v for k, v in used.items() if k not in all_ids}
print("布局文件: %d 个，布局内 id: %d 个" % (len([f for f in os.listdir(RES) if f.endswith('.xml')]), len(all_ids)))
print("代码引用的 id: %d 个" % len(used))
if missing:
    print("\n缺失的 id（代码用了但布局里没有，运行时会空指针）:")
    for k, v in sorted(missing.items()):
        print("  %-24s 用在: %s" % (k, ", ".join("%s:%d" % x for x in v[:3])))
    sys.exit(1)
print("\nID 交叉检查通过：0 处缺失")
