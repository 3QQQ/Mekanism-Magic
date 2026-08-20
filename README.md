# Mekanism Magic

Mekanism Magic 是 Mekanism 与 Occultism 的附属模组，为神秘学仪式、魔灵配方和维度矿工提供 Mekanism 风格的机器、工厂、升级、自动化和 JEI 支持。

## 双版本支持

| Minecraft | Mod Loader | 分支 | 核心依赖 |
| --- | --- | --- | --- |
| 1.21.1 | NeoForge | `main` | NeoForge 21.1.x、Mekanism 10.7.x、Occultism 1.22x |
| 1.20.1 | Forge | `codex/1.20.1` | Forge 47.4.x、Mekanism 10.4.16.80、Occultism 1.158.0 |

两个版本共用相同的功能目标，但注册、配方、NBT、GUI 和 Mekanism API 分别使用对应加载器与游戏版本的实现，不能混用构建产物或依赖 JAR。

## 主要功能

- 魔灵处理器：处理 Occultism 的灵火、粉碎、结晶和交易配方。
- 魔灵工厂：基础、高级、精英、终极工厂；安装 Mekanism Extras 时支持绝对、至尊、宇宙、无限工厂。
- 仪式引擎：16 个仪式材料输入槽、仪式选择、激活、献祭和单输出。
- 微缩仪式制作机：根据真实五芒星成型材料制作微缩五芒星，粉笔槽使用 Mekanism 风格模块面板。
- 终极微缩仪式：使用 1.20.1 中四个最高级五芒星在原版工作台制作，可作为通用仪式选择器。
- 维度矿机：单个不消耗矿工输入，三行九槽输出，支持附魔和自动输出。
- Mekanism 风格 GUI、能量槽、侧面配置、速度/能量升级和 JEI 配方分类。
- Ars Nouveau 收容罐、Occultism 灵魂容器、刷怪蛋和魔灵职业数据兼容。

## 1.21.1 NeoForge

默认分支为 `main`。

推荐环境：

- Minecraft 1.21.1
- NeoForge 21.1.194 或更高
- Mekanism 10.7.15 或更高
- Occultism 1.222.0 或更高
- Java 21

## 1.20.1 Forge

当前分支为 `codex/1.20.1`。

推荐环境：

- Minecraft 1.20.1
- Forge 47.4.22
- Mekanism 10.4.16.80
- Occultism 1.158.0
- JEI 15.49.0.190
- Java 17

IDEA 工程路径：

```text
E:\IdeaProjects\Mekanism Magic Forge 1.20.1
```

构建和启动：

```powershell
.\gradlew.bat clean build
.\gradlew.bat runData
.\gradlew.bat runClient
```

Forge 分支已验证 Mekanism Generators、Mekanism Extras 1.5.0、MoreMachine 1.2.1、Ars Nouveau 4.12.7、Occultism 和 JEI 的联合加载。

## 正式版 1.0.0

Forge 1.20.1 分支已完成实际世界测试，包括机器放置、GUI、槽位显隐、配方处理、
维度矿工输出、工作台终极微缩仪式、侧面配置以及速度/能量升级。

终极微缩仪式使用以下四个 1.20.1 五芒星：

- 锻造 Marid
- 附身 Afrit
- 召唤 Marid
- 召唤野生高等魔灵

## 依赖与文档

第三方模组 JAR 不提交到仓库，需要根据目标版本放入本地 `libs/`，具体文件名和版本以对应分支的 `build.gradle`、`gradle.properties` 及文档为准。

兼容性记录：

- [Forge 1.20.1 移植状态](docs/FORGE_1201_PORT.md)
- [附属模组兼容性](docs/COMPATIBILITY.md)

构建产物位于：

```text
build/libs/mekanism_magic-1.0.0-forge-1.20.1.jar
```

请使用与 Minecraft/加载器版本匹配的分支和 JAR；不要将 NeoForge 1.21.1 构建产物放入 Forge 1.20.1。
