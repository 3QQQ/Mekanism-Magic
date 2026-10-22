# AE Terminal Complete 2.3 — JEI 图集热重载兼容

用户在项目实际 JEI 19.39.0.369 中反馈：翻页键、配方箭头、格子与边框显示为错误色块。
本次只修改资源包的 JEI 覆盖，不添加客户端补丁，不改模组代码、JAR 或版本。

## 确认的原因

已在相同 JEI JAR 中实际复现：先打开原版 JEI，再在同一客户端中启用 2.2，
会出现用户截图里的错位；完整重新启动、在第一次打开 JEI 前启用 2.2 则正常。

JEI 的 `DrawableSprite` / `ScalableDrawable` 使用 `LazySupplier` 缓存 sprite 对象。
资源重载更新了图集，但这层缓存未刷新。2.2 中的四倍贴图和额外跨版本别名使图集重新排位，
旧 UV 随后读到新的其他图块，连本身没有改变尺寸的配方箭头也受影响。

实测 recipe_arrow 的旧 UV 为 `[0.65625,0.5,0.7421875,0.5625]`，
2.2 重载后真实 UV 为 `[0.40625,0.828125,0.42773438,0.84375]`，缓存仍保留旧值。
这是此前只查贴图／菜单、未测试 JEI 热重载所漏掉的实际兼容问题。

未启用包的 JEI 配方窗同样具有较高窗口和大块留白；这是 JEI 的窗口布局，不是贴图把
窗口高度拉长。本次不擅自改 JEI 配置、配方布局或玩家设置。

## 纯资源包修正

- JEI 目录使用当前版本原生的 46 张 PNG，以及对应缩放元数据；不再额外加入旧版／新版
  图集别名，不改变当前图集的条目集合。
- 将适用的高分辨率参考图采样到原生像素尺寸，保留 Mek 亮色与参考的控件外形。
- JEI 的 45 个实际图集 sprite 在重载前后位置、尺寸及 UV 全部一致。
- 非 JEI 的所有 `assets/` 文件与 2.2 逐字节相同，既有按钮、菜单、字体策略不变。

## 实际验证

使用项目本地 JEI 19.39.0.369、Minecraft 1.21.1、NeoForge 21.1.244、Mekanism 和
Mekanism Magic，在独立临时世界里完成：

- 冷启动启用 2.3：创造背包＋JEI 侧栏、橡木合成、熔炉、酿造页面。
- 先看未启用状态，再热重载为 2.3：相同四项页面；所有图集 UV 不变。
- 截图在 JEI 侧栏绘制之后取得，明确检查右侧物品列表实际显示，而非只截容器本身。
- 静态资源、参数、历史文件哈希检查通过；测试客户端和集成服务器正常退出。

测试代码仅位于 tools 的独立验收 source set，不进入安装包。辅助模组不是全套逐一游玩验证。

复验命令：

```powershell
.\gradlew.bat -I tools/jei-pack-smoke.init.gradle runJeiPackClient "-Pmekanism_magic.occultism_runtime=false" "-PuiPackProbe=AE-Terminal-Complete-Light-2.3.zip" "-PuiPackStable=true" --no-daemon
.\gradlew.bat -I tools/jei-pack-smoke.init.gradle runJeiPackClient "-Pmekanism_magic.occultism_runtime=false" "-PuiPackProbe=AE-Terminal-Complete-Light-2.3.zip" "-PuiPackCold=true" --no-daemon
```

## 交付与安装注意

`dist/resourcepacks/AE-Terminal-Complete-Light-2.3.zip`，8,934,094 字节。
SHA-256：`2257aac21dfe5408e092faddb6efcbd596700f1cc678ebc48d657737206b3faa`。

启用 2.3、停用其他重叠 UI 包后，**完整退出 Minecraft 并重新启动一次**。
旧 2.2 已留下的进程内 UV 缓存不保证通过退出存档或 F3+T 清除；本包不修改 JEI 的代码。
之后原版与 2.3 之间的热重载已实测稳定。兼容范围明确限定为所用 JEI 19.39.0.369。

同名 `-jei-hot.json` / `-jei-cold.json` 是原始验收记录，`-jei-*.png` 是实际配方截图。
1.8、2.0、2.1、2.2 均未覆盖；`gradle.properties` 仍为原来的 1.0.6.47。
