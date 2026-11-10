#!/usr/bin/env bash
# Installs JDK 17 and the Android SDK packages this project needs, without root.
# Idempotent: re-running it only installs what is missing.
#
#   scripts/setup-toolchain.sh            # install, then print the exports
#   eval "$(scripts/setup-toolchain.sh --print-env)"   # only the exports
#
# Location: ${ANDROID_TOOLCHAIN_DIR:-$HOME/.cache/android-toolchain}
set -euo pipefail

DIR="${ANDROID_TOOLCHAIN_DIR:-$HOME/.cache/android-toolchain}"
JDK_VERSION="17.0.13+11"
JDK_FILE="OpenJDK17U-jdk_x64_linux_hotspot_17.0.13_11.tar.gz"
JDK_URL="https://github.com/adoptium/temurin17-binaries/releases/download/jdk-${JDK_VERSION/+/%2B}/${JDK_FILE}"
JDK_SHA256="8682892fc02965930b9022c066fa164dd6f458ef4a5dc262016aa28333b30f49"
CMDLINE_FILE="commandlinetools-linux-11076708_latest.zip"
CMDLINE_URL="https://dl.google.com/android/repository/${CMDLINE_FILE}"
CMDLINE_SHA256="2d2d50857e4eb553af5a6dc3ad507a17adf43d115264b1afc116f95c92e5e258"
SDK_PACKAGES=("platforms;android-35" "build-tools;35.0.0" "platform-tools")

JAVA_HOME_DIR="$DIR/jdk-17"
SDK_DIR="$DIR/sdk"

print_env() {
  echo "export JAVA_HOME=\"$JAVA_HOME_DIR\""
  echo "export ANDROID_HOME=\"$SDK_DIR\""
  echo "export PATH=\"$JAVA_HOME_DIR/bin:$SDK_DIR/cmdline-tools/latest/bin:\$PATH\""
}

if [[ "${1:-}" == "--print-env" ]]; then
  print_env
  exit 0
fi

if [[ "$(uname -s)" != "Linux" || "$(uname -m)" != "x86_64" ]]; then
  echo "This script supports Linux x86_64 only." >&2
  exit 1
fi

mkdir -p "$DIR/downloads"

download() { # url file sha256
  local url="$1" out="$DIR/downloads/$2" sum="$3"
  if [[ ! -f "$out" ]] || ! echo "$sum  $out" | sha256sum -c --status; then
    echo "Downloading $2" >&2
    curl -fsSL --retry 3 -o "$out.part" "$url"
    echo "$sum  $out.part" | sha256sum -c --quiet
    mv "$out.part" "$out"
  fi
}

if [[ ! -x "$JAVA_HOME_DIR/bin/java" ]]; then
  download "$JDK_URL" "$JDK_FILE" "$JDK_SHA256"
  rm -rf "$JAVA_HOME_DIR" "$DIR/jdk.tmp"
  mkdir -p "$DIR/jdk.tmp"
  tar -xzf "$DIR/downloads/$JDK_FILE" -C "$DIR/jdk.tmp" --strip-components=1
  mv "$DIR/jdk.tmp" "$JAVA_HOME_DIR"
fi

if [[ ! -x "$SDK_DIR/cmdline-tools/latest/bin/sdkmanager" ]]; then
  download "$CMDLINE_URL" "$CMDLINE_FILE" "$CMDLINE_SHA256"
  rm -rf "$SDK_DIR/cmdline-tools/latest" "$DIR/cmdline.tmp"
  mkdir -p "$DIR/cmdline.tmp" "$SDK_DIR/cmdline-tools"
  unzip -q "$DIR/downloads/$CMDLINE_FILE" -d "$DIR/cmdline.tmp"
  mv "$DIR/cmdline.tmp/cmdline-tools" "$SDK_DIR/cmdline-tools/latest"
  rm -rf "$DIR/cmdline.tmp"
fi

export JAVA_HOME="$JAVA_HOME_DIR"
export ANDROID_HOME="$SDK_DIR"
SDKMANAGER="$SDK_DIR/cmdline-tools/latest/bin/sdkmanager"

missing=()
for pkg in "${SDK_PACKAGES[@]}"; do
  [[ -d "$SDK_DIR/${pkg//;//}" ]] || missing+=("$pkg")
done
if (( ${#missing[@]} )); then
  yes | "$SDKMANAGER" --sdk_root="$SDK_DIR" --licenses >/dev/null || true
  "$SDKMANAGER" --sdk_root="$SDK_DIR" "${missing[@]}" >/dev/null
fi

echo "Toolchain ready in $DIR. Add to your shell:" >&2
print_env
