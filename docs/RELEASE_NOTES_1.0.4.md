# Mekanism Magic 1.0.4

## English

- Restored Mekanism/Mekanism Extras native processing and ejection timing for all machines except the Dimensional Miner.
- Kept the Dimensional Miner batched long-buffer output path and its dedicated output throttling.
- Removed the loader-level version gate for Mek Energistics; any installed version
  can load safely, while the ME automation feature requires the runtime API from
  Mek Energistics `3.0.6+`.
- Lowered the other NeoForge optional dependency baselines:
  - JEI `19.20.0+`
  - Mekanism: MoreMachine `1.3.3+`
  - Mekanism Extras `1.4.0+`
- Mek Energistics versions below `3.0.6` load safely, disable the integration, and show a client chat reminder when entering a world.
- Kept unfinished Ars Nouveau machine content disabled in the release build.
- Improved runtime recipe scanning caches for Occultism and Ars Nouveau integrations.

## 中文

- 除维度矿机外，所有机器恢复 Mekanism/Mekanism Extras 原版运行和弹出节奏。
- 维度矿机继续使用长整数缓存、批量输出和独立弹出节流方案。
- 移除 Mek Energistics 的加载器版本门槛；任意已安装版本都可以安全加载，
  但 ME 自动化功能仍要求 Mek Energistics `3.0.6+` 的运行时 API。
- 降低其他 NeoForge 可选依赖最低版本：
  - JEI `19.20.0+`
  - Mekanism：MoreMachine `1.3.3+`
  - Mekanism Extras `1.4.0+`
- Mek Energistics `3.0.6` 以下版本仍可安全加载，但会禁用该适配，并在进入世界时通过客户端聊天框提示升级。
- 正式发布构建继续关闭尚未完成的新生魔艺机器内容。
- 改进 Occultism 和新生魔艺适配的运行时配方扫描缓存。
