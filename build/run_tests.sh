#!/usr/bin/env bash
# 在 JVM 上跑解析层 / 去广告 / 字幕的真实数据自测
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SDK="${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}"
ANDROID_JAR="$(ls -d "$SDK"/platforms/*/android.jar 2>/dev/null | sort -V | tail -1)"
JAVAC="javac"; JAVA="java"
if [ -n "$JDK_HOME" ]; then
  export PATH="$JDK_HOME/bin:$PATH"; JAVAC="$JDK_HOME/bin/javac"; JAVA="$JDK_HOME/bin/java"
fi
OUT="$ROOT/build/testout"
rm -rf "$OUT"; mkdir -p "$OUT"

GEN="$ROOT/build/gen"
if [ ! -f "$GEN/com/zaka/xinghai/R.java" ]; then
  echo "先生成一次资源（bash build/build.sh）再跑测试：R.java 在 $GEN"
  exit 1
fi
find "$ROOT/app/src/main/java" "$ROOT/test" "$GEN" -name '*.java' > "$OUT/sources.txt"
"$JAVAC" -encoding UTF-8 -nowarn -source 8 -target 8 \
  -cp "$ANDROID_JAR:$ROOT/build/libs/json.jar" -d "$OUT" @"$OUT/sources.txt"

cd "$ROOT"
"$JAVA" -cp "$OUT:$ROOT/build/libs/json.jar:$ANDROID_JAR" JvmCheck "$ROOT/build/fixtures"
