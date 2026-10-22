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
- `common/network/MachineAeResourceHooks`：单机原生 AE 供给开关、状态同步与共享服务器 tick/生命周期入口；所有普通机与工厂共用，核心不链接 AE API。
- `common/network/MachineAeNodeOwnershipHooks`：已安装网络升级的单一节点所有权；原生资源节点必须让位，不能以升级节点暂时离线为由创建第二个节点。

新增适配时禁止把第三方模组类型重新引入 `blockentity/`。需要特殊完成行为时，
可通过 `MachineRecipeResult` 的完成回调和特殊输入处理器提供。实际修改世界或生成
实体的机器须在适配包内明确实现校验、消费与失败回滚边界；可组合
`nativeBaseUpdate` / `finishServerUpdate`，不能默认通用完成回调与随后资源消费是
不可失败的原子操作。

### `src/jade/`

Jade 通过 `@WailaPlugin` 发现可选插件，公共启动代码不引用其 API。
`MachineJadeData` 仅生成有界的只读资源/进度/网络状态；服务端先检查 Mekanism 访问权限，不同步库存、配方和完整存档 NBT。FE 由 Jade 原生能力提供器显示，避免重复；Ars、Nature 与 Extras 读取保持独立可选类加载边界。

### `integration/forbiddenarcanus/`

`HephaestusForge261Bridge` 只准备原生物品结果或本机等级变化，不执行世界锻炉替换。
`HephaestusForgeProcessorBlockEntity` 持有1—5级、四精华和一次性已付款回执；
历史核心/锤位置仅作单向返还，不是当前菜单或配方角色。
`EssenceCondenserBlockEntity` 的四个保留样本工位各自保存进度和整数缓存，
`NativeEssenceVessel261Bridge` 在副本上准备原版单容器装填；
`EssenceBankFluidTank` 仅是同一缓存的可选PackagedFAA流体视图，禁止独立保存第二份余额。
旧三台精华机不保留注册表别名或NBT迁移。`RetiredEssenceMachines` 在区块调色板
解码前只把三个退休ID置为空气并去掉对应方块实体，不掉落、不转存其内容；
其他方块、容器、新凝聚器和锻炉数据保持不动。

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

### `integration/naturesaura/`

Nature's Aura 的可选入口与 41.9 配方实现隔离在此处：

- `NaturesAuraContentModule`：只在 `naturesaura` 已加载时反射创建监听器。
- `NatureAuraRecipeBridge`、`OfferingRecipeBridge`、`TreeRitualRecipeBridge` 与
  `AnimalSpawnerRecipeBridge`：分别隔离四类 Nature's Aura 非 API 配方实现。
- `NatureAuraRecipeScanner`：按 `RecipeManager` 弱键保存不可变祭坛与 Offering
  目录，并提供统一重载版本；Tree/Animal 的弱键目录随该版本失效，GUI 不单独扫描。

`SafeAuraContainer`、可组合的 `NatureAuraStorageComponent` 与
`AuraFieldInterfaceBlockEntity` 分别承担守恒储槽、普通机/工厂共用的 Nature 能力和
世界端点；`NatureAltarProcessorBlockEntity` 与 `NatureAltarFactoryBlockEntity` 只消费
桥接后的祭坛配方，不直接访问世界 Aura。工厂使用 Mekanism 原生调度，但通过专用
升级载荷和 `getFactoryType() == null` 保持自身身份，避免被外部集成替换为原版熔炉。
`NatureAltarAutomationHost` 是可选 AE/MekE 的原子投递边界：虚拟 context 只保存
recipe ID 和内容签名，不作为材料发送；真实 catalyst 仍由机器持久槽提供且不消耗。
机器不自带 AE 节点，避免与 MekE 升级产生双 provider。Aura 通道实现
`MagicPipeResourceHooks`，复用魔力管道拓扑但独立保存数量、容量和类型；Ars API
通过可选 adapter 接入内部 `PipeSourceStorage`，共用管道不要求安装 Ars。
`OfferingRecipeBridge` 与 `OfferingProcessorBlockEntity` 独立处理原版献祭花环和整批
触发，待产物与单次随机副产物持久化，不人为增加原配方不存在的 Aura 消耗。
`TreeRitualProcessorBlockEntity` 通过公共 `BoundedSlotMatcher` 聚合八槽材料用量，
保留真实黄金粉环、种植与成树消耗；`TreeRitualEnvironment` 和
`TreeRitualWorldActions` 分别承担已加载区域检查及有权限事件的世界修改/回滚。
已付款的种植状态与待产物通过统一存档及 `MachineDropPreserver` 拆机链保存。
`AnimalSpawnerProcessorBlockEntity` 使用独立生成提交流程，保留真实世界实体结果，
在事件回调后复核资源与世界状态；`AnimalSpawnerEnvironment` 负责底座、碰撞、区块
和实体数量限制。`RecipeEntityDisplayState` 同步显示数据，渲染副本不加入世界。
Offering、Tree Ritual、Animal Spawner 均禁用 ME 样板升级自动下单，但保留普通物品
能力与侧面配置，管道/AE 导出总线可提供真实输入。普通外置 AE2 图样供应器另通过
`ICraftingMachine` 的整批物品入口投料；该入口复用加工槽，并为固定角色槽规划分组，
不依赖机内网络升级。见 [外置供应器说明](EXTERNAL_PATTERN_PROVIDER.md)。Tree/Animal 已纳入本轮 48/48 项
联合服务端 GameTest（树 4 项、动物 10 项），无 Ars/Occultism 的独立 Nature 组合
24/24 项通过；客户端视觉与帧率验收待完成。

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

