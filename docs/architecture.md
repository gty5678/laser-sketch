# LaserSketch 架构

## 实施范围与状态

本轮按 [改造方案](../改造方案.md) 改造 Android。共享核心和共享 UI 为后续跨平台准备；只保留已有 iOS target 声明，不实现或验收 iOS 宿主、CoreBluetooth、桌面 CAD 或云同步服务。

Android 已接入下述模块和数据路径，当前分层边界检查、构建、299 项 JVM 回归与 176 项 Android 16 真机仪器测试全部通过（0 失败/错误/跳过）。真实权限拒绝/授予、扫描、手选 GLM 连接、单次测量与读数一致性、主动重连、首页重建及设备关机重开后的自动恢复均已确认。连续测量按用户最新要求不纳入本轮实施与验收。方案 1～44 节的对应证据与缺口集中在 [核对记录](refactor-plan-audit.md)。

行为细节分别以 [绘图规范](DRAWING_BEHAVIOR_SPEC.md)、[照片标注规范](ANNOTATION_BEHAVIOR_SPEC.md) 和 [工程格式](project-format.md) 为准。

## 关联尺寸与尺寸驱动

2026-10-24 墙长编辑统一改为尺寸驱动：移除墙属性的长度输入、移动起终点切换及近期测量直接改墙长入口，保留定位线和墙厚；取消仅为该入口服务的测量记录订阅，拉平沿用固定起点的默认行为。`canDimensionWith` 在模型、参考线选择和旧标注端点吸附中一致开放同一墙的 START/END 配对，其他同对象组合继续拒绝。驱动同墙两端时保持 first 端截面不动，调用已有 `ChangeWallLengthCommand` 沿墙原方向移动 second 端及共享节点，门窗维持到固定端的毫米距离；墙段最短 100 mm，连接墙过短/反转、缩短越过内部 T 接点或门窗不容纳时原子失败。引用顺序、尺寸位置和格式字段保持，适用现有保存、导出与撤销。操作提示明确先选端固定、后选端移动。新增回归在修复前复现无法选择同墙两端；修复后 277 项共享核心 JVM、49 项共享 UI JVM、46 项 Android JVM、两个边界检查、debug 构建及 37 项相关 Android 16 真机测试通过（0 失败/错误/跳过）。覆盖两种选择顺序、旋转/非中心定位墙、固定端门窗距离、共享节点、T 接点、原子失败、旧尺寸端点吸附、序列化、实际画布创建/输入及页面重建；原属性草稿测试改验保留的墙厚，原墙长属性测试改走图上尺寸输入。未重跑完整仪器套件。执行：

```powershell
.\gradlew.bat :shared:checkSharedBoundaries :shared:jvmTest :shared-ui:checkSharedUiBoundaries :shared-ui:jvmTest :app:testDebugUnitTest :app:assembleDebug :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.example.boschlaser.SketchDimensionInstrumentedTest,com.example.boschlaser.SketchActivityProjectInstrumentedTest,com.example.boschlaser.SketchSceneDrawingInstrumentedTest' --console=plain
```

2026-10-24 扩展测距关键边吸附：测距通过 `SnapContext.objectEdges` 启用共享 `PhysicalEdgeSnap`，从墙物理轮廓、门窗洞口边框及旋转矩形柱生成有限边段和角点/中点，圆柱使用四向极值点及真实圆周投影；不使用符号线宽或圆柱外接矩形。实体角点、边中点/极值点、边投影依次匹配屏幕换算容差；近重合时门窗/柱先于墙，无实体目标时回退原墙定位线及网格规则。两个测距落点、放大镜和释放确认使用同一路径，画墙等默认吸附行为保持原样，不改变工程格式。修复前四项回归复现各类实体目标缺失；修复后 272 项共享核心 JVM、49 项共享 UI JVM、46 项 Android JVM、两个边界检查、debug 构建及 12 项相关 Android 16 真机测试通过（0 失败/错误/跳过）。覆盖墙定位线设置、门窗、旋转柱、圆柱真实边、两次释放、容差及网格回退；真实画布依次捕捉墙角、门框、旋转柱角及圆柱极值点，工程与历史保持不变。未重跑完整仪器套件。执行：

```powershell
.\gradlew.bat :shared:checkSharedBoundaries :shared:jvmTest :shared-ui:checkSharedUiBoundaries :shared-ui:jvmTest :app:testDebugUnitTest :app:assembleDebug :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.example.boschlaser.SketchMeasurePlacementInstrumentedTest,com.example.boschlaser.SketchCanvasInstrumentedTest,com.example.boschlaser.SketchSceneDrawingInstrumentedTest,com.example.boschlaser.SketchActivityProjectInstrumentedTest#measureAndSplitToolbarEntriesUseCoreStateAndPersistOnlyCommittedSplit' --console=plain
```

