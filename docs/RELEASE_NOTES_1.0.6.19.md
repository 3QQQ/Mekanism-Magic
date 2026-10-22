# Mekanism Magic 1.0.6.19（未发布开发版）

## 禁忌燃烧处理机

新增 `mekanism_magic:clibano_processor`，依赖精确 Forbidden & Arcanus 2.6.1 + Valhelsia Core 1.1.5。
这是普通单工位机器，不含工厂升级、AE节点或样板自动下单。

- 两个无序物料槽，优先匹配双输入原配方；否则依次处理两槽的单输入。覆盖全部15条内置 Clibano 配方，沿用原生匹配和 assemble，保留结果组件。
- FE 经 Mekanism 能量体系驱动、替代原版普通燃料；速度/能量升级生效。高级火焰仍消耗真实灵魂介质，基础2700tick，crimson_stone增强为3510tick；空闲、堵塞、停机时继续倒计时，过期回普通火，不能靠换介质立即升级正在燃烧的火焰。
- 原版增强物组件决定资格，不以显示物品ID代替。普通火不抽残渣；高级火每次完成只决定一次残渣。共享64单位类型库存，按原CombineInfo聚合，未知类型保留，堵塞不重抽、不继续吞物料。
- 主产物、聚合残渣、普通XP和魂持续时间通过公共拆机/持久化链保存。等待产物排空期间不启动第二笔；高速连续加工不因一次正常排空而反复切换active。
- 经验由GUI领取，每次最多10000，先扣账再一次随机处理小数；菜单所有权、世界、机器身份、距离、安全权限均校验。外部抽取物品不领取XP，拆机保存在机器物品里，不额外生成一份经验。
- 默认六面能量输入、顶部物品输入输出；输入槽按缓存原配方Ingredient判断，魂/增强物不会被普通物料槽优先吞下，EXTRA可作专用输入。主产物和残渣可分别通过 OUTPUT_1 / OUTPUT_2 配置。
- 注册、GUI、双语、合成配方、挖掘标签和掉落表齐全。自定义资源条件与精确版本门控一致，避免不兼容/未安装依赖时解析未注册物品。
- 新增有库存的机器后，已安装但未经验证的F&A/Core组合会在注册前明确停止启动，不能像里程碑0那样静默省略该适配；这是为了避免旧世界机器ID和库存丢失。完全未安装F&A仍可正常启动。
- 首版27方块暗石/Deorum机壳使用原模组资源引用，没有复制第三方材质；无BER和额外粒子，详细外观及专用JEI/AE功能后续完善。

## 使用

上排两格放物料；下排两格依次放灵魂介质与增强物。右上为主产物，右下为残渣聚合产物。
点击“领经验”领取当前储存的普通经验；GUI显示火焰、剩余时间和残渣库存。
首次合成需要原版 clibano_core、暗石、奥术水晶及 Mekanism 组件。

## 校验入口

```text
gradlew runForbiddenGameTestServer -Pmekanism_magic.occultism_runtime=false --no-daemon
gradlew runForbiddenAbsentGameTestServer -Pmekanism_magic.occultism_runtime=false --no-daemon
gradlew check assemble validatePackagedIntegrations -Pmekanism_magic.require_full_integrations=true --no-daemon
node tools/validate_clibano_model.mjs
```

旧设计编号记录已另存 `MODEL_DESIGN_NATURE_V4_CONVERTER.md`。不发布、不覆盖旧测试JAR、不启动用户客户端。

## 验证结果

- 最终安装矩阵 26/26 GameTest、无依赖矩阵1/1通过；构建、离线回归、50项机器契约和发布包完整性校验通过。
- 原版火焰行为测试直接调用已安装2.6.1原机器的consumeSoul/serverTick作对照，不只测试自己重写的规则。
- 27方块模型与生成源一致，所有顶点在单方块内，无外露共面冲突；不声称已在用户客户端验收外观。
- 曾由回归发现远程菜单仍可能返回“接受点击”，已增加明确距离限制；随后完整复跑通过。
- 高速连续工作和真实顶部能力分流是追加的回归项；库存预留/模拟、领取与重复点击、未知残渣保留均有实际服务器测试。

## 测试包

`build/libs/mekanism_magic-1.0.6.19-neoforge-1.21.1.jar`

- 大小：2,800,531 字节。
- SHA-256：`A5F2E3779C09E9F8C8325628AFF57AFAD13D4E8211779800EB9265F0CE790751`。
- 旧 `.18` 包仍为 `408597258BC76F45328333EF518EEA6663877B2F4A023FBC1C9D1BD4D5470A21`，未覆盖。
- 原生日志另存 `build/reports/forbidden-integration/1.0.6.19/installed-server.log` 和 `absent-server.log`。
- 打包元数据、首版模型/工作模型、配方与掉落表均已从JAR取出核对源码一致，测试类未打入。
