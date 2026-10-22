# Mekanism Magic 1.0.7

## English

This stable release is based on the verified development build **1.0.6.66**. It brings together the features and fixes made since the stable 1.0.6 release, including the maintenance changes from 1.0.6fix. **It does not include the nine machines subsequently added for Malum, Goety, and Astral Sorcery.**

For Minecraft 1.21.1 / NeoForge. Update both the client and server, and back up your world before upgrading.

### Important: Worlds Used with Development Builds

- The old soul-extraction, simulated blood-extraction, and experience-bottling machines have been retired. When chunks containing these machines load, their blocks are replaced directly with air. Their stored items, essences, and tasks are **not migrated, dropped, or refunded**. To keep their contents, remove them using the old version before upgrading.
- The addon's modifier-processing machine from earlier development builds has also been removed. Forbidden & Arcanus's own modifier workbench and recipes are unaffected.
- Some older AE patterns contain outdated item-component requirements. If upgrade, repair, resurrection, or enchanting orders cannot complete, check existing outputs and returned materials before re-encoding the patterns from the corresponding machine's JEI category.

### New Nature's Aura Automation

- Added the **Aura Harmonizer**, **Aura Condenser**, **Aura Bloom Furnace**, **Natural Infusion Altar**, **Divine Offering Altar**, **Forest Ritual Matrix**, and **Birthing Incubator**.
- Natural Infusion Factories now span eight tiers, from Basic to Infinite, with 3–17 independent processing lanes. The upper four tiers require Mekanism Extras. Upgrading preserves inventories, energy, Aura, catalysts, and the corresponding automation state.
- Added support for native altar, offering, tree ritual, and animal spawning recipes, retaining their actual ingredients, Aura costs, and dynamic results.
- The Forest Ritual Matrix processes ingredients, saplings, and Gold Powder internally. No external Gold Powder arrangement or tree-growth waiting is required.
- The Divine Offering Altar requires no external flower wreath and can obtain bonus by-products from retained Wither Roses.
- The Birthing Incubator produces the appropriate spawn egg instead of spawning entities around the machine.
- Added recipe selection, a catalyst library that can expand with data packs, and automatic factory input balancing that protects exact-pattern contexts.
- Aura-producing machines can select the Aura type of the current dimension, Overworld, Nether, or End. Existing stored Aura is never relabeled as another type.

### New Forbidden & Arcanus Automation

- Added the **Clibano Arcane Furnace**, **Hephaestus Forge Station**, and **Essence Condenser**.
- The Clibano Arcane Furnace supports native single- and dual-input recipes, flame requirements, enhancers, residues, and experience.
- The Hephaestus Forge Station supports 18 item-result rituals and four machine tier-upgrade rituals. It stores its own tier from 1 to 5 and no longer requires a physical forge core or Blacksmith Gavel.
- The Essence Condenser uses four non-consumed samples to independently produce Aureal, Souls, Blood, and Experience essence, and can continuously fill native containers.
- Optional integration supports PackagedFAA's native essence fluids. PackagedFAA requires its own dependencies, including PackagedAuto and Kotlin for Forge. Without PackagedFAA, native containers, ME Magic Storage Cells, and Magic Pipes remain available.
- This integration was verified with **Forbidden & Arcanus 2.6.1** and **Valhelsia Core 1.1.5**. Forbidden & Arcanus factories are not enabled.

### Magic Resource Storage and AE Automation

- Added **1K–256K ME Magic Storage Cells** with shared capacity for Source, different Aura types, and all four Forbidden & Arcanus essences. Cell Workbench partitioning is supported.
- Expanded the eight tiers of Magic Pipes. They share one physical pipe network while keeping Source, Aura, and the four essences separate. Essences transfer in Aureal → Souls → Blood → Experience order, switching when the current essence is exhausted at the source or its destination storage is full.
- Expanded direct AE connections for machine resupply and returning outputs to the network. Machines use the applicable energy and magic resources actually stored in the network, and automatically return their real outputs. The Essence Condenser no longer needs an additional ME Interface to store essence.
- Items return to ordinary item storage; magic resources return to their corresponding cells. Pending outputs and necessary refund records are retained when the network disconnects, storage fills, or a transfer is rejected.
- A normal AE2 Pattern Provider can send a complete batch through one configured side. Primary and secondary ingredients are assigned according to the recipe, reducing the need for providers on multiple sides.
- Added native AE container interactions and an automatic bottling service for Aura, consuming the actual containers and the matching Aura type.
- Added separate master switches and per-resource controls for network resupply and returning outputs to the network. Disabling them does not clear inventories, patterns, or existing jobs.
- Added optional support for Applied Flux network energy storage and Jade status displays.

