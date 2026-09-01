# 附属模组兼容性记录

## 核心版本范围

Occultism 是本模组的主可选集成目标。未加载 Occultism 时，Mekanism Magic 不注册
任何方块、物品、容器、机器、配方、战利品表或 JEI 内容；模组本身可以随 NeoForge
和 Mekanism 一起加载，但在游戏中不提供内容。

开发环境可使用以下参数验证空内容模式：

```text
./gradlew runClient -Pmekanism_magic.occultism_runtime=false
```

如果使用已有包含 Occultism 方块或物品的世界进行测试，Minecraft 会报告未知注册表
键并将旧存档内容替换为空气/默认物品。这是存档缺少依赖的正常提示，不代表本模组
在无 Occultism 时注册了内容。

## CurseForge 自动发布

项目使用 `.github/workflows/publish-curseforge.yml` 通过 `mc-publish` 发布 CurseForge。
首次启用前，需要在 GitHub 仓库设置：

* Repository variable：`CURSEFORGE_PROJECT_ID`，填写 CurseForge 项目数字 ID。
* Repository secret：`CURSEFORGE_TOKEN`，填写 CurseForge API Token。

发布工作流默认只处理正式 GitHub Release；手动运行 `workflow_dispatch` 默认只下载并
校验产物，不上传 CurseForge。只有显式启用 `publish_to_curseforge` 后，手动任务才会
真正发布。
版本号来自 Release tag。工作流下载并发布 GitHub Release 中已经审核过的同一份
JAR，不再于 CI 中重新构建一个可能缺少二进制可选桥接的缩水包。NeoForge 发布前
会硬校验 Mekanism Extras、Mek Energistics 与 AE2 集成类和 Mixin 配置；缺失时
工作流直接失败，不会上传不完整文件。Release tag 必须使用
`v<版本>-forge-1.20.1` 或 `v<版本>-neoforge-1.21.1`，且 JAR 文件名、内部模组版本、
加载器和 Minecraft 版本必须与 tag 一致。

本项目固定 Minecraft `1.21.1`，以较低依赖版本作为编译基线：

* NeoForge `21.1.194` 或更高版本
* Mekanism `10.7.15` 或更高版本
* Occultism `1.222.0` 或更高版本
* Ars Nouveau `5.11.0` 或更高版本（可选）

已分别使用最低基线组合和 Mekanism `10.7.19.85`、Occultism `1.224.2`
组合完成编译验证。Ars Nouveau `5.13.0` 内置的 Nuggets 要求 NeoForge
`21.1.205`，因此只有安装该可选模组时才需要更高的 NeoForge。

当前开发环境已加入：

* Mekanism Extras `1.4.0` (`mekanism_extras`)
* Mekanism: MoreMachine `1.3.3` (`mekmm`) 或更高版本
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
数据组件注册表和反射读取该字段，并通过通用 `EntityContainerRegistry` 暴露给机器，
因此 Ars Nouveau 未安装时不会硬加载其类。

Occultism 的 `soul_gem`、`fragile_soul_gem`、`trinity_gem` 和 `magic_lamp_empty`
使用原版 `ENTITY_DATA` 保存实体。魔灵处理器会像处理 Ars 收容罐一样接受其中的
Foliot、Djinni、Afrit 或 Marid；这些额外容器槽位均不消耗容器本身。

魔灵处理器仅接受装有 Occultism Foliot、Djinni、Afrit 或 Marid 系实体的收容罐；
仪式引擎则可按献祭实体标签接受任意匹配的已填充收容罐。献祭完成后只移除
`ars_nouveau:mob_jar` 数据组件，空罐会保留。

## Ars Nouveau 机器与 Source

开发构建还提供四级魔力管道，旧的
`mekanism_magic:magic_source_pipe` ID 保留为基础级：

- 外观和连接形态参考 Mekanism 管道，支持六向连接；
- 基础/高级/精英/终极容量依次为 `10,000`、`40,000`、`160,000`、
  `640,000 Source`，单输出端速率依次为 `1,000`、`4,000`、`16,000`、
  `64,000 Source / tick`；
- 使用富集合金、强化合金、原子合金右键可按 Mekanism 原版方式原地升级，
  一次最多处理网络内距离最近的 8 节；升级保留 Source、连接模式和红石响应；
- 只与相邻的 Ars Source 机器或相邻魔力管道传输，不会跨区段读取 Source；
- AE2 与 ExtendedAE 的全方块/线缆接口可作为结构连接端点；安装 Ars
  Énergistique 并将接口槽标记为 Source 后，接口同时成为真实的 Source
  输入/输出端点，魔力管道的 PULL/PUSH 模式可直接读写其标记库存；
