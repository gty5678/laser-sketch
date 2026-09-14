# 参与贡献

感谢你为 LaserSketch 做出贡献。本文说明本地开发、代码修改、测试和提交合并请求时的约定。开始前建议先阅读 [README.md](README.md)；涉及平面草稿时，还必须阅读 [绘图行为规范](docs/DRAWING_BEHAVIOR_SPEC.md)。

## 开发环境

准备以下工具：

- Android Studio（建议使用当前稳定版）
- JDK 17 或更高版本，用于运行 Gradle 9.5
- Android SDK Platform 37 及对应 Build Tools
- Android 12（API 31）或更高版本的模拟器/真机
- 可选：支持 BLE 的 Bosch GLM 设备，用于端到端测距验证

项目源码的 Java 兼容目标是 11，但请让 Gradle 使用 JDK 17+。首次构建会通过 Gradle Wrapper 下载依赖，需要能够访问 Google Maven、Maven Central 和 Gradle 分发服务。

## 获取并运行项目

1. 克隆仓库后，用 Android Studio 打开仓库根目录。
2. 确认 Android SDK 路径已由 Android Studio 写入本机的 `local.properties`；该文件不要提交。
3. 等待 Gradle Sync 完成。
4. 选择 `app` 配置，在 API 31+ 的设备或模拟器上运行。

也可以在 Windows PowerShell 中构建：

```powershell
.\gradlew.bat :app:assembleDebug
```

macOS/Linux 使用：

```bash
./gradlew :app:assembleDebug
```

Debug APK 生成在 `app/build/outputs/apk/debug/app-debug.apk`。

## 开发流程

1. 从团队约定的基线分支创建一个短生命周期分支；仓库未强制规定分支名称。
2. 一次提交或合并请求聚焦一个问题，避免混入无关格式化和重构。
3. 修改前找到相关测试。修复缺陷时，尽可能添加一个会在旧实现上失败的回归测试。
4. 实现改动并执行与影响范围相匹配的验证。
5. 提交前检查 `git diff` 和 `git status`，确保没有本机配置、生成物、密钥或用户数据。

建议提交信息使用简短的祈使句并说明结果，例如：

```text
Fix hosted opening position after wall resize
Add regression coverage for split BLE frames
```

若团队已有工单系统，可在提交或合并请求正文中关联工单；本仓库不强制某一种提交信息规范。

## 代码约定

- 使用 Kotlin，保持现有 4 空格缩进和 Android/Kotlin 命名习惯。
- 优先编写职责单一、可测试的纯 Kotlin 逻辑，减少 Activity 和自定义 View 中不必要的业务逻辑。
- 用户可见文案放入 `app/src/main/res/values/strings.xml`，主题、颜色和图形资源放入相应 `res/` 目录。
- 不进行与当前任务无关的大范围重命名、排序或格式化。
- 不随意改变 `applicationId`、持久化格式和数据库结构；这些变化可能导致已安装应用的数据不可访问。
- 新增依赖前说明必要性，优先使用 AndroidX/标准库和版本目录 `gradle/libs.versions.toml`，避免为小功能引入大型依赖。

### 平面草稿与几何

`docs/DRAWING_BEHAVIOR_SPEC.md` 是绘图交互的行为契约。修改墙体、墙角、门窗、柱、吸附、裁剪、延长、拉直或撤销时：

- 同步维护规范、实现和测试；
- 保证共享端点和门窗宿主关系一致；
- 保证不合法操作原子失败，不留下半完成状态；
- 对纯几何算法增加 JVM 测试，对 Canvas/触控行为增加仪器测试；
- 浮点比较使用符合场景的容差。

### BLE 与隐私

- BLE 变更需覆盖权限授予和拒绝、扫描、连接、断开、重连及回调时序。
- 协议解析尽量保持为纯逻辑，并用原始字节帧的最小匿名样例测试。
- 不提交真实设备地址、用户测量记录、照片、数据库、日志或其他可识别信息。
- 新增权限或改变数据存储/分享方式时，在合并请求中说明用途和隐私影响，并同步更新 README。

## 测试

运行 JVM 单元测试：

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

运行已连接设备或模拟器上的仪器测试：

```powershell
adb devices
.\gradlew.bat :app:connectedDebugAndroidTest
```

macOS/Linux 将 `.\gradlew.bat` 替换为 `./gradlew`。

最低验证建议：

| 改动类型 | 必须执行 | 额外验证 |
| --- | --- | --- |
| 文档、注释 | 检查链接、命令和 Markdown | 无 |
| 协议、状态、纯几何逻辑 | `:app:testDebugUnitTest` | 相关边界用例 |
| Activity、View、资源、Manifest | `:app:assembleDebug` | 相关单元/仪器测试 |
| 触控与 Canvas 绘制 | `:app:assembleDebug` | `:app:connectedDebugAndroidTest` 和人工交互检查 |
| BLE 连接流程 | `:app:assembleDebug`、相关单元测试 | Android 12+ 真机与 Bosch GLM 实测 |
| 存储或导出 | `:app:assembleDebug`、相关测试 | 升级兼容、重启恢复、导出/分享检查 |

如果没有模拟器、真机或 Bosch 硬件，请在合并请求中明确列出未执行的测试及原因。

## 人工验收重点

根据改动范围检查以下场景：

- 首次启动时蓝牙权限拒绝、再次授权和正常扫描；
- Bosch 设备连接、测量、断开和重新连接；
- 测量历史写入、清空和 CSV 导出；
- 草稿项目创建、保存、重开、重命名和删除；
- 绘图工具的放置、选择、移动、撤销及规范中的墙体/门窗/柱场景；
- 图片来源选择、标注编辑、项目恢复、保存到相册及分享。

## 提交合并请求

合并请求应包含：

- 解决的问题和采用的方案；
- 影响的功能区域及潜在风险；
- 实际执行的测试命令和结果；
- 未执行的验证及原因；
- UI 变化的前后截图或短视频；
- 权限、数据格式、数据库或依赖变化的说明；
- 若行为发生变化，对 README 或 `docs/DRAWING_BEHAVIOR_SPEC.md` 的同步更新。

提交前请确认：

- [ ] 改动只包含当前问题所需内容
- [ ] 新增/修复行为有对应测试，或已说明无法自动化测试的原因
- [ ] 适用的构建与测试任务已经通过
- [ ] 没有提交 `local.properties`、构建目录、APK/AAB、密钥或用户数据
- [ ] 用户文案使用资源文件，文档与实际行为一致
- [ ] UI 改动已在 API 31+ 设备或模拟器上检查

## 发布说明

当前 `release` 构建临时使用 debug 签名且未启用代码优化，只用于本地安装验证，不能作为生产发布配置。请勿提交私有签名材料；正式签名、版本升级和发布流程应由项目维护者单独处理。
