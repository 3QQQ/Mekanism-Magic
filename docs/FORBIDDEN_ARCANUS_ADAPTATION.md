# Forbidden & Arcanus 适配计划

状态：**已列入路线图，尚未实现或注册任何游戏内容。**

首个目标平台跟随本项目的 Minecraft `1.21.1` / NeoForge 分支，并以用户提供的
Forbidden & Arcanus `2.6.1` 为设计与 API 审计基线。

`2.6.1` 声明 NeoForge `21.1+` 与 Valhelsia Core `1.1.4+`；官方文件目标为
Minecraft 1.21.1，但 JAR 自身元数据仍未写 Minecraft 版本范围。该版本没有独立的
`api` 包，关键配方、仪式和精华类型均位于 `common.*`。因此首个可交付版本精确锁定
`2.6.1`，所有硬链接集中在可替换的版本桥接中；验证后续版本 ABI 前不声明开放范围。
原 JAR 使用 All Rights Reserved，开发依赖不得提交、重打包或随本模组分发。

本地审计样本：

- Forbidden & Arcanus `2.6.1`：
  `8D5EF1557FCDD0BA9FE98C38458751BCBECB118C30924DAA6FBDEA0A54BF5785`
- Valhelsia Core `1.1.5`：
  `8364C248EA4B8975327BC6CC36478ECDC1567110A6C8A47CE6B43E648EDD1127`

Valhelsia Core `1.1.5` 的文件名和元数据均为 NeoForge 1.21.1，并满足 Forbidden &
Arcanus 的最低版本；两份本地审计样本已齐全。正式构建仍需配置可复现依赖，不直接
引用或分发 `F:/Downloads` 中的文件。

## 2.6.1 内容概况

| 系统 | 数量 | 首批计划 |
| --- | ---: | --- |
| `forbidden_arcanus:clibano_combustion` | 15 | 首台处理机 |
| `forbidden_arcanus:apply_modifier` | 6 | 第二台处理机 |
| `forbidden_arcanus:combine_aureal_tank` | 1 | 保留原特殊合成，不机器化 |
| 赫菲斯托斯锻炉仪式 | 22 | 独立复杂机器，后续里程碑 |
| 锻炉增强物定义 | 7 | 作为仪式上下文读取 |
| 物品修饰定义 | 6 | 保留原组件语义 |
| Clibano 残渣类型 | 11 | 首台处理机必须完整保存 |
| 赫菲斯托斯法阵类型 | 3 | 仪式上下文，不简化为装饰 |

15 条内置 Clibano 配方包含 14 条单输入和 1 条双输入；全部记录残渣类型与概率，
其中 1 条还要求特定增强物。`2.6.1` 已把 `ClibanoRecipe` 改为 record，并以
`ClibanoCookingTimes`、可选 residue 和可选 enhancer 表达上下文。配方类同时支持
Fire、Soul Fire、Enchanted Fire、双输入、经验、按火焰区分的处理时间、增强物和
可选残渣，因此实现不能只按当前 15 条 JSON 的共同值写死。

22 条赫菲斯托斯仪式中，10 条创建新物品、8 条转化主输入、4 条升级锻炉等级；每条
都消耗 Aureal、Souls 与 Blood，12 条额外消耗 Experience，最多包含 5 种、合计
8 个副材料。仪式还带主材料、锻炉等级/精确等级、增强物集合、法阵类型、持续时间和
多态结果。升级到 `2.6.1` 时 `RitualRequirements.enhancers` 也由 Optional 改为直接
HolderSet，版本桥接必须按新签名实现。

## 设计边界

1. Aureal、Souls、Blood、Experience 必须作为四个独立精华通道处理，不得合并为
   通用“魔力槽”或伪装成 Mekanism 流体/化学品。
2. `2.6.1` 虽声明 `EssenceProvider.ITEM_ESSENCE`，实际能力注册仍只覆盖玩家的
   `ENTITY_ESSENCE` 和 Edelwood bucket 的流体能力，没有通用方块精华 capability。
   首版精华输入应优先包装原版 `HephaestusForgeInput` 与 `ESSENCE_STORAGE` 数据组件；
   不得默认从玩家或附近实体自动抽取。
3. FE 用于驱动机器控制和处理，不直接无限生成四种精华；创造魔力升级只免除本机
   配方精华消耗，不产生可向外输出的无限精华。
