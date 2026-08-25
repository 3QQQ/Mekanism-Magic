# Mekanism Magic 1.0.4 — Forge 1.20.1

## English

- Restored Mekanism/Mekanism Extras native processing and ejection timing for all machines except the Dimensional Miner.
- Kept the Dimensional Miner batched output path and dedicated output throttling.
- Reduced idle Ritual Engine recipe scanning when a miniature ritual is selected but material inputs are empty.
- Compact large Dimensional Miner output counts as `0.xk`, `0.xm`, and `0.xb` to prevent slot text overlap.
- Lowered the tested Forge JEI requirement to `15.20.0+`.
- Mekanism Extras `1.5.0+` and MekMM `1.2.1+` remain the tested Forge baselines.
- Ars Nouveau machine content remains disabled in the release build.

## 中文

- 除维度矿机外，所有机器恢复 Mekanism/Mekanism Extras 原版运行和弹出节奏。
- 维度矿机继续使用批量输出和独立弹出节流方案。
- 优化仪式引擎：选择微缩仪式但材料输入为空时，不再扫描仪式配方。
- 维度矿机的大数量输出改为 `0.xk`、`0.xm`、`0.xb` 缩写，避免槽位数字堆叠。
- Forge JEI 测试最低版本降低至 `15.20.0+`。
- Mekanism Extras `1.5.0+` 和 MekMM `1.2.1+` 仍是 Forge 已验证基线。
- 正式发布构建继续关闭 Ars Nouveau 机器适配。
