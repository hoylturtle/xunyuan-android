#!/bin/bash
# Run locally on a Mac signed into iCloud. No credentials are collected.
set -euo pipefail
cd "$(dirname "$0")"
if [ "$(uname -s)" != Darwin ]; then
  echo '请在已登录 iCloud 的 Mac 上运行此脚本。' >&2
  exit 1
fi
command -v python3 >/dev/null || { echo '需要 Python 3。' >&2; exit 1; }
command -v shortcuts >/dev/null || { echo '找不到系统快捷指令命令。' >&2; exit 1; }
python3 test_shortcut.py
python3 test_pagination.py
staging=$(mktemp -d "${TMPDIR:-/tmp}/xunyuan-sign.XXXXXX")
trap 'rm -rf "$staging"' EXIT
shortcuts sign --mode anyone --input dist/Xunyuan.unsigned.shortcut --output "$staging/寻源.shortcut"
shortcuts sign --mode anyone --input dist/Xunyuan-Plus.unsigned.shortcut --output "$staging/寻源 Plus.shortcut"
python3 - "$staging" <<'PY'
import pathlib,sys
root=pathlib.Path(sys.argv[1])
for name in ['寻源','寻源 Plus']:
    p=root/(name+'.shortcut')
    if p.read_bytes()[:4]!=b'AEA1':raise SystemExit('签名输出格式异常：'+name)
PY
cp "$staging/寻源.shortcut" dist/寻源.shortcut
cp "$staging/寻源 Plus.shortcut" 'dist/寻源 Plus.shortcut'
printf '苹果官方签名命令已完成。文件位于：\n%s/dist/寻源.shortcut\n%s/dist/寻源 Plus.shortcut\n请在 iPhone 导入并测试。\n' "$PWD" "$PWD"
