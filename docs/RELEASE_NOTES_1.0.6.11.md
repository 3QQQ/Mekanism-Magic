# Mekanism Magic 1.0.6.11（未发布开发版）

## 生物收容物品兼容

献祭槽和德格米通过共用实体容器接口新增以下物品。用户所指的生物捕捉球对应工业先锋的生物捕捉工具。

| 模组与审计版本 | 物品 | 献祭后的容器行为 |
| --- | --- | --- |
| Apothic Enchanting 1.5.3 | 脆弱的末影拴绳、末影拴绳、魔法末影拴绳 | 清除收容数据，按原生 `hurtAndBreak` 处理1次损耗；支持耐久附魔、不可破坏与断裂 |
| Industrial Foregoing 3.6.38 | `industrialforegoing:mob_imprisonment_tool` | 清除收容组件，保留物品其他组件 |
| Just Dire Things 1.5.7 | `justdirethings:creaturecatcher` | 按原版释放规则返还默认空捕捉器 |
| Ender IO 8.2.11-beta | `enderio:soul_vial` | 按原版释放规则返还默认空灵魂瓶，不残留实体或生命值数据 |

- 德格米仅读取收容实体的类型及NBT，在内存中计算产物；不释放世界实体，不清空容器、不扣数量或耐久。
- 献祭成功后返还到专用容器回收槽。空容器、未知实体ID、玩家实体或不支持的格式拒绝处理，适配失败不会退化为直接吞掉整个物品。
- 带非空 `Passengers` 的多实体收容数据可供德格米只读根实体，但拒绝献祭/清空，避免只计算一个实体却丢失乘客。
- 末影拴绳预览不掷耐久随机数，保守预留回收空间；完成时才在服务器上下文使用原生耐久处理。普通可损耗拴绳不能走无服务器上下文的旧清空接口。
- 原版魔法末影拴绳自带不可破坏。即使物品仍有最大耐久属性，也不会强制扣损耗。
- 拒绝回收或抽取失败时保留原库存；机器先准备脱离真实库存的消费快照，再调用完成回调，避免容器拒绝消费后仍先执行完成效果。
- 已有 Ars 与 Occultism 容器继续可用；统一拒绝未知注册ID，避免默认注册表错误回退到猪。
- 使用已注册组件及容器自身Codec，不把这四个模组变成运行硬前置。不承诺未审计的旧版数据格式或任意带实体NBT的物品自动兼容。

Ender IO与Just Dire Things原版释放会生成默认空物品，因此本适配同样不保留这些空物品上的自定义名称等额外组件；工业先锋和末影拴绳遵循各自保留规则。

## 开发验证入口

原模组JAR只放在本地忽略的 `libs/`，用于独立 `runEntityContainerGameTestServer`；不装入正常开发客户端、不打进发布JAR。该测试还需 Apothic Attributes 2.9.1、Placebo 9.9.1、Titanium 4.0.43。EnderCore使用EnderIO包内的版本。

```powershell
.\gradlew.bat runEntityContainerGameTestServer "-Pmekanism_magic.ars_runtime=true" "-Pmekanism_magic.occultism_runtime=true"
```

本轮保留此前版本修复，不自动上传或发布。

## 验证结果与测试包

- 四个实际模组同时加载的容器专项 GameTest：7/7 通过。测试使用原生捕获或物品工厂API构造六种真实物品，覆盖NBT/网络往返、原模块判空、防御复制、耐久与断裂、回收堵塞、乘客保护和德格米真实插槽/战利品计算。
- 不加载新增四个模组的原有联合 GameTest：92/92 通过，确认旧功能和可选依赖边界。
- `check assemble validatePackagedIntegrations -Pmekanism_magic.require_full_integrations=true` 通过，包含47个机器资源契约和原有离线自测。
- JAR包含四个新增适配器，无GameTest类或测试用第三方模组JAR；未在用户整合包世界中运行或修改存档。

测试包：`build/libs/mekanism_magic-1.0.6.11-neoforge-1.21.1.jar`，2,555,600字节。
SHA-256：`84B1C5C9BB018D4A0920FC3A11271F84B4D2301BFCCE49C5AFDC1578B21A6C77`。
证据：`build/reports/entity-containers/real-mod-containers-1.0.6.11.log` 与 `combined-regression-1.0.6.11.log`。