### Interface and Controls

- Themes, AE controls, and machine-specific operating options are consolidated under **Machine Settings**, showing only applicable options.
- Uncovered inventory slots and other tabs remain usable while the settings window is open. Clicks inside the window—including right-clicks on blank space or disabled controls—do not pass through to the interface underneath.
- Standardized Chinese and English names, default GUI titles, and scrolling for long titles, while preserving player-assigned custom names.
- Made machine and factory layouts more compact while retaining normal font sizes, native slot sizes, and the complete player inventory grid. Added spacing around the casing, work area, and inventory title.
- Adjusted input/output arrows while preserving native pixels, progress filling, warnings, and recipe-viewing interactions. Fixed arrows overlapping slots and removed disconnected decorative arrows.
- Improved light and dark themes with both default assets and optional resource packs. No resource pack is required.

### Occultism, Ars Nouveau, and Stability Fixes

- Fixed mismatches between actual outputs and JEI/AE pattern components for miner upgrades, dynamic repairs and resurrection, bound scrolls, and enchanting recipes.
- Fixed stale recipe caches affecting catalyst-free imbuement, continuous processing, and catalyst switching.
- Improved enchanting ingredient matching so lower-level recipes do not take ingredients intended for higher-level recipes. Reactive enchantments retain the actual spell data from the parchment.
- Blank Mini Ritual items can capture complete pentacles. Improved matching for alternative candles, ritual bowls, chalk, and continuously supplied ingredients, and prevented duplicate material recovery.
- Spirit JEI recipes are grouped by profession. Ritual Spawn Eggs now have names, colors, and recipe-lookup identities that distinguish their entities, tiers, and professions.
- Fixed ProjectE tooltip crashes when reading machine-item inventory attachments, and startup failures caused by duplicate upgrade names.
- Standardized actual energy-consumption displays, corrected FE/J conversion and processing power costs, and fixed redstone pause handling and unnecessary charges after reaching processing-speed limits.

### Drygmy and Entity Containment

- Expanded compatibility with Ender Leads, Industrial Foregoing's Mob Imprisonment Tool, Just Dire Things' Creature Catcher, Ender IO's Soul Vial, and Forbidden & Arcanus Quantum Catchers.
- The Drygmy Symbiosis Chamber supports valid vanilla and modded spawn eggs as samples. Samples are not consumed; using spawn eggs as samples does not make them valid sacrificial entity containers.
- Added supplemental output rules for Echo Shards, Turtle Scutes, and Armadillo Scutes, together with live-harvest outputs such as eggs, wool, mushrooms, honeycomb, dragon breath, and froglights.
- Age, variant, blacklist, and upgrade requirements remain enforced. Fluids such as milk are produced only in the amount actually accepted by an online AE network. No filled buckets or bottles are generated as substitutes, and vanilla mob death drops are unchanged.

---

## 中文

本正式版以已验证的开发版 **1.0.6.66** 为基线，汇总自正式版 1.0.6 以来的功能与修复，包含 1.0.6fix 的维护改动。**不包含后续 Malum、Goety、Astral Sorcery 九台新机接入。**

适用 Minecraft 1.21.1 / NeoForge。请客户端与服务端同时更新，升级前备份存档。

### 重要：旧开发版存档

- 旧灵魂提取机、模拟采血机、经验装瓶机已合并退休。加载包含这些机器的区块时，旧方块会直接变为空气；内部物品、精华与任务不会迁移、掉落或返还。如需保留内容，请先用旧版本取出，再升级。
- 开发版曾提供的秘法赋能台已取消；原版 Forbidden & Arcanus 的修饰工作台与配方不受影响。
- 部分旧 AE 样板保存了过时的物品组件要求。升级、修复、复活或附魔订单无法完成时，请先核对已有产物与退料，再从对应机器的 JEI 页面重新编码样板。

### 新增自然灵气自动化

- 新增灵气调律仪、灵气凝聚器、灵气蕴生炉、自然灌注祭坛、诸神祭祀台、森林仪式台与降生培育舱。
- 自然灌注工厂覆盖基础至无限八档，提供 3–17 个独立工位；高四档需要 Mekanism Extras。升级保留库存、能源、灵气、催化剂与相应自动化状态。
- 接入原生祭坛、供奉、树木仪式与动物生成配方，保留实际材料、灵气费用及动态结果。
- 森林仪式在机内消耗材料、树苗和金粉加工，无需外置金粉阵或等待树木生长。
- 诸神祭祀无需外置花环，可通过保留的凋零玫瑰获得副产物加成。
- 降生培育舱产出对应刷怪蛋，不在机器周围生成实体。
- 增加配方选择、可随数据包扩展的催化剂库，以及保护精确样板上下文的工厂自动平分。
- 灵气生成机可选择当前世界、主世界、下界或末地类型，已有储量不会被重新标记为其他类型。

