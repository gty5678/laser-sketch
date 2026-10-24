# BLE 与测量边界

## 依赖与所有权

```text
Android MainActivity
        ↓
BoschBleManager（当前 View 的兼容控制器）
        ↓
shared BoschLaserDevice : LaserDevice
        ↓
shared BleTransport
        ↓
AndroidBleTransport（Android Bluetooth API）
```

`AndroidBleTransport` 不认识 Bosch 数据帧。服务/特征 UUID 由调用者传入，通知输出原始分包；它只负责扫描、权限检查、连接、服务发现、通知订阅和写入确认。协议 CRC、分包重组、Bosch 设备匹配、初始化命令与毫米转换均在 `commonMain`。后续 iOS 适配器实现同一接口，不复制协议解析。

`BoschLaserDevice` 通过 `LaserDevice` 暴露状态、测量流、单次与连续测量操作。共享 `Measurement` 只包含 ID、Double 毫米值、epoch 时间、可选设备 ID 和加减累计信息，不持有 View、墙、Canvas 或 BluetoothGatt。持久化通过共享 `MeasurementRepository` 与 Android SQLite 适配器承接；数据库升级为按 ID 保存，旧时间戳记录、原始帧和显示文案兼容迁入。详见 [测量仓库](measurement-repository.md)。旧 Android `Measurement` 只用作当前 View 的显示转换。

设备类的子 Job 属于调用者提供的 scope，但 `close()` 只停止自己的采集协程。调用者必须使用串行 dispatcher；Android 使用 Main。当前控制器在 Activity 销毁时关闭设备和传输并取消自己的 scope，不能让采集协程持有已销毁的 Activity。重连和新扫描先取消、等待旧操作清理，防止旧操作的取消处理关闭新连接。

## 状态与失败

1. 扫描仍采用 12 秒窗口，不依赖设备必须广播服务 UUID；按服务 UUID、名称或上次保存 ID 匹配后可以提前结束。
2. GATT 连通后发现服务；`subscribe` 写入 CCCD 并等待回调。只有订阅成功，传输层才进入 `READY`。
3. 设备层随后发送 Bosch 初始化命令并等待写入确认；完成后才进入设备 `READY`，首页才允许测量。
4. GATT 写入通过 Mutex 串行化，每次等待平台确认。连接 10 秒、订阅和写入 5 秒超时；取消或超时释放连接，避免等待永久挂起。
5. 回调必须属于当前 GATT/扫描对象和特征；断开清空等待操作。每次清理递增会话编号，通知及已排队的测量事件在交付时过滤旧会话。分包缓冲在重连、断开与关闭时丢弃。
6. 拒绝权限时在调用扫描或连接 API 前抛出类型化错误；清理处理权限被撤销的情况。后台错误、写入失败通过控制器转换为资源文案，不把平台错误字符串放入共享模型。
7. Android 回调在 Main 执行。通知缓冲限制为 64 个分包；消费者过慢导致缓冲溢出时关闭连接并报告错误，避免无声丢失分包后继续解析。

扫描/连接不新增权限；沿用 Android 12+ `BLUETOOTH_SCAN` 和 `BLUETOOTH_CONNECT`。不在日志打印真实设备 ID、名称、数据帧或测量值。保存上次设备仍使用原 `bosch_glm` preferences，兼容现有安装。

## 验证与未完成项

共享假传输测试覆盖初始化确认、命令编码、离线拒绝、分包、错误 CRC、注入时间/ID、断开清理、取消连接、关闭生命周期、设备匹配，以及慢消费者重连后的旧事件过滤。Android 仪器测试通过 Context 模拟权限拒绝，确认扫描/连接在平台调用前失败，不改动设备实际授权。

```powershell
.\gradlew.bat :shared:checkSharedBoundaries :shared:jvmTest :app:testDebugUnitTest :app:assembleDebug
.\gradlew.bat :app:connectedDebugAndroidTest
```