4. 原版锻炉等级、增强物、法阵、残渣与结果类型都是配方语义，不得为方便自动化而
   省略，也不得绕过原版研究或内容解锁条件。
5. 模型形成独立的暗石、Deorum 金属与奥术水晶科技系列，保持单方块范围；工厂正面
   使用 Mekanism 原版等级灯，但主体不能复制 Ars、Occultism 或其他魔法机器。
6. 所有输出必须调用原配方或结果类型的组装逻辑，保留数据组件、NBT、耐久、修饰和
   动态注册表引用。

## 里程碑 0：版本隔离与注册基础

- `2.6.1` 与 Valhelsia Core `1.1.5` JAR、版本和 SHA-256 已确认；开始实现时将两项
  依赖配置为可复现的 compileOnly/测试依赖，不引用 `F:/Downloads`。
- 版本桥接按 `2.6.1` 的 ClibanoRecipe record、按火焰保存的 cooking times、可选
  residue/enhancer、HolderSet 匹配及 RitualRequirements 签名实现并测试。
- 新建 `integration/forbiddenarcanus/`、`ForbiddenArcanusContentModule`、版本桥接、
  配方缓存和独立注册层。
- 将机器目录条件统一为 `requiredMods`，不再增加专属布尔字段。
- Forbidden & Arcanus 和 Valhelsia Core 未安装时，基础模组必须能干净构建并启动
  客户端、专用服务器，不解析任何 `com.stal111.*` 或 Valhelsia 实现类。
- 构建功能标记和发布 JAR 校验必须覆盖该集成，禁止再次出现本地可用、发布包漏类。
- 数据包重载刷新 Clibano RecipeManager 缓存和锻炉动态注册表快照；机器 tick 与 GUI
  只查询快照。

## 里程碑 1：Clibano 燃烧处理机

- 暂名“禁忌燃烧处理机”，提供两个输入位、火焰介质/上下文、增强物上下文、主输出、
  经验与残渣储存，不用固定槽位顺序判断双输入配方。
- 完整桥接 `ClibanoRecipe` 的单/双输入、输出、cooking time、experience、fire type、
  required enhancer、residue type/chance 和原版 `assemble`。
- Fire、Soul Fire、Enchanted Fire 的速度与允许配方按原版枚举处理；火焰介质是否
  消耗必须在实现前通过原版 Clibano 行为测试确定，不自行猜测。
- 11 种残渣按原注册表身份持久化；输出阻塞时不得丢弃、换型或继续产生残渣。
- 残渣不是普通静态副产物：完成时只抽取一次概率并立即保存确定结果，再按原版
  `ResidueType.CombineInfo` 累积/聚合；输出阻塞、卸载或重启不得重新抽取。
- 第一阶段只做普通机器，不同时引入工厂、AE 或 Mekanism Extras。

## 里程碑 2：物品修饰处理机

- 覆盖 6 条 `apply_modifier` SmithingRecipe，槽位语义为目标物、模板与追加材料，
  不要求玩家按某个固定格子顺序手工锁定配方。
- 输出必须调用原版 `ApplyModifierRecipe.assemble`，保留目标物全部数据组件并写入正确
  `ITEM_MODIFIER` Holder。
- 不允许重复应用、复制耐久/NBT、吞掉不匹配物品或把模板上下文错误写成 AE 材料。
- JEI 与 AE Pattern 通过 recipe ID 和 modifier Holder 保存上下文，数据包重载后重新
  解析 Holder，不持久化失效的 Java 对象引用。

## 里程碑 3：四精华与赫菲斯托斯仪式机

- 建立四个彼此独立、可持久化且可原子预留的精华缓冲：Aureal、Souls、Blood、
  Experience；容量按锻炉等级和升级动态计算。
- 暂名“自动赫菲斯托斯锻炉”，读取动态 `RITUAL` 注册表，支持主材料、最多 5 种副
  材料及数量、四精华成本、锻炉等级、增强物、法阵、持续时间和结果类型。
- 先完成 10 条 `create_item` 结果；8 条 `transmute_input` 必须验证输入数据组件、
  耐久和修饰均由原结果逻辑正确迁移后再启用。4 条 `upgrade_tier` 最后处理，且只有
  在机器自身五级锻炉状态迁移能完整保留库存、精华、FE、侧面配置、进度和上下文时
  才能启用。
