#!/usr/bin/env bash
# 发布前验证：签名 / 版本 / 权限 / 零 INTERNET / 哈希留档
# 用法: bash scripts/verify-release.sh [apk路径]
set -u
APK="${1:-app/build/outputs/apk/release/app-release.apk}"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -z "$SDK" ]; then echo "请设置 ANDROID_HOME"; exit 1; fi
BT=$(ls -d "$SDK"/build-tools/* 2>/dev/null | sort -V | tail -1)
if [ -z "$BT" ]; then echo "未找到 build-tools"; exit 1; fi
if [ ! -f "$APK" ]; then echo "APK 不存在: $APK"; exit 1; fi

echo "== 1. 签名 =="
"$BT/apksigner" verify --verbose --print-certs "$APK" || exit 1

echo "== 2. Badging =="
"$BT/aapt" dump badging "$APK" | head -8

echo "== 3. 权限清单 =="
PERMS=$("$BT/aapt" dump permissions "$APK")
echo "$PERMS"
if echo "$PERMS" | grep -qi "android.permission.INTERNET"; then
  echo "FAIL: APK 含 INTERNET 权限，违反零网络权限约束"; exit 1
fi

echo "== 4. 合并 Manifest 复核 =="
MANIFEST=$(find app/build/intermediates -path '*merged_manifests*release*AndroidManifest.xml' 2>/dev/null | head -1)
if [ -n "$MANIFEST" ]; then
  if grep -qi 'android.permission.INTERNET' "$MANIFEST"; then
    echo "FAIL: 合并 Manifest 含 INTERNET 权限"; exit 1
  fi
  echo "OK: $MANIFEST 无 INTERNET"
else
  echo "WARN: 未找到合并 Manifest，请先执行 assembleRelease"
fi

echo "== 5. 哈希留档 =="
sha256sum "$APK" | tee release-sha256.txt
echo "== 全部通过 =="
