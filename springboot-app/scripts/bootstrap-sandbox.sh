#!/usr/bin/env bash
set -euo pipefail

APP_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAVA_HOME_PATH="/opt/jdk17"
TMP_ARCHIVE="$(mktemp /tmp/temurin17.XXXXXX.tar.gz)"
trap 'rm -f "$TMP_ARCHIVE"' EXIT

sudo apt-get update
sudo DEBIAN_FRONTEND=noninteractive apt-get install -y \
  ca-certificates curl gzip maven mariadb-client mariadb-server openssl tar

if [[ ! -x "$JAVA_HOME_PATH/bin/java" ]] || ! "$JAVA_HOME_PATH/bin/java" -version 2>&1 | grep -q 'version "17\.'; then
  sudo mkdir -p "$JAVA_HOME_PATH"
  curl -fL --retry 3 \
    'https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse' \
    -o "$TMP_ARCHIVE"
  sudo tar -xzf "$TMP_ARCHIVE" -C "$JAVA_HOME_PATH" --strip-components=1
fi

sudo update-alternatives --install /usr/bin/java java "$JAVA_HOME_PATH/bin/java" 1710
sudo update-alternatives --install /usr/bin/javac javac "$JAVA_HOME_PATH/bin/javac" 1710
sudo update-alternatives --set java "$JAVA_HOME_PATH/bin/java"
sudo update-alternatives --set javac "$JAVA_HOME_PATH/bin/javac"
printf 'export JAVA_HOME=%s\nexport PATH="$JAVA_HOME/bin:$PATH"\n' "$JAVA_HOME_PATH" \
  | sudo tee /etc/profile.d/java17.sh >/dev/null
sudo chmod 644 /etc/profile.d/java17.sh
sudo service mariadb start

export JAVA_HOME="$JAVA_HOME_PATH"
export PATH="$JAVA_HOME/bin:$PATH"
printf '\n--- Installed toolchain ---\n'
java -version
mvn -version | head -n 4
mariadb --version
sudo service mariadb status | head -n 4
printf '\nSandbox toolchain ready for %s\n' "$APP_DIR"
