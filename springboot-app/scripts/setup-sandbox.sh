#!/usr/bin/env bash
set -Eeuo pipefail

if [[ "$(id -u)" -eq 0 ]]; then
  SUDO=()
else
  SUDO=(sudo)
fi

export DEBIAN_FRONTEND=noninteractive
"${SUDO[@]}" apt-get update
"${SUDO[@]}" apt-get install -y curl ca-certificates tar gzip maven mariadb-server mariadb-client

if [[ ! -x /opt/jdk17/bin/java ]]; then
  archive="$(mktemp --suffix=.tar.gz)"
  trap 'rm -f "$archive"' EXIT
  curl -fL --retry 3 \
    'https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse' \
    -o "$archive"
  "${SUDO[@]}" mkdir -p /opt/jdk17
  "${SUDO[@]}" tar -xzf "$archive" -C /opt/jdk17 --strip-components=1
fi

"${SUDO[@]}" update-alternatives --install /usr/bin/java java /opt/jdk17/bin/java 1710
"${SUDO[@]}" update-alternatives --install /usr/bin/javac javac /opt/jdk17/bin/javac 1710
"${SUDO[@]}" update-alternatives --set java /opt/jdk17/bin/java
"${SUDO[@]}" update-alternatives --set javac /opt/jdk17/bin/javac
printf 'export JAVA_HOME=/opt/jdk17\nexport PATH="$JAVA_HOME/bin:$PATH"\n' \
  | "${SUDO[@]}" tee /etc/profile.d/java17.sh >/dev/null
"${SUDO[@]}" chmod 644 /etc/profile.d/java17.sh
"${SUDO[@]}" service mariadb start

java -version
mvn -version | head -n 4
mariadb --version
"${SUDO[@]}" service mariadb status | head -n 4
