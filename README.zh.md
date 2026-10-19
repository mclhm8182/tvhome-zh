# tvhome-zh · tv-vibehack 中文版

**[English](README.md) | 简体中文**

一个把 Android TV / Google TV 换成 **Apple TV（tvOS）风格**桌面的开源项目，中文本地化 + 开机直达直播。

> **本仓库是 [Gathin23/tv-vibehack](https://github.com/Gathin23/tv-vibehack) 的中文衍生版。**
> 原项目仅提供英文与德文，本仓库补充了完整简体中文翻译，并新增「开机自动拉起指定 App」功能。
> 遵循原项目 MIT 许可证，版权归原作者 Gathin 所有（见 [LICENSE](LICENSE)）。

---

## 这是什么

把电视盒子臃肿的广告桌面换成一个干净的、苹果 tvOS 观感的桌面：

- **纯黑背景 + 底部四格 Dock**（tvOS 那个浅色圆角条的位置）
- **顶部大字显示**当前聚焦的应用名，带一圈取自应用封面的氛围光
- **按「下」键**应用网格从底部滑出
- **一块悬浮玻璃高光**，在图标之间弹性移动，而不是每个格子各画各的边框
- **三个磨砂按钮**：输入源（HDMI/AV/直播电视）、Wi-Fi、设置
- **问候语 + 大号细体时钟 + 日期**（Outfit 字体）
- 可选的**时钟屏保**，防止烧屏

**无广告、无账号、无需 root、无需数据线**，纯 ADB 覆盖安装，一条命令可还原。

## 相对原版的改动

| 改动 | 说明 |
|---|---|
| 🇨🇳 简体中文 | 补齐 20 条文案（问候语、输入源、设置、Wi-Fi 状态、错误提示等）与中文日期格式。资源文件：[`values-zh/strings.xml`](launcher/app/src/main/res/values-zh/strings.xml) |
| 🚀 开机自动拉起 | 开机后桌面起来约 2 秒，自动拉起你指定的应用（如电视直播 App），家人按电源即可看节目，无需碰遥控器。代码见 `MainActivity.bootLaunchIfDue()` |
| 🎯 中文场景默认配置 | Dock 默认应用改为国内常见组合（见下），Live TV 入口清空 |

### 关于「开机自动拉起」

很多国产盒子自带的「开机自启管理器」在 direct-boot 阶段崩溃（读加密 SharedPreferences 抛 `IllegalStateException`），导致自启链断裂。本项目改由桌面自身在开机后拉起目标 App：

- 以系统 `Settings.Global` 的 `boot_count` 为键，**每次开机只触发一次**
- 家人之后按 Home 键回桌面时**不会**被再次弹出
- 目标应用在 [`config.xml`](launcher/app/src/main/res/values/config.xml) 里配置：

```xml
<!-- 开机自动拉起的应用，留空则关闭该功能 -->
<string name="boot_launch_package" translatable="false">org.xinghe.tv</string>
<string name="boot_launch_activity" translatable="false">org.cqys.play.ui.activity.SplashActivity</string>
```

> 换应用就改这两行。想关掉就把 `boot_launch_package` 置空。

### Dock 默认应用

原项目默认是 Netflix / Prime / Hotstar（作者在印度），本仓库改为：

```
飞牛影视 (com.trim.tv) · 星河TV (org.xinghe.tv) · SmartTube (org.smarttube.stable) · 当贝市场 (com.dangbeimarket)
```

同样在 `config.xml` 的 `dock_apps` 里改，改完重新编译即可。

## 为什么选它：Android 11 兼容性的一个细节

很多国产 TV 应用（包括本项目早期尝试的若干桌面）在 Android 11 上会出现「应用列表只显示几个 App」的问题——这是 **Android 11 包可见性**（package visibility）限制：应用必须声明 `<queries>` 才能看到其他应用。本项目 `targetSdk 29`，在 Android 11 上属于「旧版行为」，能正常枚举全部应用，无需任何额外声明。

这在国产盒子（普遍停留在 Android 11）上尤其重要。

## 编译

需要 **完整 JDK**（不是 JRE，AGP 需要 `jlink`）与 Android SDK：

```bash
# JDK 21（Temurin）
# Android SDK：platforms;android-34（或 35）+ build-tools

cd launcher
./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

> **踩坑提示**：如果 `$HOME/.android` 目录不可写（常见于曾用 root 跑过 Android 工具的机器），debug 签名会失败。解决：生成一个 keystore，并在 `app/build.gradle` 里加 `signingConfigs.debug` 指向它。

## 安装

无需数据线，电视和电脑在同一局域网下：

```bash
# 1. 电视：设置 → 关于 → 连按「版本号」7 次 → 开发者选项 → 打开「USB 调试」
# 2. 电脑：连上电视
adb connect <电视IP>:5555

# 3. 安装并设为默认桌面
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant com.gathin.tvhome android.permission.ACCESS_FINE_LOCATION
adb shell cmd package set-home-activity com.gathin.tvhome/.MainActivity

# 4. 按 Home 键即可看到新桌面
```

也可以直接用原项目自带的脚本：`TV_HOST=<电视IP> ./launcher/build-and-install.sh`

### 还原

```bash
adb shell cmd package set-home-activity <原桌面组件名>
```

## 已知问题

- 部分电视 ROM 在应用启动瞬间会残留一个透明的「启动中」占位窗（`APPLICATION_ATTACHED_DIALOG`），吃掉按键，表现为「遥控器没反应」。解除：`adb shell am force-stop com.gathin.tvhome` 再重新启动。
- 上述自启管理器崩溃是**系统应用自身缺陷**，与本项目无关；本项目的开机拉起功能正是为了绕开它。

## 致谢

- **[Gathin23/tv-vibehack](https://github.com/Gathin23/tv-vibehack)** —— 原始项目，MIT 许可证，作者 Gathin。本仓库的绝大部分代码来自它，感谢这份优秀的开源工作。
- [Outfit](https://fonts.google.com/specimen/Outfit) 字体，SIL Open Font License（见 `launcher/FONT-LICENSE-OFL.txt`）。

## 许可

MIT。原项目版权 (c) 2026 Gathin，本地化与开机自启改动版权 (c) 2026 mclhm8182。