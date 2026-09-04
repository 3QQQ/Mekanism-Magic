# Mekanism Magic 代码边界

新增机器必须遵循 [新增机器契约](ADDING_MACHINES.md)。公共槽位权限、生命周期、
拆机持久化和资源完整性已经集中处理，不应从现有机器复制一套实现。

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

### `integration/common/`

所有模组都可复用的稳定边界集中在这里：

- `common/recipe/`：机器输入消耗、输出、处理时长、完成回调和特殊输入处理。
  通用机器实体只依赖这些类型，不再依赖某个魔法模组的配方桥接类。
- `common/entity/`：可复用实体容器注册表。不同模组只需提供
  `EntityContainerAdapter`，即可统一向仪式献祭、魔灵识别及后续机器暴露实体数据。
- `common/network/MachineDirectOutputHooks`：只负责可选存储网络的状态、直接写入和
  输出 tick，不承载节点生命周期。
- `common/network/MachineNetworkLifecycleHooks`：只负责可选网络节点的加载、保存、
  卸载、复活和最终拆除；处理器必须在适配包内过滤自身支持的机器。

新增适配时禁止把第三方模组类型重新引入 `blockentity/`。需要特殊完成行为时，
通过 `MachineRecipeResult` 的完成回调和特殊输入处理器提供。

### `integration/occultism/`

Occultism 专用逻辑集中在此处：

- `OccultismRecipeBridge`：运行时配方、五芒星、魔灵、仪式、献祭和投影解析。
- `OccultismRitualMachineMode`：集中声明 11 类原版仪式的机器动态结果语义，供机器与
  JEI 共用，工作魔灵在实体落地时执行原版职业初始化。
- `OccultismSpiritJobPolicy`：集中声明 26 个职业的实体等级、工作类型、旧 ID 迁移和
  JEI/机器共同门控，禁止各机器各自猜测职业能力。
- `MiniPentacleDeployment`：微缩法阵的原子展开/回收、权限事件、结构守卫与防复制状态。
- `OccultismEntityContainerAdapter`：读取并清空使用 `ENTITY_DATA` 的灵魂容器。
- `OccultismSpiritJobConfig`：魔灵等级和职业配置读取。
- `SpiritFactoryRecipe`：Occultism 配方转换为 Mekanism 工厂缓存配方。

后续兼容新的 Occultism 版本时，优先修改这里，不要修改通用机器处理循环。

### `integration/arsnouveau/`

Ars Nouveau 专用逻辑集中在此处：

- `ArsNouveauIntegration`：作为 `EntityContainerAdapter` 读取和清空
  `mob_jar` 数据组件；后续新生魔艺配方、法术或召唤适配继续拆分为该包内的独立桥接。

没有安装 Ars Nouveau 时，这里的代码必须继续通过反射/注册表方式安全降级。

### `IntegrationBootstrap`

这是可选适配器的集中注册入口。只在对应模组已加载时把适配器加入通用注册表。
它同时通过 `ContentIntegrationModule` 管理各可选模组的内容登记。当前
`OccultismContentModule` 登记已有机器和物品；后续 Ars Nouveau 内容应使用新的
模块登记，而不是扩展 Occultism 的总开关。机器、GUI 和 JEI 不直接执行
`ModList` 判断。

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
2. 需要注册内容时实现独立 `ContentIntegrationModule`；第三方类只允许出现在
   该包或反射字符串中。
3. 机器实体只调用适配层的稳定方法或数据记录。
4. 配方输出统一转换为通用 `MachineRecipeResult` 或对应的 Mekanism 缓存配方。
5. GUI 只负责显示和交互，不直接解析第三方配方。
6. 可选模组未安装时，基础模组必须能完成 `clean build` 和客户端启动。
7. 每次适配改动都要验证：
   - 基线依赖组合；
   - 可选模组未加载；
   - 可选模组单独加载；
   - 资源和数据包加载；
   - 自动输入输出和升级组件。

## 新生魔艺后续适配入口

1. 实体收容类物品继续实现 `EntityContainerAdapter`，不要在 Occultism 桥接中增加
   Ars Nouveau 专用分支。
2. 配方处理建立 `integration/arsnouveau/` 下的独立 recipe bridge，并统一返回
   `MachineRecipeResult`。
3. 如果新增机器，注册、机器实体、GUI 和 JEI 分类分别保持在现有层级；第三方 API
   只允许出现在 Ars Nouveau 适配包内。
4. Ars Nouveau 未加载时不得注册对应内容，也不得让 JVM 解析其实现类。

具体配方类型统计与推荐实现顺序见
[`ARS_NOUVEAU_ADAPTATION.md`](ARS_NOUVEAU_ADAPTATION.md)。

## Ars 开发资源

Ars Nouveau 机器数据放在 `src/arsDev/resources/`，默认加入普通构建，并在
`META-INF/mekanism_magic_features.properties` 中写入启用状态。保持机器内容默认
启用可确保方块、物品和方块实体注册 ID 在重启后不发生缺失映射。

## Forbidden & Arcanus 后续适配入口

1. 所有 Forbidden & Arcanus 类型引用限制在 `integration/forbiddenarcanus/`；通用
   机器、GUI 以及其他魔法模组适配包不得直接引用其 `common.*` 实现类。
2. `2.6.1` 没有独立公开 API 包，配方、动态注册表、能力与数据组件访问必须集中在
   可替换的精确版本桥接中；升级依赖前先做 ABI diff，不允许实现类泄漏到通用层。
3. Aureal、Souls、Blood 与 Experience 是四个独立精华通道，不得合并成单一魔力、
   流体、Ars Source 或其他模组资源。
4. Clibano 配方从 `RecipeManager` 读取；赫菲斯托斯仪式、增强物、物品修饰、法阵和
   残渣类型从对应动态注册表读取。数据包重载只刷新适配缓存，不在机器 tick 或 GUI
   中反复全表扫描。
5. Forbidden & Arcanus 未加载时不得注册对应内容或解析实现类；Valhelsia Core 仅是
   该模组加载后的传递依赖，不应变成本模组在无适配内容时的强制依赖。

目标版本、分阶段范围和验收矩阵见
[`FORBIDDEN_ARCANUS_ADAPTATION.md`](FORBIDDEN_ARCANUS_ADAPTATION.md)。
