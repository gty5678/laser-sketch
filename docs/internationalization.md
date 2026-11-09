# 国际化与语言切换

Android 和 iOS 支持简体中文（`zh-Hans`）、繁体中文（`zh-Hant`）、日语（`ja`）、韩语（`ko`）、英语（`en`）。首次运行跟随系统；不支持的系统语言回退到英语。首页「设置 → 语言」可以选择语言或恢复跟随系统。选项使用语言自己的名称，方便切换回来。

- Android 使用系统资源与 AppCompat 应用语言 API。Android 13+ 由系统保存，Android 12/12L 由 AppCompat 自动保存；切换时 Activity 可能重建。系统应用语言设置也可使用这五种语言。
- iOS 使用各语言 `.lproj/Localizable.strings`，应用选择保存在 `NSUserDefaults`，通过 Compose 状态立即更新界面，不重新创建业务服务。系统照片选择器、分享面板和权限弹窗仍由 iOS 的系统/应用语言设置决定；权限说明的五种翻译在 `InfoPlist.strings` 中。
- 共享界面继续通过宿主注入 Labels；共享核心不引入平台资源依赖。工程文件格式不变，已保存的项目名称、用户文字和测量数据不随语言切换重写。语言选择不改变测量单位和数值解析规则。

## 新增和修改文案

Android 默认 `app/src/main/res/values/strings.xml` 是完整的英文回退资源，`values-en`、`values-b+zh+Hans`、`values-b+zh+Hant`、`values-ja`、`values-ko` 保持相同键、数组长度与格式参数。iOS 的 `iosApp/LaserSketch/{en,zh-Hans,zh-Hant,ja,ko}.lproj` 保持相同键与格式参数，新增语言资源须同时登记在 Xcode 项目中。请勿翻译单位符号、文件扩展名、格式参数或语言选择中的自称。

运行资源检查：

```sh
python3 scripts/check-localizations.py
```

该检查验证资源覆盖、重复键、空翻译、数组长度、格式参数以及 iOS 资源登记。它不替代人工语言审校。

## 验收

在专用模拟器上从首页设置依次选择五种语言，检查首页、草图、照片标注、设备、错误提示和导出文案。检查长文字、日语/韩语字体、按钮换行和弹窗滚动。重启应用确认语言保留，再恢复跟随系统。确认切换前创建的项目及用户文字仍保持原样。

Android 自动交互回归：`AppLanguageInstrumentedTest`（只允许通过 `ANDROID_SERIAL` 显式选择可丢弃模拟器）。iOS：`scripts/test-ios.sh -only-testing:LaserSketchUITests/LaserSketchUITests/testHomeLanguageSwitchAndPersistence`，脚本创建并清理隔离模拟器。

## 本次验证记录（2026-11-09）

以下命令已通过（Gradle 使用 Android Studio 内置 JDK）：

```sh
python3 scripts/check-localizations.py
./gradlew :shared:checkSharedBoundaries :shared-ui:checkSharedUiBoundaries :shared:jvmTest :shared-ui:jvmTest :app:testDebugUnitTest :app:assembleDebug :shared-ui:compileKotlinIosSimulatorArm64 --console=plain
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest :shared-ui:checkSharedUiBoundaries --console=plain
./gradlew :app:assembleDebugAndroidTest --console=plain
xcodebuild -project iosApp/LaserSketch.xcodeproj -scheme LaserSketch -configuration Debug -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' -derivedDataPath /tmp/laser-sketch-ios-build CODE_SIGNING_ALLOWED=NO build
xcodebuild -project iosApp/LaserSketch.xcodeproj -scheme LaserSketch -configuration Debug -sdk iphoneos -destination 'generic/platform=iOS' -derivedDataPath /tmp/laser-sketch-ios-device-build CODE_SIGNING_ALLOWED=NO build
./scripts/test-ios.sh -only-testing:LaserSketchUITests/LaserSketchUITests/testHomeLanguageSwitchAndPersistence
```

JVM 结果：Android 57 项、共享核心 306 项、共享 UI 60 项，均无失败、错误或跳过。iOS 语言交互回归 1 项通过，覆盖五种语言、重启保留、已有项目名称保留和恢复跟随系统。

Android 交互测试包含首页选择、Activity 重建和 CSV 表头语言检查，测试包已编译；本机 SDK 缺少可执行的 Android emulator 程序且没有连接设备，因此未执行 Android 仪器测试。本次未执行完整 iOS UI 套件或 BLE 硬件验收。