- 通过 Ars Nouveau `SOURCE_CAPABILITY` 对外提供 Source；
- 未启用 Ars Nouveau 开发内容时不会注册。

开发树中包含以下 Ars Nouveau `5.13.0` 机器实现：

- `mekanism_magic:source_generator`（FE魔源增强器）：增强附近原版魔源连接器的 Source 产量；
- `mekanism_magic:source_converter`（FE魔源转换器）：消耗 FE 直接生成 Ars Source；
- `mekanism_magic:catalyst_identifier_assembler`：读取 1–9 个真实灌注材料，
  生成一个不消耗的催化剂标识；
- `mekanism_magic:imbuement_processor`：处理灌注室配方；
- `mekanism_magic:enchanting_apparatus_processor`：处理附魔装置体系配方；
- Ars 配方机器额外注册独立的 Mekanism 原生升级类型
  `mekanism_magic:creative_source_upgrade`。它通过原版升级输入槽安装并免除
  配方的 Source 消耗，不影响机器的 FE 消耗。
- Mekanism Extras 的 `upgrade_creative` 保持原有逻辑；它与创造魔力升级
  类型不同，因此两者可以在同一台机器中同时安装。
- 创造魔力升级生效时，机器保留已有 Source 与输出能力，但拒绝管道、AE、
  Relay、绑定罐和附近魔源罐继续输入；移除升级后输入立即恢复。

这些机器、配方、掉落和 JEI 催化剂默认随发布构建注册。构建会从
`src/arsDev/resources` 打包 Ars 专用数据，并在发布 JAR 中写入
`ars_nouveau_machine_content=true`，保证存档重新加载时注册映射保持一致。

三台机器均公开 Ars Nouveau `SOURCE_CAPABILITY`，基础容量为
`100,000 Source`；支持堆叠升级的机器会继续按倍率扩容。
灌注机和附魔装置机支持每次 `10,000 Source` 的双向传输；FE魔源增强器只允许输出。
灌注机提供核心输入和可展开的催化剂标识库，附魔装置机提供八个材料槽；
附魔装置材料可在槽内堆叠，实际处理时按配方数量消耗。

Ars 机器支持无电慢速运行：正常供电时按 Mekanism 速度运行；当 FE 不足时，
只要配方需要的 Source 仍然足够，机器每 5 tick 推进一次处理，不会完全停机。
Source 仍按原配方在完成时消耗；安装创造魔力升级后不再启用该 Source 后备模式，
配方不再消耗 Source，但机器仍需要自身正常运行所需的 FE。

Ars 机器 GUI 侧边新增“魔力配置”标签，六个方向分别支持：
关闭、输入、输出、输入输出。该配置独立于物品、流体、气体和能量配置，
并会同步保存到机器数据；魔力管道只会连接已启用 Source 的侧面。

FE魔源增强器每轮从半径 4 格内的原版魔源连接器抽取 `100 Source`，消耗
`10,000 FE` 后输出 `150 Source`，不会脱离原版生产条件凭空生成魔源。
魔源转换机每轮消耗约 `500,000 FE`，生成 `1,000 Source`，保持
`500 FE / Source` 的固定换算效率，并通过
`SOURCE_CAPABILITY` 输出到 Ars Source 网络；支持速度、能量及
Mekanism Extras 堆叠升级。

催化剂标识制作机为包含 1–9 个基座材料的配方生成标识，输出物品保存
`catalyst_id`、匹配的灌注配方 ID 列表和当前催化剂配方签名。服务器启动和
数据包重载时会重新扫描；催化剂材料或标签内容改变后，旧签名自动失效并按新组合生成
标识。标识名称使用目标输出物品的本地化名称，因此会自动跟随 Ars Nouveau 的中英文
翻译和整合包新增配方。
灌注处理机现在使用一个可展开的催化剂标识库，不再需要同时摆放 3 个真实催化剂；
JEI 会将每个 `catalyst_id` 注册为独立物品子类型和独立制作配方。玩家可将目标标识
从 JEI 拖入配方锁定槽进行虚拟选择，不要求标识库中已有实体，也不会免费生成可取出的
标识物品。
标识物品在处理时不消耗，实际只消耗核心输入和 Source。
不含基座催化剂的灌注配方不生成实体标识，灌注机和工厂可在空锁定槽下直接按
核心材料处理。

魔法灌注工厂等级：

