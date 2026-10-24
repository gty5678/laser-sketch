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
- 可共享逻辑放入 `shared/src/commonMain/kotlin`，不依赖 Android、Java 或 Compose；新旧模型转换集中在 `SketchCoreAdapter`。迁移状态见 [架构说明](docs/architecture.md)。
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
.\gradlew.bat :shared:checkSharedBoundaries :shared:jvmTest
.\gradlew.bat :shared-ui:checkSharedUiBoundaries :shared-ui:jvmTest
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

BLE 分层与测试范围见 [BLE 架构](docs/ble-architecture.md)。修改传输或设备逻辑后，除共享与仪器测试外，还需用 Android 12+ 和 Bosch GLM 验证权限、扫描、测量、重连及 Activity 退出后的清理。仪器测试的模拟权限拒绝不能替代硬件验证。

### 项目交换验证

- 首页选择“导入项目副本 (.lsk)”，导入包含草图、照片标注和测量绑定的有效工程；检查相应列表与编辑页面。
- 重复导入同一文件，确认生成独立副本，原工程及本机修改保持不变。
- 从草图或照片项目菜单导出，再导入该文件；确认完整项目的所有分区、原照片字节和标注保留。
- 取消文件选择、选择无效 ZIP、无效语义数据或缺失照片的工程；确认目录没有半个项目。
- 导出选择器打开时重建首页，返回后仍应导出所选工程；无需存储权限。

自动化回归：`AndroidProjectRepositoryTest` 覆盖重复 ID 和副本引用完整性，`ProjectTransferInstrumentedTest` 覆盖 Android URI、文件提供器、真实编码照片和无效导入的原子失败。系统文件选择器界面与外部文件提供器仍需按上述步骤人工验证。

共享 Compose 界面放入 `shared-ui/src/commonMain/kotlin`，仅依赖共享核心；平台 Context、URI、Bitmap、权限与导航放在应用宿主。Android 文案继续使用 `strings.xml` 并注入共享屏幕。`ProjectScreenInstrumentedTest` 覆盖空态、菜单项目 ID 与目录流的重命名/删除更新；墙角渲染测试在 Android 主线程创建和绘制 View，不能在测试线程安装不运行的 Looper。

`MeasurementScreenInstrumentedTest` 覆盖共享测量界面的禁用状态、单条记录 ID、100 条分页、Double 显示与时间戳碰撞删除。Compose 测试在清理前正常 finish 宿主 Activity，避免依赖辅助页面的停止时序；不得省略原有断言。

`DeviceScreenInstrumentedTest` 验证共享设备面板的状态刷新和回调；它使用合成文案，不访问真实设备，也不能替代 BLE 硬件验证。

`SketchToolbarInstrumentedTest` 覆盖共享核心 ToolType、工具高亮及宿主返回/设置动作；门属性页面测试通过 Compose 语义检查工具栏，并继续验证门翻转按钮的窗口位置、56 dp 尺寸、像素圆形轮廓、竖向排列、模式切换隐藏与保存结果。`SelectionActionsInstrumentedTest` 覆盖墙/门/空选择对应的控件与独立回调。

共享 `SketchEditTools` 与 `GridSnapControl` 的组件测试检查激活状态和回调；`SketchActivityProjectInstrumentedTest` 还通过真实页面开关关闭吸附后放置墙体，检查端点保留非网格毫米坐标。异步加载验收要同时检查 View 已启用和文档内容，空文档相等不能独自证明加载完成。

`EditorActionsInstrumentedTest` 验证共享浮动按钮的可见性和宿主回调；草稿页面测试通过实际按钮完成墙体放置并撤销，删除选中柱后再撤销，检查共享文档完整恢复。

`AnnotationToolbarInstrumentedTest` 检查五种共享标注工具的宿主状态、高亮互斥与独立平台回调，包括窄屏横向滚动。照片工程页面测试通过真实工具栏进入移动模式后编辑标签或取消拖动，继续验证照片字节、Double 坐标及保存边界。

`PlacementOptionsInstrumentedTest` 验证单次/连续选项与宿主状态；照片工程测试通过真实选项创建三条尺寸，检查单次返回移动、连续保持尺寸工具、页面重建恢复连续设置与已保存文档。

