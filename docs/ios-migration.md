# Android / iOS 双端开发与验证

同一仓库生成 Android 和 iOS 两个独立应用。两端复用 `shared` 的编辑文档、几何命令、Bosch 协议、工程序列化及 `shared-ui` 的界面组件与场景绘制；平台适配分别实现。共享代码和 `.lsk` 格式保持一致，不包含云端数据自动同步。

## 当前实现

| 范围 | iOS 实现 | 自动化验证与边界 |
| --- | --- | --- |
| 工程宿主 | `iosApp/LaserSketch.xcodeproj`、共享 `LaserSketch` scheme、静态 `LaserSketchUI.framework` | Xcode 模拟器及 iPhone arm64 未签名构建；模拟器实际启动 |
| 草图 | 墙、柱、门窗、选择与拖动、放大镜、分割/测距、连接/延伸、定位线、创建参数与属性、门型和翻转、撤销重做、尺寸创建及尺寸驱动、网格与绘图设置 | 共享核心在 JVM 和 iOS Native 执行；XCTest 覆盖参数校验、墙创建/撤销/重做、提交后像素刷新、尺寸创建与驱动实际墙边、保存重启恢复。未将代表性场景视为整份绘图规范的逐项触控验收 |
| 项目 | 列表、命名/重命名、删除确认、照片分区、本地原子保存、`.lsk` 导入独立副本及导出 | 存储及分区保护测试；Android 实际导出器的 DEFLATE 样本可由 iOS 读取，iOS STORED 导出可由 Android 实际导入器读取；XCTest 验证系统文件选择器导入 Android 样本、打开草图并重启恢复，以及系统导出面板 |
| 照片标注 | 系统 PHPicker、图片方向归一化、共享 AnnotationCanvas/Editor、尺寸/文字/角度/面积、选择移动、控制点、颜色/样式、数值单位、面积顶点增删、单次/连续放置、显式接收下一次测量、保存/分享及相册写入 | XCTest 覆盖系统选择照片、四类标注创建、删除、文字输入、真实相册写入成功和重启恢复；共享核心覆盖预览取消、拖动及顶点约束。相册权限的完整真机交互仍待验证 |
| 蓝牙测距 | CoreBluetooth 扫描、服务/特征发现、通知确认、带响应写入、超时/取消、会话隔离、手动断开与前台自动重连、共享 BoschLaserDevice、持久测量历史和 CSV | 通知旧会话丢弃及溢出回归；模拟器扫描不可用时显示真实错误；iPhone + Bosch GLM 的权限、测量和断连/重连尚待真机验收。连续测量沿用 Android 当前范围，不新增入口 |

iOS 功能代码已接入，仍不能将模拟器、纯逻辑测试或未签名构建称为蓝牙真机验收或生产发布验收。

## 打开与运行

- Android：根目录 `./gradlew :app:assembleDebug`，产物 `app/build/outputs/apk/debug/app-debug.apk`。
- iOS：用 Xcode 打开 `iosApp/LaserSketch.xcodeproj`，选择 `LaserSketch` scheme 和 iPhone 模拟器，按 Command-R。
- iPhone：在 Xcode 的 Signing & Capabilities 选择自己的 Team，再选择已配对且开启开发者模式的 iPhone。仓库不保存个人 Team、证书或描述文件。
- Kotlin / Compose 在 Android Studio 编辑；Swift 宿主、签名及原生调试在 Xcode 编辑。

需要 macOS、Xcode、已安装的 iOS 模拟器运行时、Android SDK、Python 3 和 JDK 17+。Xcode 构建阶段调用 `iosApp/build-shared.sh`：优先使用 JAVA_HOME，其次系统 JDK，再尝试标准路径下 Android Studio 的 JBR。非标准安装可在 scheme 中设置 JAVA_HOME。首次编译需要下载 Kotlin/Native / LLVM。

