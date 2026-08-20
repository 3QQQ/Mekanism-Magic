# Mekanism Magic 1.0.1

## English

Bug-fix release for Minecraft 1.21.1 / NeoForge.

### Fixes

- Significantly reduced recipe-scanning overhead when opening and rendering the Mini Ritual Assembler.
- Fixed missing mining tags and the missing Dimensional Miner loot table, allowing machines to be recovered correctly.
- All machines can now be recovered with a stone pickaxe or better.
- Fixed multiplayer crashes involving factory configuration, auto-sort, and auto-eject controls.
- Fixed higher-tier factories incorrectly executing server ticker logic on the client.
- All machine energy sides now default to input.
- Kept the unfinished Ars Nouveau machine integration disabled in release builds; verified mob-jar compatibility remains available.
- Incompatible optional Mekanism Extras APIs now produce a warning and gracefully disable the integration instead of preventing startup.
- Reduced unnecessary auto-eject checks performed by idle Dimensional Miners.

### Compatibility

- Minecraft `1.21.1`
- NeoForge `21.1.194` or newer
- Mekanism `10.7.15` or newer
- Java `21`

Clients and servers must use the same `1.0.1` JAR.

## 中文

适用于 Minecraft 1.21.1 / NeoForge 的错误修复版本。

### 修复

- 大幅降低微缩仪式制作机首次打开和持续渲染时的配方扫描开销。
- 修复机器缺少挖掘标签、维度矿机缺少战利品表而无法正常回收的问题。
- 所有机器现在使用石镐及以上工具即可回收。
- 修复魔灵工厂配置、自动平分和自动弹出在多人服务器上的崩溃路径。
- 修复高阶工厂错误在客户端执行服务端 ticker 的问题。
- 所有机器能量侧面默认设置为六面输入。
- 发布版继续禁用尚未完成全部适配的新生魔艺机器；已验证的收容罐兼容仍然可用。
- 可选 Mekanism Extras API 不兼容时改为警告并禁用集成，不再阻止模组启动。
- 减少空闲维度矿机执行的无效自动弹出检查。

### 兼容性

- Minecraft `1.21.1`
- NeoForge `21.1.194` 或更高
- Mekanism `10.7.15` 或更高
- Java `21`

客户端和服务器必须安装相同的 `1.0.1` JAR。
