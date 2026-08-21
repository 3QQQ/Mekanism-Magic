# Mekanism Magic 1.0.2

## English

Feature and stability update for Minecraft 1.21.1 / NeoForge.

### Highlights

- Reworked Mini Ritual Assembler automation:
  - automatically identifies complete pentacle material sets;
  - prefers exact inventory matches, then the closest valid match;
  - waits for inputs to settle before starting and keeps the running recipe stable;
  - assigns every miniature pentacle a unique dye marker for deterministic AE2 patterns;
  - supports an optional JEI drag-and-drop lock slot with right-click clearing.
- Registered miniature pentacle variants in the JEI ingredient list.
- Reworked ritual and pentacle JEI navigation to use native subtype-aware filtering.
- Added colored JEI slot roles and bilingual slot tooltips.
- Chalk is now a JEI catalyst and is excluded from AE2 processing-pattern inputs.
- Added Mekanism-native slot classifications for normal input/output, spirit, activation, and sacrifice automation.
- Kept chalk, ritual selectors, and the Dictionary of Spirits manual-only.
- Added a public, dependency-free automation API for future Mek Energistics integration.

### Fixes and performance

- Fixed Mini Ritual Assembler lock synchronization and right-click clearing on servers.
- Fixed recipe progress resets when automation supplies additional materials mid-process.
- Fixed ambiguous pentacle selection when several recipes can use the current inventory.
- Fixed Spirit Factories occasionally continuing cached processing after the spirit source was removed.
- Restored automatic item ejection after output-slot tracking was reset during construction.
- Optimized accelerated auto-ejection to run only when real output slots contain items.
- Improved item output responsiveness without continuously scanning idle machines.
- Preserved the release-disabled state of unfinished Ars Nouveau machine content.

### Compatibility

- Minecraft `1.21.1`
- NeoForge `21.1.194` or newer
- Mekanism `10.7.15` or newer
- Java `21`

Clients and servers must use the same `1.0.2` JAR.

## 中文

适用于 Minecraft 1.21.1 / NeoForge 的功能与稳定性更新。

### 主要更新

- 重做微缩仪式制作机自动化：
  - 自动识别完整的五芒星材料；
  - 优先选择与机器库存完全相符的配方，其次选择最接近的有效配方；
  - 材料输入稳定后才开始加工，并在加工期间固定当前配方；
  - 为每一种微缩五芒星分配唯一染料标识，确保 AE2 样板稳定区分；
  - 支持从 JEI 拖入可选锁定槽，并可右键清除。
- 在 JEI 物品列表中注册所有微缩五芒星变体。
- 仪式和五芒星 JEI 跳转改为原生子类型精确筛选。
- 为 JEI 槽位增加颜色分类和中英双语提示。
- 粉笔改为 JEI 催化剂，不会进入 AE2 处理样板材料。
- 普通输入输出、魔灵、激活与献祭槽采用 Mekanism 原生自动化分类。
- 粉笔、仪式选择器和魔灵宝典保持纯手动。
- 提供无强制依赖的公开自动化 API，供 Mek Energistics 作者后续适配。

### 修复与性能

- 修复服务器中微缩仪式锁定状态不同步及右键无法清除的问题。
- 修复自动化加工过程中补料导致进度重置的问题。
- 修复多配方同时满足时可能选择错误五芒星的问题。
- 修复魔灵被移除后魔灵工厂偶尔继续使用缓存配方的问题。
- 修复构造期间输出槽记录被重置而导致自动弹出消失的问题。
- 高速自动弹出仅在真实输出槽存在物品时运行。
- 提升输出响应，同时避免空闲机器持续扫描造成卡顿。
- 尚未完成的新生魔艺机器内容继续保持发布版禁用。

### 兼容性

- Minecraft `1.21.1`
- NeoForge `21.1.194` 或更高
- Mekanism `10.7.15` 或更高
- Java `21`

客户端和服务器必须安装相同的 `1.0.2` JAR。