支持 iPhone arm64 与 Apple Silicon 模拟器 arm64，最低部署 iOS 16；本机 UI 验证运行在 iOS 27.2。没有配置 Intel 模拟器 target。Xcode 直接集成依据 [Kotlin 官方文档](https://kotlinlang.org/docs/multiplatform/multiplatform-direct-integration.html)。

## 平台与资源边界

- `shared/commonMain` 保持无 Android、Foundation、Java、Compose 依赖。
- `shared-ui/commonMain` 提供共享组件、资源和绘制；`shared-ui/iosMain` 提供 UIKit 入口、Compose 指针适配、文件/照片/字体/导出和 CoreBluetooth。
- `IosProjectRepository` 接入同一个共享 `ProjectService`。草图保存自动过滤失效绑定，照片保存不覆盖草图、其他照片、测量与资产。已接受保存使用应用 scope，并在退后台时申请有限后台完成时间；保存失败保留页面和正式编辑文档。
- 草图和照片持久化前先取消未提交手势；绘制观察显式刷新状态，不能靠视口变化偶然刷新。字体和图片通过宿主适配进入同一场景，图片导出排除选择和预览。
- 图标唯一源位于 `shared-ui/src/commonMain/composeResources/drawable`。Android 原生宿主的 `R.drawable` 通过 app 的资源源目录继续读取这些文件，iOS 通过 Compose Resources 读取。共享矢量使用具体颜色值，不允许 `@android:color` 引用；边界检查覆盖该规则。
- Android 文案继续使用 strings.xml；iOS 使用 `iosApp/LaserSketch/{en,zh-Hans}.lproj/Localizable.strings`，权限说明使用各语言的 InfoPlist.strings，色样使用 Palette.plist。主题使用 MaterialTheme。
- 两端单位转换共用十进制移位逻辑，保留物理量，避免把二进制浮点尾数显示为 `12.575000000000001`。

## 数据与系统操作

- 兼容第一阶段 iOS 私有 JSON：Application Support/LaserSketch/projects 下的四份语义 JSON 包装文件继续读取，不清空旧数据。
- 照片保存在项目 ID 对应的私有资产目录。新资产使用独立 ID；导入前校验容器、CRC、路径、条目数量、大小、元数据及资产集合，之后写入新项目 ID，不覆盖已有项目。失败不发布半个项目。
- `.lsk` 为标准 ZIP32，iOS 导出 STORED、读取 STORED/DEFLATE 和数据描述符，使用共享 ProjectSerializer；不把 ZIP 中的路径直接提取到文件系统。压缩包输入最多 256 MiB，条目最多 4096，单资产 128 MiB、元数据 32 MiB、解压总量 256 MiB；不支持加密、多磁盘或 ZIP64 容器。
- PHPicker 仅提供用户选择的图片，不申请读取整个照片库的权限。选择图片按方向归一化、最长边限制为 4096 像素；缩略图使用 512 像素上限。无法处理或过大的图片明确报错。内部标注坐标保持归一化 Double。
- 保存相册仅申请添加照片权限；分享使用系统 UIActivityViewController 和临时文件 URL，完成后清理临时文件。Documents/Exports 可由系统“文件”访问，私有工程与原照片继续保存在 Application Support。
- 测量历史使用独立原子文件和应用 scope，按记录 ID 更新；时间戳相同仍保留不同记录。CSV 使用共享七列协议及其指定的小数位数。测量不会自动修改草图，照片尺寸仅显式等待一次新测量时自动填入。
- CoreBluetooth 对象和回调位于主队列；连接操作串行。新连接使用新 central，队列中的通知带原始会话 ID，旧通知不可冒充新连接。通知拥塞失败断开，写入仅在原生确认后成功，实际退后台时取消扫描/连接，前台恢复已选择设备；权限弹窗造成的暂时失焦不取消蓝牙授权后的扫描。
- 不改变 Android 的包名、SQLite、工程布局、FileProvider 或 release 签名设置。iOS 未配置 App Store 发布。

## 统一验证入口

```sh
./scripts/check-mobile.sh
# 包含真实 iOS 界面测试和已连接 Android 的仪器测试：
ANDROID_SERIAL=emulator-5554 LASERSKETCH_UI_TESTS=1 LASERSKETCH_ANDROID_DEVICE_TESTS=1 ./scripts/check-mobile.sh
```

`check-mobile.sh` 执行两个边界检查、JVM 与 iOS Native 测试、Android APK、iOS 模拟器及未签名 iPhone 构建。Android 仪器测试需要已启动的专用 Android 12+ 模拟器，并通过 `ANDROID_SERIAL` 明确选择；禁止使用保存日常项目的手机。

```sh
# 创建一次性模拟器并注入合成照片和 Android 工程样本，测试后删除该模拟器：
./scripts/test-ios.sh
# 失败调查时可保留脚本自己创建的模拟器：
LASERSKETCH_KEEP_TEST_SIMULATOR=1 ./scripts/test-ios.sh
```

XCTest 仅使用 Debug 下的随机测试存储及偏好域，不依赖真实项目和测量。照片及文件导入测试还要求脚本设置的 LASERSKETCH_TEST_PHOTO_READY 标记；直接在普通模拟器中按 Command-U 时跳过这两项，避免误选用户已有内容。脚本只在自己创建的一次性模拟器中授予添加照片权限，并在系统“文件”的本地目录放入合成 Android 工程样本；相册权限弹窗组合仍留待真机。可用 `-only-testing:LaserSketchUITests/LaserSketchUITests/方法名` 选择测试，并用 `-resultBundlePath /tmp/自定义名字.xcresult` 保留截图和报告。脚本不更改已有模拟器的照片库。界面测试处理首次键盘引导，并等待键盘退出及异步重绘，保留几何与恢复结果断言。

单独运行命令：

```sh
./gradlew :shared:checkSharedBoundaries :shared-ui:checkSharedUiBoundaries \
  :shared:jvmTest :shared-ui:jvmTest :app:testDebugUnitTest :app:assembleDebug \
  :shared:iosSimulatorArm64Test :shared-ui:iosSimulatorArm64Test
xcodebuild -project iosApp/LaserSketch.xcodeproj -scheme LaserSketch -configuration Debug \
  -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath /tmp/laser-sketch-ios-build CODE_SIGNING_ALLOWED=NO build
xcodebuild -project iosApp/LaserSketch.xcodeproj -scheme LaserSketch -configuration Debug \
  -sdk iphoneos -destination 'generic/platform=iOS' \
  -derivedDataPath /tmp/laser-sketch-ios-device-build CODE_SIGNING_ALLOWED=NO build
./gradlew :app:connectedDebugAndroidTest
```

## 2026-11-07 验证记录

- 最终执行 `./scripts/check-mobile.sh`：两个分层检查、Android APK、iOS 模拟器及 iPhone arm64 未签名构建均通过。
- JVM：296 项共享核心、49 项共享 UI、56 项 Android 单元测试，共 401 项通过；iOS Native：296 项共享核心、64 项共享 UI 与平台测试，共 360 项通过（0 失败/错误/跳过）。新增覆盖 ZIP、真实 Foundation URL 读取、原子存储、工程分区与绑定、测量持久化与数字格式、通知会话及溢出。
- Xcode 的 8 项界面测试全部通过（0 失败/跳过），覆盖墙体重绘和保存恢复、参数草稿、尺寸驱动实际墙边、四类照片标注、实际相册写入、系统文件选择器导入 Android 工程并重启恢复、系统分享、测量入口和模拟器蓝牙不可用。
- Android 全量 217 项仪器测试已通过。旧测试按当前行为规范补齐显式等待测量、单位换算、延迟删除、分页与设置草稿的断言；柱拖动夹具明确避开墙端点命中区域，保留原移动/取消断言。
- 按用户当前确认，本阶段完成代码和模拟器验证。后续真机验收尚包括：真实 iPhone 上的相册权限组合、完整绘图/手势逐项验收、与 Bosch GLM 的权限授予/拒绝、测量及重连；当前仅有模拟器，未声称这些检查已通过。
