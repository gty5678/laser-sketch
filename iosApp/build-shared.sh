#!/bin/sh
set -eu
if [ "${OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED:-}" = "YES" ]; then
    exit 0
fi
if [ -z "${JAVA_HOME:-}" ]; then
    if JAVA_HOME=$(/usr/libexec/java_home -v 17+ 2>/dev/null); then
        export JAVA_HOME
    elif [ -d "/Applications/Android Studio.app/Contents/jbr/Contents/Home" ]; then
        export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
    else
        echo "A JDK 17+ is required. Set JAVA_HOME in the Xcode scheme." >&2
        exit 1
    fi
fi
cd "${SRCROOT}/.."
exec ./gradlew :shared-ui:embedAndSignAppleFrameworkForXcode --console=plain
