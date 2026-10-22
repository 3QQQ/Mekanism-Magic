# 1.0.6.8（未发布）

- 修复 ProjectE 计算带库存的微缩法阵铭刻台物品 EMC 时，Mekanism 抛出 `No known containers for item` 导致悬浮提示崩溃的问题。
- 为本模组全部 `NativeMachineBlock` 物品补充缺失的 Mekanism ITEM 附件容器创建器，覆盖同类机器；已有创建器保留不动。
- 按物品实际保存的槽位数量和顺序读取，兼容旧物品布局，不改写库存或给全新物品添加库存，不暴露新的物品自动化能力。
- 此次修复针对日志中的 ITEM 附件读取异常，不改变 ProjectE 的 EMC 定价规则。

## 验证

- `runMachineItemGameTestServer`：2 项游戏内回归测试通过；覆盖铭刻台保存、读取和重新放置后的库存一致性，以及测试环境内 21 种原生机器的 1 / 34 / 73 槽位附件读取（含空槽和超普通堆叠数量）。
- `build validatePackagedIntegrations -Pmekanism_magic.require_full_integrations=true`：通过，包含现有离线自测、47 项机器契约和完整集成打包校验。
- 使用 Mekanism 10.7.19.85、NeoForge 21.1.244。回归测试直接调用日志中 ProjectE 兼容处理器使用的 `ContainerType.ITEM.getAttachmentContainersIfPresent`；未运行原整合包的客户端悬浮提示。