这些测试不能证明真实 GLM 通信成功。仍需在 Android 12+ 与目标 Bosch GLM 上检查授权拒绝/授予、蓝牙关闭、自动识别/手工选择、已存设备重连、实际测量、连接中断、Activity 销毁与重新进入。iOS CoreBluetooth 适配器尚未实现；共享测量服务与仓库已接入；工程数据、独立照片标注与 .lsk 文件适配已接入，另见项目格式与架构文档。

## 共享设备界面

`:shared-ui/DeviceScreen` 接收宿主提供的状态、设备描述、保存设备连接可用性和本地化文案，输出扫描、连接保存设备与断开回调。就绪表示 Bosch 协议初始化已完成，不能把 GATT 连接成功直接显示为可测量。MainActivity 的同一个展示状态同时驱动面板和测量入口；连接/断开回调更新它，Compose 自动重绘。Android 宿主继续负责附近设备权限、自动重连与超时、扫描候选选择、浮动面板开关和 Activity 生命周期；协议与 GATT 层没有因为 UI 迁移而变化。

`DeviceScreenInstrumentedTest` 覆盖控件可用性、宿主动作回调和状态刷新，不代表已进行真实 Bosch GLM 扫描、测量或重连验证。真实 Android 12+ 设备和 GLM 硬件仍需按 BLE 验收流程验证。

## 权限撤销与异步回调

扫描结果读取设备名称/地址、连接后的服务发现以及旧版通知值读取，均通过 Android Transport 内的权限异常边界执行。当前扫描/GATT 身份检查先于该边界，旧对象的回调不会触发新连接失败。平台调用抛出 SecurityException 时，Transport 统一报告 PERMISSION，结束扫描/连接/订阅/写入等待，递增会话并清理 GATT；不让权限异常逸出回调。订阅与写入也保护权限检查之后的平台操作，并在权限失效时释放已有连接。其他异常不会被通用吞掉。

新增仪器回归模拟权限撤销并检查所有等待操作释放、失败类型、会话递增及清理后可断开；同时检查订阅/写入的类型化失败。这些测试已在此前 Android 16 真机完整运行中通过；模拟权限异常不能替代真实系统撤权与 Bosch GLM 验收。最新完整 176 项仪器套件已通过。真实权限拒绝/授予、扫描、手选 GLM、单次测量及保存设备重连也已执行；连续模式按用户最新要求不再处理，不纳入本轮验收。


## 手工候选选择

未自动匹配时，AndroidBleDevicePicker 显示候选列表，只把选择索引回传给 MainActivity，由宿主连接对应地址。列表弹窗不能同时设置正文 message，否则 AppCompat 会以正文替换列表；说明保留在首页状态中，标题与取消取自 Android 资源。AndroidBleDevicePickerInstrumentedTest 用虚构标签检查真实 Dialog 中候选可见、原生列表选择索引及取消关闭，不连接任何真实设备。真实窗口坐标点击另由实机验收确认。


## 当前实机证据

Android 16 上真实拒绝附近设备权限后显示权限提示，允许后可以扫描。用户手动选择 GLM 后，协议初始化完成并启用测量；用户确认手机测量入口能控制仪器，读数一致，应用历史有新增。主动断开后测量禁用，连接上次设备后恢复；首页销毁重建会自动连接保存设备。用户还确认 GLM 关机、再开机并开启蓝牙后应用自动恢复连接。完整 176 项仪器测试和 299 项 JVM 测试通过，模拟权限异常与真实硬件证据分别记录，不包含设备标识或测量数值。


用户反馈连续测量未正常更新后，明确要求不再处理连续测量。本轮停止该项排查，不声明连续模式硬件验收通过，也不增加连续测量界面；共享接口和既有协议兼容保留。单次测量、主动重连、首页重建及设备关机重开恢复的实机证据不受此项范围调整影响。
