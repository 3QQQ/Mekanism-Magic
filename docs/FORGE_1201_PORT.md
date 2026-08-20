# Forge 1.20.1 移植状态

当前分支目标为 Minecraft 1.20.1 + Forge 47.4.22，使用 Java 17。

## 已完成

* Gradle 已切换到 ModDevGradle Legacy Forge 模式。
* 本地模组依赖通过 `modCompileOnly` / `modLocalRuntime` 进行开发环境重映射。
* 已适配 Mekanism 10.4.16.80、Occultism 1.20.1-1.158.0 和 JEI 15.49.0.190。
* `mods.toml`、资源包格式、Forge 注册 API 和客户端屏幕注册已迁移。
* ItemStack 数据组件已迁移到 1.20.1 NBT。
* Occultism 配方遍历、配方输入容器和运行时配方桥已迁移。
* Mekanism 机器基类、能量容器、升级数据、缓存配方与四级魔灵工厂已迁移到 10.4 API。
* JEI 15 插件和配方分类已完成源码适配。
* 资源目录已改为 1.20.1 的 `recipes` / `loot_tables`，合成结果字段已改为 `item`。
* 复合机器模型已从 `neoforge:composite` 改为 `forge:composite`。
* 粉笔模块与魔灵全典模块已用 Mekanism 10.4 的侧边标签组件恢复。
* 通用机器界面已重新使用 Mekanism 原生背景与边框渲染。
* Ars Nouveau 1.20.1 Mob Jar 已按 `BlockEntityTag.entityTag` NBT 格式恢复软兼容。
* Mekanism Extras 1.5.0 的绝对、至尊、宇宙、无限魔灵工厂已恢复，分别提供 11/13/15/17 个处理槽。
* 终极魔灵工厂已重新连接到 Extras 的绝对工厂升级链。
* MoreMachine 1.2.1 已加入共存验证，并覆盖其四个错误使用 `neoforge:composite` 的绘制工厂模型。
* 已同步 1.21.1 后续的终极微缩仪式严格配方：必须使用四个最高等级 Marid 五芒星。
* 已同步真实五芒星成型材料匹配、微缩仪式候选预览/手动选择和仪式特殊效果过滤。
* 已同步维度矿工使用 Occultism 维度矿井合成、256 堆叠输出、额外输出吞吐和独立矿工界面布局。
* 已同步机器工作动画、客户端进度同步、工厂逐进程时长同步、标准 Mekanism 自动输入输出及能量槽充能。
* 已同步维度矿工和高阶魔灵工厂的专用纹理资源。
* 已同步 1.21.1 的可选 Occultism 门控；关闭 Occultism 时不会注册本模组内容。
* 已同步 Forge 1.20.1 的显式服务端方块实体 ticker、标准侧面输入输出和自动弹出。
* 已新增 `docs/ARCHITECTURE.md`，整理通用机器框架与各模组集成边界。

## 已验证

* `gradlew clean build` 成功。
* `gradlew runData` 成功。
* `gradlew runClient` 能完成模组加载并进入主菜单，未产生新的崩溃报告。
* 客户端日志中已无 Mekanism Magic 模型加载错误。
* `gradlew runServer` 能完成专用服务端模组加载并到达 EULA 检查。
* Mekanism、Generators、Extras、MoreMachine、Occultism、Ars Nouveau 和 JEI 全套开发环境可共同加载到主菜单。
* 迁移后的完整客户端启动未出现 Mekanism Magic 专用崩溃或模型加载错误。
* `gradlew runClient "-Pmekanism_magic.occultism_runtime=false"` 可在无 Occultism
  运行依赖时完成基础客户端加载。

## 尚未完成

* 需要在实际世界中逐台放置机器，验证方块实体、容器、GUI、侧面配置、升级和配方处理。
* 需要在进入世界后验证 JEI 配方查看、仪式数据加载和维度矿工输出。

因此当前版本已经达到“可构建、可启动到主菜单”的阶段，但还不能视为完成的 1.20.1 发布版。