## Nature's Aura 已实现边界

1. 所有 Nature's Aura API 硬链接必须限制在 `integration/naturesaura/`；通用机器、
   GUI、Occultism 和 Ars Nouveau 包不得直接引用其实现类。
2. 世界 Aura 只能通过 `IAuraChunk` 的公开 drain/store 语义访问，方块和物品容器
   通过 `IAuraContainer` 能力互操作；不得把 Aura 伪装成流体或 Ars Source。
3. 区域 Aura 查询和回注只在服务端限频执行，不得因 GUI 打开、并行工位或渲染帧
   增加扫描频率，也不得强加载远处区块。
4. Nature's Aura 未加载时不得注册对应内容或让 JVM 解析其实现类；机器目录已经使用
   可声明多个可选模组的 `requiredMods`，新增内容不得退回专属布尔条件。
5. 配方桥接继续输出通用机器结果；环境 Aura 类型、配方上下文和实体结果由独立适配
   数据承载，不写入现有 Ars/Occultism 分支。
6. 树之仪式保留真实树木环境与已付款种植状态；动物配方生成真实实体。两类机器的
   世界副作用由专用提交流程处理，实体显示或普通物品能力不能替代配方的实际结果。

已实现范围、后续里程碑和验收矩阵见
[`NATURES_AURA_ADAPTATION.md`](NATURES_AURA_ADAPTATION.md)。

## Ars 开发资源

Ars Nouveau 机器数据放在 `src/arsDev/resources/`，默认加入普通构建，并在
`META-INF/mekanism_magic_features.properties` 中写入启用状态。保持机器内容默认
启用可确保方块、物品和方块实体注册 ID 在重启后不发生缺失映射。

## Forbidden & Arcanus 后续适配入口

`1.0.6.18` 已接入独立内容模块、精确版本门控、原版目录和量子捕捉器容器适配。
`ForbiddenArcanus261Bridge` 承担原生配方/动态注册表 ABI，`ForbiddenArcanusRecipeCatalog`
只公开本模组自己的不可变数据视图，按服务端配方管理器弱缓存，服务端启动/全局重载刷新。
`ForbiddenArcanusEntityContainerAdapter` 只在此可选包内访问原生 StoredEntity 组件；公共
实体容器注册表、德格米与献祭流程不引用 Forbidden 实现类型。
`1.0.6.19` 的 `ClibanoRecipeSelector` 只扫描已发布快照，`ClibanoProcessorBlockEntity`
将原版组装、一次性残渣结果、XP及输入扣除统一提交到可持久化pending状态；等待输出时不再次加工。
火焰介质与增强物为输入权限受控的原生Mek槽，经验领取使用现有容器按钮协议与服务端授权检查。
`ForbiddenArcanusEnabledCondition` 无第三方类型引用，始终注册，使JSON配方/掉落和精确版本注册保持同步。

1. 所有 Forbidden & Arcanus 类型引用限制在 `integration/forbiddenarcanus/`；通用
   机器、GUI 以及其他魔法模组适配包不得直接引用其 `common.*` 实现类。
2. `2.6.1` 没有独立公开 API 包，配方、动态注册表、能力与数据组件访问必须集中在
   可替换的精确版本桥接中；升级依赖前先做 ABI diff，不允许实现类泄漏到通用层。
3. Aureal、Souls、Blood 与 Experience 是四个独立精华通道，不得合并成单一魔力、
   流体、Ars Source 或 Nature's Aura。
4. Clibano 配方从 `RecipeManager` 读取；赫菲斯托斯仪式、增强物、物品修饰、法阵和
   残渣类型从对应动态注册表读取。数据包重载只刷新适配缓存，不在机器 tick 或 GUI
   中反复全表扫描。
5. Forbidden & Arcanus 未加载时不得注册对应内容或解析实现类；Valhelsia Core 仅是
   该模组加载后的传递依赖，不应变成本模组在无适配内容时的强制依赖。

目标版本、分阶段范围和验收矩阵见
[`FORBIDDEN_ARCANUS_ADAPTATION.md`](FORBIDDEN_ARCANUS_ADAPTATION.md)。
