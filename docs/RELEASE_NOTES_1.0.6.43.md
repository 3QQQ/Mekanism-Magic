# Mekanism Magic 1.0.6.43 — 取消通用粉笔的 JEI 替代显示

- 微缩法阵的 JEI 配方只显示对应颜色的普通粉笔，取消彩虹／虚空粉笔轮换选项
  及上一版新增的双语替代说明；删除仅用于该显示的辅助方法。
- 彩虹、虚空粉笔仍能放入任意粉笔槽，覆盖全部所需颜色且不消耗。
  `isUniversalChalk`、`isChalkForColor`、`isAnyChalk`、`matchesChalkColors`
  四个实际识别方法逐项确认未改动。
- 不隐藏原模组物品列表中的粉笔，不修改配方、机器布局、库存或 `.42` 的启动修复。

验证：IDEA 后台编译通过；77/77 游戏测试通过，包含全部 18 种法阵的通用粉笔
加工、16 个粉笔槽、粉笔不消耗及之前的灌注自动化回归。
`check -x jar -x validatePackagedIntegrations` 通过；先排除打包依赖保护旧包，
完成代码验证后才将开发版本从 `.42` 递增一次到 `.43`，随后独立打包验证。

旧 `.42` JAR 保留，SHA-256：
`2C893BBF31E0E3CD27790240C6D52A69E3BEB1A211059B605E31761347822E02`。

未重启用户客户端，未发布或上传。

最终 `assemble validatePackagedIntegrations -Pmekanism_magic.require_full_integrations=true`
通过。包内 664 个主类与当前编译结果一致，不含测试类；与 `.42` 相比，仅本次
两个源码对应类、双语说明及版本元数据改变，无其他新增、丢失或修改条目。

测试包：`build/libs/mekanism_magic-1.0.6.43-neoforge-1.21.1.jar`，3,102,534 字节。

SHA-256：`82895253B6438FA8EA10B463F83E935935FD8951090FB04D1B145F368A72A020`。
