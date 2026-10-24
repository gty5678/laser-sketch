# 测量仓库与兼容升级

## 数据入口

```text
LaserDevice / BoschLaserDevice
             ↓
MeasurementService
             ↓
MeasurementRepository
      /              \
Android SQLite    InMemory（测试/预览）
             ↓
StateFlow<List<MeasurementRecord>>
             ↓
首页历史 / 草图近期测量 / 照片标注近期测量
```

共享记录按时间从旧到新排列；同一时间保持插入顺序。`Measurement.id` 是更新与删除依据，时间戳不再作为实体身份，避免同一毫秒的记录互相覆盖。值为 Double 毫米，不引用几何或 UI。`MeasurementRecord` 附加诊断原始字节与旧显示类型文本；字节在构造和读取时复制，外部修改不能改变仓库记录。

`MeasurementService` 提供记录、刷新、删除、清空以及最新列表；最新列表默认取倒序前 200 条，不能反向裁剪完整历史。通用 `acquire(LaserDevice, scope)` 支持非 Bosch 设备；当前 Android 兼容控制器传入带原始帧的记录以保留 CSV 所需信息。

Android `MeasurementServices` 容器持有应用 Context、共享服务和持久化 scope。存储操作可跨页面完成，不持有 Activity。每个屏幕只订阅数据流，停止/销毁时取消对应 UI 订阅；首页的历史显示外观仍转为原来的 Float 米、格式化时间和类型文案。原 `MeasurementStore` 和 `MeasurementHistoryStore` 已移除。

## SQLite 版本 2

文件仍为应用内部的 `measurement-history.db`。原版本 1 表 `measurements` 保存时间戳主键、米值、类型文本、累计米值与原始帧；升级时在 SQLiteOpenHelper 的升级事务中创建 `measurement_records`，复制全部行：

- ID：`legacy-<timestamp>`，可重复读取、稳定定位。
- `value_mm` / `accumulated_mm`：旧米值乘 1000，保留原数值精度，不按显示小数位截断。
- 时间戳、原始帧、旧类型文本完整保留；旧记录没有设备 ID，因此使用 null。
- 新记录使用独立测量 ID、设备 ID、枚举操作和 Double 值；同一时间戳可以保存多条。

新表是唯一活跃历史来源。旧表作为升级来源保留，不再同步新增测量，也不会在每次刷新时重新导入。用户删除迁入的旧记录时同时删除对应旧表行，清空时同时清空两表，不能留下已请求删除的旧副本。

升级失败时 SQLite 事务回滚，新表和版本号不留下部分状态，原数据库数据保留。写入/删除/清空同样先在事务中执行并读取结果，提交成功后才发布记录流；失败时界面不能出现未保存的记录。仓库先取得串行锁再切到 IO，避免 IO 线程调度反转先后的写入请求。更新已有 ID 不改变同时间记录的插入顺序。

## 验证

共享测试保留了旧桥接“监听立即收到最新列表”的断言，并覆盖同时间不同 ID、最新 200 条与完整历史、更新顺序、失败传播、通用设备采集、取消以及原始字节隔离。

Android 仪器测试使用随机命名的测试数据库，覆盖版本 1 升级、旧米值/类型/原始帧兼容、ID 记录重启恢复、同时间记录删除、清空两表、触发器写入失败以及损坏旧模式的升级回滚。不会用生产数据库作测试样本或清理目标。

```powershell
.\gradlew.bat :shared:checkSharedBoundaries :shared:jvmTest :app:testDebugUnitTest :app:assembleDebug
.\gradlew.bat :app:connectedDebugAndroidTest
```

`.lsk` 的 `measurements.json` 已由共享 `ProjectSerializer` 编解码并随项目导入导出；iOS 持久化适配与平台验证仍待完成，不能把 Android SQLite 回归称为 iOS 验证。

## 共享测量界面

首页通过 `:shared-ui/MeasurementScreen` 展示当前距离与历史。Android 宿主 `AndroidMeasurements` 在 STARTED 生命周期内收集共享记录流，以 Double 毫米值格式化当前值、历史值和累计值，不经过旧 Float 外观。共享屏幕按最新记录优先展示，默认 100 条，每次展开 100 条；分页只影响展示，不裁剪仓库或 CSV。记录删除使用稳定 ID，时间戳相同的记录不会误删。全部清空保留宿主确认对话框，未连接时禁止发送测量命令。Android 本地化文案继续使用资源并注入共享屏幕。

`MeasurementScreenInstrumentedTest` 验证空态/禁用动作、稳定 ID 回调、分页与 Android 测量记录流刷新，包含超过 Float 精确整数范围的 Double 数值和相同时间戳记录的删除。

## CSV 导出边界

`MeasurementCsv` 在共享核心生成七列 CSV，接收平台注入的时间、类型和小数格式化函数。数值直接来自 Double 毫米，保留原列顺序、毫米一位/米四位小数、原始帧小写十六进制和完整历史顺序；包含逗号、引号、换行的文本按 CSV 规则转义。

Android `MeasurementServices.exportCsv` 在用户选择目标 URI 后捕获完整记录快照，在应用 scope 执行任务。`AndroidMeasurementCsv` 只持有应用 Context，在 IO 上格式化并打开、写入、关闭 UTF-8 provider 流；Activity 只接收结果。退出页面取消结果订阅，不取消已经接受的文件写入。共享测试覆盖 Double 精度、205 条历史、文本转义、空历史及列数量；设备测试验证真实 FileProvider 写入与再次导出截断旧内容。

草图属性通过稳定实体 ID 从共享渲染文档读取墙/门窗/柱参数，初始草稿不再从 Float 兼容投影取尺寸。草图沿用整数毫米显示，格式化直接接受 Double，半格向正无穷舍入并避免 Int 饱和；近期测量和画布/导出标签使用同一格式。点击近期测量仍把原 Double 值交给 ChangeWallLengthCommand，显示舍入不改变实际赋值或持久化。新增 JVM 回归覆盖半格、负零、超过 Float 精度及 Int 范围的值；Android 属性面板回归验证 16777216.5 mm 显示为 16777217，同时不修改文档和历史，该仪器测试已编译但未设备执行。
