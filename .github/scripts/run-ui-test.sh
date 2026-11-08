#!/bin/bash
# Instala o app e o teste, roda o teste e copia as fotos da tela para ui-results/
set -u
mkdir -p ui-results
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r com.xadrezrpg.app.test/androidx.test.runner.AndroidJUnitRunner | tee ui-results/resultado-teste.txt
adb pull /sdcard/Android/data/com.xadrezrpg.app/files/shots ui-results/ || true
mv ui-results/shots/* ui-results/ 2>/dev/null || true
rmdir ui-results/shots 2>/dev/null || true
adb logcat -d -t 400 > ui-results/logcat.txt || true
ls -la ui-results
if grep -q "FAILURES!!!\|INSTRUMENTATION_FAILED\|Process crashed" ui-results/resultado-teste.txt; then exit 1; fi
grep -q "OK (" ui-results/resultado-teste.txt
