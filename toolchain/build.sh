#!/bin/sh
# build.sh — Alpine aarch64 上的 APK 手动构建流水线
# 用法: build.sh <app目录>
# app目录需要: src/ (Java源码, 无资源文件方案), app.properties
set -e

APP_DIR="$1"
[ -z "$APP_DIR" ] && { echo "用法: $0 <app目录>"; exit 1; }
cd "$APP_DIR"

# 加载应用配置
. ./app.properties

# 工具路径
JAVA_HOME=/usr/lib/jvm/java-17-openjdk
export PATH="$JAVA_HOME/bin:$PATH"
ANDROID_JAR=/opt/android-sdk/platforms/android-35/android.jar
D8_JAR=/opt/android-sdk/build-tools/35.0.0/lib/d8.jar
APKSIGNER_JAR=/opt/android-sdk/build-tools/35.0.0/lib/apksigner.jar
MINI_AAPT=/workspace/tools
KEYSTORE=${KEYSTORE:-/root/.android/debug.keystore}
KS_PASS=android
KS_ALIAS=androiddebugkey

BUILD=build
rm -rf "$BUILD"
mkdir -p "$BUILD/classes" "$BUILD/dex" "$BUILD/apk"

echo "[1/5] javac 编译 Java 源码"
find src -name '*.java' > "$BUILD/sources.txt"
javac --release 8 -encoding UTF-8 -classpath "$ANDROID_JAR" \
      -d "$BUILD/classes" @"$BUILD/sources.txt" 2>&1 | grep -viE "warning|obsolete" || true
echo "      -> $(find "$BUILD/classes" -name '*.class' | wc -l) 个 class 文件"

echo "[2/5] d8 转 dex"
CLASSES=$(find "$BUILD/classes" -name '*.class' | tr '\n' ' ')
java -cp "$D8_JAR" com.android.tools.r8.D8 --release --min-api "$MIN_SDK" \
     --lib "$ANDROID_JAR" --output "$BUILD/dex" $CLASSES
mv "$BUILD/dex/classes.dex" "$BUILD/apk/classes.dex"

echo "[3/5] MiniAapt 生成二进制 AndroidManifest.xml"
java -cp "$MINI_AAPT" MiniAapt \
     --package "$PKG" --label "$LABEL" --activity "$ACTIVITY" \
     --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" \
     --min-sdk "$MIN_SDK" --target-sdk "$TARGET_SDK" \
     --out "$BUILD/apk/AndroidManifest.xml"

echo "[4/5] 打包 + 签名"
(cd "$BUILD/apk" && zip -q -X ../unsigned.apk AndroidManifest.xml classes.dex)
java -jar "$APKSIGNER_JAR" sign \
     --ks "$KEYSTORE" --ks-pass "pass:$KS_PASS" --ks-key-alias "$KS_ALIAS" \
     --key-pass "pass:$KS_PASS" \
     --out "$APP_NAME.apk" "$BUILD/unsigned.apk"

echo "[5/5] 验证签名"
java -jar "$APKSIGNER_JAR" verify --print-certs "$APP_NAME.apk" | head -6
ls -la "$APP_NAME.apk"
echo ""
echo "完成: $APP_DIR/$APP_NAME.apk"
