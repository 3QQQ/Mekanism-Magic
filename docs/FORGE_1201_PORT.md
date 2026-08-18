# Forge 1.20.1 移植状态

当前分支目标为 Minecraft 1.20.1 + Forge 47.4.22。

已完成：

* Gradle 已切换到 ModDevGradle Legacy Forge 模式。
* Java 工具链切换为 Java 17。
* 已准备 Mekanism 10.4.16.80、Occultism 1.20.1-1.158.0、JEI 15.49.0.190
  及 Occultism 的 Forge 依赖。
* `mods.toml` 和资源包格式已切换到 Forge/Minecraft 1.20.1。
* NeoForge 包名已批量替换为 Forge 包名。

尚未完成：

* 1.21 数据组件需要迁移到 1.20.1 的 ItemStack NBT。
* RecipeHolder、SingleRecipeInput、ItemHandlerRecipeInput 等配方 API 需要迁移。
* TileEntityMekanism、能量容器、升级数据和侧面配置 API 需要按 Mekanism 10.4 重写。
* Mekanism Factory 10.4 的缓存和工厂接口与 10.7 不兼容。
* JEI 15 的插件/分类接口需要重新适配。
* Ars Nouveau、Mekanism Extras 和 MoreMachine 的 1.20.1 版本尚未加入。

因此当前分支是移植起点，尚未达到可运行状态；不要用它替换 1.21.1 的正式 JAR。
