# Mekanism Magic 1.0.6.18（未发布开发版）

## Forbidden & Arcanus 第一阶段

- 接入精确版本 `Forbidden & Arcanus 2.6.1 + Valhelsia Core 1.1.5`，独立版本桥接与注册入口；缺少或未验证的版本不会加载其实现类，也不会阻止其他集成使用。
- 通过固定公开 Maven 文件坐标编译，SHA-256 与用户提供的两份样本一致。依赖仅用于编译和独立测试，不随本模组重打包。
- 启动与全局数据包同步时生成不可变原生目录：15 条 Clibano、6 条物品修饰、22 条锻炉仪式、7 个增强物、6 个修饰定义、11 种残渣和3种法阵；查询只读缓存，不在机器 tick 或 GUI 中扫描。
- 保留双输入无序匹配、三种火焰资格及各自时长、真实增强物组件身份、残渣概率与聚合数据、经验及四类独立仪式精华要求。配方组装委托原版，目录与预览不消耗材料、不抽取随机数。
- 量子捕捉器接入现有 `EntityContainerRegistry`：普通、16 色和 Boss Catcher 共18种。读取完整实体数据，预览不改变输入，清空只移除原生 `STORED_ENTITY`，保留名称、附魔、Aureal等组件；玩家、伪造物品和破坏性乘客处理均被拒绝。

**本轮未新增禁忌燃烧处理机、修饰机、赫菲斯托斯锻炉、工厂、四精华储槽、管道或 AE 存储。**
当前可用的游戏功能是现有献祭/德格米容器识别获得量子捕捉器兼容，配方目录与资格/组装接口为后续普通机器提供基础，不代表已自动化原版仪式。

## 测试

- `runForbiddenGameTestServer -Pmekanism_magic.occultism_runtime=false`：真实 2.6.1 + 1.1.5、无 Ars/Occultism/AE 的独立专用服务器，9/9 项通过。
- `runForbiddenAbsentGameTestServer -Pmekanism_magic.occultism_runtime=false`：无 F&A/Valhelsia 的专用服务器，1/1 项通过。
- 原生日志：`run-forbidden-gametest/logs/latest.log`、`run-forbidden-absent-gametest/logs/latest.log`。
- 上述日志已另存 `build/reports/forbidden-integration/1.0.6.18/`，避免后续测试覆盖本轮证据。
- `check assemble validatePackagedIntegrations -Pmekanism_magic.require_full_integrations=true` 通过；49 个已有机器契约、精确版本门控离线测试、依赖样本 SHA-256 和隔离包边界均通过。发布包检查确认适配类及功能标记存在，未打入原模组/前置类、原资源或测试类。
- 客户端实机、与所有魔法模组同时加载的整合测试、真正机器加工/残渣提交/经验领取仍属后续验证范围。本轮未启动用户游戏客户端。

旧的 V3 样机记录移至 `MODEL_DESIGN_NATURE_V3.md`，不再将独立设计阶段编号当成运行时构建。

## 测试包

`build/libs/mekanism_magic-1.0.6.18-neoforge-1.21.1.jar`

- 大小：2,765,783 字节。
- SHA-256：`408597258BC76F45328333EF518EEA6663877B2F4A023FBC1C9D1BD4D5470A21`。
- 旧 `.17` JAR 仍为 `4A363D6CEBAA3AFF187CA375673ADF3719A8C20D914F3719FDF175AE1C289546`，未覆盖。
- 未发布、未上传、未替换用户整合包文件。
