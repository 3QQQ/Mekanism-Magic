# 附属模组兼容性记录

## Forge 1.20.1 基线

- Minecraft `1.20.1`
- Forge `47.4.22`
- Java `17`
- Mekanism `10.4.16.80`
- Occultism `1.158.0`
- JEI `15.20.0.100`

Occultism 是本分支的主可选集成目标。默认开发运行加载 Occultism；可用以下参数测试无
Occultism 模式：

```powershell
.\gradlew.bat runClient "-Pmekanism_magic.occultism_runtime=false"
```

无 Occultism 时，本模组不会注册机器、物品、容器、配方、战利品表或 JEI 内容。

## 可选模组

默认 `gradle.properties` 不加载以下附属模组，避免附属模组自身资源或 JEI 问题干扰核心验证：

- Ars Nouveau `4.12.7`
- Mekanism Extras `1.5.0`
- Mekanism: MoreMachine `1.2.1`

需要测试时启用：

```properties
mekanism_magic.ars_runtime=true
mekanism_magic.mekextras_runtime=true
mekanism_magic.mekmm_runtime=true
```

Mekanism Generators `10.4.16.80` 默认加载，因为 Extras 的部分扫描逻辑依赖其类。

## Ars Nouveau 收容罐

1.20.1 的 `ars_nouveau:mob_jar` 使用传统 `BlockEntityTag.entityTag` NBT，
不是 1.21.1 的数据组件。本模组通过注册表判断物品并直接读取该 NBT，不硬链接 Ars
Nouveau 实现类。

## Mekanism Extras

Extras `1.5.0` 提供绝对、至尊、宇宙、无限四种高阶工厂。本模组将魔灵工厂接入其
11/13/15/17 处理槽和升级链。

四个高阶工厂配方使用 Forge 条件配方，仅在 Mekanism Extras 存在时加载。高阶工厂
方块自行返回对应方块物品，因此无需在默认资源包中保留会引用未注册物品的战利品表；
未安装 Extras 时不会再产生未知高阶工厂物品的配方或战利品表错误。

## MoreMachine

MoreMachine `1.2.1` 可与本模组共同加载。本分支覆盖其四个绘制工厂模型中的
`neoforge:composite` 为 `forge:composite`，避免 Forge 资源加载器错误。

MoreMachine 自身旧版 JEI 注册可能出现 `customRecipeMap` 空指针；该错误来自附属模组
自身，不影响 Mekanism Magic 的核心构建。

## GUI 与自动化

机器基于 Mekanism 10.4 的 `Machine`、`TileEntityMekanism`、
`MekanismTileContainer`、`GuiConfigurableTile`、侧面配置、能量槽和升级组件。
魔灵槽、仪式槽、激活槽、献祭槽、魔灵全典和粉笔模块均按 Mekanism 的额外槽语义接入。
