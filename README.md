[简体中文](README.md) | [English](README.en.md)

---

# tvhome-zh

把 Android TV / Google TV 换成 **Apple TV（tvOS）风格**桌面的开源项目，**装完就能用，不折腾**。

**本仓库是 [Gathin23/tv-vibehack](https://github.com/Gathin23/tv-vibehack) 的衍生分支**，遵循原项目 MIT
许可证，版权归原作者 Gathin 所有（见 [LICENSE](LICENSE) 与 [NOTICE](NOTICE.md)）。**非官方项目**，与原作者
及上游社区无隶属关系。

> 上游现已更名为 **CleanYourTV**（官网 [cleanyour.tv](https://cleanyour.tv)），tv-vibehack 的旧链接会重定向过去。
> 上游版本更新更快、功能更多，但设计前提是"你会用 AI 帮你折腾电视"；本仓库解决的是另一件事。

上游的原话是「一个 GitHub 仓库、一个 AI 和十分钟」。它把清理电视的整套方法做成了提示词和脚本，
交给 Claude Code 之类的助手去你家电视上执行——**这套东西需要你愿意折腾**。

本仓库走的是另一条路：**把上游那些"要改配置文件、要重新编译、要推文件"的设置项，全部变成按遥控器就能操作。**
所以它适合这样一类人：

- 想换掉臃肿的广告桌面，但**不想装 JDK、不想编译、不想敲命令行**；
- 家里有老人用电视，希望**开机就是能看的画面**，不需要有人先"配置"一下；
- 中文电视系统，或者干脆想要界面全中文。

如果你要的是上游的全部功能（主题目录、模块卡片、250 KB 瘦身构建等），或者你本来就喜欢自己动手折腾，
**请直接用[上游项目](https://github.com/Gathin23/tv-vibehack)**——它更新更快、功能更多。

## 📦 下载

**➜ [下载最新版 tvhome-zh APK](https://github.com/mclhm8182/tvhome-zh/releases/latest)** · 仅 **275 KB**，纯 Java，无广告 SDK

前往 [Releases 页面](https://github.com/mclhm8182/tvhome-zh/releases) 查看全部版本。

> 上游目前仍**不提供编译好的安装包**（其 release 只附源码），需要自行安装 JDK/SDK 并运行
> `launcher/build-and-install.sh` 编译。本仓库**直接提供编译好的 APK**，一条 `adb install` 就能装上
> ——在"不想折腾"这件事上，这是最实际的差别。

## 这不是什么

把电视盒子臃肿的广告桌面换成一个干净的、苹果 tvOS 观感的桌面：

- **纯黑背景 + 底部四格 Dock**（tvOS 那个浅色圆角条的位置）
- **顶部大字显示**当前聚焦的应用名，带一圈取自应用封面的氛围光
- **按「下」键**应用网格从底部滑出
- **一块悬浮玻璃高光**，在图标之间弹性移动，而不是每个格子各画各的边框
- **四个磨砂按钮**：桌面设置、常用、输入源（HDMI / AV / 直播电视）、Wi-Fi / 设置
- **问候语 + 大号细体时钟 + 日期**（Outfit 字体）
- 可选的**时钟屏保**，防止烧屏

**无广告、无账号、无需 root、无需数据线**，纯 ADB 覆盖安装，一条命令可还原。

### 📦 轻量

安装包只有 **275 KB**（R8 裁剪 + 资源压缩的正式构建），比同类的 TV 桌面轻得多——
纯 Java 实现、仅依赖 `recyclerview` 与 `dynamicanimation` 两个轻量 AndroidX 库，
**没有引入 Compose / Glance 等重型框架，也没有内置广告 SDK 或推荐引擎**。
在 2 GB 内存的国产盒子上实测常驻内存约 **107 MB（PSS）**，运行流畅不拖后腿。
对内存吃紧的低配盒子来说，体积小本身就是优势——装它几乎不占空间。

### 中文版实机截图

在国产电视盒子上运行的样子（简体中文界面）：

![tvhome-zh 桌面截图](docs/screenshot-zh.png)

<details>
<summary>对比：原厂广告桌面（左） vs 本项目桌面（右）</summary>

![Before: stock Google TV home with a sponsored ad. After: the tv-vibehack launcher](docs/stock-vs-new-side.jpg)

</details>

## 和上游的差异

一句话概括：**上游把配置交给文件和命令行，本仓库把配置交到遥控器上。**

### 本仓库独有

| 功能 | 上游 | 本仓库 |
|---|---|---|
| ⭐ **编辑常用应用**（底部 Dock） | 只能改 `config.xml` 再重新编译 | **按遥控器菜单键**打开面板勾选，或**长按**图标置入/移出；改动存在电视本地 |
| 🎨 **壁纸** | 需用 ADB 把 `wallpaper.jpg` 推到电视上 | 面板里一键开启**必应每日壁纸**，或**从电视里选自己的图**；新图自动替换旧图，**空间恒定一张**，不会越攒越多 |
| 🔢 **每行图标数 3–8** | 只能推 `config.json` 或改配置重编译 | 面板里点一下循环切换；首次使用**按屏幕实际宽度自动推荐** |
| 🌓 **Dock 底板自适应** | 固定不透明，亮色壁纸下像一块黑砖 | 有壁纸时自动转为半透明压暗，纯黑背景时保持原样 |
| 🇨🇳 **中文界面** | 已有（`values-zh-rCN`） | 简繁中文齐全，另补齐德/西/法/俄/加泰/泰/阿/波/塞/土，共 13 种语言 |
| ⌨️ **菜单键快捷设置** | 需改配置文件开启，且按键映射因电视而异 | **菜单键直接弹出**玻璃浮层（电视设置/显示/网络/管理应用/常用/桌面设置），可选择在别的应用里也接管 |
| 📦 **提供编译好的 APK** | 不提供二进制，需自行编译 | release 页直接下载（**275 KB** 正式构建），一条命令装上 |

### 本仓库暂缺（上游有，这里没有）

诚实地列出来，你需要的话请用上游：

- 主题目录一键推送（`themes/`）、模块卡片（其他应用可插入磁贴）
- 首启 AOT 预编译（本仓库是 R8 瘦构建，但不做 AOT）
- 桌面上直接切换 HOME 的快捷磁贴、VPN 应用标记、波斯历显示

其余部分（tvOS 观感、玻璃高光、时钟屏保、`config.json` 覆盖机制、去广告脚本、还原脚本等）
均来自上游，遵循原项目 MIT 许可证。

**默认配置保持通用**，不预设任何片商或设备专属设置，适合任意品牌电视盒子。

### ⭐ 编辑常用应用（底部 Dock）

Dock 就是底部那一排常用应用。两个入口，随你喜欢：

- **按遥控器菜单键**（有些遥控器叫「选项」/长按菜单）→ 弹出「常用应用」面板，列出全部已装应用，已固定的带勾，点一下切换；
- **长按任意应用图标** → 直接置入 Dock；Dock 已满时点它会顶替第一个位置。

选择保存在电视本地，**下次开机依然有效**。想清空就在面板里逐个取消勾选。

### 🎨 桌面设置面板

顶栏第一个磨砂按钮，点开后是一块面板，**所有设置都在这里，不用碰任何配置文件**：

- **必应每日壁纸**——开关打开即每天自动换一张（关掉只停止每日更换，当前壁纸会保留）；
- **每行图标数**——点一下在「自动 / 3 / 4 / 5 / 6」之间循环，当前值和来源始终显示在标题下方；
- **从电视中选择图片**——调用系统文件选择器，电视里存的图都能用，自动裁剪适应屏幕；
- **移除壁纸**——恢复纯黑背景。

### 🔧 进阶：换主题 / 推配置（不用重新编译）

桌面启动时也会自动读取电视上的这个目录（可放 `config.json` 和 `wallpaper.jpg`）：

```
/sdcard/Android/data/com.gathin.tvhome/files/
```

- 放一张 `wallpaper.jpg` → 重启桌面即成为背景（删掉即恢复纯黑）；
- 放一个 `config.json` → 覆盖 Dock、列数、背景色等内置默认值。

仓库里的 [`tools/set-wallpaper.sh`](tools/set-wallpaper.sh) 是一键换壁纸脚本（`./set-wallpaper.sh <电视IP> <图片路径>`）。
走 ADB 推配置文件是给进阶用户用的；不想碰命令行的话，上面那个面板就够了。

## 设备兼容性

### Android 11 的包可见性（本项目能解决）

很多国产 TV 应用在 Android 11 上会出现「应用列表只显示几个 App」的问题——这是
**Android 11 包可见性**（package visibility）限制：应用必须声明 `<queries>` 才能看到其他应用。
本项目 `targetSdk 29`，在 Android 11 上属于「旧版行为」，能正常枚举全部应用，无需额外声明。

这在国产盒子（普遍停留在 Android 11）上尤其重要。

### 部分厂商固件拒绝安装第三方桌面（本项目解决不了）

极少数国产电视固件会**以自家原生桌面的签名做校验**，任何签名不同的桌面 APK 都会被拒绝安装，
在 ADB 下表现为 `INSTALL_FAILED_VERIFICATION_FAILURE`，日志里通常有类似
`verifier: signature does not match ... will not be installed!` 的记录。

已知情况：**部分 TCL 中国区固件**（如 tcl_mt5879_cn 等）会这样。这不是本项目的问题——
上游 tv-vibehack 同样被拒（上游 README 亦记录 "A few China-market TCL sets refuse to install
any launcher at all"）。若你的电视属于这类，**只能保留它自带的桌面**。

仍然可用的是上游的去广告部分（用 ADB 禁用预装包），只是桌面换不了。

## 语言

**自动跟随电视的系统语言**，不需要任何设置。支持 13 种：

| | | | |
|---|---|---|---|
| 英文 `en` | 简体中文 `zh-CN` | 繁体中文 `zh-TW` | 德文 `de` |
| 西班牙文 `es` | 法文 `fr` | 俄文 `ru` | 加泰罗尼亚文 `ca` |
| 泰文 `th` | 阿拉伯文 `ar` | 波兰文 `pl` | 塞尔维亚文 `sr-Latn` |
| 土耳其文 `tr` | | | |

界面文案已按各地习惯写，不是机翻——比如港台用「桌布」而不是「壁纸」，设置项写作「桌面设定」。

Android 13 及以上的电视，还可以在「设置 → 应用 → tvhome → 语言」里**单独给桌面指定语言**，
不用把整个系统语言改掉。

## 安装

### 方式一：在电视上直接安装（多数盒子可行）

1. 把 `tvhome-zh.apk` 拷到电视上（U 盘、局域网共享、或者电脑下载后用数据线）；
2. 在电视上用文件管理器点开这个 APK，按提示安装（第一次会要求允许「安装未知应用」）；
3. 装好后尝试在 **设置 → 应用 → 默认桌面** 里选它。

**这个方法会卡住的地方**：不少国产 ROM 不在设置里提供「默认桌面」入口，或者藏得很深；
遇到这种情况改用下面的方式二。

### 方式二：通过 ADB（更可靠，推荐）

电视和电脑在同一局域网下，无需数据线：

```bash
# 1. 电视：设置 → 关于 → 连按「版本号」7 次 → 开发者选项 → 打开「USB 调试」
# 2. 电脑：连上电视
adb connect <电视IP>:5555

# 3. 安装
adb install -r tvhome-zh-debug.apk

# 4. 设为默认桌面（这一步 TV 设置里没有入口时才需要）
adb shell pm grant com.gathin.tvhome android.permission.ACCESS_FINE_LOCATION
adb shell cmd package set-home-activity com.gathin.tvhome/.MainActivity

# 5. 按 Home 键即可看到新桌面
```

从旧版本升级直接覆盖安装即可（`install -r`），**已设置的常用应用和壁纸都会保留**。

> **从 v1.2.0 升级需要先卸载一次。** v1.3.0 换了签名密钥（debug 签名的正常现象，不是人为设置的），直接覆盖会报
> `INSTALL_FAILED_UPDATE_INCOMPATIBLE`。卸载旧版再装即可，Dock 和壁纸需要重设一次。
> **v1.3.0 之后的版本都可以直接覆盖升级**，不会再有这个问题。

> 装不上时，先看上面的[设备兼容性](#设备兼容性)：如果是厂商固件拒绝，换多少个桌面都一样。

### 想让 Home 键真的会回顶？

系统在桌面已经打开时会直接吞掉 Home 键，所以这个功能要靠一个**无障碍服务**实现：

**设置 → 无障碍 → 已下载的服务 → 「Home 键回顶」→ 打开。**

打开后：遥控器按 Home 回到桌面顶部；在别的应用里按菜单键也会弹出快捷设置浮层
（这一项可在「桌面 → 桌面设置 → 其他应用里也接管菜单键」里关掉）。
不打开也能正常当桌面用，只是上面两条不生效。

### 还原

```bash
adb shell cmd package set-home-activity <原桌面组件名>
```

## 自定义（给想改源码的人）

**日常设置不需要改这些** —— Dock、壁纸、每行图标数在电视的「桌面设置」面板里就能调。

只有要做更深的改动时才需要编辑 [`config.xml`](launcher/app/src/main/res/values/config.xml)，改完重新编译：

- `dock_apps` —— 底部 Dock 固定的应用（**默认留空**，留空则应用按名称排序；电视上设置过之后以电视上的为准）
- `live_tv_package` —— 「直播电视」输入源对应的应用包名（默认留空＝不显示该入口）
- `hidden_apps` —— 不出现在网格中的应用（默认留空）
- `columns` —— 网格每行几个图标（默认 4；电视上设置过之后以电视上的为准）

包名怎么查？电视上执行 `adb shell pm list packages`。

## 从源码编译

需要**完整 JDK**（不是 JRE，AGP 需要 `jlink`）与 Android SDK：

```bash
# JDK 21（Temurin）
# Android SDK：platforms;android-34（或 35）+ build-tools

cd launcher
./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

> **踩坑提示**：如果 `$HOME/.android` 目录不可写（常见于曾用 root 跑过 Android 工具的机器），
> debug 签名会失败。解决：生成一个 keystore，并在 `app/build.gradle` 里加 `signingConfigs.debug` 指向它。

## 已知问题

- 部分电视 ROM 在应用启动瞬间会残留一个透明的「启动中」占位窗（`APPLICATION_ATTACHED_DIALOG`），
  吃掉按键，表现为「遥控器没反应」。解除：`adb shell am force-stop com.gathin.tvhome` 再重新启动。
- 必应每日壁纸需要电视能联网；网络不通时会提示获取失败，其他功能不受影响。
- 「从电视中选择图片」依赖系统文件选择器，个别 ROM 没有这个组件时会提示不支持。
- 必应每日壁纸功能需要 `INTERNET` 权限（开启壁纸时才会用到），默认关闭，不影响其他功能。

## 致谢

- **[Gathin23/tv-vibehack](https://github.com/Gathin23/tv-vibehack)** —— 原始项目，MIT 许可证，
  作者 Gathin。本仓库的绝大部分代码来自它，感谢这份优秀的开源工作。
  本仓库是**非官方衍生项目**，与原作者及上游社区无隶属关系。
- 上游社区贡献的西班牙文、法文、俄文、加泰罗尼亚文、泰文、德文翻译，本仓库沿用并补全。
- [Outfit](https://fonts.google.com/specimen/Outfit) 字体，SIL Open Font License
  （见 `launcher/FONT-LICENSE-OFL.txt`）。

## 许可

MIT。原项目版权 (c) 2026 Gathin，本仓库改动版权 (c) 2026 mclhm8182。