2026-10-24 补齐草图测距第一点的放大镜预览：`MeasureTool` 在起点尚未确认时也计算吸附点、更新放大镜焦点，保持 `preview` 为空直到松手；第一点释放才建立待完成测距，第二点按住移动只更新终点预览，释放才完成。共享场景在第一点定位期间显示待落点，不显示未确认距离；释放、取消和缩放沿用清理规则，测量及落点标记不进入工程和导出。新增回归在修复前复现第一点无放大镜，修复后 267 项共享核心 JVM、49 项共享 UI JVM、46 项 Android JVM、两个边界检查、debug 构建及 11 项相关 Android 16 真机测试通过（0 失败/错误/跳过）。覆盖两个点按下/移动/释放的中间状态、吸附焦点、取消/缩放、待落点像素、放大镜裁剪及测量不保存；未重跑完整仪器套件。执行：

```powershell
.\gradlew.bat :shared:checkSharedBoundaries :shared:jvmTest :shared-ui:checkSharedUiBoundaries :shared-ui:jvmTest :app:testDebugUnitTest :app:assembleDebug :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.example.boschlaser.SketchMeasurePlacementInstrumentedTest,com.example.boschlaser.SketchCanvasInstrumentedTest,com.example.boschlaser.SketchSceneDrawingInstrumentedTest,com.example.boschlaser.SketchActivityProjectInstrumentedTest#measureAndSplitToolbarEntriesUseCoreStateAndPersistOnlyCommittedSplit' --console=plain
```

2026-10-23 补齐墙端短边尺寸参考：`WallFaceSide.START/END` 解析为该端左右物理墙面之间的截面，沿用已有引用、绘制、端点吸附、持久化及尺寸驱动路径。门窗边框先选和墙端短边先选均可配对；同宿主门窗选中后沿墙移动，固定墙不变，越界或带动固定参考时原子失败。命中将亚像素差异作为同距，保留门窗/柱及墙端相对墙中心轴的优先顺序，避免屏幕坐标舍入导致中心轴抢选。原自动净距仍显式只枚举左右长面。修复前新增回归复现无法选择短边；修复后 265 项共享核心 JVM、49 项共享 UI JVM、46 项 Android JVM、两个边界检查、debug 构建及 21 项相关 Android 16 真机测试通过（0 失败/错误/跳过）。覆盖两端、旋转、定位线设置、门窗驱动、失败原子性、撤销重做、保存恢复及真实画布两种选择顺序；未重跑完整仪器套件。执行：

```powershell
.\gradlew.bat :shared:checkSharedBoundaries :shared:jvmTest :shared-ui:checkSharedUiBoundaries :shared-ui:jvmTest :app:testDebugUnitTest :app:assembleDebug :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.example.boschlaser.SketchDimensionInstrumentedTest,com.example.boschlaser.SketchSceneDrawingInstrumentedTest' --console=plain
```

2026-10-22 修复放置预览的回位与跳变：预览阶段按下及空白轻微移动不更新定位点，确认平移时直接保持原世界位置；`DimensionGesture` 保存按下世界坐标和实际尺寸拖动状态，命中预览线/数字且超过 touchSlop 后按起点位移更新，保留手指偏移。抬手沿用拖动预览，短按仍按目标位置放置，旧端点吸附按移动后的预览定位点计算。修复前回归断言复现空白按下跳位和数字拖动偏移丢失；修复后 262 项共享核心 JVM、46 项 Android JVM、共享核心边界检查、debug 构建及 16 项相关 Android 16 真机测试通过（0 失败/错误/跳过）。真机检查按下、轻微移动和释放的中间状态，并覆盖连续拖动、移图、缩放、尺寸驱动及保存恢复；未重跑完整仪器套件。执行：

```powershell
.\gradlew.bat :shared:checkSharedBoundaries :shared:jvmTest :app:testDebugUnitTest :app:assembleDebug :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.example.boschlaser.SketchDimensionInstrumentedTest,com.example.boschlaser.SketchViewEditorStateInstrumentedTest' --console=plain
```

