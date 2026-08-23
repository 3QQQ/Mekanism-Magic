# Mekanism Magic 1.0.3

## English

Performance and compatibility update for the NeoForge 1.21.1 and Forge 1.20.1 builds.

### Highlights

- Optimized Dimensional Miner output:
  - direct batch insertion into adjacent inventories;
  - long logical buffering with safe slot materialization;
  - one-second transfer scheduling with retry handling;
  - stacked upgrades increase per-operation output instead of recipe lookup count;
  - output buffers can use the full integer `ItemStack` count range.
- Reduced idle recipe scans and accelerated-machine overhead.
- Added optional Mek Energistics 3.0.6 integration hooks and safe fallback behavior.
- Lowered the optional Ars Nouveau 1.21.1 minimum version to 5.0.0.
- Kept unfinished Ars Nouveau machine content disabled by default.

### Compatibility

- Minecraft `1.21.1` / NeoForge `21.1.244` or newer
- Minecraft `1.20.1` / Forge `47.4.0` or newer
- Mekanism versions matching the selected loader build
- Java `21` for NeoForge; Java `17` for Forge

Clients and servers must use the same `1.0.3` JAR.

## 中文

适用于 NeoForge 1.21.1 和 Forge 1.20.1 的性能与兼容性更新。

### 主要更新

- 优化维度矿机输出：
  - 直接批量写入相邻容器；
  - 使用 long 逻辑缓存并安全拆分到物品槽；
  - 每秒执行一次输出并支持失败重试；
  - 堆叠升级提高单次产出，而不是增加配方查询次数；
  - 输出槽支持完整的整数 `ItemStack` 数量范围。
- 降低空闲配方扫描和外部加速机器的额外开销。
- 增加 Mek Energistics 3.0.6 可选适配接口及安全回退逻辑。
- 将 1.21.1 可选 Ars Nouveau 最低版本降低到 5.0.0。
- 尚未完成的新生魔艺机器内容继续默认禁用。

### 兼容性

- Minecraft `1.21.1` / NeoForge `21.1.244` 或更高
- Minecraft `1.20.1` / Forge `47.4.0` 或更高
- Mekanism 使用与对应加载器匹配的版本
- NeoForge 使用 Java `21`；Forge 使用 Java `17`

客户端和服务器必须安装相同的 `1.0.3` JAR。