- Forbidden & Arcanus 五级锻炉资格与 Mekanism FactoryTier 是两套独立概念：前者
  决定仪式可用性，后者只决定并行工位，界面与存档不得混用同一个等级字段。
- 多工位版本必须在输出提交前一次性预留四种精华和全部材料，任一资源不足时不得
  部分扣除、负数透支或免费完成其他工位。

## 里程碑 4：JEI、AE 与外部自动化

- 为 Clibano、物品修饰和锻炉仪式分别注册 JEI 类别与机器催化器；原模组仍可用的
  原生页面不隐藏。
- Clibano 普通机器通过完整配方、残渣和升级迁移测试后，再增加 Mekanism
  基础/高级/精英/终极四级工厂并使用原版 `FactoryTier.processes`；每个工位独立
  保存双输入、配方、进度和已确定的随机残渣结果。
- AE 样板只规划真实消耗的物品。火焰、增强物、法阵、modifier Holder 与仪式 ID
  作为虚拟上下文保存，不重复请求或发配。
- 复用现有 AE 批量提交、输出合并和退避机制；GUI 打开不得提高配方或动态注册表扫描
  频率。
- `2.6.1` 没有可供第三方方块统一传输四精华的稳定 block capability；首版不
  设计精华管道或 AE Essence Key，只支持经过验证的原版精华物品输入。
- 是否增加四通道精华管道、AE 存储和第三方接口另行设计，不复用 Ars Source 或
  单一流体网络。

## 后续候选范围

- 灵魂提取自动化必须明确实体来源、黑名单、每次抽取量和 Utrem Jar 上限，不扫描或
  抽取附近玩家。
- Quantum Catcher 可通过现有 `EntityContainerAdapter` 增加实体数据读取，但不得在
  通用适配层硬编码其 `STORED_ENTITY` 数据组件。
- Research Desk、世界生成、黑暗商人、方块仪式和环境效果默认不自动化；只有出现
  明确机器语义且不绕过进度系统时再单独立项。
- `combine_aureal_tank` 是合并动态精华/NBT 的特殊合成，保留原合成流程，不作为
  普通机器配方桥接。
- Mekanism Extras 11/13/15/17 工位工厂放在普通四级稳定之后，并纳入发布包完整性
  校验和 installer 升级链测试。

## 验收矩阵

### 加载与版本

- 无 Forbidden & Arcanus、精确 `2.6.1 + Valhelsia Core 1.1.5`、加 JEI、加 AE2、
  加 Mekanism Extras 及完整整合包分别完成客户端和专用服务器启动。
- 首版只接受精确验证版本；版本不符时明确禁用桥接并给出一次性日志，不在半注册状态
  继续加载。
- 自动统计 15 条 Clibano、6 条 modifier、22 条仪式、7 个增强物、11 种残渣和
  3 种法阵；数据包新增内容无需改代码即可进入缓存。

### 配方与资源正确性

- Clibano 单/双输入、三种火焰、增强物、时间、经验、结果组件和残渣概率分别测试；
  simulate 与实际提交不得执行两次随机残渣判定。
- Modifier 输出与原 SmithingRecipe 对同一输入完全一致，重复应用和不兼容物品行为
  与原版一致。
- 22 条仪式逐条验证材料、四精华、等级、增强物、法阵、时长和三类结果；多工位并发不
  透支任何一种精华。
- 创造魔力升级不抽取外部精华且不生成可外送的无限精华；移除后立即恢复真实消耗。

### 生命周期、自动化与客户端

- 拆除、搬运、升级、区块卸载和重启均保留物品、FE、四精华、残渣、机器等级、模式、
  配方上下文、进度与缓存输出。
- 外部高速物品抽取遵循能力调用结果，不被自动弹出速率错误限流；输出阻塞时机器正确
  退避。
- AE 下单不请求虚拟上下文，重启和数据包重载后 recipe/ritual/Holder ID 仍能解析。
- 所有模型不超过单方块，无贴图重叠、频闪、邻块透视或视角消失；工厂等级灯正面
  可见。
- 动画开关、强制测试开关和 GUI 后台暂停继续生效；精华、火焰、残渣与法阵动画使用
  批处理、距离裁剪和稳定相位，不以大量小方块代替视觉主体。
- machine contract 覆盖注册、方块状态、物品模型、渲染器、双语、战利品表、挖掘
  标签、升级迁移、发布包集成类和 z-fighting 检查。
