# 机器命名与默认标题

此表记录附属机器的名称，不是对原模组翻译的覆盖。单纯更名只修改显示文字，
不改变注册 ID、配方 ID、NBT 和玩家给机器起的自定义名字。
旧灵魂提取、模拟采血、经验装瓶三机按用户要求完全退休，进入旧存档时随区块加载直接清除，
不迁移到新凝聚器，不掉落或返还其内容；这不是单纯更名。

## 命名依据

- 神秘学保留“魔灵”“灵体驱策”“维度矿井”“五芒星阵”等辨识词。
- 新生魔艺依据原版“灌注室”“附魔装置”“德格米石阵”与魔源体系命名。
- 自然灵气依据中文手册“森林仪式”“祭祀诸神”，以及原方块“祭祀台”“降生祭坛”“自然祭坛”命名。
- 禁忌奥秘保留“赫菲斯托斯”“灵魂”等原词。2.6.1 中文文件缺少 Clibano 译名，
  “克里巴诺”为本附属参考英文 Clibano 的音译，不宣称是原模组官方译名。
- 结合机器的实际用途选用台、室、舱、仪、炉等器械名。不能为了风格而把经验瓶说成水晶、
  把模拟采血说成真实击杀，或暗示没有实现的功能；详细机制仍由 GUI/JEI 提示说明。

已核对本地 Occultism 1.222.0、Ars Nouveau 5.13.0、Nature's Aura 41.9、Forbidden & Arcanus 2.6.1
的语言/手册资源。本附属的组合名称为自拟名称，原模组词汇仅作为参考。

## 现有独立机器

| 注册 ID（不变） | 中文名称 | 英文名称 |
| --- | --- | --- |
| `essence_condenser` | 四相精华凝聚器 | Essence Condenser |
| `hephaestus_forge_processor` | 赫菲斯托斯锻造台 | Hephaestus Forge Station |
| `clibano_processor` | 克里巴诺秘炉 | Clibano Arcane Furnace |
| `spirit_processor` | 魔灵驱策台 | Spirit Command Station |
| `dimension_miner` | 维度采掘井 | Dimensional Excavation Well |
| `ritual_engine` | 秘仪演算核心 | Ritual Calculus Core |
| `mini_ritual_assembler` | 微缩法阵铭刻台 | Miniature Pentacle Engraver |
| `source_generator` | 魔源谐振器 | Source Resonator |
| `source_converter` | 魔源凝聚器 | Source Condenser |
| `catalyst_identifier_assembler` | 催化铭刻台 | Catalyst Engraver |
| `imbuement_processor` | 魔源灌注室 | Source Imbuement Chamber |
| `enchanting_apparatus_processor` | 奥术附魔装置 | Arcane Enchanting Apparatus |
| `drygmy_simulator` | 德格米共生舱 | Drygmy Symbiosis Chamber |
| `aura_field_interface` | 灵气调律仪 | Aura Harmonizer |
| `fe_aura_converter` | 灵气凝聚器 | Aura Condenser |
| `aura_fuel_generator` | 灵气蕴生炉 | Aura Bloom Furnace |
| `nature_altar_processor` | 自然灌注祭坛 | Natural Infusion Altar |
| `offering_processor` | 诸神祭祀台 | Divine Offering Altar |
| `tree_ritual_processor` | 森林仪式台 | Forest Ritual Matrix |
| `animal_spawner_processor` | 降生培育舱 | Birthing Incubator |

三条工厂系列分别为“魔灵驱策工厂”“魔源灌注工厂”“自然灌注工厂”，
保留基础、高级、精英、终极、绝对、至尊、宇宙、无限八档等级前缀。
已有魔力管道不在本轮更名范围内。

## 三模组单方块首轮

以下均为本附属的组合设备名；辨识术语已核对供应的 Malum 1.8.2、Goety 3.2.1 和
Astral Sorcery 2.0.2.51 中英文语言及书籍，不覆盖原模组名称。

| 注册 ID | 中文名称 | 英文名称 |
| --- | --- | --- |
| `spirit_infusion_chamber` | 精魂灌注舱 | Spirit Infusion Chamber |
| `runic_inscriber` | 符文刻印机 | Runic Inscriber |
| `spirit_repair_chamber` | 精魂修复舱 | Spirit Repair Chamber |
| `cursed_infusion_chamber` | 诅咒注入舱 | Cursed Infusion Chamber |
| `soul_absorption_chamber` | 灵魂吸收舱 | Soul Absorption Chamber |
| `soul_mending_chamber` | 灵魂修补舱 | Soul Mending Chamber |
| `starlight_condenser` | 聚星凝液器 | Starlight Condenser |
| `starlight_infusion_chamber` | 星能注入舱 | Starlight Infusion Chamber |
| `dual_fluid_reactor` | 双液反应釜 | Dual Fluid Reactor |

## 标题栏契约

1. 每台机器必须同时提供两种语言中的 `block.mekanism_magic.<id>` 和
   `container.mekanism_magic.<id>`，同语言的值必须一致。
   Mekanism 对可命名机器的默认菜单标题使用 container 键，只有 block 键还不够。
2. 保留 Mekanism 原生的玩家自定义名称优先级；界面使用传入的标题，不另构造硬编码名字。
3. 普通机自动继承 `NativeMagicMachineScreen` 的标题栏。原生/Extras 工厂使用
   `MagicThemedScreen.drawMachineHeader`，统一在 x=10、y=4 绘制标题，给右侧状态标记留出间隔。
4. 长标题走原生滚动文字，不覆盖状态灯；具体机器重写前景文字时必须先调用父类标题绘制。
5. 新增或更名时同步相关 JEI 分类。运行 `validateMachineContracts` 和 `testMachineTitles`；
   后者按编译输出发现所有共用主题的界面，新增工厂也受同一检查约束。

这些要求已写入项目 `AGENTS.md` 和 `docs/ADDING_MACHINES.md`。
