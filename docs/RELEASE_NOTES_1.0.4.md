# Mekanism Magic 1.0.4

## English

- Restored Mekanism/Mekanism Extras native processing and ejection timing for all machines except the Dimensional Miner.
- Kept the Dimensional Miner batched long-buffer output path and its dedicated output throttling.
- Lowered the NeoForge optional dependency baselines:
  - JEI `19.20.0+`
  - Mekanism: MoreMachine `1.3.3+`
  - Mekanism Extras `1.4.0+`
  - Mek Energistics `3.0.5+`
- Mek Energistics ME automation now activates only on `3.0.6+`. Older supported versions load safely, disable the integration, and show a client chat reminder when entering a world.
- Kept unfinished Ars Nouveau machine content disabled in the release build.
- Improved runtime recipe scanning caches for Occultism and Ars Nouveau integrations.

## 中文

- 除维度矿机外，所有机器恢复 Mekanism/Mekanism Extras 原版运行和弹出节奏。
- 维度矿机继续使用长整数缓存、批量输出和独立弹出节流方案。
- 降低 NeoForge 可选依赖最低版本：
  - JEI `19.20.0+`
  - Mekanism：MoreMachine `1.3.3+`
  - Mekanism Extras `1.4.0+`
  - Mek Energistics `3.0.5+`
- Mek Energistics 的 ME 自动化仅在 `3.0.6+` 启用。较旧的受支持版本仍可安全加载，但会禁用该适配，并在进入世界时通过客户端聊天框提示升级。
- 正式发布构建继续关闭尚未完成的新生魔艺机器内容。
- 改进 Occultism 和新生魔艺适配的运行时配方扫描缓存。
