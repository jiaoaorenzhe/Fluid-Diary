# 出入量日记 · Fluid-Diary

**为颅咽管瘤术后垂体功能下降、尿崩症患者设计的 Android 出入量记录应用。**

记录每日饮水量、尿量与用药情况，自动生成趋势图，一键复制数据给医生。
完全离线、无广告、无联网权限，所有数据仅保存在本机。

> 本应用仅为个人记录工具，不构成任何医疗建议。
> 饮水量、尿量与用药请以医生指导为准，去氨加压素等药物剂量调整务必遵医嘱。

## 功能

- **今日**：饮水/尿量一键记账（快捷量 + 自定义）、用药打卡、当日明细（含用药，时间倒序，可撤销）
- **记录**：最近 14 天汇总表格，点按查看当日明细、可删除整日数据
- **趋势**：7/14/30 天双柱状图（自绘 Canvas）、日均统计、单日最高尿量
- **导出**：一键复制 30 天 TSV 数据，直接粘贴给医生（微信/备忘录）
- **Material You 动态取色**：跟随系统壁纸变色（Android 12+），深色模式、全面屏手势适配（minSdk 28 / targetSdk 36）
- **用药名单自定义**：预置去氨加压素（弥凝）、氢化可的松、左甲状腺素（优甲乐）、生长激素，可增删

## 下载安装

直接下载仓库中的预构建 APK（debug 签名）：

```
apps/fluid-diary/fluid-diary-v3.apk
```

安装时允许「来自此来源的应用」即可。数据存于应用私有目录，卸载即清除。

## 仓库结构

```
├── apps/
│   ├── fluid-diary/      出入量日记（主项目，单 Activity ~700 行纯 Java）
│   └── hello-app/        M3 Hello World 示例（用于验证工具链）
└── toolchain/
    ├── build.sh          一键构建：javac → d8 → MiniAapt → zip → apksigner
    ├── MiniAapt.java     纯 Java 实现的 AndroidManifest.xml → 二进制 AXML 编译器
    └── MiniServe.java    本地 HTTP 分发服务器（手机浏览器直接下载 APK）
```

## 在手机上构建 APK（本项目最特别的部分）

本项目的**每一个 APK 都是在 Android 手机上构建并签名的**，全程无电脑参与：

```
┌─────────────────────────────────────────────────────┐
│  Android 手机（root）                                │
│  ┌───────────────────────────────────────────────┐  │
│  │ Aether 沙箱：Alpine Linux aarch64 (proot/musl) │  │
│  │                                               │  │
│  │  OpenJDK 17 (musl)                            │  │
│  │    └─ javac --release 8                       │  │
│  │  d8 (Android build tools)                     │  │
│  │    └─ .class → classes.dex (min-api 28)       │  │
│  │  MiniAapt（自研，~200 行）                     │  │
│  │    └─ AndroidManifest.xml → 二进制 AXML        │  │
│  │  zip + apksigner                              │  │
│  │    └─ v1+v2+v3 签名                           │  │
│  │  MiniServe                                    │  │
│  │    └─ 127.0.0.1:8765 → 手机浏览器下载安装       │  │
│  └───────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────┘
```

### 环境需求

- Android 9+ 设备，已 root（Magisk）
- 一个 Alpine proot 环境（如 Termux + proot-distro，或支持 proot 沙箱的 App）
- Android build tools 中的 `d8.jar`、`apksigner.jar`（SDK 35）

### 构建一个 App

```sh
# 1. 准备 SDK（下载 build-tools 后解出 d8.jar / apksigner.jar）
# 2. 把 toolchain/ 放到任意目录，设置环境变量
export ANDROID_JAR=/opt/android-sdk/platforms/android-35/android.jar
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk

# 3. 每个 App 一个目录：app.properties + src/包路径/MainActivity.java
./toolchain/build.sh /path/to/apps/fluid-diary
# 产物：fluid-diary-v3.apk
```

`app.properties` 示例：

```properties
APP_NAME="fluid-diary-v3"
PKG="com.aether.fluiddiary"
LABEL="出入量日记"
ACTIVITY="com.aether.fluiddiary.MainActivity"
VERSION_CODE=3
VERSION_NAME="1.2"
MIN_SDK=28
TARGET_SDK=36
```

## 踩坑记录（手机端构建的关键点）

在 Alpine/musl 沙箱里搭 Android 构建流水线，这些坑都踩过并解决：

1. **javac 交叉编译**：必须用 `javac --release 8 -encoding UTF-8 -classpath android.jar`。
   旧写法 `-source 8 -target 8 -bootclasspath` 在新 JDK 上会因找不到 `LambdaMetafactory`
   而报错（lambda 需要 `--release` 模式下的 CT 符号表）；`-bootclasspath` 与 target 17 冲突。
2. **Alpine 默认 ASCII locale**：javac 不加 `-encoding UTF-8` 会把中文字符串/emoji 编译成乱码。
3. **d8 全量脱糖**：`d8 --min-api 28` 会把 lambda、字符串拼接（invokedynamic）全部脱糖，
   产物 dex 中不会残留 `LambdaMetafactory`/`StringConcatFactory`，Android 9+ 运行安全。
4. **MiniAapt 手写 AXML**：字符串池按「UTF-16 code unit 长度 + UTF-8 字节长度」双长度字段编码，
   中文 App 名可直接通过；属性 Resource ID 按字符串池排序映射（attr ID 首个为 0x01010000 起点）。
5. **Edge-to-edge 时序**：`getInsetsController()` 必须在 `setContentView()` **之后**调用——
   Android 16 上 DecorView 未创建时会直接 NPE（而非返回 null）。
6. **纯框架 API 的 Material You**：`getResources().getIdentifier("system_accent1_600", "color", "android")`
   可在 API 31+ 取 Monet 动态色，低版本回退 M3 基准紫；框架无 `ColorUtils`，ARGB 插值需自己实现。
7. **签名**：minSdk ≥ 28 时 apksigner 默认 v2+v3 即可；debug keystore 固定复用才能实现应用内升级。

## 技术细节

- 单 Activity 架构（MiniAapt 目前只支持单 Activity 清单），底部导航 + 页面切换
- 数据持久化：SharedPreferences + org.json（`dyyyy-MM-dd` → JSON 事件数组）
- UI 全部代码构建（无 XML 布局），零第三方依赖，APK 仅 ~29 KB
- 趋势图为自绘 `Canvas` 柱状图（双柱、网格、图例、自适应量程）

## License

MIT
