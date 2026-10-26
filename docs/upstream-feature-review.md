# 上游 v2.7.0 功能评估：哪些值得进 tvhome-zh

评估日期：2026-10-23 · 对照版本：上游 `Gathin23/cleanyourtv`（原 tv-vibehack）v2.7.0
基线说明：我方 fork 起点对应上游 v2.1.0（2026-10-19）。

## 一、先看盘子有多大

上游这四天把 `config.xml` 的配置项从 **8 个扩到 76 个**，新增 40 个 Java 类、5 个布局、10 个图标。
launcher 源码净增：MainActivity +2694 行、Config +729、HomeKeyService +206、AppAdapter +523、AndroidManifest +112。

我们 fork 是精简重写版：`launcher/` 只有 13 个 Java 类、`config.xml` 只有 16 项。
两边的差距不是"落后几个补丁"，而是**上游把自己从"一个 Apple TV 风格的桌面"扩成了"一个可编程的启动器平台"**。

所以评估的第一原则不是"上游有什么我们补什么"，而是：

> 上游 76 个开关里，绝大多数是给**愿意改配置文件再重新编译**的人用的。
> tvhome-zh 的用户是不改文件、装 APK 就用的人。这些开关在我们这儿一个都不该是"改 XML"。

正确的落点是**把它们做成桌面设置面板里的一行开关**——这恰好是我们的差异化，也是上游没有的（上游只有 `LauncherSettingsActivity` 的一部分）。

## 二、评估用的四条标准

| 标准 | 说明 |
|---|---|
| 用户可感知 | 用户在沙发上能看见/能按出来，不进 adb、不改文件 |
| 国内痛点 | 创维/酷开/小米/海信等实际在售机型；影视仓这类无 leanback 入口的 App |
| 低配友好 | 目标机型 2GB RAM +  Mali/Amlogic 老 GPU，不能吃显存和常驻线程 |
| 不冲突 | 不推翻"中文优先 + 装完即用"的定位，不与天气行/顶栏布局打架 |

---

## 三、第一梯队：建议做（5 项）

### 1. 应用库：让没有 leanback 入口的 App 也能上桌面 ★★★★★

- **上游**：`app_library` + `app_library_selection` + `library_apps`；实现是 `LibraryApps.java` / `AppLibraryDialog.java` / `row_app_library.xml`
- **我们现状**：`AppAdapter.reload()` 只查 `CATEGORY_LEANBACK_LAUNCHER`（AppAdapter.java:202）。**没声明 leanback 入口的 App 一律不显示。**我们的"管理应用"面板只能卸载/停用，不能"加到桌面"。
- **为什么值得**：这是我们目前最大的功能缺口，而且是国内电视的真实痛点。上游自己在注释里点名：*"For sets where almost no app declares a leanback entry (many China-market TVs, e.g. Coocaa 8R145), a fresh Home with app_library on would otherwise be nearly empty."* 国内用户装完影视仓、直播软件，桌面上是空的——这正是最容易被投诉的点。
- **成本**：中等。要写普通 App 查询 + 一个勾选界面。上游代码可参考，但要改成我们的视觉风格。
- **注意**：不能默认全开。桌面全是普通 App（很多是手机版 App，横屏拉伸）会很丑。做法是**默认只显示 leanback App，在设置里开一个"显示普通 App"开关**，或做成"应用库"面板按需添加。

### 2. 创维 / 酷开（Coocaa OS）输入源适配 ★★★★★

- **上游**：`coocaa_sources`；实现是 `CoocaaSources.java`（读 `com.skyworth.tv` 自己的 provider）和 `source_panel_extra`
- **我们现状**：`VendorInputs.java` 只认小米国行（`com.xiaomi.mitv.tvplayer` 且没有 `com.mitv.livetv`）。创维/酷开电视上，我们的输入菜单是空的——用户只能退回系统设置切源。
- **为什么值得**：创维/酷开是国内销量前列的品牌。**每多适配一个品牌，就多一批能用的机器。**这是把"装完即用"真正兑现的地方。
- **成本**：中等。上游有现成实现，我们已有 `VendorInputs` 的三级回退框架可以扩展。
- **风险**：酷开系统版本多，需要实测几台机器。可做成"检测到酷开就自动启用"。

