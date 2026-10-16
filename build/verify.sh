#!/usr/bin/env bash
# APK 交付前静态验证：包信息 / 签名 / 对齐 / dex 完整性 / 类与组件一致性
set -e
if [ -n "$JDK_HOME" ]; then export PATH="$JDK_HOME/bin:$PATH"; fi
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SDK="${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}"
BT="$(ls -d "$SDK"/build-tools/* 2>/dev/null | sort -V | tail -1)"
APK="${1:-$ROOT/build/out/xinghai-signed.apk}"
TMP="$ROOT/build/verify"
rm -rf "$TMP"; mkdir -p "$TMP"

echo "════ 1. 包信息 ════"
"$BT/aapt2" dump badging "$APK" | grep -E "^package|sdkVersion|targetSdkVersion|application-label|launchable-activity|uses-permission" | head -12

echo
echo "════ 2. 签名校验 ════"
"$BT/apksigner" verify --print-certs "$APK" | head -8

echo
echo "════ 3. 对齐校验 ════"
"$BT/zipalign" -c -p 4 "$APK" && echo "ALIGN_OK"

echo
echo "════ 4. 包内容 ════"
unzip -l "$APK" | tail -4
echo "dex: $(unzip -l "$APK" | grep -oE 'classes[0-9]*\.dex' | sort -u | tr '\n' ' ')"
echo "资源条目: $(unzip -l "$APK" | grep -cE 'res/|resources.arsc')"

echo
echo "════ 5. dex 完整性 ════"
(cd "$TMP" && unzip -o -q "$APK" "classes*.dex")
for f in "$TMP"/classes*.dex; do
  "$BT/dexdump" -f "$f" | grep -E "dex_version|checksum|file_size" | head -3
done

echo
echo "════ 6. 应用类是否齐（源码 vs dex）════"
SRC="$ROOT/app/src/main/java"
"$BT/dexdump" "$TMP"/classes*.dex 2>/dev/null | grep "Class descriptor" \
  | grep -oE "Lcom/zaka/xinghai[^;]*;" | sort -u > "$TMP/dex_classes.txt"
find "$SRC" -name '*.java' | sed "s|$SRC/||; s|\.java$||" | sort -u > "$TMP/src_classes.txt"
MISS=0
while read -r c; do
  desc="L$(echo "$c" | tr '.' '/');"
  if ! grep -qx "$desc" "$TMP/dex_classes.txt"; then
    echo "  缺失: $c"; MISS=$((MISS+1))
  fi
done < "$TMP/src_classes.txt"
echo "  源码类: $(wc -l < "$TMP/src_classes.txt")   dex 内应用类: $(wc -l < "$TMP/dex_classes.txt")   缺失: $MISS"
[ "$MISS" -eq 0 ] || exit 1

echo
echo "════ 7. 清单组件与 dex 一致性 ════"
python3 - "$APK" "$TMP/dex_classes.txt" "$BT" <<'PY'
import re, subprocess, sys
apk, dexlist, bt = sys.argv[1], sys.argv[2], sys.argv[3]
xml = subprocess.run([bt + "/aapt2", "dump", "xmltree", "--file", "AndroidManifest.xml", apk],
                     capture_output=True, text=True).stdout
names = re.findall(r'android:name\(0x[0-9a-f]+\)="(com\.zaka\.xinghai[^"]*)"', xml)
classes = set()
for line in open(dexlist):
    line = line.strip()
    if line.startswith("L") and line.endswith(";"):
        classes.add(line[1:-1].replace("/", "."))
bad = 0
for n in sorted(set(names)):
    if n not in classes:
        print("  清单里声明但 dex 里没有:", n); bad += 1
print("  清单组件类: %d，全部命中 dex: %s" % (len(set(names)), "是" if bad == 0 else "否"))
sys.exit(1 if bad else 0)
PY

echo
echo "════ 8. 布局里的自定义控件类是否真在 dex ════"
python3 - "$ROOT" "$TMP/dex_classes.txt" <<'PYEOF'
import os, re, sys
root, dexlist = sys.argv[1], sys.argv[2]
res = os.path.join(root, "app/src/main/res/layout")
classes = set()
for line in open(dexlist):
    line = line.strip()
    if line.startswith("L") and line.endswith(";"):
        classes.add(line[1:-1].replace("/", "."))
bad = 0
seen = set()
for f in os.listdir(res):
    if not f.endswith(".xml"):
        continue
    for m in re.finditer(r'<([A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)+)[\s/>]', open(os.path.join(res, f), encoding="utf-8").read()):
        cls = m.group(1)
        if cls.startswith("android.") or cls in seen:
            continue
        seen.add(cls)
        if cls not in classes:
            print("  布局 %s 引用的控件类不在 dex: %s" % (f, cls))
            bad += 1
print("  引用自定义控件: %d 个，全部命中 dex: %s" % (len(seen), "是" if bad == 0 else "否"))
sys.exit(1 if bad else 0)
PYEOF

echo
echo "════ 9. 布局 / 代码 ID 交叉检查 ════"
python3 "$ROOT/build/check_ids.py"

echo
echo "全部静态校验通过"
