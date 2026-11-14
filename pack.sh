#!/bin/bash
cd /home/memobox/nightbell_build
BT=/usr/lib/android-sdk/build-tools/29.0.3
AJ=/usr/lib/android-sdk/platforms/android-36/android.jar
AJR=/usr/lib/android-sdk/platforms/android-23/android.jar
KS=/home/memobox/apk_build/memobox.keystore
OUT=${1:-1.0.0}
CODE=$(echo "$OUT" | awk -F. '{printf "%d%02d%02d", $1, $2, $3}')
sed -i 's|android:versionCode="[0-9]*" android:versionName="[^"]*"|android:versionCode="'"$CODE"'" android:versionName="'"$OUT"'"|' AndroidManifest.xml
rm -rf obj classes.dex unsigned.apk aligned.apk 夜铃.apk
mkdir -p obj
echo "== javac =="
javac -encoding UTF-8 -source 8 -target 8 -cp /home/memobox/shizuku/api/classes.jar:/home/memobox/shizuku/prov/classes.jar:/home/memobox/shizuku/aidl/classes.jar:/home/memobox/shizuku/shared/classes.jar:/home/memobox/sherpa/x/classes.jar:/home/memobox/sherpa/kotlin-stdlib.jar -bootclasspath "$AJ" -d obj $(find src -name "*.java") || exit 1
echo "== dx =="
rm -rf /tmp/dxout /tmp/obj.jar && mkdir -p /tmp/dxout
(cd obj && zip -q -r /tmp/obj.jar .) || exit 1
/tmp/bt34/android-14/d8 --min-api 26 --lib "$AJ" --output /tmp/dxout /tmp/obj.jar /home/memobox/shizuku/api/classes.jar /home/memobox/shizuku/prov/classes.jar /home/memobox/shizuku/aidl/classes.jar /home/memobox/shizuku/shared/classes.jar /home/memobox/sherpa/x/classes.jar /home/memobox/sherpa/kotlin-stdlib.jar || exit 1
cp -f /tmp/dxout/classes.dex classes.dex || exit 1
echo "== aapt =="
"$BT/aapt" package -f -M AndroidManifest.xml -S res -A assets -I "$AJR" -F unsigned.apk --min-sdk-version 26 --target-sdk-version 33 || exit 1
echo "== zip dex =="
zip -u unsigned.apk classes.dex
mkdir -p lib/arm64-v8a
rm -rf /tmp/sherpa_jni && mkdir -p /tmp/sherpa_jni
unzip -o -q /home/memobox/sherpa/sherpa.aar 'jni/arm64-v8a/*' -d /tmp/sherpa_jni
cp -f /tmp/sherpa_jni/jni/arm64-v8a/*.so lib/arm64-v8a/
zip -u unsigned.apk lib/arm64-v8a/libonnxruntime.so lib/arm64-v8a/libsherpa-onnx-c-api.so lib/arm64-v8a/libsherpa-onnx-cxx-api.so lib/arm64-v8a/libsherpa-onnx-jni.so
echo "== model assets =="
rm -rf /tmp/nbmodel
mkdir -p /tmp/nbmodel/assets/models/stt-sense
cp -f /sdcard/Download/Operit/app_data/nightbell/stt-sense/model.int8.onnx /tmp/nbmodel/assets/models/stt-sense/ || exit 1
cp -f /sdcard/Download/Operit/app_data/nightbell/stt-sense/tokens.txt /tmp/nbmodel/assets/models/stt-sense/ || exit 1
(cd /tmp/nbmodel && zip -q -0 -u /home/memobox/nightbell_build/unsigned.apk assets/models/stt-sense/model.int8.onnx assets/models/stt-sense/tokens.txt) || exit 1
rm -rf /tmp/nbmodel
echo "== align =="
"$BT/zipalign" -p -f 4 unsigned.apk aligned.apk || exit 1
echo "== sign =="
"$BT/apksigner" sign --ks "$KS" --ks-pass pass:memobox123 --ks-key-alias memobox --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true --out "夜铃_$OUT.apk" aligned.apk || exit 1
cp "夜铃_$OUT.apk" /sdcard/Download/夜铃.apk
cp "夜铃_$OUT.apk" /sdcard/Documents/夜铃.apk 2>/dev/null
ls -la "夜铃_$OUT.apk" /sdcard/Download/夜铃.apk
echo PACK_DONE