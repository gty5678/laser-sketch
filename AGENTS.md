# AGENTS.md

本文件适用于仓库根目录及其所有子目录，用于帮助自动化开发代理安全、准确地修改 LaserSketch。若子目录中出现更具体的 `AGENTS.md`，以离目标文件最近的说明为准。

## 项目概览

LaserSketch 是一个单模块原生 Android 应用，使用 Kotlin 和传统 View/Canvas UI。它通过 BLE 连接 Bosch GLM 测距设备，并提供测量历史、平面草稿和图片标注功能。

- Gradle 项目名：`LaserSketch`
- 应用模块：`:app`
- 包名及 namespace：`com.example.boschlaser`
- 最低 Android 版本：API 31（Android 12）
- compileSdk / targetSdk：37
- Gradle / AGP：9.5.0 / 9.3.2
- Gradle 运行 JDK：17 或更高版本
- Java/Kotlin 字节码兼容目标：Java 11

## 重要文件与职责

- `app/src/main/java/com/example/boschlaser/MainActivity.kt`：首页、权限、BLE 设备连接和测量记录入口。
- `BoschBleManager.kt`：BLE 扫描、连接、GATT 通信与生命周期管理。
- `BoschProtocol.kt`：Bosch 数据帧和测量结果解析；应尽量保持为可做 JVM 单元测试的纯逻辑。
- `MeasurementStore.kt`：进程内最新测量值及监听。
- `MeasurementHistoryStore.kt`：SQLite 测量历史。
- `SketchActivity.kt` / `SketchView.kt`：平面草稿界面、Canvas 绘制、触控和几何编辑。
- `WallJointGeometry.kt`：墙角几何计算；优先在这里放置可复用的纯几何逻辑。
- `AnnotationActivity.kt` / `ImageAnnotationView.kt`：图片标注界面、绘制和交互。
- `SketchProjectStore.kt` / `AnnotationProjectStore.kt`：项目序列化及应用内部文件管理。
- `docs/DRAWING_BEHAVIOR_SPEC.md`：平面绘图行为契约和验收场景。
- `app/src/test/`：不依赖 Android 运行时的 JVM 单元测试。
- `app/src/androidTest/`：需要设备或模拟器的 Android 仪器测试。

## 修改原则

1. 修改前先阅读目标类、相邻测试和相关行为文档；不要仅根据类名推断行为。
2. 保持改动聚焦，不顺手重构无关代码，也不要覆盖工作区中已有的用户改动。
3. 新逻辑优先拆成可测试的纯 Kotlin 函数。只有依赖 `Context`、View、Canvas、触控或 Android 生命周期的行为才放入仪器测试。
4. 绘图功能必须遵守 `docs/DRAWING_BEHAVIOR_SPEC.md`。如果需求有意改变既有行为，应在同一次改动中更新规范和对应测试。
5. 对墙、门窗、柱等模型的修改要维护几何不变量，包括共享端点、宿主关系、实际长度/位置和原子失败；操作失败时不得留下部分状态。
6. BLE 代码必须处理权限、扫描/连接状态、回调时序和 Activity 生命周期。不要在日志、测试或提交内容中写入真实设备标识或用户测量数据。
7. 持久化格式变更要考虑现有本地数据兼容性。除非需求明确允许迁移或清除数据，不要随意改变包名、文件布局、JSON 字段含义或 SQLite 结构。
8. UI 文案应放在 `app/src/main/res/values/strings.xml`，颜色和主题值应放在资源文件中；不要在 Kotlin 中新增可见文案或重复的样式常量。
9. 保持当前 Kotlin 风格：4 空格缩进、清晰命名、尽量缩小可见性。不要只为格式统一而大范围重排现有文件。
10. 不要提交生成物或本机配置，包括 `.gradle/`、`.gradle-local/`、`.android/`、`.idea/`、`build/`、`local.properties`、APK/AAB、密钥和日志。

## 构建与验证

Windows PowerShell 使用仓库自带 Wrapper：

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:connectedDebugAndroidTest
```

macOS/Linux 对应使用 `./gradlew`。不要依赖系统安装的 Gradle。

按改动范围选择验证：

- 文档或注释：检查链接、路径、命令和 Markdown 结构。
- 纯 Kotlin、协议、测量状态或几何计算：运行 `:app:testDebugUnitTest`。
- Android 资源、Manifest、Activity/View 或持久化集成：至少运行 `:app:assembleDebug`，并在适用时运行单元测试。
- 触控、Canvas 绘制、墙体/门窗/柱交互：运行相关仪器测试；条件允许时运行完整 `:app:connectedDebugAndroidTest`。
- BLE 行为：除自动化测试外，在真实 Android 12+ 设备和目标 Bosch GLM 设备上验证权限拒绝/授予、扫描、连接、测量和重连。

仪器测试需要已启动的模拟器或已连接设备，可先检查：

```powershell
adb devices
```

如果环境缺少 SDK、设备或硬件，仍应完成所有可运行的检查，并在结果中准确说明未运行的项目和原因，不能声称测试已通过。

## 测试要求

- 修复缺陷时，尽可能先添加能复现问题的回归测试。
- 新协议解析、数值转换和几何算法应覆盖正常输入、边界值与无效输入。
- 浮点几何比较使用合理容差，不直接依赖精确相等。
- 仪器测试应独立、可重复，不依赖执行顺序、真实用户数据或固定屏幕外部状态。
- 若测试通过反射访问 `SketchView` 内部状态，重命名相关字段时同步更新测试；新增测试仍优先通过稳定的公开或 `internal` 测试入口验证行为。
- 不删除或放宽断言来迁就实现；只有行为契约确实改变时才更新预期。

## 权限、安全与发布

- 保留 BLE 所需权限的版本语义，新增权限必须有直接功能理由，并同步更新 README/贡献说明中的验证步骤。
- 文件分享必须继续使用 `FileProvider` 或系统提供的安全 URI，不暴露内部绝对路径。
- 不提交签名密钥、口令、令牌、真实设备地址或包含用户照片/测量记录的样本。
- 当前 release 构建临时使用 debug 签名且未启用优化，不可视为生产发布配置。未经明确要求，不修改签名或发布设置。

## 完成标准

交付前应确认：改动符合行为规范；新增或变化的行为有测试；适用的 Gradle 任务已通过；`git diff` 中只有预期文件；文档与实现一致。最终说明应列出修改摘要、实际执行的验证命令，以及任何因环境限制未完成的检查。