`AnnotationActionsInstrumentedTest` 验证完成/删除回调、可见性及删除按钮低于完成按钮 64 dp 的相对布局。照片工程测试还通过真实完成按钮进入移动模式，选中并删除尺寸，在页面暂停保存后验证删除持久化和其他标注保留。

`AnnotationStylePropertiesInstrumentedTest` 检查样式选择、属性刷新无命令及按选择显示控件。照片工程测试通过真实字号、白底和线宽控件修改样式，比较完整共享文档与保存文档，确保 Double 坐标、ID、标签和其他实体不被修改。

`ColorPaletteInstrumentedTest` 验证 ARGB 回调、选中互斥、未知颜色、空选择禁用与外部刷新无额外命令。照片属性页面测试先改文字绿色再改尺寸蓝色，比较完整共享文档与暂停保存结果，验证选中状态切换及原有坐标/标签保留。

`AnnotationLabelPropertiesInstrumentedTest` 验证文字/角度输入、选择刷新无编辑与清除焦点。照片页面通过真实输入框修改文字及角度标签，比较完整文档与暂停保存结果，确认 Double 位置、角度射线与其他属性不变。

`AnnotationQuantityLabelTest` 覆盖旧标签解析、未知单位、空文本及不做数值换算的后缀组合。共享数值控件和照片工程设备测试验证尺寸/面积输入、单位切换、重新选择当前单位无文档改动，以及 Double 端点、多边形和保存结果保持一致。

照片属性的暂停保存验收使用 `ActivityScenario.moveToState(STARTED)`，对应已暂停状态；不要求额外进入 STOPPED，避免将设备停止界面的辅助 Activity 等待混入 onPause 保存契约。清理时先恢复 RESUMED，由 ActivityScenario 关闭暂停用的浮动辅助窗口，再主动 finish 和 close，避免辅助窗口影响下一个用例。文档完整比较、取消预览和保存断言保持不变。

`AnnotationPropertyActionsInstrumentedTest` 验证近期测量最多三条、顺序、点击索引及选择控制的可见性，以及面积顶点删除入口；`AnnotationActivityProjectInstrumentedTest` 通过实际共享按钮验证三点面积删除失败时文档不变，继续验证标签编辑和暂停保存。

`NumberPropertyInstrumentedTest` 验证数值草稿和外部刷新不自动应用；`SketchActivityProjectInstrumentedTest.sharedOpeningWidthAppliesExplicitlyAndPreservesSemanticPrecision` 覆盖实际窗宽输入、无效输入原子失败、Double 宿主/位置/宽度保持、Float 翻转桥接、撤销及持久化。

`ColumnPropertyCommandsTest` 覆盖尺寸边界、无效值原子失败、旋转归一化及 Undo/Redo 精度；`ColumnSizePropertyInstrumentedTest` 和 `SketchActivityProjectInstrumentedTest` 验证柱属性草稿、宽深整体应用、圆柱输入可见性、实际保存与精确几何保持。

`WallControlLinePickerInstrumentedTest` 验证核心控制线身份与刷新无命令；`SketchActivityProjectInstrumentedTest.sharedWallPropertiesPreserveFixedEndsAndRejectHostedOpeningConflicts` 验证实际墙属性输入、门窗冲突原子失败、Double 长厚、控制线切换、移动起点/终点及固定端门距保存。

`DrawingSettingsDialogInstrumentedTest` 验证共享设置草稿、整体提交与取消；`SketchActivityProjectInstrumentedTest.sharedDrawingSettingsApplyAtomicallyAndSurviveRecreation` 验证 Android 范围拒绝时两个参数和偏好均不变、有效设置持久化及页面重建恢复。测试恢复原偏好值。

`OpeningSwingGeometryTest` 在共享 JVM 测试验证 24 个方向、三种定位线和四种翻转组合的精确圆弧与命中折线一致、完整门宽、墙端限制及窗口最小可见框线。`SketchOpeningDrawingInstrumentedTest` 验证共享 DrawScope 经 Android 位图适配后的门扇/圆弧及窗口框线像素；应与现有 `SketchViewDoorRenderingInstrumentedTest` 一起在设备上运行。

