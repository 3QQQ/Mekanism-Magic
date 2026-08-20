# Mekanism Magic 代码边界

Forge 1.20.1 分支与 1.21.1 分支保持相同的职责分层，但使用 Forge、
Minecraft 1.20.1 和 Mekanism 10.4 的对应 API。

## 核心层

### `block/`

只放机器方块包装类，不放第三方配方解析。

### `blockentity/`

只放机器实体和通用处理流程：

- `NativeMagicMachineBlockEntity`：能量、升级、侧面配置、输入输出、进度和消耗流程。
- `NativeSpiritProcessorBlockEntity`：魔灵处理器。
- `NativeRitualEngineBlockEntity`：仪式引擎。
- `NativeMiniRitualAssemblerBlockEntity`：微缩五芒星制作机。
- `NativeDimensionMinerBlockEntity`：维度矿工。
- `NativeSpiritFactoryBlockEntity`：Mekanism 原版工厂。

机器实体通过适配层的稳定方法读取 Occultism/Ars Nouveau 数据，不直接依赖第三方实现类。

## 适配层

### `integration/occultism/`

集中处理 Occultism 配方、仪式、五芒星、魔灵和灵魂容器。

### `integration/arsnouveau/`

集中处理 Ars Nouveau 1.20.1 `BlockEntityTag.entityTag` 格式的 Mob Jar。

### `integration/mekextras/`

集中处理 Mekanism Extras 的 11/13/15/17 槽高阶魔灵工厂及升级链。

### `integration/jei/`

集中处理 JEI 分类、配方数据和机器催化器。

### `integration/ModCompatibility`

集中管理可选模组加载判断。未安装 Occultism 时，基础注册、数据配方和 JEI 内容均不启用。

## 注册层

`NativeMekanismRegistries` 负责机器方块、方块实体、容器、能量和升级属性注册；
可选 Extras 通过独立入口反射加载，避免基础分支硬链接附属实现。

`MekanismMagic` 负责物品、创造标签页、自定义终极微缩仪式配方序列化器和可选集成入口。

## 版本边界

1.20.1 使用传统 `ItemStack` NBT、Forge `DeferredRegister`、Forge `mods.toml`、
`CraftingContainer` 和 Mekanism 10.4 的 Tile/Container API。

1.21.1 使用数据组件、NeoForge 注册器和对应的 `CraftingInput`/NeoForge API。
两个分支的实现不能直接混合。
