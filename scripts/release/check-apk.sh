#!/usr/bin/env bash
# Checks a built APK before it is tested or published:
#   - the licence notices survived resource shrinking;
#   - the manifest requests no platform permission and keeps backup off;
#   - with --signed, the APK verifies and carries no AGP dependency-metadata block (F-Droid rejects it).
#
#   scripts/release/check-apk.sh app/build/outputs/apk/release/app-release.apk [--signed]
set -euo pipefail
apk="$1"
signed="${2:-}"
tools="$ANDROID_HOME/build-tools/35.0.0"

for notice in cc-by-4.0.txt openmoji.txt piper-cori.txt; do
  unzip -l "$apk" "assets/licences/$notice" > /dev/null || { echo "missing assets/licences/$notice" >&2; exit 1; }
done
echo "licence notices: present"

manifest="$("$tools/aapt2" dump xmltree --file AndroidManifest.xml "$apk")"
"$tools/aapt2" dump badging "$apk" | sed -n 1p
if grep -E 'E: uses-permission' -A1 <<<"$manifest" | grep -o '"android.permission[^"]*"'; then
  echo "the APK requests a platform permission" >&2; exit 1
fi
grep -q 'allowBackup.*=false' <<<"$manifest" || { echo "allowBackup is not false" >&2; exit 1; }
if grep -q 'androidx.emoji2' <<<"$manifest"; then echo "the emoji2 initialiser is in the manifest" >&2; exit 1; fi
echo "manifest: no platform permission, backup off, no emoji2 initialiser"

if [[ "$signed" == "--signed" ]]; then
  "$tools/apksigner" verify --verbose "$apk" | grep -E '^Verified using|^Verifies'
  # AGP's dependency list sits in the APK signing block under ID 0x504b4453 ("DSKP").
  if python3 - "$apk" <<'PY'
import struct, sys
data = open(sys.argv[1], "rb").read()
eocd = data.rfind(b"PK\x05\x06")
cd_offset = struct.unpack("<I", data[eocd + 16:eocd + 20])[0]
if data[cd_offset - 16:cd_offset] != b"APK Sig Block 42":
    sys.exit(1)
size = struct.unpack("<Q", data[cd_offset - 24:cd_offset - 16])[0]
block = data[cd_offset - size - 8:cd_offset - 24]
position = 8
while position < len(block):
    length, block_id = struct.unpack("<QI", block[position:position + 12])
    if block_id == 0x504B4453:
        sys.exit(0)
    position += 8 + length
sys.exit(1)
PY
  then echo "the APK carries a dependency-metadata block" >&2; exit 1; fi
  echo "signature: verified, no dependency-metadata block"
fi
