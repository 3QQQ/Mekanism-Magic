# Mekanism Magic 1.0.6fix — NeoForge 1.21.1

## 中文

基于正式版 1.0.6 整理的维护版本，不包含未完成的自然灵气、禁忌内容，
也不包含新魔法资源存储元件／接口。原有神秘学、新生魔艺及自动化适配保留。

### 修复与保留

- 修复灌注机、原生／Extras 灌注工厂切换催化物后沿用旧配方缓存、连续合成停滞的问题。
- 修复微缩法阵制作的等待计数与配方查询退避叠加，导致持续补料反复推迟启动的问题。
- 修复刷新升级名称映射时，同名 `capacity` 等升级触发启动异常；保留原数字 ID 和旧名称别名。
- 修复机器物品容器初始化、拆卸／升级／重载后的库存及待产物状态问题，保留自动化输入与回滚保护。
- 修复仪式刷怪蛋的名称、颜色、语义变体和 JEI 区分，整理魔灵职业分类与配方显示。
- 修复粉笔／辞典、ME 样板、红石按钮的重叠，以及保留槽面板边框、标题和点击区域问题。
- 保留空白微缩仪式捕获完整法阵、再次展开与回收的功能，并保留权限、结构状态、回滚和防重复收取检查。
  彩虹／虚空粉笔仍可作为通用粉笔使用；JEI 配方不额外轮换展示它们。

### 发布范围与验证

- 原有 **34 个设备 ID** 保持不变。
- 保留 **36 个原有配方**，以及明确确认保留的 **空白微缩仪式配方**。
- 神秘学、新生魔艺、AE2、Mek Energistics、Ars Énergistique 等联合环境的 **141 项游戏测试通过**。
- 额外安装自然灵气 41.9、禁忌 2.6.1／Valhelsia Core 1.1.5 的排除边界矩阵 **143/143 通过**，
  确认这些原模组共存时也不会重新启用被排除的适配，且空白捕获配方仍然存在。
- 完整离线 `check`、设备契约、双语标题、升级序列化、侧栏布局和包内适配／排除校验通过。
- 测试结果是受控验证，不代表所有第三方整合包均已实测。

### 更新提示

适用于 Minecraft 1.21.1 / NeoForge，需 Java 21 和 Mekanism。客户端与服务端请同步更新并先备份存档。
使用过未发布自然灵气／禁忌机器或新存储元件的开发存档，不应直接替换为这个范围缩减的维护包。
本次不修改旧 Release、旧标签或旧 JAR，也不发布 Forge 1.20.1 版本。

## English

Maintenance release for Minecraft 1.21.1 / NeoForge, based on 1.0.6.

- Fixes stale imbuement recipe caches, consecutive processing stalls, and miniature-assembler input settling.
- Fixes duplicate upgrade-name startup failures while preserving numeric IDs and legacy aliases.
- Includes existing-machine inventory/persistence, ritual spawn-egg/JEI identity, automation safeguards, and sidebar-layout fixes.
- Retains the explicitly requested blank miniature pentacle capture, deployment and recovery functionality.
- Excludes unfinished Nature's Aura and Forbidden & Arcanus integrations and the new magic-resource storage cells/interfaces.
- Keeps all 34 released machine IDs and the 36 existing recipes, plus the retained blank-capture recipe.
- 141 controlled game tests and the offline/build/package checks passed; a second 143-test matrix with the excluded source mods installed also passed.

Back up worlds and update clients and servers together. This release is not a drop-in replacement for development worlds
that use the excluded experimental machines or storage. Existing releases and the Forge 1.20.1 branch remain unchanged.

## Artifact

`mekanism_magic-1.0.6fix-neoforge-1.21.1.jar` — 2,335,286 bytes.

SHA-256: `B03EF8E34CFE7D9B44FD842417AD971E1F790356DC1C3E43334CD249538C8AEF`
