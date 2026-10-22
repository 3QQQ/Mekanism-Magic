# Mekanism Magic 1.0.6.10（未发布开发版）

- 修复随机实体仪式刷怪蛋在保存玩家背包时触发 `Missing id for entity` 的崩溃。日志中的物品数据包含 `CustomName` 与 `ritual_entity_tag`，却没有原版 `minecraft:entity_data` 编码器必需的实体 `id`。
- 标签型刷怪蛋将待生成实体数据保存到 `minecraft:custom_data` 下的 `mekanism_magic:deferred_entity_data`，使用时才从标签中随机选定实体，并向临时生成上下文提供有效 `id`。不提前锁定随机结果。
- 更新名称、归属、精灵工作与唤灵书元数据时保持安全的存储格式，并保留其他自定义数据；固定实体刷怪蛋继续使用原版实体组件。
- 配方实体预览与仪式实体数据读取适配新格式，仍可读取旧格式。修复缺少实体 ID / 标签时将空字符串解析为资源位置的问题。
- 保留 `1.0.6.8` 的物品库存读取修复及 `1.0.6.9` 的默认物品配置页改动。

## 验证命令

```powershell
.\gradlew.bat runMachineItemGameTestServer build validatePackagedIntegrations "-Pmekanism_magic.require_full_integrations=true" --no-daemon
```

新增游戏内回归覆盖原日志缺失 ID 数据的编码失败、新格式的 NBT / 网络往返、玩家与机器库存保存、随机标签及命名保留、固定实体和附加元数据保存、旧格式读取与更新，以及保存后的随机刷怪蛋实际生成实体。

验证结果：5 项游戏内回归全部通过（含之前的 2 项机器物品库存回归），完整构建、现有离线自测、47 项机器契约和完整集成打包校验通过。运行环境为 Minecraft 1.21.1 / NeoForge 21.1.244 / Mekanism 10.7.19.85。

未改动用户世界存档，也未在原整合包客户端运行测试。
