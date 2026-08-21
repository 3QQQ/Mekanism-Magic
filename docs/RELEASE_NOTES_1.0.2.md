# Mekanism Magic 1.0.2

## English

Feature and stability update for Minecraft 1.20.1 / Forge.

### Highlights

- Reworked Mini Ritual Assembler automation with automatic material detection, stable input settling, exact/closest recipe matching, and a JEI lock slot.
- Assigned every miniature pentacle a unique dye marker for deterministic machine selection and AE2 processing patterns.
- Registered miniature pentacle variants in JEI and changed chalk to a non-consumed catalyst.
- Added native JEI recipe filtering, colored slot roles, and bilingual tooltips.
- Added Mekanism-native automation categories for normal I/O, spirit, activation, and sacrifice slots.
- Added a public automation API for future Mek Energistics integration.

### Fixes and performance

- Fixed lock synchronization, right-click clearing, and progress resets during automated input.
- Fixed Spirit Factories occasionally processing without a current spirit source.
- Restored automatic ejection and limited accelerated ejection checks to real occupied output slots.
- Improved output responsiveness while avoiding idle-machine scan overhead.
- Kept chalk, ritual selectors, and the Dictionary of Spirits manual-only.

### Compatibility

- Minecraft `1.20.1`
- Forge `47.4.22`
- Mekanism `10.4.16.80`
- Java `17`

Clients and servers must use the same `1.0.2` JAR.

## 中文

适用于 Minecraft 1.20.1 / Forge 的功能与稳定性更新。

### 主要更新

- 重做微缩仪式制作机自动化，支持材料自动识别、输入稳定等待、完全/最接近配方选择和 JEI 锁定槽。
- 为每一种微缩五芒星分配唯一染料标识，确保机器和 AE2 处理样板稳定区分。
- 在 JEI 中注册微缩五芒星变体，并将粉笔改为非消耗催化剂。
- 增加 JEI 原生精确筛选、槽位颜色分类和中英双语提示。
- 普通输入输出、魔灵、激活和献祭槽采用 Mekanism 原生自动化分类。
- 提供公开自动化 API，供 Mek Energistics 作者后续适配。

### 修复与性能

- 修复锁定同步、右键清除以及自动输入期间进度重置的问题。
- 修复魔灵工厂偶尔在无有效魔灵时继续处理的问题。
- 恢复自动弹出，并仅对真实且非空的输出槽执行高速检查。
- 提升输出响应，同时避免空闲机器扫描造成卡顿。
- 粉笔、仪式选择器和魔灵宝典保持纯手动。

### 兼容性

- Minecraft `1.20.1`
- Forge `47.4.22`
- Mekanism `10.4.16.80`
- Java `17`

客户端和服务器必须安装相同的 `1.0.2` JAR。