`:shared-ui:jvmTest` 中的 `ColumnDrawingLayoutTest` 验证所有 15°方向及三种缩放下的柱绘制/手柄布局一致、最小可见尺寸和圆柱无手柄。`SketchColumnDrawingInstrumentedTest` 覆盖共享 DrawScope 的填充、旋转轮廓、手柄及中心点像素，应与原柱旋转/选择回归共同运行。

`GridDrawingLayoutTest` 覆盖原 Float 缩放阈值、负视口偏移、主次网格线、分数间距与无效参数。`SketchWallDrawingInstrumentedTest` 覆盖共享墙网络扣洞/两侧门框、三种定位线的端点控件和起点十字预览；应与 `WallJointRenderingInstrumentedTest` 的墙角旋转回归一起在设备上运行。JVM 布局测试和仪器测试编译不能代替这些像素回归的设备执行。

`WallLengthLabelLayoutTest` 验证 24 个方向、三种定位线、三种缩放的墙长标签基线位置、实际测量值的 Double 精度、最小厚度与退化墙隐藏。`SketchWallLabelDrawingInstrumentedTest` 比较共享文字与原生粗体文字的居中/基线边界，覆盖普通密度及字体缩放；`SketchSceneDrawingInstrumentedTest` 检查导出隐藏选择/预览、预览定位线及尺寸、临时 ID 重名和文档不变。Android View 的屏幕、放大镜和导出必须共用 `drawSketchScene`，不得在宿主另建绘制顺序。

`MagnifierDrawingLayoutTest` 在 JVM 验证多种缩放、密度和倍率下目标映射到镜片中心、1.8 最大绘制缩放、屏幕平移独立性与无效参数。`SketchCanvasInstrumentedTest` 使用真实 Compose Canvas 截图验证文档更新触发重绘、放大镜居中和圆形裁剪、关闭镜片后恢复背景；`SketchActivityProjectInstrumentedTest.sharedCanvasForwardsRealTouchToTheSameEditorAndHonorsDisabledLoadingState` 通过 Compose 触控注入验证禁用期间不编辑、正常放置与共享撤销。SketchView 通过 AbstractComposeView 承载共享 Canvas，不再维护原生 onDraw；不应在宿主另加画面绘制路径。

草图选择测试使用 `SketchView.selectEntity`，读取 `editorState()` 或选中实体投影；工具和创建预览也读取共享状态，不再反射选中 ID、mode、wallStart 或 columnPreviewCenter 字段。`SketchViewEditorStateInstrumentedTest` 检查属性回调/选择投影/删除撤销的一致性及墙长、定位线编辑后的 Double 墙链起点。共享 `SketchEditorTest` 验证类型化选择与非法目标、工具切换、取消墙链、删除和预览提交后的悬空引用清理，以及取消预览时保留仍有效的选择。

`AnnotationShapeDrawingLayoutTest` 覆盖 24 个方向与三种比例下的尺寸端线/斜刻线、线宽档、重合端点、Double 坐标、面积顶点均值与旧超范围点。`AnnotationShapeDrawingInstrumentedTest` 覆盖实际像素中的端线/刻线、面积透明度和闭合边、顶点选中光晕、开放预览，以及 ImageAnnotationView 完整/缩略图导出复用共享绘制且不改原照片与文档。仪器测试必须在设备执行；仅编译和 JVM 布局通过不能称为像素回归通过。通用原生画布适配器已命名为 `AndroidCanvasDrawing`，草图与照片标注复用。

`AnnotationAngleDrawingLayoutTest` 穷举两射线每 15°方向的组合，验证小角扫角、圆弧与射线方向一致、跨零度及射线交换的标签位置、较短射线/最小半径、非等比例照片映射、重合端点及无效比例。`AnnotationShapeDrawingInstrumentedTest` 增加小角圆弧像素、射线交换及原图/缩略图角度导出回归；仍需要设备执行，不能将编译通过视为像素验收。

