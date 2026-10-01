# LaserSketch

LaserSketch 是一款面向 Android 的激光测距与空间记录工具。应用通过 Bluetooth Low Energy（BLE）连接 Bosch GLM 系列测距设备，将测量结果保存为历史记录，并支持绘制平面草稿、在照片上添加尺寸标注以及导出和分享结果。

## 功能

### 激光测距

- 扫描并连接附近的 Bosch GLM BLE 设备
- 自动识别设备服务和测量特征
- 支持重新连接上次使用的设备
- 执行测量并实时显示距离
- 持久化保存测量历史
- 清空记录或导出为 CSV 文件

### 平面草稿

- 绘制墙体并编辑墙长、墙厚
- 墙体可使用中心线、内侧线或外侧线作为定位线；切换时定位线保持不动，墙体移动到对应位置，连续绘墙会继承当前定位线
- 添加矩形柱和圆柱
- 方柱支持通过独立旋转手柄按 15°吸附旋转，也可在属性面板输入精确角度；旋转角度随草稿保存
- 添加门、窗并调整位置、宽度和方向
- 选择、移动和删除图形
- 将墙体连接、延长或拉直为水平/垂直
- 支持直角和斜角 T 型墙体融合：支墙端部的两个角分别贴合主墙远侧墙面，接头内部连续，不出现楔形缺口、横穿边线或突出主墙的墙角
- 支持撤销最近一次放置操作
- 创建、打开、重命名和删除草稿项目

墙体连接以各墙当前定位线为几何基准。墙端连接到另一堵墙的中段时形成 T 型连接；如果支墙为斜墙，软件会按实际夹角分别计算支墙两侧边与主墙边界的交点，使墙体只在主墙范围内融合，不会因为统一延长距离而让其中一角穿出主墙。

### 图片标注

- 从相机或相册创建图片标注项目
- 添加尺寸线、文字、角度和面积标注
- 使用最近的测量结果快速填充尺寸
- 拖动调整标注位置和控制点
- 保存标注图片到相册
- 导出并通过微信分享标注图片
- 创建、打开、重命名和删除图片标注项目

## 技术栈

- Kotlin
- Android Gradle Plugin `9.3.2`
- Gradle `9.5.0`
- Android SDK：`compileSdk 37`，`targetSdk 37`
- 最低支持：Android `12`（API 31）
- AndroidX Core KTX、AppCompat、Material Components
- Bluetooth GATT / BLE
- SQLite：保存测量历史
- JSON + 应用内部文件：保存草稿和图片标注项目

## 项目结构

```text
.
├── app/
│   └── src/
│       ├── main/
│       │   ├── java/com/example/boschlaser/
│       │   │   ├── MainActivity.kt              # 首页、设备连接和测量记录
│       │   │   ├── BoschBleManager.kt           # BLE 扫描、连接和通信
│       │   │   ├── BoschProtocol.kt             # Bosch 测量协议解析
│       │   │   ├── SketchActivity.kt            # 平面草稿编辑器
│       │   │   ├── SketchView.kt                # 平面草稿绘制和几何操作
│       │   │   ├── AnnotationActivity.kt        # 图片标注编辑器
│       │   │   ├── ImageAnnotationView.kt       # 图片标注绘制和交互
│       │   │   ├── *ProjectStore.kt             # 项目保存、加载和管理
│       │   │   └── MeasurementHistoryStore.kt   # 测量历史数据库
│       │   └── res/                             # 图标、主题和资源
│       ├── test/                                # JVM 单元测试
│       └── androidTest/                         # Android 仪器测试
├── gradle/
├── build.gradle.kts
├── settings.gradle.kts
└── gradlew / gradlew.bat
```

## 环境要求

- Android Studio
- JDK 17 或更高版本（Gradle 9.5 运行要求）
- Android SDK Platform 37
- 一台 Android 12 或更高版本的设备或模拟器
- 如需测距功能，需要支持 BLE 的 Bosch GLM 测距设备

项目源码的 Java 编译目标为 Java 11，但 Gradle 本身使用 JDK 17 或更高版本运行。

## 构建与运行

在 Windows PowerShell 中：

```powershell
.\gradlew.bat :app:assembleDebug
```

生成的 Debug APK 位于：

```text
app/build/outputs/apk/debug/app-debug.apk
```

安装到已连接设备：

```powershell
.\gradlew.bat :app:installDebug
```

在 macOS/Linux 中使用：

```bash
./gradlew :app:assembleDebug
./gradlew :app:installDebug
```

## 测试

运行 JVM 单元测试：

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

运行连接设备上的 Android 仪器测试：

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

## 权限说明

应用需要以下权限用于 BLE 测距设备连接：

- `BLUETOOTH_SCAN`
- `BLUETOOTH_CONNECT`

Android 12 及以上版本会在运行时请求“附近设备”权限。BLE 扫描明确声明为不用于推断位置，应用不申请粗略或精确位置权限。

## 数据与隐私

- 测量历史保存在应用本地 SQLite 数据库中。
- 平面草稿和图片标注项目保存在应用内部目录中，包含 JSON 数据、原图和预览图。
- 保存到相册的图片会写入 `Pictures/LaserSketch`。
- CSV 导出位置由系统文件选择器决定。
- 应用不会在本项目中配置生产签名密钥。

## 当前应用信息

- 应用名称：`LaserSketch`
- Gradle 项目名：`LaserSketch`
- Application ID：`com.example.boschlaser`
- Version：`1.0`（versionCode `1`）

Application ID 暂时保留为 `com.example.boschlaser`，以避免更换包名后影响已安装应用的数据和现有测试。