2026-10-22 调整尺寸提示及建筑画法：选择关联墙、门窗或柱时，有效尺寸采用加粗数字与加粗尺寸线提示可激活输入；无关、失效及导出尺寸保持常规样式。共享场景依据选择类型、引用与有效布局派生提示，不改模型或历史。`DimensionDrawingStyle` 由 Android 资源注入细实线、中粗 45° 刻线、界线留空/伸出及文字间距；`dimensionTextPlacement` 统一计算线上文字、竖向左侧旋转、斜向可读方向、短尺寸外置及旋转命中矩形。Android 仅对关联尺寸使用纯毫米数字格式，独立测距文案保持原样。屏幕操作提示不进入工程预览或导出。画法依据及出图边界见 [绘图规范](DRAWING_BEHAVIOR_SPEC.md#关联尺寸标注与尺寸驱动)。本次 49 项共享 UI JVM、46 项 Android JVM、共享 UI 边界检查、debug 构建与 19 项相关 Android 16 真机回归通过（0 失败/错误/跳过）；未重跑完整仪器套件。像素回归比较关联门窗/柱的线色和线宽、无关墙不强调及导出普通样式，并保留透明背景、无移动箭头、数值输入、预览拖动、平移与缩放断言。执行：

```powershell
.\gradlew.bat :shared-ui:checkSharedUiBoundaries :shared-ui:jvmTest :app:testDebugUnitTest :app:assembleDebug :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.example.boschlaser.SketchDimensionInstrumentedTest,com.example.boschlaser.SketchSceneDrawingInstrumentedTest' --console=plain
```

2026-10-22 修复第二参考线选完后无法拖动预览尺寸：`DimensionGesture.movingDimension` 按按下时的预览线/端点或真实数字文本矩形确定手势，命中后只更新尺寸预览，抬手一次提交；空白拖动仍返回 `panBy`，不因途中经过尺寸线切换目标。Android 复用共享文字布局提供预览数字命中。351 项 JVM 测试、两个边界检查、debug 构建与 16 项相关 Android 16 真机回归通过（0 失败/错误/跳过）；本次未重跑完整仪器套件。新增真机用例选择两参考线后按住数字拖到新位置，断言正式文档在移动中不变、图面坐标不动、释放后尺寸位置正确且仅一次撤销。空白平移、连续缩放、端点吸附和尺寸驱动回归同时通过。执行：

```powershell
.\gradlew.bat :shared:checkSharedBoundaries :shared:jvmTest :shared-ui:checkSharedUiBoundaries :shared-ui:jvmTest :app:testDebugUnitTest :app:assembleDebug :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.example.boschlaser.SketchDimensionInstrumentedTest,com.example.boschlaser.SketchViewEditorStateInstrumentedTest' --console=plain
```

2026-10-22 补齐尺寸模式单指平移：`DimensionGesture` 在共享状态中保存按下屏幕坐标及原尺寸预览，累计位移超过系统 touchSlop 后返回完整首段和后续增量 `panBy`，Android 放置路径应用视口偏移。拖动恢复原预览的世界位置，保留参考线；抬手不会选线、提交或新增历史，缩放中断清除本次平移手势并保留创建步骤。349 项 JVM 测试、两个边界检查、debug 构建及 15 项相关 Android 16 真机回归通过（0 失败/错误/跳过），本次未重跑完整仪器套件。新增真机用例在三个创建阶段逐一断言实际图面偏移、预览和引用不变，继续创建后仅一次撤销；原双指缩放、端点吸附和尺寸驱动用例同时通过。执行：

```powershell
.\gradlew.bat :shared:checkSharedBoundaries :shared:jvmTest :shared-ui:checkSharedUiBoundaries :shared-ui:jvmTest :app:testDebugUnitTest :app:assembleDebug --console=plain
.\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.example.boschlaser.SketchDimensionInstrumentedTest,com.example.boschlaser.SketchViewEditorStateInstrumentedTest' --console=plain
```

2026-10-22 修复尺寸创建中的缩放中断：Android 双指路径改用 `interruptPointerForViewportChange`，尺寸工具保留第一参考线和待放置预览，清除吸附、放大镜和触控状态；缩放尾部事件仍全部吞掉，不能提交标注。显式取消与其他工具继续沿用取消规则。347 项 JVM 测试、两个边界检查、debug 构建和 14 项相关 Android 16 真机回归通过（0 失败/错误/跳过）；本次未重跑完整仪器套件。真机断言实际比例变大/变小，分别在第一参考线后和放置预览阶段连续缩放，接着完成点选，只产生一次可撤销创建。执行：

```powershell
.\gradlew.bat :shared:checkSharedBoundaries :shared:jvmTest :shared-ui:checkSharedUiBoundaries :shared-ui:jvmTest :app:testDebugUnitTest :app:assembleDebug :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.example.boschlaser.SketchDimensionInstrumentedTest,com.example.boschlaser.SketchViewEditorStateInstrumentedTest' --console=plain
```

2026-10-22 新尺寸支持捕捉已放置尺寸线的两个端点刻线。`snapEndpoint` 由当前布局解析精确端点与底层对象引用，不建立尺寸之间的宿主依赖；点选前两端遵守不同对象、平行和有效间距规则，第三次放置也可吸附端点以对齐尺寸线。共享 `snap` 和放大镜展示捕捉位置，取消或完成清除临时状态。345 项 JVM 测试、两个边界检查、debug 构建及 15 项相关 Android 16 真机回归通过（0 失败/错误/跳过）；本次未重跑完整仪器套件。新增真机用例在墙外的旧尺寸端点附近按下，检查实际捕捉坐标和放大镜，依次创建新标注、键盘提交驱动，并验证旧标注引用保持。执行：

```powershell
.\gradlew.bat :shared:checkSharedBoundaries :shared:jvmTest :shared-ui:checkSharedUiBoundaries :shared-ui:jvmTest :app:testDebugUnitTest :app:assembleDebug --console=plain
.\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.example.boschlaser.SketchDimensionInstrumentedTest,com.example.boschlaser.SketchSceneDrawingInstrumentedTest' --console=plain
```

共享 `WallDimension` 保存两个 `DimensionReference` 及标注定位点。引用覆盖墙的物理边/中心线、门窗洞口四边框、柱的边/轴；`WallDimensionGeometry` 统一派生几何、命中和标注布局。共享 `DimensionTool` 负责三次点选，第二参考线按第一参考线的平行方向过滤。`forMovingSelection` 依照已选对象类型和 ID 临时定向驱动，不交换保存引用。`DriveWallDimensionCommand` 按对象分派：墙沿法向移动并以原方向交点规划连接墙与 T 支墙，经 `UpdateWallsCommand` 整体检查洞口；柱整体平移并保留尺寸、旋转；门窗仅改变沿宿主墙的 offset，不截断或脱离宿主。固定参考线位置和最终尺寸均在提交前复核，失败不写状态；成功一次差量撤销。该能力没有引入全局约束求解器、对象自身尺寸变更或永久尺寸锁。

共享场景统一绘制尺寸，Android 仅提供资源、输入及属性意图。标注作为 `SketchDocument.dimensions` 随工程保存；可选 JSON 字段与旧读取器边界见 [工程格式](project-format.md#墙面尺寸的兼容扩展)。

2026-10-22 扩展对象间尺寸引用：341 项 JVM 回归、192 项 Android 16 真机仪器测试、两个分层边界检查及 debug 构建全部通过，失败/错误/跳过均为 0。新增覆盖门窗四边框、矩形柱旋转边/轴、圆柱切线/轴、墙实体中心线、跨类型平行引用组合、门窗沿宿主移动和越界回滚、固定宿主引用拒绝随动、关联删除与撤销、JSON 兼容与非法引用。真机覆盖墙中心到柱中心、墙中心到门框的实际点选创建、对象选择、数字输入驱动、原几何保留及重建后持久化；原墙面用例改为点选洞口外墙段，尺寸测试独立设置自动净距开关并恢复原偏好。执行命令：

```powershell
.\gradlew.bat :shared:checkSharedBoundaries :shared:jvmTest :shared-ui:checkSharedUiBoundaries :shared-ui:jvmTest :app:testDebugUnitTest :app:assembleDebug :app:connectedDebugAndroidTest --console=plain
```

选墙直接编辑净距：`WallDimensionGeometry.forSelectedWall` 在每侧找最近且有投影重叠的平行墙，派生相向墙面引用；选择工具仅在点击图上数字时发出输入意图，底部墙属性不提供净距入口。共享 `WallFaceDistanceDialog` 提供聚焦数值输入、键盘完成与草稿错误显示，Android 将提交交给 `DriveWallFaceDistanceCommand`。该命令原子调整选中墙并保持正式标注，临时标注不进入保存、导出或历史。

同日的选墙输入修复已执行下方完整命令并通过：325 项 JVM 测试、187 项 Android 16 真机仪器测试，失败/错误/跳过均为 0。新增用例通过真实画布选墙、点击临时净距、等待弹窗焦点、键盘输入和完成键提交，断言选中墙移动、对墙固定、连接与门窗保持、关闭页面后的保存结果；另覆盖属性入口、无效草稿、取消及不产生永久标注。首次弹窗自动焦点回归失败后，焦点请求改为等待弹窗自身窗口获得焦点，保留自动聚焦断言。

已放置标注的直接输入：`SelectTool` 在尺寸点击抬手时发出稳定标注 ID 的输入意图，拖动/取消不发出；Android 将其接到聚焦数值弹窗并调用原 `DriveWallDimensionCommand`。已放置标注优先于选墙临时尺寸，数字命中复用共享 `sketchDimensionLabelBounds` 的实际绘制矩形。新增真机回归从保存工程重新打开，点竖向尺寸数字的边缘（细线命中范围以外），覆盖取消/无效输入及键盘提交后参考墙固定、目标墙移动、连接/洞口和持久化保持。

该点击修复实际通过 327 项 JVM 测试、两项边界检查、debug 构建，以及 17 项相关 Android 16 真机回归（0 失败/错误/跳过）。尺寸手势还验证了 3 px 手指抖动仍弹框，实际拖动和取消不弹框。本次未重跑完整仪器套件，执行命令为：

```powershell
.\gradlew.bat :shared:checkSharedBoundaries :shared-ui:checkSharedUiBoundaries :shared:jvmTest :shared-ui:jvmTest :app:testDebugUnitTest :app:assembleDebug :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.example.boschlaser.SketchDimensionInstrumentedTest,com.example.boschlaser.SketchSceneDrawingInstrumentedTest,com.example.boschlaser.SketchViewCommandInstrumentedTest,com.example.boschlaser.SketchViewEditorStateInstrumentedTest' --console=plain
```

2026-10-16 尺寸试用版实际执行以下全量验证：321 项 JVM 测试、185 项 Android 16 真机仪器测试，失败/错误/跳过均为 0，两个边界检查与 debug 构建通过。新增回归覆盖物理墙面与定位线、旋转/反向、矩形及斜接角联动、T 支墙、门窗冲突回滚、撤销、JSON 兼容、真实画布点选和属性输入、交换固定侧、页面关闭持久化，以及正式标注导出/排除未确认预览。没有进行永久尺寸锁或任意墙网全局约束求解验收。

```powershell
.\gradlew.bat :shared:checkSharedBoundaries :shared-ui:checkSharedUiBoundaries :shared:jvmTest :shared-ui:jvmTest :app:testDebugUnitTest :app:assembleDebug :app:connectedDebugAndroidTest --console=plain
```

显示样式调整（2026-10-16）：共享场景不再绘制墙体自动长度文字（包括创建预览和导出），保留正式及临时尺寸。尺寸文字取消背景填充，尺寸线在文字矩形处断开，文字触控范围保持。已通过 327 项 JVM 测试、两个边界检查、debug 构建及 10 项相关 Android 16 真机回归；透明效果用有色底图像素验证，并验证尺寸点击输入和驱动。未重跑完整仪器套件。执行：

```powershell
.\gradlew.bat :shared:checkSharedBoundaries :shared-ui:checkSharedUiBoundaries :shared:jvmTest :shared-ui:jvmTest :app:testDebugUnitTest :app:assembleDebug :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.example.boschlaser.SketchSceneDrawingInstrumentedTest,com.example.boschlaser.SketchDimensionInstrumentedTest' --console=plain
```

自动净距样式进一步简化：仅显示端部刻线，不叠加移动方向箭头。手工标注选中时的方向提示保留；尺寸驱动逻辑未变。共享 UI 边界检查、JVM 测试、debug 构建及 10 项相关真机回归通过，本次未重跑全量仪器测试。执行命令：

```powershell
.\gradlew.bat :shared-ui:checkSharedUiBoundaries :shared-ui:jvmTest :app:assembleDebug :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.example.boschlaser.SketchSceneDrawingInstrumentedTest,com.example.boschlaser.SketchDimensionInstrumentedTest' --console=plain
```

2026-10-20 调整数值输入入口：底部墙属性移除净距条目，选中/放置尺寸不打开底部属性面板。正式和临时尺寸仅通过实际数字矩形发出输入意图，点击细尺寸线只选中或取消选择，不弹框；拖动保持原行为。固定侧交换移入数值弹窗，作为草稿在成功应用时与几何变更一起提交，取消/失败不交换正式引用。328 项 JVM 测试、两个边界检查、debug 构建及 12 项相关真机回归通过（0 失败/错误/跳过）；本次未重跑完整仪器套件。执行：

```powershell
.\gradlew.bat :shared:checkSharedBoundaries :shared-ui:checkSharedUiBoundaries :shared:jvmTest :shared-ui:jvmTest :app:testDebugUnitTest :app:assembleDebug :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.example.boschlaser.SketchDimensionInstrumentedTest,com.example.boschlaser.SketchViewCommandInstrumentedTest,com.example.boschlaser.SketchViewEditorStateInstrumentedTest' --console=plain
```

2026-10-20 对象优先的尺寸驱动：移除所有尺寸的附加移动箭头，包括选中标注与创建预览。仅预先选中尺寸引用的关联墙后点击数字，才发出保持墙选择的输入意图；未选择、仅选标注、柱或无关墙均不激活。`WallDimensionGeometry.forMovingWall` 临时定向引用，`DriveWallFaceDistanceCommand` 原子移动选中的墙并保留原标注引用；取消弹窗中的固定侧交换。通过 329 项 JVM 测试、两个边界检查、debug 构建及 17 项相关 Android 16 真机回归（0 失败/错误/跳过），覆盖左右两侧选择、无关对象拒绝、取消、保存及无箭头像素断言。本次未重跑完整仪器套件。执行：

```powershell
.\gradlew.bat :shared:checkSharedBoundaries :shared-ui:checkSharedUiBoundaries :shared:jvmTest :shared-ui:jvmTest :app:testDebugUnitTest :app:assembleDebug :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.example.boschlaser.SketchDimensionInstrumentedTest,com.example.boschlaser.SketchSceneDrawingInstrumentedTest,com.example.boschlaser.SketchViewCommandInstrumentedTest,com.example.boschlaser.SketchViewEditorStateInstrumentedTest' --console=plain
```

## 模块与依赖方向


```text
:app ───────────────→ :shared-ui ───────────────→ :shared
Android 宿主与适配       Compose 展示与绘制          纯 Kotlin 语义与业务
  └──────────────────────────────────────────→ :shared
```

| 模块 | 负责 | 边界 |
| --- | --- | --- |
| `:shared` | 文档、数学、拓扑、命令、编辑器、工具、协议、设备抽象、测量和项目服务 | 不依赖 Android、Java、Compose、org.json 或宿主 |
| `:shared-ui` | 项目/测量/设备屏幕，两类编辑页面、工具栏、属性、设置、Canvas、放大镜与绘制布局 | 依赖核心和 Compose；接收状态、文案、样式、绘制 slot 和动作回调，不持有平台仓库或句柄 |
| `:app` | Activity、导航、权限、生命周期、MotionEvent/缩放、字体/位图、资源、SQLite、文件/URI、分享、旧格式兼容 | 调用共享 API；平台实现集中在 `platform/`，不另建几何算法或文档所有权 |

共享模块使用 Android KMP library 插件，Android 应用使用 `com.android.application`。保留 `:app` 模块名与现有 applicationId。版本以 `gradle/libs.versions.toml` 为准；当前 Kotlin/Compose compiler 为 2.2.20，Compose Multiplatform 为 1.9.3，AGP 为 9.3.2。

`:shared:checkSharedBoundaries` 与 `:shared-ui:checkSharedUiBoundaries` 均接入各模块 `check`。它们拒绝违规 import，并报告文件/行号；已用临时 Android、Java、Compose 和宿主 import 验证拒绝行为，探针随后清理。检查覆盖 import，不代替职责审计或平台运行验证。

## 核心包与入口

所有共享核心入口位于 `shared/src/commonMain/kotlin/com/example/boschlaser/shared/`。

| 包 | 主要入口 | 职责 |
| --- | --- | --- |
| `model` | `SketchDocument`、`Wall`、`Opening`、`Column`、`Project` | 不可变语义数据、稳定 ID 与引用 |
| `geometry` | `Geometry`、`WallGeometry`、`OpeningGeometry`、`ColumnGeometry`、`WallNetworkGeometry` | 纯计算与可丢弃派生图形 |
| `topology` | `SketchTopology`、`WallEditPlanner` | 端点节点、连接墙联动、T 形附着规划 |
| `command` | `SketchCommand`、`CommandManager` | 原子永久修改、实体差量历史、顺序恢复 |
| `editor` | `SketchEditor`、`EditorState`、`WallEditSession`、`SnapEngine`、`HitTestEngine`、`Viewport` | 文档所有权、手势状态、选择、预览、吸附、命中与坐标映射 |
| `tool` | `SelectTool`、`WallTool`、`ColumnTool`、`OpeningTool`、`MeasureTool`、`SplitTool` | 平台无关的指针语义与工具分派 |
| `annotation` | `AnnotationDocument`、`AnnotationEditor`、`AnnotationHitTest` | 独立照片标注文档、创建/拖动/双击状态、命令与差量历史 |
| `ble` / `bosch` | `BleTransport`、`BoschLaserDevice`、`BoschProtocol`、`MtFrameAssembler` | 传输接口、设备业务、二进制协议与分包重组 |
| `measurement` | `LaserDevice`、`MeasurementService`、`MeasurementRepository`、`MeasurementCsv` | 独立测量流、记录、仓库接口与 CSV |
| `storage` | `ProjectService`、`ProjectRepository`、`ProjectSerializer`、`ProjectMigration` | 项目分区操作、目录流、语义 JSON 与旧数据纯转换 |

## 文档、预览与历史的所有权

`SketchEditor` 是草图文档的唯一编辑入口，`AnnotationEditor` 是照片标注文档的唯一编辑入口。正式文档、预览文档和交互状态各自分离：

```text
Android 指针/属性意图 → Editor / Tool → Command → 正式 Document
                            └────────→ 临时预览 / EditorState
Document + EditorState → 共享 Canvas / DrawScope → 屏幕、放大镜或位图
```

- 永久增删改通过命令；拖动只更新预览，抬手一次提交，取消/双指缩放/页面暂停回滚未确认编辑。
- 无效或无变化操作不写历史、不清除 Redo；Undo/Redo 保存实体差量及必要的绘制顺序，不保存完整文档栈。
- 选择以稳定实体 ID 表达；提交、取消、删除和历史操作后清理悬空实体/控制点引用。
- Android View 中的墙、柱、门窗和四类照片标注集合均为只读投影；不维护第二套可变文档、拖动目标或历史。
- Canvas 快照只封装不可变绘制输入。绘制、命中、属性草稿、面板收起或焦点变化都不修改正式文档。

## 几何与编辑不变量

草图世界坐标和长度使用有限 Double 毫米。墙 `start/end` 表示当前定位线，墙体中心线、边线、多边形与洞口即时派生。门窗保存宿主墙 ID、毫米中心偏移和独立翻转属性；照片点使用独立的归一化 Double，不换算为毫米。

共享墙节点按端点接触识别；内部数学交叉不自动创建连接。显式 `JoinWallsCommand` 合并指定端点，`SplitWallCommand` 产生新端点。节点 ID 来自稳定端点引用，与坐标移动和文档顺序无关。T 形附着判断与移动使用各墙定位线。

所有墙编辑先规划连接墙变化，再由 `UpdateWallsCommand` 整体检查门窗固定端距离和容纳范围。门窗不按墙长比例移动，失败不得修改任何墙、门窗或历史。`ChangeOpeningPropertiesCommand` 改宽时保留原中心，仅在无法容纳时向内夹取；翻转保留精确宽度、偏移和宿主。柱尺寸/旋转命令保留原中心及未修改的属性。

`HostedOpeningEditMatrixTest` 覆盖 24 个方向、三种定位线、两种固定端、连接墙和 T 宿主，以及 Trim/Extend 的九种定位线组合；断言毫米距离、实际位置、边界墙不变、冲突回滚和精确 Undo/Redo。它证明核心命令行为，不代替触控与像素验收。

## 工具与 Android 输入边界

`SelectionGesture` 在核心捕获按下时的稳定目标及 Double 指针；后续选择刷新不能改变拖动目标。Android 仅提供屏幕旋转柄命中 ID、MotionEvent、像素容差和缩放焦点。实际指针直接通过共享 `Viewport` 映射到 Double 世界点，不先转成旧 Float 世界点。

Trim/Extend 使用共享 `WallEditSession` 保存首墙 ID 和保留侧点击点。第二击排除首墙；成功执行一条命令并结束会话，失败清除本次墙对并等待重选，取消/恢复/工具切换清理会话。Android 只观察会话并显示资源提示。

草图测距两次点击确定吸附后的 Double 线段，第三次重新开始。辅助线属于 EditorState，不进入工程、导出或历史；测距撤销仅清除本次辅助线。拆墙命中可见墙身后投影到定位线，执行一条命令；两段至少 100 mm，不得切穿门窗，后半段洞口改挂新墙且世界位置不变。

照片创建、拖动、控制点、命中和面积边双击均由 AnnotationEditor/AnnotationHitTest 处理。Android 提供单调事件时间和资源容差；双击不依赖系统日期或旧列表索引。`PhotoDrawingViewport.photo` 统一 Double 逆映射：创建显式夹取，整体拖动/命中保留越界指针，空视口拒绝输入。`AnnotationNotice` 显式指定 Toast 策略，Activity 不解析本地化文案前缀。

## 共享 UI 与绘制

共享 UI 位于 `shared-ui/src/commonMain/kotlin/com/example/boschlaser/ui/`。Android 根容器适配系统 Insets，并承载同一 Compose 组合；SketchView/ImageAnnotationView 为 AbstractComposeView，输入继续经 pointerInteropFilter 桥接。

| 内容 | 共享入口 | Android 适配 |
| --- | --- | --- |
| 首页 | `HomeScreen`、`ProjectScreen`、`MeasurementScreen`、`DeviceScreen` | 生命周期收集状态、导航、缩略图、设备权限与文件选择 |
| 草图页面 | `SketchScreen`、工具栏/动作、属性/设置、`SketchCanvas` | 资源、Insets、键盘、指针/缩放、属性意图 |
| 照片页面 | `AnnotationScreen`、工具栏/放置选项、属性、`AnnotationCanvas` | 图片、字体、资源、键盘、指针及系统操作 |
| 草图绘制 | `drawSketchScene`、墙网络/门窗/柱/标签/网格、放大镜 | `AndroidSketchDrawingStyle`、字体解析器、`AndroidCanvasDrawing` |
| 照片绘制 | `drawAnnotationScene`、形状/标签/放大镜、照片视口 | `AndroidAnnotationTextPainter`、位图回调、`AndroidCanvasDrawing` |

屏幕、放大镜和导出复用场景绘制；预览不覆盖同 ID 正式实体，导出隐藏选择、网格和未确认预览。墙网络布尔合并/扣洞在 UI 层操作派生图形，不反写语义数据。绘制前保留 Double，只在屏幕/Canvas 边界转为 Float。

草图画布保持全窗口视口；照片画布占据工具栏下方剩余空间。两类属性面板覆盖画布，不因显示属性重新缩放文档。面板只拥有展示动画；草图取消拖动始终回弹，照片手柄取消超过阈值可收起，两者不能混用策略。详细尺寸、图层与手势规则见对应行为规范。

共享 `sketchContentBounds` 和 `sketchExportViewport` 计算导出范围与相机；Android 只创建 Bitmap 和桥接 Canvas。内容范围、相机及标签不经旧 Float 文档重建。

## 设备、测量与持久化

```text
BoschBleManager → BoschLaserDevice : LaserDevice → BleTransport ← AndroidBleTransport
                         ↓
                 MeasurementService → MeasurementRepository ← Android SQLite
                         ↓
                 同一记录流 → 首页 / 草图 / 照片
```

Bosch 协议、CRC、初始化、单位换算和帧重组在核心；Android Transport 负责扫描/GATT/权限、确认、超时、会话隔离与清理，不解析设备业务帧。测量不直接修改 View 或墙；墙属性不再提供选择近期测量赋墙长入口，精确墙长通过同墙两端尺寸输入调用共享命令。详见 [BLE 边界](ble-architecture.md)。

`MeasurementServices` 与 `ProjectServices` 持有应用 Context 和 scope；页面销毁不取消已接受的持久化工作。UI 在生命周期内订阅。SQLite 版本 1→2 保留原记录并迁入按 ID 保存的毫米记录；CSV 捕获完整 Double 快照。新增记录按 ID 判断，不按数值相等猜测；同 ID 刷新、删除和清空不覆盖用户手动标签。详见 [测量仓库](measurement-repository.md)。

`ProjectService` 串行修改项目分区，保留其他分区、测量和资产，只过滤失效绑定。`ProjectSerializer` 负责四份严格语义 JSON；AndroidProjectArchive 负责 ZIP/UTF-8/路径/大小/资产校验，AndroidProjectRepository 同步临时文件后原子替换。保存失败保留旧工程，成功后发布目录状态。系统 URI、照片字节、分享与文件生命周期由平台适配。

旧工程迁移保留原 JSON、照片与预览，使用确定性名称空间和持久迁移记录；单个失败不阻塞其他项目，删除后不会从旧原件复活。新编辑仅写统一 `.lsk`。Document Spec 是桌面交换边界，不要求共享整个移动 Engine；格式和独立样本见 [工程格式](project-format.md)。

## 兼容适配与后续修改规则

`SketchCoreAdapter` / `SketchCoreProjection` 隔离旧 Float、中心线和比例模型与新 Double、定位线和毫米偏移。投影弱缓存保留旧值及圆柱兼容字段，不成为可编辑文档。旧签名供兼容调用与原回归使用；新正式输入、属性、持久化和绘制使用共享实体，不能把旧投影反写未改变的语义字段。

新增可复用规则放入核心命令/工具/几何；Compose 类型只进入 UI。平台新增能力实现共享接口或宿主回调，不让核心导入平台类。用户文案、颜色和样式取自宿主资源。移动端保持轻量模型、基础拓扑与现场编辑，CAD 内核、约束求解、房间生成和 DXF/IFC/BIM 转换留给后续桌面范围。

## 验证与尚缺证据

本机 Android 范围检查：

```powershell
.\gradlew.bat :shared:checkSharedBoundaries :shared-ui:checkSharedUiBoundaries :shared:jvmTest :shared-ui:jvmTest :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --console=plain
```

共享 commonTest 通过 JVM target 执行，不依赖设备。仪器测试包构建仅证明编译/打包成功；需连接 Android 12+ 设备后运行：

```powershell
adb devices
.\gradlew.bat :app:connectedDebugAndroidTest
```

已在 Android 16 真机执行触控、Canvas 像素、页面重建、SQLite 升级和 FileProvider URI 导入导出仪器测试；这不覆盖外部分享目标的完整系统选择流程。完整 176 项仪器套件已通过；真实权限拒绝/授予、扫描、GLM 单次测量、断开与重连已经确认；连续测量按用户最新要求排除，未声明实机通过。性能、单手和平板体验也不能由单元测试证明。iOS/桌面宿主及云服务不纳入本轮。逐项状态见核对记录，测试入口与运行约束见 [贡献指南](../CONTRIBUTING.md)。

首页由单个 Android `ComposeView` 承载共享 `HomeScreen`；共享层安排滚动内容、新建入口、设备悬浮按钮和可切换面板，并处理系统栏留白。文案、按钮颜色与设备图标由 Android 资源注入，项目缩略图和 Flow 生命周期收集仍由 Android 适配器管理。`HomeScreenInstrumentedTest` 覆盖滚动后操作入口、设备面板切换及内容滚动位置保留，已在 Android 16 真机通过；完整套件状态以核对记录为准。