- 基础、高级、精英、终极：分别使用 Mekanism 原版工厂等级；
- 安装 Mekanism Extras 时增加绝对、至尊、宇宙、无限工厂；
- Extras 高阶工厂分别提供 11、13、15、17 个并行进程；
- 全部工厂共用不消耗的催化剂锁定槽（无催化剂配方可留空），支持 Source 能力、创造魔力升级、
  JEI 灌注配方催化剂和 Mek Energistics 可选自动化。
- 全部工厂在 FE 不足但 Source 足够时以每 5 tick 一次进度的速度继续运行；
  恢复供电后自动回到正常速度。

安装 AE2 后，灌注处理机可作为可选 AE Crafting Provider：
每个 Ars 灌注配方会生成一个 AE Pattern，Pattern 内部保存
`recipe_id` 和 `catalyst_id` 虚拟上下文。AE 推送时只发送实际消耗的核心输入，
不会发送或消耗催化剂标识物品；机器直接使用样板中的虚拟 `catalyst_id` 锁定组合。
配方数据包改变后，已连接的 AE Crafting Provider 会刷新其动态样板目录。
无催化剂配方保留独立虚拟 Pattern 身份，但推送时跳过标识库选择。
创造魔力升级放置在 Mekanism 原版升级界面，不再占用机器额外物品槽；
安装后配方不消耗 Source，也不会额外增加机器耗电。
Mekanism 速度和能量升级继续正常生效。

## 维度矿机与 Mekanism Extras 堆叠升级

安装 Mekanism Extras `1.4.0` 和兼容的 Mekanism `10.7.19` 或更高版本时，
维度矿机会额外支持 `mekanism_extras:upgrade_stack`。

- 每个堆叠升级令单周期并行次数乘以 2，最高安装 8 个；
- 并行倍率范围为 `1x` 至 `256x`；
- 每 tick 能量消耗按并行倍率同步增加；
- 单个输出槽的容量从基础 `256` 同步扩大，最高为 `65,536`；
- 安装或移除堆叠升级会重置尚未完成的待输出批次；
- 未加载 Mekanism Extras 时只显示并支持原有速度和能量升级。

该功能通过反射读取 Extras 动态注入的 `ExtraUpgrade.STACK`，不会让
Mekanism Extras 变成硬性前置。

开发树另有 `mekanism_magic:drygmy_simulator`：

- 九个不消耗的已填充 `mob_jar` 输入槽；
- 二十七个可堆叠输出槽；
- 使用原版实体战利品表、德格米黑名单、种类奖励与经验宝石换算；
- 使用 Source，或安装 Mekanism Extras 创造升级；
- 收容实体只在内存中创建用于计算，不加入世界。

维度矿机和德格米模拟器的原生 AE 节点采用批量输出：网络在线时先把结果
合并进持久化 `long` 缓存，每 20 tick 按物品类型整批写入 AE。网络拒绝时
机器进入背压并保留缓存，不会无限继续生产；网络离线时立即回退到可见输出槽
和第三方物品管道。两台机器 GUI 会显示 AE 在线、离线或存储受阻状态。

安装 AE2 后，`mekanism_magic:dimension_miner` 与
`mekanism_magic:drygmy_simulator` 会自带需要频道的原生 AE 节点；线缆可直接
连接机器，无需 ME 工厂升级。输出使用 AE 网络供电、安全权限和存储容量语义，
只扣除网络实际接受的数量；断电、满仓或缺少频道时物品保留在机器内。

安装任意版本的 Mek Energistics 都不会阻止游戏加载。只有检测到 `3.0.6`
或更高版本时，配方机器的原地 ME 样板升级才会启用；低于 `3.0.6` 或版本
信息无法识别时继续使用原生逻辑，并在客户端首次进入世界时提示升级。

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

安装 JEI `19.20.0` 或更高版本时，本模组会注册“微缩五芒星制作”“神秘仪式”和
“魔灵处理”三个配方分类：前者显示 18 种五芒星成立材料、法阵粉笔和微缩输出，
第二类显示可由仪式机器处理的具体仪式材料、激活物品、献祭示例和输出，包含普通合成、
矿工魔灵和储存升级仪式；第三类显示灵火、粉碎、
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

## 可选适配代码约束

通用机器循环只接受 `integration/common/recipe/MachineRecipeResult`，不直接依赖
Occultism 或 Ars Nouveau 的配方实现。可复用实体容器统一通过
`integration/common/entity/EntityContainerAdapter` 注册。

后续新增 Ars Nouveau 配方兼容时，应在 `integration/arsnouveau/` 中建立独立桥接，
将第三方配方转换为通用结果；不得把 Ars Nouveau 类型或配方判断写入
`NativeMagicMachineBlockEntity`、GUI 或 Occultism 桥接。
