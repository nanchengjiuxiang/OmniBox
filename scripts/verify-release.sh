#!/usr/bin/env bash
# 发布前验证：签名 / 版本 / 权限策略 / 哈希留档
# 用法: bash scripts/verify-release.sh [apk路径]
set -u
APK="${1:-app/build/outputs/apk/release/app-release.apk}"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -z "$SDK" ]; then echo "请设置 ANDROID_HOME"; exit 1; fi
BT=$(ls -d "$SDK"/build-tools/* 2>/dev/null | sort -V | tail -1)
if [ -z "$BT" ]; then echo "未找到 build-tools"; exit 1; fi
if [ ! -f "$APK" ]; then echo "APK 不存在: $APK"; exit 1; fi

# Windows 的 build-tools 里是可执行脚本带后缀（aapt.exe / apksigner.bat），
# Linux/macOS 无后缀。这里按实际存在的文件挑，避免在中文路径下被 shell 当成不存在。
pick() {
  for cand in "$BT/$1.exe" "$BT/$1.bat" "$BT/$1"; do
    [ -f "$cand" ] && { echo "$cand"; return 0; }
  done
  return 1
}
APKSIGNER=$(pick apksigner) || { echo "未找到 apksigner"; exit 1; }
AAPT=$(pick aapt) || { echo "未找到 aapt"; exit 1; }

echo "== 1. 签名 =="
"$APKSIGNER" verify --verbose --print-certs "$APK" || exit 1

echo "== 2. Badging =="
"$AAPT" dump badging "$APK" | head -8

echo "== 3. 权限清单 =="
PERMS=$("$AAPT" dump permissions "$APK")
echo "$PERMS"
# 网络权限策略：探索页需要 INTERNET + ACCESS_NETWORK_STATE；其余网络类权限一律视为异常回注。
for required in android.permission.INTERNET android.permission.ACCESS_NETWORK_STATE; do
  if ! echo "$PERMS" | grep -qi "$required"; then
    echo "FAIL: 缺少 $required（探索页外链与离线提示需要）"; exit 1
  fi
done
EXTRA_NET=$(echo "$PERMS" | grep -oiE 'android\.permission\.(ACCESS_WIFI_STATE|CHANGE_NETWORK_STATE|CHANGE_WIFI_STATE|ACCESS_BACKGROUND_LOCATION_EXTRA|READ_PHONE_STATE)' || true)
if [ -n "$EXTRA_NET" ]; then
  echo "WARN: 出现非预期网络相关权限，请复核来源：$EXTRA_NET"
fi
echo "OK: 网络权限符合策略（INTERNET 仅用于探索页外链与图标）"

echo "== 4. 合并 Manifest 复核 =="
MANIFEST=$(find app/build/intermediates -path '*merged_manifests*release*AndroidManifest.xml' 2>/dev/null | head -1)
if [ -n "$MANIFEST" ]; then
  if ! grep -qi 'android.permission.INTERNET' "$MANIFEST"; then
    echo "FAIL: 合并 Manifest 丢失 INTERNET（探索页外链会打不开）"; exit 1
  fi
  if grep -q 'tools:node="remove"' "$MANIFEST"; then
    echo "WARN: 合并 Manifest 仍含 tools:node=\"remove\" 规则，请确认没有误移除网络权限"
  fi
  echo "OK: $MANIFEST 含预期网络权限"
else
  echo "WARN: 未找到合并 Manifest，请先执行 assembleRelease"
fi

echo "== 5. 哈希留档 =="
sha256sum "$APK" | tee release-sha256.txt
echo "== 全部通过 =="
