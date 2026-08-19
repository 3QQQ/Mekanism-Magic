# 附属模组兼容性记录

## 核心版本范围

本项目固定 Minecraft `1.21.1`，以较低依赖版本作为编译基线：

* NeoForge `21.1.194` 或更高版本
* Mekanism `10.7.15` 或更高版本
* Occultism `1.222.0` 或更高版本

已分别使用最低基线组合和 Mekanism `10.7.19.85`、Occultism `1.224.2`
组合完成编译验证。Ars Nouveau `5.13.0` 内置的 Nuggets 要求 NeoForge
`21.1.205`，因此只有安装该可选模组时才需要更高的 NeoForge。

当前开发环境已加入：

* Mekanism Extras `1.4.0` (`mekanism_extras`)
* Mekanism: MoreMachine `1.4.0` (`mekmm`)
* Ars Nouveau `5.13.0` (`ars_nouveau`，可选)

对应 JAR 位于本地 `libs/`，并通过 `compileOnly` 或按需 `localRuntime`
接入 Gradle。这些附属模组在 `neoforge.mods.toml` 中作为可选依赖，
不安装它们时本模组仍可加载。

### Mekanism Extras / MoreMachine 版本组合

实测结果：

* Mekanism Extras `1.4.0` 单独加载：兼容。
* Mekanism: MoreMachine `1.4.0` 单独加载：兼容。
* 两者同时使用 `1.4.0`：不兼容。MoreMachine `1.4.0` 新增
  `PRESSING` 工厂类型，而 Extras `1.4.0` 的类型映射只覆盖此前 6 种
  类型，会在启动时抛出 `MatchException`。
* Mekanism Extras `1.4.0` + mekmm `1.3.3`：兼容，客户端可正常完成
  模组初始化和资源加载。
* 本模组自身不依赖 mekmm `1.4.0` 的专用 API，因此可选依赖下限已放宽
  到 `1.3.3`。`mekanism_magic.compat_runtime=true` 默认使用已验证的
  Extras `1.4.0 + mekmm 1.3.3` 组合。

## Ars Nouveau 收容罐

Ars Nouveau 的实体收容罐注册名为 `ars_nouveau:mob_jar`。其捕获实体不使用原版
`ENTITY_DATA`，而是保存在同名数据组件的 `MobJarData#entityTag()` 中。本项目通过
数据组件注册表和反射读取该字段，因此 Ars Nouveau 未安装时不会硬加载其类。

Occultism 的 `soul_gem`、`fragile_soul_gem`、`trinity_gem` 和 `magic_lamp_empty`
使用原版 `ENTITY_DATA` 保存实体。魔灵处理器会像处理 Ars 收容罐一样接受其中的
Foliot、Djinni、Afrit 或 Marid；这些额外容器槽位均不消耗容器本身。

魔灵处理器仅接受装有 Occultism Foliot、Djinni、Afrit 或 Marid 系实体的收容罐；
仪式引擎则可按献祭实体标签接受任意匹配的已填充收容罐。献祭完成后只移除
`ars_nouveau:mob_jar` 数据组件，空罐会保留。

## Occultism 灵火与交易配方

Occultism `1.224.2` 内置 27 个 `occultism:spirit_fire` 配方，配方数据均不包含
`trader_id`，因此任意有效等级的魔灵来源均可在魔灵处理器或魔灵工厂中执行。

另有 14 个 `occultism:spirit_trade` 配方，它们都带有明确的 `trader_id`。机器只在
魔灵实体数据的 `spiritJob.factoryId` 与该 ID 完全一致时执行交易；普通 Foliot 或
Djinni 刷怪蛋不会再被误判成交易者。交易者输入不匹配交易配方时仍可回退处理
普通灵火配方。

## 可复用的 Mekanism GUI 入口

Mekanism Extras：

* `com.jerry.mekextras.client.gui.machine.GuiExtraFactory`
* `com.jerry.mekextras.client.gui.machine.GuiExtraAdvancedFactory`
* `com.jerry.mekextras.client.gui.GuiExtraEnergyCube`
* `com.jerry.mekextras.common.integration.mekmm.*`

Mekanism: MoreMachine：

