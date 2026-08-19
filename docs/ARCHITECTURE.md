# Mekanism Magic 代码边界

本项目按“通用 Mekanism 机器框架”和“可选模组适配”分层。新增兼容内容应优先放入
`integration/`，不要直接把第三方模组判断、反射调用或配方解析塞进机器实体。

## 核心层

### `block/`

只放机器方块包装类，例如 `NativeMachineBlock`。这里不放配方和第三方模组逻辑。

### `blockentity/`

只放机器实体和通用机器处理流程：

- `NativeMagicMachineBlockEntity`：能量、升级、侧面配置、输入输出、进度和通用消耗流程。
- `NativeSpiritProcessorBlockEntity`：魔灵处理器机器本身。
- `NativeRitualEngineBlockEntity`：仪式引擎机器本身。
- `NativeMiniRitualAssemblerBlockEntity`：微缩五芒星制作机机器本身。
- `NativeDimensionMinerBlockEntity`：维度矿机机器本身。
- `NativeSpiritFactoryBlockEntity`：Mekanism 原版工厂机器本身。

机器实体可以调用适配层提供的稳定方法，但不应直接引用 Occultism、Ars Nouveau、
Mekanism Extras 或其他附属模组的实现类。

## 适配层

### `integration/occultism/`

Occultism 专用逻辑集中在此处：

- `OccultismRecipeBridge`：运行时配方、五芒星、魔灵、仪式、献祭和投影解析。
- `OccultismSpiritJobConfig`：魔灵等级和职业配置读取。
- `SpiritFactoryRecipe`：Occultism 配方转换为 Mekanism 工厂缓存配方。

后续兼容新的 Occultism 版本时，优先修改这里，不要修改通用机器处理循环。

### `integration/arsnouveau/`

Ars Nouveau 专用逻辑集中在此处：

- `ArsNouveauIntegration`：`mob_jar` 数据组件读取、实体识别和清空收容罐。

没有安装 Ars Nouveau 时，这里的代码必须继续通过反射/注册表方式安全降级。

### `integration/mekextras/`

Mekanism Extras 专用工厂、方块类型、等级和升级链集中在此处。该包只能在
`mekanism_extras` 已加载时初始化。

### `integration/jei/`

JEI 分类和配方展示集中在此处。JEI 显示数据应通过适配层获取，不应在 JEI 类中
直接扫描第三方配方实现类。

## 注册层

### `NativeMekanismRegistries`

只负责方块、方块实体、容器和 Mekanism 机器属性注册。可选模组注册通过独立入口
反射调用，不应把 Extras 或其他模组的硬链接类引入基础注册路径。

### `MekanismMagic`

只负责基础物品、配方序列化器、创造标签页和可选集成入口。

## 新增兼容模组的规则

1. 新模组建立独立包：`integration/<modid>/`。
2. 第三方类只允许出现在该包或反射字符串中。
3. 机器实体只调用适配层的稳定方法或数据记录。
4. 配方输出统一转换为通用 `RecipeResult` 或对应的 Mekanism 缓存配方。
5. GUI 只负责显示和交互，不直接解析第三方配方。
6. 可选模组未安装时，基础模组必须能完成 `clean build` 和客户端启动。
7. 每次适配改动都要验证：
   - 基线依赖组合；
   - 可选模组未加载；
   - 可选模组单独加载；
   - 资源和数据包加载；
   - 自动输入输出和升级组件。
