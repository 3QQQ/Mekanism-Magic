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

下列机器代码已进入开发树，但在默认发布构建中保持禁用，不注册方块、物品、
容器、JEI 催化剂或数据配方。完成全部配方类型、特殊结果和多人服务器验证后再启用。

当前发布版仅启用已经验证的 `ars_nouveau:mob_jar` 实体容器兼容。

开发构建使用：

```text
./gradlew clean build -Pmekanism_magic.ars_machine_content=true
```

Ars 运行验证还需加入：

```text
-Pmekanism_magic.ars_runtime=true -Pneo_version=21.1.205
```

## 开发树中已实现、发布版暂未启用

- **魔力管道**
  - 参考 Mekanism 管道的中心节点和六向连接模型；
  - 每段管道缓存 `10,000 Source`，相邻段之间按 `1,000 Source / tick`
    自动均衡；
  - 只连接相邻 Ars Source 机器或魔力管道，不加入全局 SourceManager
    的跨距离提供者列表，避免断开的管道互相串网。

- **魔源增幅器**
  - 不再无条件把电力转换为 Source，必须由附近原版魔源连接器先完成生产。
  - 在半径 4 格内选择存量最高的农业、炼金、生物、菌植或火山魔源连接器。
  - 每轮抽取 `100 Source`，消耗 `10,000 FE`，输出 `150 Source`；
    原版产量保留并额外获得 50% 增幅。
  - 只允许向魔源网络输出，不接受网络输入，避免把自身产物循环增幅。
  - 支持 Mekanism 速度、能量升级，并通过 Ars Nouveau
    `SOURCE_CAPABILITY` 向魔源罐和中继输出。

- **魔源转换机**
  - 不需要附近的魔源连接器；
  - 每轮消耗约 `50,000 FE`，生成 `100 Source`；
  - 通过 Ars Nouveau `SOURCE_CAPABILITY` 输出到魔源罐、魔力管道和机器；
  - 支持 Mekanism 速度、能量和创造魔力升级。

- **催化剂标识制作机**
  - 自动读取当前 Ars Nouveau 灌注配方的三个基座材料；
  - 使用真实材料样本生成带 `catalyst_id` 的催化剂标识；
  - 灌注处理机使用类似粉笔模块的可展开标识库，存放全部标识；
  - JEI 可将标识拖入配方锁定槽，手动锁定目标配方；
  - 标识物品不消耗，灌注时只消耗核心输入和 Source。

- **魔法灌注工厂**
  - Mekanism 原版四级工厂提供 1、2、3、4 个并行进程；
  - Mekanism Extras 额外提供 11、13、15、17 进程工厂；
  - 工厂使用单输入/单输出进程，并共用催化剂锁定标识；
  - Source 缓冲会持久化，工厂升级链从基础一直连接到无限等级。

- **AE2 虚拟上下文**
  - AE2 为每个灌注配方生成独立 Pattern；
  - Pattern 保存 `recipe_id/catalyst_id`，催化剂标识不作为实际物品输入；
  - AE 推送时只注入核心输入，机器按虚拟 ID 从标识库锁定对应组合；
  - AE2 兼容保持可选，不安装 AE2 时不加载相关实现类。

- **灌注处理机**
  - 一个核心输入、三个可堆叠基座材料槽和一个输出槽。
  - 处理全部 `ars_nouveau:imbuement` 配方。
  - 支持 Source 能力输入输出和原配方 `source` 消耗。

- **附魔装置处理机**
  - 一个试剂槽、八个可堆叠材料槽和一个输出槽。
  - 使用 Ars Nouveau 的 `IEnchantingRecipe` 列表，覆盖普通附魔装置、
    附魔和盔甲升级配方。
  - 输出通过原配方 `assemble` 生成，保留 `keepNbtOfReagent`、
    附魔和动态组件逻辑。

- **创造魔力升级**
  - Ars 机器使用 Mekanism Extras 的 `upgrade_creative`，并提供
    `mekanism_magic:creative_source_upgrade` 同类型升级物品，均放入原版升级界面。
  - 升级本身不消耗；安装后配方不再消耗 Source，改为按
    `200 FE / Source` 增加整次处理的耗电。
  - 通过反射读取 `ExtraUpgrade.CREATIVE`，未安装 Mekanism Extras 时不会
    解析其实现类，机器仍按普通 Source 配方运行。

- **魔源网络互动**
  - 三台机器均实现 `ISourceTile` 并公开 Ars Nouveau
    `SOURCE_CAPABILITY`。
  - 内部容量 `10,000 Source`；灌注机和附魔装置机双向传输，
    魔源增幅器仅输出。
  - 可参与 SourceManager 的魔源罐与中继网络。

- **无电慢速运行**
  - 魔源增幅器在附近存在可用 Source 时，即使 FE 暂时不足也会继续工作；
  - 灌注处理机、附魔装置处理机和德格米模拟器在 Source 足够时每 5 tick
    推进一步；
  - 供电恢复后自动回到正常 Mekanism 处理速度；
  - 创造魔力升级仍按额外 FE 替代 Source 的规则运行，不与无电后备模式叠加。

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
5. 提供三行九格输出，并禁止自动抽出收容罐。

该机器仍属于开发构建，需要继续验证依赖实体状态、玩家击杀条件或特殊
LootContext 的第三方战利品表。

## 2026-08-21 开发验证

- 四台 Ars 机器可在 NeoForge `21.1.205` + Ars Nouveau `5.13.0`
  专用服务器中放置并持续 tick。
- 魔源增幅器可抽取原版农业魔源连接器的 Source 并把增幅结果输出到 Source 网络。
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