* `com.jerry.mekmm.client.gui.machine.GuiMoreMachineFactory`
* `com.jerry.mekmm.client.gui.machine.GuiPresser`
* `com.jerry.mekmm.client.gui.machine.GuiRecycler`
* `com.jerry.mekmm.api.recipes.MoreMachineRecipeTypes`
* `com.jerry.mekmm.api.recipes.TripleItemToItemRecipe`

本项目的机器现在也建立在 Mekanism 的 `Machine`/`BlockTile`、`TileEntityMekanism`、`GuiConfigurableTile`、`GuiSlot`、`GuiProgress`、`TileComponentConfig`、`TileComponentUpgrade` 和 `MekanismTileContainer` 体系上。后续若要做更深层适配，应优先复用这些基类和 recipe type，而不是复制其内部实现。

## 后续适配方向

1. 在 JEI/EMI 中为仪式机和魔灵机注册独立 recipe category。
2. 对 `mekmm` 的三输入配方、压制/回收等 recipe type 做可选桥接。
3. 对 `mekanism_extras` 的工厂/高级工厂做自动化输入输出兼容。
4. 如果需要兼容它们的升级、侧面配置或工厂复制行为，再分别实现对应的 Mekanism tile/container 接口。

## 工厂与升级语义

Mekanism 的 `speed_upgrade` / `energy_upgrade` 是普通机器的升级物品；Mekanism Factory 则是独立的工厂方块和 `TileEntityFactory` 体系，不是可以放入普通机器的“工厂升级物品”。两台基础机器支持前者的升级槽；魔灵处理器另有独立的 Spirit Factory 方块和并行工位。

当前已注册魔灵工厂的四个 Mekanism Tier Installer 等级：基础 3 工位、高级 5 工位、精英 7 工位、终极 9 工位。工厂使用 `TileEntityFactory` 的缓存处理、排序、能量和原生 Factory 容器布局；魔灵配方仍由 Occultism 运行时桥接提供。

安装 Mekanism Extras 后，本模组通过独立的可选集成注册其四种
`ExtraFactoryTier`：

* 绝对魔灵工厂：11 工位
* 至尊魔灵工厂：13 工位
* 宇宙魔灵工厂：15 工位
* 无限魔灵工厂：17 工位

高阶工厂使用 Extras 自带的 `TileEntityExtraFactory`、Factory 容器、
等级灯和 `ItemExtraTierInstaller` 升级链。Extras 未安装时这些类不会被
加载，基础模组仍只注册 Mekanism 原版四级工厂。

安装 JEI `19.39.0` 或更高版本时，本模组会注册“微缩五芒星制作”“神秘仪式”和
“魔灵处理”三个配方分类：前者显示 18 种五芒星成立材料、法阵粉笔和微缩输出，
第二类显示可由仪式机器处理的具体仪式材料、激活物品、献祭示例和输出，第三类显示灵火、粉碎、
结晶和交易处理的魔灵来源、输入与输出。机器都会注册为对应配方催化器。

## 微缩仪式制作机

`mekanism_magic:mini_ritual_assembler` 使用 Mekanism 普通机器框架，输入槽接受最多
16 个法阵成型材料，不要求生存中无法正常获取的 `ritual_dummy/*` 物品。机器会通过当前
`RecipeManager` 找到对应法阵并逐槽匹配材料，输出 18 种五芒星微缩仪式之一；具体仪式
仍由仪式引擎根据五芒星、材料、激活物品和献祭条件确定。机器消耗匹配材料后输出
`mekanism_magic:mini_ritual`；输出物品的 `CUSTOM_DATA.ritual`
用于仪式机器精确匹配，`CUSTOM_DATA.pentacle` 用于客户端显示对应微缩法阵。

微缩仪式制作机另有 16 个颜色粉笔槽：黑、蓝、棕、青、灰、绿、淡蓝、淡灰、黄绿、
品红、橙、粉、紫、红、白、金。槽位不包含彩虹粉笔或虚空粉笔专用槽，但这两种
通用粉笔放入任意颜色槽后会满足所有法阵颜色判定；粉笔槽只用于检查，不参与消耗。
客户端主工作界面默认收起粉笔槽，可通过右侧粉笔 Tab 展开 4×4 粉笔模块。
