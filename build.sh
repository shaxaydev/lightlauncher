#!/bin/sh
set -eu
cd "$(dirname "$0")"
if [ -z "${JAVA_HOME:-}" ] && [ -d '/Applications/Android Studio.app/Contents/jbr/Contents/Home' ]; then
    export JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home'
fi
if [ -z "${ANDROID_HOME:-}" ] && [ -d "$HOME/Library/Android/sdk" ]; then
    export ANDROID_HOME="$HOME/Library/Android/sdk"
fi
mkdir -p .signing
chmod 700 .signing
if [ ! -f .signing/password ]; then
    python3 - <<'PY'
from pathlib import Path
import secrets
p = Path('.signing/password')
p.write_text(secrets.token_urlsafe(32))
p.chmod(0o600)
PY
fi
if [ ! -f .signing/doma.jks ]; then
    "${JAVA_HOME:?Set JAVA_HOME to a JDK 17 or newer}/bin/keytool" -genkeypair \
        -keystore .signing/doma.jks -storepass:file .signing/password \
        -keypass:file .signing/password -alias doma -keyalg RSA -keysize 3072 \
        -validity 10000 -dname 'CN=LightLauncher'
fi
chmod 600 .signing/doma.jks
./gradlew :app:assembleRelease "$@"
