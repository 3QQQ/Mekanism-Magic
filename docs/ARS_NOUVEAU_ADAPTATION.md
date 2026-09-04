# Ars Nouveau 适配准备

本文件记录 Minecraft 1.21.1 / Ars Nouveau `5.13.0` 的本地 JAR 检查结果，以及后续
适配的实现顺序。适配代码必须继续遵守 `docs/ARCHITECTURE.md` 中的可选模块边界。

## 已完成的基础结构

- `integration/common/recipe/` 提供通用机器配方结果，不再由机器实体直接依赖
  Occultism 配方类型。
- `integration/common/entity/` 提供实体容器适配注册表。
- `ArsNouveauIntegration` 已实现 `EntityContainerAdapter`，负责读取和清空
  `ars_nouveau:mob_jar`。
- `ContentIntegrationModule` 将可选模组的内容注册拆开。现有机器由
  `OccultismContentModule` 注册；后续 Ars Nouveau 机器应使用自己的模块。

## Ars Nouveau 5.13.0 配方概况

从本地 `ars_nouveau-1.21.1-5.13.0.jar` 的数据包统计到：

| 配方类型 | 数量 | 主要结构 |
| --- | ---: | --- |
| `ars_nouveau:crush` | 26 | 单输入、带概率的多输出 |
| `ars_nouveau:imbuement` | 13 | 核心输入、基座材料、单输出、Source 消耗 |
| `ars_nouveau:enchanting_apparatus` | 82 | 试剂、多个基座材料、单输出、可保留 NBT、Source 消耗 |
| `ars_nouveau:glyph` | 85 | 多输入、单输出、经验消耗 |
| `ars_nouveau:enchantment` | 96 | 附魔目标与材料、等级相关输出 |
| `ars_nouveau:scry_ritual` | 10 | 方块标签高亮目标，不是普通物品输出 |
| `ars_nouveau:summon_ritual` | 1 | 增强物品、权重实体列表、实体生成 |

此外还有盔甲升级、魔法书升级、反应附魔、药剂瓶、召唤与驱散等少量专用配方。

## 发布状态

下列机器代码已进入默认发布构建；安装 Ars Nouveau 时注册对应方块、物品、容器、
JEI 催化剂和数据配方，未安装时保持惰性且不产生缺失注册表内容。
Ars Nouveau 机器内容和 `ars_nouveau:mob_jar` 实体容器兼容均默认启用。若需要
显式验证默认构建配置，可使用：

```text
./gradlew clean build
```

Ars 运行验证还需加入：

```text
-Pmekanism_magic.ars_runtime=true -Pneo_version=21.1.205
```

## 已实现并进入发布构建

- **魔力管道**
  - 参考 Mekanism 管道的中心节点和六向连接模型；
  - 基础/高级/精英/终极四级容量为 `2,000`、`8,000`、`32,000`、
    `128,000 Source`，对应主动抽取速率为 `250`、`1,000`、`8,000`、
    `32,000 Source / tick`；高阶四级继续按 `8×` 曲线扩展；
  - 使用 Mekanism 富集、强化、原子合金右键原地升级，并继承管网 Source、
    六面连接模式和红石响应状态；
  - 只连接相邻 Ars Source 机器或魔力管道，不加入全局 SourceManager
    的跨距离提供者列表，避免断开的管道互相串网。
  - AE2/ExtendedAE 全方块接口与线缆接口可形成结构连接；存在 Ars
    Énergistique Source Key 且接口已标记时，会把接口的通用库存适配为
    `ISourceCap`，支持魔力管道按 PULL/PUSH 模式实际抽取与写入。

- **FE魔源增强器**
  - 不再无条件把电力转换为 Source，必须由附近原版魔源连接器先完成生产。
  - 在半径 4 格内选择存量最高的农业、炼金、生物、菌植或火山魔源连接器。
  - 每轮抽取 `100 Source`，消耗 `10,000 FE`，输出 `150 Source`；
    原版产量保留并额外获得 50% 增幅。
  - 只允许向魔源网络输出，不接受网络输入，避免把自身产物循环增幅。
  - 支持 Mekanism 速度、能量升级，并通过 Ars Nouveau
    `SOURCE_CAPABILITY` 向魔源罐和中继输出。

