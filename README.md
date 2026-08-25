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
- 终极微缩仪式：使用四个最高等级 Marid 五芒星制作，可作为通用仪式选择器。
- 维度矿工处理机：单个不消耗矿工输入，三行九槽输出，支持附魔和自动输出。
- 安装 Mekanism Extras 时，维度矿机支持最高 256 倍并行的堆叠升级。
- Ars 机器提供“创造魔力升级”，使用 Mekanism Extras 时可放入原版升级界面。
- Ars 机器在 FE 不足时可依靠足量 Source 缓慢工作，供电恢复后自动恢复正常速度。
- Ars 机器 GUI 提供独立的“魔力配置”界面，可分别设置六个方向的 Source 输入/输出。
- 魔源转换机可消耗 FE 直接生成 Source，并输出到 Ars Source 网络。
- 催化剂标识制作机可将 3 个灌注材料合成为一个不消耗的催化剂标识；
  灌注处理机使用可展开标识库，并支持从 JEI 拖拽标识锁定配方。
- 安装 AE2 后，灌注处理机支持带 `recipe_id/catalyst_id` 虚拟上下文的 AE 自动化。
- 魔法灌注工厂包含 Mekanism 四级工厂；安装 Mekanism Extras 时扩展至
  绝对、至尊、宇宙、无限等级（最高 17 个并行进程）。
- 安装 Mek Energistics 时，维度矿机支持 ME 升级弹出机制。
- Mekanism 风格 GUI、能量槽、侧面配置、速度/能量升级和 JEI 配方分类。
- Ars Nouveau 收容罐、Occultism 灵魂容器、刷怪蛋和魔灵职业数据兼容。
- Ars Nouveau 收容罐兼容；魔源增幅器、灌注处理机、附魔装置处理机和
  德格米生态模拟器在完成全部适配验证前不在发布版中启用。
- 开发构建提供参考 Mekanism 连接形态的魔力管道，用于 Ars Source 的相邻
  管道传输。

## 1.21.1 NeoForge

默认分支为 `main`。

推荐环境：

- Minecraft 1.21.1
- NeoForge 21.1.194 或更高
- Mekanism 10.7.15 或更高
- Occultism 1.222.0 或更高
- Java 21

IDEA 中以 Gradle 项目打开根目录，刷新 Gradle 后运行 `client` 或 `server`。

项目还提供两个 IDEA 专用 Ars Nouveau 测试配置：

- `Ars Compatibility Client`：启动带 Ars Nouveau 运行时和开发机器内容的客户端；
- `Ars Compatibility Server`：启动同样开关的专用服务器。

这些配置只在当前 IDEA 启动时传入
`-Pmekanism_magic.ars_runtime=true` 和
`-Pmekanism_magic.ars_machine_content=true`，不会修改
`gradle.properties`，因此普通 `build` 和正式发布包仍保持关闭 Ars 开发机器内容。

## 1.20.1 Forge

切换到 `codex/1.20.1` 分支。

推荐环境：

- Minecraft 1.20.1
- Forge 47.4.22
- Mekanism 10.4.16.80
- Occultism 1.158.0
- JEI 15.49.0.190
- Java 17

Forge 分支工程位于：

```text
E:\IdeaProjects\Mekanism Magic Forge 1.20.1
```

构建和启动：

```powershell
.\gradlew.bat idea build
.\gradlew.bat runClient
```

Forge 分支已验证 Mekanism Generators、Mekanism Extras 1.5.0、MoreMachine 1.2.1、Ars Nouveau 4.12.7、Occultism 和 JEI 的联合加载。

## 依赖与文档

第三方模组 JAR 不提交到仓库，需要根据目标版本放入本地 `libs/`，具体文件名和版本以对应分支的 `build.gradle`、`gradle.properties` 及文档为准。

兼容性记录：

- [Forge 1.20.1 移植状态](docs/FORGE_1201_PORT.md)
- [附属模组兼容性](docs/COMPATIBILITY.md)
- [Ars Nouveau 适配准备](docs/ARS_NOUVEAU_ADAPTATION.md)

构建产物位于：

```text
build/libs/mekanism_magic-1.0.1-neoforge-1.21.1.jar
```

请使用与 Minecraft/加载器版本匹配的分支和 JAR；不要将 NeoForge 1.21.1 构建产物放入 Forge 1.20.1。