### 新增禁忌奥秘自动化

- 新增克里巴诺秘炉、赫菲斯托斯锻造台与四相精华凝聚器。
- 秘炉支持原生单／双输入、火焰资格、增强物、残渣与经验。
- 锻造台支持 18 条物品仪式和 4 条本机升阶仪式，自身保存 1–5 级，不再需要实体锻炉核心或锻锤。
- 四相凝聚器使用四个不消耗的样本独立生产耀金、灵魂、血液与经验精华，并持续装填原版容器。
- 可选兼容 PackagedFAA 原生精华流体；安装时需满足它的 PackagedAuto、Kotlin for Forge 等前置。未安装时，原版容器、魔力磁盘与魔力管道仍可使用。
- 本次适配验证组合为 Forbidden & Arcanus 2.6.1 与 Valhelsia Core 1.1.5，未启用禁忌工厂。

### 魔法资源存储与 AE 自动化

- 新增 1K–256K ME 魔力磁盘，共享容量保存魔源、各类型自然灵气与四种禁忌精华，支持元件工作台分区。
- 扩展八级魔力管道：共用物理管网，分别保存魔源、灵气与四精华；精华按耀金、灵魂、血液、经验顺序输送，当前种抽空或目标存满后切换。
- 扩展机器直连 AE 的补给与回网：直接使用网络中适用的能量和魔法资源，真实产物自动入网；凝聚器存精华不再需要额外 ME 接口。
- 物品回普通物品存储，魔法资源回相应磁盘；断网、满盘或拒收时保留待交付产物与必要退款记录。
- 普通 AE2 图样供应器可从一个已配置侧面整批投料，按配方分配主副材料，减少多面供应器需求。
- 新增灵气的 AE 原生手动容器交互与自动装瓶服务，实际消耗容器及对应类型的灵气。
- 新增补给／回网总开关及资源分项控制，关闭不会清空库存、样板或已有作业。
- 可选支持 Applied Flux 网络储能与 Jade 状态显示。

### 界面与操作

- 主题、AE 控制及本机工作选项统一收纳到“机器设置”，只显示适用项目。
- 设置窗口打开时，仍可操作未被遮挡的背包与其他页签；窗口内空白、禁用控件和右键不会穿透。
- 统一中英文名称、默认 GUI 标题和长标题滚动，保留玩家自定义名称。
- 收紧机器与工厂布局，保留正常字号、原生槽位大小和完整背包，增加外壳、工作区与背包标题之间的留白。
- 调整输入／输出箭头，保留原生像素、进度填充、警告与配方查看，修复压槽和孤立装饰箭头。
- 改善默认材质及可选资源包下的深浅主题；无需安装资源包即可使用。

### 神秘学、Ars 与稳定性修复

- 修复矿工升阶、动态修复／复活、绑定卷轴及附魔配方的真实产物与 JEI／AE 样板组件不一致。
- 修复灌注无催化配方、连续加工和催化剂切换时的旧缓存问题。
- 改善附魔材料匹配，避免低级配方抢占高级配方材料；反应附魔保留真实羊皮纸法术数据。
- 微缩法阵支持用空白物品收取完整法阵；完善替代蜡烛、仪式碗、粉笔和持续补料匹配，防止重复回收材料。
- 魔灵 JEI 按职业分页；仪式刷怪蛋按实体、阶级与职业区分名称、配色和查询身份。
- 修复机器物品附件读取引发的 ProjectE 悬浮提示崩溃，以及重复升级名称导致的启动异常。
- 统一实际耗电显示，校正 FE／J 换算与加工功率，修复红石停机和速度触顶后继续多收费的问题。

### 德格米与实体收容

- 扩展末影拴绳、工业先锋捕捉工具、Just Dire Things 捕捉器、Ender IO 灵魂瓶及禁忌量子捕捉器兼容。
- 德格米支持合法原版／模组刷怪蛋样本；样本不消耗，也不会被顺带当作可献祭的实体容器。
- 新增回响碎片、海龟／犰狳鳞甲等补充规则，以及鸡蛋、羊毛、蘑菇、蜂脾、龙息、蛙明灯等活体产物。
- 保留年龄、变种、黑名单和升级条件。牛奶等流体只按在线 AE 的实际接收量产出，不生成桶瓶替代品，不改原版生物死亡掉落。
