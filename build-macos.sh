#!/bin/bash
set -euo pipefail

cd -- "$(dirname -- "${BASH_SOURCE[0]}")"

studio_jdk="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
if [[ -z "${JAVA_HOME:-}" ]]; then
    if [[ -x "$studio_jdk/bin/java" ]]; then
        JAVA_HOME="$studio_jdk"
    elif ! JAVA_HOME="$(/usr/libexec/java_home -F -v 25 2>/dev/null)" &&
         ! JAVA_HOME="$(/usr/libexec/java_home -F -v '17+' 2>/dev/null)"; then
        echo "找不到 JDK。请安装 Android Studio，或将 JAVA_HOME 指向 JDK 17 或更高版本。" >&2
        exit 1
    fi
fi
if [[ ! -x "$JAVA_HOME/bin/java" ]]; then
    echo "JAVA_HOME 下找不到可执行的 bin/java，请检查 JDK 路径：$JAVA_HOME" >&2
    exit 1
fi
export JAVA_HOME

default_sdk="$HOME/Library/Android/sdk"
if [[ -z "${ANDROID_HOME:-}" && -z "${ANDROID_SDK_ROOT:-}" && -d "$default_sdk" ]]; then
    export ANDROID_HOME="$default_sdk"
fi

if [[ $# -eq 0 ]]; then
    set -- :app:assembleDebug
fi

exec ./gradlew "$@"
