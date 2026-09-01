# Mekanism Magic 1.0.5

## English

- Enabled and completed the Ars Nouveau machine and factory integration.
- Added native AE2 output nodes to the Drygmy Simulator and Dimensional Miner.
- Coalesced identical outputs in persistent long buffers and submits AE batches
  every 20 server ticks, with online/offline/blocked status shown in the GUI.
- Added Ars Energistique and ExtendedAE Source-interface compatibility.
- Added eight Source-pipe tiers, native Mekanism-style connection modes, Pipez
  high-throughput compatibility, animation controls, and persistent contents.
- Fixed machine inventories and buffered outputs being lost when blocks are
  removed, upgraded, unloaded, or restarted.
- Fixed Source capacity display and reload synchronization for stacked machines.
- Creative Magic upgrades now disable unnecessary Source input while retaining
  existing contents and output access.
- Rebalanced the FE Source Converter to `500,000 FE -> 1,000 Source`, fixed
  parallel-batch FE accounting, energy display, and redstone control.
- Improved recipe caching, AE pattern/catalyst handling, output throughput, and
  server-tick performance across high-speed machines.
- Reworked Ars Nouveau and Occultism machine models, factory tier indicators,
  recipe-item/entity rendering, and configurable animations.

## 中文

- 启用并完善新生魔艺机器及工厂适配。
- 德格米模拟器与维度矿机新增原生 AE2 输出节点。
- 相同产物先合并到持久化长整数缓存，每 20 tick 批量写入 AE，并在 GUI
  显示在线、离线或存储受阻状态。
- 增加 Ars Energistique 与 ExtendedAE 魔源接口兼容。
- 增加八级魔力管道、Mekanism 原生连接模式、Pipez 高速抽取适配、动画
  配置及内容持久化。
- 修复机器拆除、升级、区块卸载或重启时物品和长缓存丢失的问题。
- 修复堆叠机器重进游戏后魔源容量及显示重置的问题。
- 创造魔力升级现在会停止无意义的外部魔源输入，同时保留已有储量与输出。
- FE 魔源转换器调整为 `500,000 FE -> 1,000 Source`，并修复并行批次少扣
  FE、能量信息显示及红石控制。
- 优化配方缓存、AE 样板与催化剂识别、高速输出和服务器 tick 性能。
- 重构新生魔艺与神秘学机器模型、工厂等级灯、配方物品/实体渲染及可配置动画。
