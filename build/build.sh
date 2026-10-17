#!/usr/bin/env bash
# ============================================================
# 星海 · 命令行打包脚本
#   build-tools: aapt2 / d8 / zipalign / apksigner
#   platforms  : android.jar
# 用法:
#   export ANDROID_SDK_ROOT=/path/to/android-sdk
#   export JDK_HOME=/path/to/jdk          # 可选
#   bash build/build.sh --sign
# ============================================================
set -e

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT/app/src/main"
OUT="$ROOT/build/out"
GEN="$ROOT/build/gen"
CLASSES="$ROOT/build/classes"
DEX="$ROOT/build/dex"

SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Android/Sdk}}"
JAVAC="javac"
if [ -n "$JDK_HOME" ]; then
  export PATH="$JDK_HOME/bin:$PATH"
  JAVAC="$JDK_HOME/bin/javac"
fi

BT="$(ls -d "$SDK"/build-tools/* 2>/dev/null | sort -V | tail -1)"
ANDROID_JAR="$(ls -d "$SDK"/platforms/*/android.jar 2>/dev/null | sort -V | tail -1)"
if [ -z "$BT" ] || [ -z "$ANDROID_JAR" ]; then
  echo "找不到 Android SDK，请设置 ANDROID_SDK_ROOT（需要 build-tools 和 platforms）"
  exit 1
fi
echo "SDK        : $SDK"
echo "build-tools: $BT"
echo "android.jar: $ANDROID_JAR"

rm -rf "$GEN" "$CLASSES" "$DEX" "$OUT"
mkdir -p "$GEN" "$CLASSES" "$DEX" "$OUT"

echo "[1/5] 编译资源 (aapt2 compile)"
"$BT/aapt2" compile --dir "$SRC/res" -o "$OUT/res.zip"

echo "[2/5] 链接资源 + 生成 R.java (aapt2 link)"
MANIFEST_TMP="$OUT/AndroidManifest.xml"
sed 's|<manifest |<manifest package="com.zaka.xinghai" |' "$SRC/AndroidManifest.xml" > "$MANIFEST_TMP"
"$BT/aapt2" link \
  -o "$OUT/base.apk" \
  -I "$ANDROID_JAR" \
  --manifest "$MANIFEST_TMP" \
  --java "$GEN" \
  --min-sdk-version 21 \
  --target-sdk-version 34 \
  --version-code 5 \
  --version-name 2.0.2 \
  --auto-add-overlay \
  "$OUT/res.zip"

echo "[3/5] 编译 Java 源码 (javac)"
find "$SRC/java" "$GEN" -name '*.java' > "$OUT/sources.txt"
"$JAVAC" -encoding UTF-8 -nowarn -source 8 -target 8 \
  -bootclasspath "$ANDROID_JAR" \
  -cp "$ANDROID_JAR" \
  -d "$CLASSES" \
  @"$OUT/sources.txt"

echo "[4/5] 转 dex (d8)"
find "$CLASSES" -name '*.class' > "$OUT/classes.txt"
"$BT/d8" --release --min-api 21 --lib "$ANDROID_JAR" \
  --output "$DEX" @"$OUT/classes.txt"

echo "[5/5] 组装 APK"
cp "$OUT/base.apk" "$OUT/xinghai-unsigned.apk"
(cd "$DEX" && zip -q -X "$OUT/xinghai-unsigned.apk" classes*.dex)
"$BT/zipalign" -f -p 4 "$OUT/xinghai-unsigned.apk" "$OUT/xinghai-aligned.apk"

# 默认自动签名：无密钥时生成 debug 密钥（仅用于安装验证，非发布密钥）
SIGN="${1:-}"
if [ "$SIGN" = "--sign" ] || [ "$SIGN" = "" ] || [ "$SIGN" = "--debug-sign" ]; then
  KS="${KEYSTORE:-}"
  ALIAS="${KEY_ALIAS:-}"
  PASS="${KEY_PASS:-}"
  if [ -z "$KS" ] || [ ! -f "$KS" ]; then
    if [ -f "$ROOT/keys/starfilm.p12" ]; then
      KS="$ROOT/keys/starfilm.p12"
      ALIAS="${KEY_ALIAS:-starfilm}"
      PASS="${KEY_PASS:-starfilm123}"
    else
      mkdir -p "$ROOT/keys"
      KS="$ROOT/keys/debug.keystore"
      ALIAS="androiddebugkey"
      PASS="android"
      if [ ! -f "$KS" ]; then
        echo "[sign] 生成 debug 密钥 $KS"
        keytool -genkeypair -v -keystore "$KS" -storetype JKS \
          -alias "$ALIAS" -keyalg RSA -keysize 2048 -validity 10000 \
          -storepass "$PASS" -keypass "$PASS" \
          -dname "CN=Xinghai Debug,O=Xinghai,C=CN"
      fi
    fi
  fi
  echo "[sign] keystore=$KS alias=$ALIAS"
  "$BT/apksigner" sign --ks "$KS" --ks-key-alias "$ALIAS" \
    --ks-pass "pass:$PASS" --key-pass "pass:$PASS" \
    --v1-signing-enabled true --v2-signing-enabled true \
    --out "$OUT/xinghai-signed.apk" "$OUT/xinghai-aligned.apk"
  # 清理中间包，避免误装
  rm -f "$OUT/base.apk" "$OUT/xinghai-unsigned.apk" "$OUT/xinghai-aligned.apk"
  echo "完成: $OUT/xinghai-signed.apk（可安装）"
  ls -la "$OUT"/*.apk
  exit 0
fi

echo "完成: $OUT/xinghai-aligned.apk（未签名，现代 Android 无法直接安装）"
ls -la "$OUT"/*.apk