- **魔源转换机**
  - 不需要附近的魔源连接器；
  - 每轮消耗约 `500,000 FE`，生成 `1,000 Source`；基础容量扩展后仍
    保持 `500 FE / Source` 与约 500 秒的无输出填满时间；
  - 通过 Ars Nouveau `SOURCE_CAPABILITY` 输出到魔源罐、魔力管道和机器；
  - 支持 Mekanism 速度、能量及 Mekanism Extras 堆叠升级。

- **催化剂标识制作机**
  - 自动读取当前 Ars Nouveau 灌注配方的 1–9 个基座材料；
  - 使用真实材料样本生成带 `catalyst_id` 的催化剂标识；
  - 无基座材料配方不生成实体标识，避免零输入机器无限产出；
  - 启动和数据包重载时按配方与标签内容指纹重建目录，变化后的旧标识签名不再误匹配；
  - 标识显示名组合目标输出物的翻译组件，自动跟随当前客户端语言；
  - 灌注处理机使用类似粉笔模块的可展开标识库，存放全部标识；
  - JEI 将每个标识注册为独立子类型和独立制作页，可拖入锁定槽进行虚拟选择；
  - 标识物品不消耗，灌注时只消耗核心输入和 Source。

- **魔法灌注工厂**
  - Mekanism 原版四级工厂提供 1、2、3、4 个并行进程；
  - Mekanism Extras 额外提供 11、13、15、17 进程工厂；
  - 工厂使用单输入/单输出进程，并共用催化剂锁定标识；无催化剂配方可留空；
  - Source 缓冲会持久化，工厂升级链从基础一直连接到无限等级。

- **AE2 虚拟上下文**
- AE2 为每个灌注配方生成独立 Pattern；
- Pattern 保存 `recipe_id/catalyst_id`，催化剂标识不作为实际物品输入；
- AE 推送时只注入核心输入，机器按样板虚拟 ID 锁定对应组合；
- 普通灌注机使用唯一原生 AE crafting provider；节点随机器保存、卸载、复活和拆除，
  不与 Mek Energistics 节点并存；
- 输入与催化剂上下文按单次事务提交，槽位已占用后立即拒绝下一次推送，避免同 tick
  样板覆盖；
- 标识不进入 Pattern 输入数组，因此 AE 不会请求、发送或消耗标识物品；
  - 数据包重载后主动刷新已连接 AE 网络中的动态 Pattern；
  - 无催化剂配方使用独立虚拟身份，并跳过标识库选择；
  - AE2 兼容保持可选，不安装 AE2 时不加载相关实现类。

- **灌注处理机**
  - 一个核心输入、一个可展开的催化剂标识库和一个输出槽。
  - 处理全部 `ars_nouveau:imbuement` 配方。
  - 支持 Source 能力输入输出和原配方 `source` 消耗。

- **附魔装置处理机**
  - 一个试剂槽、八个可堆叠材料槽和一个输出槽。
  - 使用 Ars Nouveau 的 `IEnchantingRecipe` 列表，覆盖普通附魔装置、
    附魔和盔甲升级配方。
  - 输出通过原配方 `assemble` 生成，保留 `keepNbtOfReagent`、
    附魔和动态组件逻辑。

- **创造魔力升级**
  - `mekanism_magic:creative_source_upgrade` 是独立的 Mekanism 原生升级
    类型，通过原版升级输入槽安装、列表显示和输出槽卸载，不再使用额外槽位。
  - 安装后配方不再消耗 Source，机器仍维持自身的正常 FE 消耗。
  - 安装期间停止管道、AE 接口、Relay、绑定罐与附近魔源罐继续输入；已有
    Source 保留且仍允许输出，移除升级后输入立即恢复。
  - Mekanism Extras 的 `upgrade_creative` 仍是另一种升级，两者可以同时
    安装；没有 Mekanism Extras 时也不影响创造魔力升级本身。

