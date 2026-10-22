# Mekanism Magic 1.0.6.21（未发布开发版）

## 自动锻炉：护甲转化仪式

同一台 `mekanism_magic:hephaestus_forge_processor` 新增8条原生 `transmute_input` 仪式。此前10条创建物品仪式保持可用，总计18条；没有增加重复机器或复制原模组配方JSON。

- 二级及以上原版核心：下界合金头盔、胸甲、护腿、靴子分别转为天龙神秘护甲。
- 四级及以上原版核心：天龙神秘四件护甲分别转为战神提尔护甲。
- 每笔只处理一件主料，保留原生副料数量、增强物、四精华、锻造锤损伤、持续时间与法阵；不能因输入堆叠而复制多件结果。
- 委托原版 `TransmuteInputResult.getResultItem(singleInputCopy)`，以目标物品的默认组件加原输入的显式补丁生成结果：附魔、自定义名称、损伤、自定义数据和修饰等按原生逻辑迁移，包括永恒修饰的组件删除记录。
- 不把旧装备未修改的默认最大耐久/属性带到新装备，不把损伤自行按比例换算，也不重复运行修饰应用回调；数据处理与2.6.1原生转化保持一致。
- 输出空间检查使用真实输入生成的组件相关预览。同物品但附魔、名称或其他组件不同的产物不能误合并；主料组件在预览后发生变化时，过期预览在损锤/扣料前拒绝。
- 回归发现并修复已存超常堆叠的精确扣料：Mekanism库存允许恢复超出默认堆叠上限的物品，但 `shrinkStack` 会把剩余量限幅。锻炉现对材料、启动锤和精华输入精确扣除已预留数量，不额外截掉余量；未放宽外部插入或正常堆叠限制。
- 复用既有确定任务、一次付费、堵塞暂停和存档恢复机制；付款后的产物不会因主槽改变、数据包重载或配方缺失而重新生成。创造魔力仍只免精华，材料、FE、锤消耗不变。
- 结果白名单只允许两个已经审计的原生final记录：创建物品和转化输入。4条 `upgrade_tier` 继续禁用；不执行会修改原世界锻炉方块的效果。
- 配方目录及主料/副料角色索引包含新仪式，配方预览由每台机器的输入缓存生成，不在GUI或稳定运行时全表扫描。采用同一原生结果类型的数据包配方可通过既有刷新流程接入。
- 本轮保留 `.20` 的模型、界面和槽位布局；实际工作法阵仍按仪式ID选取原生纹理，没有新增第二层静态图案。

精确依赖仍为 Forbidden & Arcanus **2.6.1** + Valhelsia Core **1.1.5**，Minecraft **1.21.1** / NeoForge。本轮不加入物品修饰处理机、升阶、工厂或专用JEI/AE，也不发布或启动用户客户端。

## 校验入口

```text
gradlew runForbiddenGameTestServer -Pmekanism_magic.occultism_runtime=false --no-daemon
gradlew runForbiddenAbsentGameTestServer -Pmekanism_magic.occultism_runtime=false --no-daemon
gradlew check assemble validatePackagedIntegrations -Pmekanism_magic.require_full_integrations=true --no-daemon
node tools/validate_hephaestus_model.mjs
```

旧 `.20` 测试包保留，其SHA-256为 `DFF46C96440A1C4A6613EF7D261919E68FA377D4B21E1C89EC66F9827695B2BE`。

## 最终验证与测试包

- 安装指定依赖的独立服务端49/49 GameTest、完全未安装F&A/Core的服务端1/1通过；包含原有43项回归和新增6项转化测试。
- 新回归分别检查全部8条有损伤/附魔/灵魂绑定护甲与全部8条永恒修饰护甲：目标默认属性、输入组件删除记录、NBT/网络序列化、实际槽位校验、单件数量、过期预览、输出组件堵塞、速度/创造升级切换及付费任务拆机恢复。
- 首轮发现超堆扣料会额外限幅后，修复运行时并保持严格3→2数量断言；原生最大堆叠1的锻造锤也验证剩余两件完好、一件单独损耗。完整复跑通过，没有放宽测试断言。
- `check assemble validatePackagedIntegrations` 完整构建、51项机器契约、既有离线回归、精确依赖哈希和可选加载边界通过。
- 包内44份该集成class与最终编译字节一致，版本元数据正确，旧 `CreateRecipeView` 和GameTest类未入包；原模组类/资源不随本模组分发。
- 34部件模型资源未改，几何与法阵范围校验仍通过；本轮没有客户端实机视觉验收。
- 原生日志归档：`build/reports/forbidden-integration/1.0.6.21/installed-server.log`、`absent-server.log`。

测试包：`build/libs/mekanism_magic-1.0.6.21-neoforge-1.21.1.jar`

- 大小：2,857,651字节。
- SHA-256：`650FD68E5FD4E8F85C0706A299511F07323983DC1E694F466DBEC40C26A34BC6`。
- 旧 `.20` 哈希复核未变；未发布。
