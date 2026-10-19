#!/bin/bash
# 给 tvhome-zh 换壁纸：把一张图片推送到电视，重启桌面即可生效。
#
# 用法：
#   ./set-wallpaper.sh <电视IP> <图片路径>
# 例：
#   ./set-wallpaper.sh 192.168.1.6 ~/Pictures/my.jpg
#
# 壁纸文件会放到电视上：/sdcard/Android/data/com.gathin.tvhome/files/wallpaper.jpg
# 桌面启动时自动读取，显示为背景；换成纯黑色背景请删掉该文件（见文末说明）。

set -euo pipefail

TV_HOST="${1:-}"
IMG="${2:-}"
[ -n "$TV_HOST" ] || { echo "用法: $0 <电视IP> <图片路径>"; exit 1; }
[ -n "$IMG" ] || { echo "用法: $0 <电视IP> <图片路径>"; exit 1; }
[ -f "$IMG" ] || { echo "找不到图片: $IMG"; exit 1; }

ADB=adb
command -v adb >/dev/null 2>&1 || ADB=/tmp/pt/platform-tools/adb
DIR="/sdcard/Android/data/com.gathin.tvhome/files"

# 图片格式：Android 的 BitmapFactory 直接吃 JPEG/PNG/WebP，扩展名要对应
case "${IMG,,}" in
  *.jpg|*.jpeg) EXT=jpg ;;
  *.png)        EXT=png ;;
  *.webp)       EXT=webp ;;
  *) echo "只支持 jpg / png / webp（你的是: $IMG）"; exit 1 ;;
esac

echo "==> 连接 $TV_HOST"
$ADB connect "$TV_HOST:5555" >/dev/null

echo "==> 推送壁纸 ($(du -h "$IMG" | cut -f1))"
$ADB -shell "mkdir -p $DIR" >/dev/null
$ADB push "$IMG" "$DIR/wallpaper.$EXT" >/dev/null

# 换格式时清掉旧文件，避免残留的另一张仍被读取
$ADB shell "cd $DIR; for f in wallpaper.jpg wallpaper.png wallpaper.webp; do [ \$f = wallpaper.$EXT ] || rm -f \$f; done" >/dev/null

echo "==> 重启桌面使壁纸生效"
$ADB shell "am force-stop com.gathin.tvhome; am start com.gathin.tvhome/.MainActivity" >/dev/null

echo
echo "✓ 完成。按 Home 键回桌面即可看到新壁纸。"
echo
echo "  想换回来（恢复纯黑背景）："
echo "    adb -s $TV_HOST:5555 shell rm -f $DIR/wallpaper.jpg \\"
echo "    adb -s $TV_HOST:5555 shell am force-stop com.gathin.tvhome"
echo "    adb -s $TV_HOST:5555 shell am start com.gathin.tvhome/.MainActivity"