### 3. 每个磁贴常显应用名 ★★★★

- **上游**：`always_show_names`（未聚焦的名字变暗，聚焦的纯白；dock 行会因此变高）
- **我们现状**：只有聚焦的磁贴显示名字（`AppAdapter` 里 `h.name.setAlpha(focused ? 1f : 0f)`）
- **为什么值得**：低配电视用户、新用户、电视桌面上装了一堆图标不认识的 App（各家影视 App 的图标都是自家 logo）。**找不到图标对应哪个 App 是高频抱怨。**上游把它做成默认关（因为费空间），我们做成设置里一个开关正合适。
- **成本**：极低。视图和 alpha 逻辑都现成，就是个开关 + 一行高度计算。

### 4. 遥控器"输入源"键接住 ★★★★

- **上游**：`handle_view_inputs`——响应系统的 `com.android.tv.action.VIEW_INPUTS`，用我们的输入菜单应答
- **我们现状**：没有这个 intent-filter。换掉原桌面后，遥控器上的"TV/信号源"键常按无反应。
- **为什么值得**：纯粹的"坏了"体验，一行 manifest + 一个 intent 分发就能修好，用户感知极强。
- **成本**：极低。但上游默认关（怕和电视自带的选择器抢），我们要默认开还是关得想一下——**建议默认开，但只在系统确实没有别的处理器时接管**。

### 5. 状态行：CPU / 内存 / 存储 ★★★☆

- **上游**：`show_stats`——时钟下面一行，显示 CPU 占用、空闲内存、空闲存储
- **我们现状**：没有。
- **为什么值得**：我们的用户就是被"2GB 盒子被 23 个 App 撑爆"折磨过的人（这个痛点我们自己都写过）。桌面常驻一行内存数字，对懂点技术的人是真有用。
- **成本**：低（上游 `StatsText.java`）。
- **注意**：上游说每次刷新约 7ms 都在 UI 线程，**必须挂在开关下 + 低频刷新（10 秒级）**，别拖慢 2GB 机器。

---

## 四、第二梯队：可以做，看精力（4 项）

### 6. 时段天空背景 + 与天气联动 ★★★★（我们的独家机会）

- **上游**：`sky_intensity` + `latitude` / `longitude`，实现是 `Sky.java` / `SkyView.java` / `Sun.java`
- **上游的问题**：要用户手填经纬度。填对了，日出日落光晕才准。
- **我们的优势**：**我们已经有天气模块，城市经纬度是自动拿到的**（Open-Meteo geocoding 定位结果里就有）。而且我们还有 AQI 和实时天气状况。
- **可以做到**：阴天=灰调、雨天=蓝调、雾霾=土黄、夜晚=深蓝，日出日落按真实太阳位置给暖光。这在中文市场是"看起来很贵"的观感，而上游做不到自动化。
- **成本**：中等。三个类可以参考上游的 Sun 计算（本地算，不联网）。
- **建议**：和天气模块合并成一个开关，不要单开设置项。

### 7. 桌面闲置 N 分钟后显示时钟屏保 ★★★

- **上游**：`home_idle_clock_minutes` + `ClockActivity` / `ClockScene`
- **我们现状**：有 `ClockDream`（系统屏保，靠 adb 设 screensaver_components），但桌面自己不会变时钟。
- **为什么值得**：电视开机后没人操作，摆个时钟屏保比摆着桌面好看。上游这个"不会在直播电视上启动"，正好避开电视场景的尴尬。
- **成本**：低。

### 8. 内置几张壁纸 + 轮播（而不是让用户 push 文件）★★★

- **上游**：`wallpaper_captions` / `wallpaper_moods` / `WallpaperRotation` / `WallpaperMood`——从目录里轮播，可带图注，可按早/晚/夜分组
- **上游的问题**：需要用 `tools/theme.sh` 把图推到电视上，用户得会用 adb。
- **我们的做法应该不同**：既然要"装完即用"，不如**在 APK 里内置 5–8 张壁纸**，壁纸面板里直接选；再让用户能选本机图片（我们已有）。轮播和图注可以在此基础上加。
- **成本**：中等。收益主要是观感。