`AnnotationLabelLayoutTest` 验证尺寸标签各方向的可读旋转、基线偏移、文字/面积/角度白底边距、字号档与无效度量。`AnnotationLabelDrawingInstrumentedTest` 用旧原生 Paint 绘制作为参考，与共享布局加 Android 字体适配的像素结果比较，覆盖尺寸旋转、白底开关及含换行字符的旧文字；仅编译不能声称字体像素验证通过。View 不直接测量或绘制文字，字体细节位于 `platform/AndroidAnnotationTextPainter.kt`，布局与背景在共享 UI。


`PhotoMagnifierDrawingLayoutTest` 覆盖密度/画布限制、焦点居中和 2.35 倍映射、小画布隐藏、照片完整适配/顶部对齐及无效参数。`AnnotationSceneDrawingInstrumentedTest` 验证类型图层、屏幕/镜片各自的预览顺序、ID 重名不覆盖正式标注、导出隐藏预览，以及放大镜变换/圆形裁剪/十字线像素。`AnnotationCanvasInstrumentedTest` 通过真实 Compose 截图验证照片与标注更新、镜片开关；通过 AndroidView 承载真实 ImageAnnotationView，并经 Compose 触控注入验证一次尺寸创建和撤销重绘。旧尺寸/事务适配测试仅布局离屏宿主，不强制创建无生命周期的 Composition，原行为断言保留。新增画布仪器测试必须在设备运行，编译通过不代表触控或像素验收通过。


`AnnotationEditorStateTest` 验证稳定 ID 选择、非法目标/顶点、选择不写历史或消耗 Redo、工具切换取消预览及显式保留选择、删除和撤销/重做后清理悬空引用、预览专属目标取消、面积顶点范围及恢复文档。`ImageAnnotationViewTransactionInstrumentedTest` 的四类属性编辑回归同时检查共享模式和选择 ID；Android 各类型索引现在是共享状态的只读派生，不能以字段反射或第二份可变选择替代共享编辑器。


`AnnotationCreationTest` 覆盖 Double 尺寸预览与最后抬手坐标、严格像素阈值、短拖动不消耗 Redo、超范围点夹取、重复/空 ID 原子失败和重试、角度点序、三点面积、文字原始内容、一次创建一条历史、工具切换/恢复/单次取消以及大有限长度的溢出保护。`ImageAnnotationViewDimensionInstrumentedTest` 增加共享创建状态、创建前文档不变、确认后清空、取消/切换时部分点击保留规则；不再依赖已移除的 Android 尺寸拖动坐标或可变点列。设备回归仍需执行完整尺寸/事务/Compose 触控套件。


`AnnotationDragTest` 覆盖四类标注的整体/控制点拖动、相对手势起点的绝对位移、严格阈值和返回起点不消耗 Redo、指针越界后的整组夹取、控制点 Double 保留、同一预览的属性修改、稳定目标不跟随选择、非法目标/阈值原子拒绝、取消/工具切换/恢复/命令清理及悬空顶点和位移溢出保护。`ImageAnnotationViewTransactionInstrumentedTest` 增加共享拖动起点/启动/清理、仅一次操作启动回调、指针越出照片后的整组夹取及工具切换回滚；旧行为断言保留。Android 不应再引入 moveKind、moveIndex、moveTouchStart 或 moveStarted 镜像。


`AnnotationHitTestTest` 覆盖类型/控制点优先级、已选及反序面积顶点、最近和同距命中、非正方形照片的像素容差与边界、角度有限射线、面积闭合边和凹多边形、重叠面积顺序、退化几何、Double 精度、旧超范围点及非法输入。`AnnotationHitTestInstrumentedTest` 经原生 MotionEvent 适配验证文字/尺寸/面积重叠时的选择、已选端点优先以及后绘制面积内部选择；测试布局按设备密度缩放，避免固定屏幕外部状态。Android View 不再实现点线距离、射线/多边形命中或列表索引优先级，应与已有尺寸、事务及 Compose 触控套件一起在设备上运行。


`AreaEdgeTapTest` 覆盖闭合边、450 ms 边界、超时/时间回退/零时间戳、稳定 ID 在其他删除后的识别、不同面积/边不串联、非法目标/时间原子拒绝、几何变化/删除/恢复清理及非几何修改保留、释放预览提交/取消、一次撤销、极大时钟和有限极大/次正规坐标中点。`AnnotationHitTestInstrumentedTest.closingEdgeDoubleTapUsesEventTimeAndOneSharedUndoTransaction` 注入确定的 MotionEvent 时间验证实际桥接、两次提示、新顶点选择及一次撤销，不靠线程睡眠命中时间窗口。Android 不应重新引入 lastAreaEdgeTapAt / Area / Index 镜像或以系统日期时间判断双击。


