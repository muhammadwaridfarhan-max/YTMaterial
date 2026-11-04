#!/usr/bin/env bash
# fix_build.sh - terapkan 3 perbaikan error build #259. Jalankan dari folder repo YTMaterial.
set -euo pipefail

SRC="app/src/main/java/com/example/ytclient"
RES="app/src/main/res"

[ -d "$SRC" ] || { echo "Jalankan dari folder root repo YTMaterial." >&2; exit 1; }

# 1) string open_channel (EN + ID)
add_string() {
  local file="$1" text="$2"
  if grep -q 'name="open_channel"' "$file"; then
    echo "skip: open_channel sudah ada di $file"
  else
    sed -i "s|</resources>|    <string name=\"open_channel\">$text</string>\n</resources>|" "$file"
    echo "ok:   open_channel ditambah di $file"
  fi
}
add_string "$RES/values/strings.xml" "Go to channel"
add_string "$RES/values-in/strings.xml" "Buka channel"

# 2) import CompactDecimalFormat yang benar
CH="$SRC/ui/ChannelScreen.kt"
if grep -q '^import java.text.CompactDecimalFormat' "$CH"; then
  sed -i 's|^import java.text.CompactDecimalFormat|import android.icu.text.CompactDecimalFormat|' "$CH"
  echo "ok:   import CompactDecimalFormat diperbaiki"
else
  echo "skip: import CompactDecimalFormat sudah benar"
fi

# 3) arrayOf campuran String/Int -> arrayOf<Any>
YT="$SRC/data/YtAccountApi.kt"
if grep -q 'arrayOf(' "$YT"; then
  sed -i 's|arrayOf(|arrayOf<Any>(|g' "$YT"
  echo "ok:   arrayOf<Any> diterapkan di YtAccountApi.kt"
else
  echo "skip: arrayOf sudah bertipe"
fi

echo
git diff --stat
echo
echo 'Lanjut: ./gitflow.sh all "fix: perbaiki error build"'
