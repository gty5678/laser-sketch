# 改造方案核对记录

依据根目录《改造方案.md》1～44 节。当前实施范围以用户补充要求为准：只适配 Android，共享层为未来跨平台准备，不实现或验收 iOS、桌面 CAD 宿主。

本表区分源码对应、自动化验证与硬件证据。当前 Android 架构改造、299 项单元测试与 176 项完整仪器测试通过；单次 GLM 测量及重连已经实机确认。用户最新要求不再处理连续测量，因此连续模式不作为本轮完成条件；其已观察到的限制保留在文末记录。

## 章节与证据

| 方案节 | 要求 | 当前对应与核对结果 |
| --- | --- | --- |
| 1 | 测量、基础草图、照片、项目与导出；重型 CAD 留桌面 | Android 入口和共享核心已接入；单次 GLM 测量、权限与重连已验收，草图/照片/项目/导出路径有完整仪器证据。上传/导出按现有系统分享与 .lsk 导出路径核对，未引入云服务。 |
| 2 | KMP、共享 UI、平台适配分层 | `:shared` 只依赖协程/JSON，`:shared-ui` 依赖核心和 Compose；Android 适配在 `app/platform`。核心与共享 UI 均有接入模块 check 的 import 边界检查；临时违规 import 负向验证已证明拒绝 Android/Java/宿主反向依赖，并报告文件/行号。首页已由单个 ComposeView 接入共享 HomeScreen，平台只组装资源、服务适配和操作回调；调用职责的源码证据见第 43 节规则表。 |
| 3 | 模型、几何、命令、编辑器、服务、平台、文档目录 | 对应包已建立；保留 `:app` 模块名，共享 UI 独立模块；iOS 目录按用户要求不实现。命名差异不作为功能验收证据。 |
| 4 | 自有 Vec2/Segment2/Line2/Rect2，毫米 Double | `shared/geometry/Geometry.kt`；核心无 Android/Compose import。Android 旧 Float 投影仅用于兼容；实际指针直接映射 Double 世界点，属性读取原共享实体，避免窄化。新增大坐标指针映射仪器断言已在此前真机运行中通过。 |
| 5 | 纯数学距离、投影、交点、偏移 | `Geometry.kt` 与 `GeometryTest` 已核对对应接口及基本正常/退化输入；不认识 UI 或撤销。 |
| 6 | Wall 保存定位线、厚度与对齐 | `model/Wall.kt`；中心线由派生几何计算，旧文件中心线转换在 Android 兼容适配。 |
| 7 | WallGeometry 派生形状、不持久化多边形 | 已核对 WallGeometry 派生中心线/边线，ProjectSerializer 只写语义墙字段；独立手写样本验证 LEFT 定位线语义。 |
| 8 | 门窗 Host + 毫米 Offset，随宿主编辑 | Opening 保存稳定宿主 ID 与毫米中心偏移；独立手写格式样本验证 1800 mm 偏移不会被解释为归一化比例。HostedOpeningEditMatrixTest 已覆盖 24 个方向、三种定位线、两种固定端、连接墙及 T 宿主；Trim/Extend 覆盖九种定位线组合，断言实际位置、固定端毫米距、冲突原子性与 Undo/Redo。 |
| 9 | 矩形柱、圆柱与旋转 | `model/Column.kt`、`ColumnGeometry`、柱属性命令；旋转矩形/圆柱命中测试通过。 |
| 10 | 统一 SketchDocument，View 不拥有可变实体 | `SketchEditor`、`SketchDocument`、`SketchCoreProjection`；View 集合为只读投影。 |
| 11 | L/T/简单 X/端点合并；相交不自动连接 | `SketchTopology`、`WallEditPlanner`、`JoinWallsCommand`；新增显式交叉拆分→T→四向端点及撤销回原数学交叉、端点合并门窗冲突原子回归已通过。 |
| 12 | 增删移动、尺寸、Trim/Extend/Join/Split 全走命令 | `command` 包；移动门窗/柱用 Update 命令复制原实体；Android 实际入口已对应创建/删除/拖动、墙属性、柱属性、门窗属性、Trim/Extend 和拆墙命令；门窗改宽中心夹取已迁入 ChangeOpeningPropertiesCommand。 |
| 13 | CommandManager 撤销/重做，避免快照历史 | `CommandManager` 保存实体差量及顺序变化；不保存完整文档栈，现有单元测试通过。 |
| 14 | Editor 管理工具、选择、预览、命令、吸附、视口交互 | `SketchEditor` 与 `EditorState`；旋转/拖动目标、首墙与保留点均由核心持有。 |
| 15 | 选择、墙、门窗、柱、Measure；后续 Trim/Extend/Split 工具 | 选择/放置工具与 Trim/Extend 共享会话已接入；按用户确认新增 MeasureTool/ SplitTool 与 Android 工具栏入口、资源提示、共享测距辅助线。新增核心精度/吸附/取消/失败/撤销回归通过；真实入口、触控、自动保存与辅助线绘制回归已在此前真机运行中通过；最终 176 项完整仪器套件已通过。 |
| 16 | 创建预览与永久文档分离，抬手原子提交 | `SketchEditor.preview`、工具状态与 CommandManager；取消、失败、一次撤销回归通过。 |
| 17 | 网格、端点、中点、交点、轴向、15°吸附 | `SnapEngine` 支持配置；Android 保留原启用项。网格已改为 Double 索引，保留半格向正无穷舍入；超 Int、大整数、极小步长和不可表示结果回归通过。 |
| 18 | HitResult 包括墙、定位端点、门窗和柱 | `HitTestContext` 与类型化 `HitResult.WallEndpoint` 已补齐；SelectTool 使用统一查询，优先级/独立容差回归通过。 |
| 19 | Viewport 世界/屏幕映射独立 | `editor/Viewport.kt` 使用 Double；Android 输入直接映射 Double 世界点；Float 相机、缩放焦点和屏幕平移仅属于像素适配，不反写文档。大坐标映射仪器断言已在此前真机运行中通过。 |
| 20 | 共享 Canvas 只输入与绘制，不操作文档/历史 | `SketchCanvas`、`drawSketchScene` 与 Android 输入适配；像素测试直接使用共享绘制；修正背景与抗锯齿采样后的相关真机回归通过，最终 176 项完整仪器套件已通过。 |
| 21 | BoschBleManager 拆协议、设备与 Transport | 已核对 BoschBleManager 只协调 Android 资源/生命周期，协议帧由共享设备解析，GATT 在 AndroidBleTransport；用户手选 GLM 后连接就绪，单次测量、保存设备重连、首页重建与设备关机重开后的自动恢复已确认；连续模式按最新要求排除。 |
| 22 | 共享 BleTransport，Android BLE 实现 | 已核对 raw notifications、会话 ID、扫描/连接/订阅/写入、超时与关闭路径；异步扫描结果/服务发现/旧通知及订阅/写入已补 SecurityException 边界，统一失败并释放等待；新增权限异常模拟仪器回归已在此前真机运行中通过，真实权限拒绝/授予及 GLM 单次测量与重连已确认；运行中系统撤权边界由模拟异常回归覆盖，未声称实际撤权硬件通过。iOS 排除。 |
| 23 | BoschProtocol 共享请求、解析和 CRC | `bosch/BoschProtocol.kt` 含请求构造、CRC 与解析；已核对请求独立字节样本、三个参考位置、加减模式、米→毫米、非有限/越界/截断/CRC 错误、无符号连续值、分包/合包/超时恢复断言。 |
| 24 | LaserDevice 统一设备抽象与连续测量 | 已核对 LaserDevice 三个命令映射、订阅与初始化确认、取消释放、重连丢弃旧帧、关闭不取消拥有者 scope；真实拒绝/授予附近设备权限、扫描、连接、单次测量及中断重连已确认；超时/取消由共享假传输测试覆盖。连续模式按用户最新要求排除，不新增入口或继续实机排查。 |
| 25 | Measurement 独立几何，关联使用 ID | Project 模型校验测量/实体 ID 与属性类型，独立格式样本验证绑定与测量精度；ProjectService 删除几何时只过滤失效绑定，保留测量。Android 近期测量直接保留 Double，通过 ChangeWallLengthCommand 赋值；属性初始草稿已改读共享实体，避免 Float/Int 窄化。新增显示回归已在此前真机运行中通过。 |
| 26 | .lsk 包含四类 JSON 与 assets | 已核对四份 JSON、assets 的双向一致性、严格 UTF-8、重复条目/路径/大小限制；AndroidProjectArchive 与 Repository 的保存/导出/重开测试通过。FileProvider URI 导入导出回归已在此前真机运行中通过；外部分享目标的完整系统选择流程仍待人工验收。 |
| 27 | Manifest format/formatVersion/projectId/unit | 已核对 Manifest format/版本/单位的独立错误类型；旧版本拒绝、迁移原件保留、名称空间及删除后不复活测试通过。 |
| 28 | 文件保存语义墙，桌面重建几何 | 已核对 Serializer 墙/门窗/柱字段，JSON 不写派生多边形、选中或预览；独立格式样本验证定位线、毫米偏移和稳定 ID。桌面宿主不在本轮范围。 |
| 29 | 共享 Repository 接口，Android 存储实现 | ProjectRepository 接口共享，Android 文件仓库采用散列逻辑 ID、fd.sync 与原子移动；失败替换保留旧项目、并发独立项目和迁移幂等测试通过。SQLite 1→2 保留源表、毫米扩精度复制、按 ID 删除、事务提交后发布已核对；同时间记录、失败插入与升级回滚仪器断言已在此前真机运行中通过。 |
| 30 | 独立标注文档/编辑器；照片平台适配 | `annotation` 与 Android 图片/文件适配已接入；照片坐标逆映射复用共享 Double 视口，原始越界与创建夹取规则回归通过；提示改为显式标记且文案在资源，不再解析中文前缀。真实输入、字体和位图相关仪器测试已执行，照片暂停保存夹具修正后 9 项集成回归通过；最终 176 项完整仪器套件已通过。 |
| 31 | 建立 shared，Android 使用，测试通过 | Android/JVM 编译和共享测试已通过；Android 仪器测试已执行并完成针对性修复验证；最终 176 项完整仪器套件已通过，GLM 单次测量与重连实机验收通过。 |
| 32 | SketchView 基础数学迁出 | 旧命中/投影与绘制反射辅助函数已删除；内容范围在共享 geometry，导出相机在 shared-ui；门窗属性中心夹取已迁到共享命令；剩余计算为像素视口、缩放焦点、旋转柄布局和平台默认参数验证。 |
| 33 | 文档拥有墙、柱、门窗 | 已核对同节 10；旧格式迁移适配已核对，原件及照片/预览字节保留，失败无半成品且不阻塞其他工程。 |
| 34 | 命令替换快照历史，再迁 Trim/Extend/Join | 已核对同节 12～13；终态中没有 ArrayDeque<SketchState> 历史。 |
| 35 | 工具、选择、触摸语义、Snap/Preview 离开 View | 已迁移核心手势与多步会话；Android 保留 MotionEvent/ScaleGestureDetector/坐标映射。剩余可变字段为像素相机/触控检测、资源设置、平台默认参数及回调错误标记；文档/选择/创建预览/拖动/多步首墙状态均来自 Editor。 |
| 36 | 项目、测量、设置、属性、工具栏和 Canvas 共享 | 对应 shared-ui 组件已接入；新增 HomeScreen 统一首页滚动布局、新建/导入操作和设备面板，共享组件交互、布局和输入测试已在此前真机运行中执行；首页及编辑页针对性回归通过，最终 176 项完整仪器套件已通过。 |
| 37 | iOS App 与平台适配 | 按用户要求排除；只保留已有 target 声明，不要求 iOS 编译或实现。 |
| 38 | 分层迁移并始终保持 Android 可运行 | 当前 Android 构建通过；真机已执行仪器测试及失败项修复回归；最终 176 项完整仪器套件通过，GLM 单次测量与重连已确认。 |
| 39 | 几何、墙、门窗、命令、拓扑、协议回归矩阵 | 已按第 39 节逐项核对 GeometryTest（交点/平行/投影/距离/偏移）、WallGeometryTest（定位线/厚度/斜墙）、HostedOpeningEditMatrixTest（位置/宽度/旋转/改长/原子失败）、CommandManagerTest（增删/Undo/Redo/改长）、WallEditingCommandsTest 与 SplitWallCommandTest（L/T/合并/显式 X）、BoschProtocolTest（独立帧/CRC/解析）。Canvas/触摸已执行仪器测试及修复回归，最终 176 项完整仪器套件通过；GLM 单次测量与重连已确认。 |
| 40 | 不引入 CAD 内核/求解器/BIM 等 | 已核对共享模块仅使用协程/JSON/Compose 等现有依赖；轻量数学/端点拓扑在核心，可丢弃 Path 合并/扣洞在共享 UI，未引入 CAD 内核、求解器、BRep/NURBS、BIM 或 DXF 编辑。 |
| 41 | 快速测量、触控、照片、备注、项目同步、平板 | 保留现有现场测量、触控、照片备注及项目导出；本轮没有新增云同步服务。单次测量已实机确认，页面约束/覆盖/输入有仪器证据；性能、单手与真实平板体验未做专项认证，不宣称已经证明这些产品体验目标。 |
| 42 | UI→Editor→Engine；设备/持久化各自适配 | 已核对共享 UI 不直接持有 Editor/Repository 或执行命令，宿主将意图交给共享 Editor/服务；设备与持久化实现注入共享接口。正式文档/状态/派生绘制的所有权与流向已集中写入 architecture.md，运行路径已有真机仪器证据，最终 176 项完整仪器套件已通过，GLM 单次测量与重连已实机确认。 |
| 43 | 六条架构规则 | 六条规则源码核对已完成，证据见下文；Android 176 项完整仪器套件已通过，GLM 单次测量与重连已独立实机确认。 |
| 44 | Shared Mobile Application + Light Engine + Semantic Document | Android 构建、299 项单元测试及 176 项真机仪器测试已通过；当前 Android 范围与单次 GLM 测量/重连验收完成；连续模式、iOS 和桌面宿主按范围排除。 |