`AnnotationScreenInstrumentedTest` 覆盖主页面容器的剩余高度、动作靠右/垂直居中、属性边距与覆盖顺序，验证属性显示不改变照片视口且点击不穿透到画布。`AnnotationActivityProjectInstrumentedTest.sharedMeasurementSnapshotKeepsDoubleForDefaultAndSelectedDimensionLabels` 覆盖 Double 测量快照用于默认尺寸、自动更新和手动填入；`AnnotationMeasurementLabelTest` 在 JVM 验证显示舍入、大数值、负累计值和负零。仪器测试需连接设备后执行，编译测试 APK 不能替代运行。

`AnnotationPropertySheetTest` 验证下拉透明度、下限和零高度；`AnnotationPropertySheetInstrumentedTest` 覆盖连续短拖回弹、向上夹取、浮层点击不穿透、超过阈值取消手势也收起，以及新选择打断收起动画并复位。后者仍须 Android 设备执行，不能将 APK 编译通过当作行为验收。

`AnnotationActivityProjectInstrumentedTest.swipingSharedPropertySheetKeepsSelectionDocumentAndUndoHistory` 在实际照片页选中尺寸后通过共享手柄收起浮层，再重新选中，验证 Double 文档、核心选择和撤销历史均不因展示变化而改变。

`SelectionGestureTest` 覆盖稳定实体目标、Double 指针、选择变化后的目标一致性、取消/提交/切换工具/永久删除后的状态清理、空白平移和无按下 MOVE。柱移动/放置/旋转、墙放置/控制线拖动、门窗宿主和 T 墙联动仪器测试已改为通过实际 MotionEvent 输入和 `editorState()` 检查行为，保留原几何和像素断言；这些仪器场景仍须设备执行。

`SketchScreenInstrumentedTest` 覆盖系统栏 Insets、工具栏与动作定位、属性显示/增高/隐藏后的吸附位置、完整画布视口和属性点击不穿透；另检查高属性面板不会为了吸附控件被缩小。测试 APK 编译通过仍需连接 Android 设备执行。

`SketchPropertySheetTest` 覆盖严格触控阈值/方向判断、透明度下限与随高度变化的收起阈值；`SketchPropertySheetInstrumentedTest` 覆盖子按钮点击、横向和短拖、长拖取消回弹、动画结束前保留布局及新选择打断动画。实际页面集成测试 `propertySheetDismissalPreservesDoubleDocumentAndSelectionAndResetsUnappliedDraftOnReselection` 验证收起保留精确文档/核心选择/历史，重新选择重置未应用草稿。门属性测试改用 Compose 子树断言右侧翻转入口不在底部面板内；这些仪器场景仍须设备执行。


共享核心和共享 UI 的 import 边界分别由 `:shared:checkSharedBoundaries`、`:shared-ui:checkSharedUiBoundaries` 检查，并接入各模块 `check`。前者拒绝平台、Compose 与宿主依赖，后者拒绝 Android/Java/org.json 与宿主依赖；这两项为 import 检查，不代替职责审计。代码边界已用临时违规文件验证拒绝行为，探针随后清理。

`PhotoViewportMappingTest` 验证非正方形照片的 Double 双向映射、创建夹取与越界拖动、超 Float 精度及空视口拒绝。`AnnotationNoticeInstrumentedTest` 通过实际角度/面积/文字创建验证显式提示标记及原 Toast 时机；这些仪器断言须连接设备执行。


`HostedOpeningEditMatrixTest` 通过共享正式命令覆盖 24 个方向、三种定位线、两种固定端、连接墙门窗冲突、T 宿主跟随，以及 Trim/Extend 的九种定位线组合。断言包括固定端毫米距离、门窗实际位置/宽度、边界墙不变、整次失败保留 Redo 与精确 Undo/Redo；这组 JVM 矩阵不能替代 Android 触控和像素套件。