- **魔源网络互动**
  - 三台机器均实现 `ISourceTile` 并公开 Ars Nouveau
    `SOURCE_CAPABILITY`。
  - 内部基础容量 `100,000 Source`、基础传输率 `10,000 Source/t`；
    灌注机和附魔装置机双向传输，
    FE魔源增强器仅输出。
  - 可参与 SourceManager 的魔源罐与中继网络。
  - 安装 Ars Énergistique `2.1.1-beta+` 后，在线 AE 节点会按
    `10,000 Source / 20 tick` 从网络 `SourceKey` 自动补充机器内部槽；抽取
    使用 AE2 带供电事务，显式绑定端点优先，网络不足时再回退附近原版魔源罐。
  - 魔源灌注机和德格米使用已有原生 AE 节点；附魔装置、普通灌注工厂及
    Mekanism Extras 灌注工厂在启用 MekE 网络升级后复用其节点，不创建
    重复 AE 节点。
  - 魔源链接工具可直接绑定 Ars Énergistique 的 ME 魔源罐；机器即使没有
    直接接入 AE，也可通过该罐抽取其所在网络的魔源。

- **无电慢速运行**
  - FE魔源增强器在附近存在可用 Source 时，即使 FE 暂时不足也会继续工作；
  - 灌注处理机、附魔装置处理机和德格米模拟器在 Source 足够时每 5 tick
    推进一步；
  - 所有等级的魔源灌注工厂遵循相同规则，各并行进程分别判定 Source；
  - 供电恢复后自动回到正常 Mekanism 处理速度；
  - 创造魔力升级令配方 Source 需求始终满足且不再消耗 Source，但不会把
    有限内部储罐变成可向外提供的无限 Source；由于仍需机器自身的 FE，它
    不与无电后备模式叠加。

## 明确排除的内容

- 不制作任何模拟施法、使用魔符、法术书或法术炮塔的机器。
- `ars_nouveau:crush` 由粉碎魔符的法术效果执行，不纳入机器适配。
- `ars_nouveau:glyph` 及其他魔符制作/执行流程暂不适配。
- 不把 Mekanism 能量机器设计成自动施放 Ars Nouveau 法术。

## 德格米适配可行性

德格米可以适配，但不能作为普通 JSON 配方处理。原版 `DrygmyTile` 的生产流程包含：

- 搜索附近真实 `LivingEntity`；
- 排除 `ars_nouveau:drygmy_blacklist` 实体；
- 使用实体自身战利品表和 Ars Nouveau Fake Player 构造掉落上下文；
- 按不同实体类型计算产量奖励；
- 周期性消耗 Source；
- 将实体经验转换为经验宝石。

开发树已加入独立“德格米生态模拟器”：

1. 九个不消耗的 Ars Nouveau `mob_jar` 槽代替附近实体；
2. 从收容罐 NBT 创建只用于战利品计算、不会加入世界的实体实例；
3. 复用实体战利品表、`drygmy_blacklist`、种类/数量奖励和经验宝石换算；
4. 使用 Source 缓冲及 Mekanism Extras 创造魔力升级；
5. 提供三行九格输出，并禁止自动抽出收容罐；
6. 在随机抽选前过滤装备型掉落，并允许整合包通过物品标签覆盖过滤策略；
7. 单独补回普通 loot table 无法取得的凋灵下界之星与荒野奇美拉贡品，按实际模拟
   操作次数结算，不参与普通数量奖励的重复抽选。

该机器仍属于开发构建，需要继续验证依赖实体状态、玩家击杀条件或特殊
LootContext 的第三方战利品表。

## 2026-08-21 开发验证

- 四台 Ars 机器可在 NeoForge `21.1.205` + Ars Nouveau `5.13.0`
  专用服务器中放置并持续 tick。
- FE魔源增强器可抽取原版农业魔源连接器的 Source 并把增幅结果输出到 Source 网络。
- 灌注处理机以紫水晶碎片完成 `imbuement_amethyst`，正常输出魔源宝石并消耗
  200 Source。
- 安装 Mekanism Extras 创造升级后，灌注处理机在 Source 为 0 时仍可完成
  相同配方。
- 附魔装置处理机以幽匿感测体完成 `spell_sensor` 配方。
- 德格米生态模拟器以牛收容罐计算出 4 个战利品并消耗 1000 Source。

## 必须保持的兼容条件

- 未安装 Ars Nouveau 时不能解析其实现类或注册相关内容。
- 只安装 Mekanism、未安装任何魔法模组时，本模组保持空内容。
- Occultism 机器、现有 GUI 尺寸、侧面配置、升级与配方行为不因适配而改变。
- Ars Nouveau、Occultism、Mekanism Extras 和 mekmm 的代码分别保持独立包边界。
- JEI 展示通过配方桥接提供的数据模型读取，不直接扫描第三方实现类。