## 当前执行证据

最近实际执行并通过（共享核心 211 项、共享 UI 42 项、Android JVM 46 项，共 299 项单元测试）：

```powershell
.\gradlew.bat :shared:checkSharedBoundaries :shared-ui:checkSharedUiBoundaries :shared:jvmTest :shared-ui:jvmTest :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --console=plain
```

此前已连接 Android 16 真机执行完整仪器测试和修复后的针对性回归；当前手机断开，逐项结果以文末最新运行记录为准。Bosch GLM 实际权限、连接、测量和重连硬件验证尚未完成。

首页整页迁移后实际执行并通过：

```powershell
.\gradlew.bat :shared:checkSharedBoundaries :shared-ui:checkSharedUiBoundaries :shared-ui:jvmTest :app:assembleDebug :app:assembleDebugAndroidTest --console=plain
```

## 第 43 节六条规则的源码证据

| 规则 | 已检查的权威来源 | 结论与证明范围 |
| --- | --- | --- |
| Engine 不依赖 Android | shared/build.gradle.kts、commonMain import 检查、Android/JVM 编译 | 核心依赖仅协程和 JSON；当前源码没有平台 import。检查器只扫描 import，不是完整静态依赖分析。 |
| Engine 不依赖 Compose | shared/build.gradle.kts、核心几何/编辑器源码、shared-ui/build.gradle.kts | Compose 依赖和绘制实现位于 shared-ui；核心使用自有 Vec2/Segment2 等数学类型。 |
| Document 保存语义 | SketchDocument、AnnotationDocument、ProjectSerializer 的显式编码字段及独立 JSON 测试 | 墙定位线、厚度、对齐、洞口毫米偏移、柱参数和照片比例坐标参与保存；轮廓、视口、预览和历史不参与。 |
| 永久修改通过 Command | SketchEditor.execute/preview/commitEdit、CommandManager、AnnotationEditor.execute/preview/commitEdit；两个 Android View 的调用与两个 Activity 的保存入口 | 正常编辑通过命令应用；拖动暂存预览并一次提交差量历史。restore 仅承担工程恢复或更换照片时初始化文档，清空历史。Activity 保存 snapshotDocument，即已确认文档。 |
| 桌面共享文档规范 | docs/project-format.md、ProjectSerializer、AndroidProjectArchive、独立格式样本 | .lsk 保存稳定 ID 和语义字段，桌面无需引用移动编辑器。桌面导入器本身不在本轮范围，尚无跨宿主运行验收。 |
| 保持移动端轻量边界 | 共享模块依赖、Geometry/WallGeometry/WallEditPlanner、docs/geometry-engine.md | 当前仅基础数学、端点/T 关系规划和轻量编辑；没有 CAD 内核、约束求解器或 BIM 生成。未来新增需求仍须按此边界评审。 |

