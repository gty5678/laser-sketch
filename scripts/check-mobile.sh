#!/bin/sh
# Verify shared behavior and both installable development targets from one checkout on macOS.
set -eu
cd "$(dirname "$0")/.."
if [ -z "${JAVA_HOME:-}" ] && [ -d '/Applications/Android Studio.app/Contents/jbr/Contents/Home' ]; then
    export JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home'
fi
./gradlew :shared:checkSharedBoundaries :shared-ui:checkSharedUiBoundaries \
    :shared:jvmTest :shared-ui:jvmTest :app:testDebugUnitTest :app:assembleDebug \
    :shared:iosSimulatorArm64Test :shared-ui:iosSimulatorArm64Test --console=plain
xcodebuild -project iosApp/LaserSketch.xcodeproj -scheme LaserSketch -configuration Debug \
    -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' \
    -derivedDataPath /tmp/laser-sketch-ios-build CODE_SIGNING_ALLOWED=NO build
xcodebuild -project iosApp/LaserSketch.xcodeproj -scheme LaserSketch -configuration Debug \
    -sdk iphoneos -destination 'generic/platform=iOS' \
    -derivedDataPath /tmp/laser-sketch-ios-device-build CODE_SIGNING_ALLOWED=NO build
if [ "${LASERSKETCH_UI_TESTS:-0}" = 1 ]; then ./scripts/test-ios.sh; fi
if [ "${LASERSKETCH_ANDROID_DEVICE_TESTS:-0}" = 1 ]; then
    # Gradle requires ANDROID_SERIAL to select a disposable emulator.
    ./gradlew :app:verifyInstrumentationDevice :app:connectedDebugAndroidTest --console=plain
fi
