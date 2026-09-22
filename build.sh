#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")"
if [ -n "${JAVA_HOME:-}" ]; then PATH="$JAVA_HOME/bin:$PATH"; export PATH; fi
mkdir -p build/classes
javac --release 17 -encoding UTF-8 -d build/classes src/nids/*.java
jar --create --file build/nids-demo.jar --main-class nids.Main -C build/classes .
echo 'Built build/nids-demo.jar'