本轮完整本机验证再次通过：核心 211、共享 UI 42、Android JVM 46，共 299 项，失败/错误/跳过均为 0。检测到 Android 16 真机后，首次完整仪器测试在照片角度输入与后续画布用例停留，21/173 后人工中止，因此首次整套结果未通过。两个用例单独运行均通过；临时线程栈将组合停留定位到 ActivityScenario.close 等待跨进程 EmptyActivity 回调，目标主线程空闲。移除重复 finish 仍不能消除另一处 close 等待，因此清理改为直接 finish 目标 Activity、最多 5 秒等待并断言 DESTROYED、再 close 已销毁的 Scenario；全部业务断言保持原样。临时诊断代码已移除。

修复后以下组合真机验证实际通过 11/11（0 失败、0 跳过）：

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.example.boschlaser.AnnotationActivityProjectInstrumentedTest,com.example.boschlaser.AnnotationCanvasInstrumentedTest' --console=plain
```

随后完整运行在无前台窗口的文字绘制测试阶段再次停留。系统 FreecessController 日志明确记录测试宿主与辅助进程被 Samsung 后台策略冻结；对同一仍在运行的任务恢复前台窗口后，13 项组合立即继续并通过，说明停留不能归为像素断言失败。框架 sticky unfreeze 和临时电池优化白名单不足以解除厂商冻结，白名单已恢复，进程已结束，未保留系统设置变更。

新增仅用于仪器测试的 ForegroundTestHostRule，为原生绘图/输入测试提供前台 ComponentActivity，并在清理时确认 DESTROYED；不更改应用后台行为、权限或业务断言。临时线程诊断全部移除。下列长序列在无人工介入、无电池白名单情况下实际通过 13/13，耗时 36 秒：

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.example.boschlaser.AnnotationActivityProjectInstrumentedTest,com.example.boschlaser.AnnotationCanvasInstrumentedTest,com.example.boschlaser.AnnotationLabelDrawingInstrumentedTest' --console=plain
```

