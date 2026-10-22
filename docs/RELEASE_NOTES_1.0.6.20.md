# Mekanism Magic 1.0.6.20（未发布开发版）

## 自动赫菲斯托斯锻炉：创建物品仪式

新增 `mekanism_magic:hephaestus_forge_processor`。精确依赖 Forbidden & Arcanus **2.6.1** 与 Valhelsia Core **1.1.5**；不是工厂，不含AE节点或样板下单。

- 接入原动态仪式注册表中10条创建物品配方：ferrognetic_mixture、quantum_catcher、smelter_prism、terrastomp_prism、eternal_stella、quantum_injector、sea_prism、whirlwind_prism、boss_catcher、soul_binding_crystal。
- 保留主料、无序副料及数量、真实锻炉等级、增强物、四精华、原生锻造锤损伤、结果组件和持续时间。原版八基座限制映射为副料合计最多8单位，可以来自同一堆叠，不要求逐个拆开。
- FE驱动，支持原生速度、能量及创造魔力升级；创造魔力仅免精华且停止补充精华，仍扣材料、FE和锤耐久。完全只装F&A时也能取得共享创造魔力插件，燃烧处理机不因此获得该升级支持。
- 原版五级锻炉方块物品作为保留资格核心；没有免费GUI升阶。前 `min(等级,4)` 个增强物槽有效，超过范围的槽不提供效果。
- 耀金、灵魂、血、经验四种精华分别储存，容量按原生核心等级。移除核心暂停并保留余额；降级不删除超过新容量的余额，但该通道不再输入。
- 显式输入原生确定性精华物品及存储容器。储存容器一次处理一件，至多10单位/tick；堆叠余件不被同步改组件，部分和空容器不会丢弃。固定量物品空间不足时不消耗，歧义多通道/附魔拆解等输入暂不支持。
- 开始时在副本上准备结果、四精华扣量与锤损伤，再一次性付款并保存确定任务。断电、堵塞、存档恢复、重载、创造升级切换不会重复扣料或重新组装结果。付费后改变核心/有效增强物会暂停，恢复原组件上下文后续作。
- 忠实保留2.6.1原生费用差异：修饰后的精华门槛决定能否开始，原生实际reduce仍按原始费用扣到最低0。没有把资格优惠错误解释成保证按优惠后的数量扣款。
- 默认六面能量输入、顶部物品输入输出。普通物料与EXTRA分流；OUTPUT_1为主产物、OUTPUT_2为空/返还容器。核心、锤、增强物及处理中容器不能被外部输出误抽。
- 配方目录和上下文缓存复用现有架构，稳定运行时不每tick做组件JSON/哈希，开GUI不增加注册表扫描；仅同步GUI标量及少量法阵状态，不每tick广播已付费任务NBT。
- 34部件单方块功能模型及方块/物品/工作状态、GUI、双语、掉落表和合成已加载到资源目录。工作台不画静态法阵，工作时使用原版法阵纹理；无额外世界粒子，支持现有动画与测试开关。

## 使用

1. 上排依次为充电、原版锻炉核心、锻造锤；右侧单格放主材料，中间八格放副料，右上为成品。
2. 下排左四格放增强物；右三格为精华输入、处理中单件容器、返还容器。四条储量显示互相独立。
3. 插入按原流程取得的原版锻炉方块；高级配方仍需相应等级和增强物。锤的原生基础损伤通常为每次50耐久，耐久附魔/永恒修饰仍按原逻辑生效。
4. 固定精华例：耀金瓶35、飞溅耀金瓶30；灵魂/腐化灵魂1、附魔灵魂10；经验石化球91。血试管、装精华的Utrem罐按实际组件内容转入。不会从附近玩家或实体抽资源。

## 尚未启用

8条转化输入仪式、4条锻炉升阶仪式、物品修饰处理机、专用JEI/AE、工厂和四精华管网均未启用。本阶段没有把四精华伪装成魔源、灵气或流体。

## 校验入口

```text
gradlew runForbiddenGameTestServer -Pmekanism_magic.occultism_runtime=false --no-daemon
gradlew runForbiddenAbsentGameTestServer -Pmekanism_magic.occultism_runtime=false --no-daemon
gradlew check assemble validatePackagedIntegrations -Pmekanism_magic.require_full_integrations=true --no-daemon
node tools/validate_hephaestus_model.mjs
```

已安装指定依赖的43项服务端GameTest通过，包括原有26项基础/燃烧处理机回归及17项锻炉桥接/机器用例。模型生成源与运行时JSON一致，无越界、外露共面或法阵旋转穿模；这不替代客户端实机外观验收。

旧设计日志迁至 `MODEL_DESIGN_NATURE_V4_SERIES.md`。本轮不发布、不覆盖 `.19` 或其他旧测试JAR、不启动用户客户端。

## 最终验证与测试包

- 已安装矩阵43/43、未安装F&A/Core矩阵1/1通过；后者同时检查两台机器的方块、物品和配方均未错误加载。
- `check assemble validatePackagedIntegrations` 完整构建通过；51项机器契约、既有离线回归、原依赖SHA-256和可选边界均通过。
- GUI终版已编译入包，精华/状态文字按可用宽度缩放，精华悬停显示精确数值，空槽说明复用原生槽位且不拦截点击；未做客户端实机视觉验收。
- 包内8份关键资源与源码字节一致，44份该集成的编译类与最终class字节一致；版本元数据为1.0.6.20，GameTest类及第三方F&A/Core实现未打入。
- 日志：`build/reports/forbidden-integration/1.0.6.20/installed-server.log`、`absent-server.log`。

测试包：`build/libs/mekanism_magic-1.0.6.20-neoforge-1.21.1.jar`

- 大小：2,857,630字节。
- SHA-256：`DFF46C96440A1C4A6613EF7D261919E68FA377D4B21E1C89EC66F9827695B2BE`。
- 旧 `.19` 仍为 `A5F2E3779C09E9F8C8325628AFF57AFAD13D4E8211779800EB9265F0CE790751`，未覆盖。