### 9. 顶栏收成一个齿轮 + 图标化按钮 ★★★

- **上游**：`top_menu`（输入/WiFi/设置折进一个圆齿轮）、`icon_only_buttons`、`icon_only_inputs`
- **我们现状**：顶栏有 **5 个**按钮（桌面、壁纸、输入、WiFi、设置），比上游的 3 个还挤。
- **为什么值得**：顶栏清爽是 Apple TV 观感的一部分，也给天气行腾地方。
- **成本**：中等，要重做导航，但我们的"桌面"按钮是左侧第一个（用户刚习惯了左键跳天气），改成齿轮要重新教用户。
- **建议**：做成**可选**（设置里开关），默认保持现状。别为了一次性好看把用户的肌肉记忆推翻。

---

## 五、第三梯队：建议不做（附理由）

| 上游功能 | 不做的理由 |
|---|---|
| `cinematic_home` 大货架 + 艺术图 | 视觉冲击强但吃性能，且与我们"天气行 + 顶栏"的布局正面冲突 |
| `fade_rows` 行淡出 | 需要 +6MB 显存硬件层，2GB 盒子别碰 |
| `dock_glass` / `dock_even_padding` / `app_glow` / `glow_style` / `focus_overshoot` | 纯调参，边际收益低，调错了还更糟 |
| `wallpaper_true_color` | 壁纸 8MB 显存，默认关就行，不必专门做开关 |
| `module_cards` + `addons/` 整套 | 需要 App 方配合声明卡片，零生态，是我们控制不了的依赖 |
| `restore_key_services` | 要 `WRITE_SECURE_SETTINGS`，普通用户做不到 |
| `gamepad_passthrough` | 手柄 niche，人少 |
| `stock_home_redirect` | 只有 XGIMI 之类少数固件会抢回原桌面 |
| `app_library_selection` / `library_button` | 复杂度高于收益，普通用户不需要"勾选式"管理 |
| `themes/` 主题系统（config.json + `theme.sh` 推送） | **和"装完即用"直接冲突**。要做主题，应该做成"设置里选一张内置主题"，而不是上游那套推送机制 |
| `examples/` debloat 列表 | 那是上游的主体功能（"清理电视"），但它要人拿电脑跑 adb。**建议单开一个独立工具，不塞进这个 APK** |

---

## 六、顺带要清的东西：死配置

我们 `config.xml` 有 16 项，`Config.java` 全都读了，但其中有几项**读进来就没人用**：

| 配置项 | 状态 |
|---|---|
| `module_cards` | `Config` 读了，`Cards.java` / `CardFeature.java` **不存在**，注释却写着"见 CardFeature" |
| `rows_below_dock` | `Config` 读了，`MainActivity` 里没有任何引用 |
| `shamsi_date` | 同上（波斯历，对国内用户无用） |
| `vpn_app` | 同上 |
| `show_home_settings_shortcut` | 同上 |

这些是从上游 config.xml 抄来、但对应功能我们没实现的。留着会让人以为"改这里就能开"。
**要么实现，要么从 config.xml 和 Config.java 里删掉**，别留半截。

另外上游那套 `config.json` 覆盖（`Config.load` 里读的那一坨）我们也没有推送工具，同样是"看着像能用其实不能用"。

---

## 七、建议的落地顺序

1. **输入源接住 + 常显应用名**（成本极低，先把明显的坏点补上）
2. **创维/酷开输入源**（扩品牌覆盖，每多一个品牌多一批机器）
3. **应用库**（补最大的功能缺口，工作量最大但价值最高）
4. **天气联动天空背景**（我们的独家差异化）
5. **状态行 / 闲置时钟 / 内置壁纸**（按用户反馈挑）

每一项都做成**桌面设置面板里的一行开关**，不新增设置层级——这是我们和上游最该不一样的地方。

---

## 附：本次评估未做的事

- 本次只做评估，**没有实现任何上游新功能**。
- 已在上一轮修掉的上游 7 处 bug（QuickSettings API 26 崩溃、directBootAware、网格末行、方形 banner、dock 圆角、AV 连接状态、CEC 父端口、动画重播）见 commit 说明。
- **没有提交、没有推送、没有发布 release。**
