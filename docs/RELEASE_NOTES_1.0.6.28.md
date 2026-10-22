# Mekanism Magic 1.0.6.28（未发布开发版）

## 模拟采血与德格米：刷怪蛋样本

- 模拟采血机和德格米生态模拟器可将原版及模组的合法刷怪蛋作为保留样本。普通生物罐、量子捕捉器及已接入的其他收容物品仍可使用。
- 通过原`SpawnEggItem.getType(ItemStack)`多态接口读取类型，支持NeoForge延迟注册蛋，不按物品名称猜实体，也不依赖只包含原版蛋的映射表。
- 保留合法`ENTITY_DATA`中的单实体变种数据；显式实体ID非法、未知、玩家、非生物或非空乘客负载时拒绝，不悄悄回退成原蛋默认生物。本模组固定实体的仪式蛋同样支持；只有随机实体标签或条件鸡替代结果的蛋，须先得到确定生物再作为样本。
- 样本不消耗，不放置世界实体。每槽仍只对应一个样本，32/64个相同蛋不会额外放大该槽产量。德格米原本的多槽和物种统计机制保持不变。
- 采血仍读取类型默认生命值，保存的Health/Attributes不增加产血；原血液黑名单、3000单轮上限、FE费用和满管装填保持不变。
- 德格米保留Ars黑名单、原Source/FE费用、产物过滤、长缓存与AE输出机制。刷怪蛋替换、类型或组件变化会使对应准备缓存失效；已经保存的有效任务仍按原生命周期恢复。
- 兼容仅用于这两个模拟器，不注册全局实体容器适配器，因此没有顺带让献祭槽消耗刷怪蛋或让释放接口把它当成已收容生物。
- 更新采血机、德格米样本槽及JEI说明。没有改模型、配方、其他机器耗电或经验装瓶规则。

## 验证入口

```text
gradlew runForbiddenGameTestServer runForbiddenAbsentGameTestServer -Pmekanism_magic.occultism_runtime=false --no-daemon
gradlew runEnergyGameTestServer -Pmekanism_magic.ars_runtime=true --no-daemon
gradlew check assemble validatePackagedIntegrations -Pmekanism_magic.require_full_integrations=true --no-daemon
```

`.27`三台精华机器的已完成包保留不覆盖：`A51CF29B2C9EC37F07CEAAEF8310D969B8B1E00C77EA240081642223428AAB94`。
本轮继续通过IDEA后台工作，不启动用户客户端，不发布或上传。

## 最终验证与测试包

- F&A安装矩阵118/118、缺少F&A/Core矩阵1/1通过；包含原采血回归、3项新刷怪蛋采血用例及4项共享解析测试。测试源在正常注册阶段加入真实DeferredSpawnEggItem和固定仪式蛋夹具，未把它们放入生产包。
- Ars/能耗矩阵31/31通过，其中7项新增德格米刷怪蛋回归，覆盖真实模组蛋、原罐混用、每槽/物种计数、ENTITY_DATA变种、原黑名单、Source/FE计费、未消耗样本、准备任务和拆机重放。
- 数字型ENTITY_DATA.id被Minecraft自身Codec拒绝保存。测试分别验证内存输入拒绝、Codec拒绝，以及可序列化失效旧样本的加载保护，不把不可能保存的坏组件硬作旧存档。
- `check assemble validatePackagedIntegrations`、55台机器契约、现有离线测试、IDE后台检查通过。混合Ars测试环境仍有MekMM/MekEnergistics可选工厂战利品引用缺失物品的既有日志，不宣称全日志无错误；上述回归均通过。
- 最终日志归档：`build/reports/forbidden-integration/1.0.6.28/{installed-server.log,absent-server.log,ars-energy-server.log}`。

测试包：`build/libs/mekanism_magic-1.0.6.28-neoforge-1.21.1.jar`，3,042,410字节。

SHA-256：`2082B86F4C1BF944FD6BF6CCF4D60606BE3C5F6835A5269BF5FAD9C5996E44C0`。

包内版本`.28`、642份main编译类核验一致，无GameTest/SelfTest/测试注册内容；未删除旧条目，资源仅版本元数据及双语说明改变。旧`.27`哈希未变。该包包含`.27`完成的三台精华机器；未做新的客户端实机画面验收，未发布。
