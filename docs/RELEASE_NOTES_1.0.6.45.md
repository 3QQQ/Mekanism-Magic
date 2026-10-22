# Mekanism Magic 1.0.6.45 — 重复升级名称的启动兼容修复

## 原因与改动

- 日志中的 `Duplicate key capacity` 在 Magic 重建 Mekanism 升级名称映射时触发。
  Minecraft 1.21.1 对不超过 16 个值使用顺序查找，超过 16 个值则使用不允许重复键的 Map；
  因此添加创造魔法升级后可能暴露其他附属已注册的同名升级。
- 新增 `UpgradeSerialization`，统一按原顺序保留第一个同名值供名称解码，
  并在日志中记录重复名称、枚举名称及双方数字 ID，替代本模组调用的严格名称映射构造。
- 不删除、不合并、不重排升级枚举，保留网络编号和压缩编码的数字 ID；
  `creative_magic` 保存名称、`chemical` 名称及旧存档 `gas` 别名保持不变。
- 回归测试构造 16 -> 17 个升级值的情形，先复现旧异常，再检查修复后的名称、数字 ID、
  重复名称、旧别名及非法输入；测试已接入 Gradle `check`。

## 范围

这是本模组序列化重建逻辑的兼容修复，不修改其他附属注册的升级，也不修复它们
同名升级在语义上的歧义。以名称保存的重复升级仍遵循原先的小数组行为：读取第一个。
`aemodbtnfix` 提前读取配置是另一个模组的问题，本次没有修改该模组。

当前有效基线是 `.44`，本轮实质运行时代码差异为 `UpgradeMixin` 改用容错映射及
新增 `UpgradeSerialization`；验证完成后仅递增一次至 `.45`。不发布、不上传。
保留 `.44` 历史包，SHA-256：
`80615F0A5302ACD7D7C02A97D2F8B203944A9FECEFE420DA62A08D7694E1DF61`。

测试为离线回归与构建验证；未在用户提供日志的完整整合包中实测启动。

## 验证

- `testUpgradeSerialization` 通过：先复现原异常，再验证修复后的 16/17 值查找、
  全部数字 ID 往返、重复名称取首值、规范保存名称、旧 `gas` 别名和非法输入。
- `check -x jar -x validatePackagedIntegrations` 完整通过；验证期间排除打包，
  避免覆盖当前 `.44` 历史产物。包含 55 个机器契约及 47 对双语标题检查。
- IDEA MCP 可连接，但项目打开请求后仍只报告另一个项目；本次使用 Gradle 完成编译和检查。
- `assemble validatePackagedIntegrations -Pmekanism_magic.require_full_integrations=true` 通过。
- JAR 与 `.44` 逐项比较：仅修改 `UpgradeMixin.class`、新增 `UpgradeSerialization.class`
  以及更新版本元数据，无删除条目、无其他资源变化，也未打入测试类。旧 `.44` 哈希不变。

## 修复包

`build/libs/mekanism_magic-1.0.6.45-neoforge-1.21.1.jar`，3,105,988 字节。

SHA-256：`7E488F5DDE3CD41B425D1C66CA14DDAC263793B0640149ABEE973CA0A599E333`。