前台宿主修复后的完整运行已越过文字绘制并推进到草图集成，报告 88 项完成、0 个业务断言失败；因再次观察到测试清理触发厂商冻结而中止。运行器随后将被中止的在途用例记为失败，不能把这次整套结果记为通过，也不能据此判定该用例的业务行为错误。

将照片测试已验证的销毁等待提取为 TestActivityLifecycle.finishAndCloseScenario，共享给 Compose 页面清理、草图集成和 ForegroundTestHostRule；两个 Canvas 测试也显式持有并清理 ActivityScenario。目标是先确认目标页面已销毁，再释放 Scenario，避免后续规则重复关闭时启动辅助任务。业务断言与超时标准没有放宽。

以下 17 项真机针对性测试通过（0 失败、0 跳过），耗时 54 秒：

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.example.boschlaser.SketchActivityProjectInstrumentedTest,com.example.boschlaser.AnnotationPropertySheetInstrumentedTest,com.example.boschlaser.HomeScreenInstrumentedTest' --console=plain
```

完整运行随后正常结束，173 项中 169 项通过、4 项失败（0 跳过）。逐项检查发现：草图属性面板把 Compose 已消费的取消抬起当作正常释放，已改为取消始终回弹；放大镜背景和墙端点断言原采样落在网格线及抗锯齿边缘，现移到对应纯色区域；柱自由移动用例改用屏幕可精确表示的四分之一像素坐标，避免 MotionEvent 亚像素量化，仍验证网格开启时保留非网格小数世界坐标。断言精度和容差没有放宽。

修复后的四组仪器回归实际通过 11/11（0 失败、0 跳过）；随后分层检查、299 项单元测试与 Android 构建再次通过；完整仪器测试在照片暂停场景再次遭遇 Samsung 后台冻结而中止，不能记为全套通过。

四个照片暂停保存场景改用 debug 专属 LifecyclePauseActivity 透明页面，保持应用进程前台可见，且显式断言原页面处于 STARTED，实际触发 onPause 后再检查持久化。该页面不进入 release，不改变应用权限和正式生命周期策略。修改后 AnnotationActivityProjectInstrumentedTest 全部 9 项实际通过（0 失败、0 跳过）。

最终全量命令再次启动时，自动审批因账户用量上限无法完成审核，命令没有执行；这是审核服务限制，不是安全否决。最终 173 项全量通过仍待确认。用户已确认有 Bosch GLM 可开启蓝牙验收，实际权限、连接、测量和重连尚待执行；不记录设备标识。


最新重试中审批恢复，以下命令实际执行：

```powershell
.\gradlew.bat :shared:checkSharedBoundaries :shared-ui:checkSharedUiBoundaries :shared:jvmTest :shared-ui:jvmTest :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:connectedDebugAndroidTest --console=plain
```

边界检查、299 项 JVM 测试和两个 Android 构建任务通过；connectedDebugAndroidTest 因 `No connected devices!` 失败，未启动仪器测试。ADB 随后确认在线设备与未授权设备数量均为 0。已请求重新连接 Android 手机并准备 GLM，最终全量及硬件验收等待设备就绪。此次阻塞来自设备连接，不再是审批用量限制。


用户重新连接设备后，完整 173 项仪器测试实际运行结束：172 通过、1 失败，0 跳过。失败位于照片连续创建集成场景的删除后按钮隐藏断言；单独运行原场景通过。新增固定 320 dp 高属性面板的布局回归，稳定复现点击删除按钮被下方属性面板截走（删除回调计数 0）。AnnotationScreen 将侧边动作层放到属性面板之上，保持原按钮位置和照片视口，并同步行为规范。修复后 AnnotationScreenInstrumentedTest 与 AnnotationActivityProjectInstrumentedTest 共 11 项通过，0 失败/跳过。最终完整套件现为 174 项，正在重跑。


照片侧边动作遮挡修复后，全量命令实际通过：299 项 JVM 单元测试、174 项 Android 16 仪器测试均为 0 失败/错误/跳过，两个边界检查与 Android 构建通过。

随后开始 GLM 实机验收：真实拒绝附近设备权限后首页正常，设备面板显示权限提示；再次请求并允许权限后扫描成功返回候选，但手工候选弹窗同时设置 message/items 导致列表被正文隐藏。已提取 AndroidBleDevicePicker，新增虚构候选的显示/索引/取消仪器回归，稳定复现列表不可见；删除冲突正文并将标题/取消文案资源化。新增用例后完整套件为 176 项，最终结果待收集；GLM 实际连接、测量和重连尚未证明成功。


AndroidBleDevicePickerInstrumentedTest 两项回归实际通过，0 失败/跳过；列表可见性通过真实 Dialog 窗口检查，选择与取消通过原生 ListView/Button 点击契约验证，不能据此代替窗口坐标触控验收。用户再次确认 GLM 蓝牙开启且未被其他连接占用。包含新增用例的 176 项完整套件已启动。


候选选择器修复后的最终全量命令实际通过，耗时 3 分 35 秒：核心 211、共享 UI 42、Android JVM 46，共 299 项单元测试；Android 16 完整仪器测试 176 项，失败/错误/跳过均为 0。两个边界检查与两个 Android 构建任务通过。已重新安装最终 debug APK 并继续 GLM 硬件扫描；真实通信结果另行记录，不以自动化通过代替硬件证据。


最终版本已完成真实权限授予、扫描，候选列表在窗口中正常显示。用户手动选择 GLM 后，界面显示“已连接，测量通知已启用”，测量入口可用，收到距离与历史记录；未输出或保存设备名称、地址、数据帧和测量数值。再次点击测量后尚未观察到记录增加，已请求用户确认仪器响应与读数一致性；实际重连和连续模式仍待验证。


真实 GLM 单次测量验收：用户确认首页按钮能使仪器响应，手机与仪器读数一致；界面历史新增记录已观察到。通过设备面板主动断开后，测量与断开按钮禁用、连接上次设备入口启用；点击连接上次设备后，界面再次进入初始化完成的 READY。使用 NEW_TASK|CLEAR_TASK 创建新首页实例后，保存设备自动连接并恢复测量可用，历史未清空。真实设备关机再开启的中断恢复及连续模式仍待用户协作确认。


用户确认 GLM 关机、等待手机断开后再开机并开启蓝牙，应用自动恢复连接。恢复后测量入口可用，历史仍存在。已请求在仪器上开启并停止连续测量，等待确认手机是否持续更新及停止后是否停止新增。


## 当前范围的交付结论

用户确认连续测量未正常更新，随后明确要求“不要弄连续测量的”。已停止该项排查，不把它记为通过，不新增连续测量入口；保留此前已有的共享接口/协议兼容。连续模式从本轮实施和验收范围排除。

本轮 Android 架构改造与明确新增的测距/拆墙入口已完成。最终验证为：两个边界检查、299 项 JVM 单元测试、Android debug/仪器包构建和 176 项完整真机仪器测试全部通过，0 失败/错误/跳过。GLM 实机权限拒绝/授予、扫描/手选连接、单次测量控制与读数一致、记录新增、主动断开/保存设备重连、首页销毁重建自动连接，以及设备关机重开后自动恢复均已确认。iOS、桌面 CAD、云服务不纳入本轮；外部分享目标的实际发送、性能/单手/平板专项认证没有执行，不以本轮测试代替这些证据。
