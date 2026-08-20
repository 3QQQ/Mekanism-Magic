# Mekanism Magic 1.0.1

## English

Bug-fix release for Minecraft 1.20.1 / Forge.

### Fixes

- Added pentacle catalog, candidate-result, and preview-text caches to the Mini Ritual Assembler.
- Fixed the Spirit Factory auto-sort logic using the wrong inventory slot.
- Added server-side safeguards to auto-eject processing for regular machines and factories.
- Added pickaxe mining tags, the stone-tool requirement, and the missing Dimensional Miner loot table.
- Reduced unnecessary auto-eject checks performed by idle Dimensional Miners.
- Persisted pending Dimensional Miner outputs and Mini Ritual Assembler crafting requests across server restarts.
- Incompatible optional Mekanism Extras APIs now produce a warning and gracefully disable the integration.

### Compatibility

- Minecraft `1.20.1`
- Forge `47.4.22`
- Mekanism `10.4.16.80`
- Java `17`

Clients and servers must use the same `1.0.1` JAR.

### Build artifact

```text
build/libs/mekanism_magic-1.0.1-forge-1.20.1.jar
```

## 中文

适用于 Minecraft 1.20.1 / Forge 的错误修复版本。

### 修复

- 为微缩仪式制作机增加五芒星目录、候选结果和预览文本缓存。
- 修复魔灵工厂自动平分时使用错误槽位的问题。
- 为普通机器和工厂的自动弹出处理增加服务端保护。
- 补充机器的镐挖掘标签、石质工具等级和维度矿机战利品表。
- 减少空闲维度矿机执行的无效自动弹出检查。
- 保存维度矿机待产物和微缩仪式制作请求，避免服务器重启时丢失当前轮次。
- 可选 Mekanism Extras API 不兼容时改为警告并禁用集成。

### 兼容性

- Minecraft `1.20.1`
- Forge `47.4.22`
- Mekanism `10.4.16.80`
- Java `17`

客户端和服务器必须安装相同的 `1.0.1` JAR。

### 构建产物

```text
build/libs/mekanism_magic-1.0.1-forge-1.20.1.jar
```
