# Mekanism Magic 1.0.6.23（未发布开发版）

历史记录：本附属的物品修饰处理机已在 `1.0.6.49` 取消，相关实现与专用校验脚本
均已删除。本页不代表当前可用功能或重建计划。

## 物品修饰处理机

新增 `mekanism_magic:item_modifier_processor`，接入禁忌奥秘原版全部6条 `ApplyModifierRecipe`。不复制原配方JSON，也不修改已接入的22条锻炉仪式。

### 使用与原版规则

- 三个输入格分别放目标物品、`forbidden_arcanus:apply_modifier_smithing_template` 和追加材料，顺序不限；右侧为产物。每次消耗一件目标、一件模板、一件追加材料。
- 支持 aquatic、demolishing、eternal、fiery、magnetized、soulbound；原生物品谓词、动态标签和附魔排除集决定能否加工。
- 已经有任何修饰的物品不能再次添加，包括换成另一种修饰。Eternal还要求可损伤物品，并排除鞘翅、耐久/经验修补；Fiery排除精准采集。具体资格始终委托原配方，不将这些文字样例写成机器白名单。
- 原生组装保留目标数据，按照修饰更新名称和组件。无自定义名时加原版修饰前缀，有自定义名则保留；Eternal移除DAMAGE/MAX_DAMAGE，而不是添加UNBREAKABLE。
- 模板和追加材料均消耗，没有额外返还容器、XP、锻造锤或四精华消耗。

| 修饰ID | 追加材料（均为forbidden_arcanus） | 合法目标示例 |
| --- | --- | --- |
| aquatic | sea_prism | 钻石头盔 |
| demolishing | terrastomp_prism | 钻石镐 |
| eternal | eternal_stella | 无耐久/经验修补的钻石剑 |
| fiery | smelter_prism | 无精准采集的钻石镐 |
| magnetized | ferrognetic_mixture | 钻石靴 |
| soulbound | soul_binding_crystal | 钻石剑或钻石靴 |

### 机器与安全

- 基础处理时间100tick，使用100单位Mekanism能量/t，基础能量容量500000；支持原生速度和能量升级。这是机器自定加工开销，不是原版手工锻造配方附带成本。
- 默认六面能量输入、顶部物品输入输出；输入不能被外部管道抽走，产物支持正常抽取和侧面配置控制的自动弹出。没有精华槽，也不支持创造魔力、工厂升级或专用AE样板。
- 每台机器缓存全部输入数量/组件和目录快照。固定输入的GUI读取、缺电和堵塞等待不会重复扫描或应用修饰；输入或配方数据真正变化后才重建预览。
- 对实际目标组装一次副本预览，付款前复核原输入与配方，之后保存确定产物并精确消耗三件材料。超出默认堆叠上限的已存输入保留剩余量，不会因扣1而额外截断。
- 输出按完整组件匹配空间，不把同名但数据不同的装备误合并。断电/红石/堵塞暂停，重载、配方暂缺、拆机放回不会重扣、重复onApplied或再次添加名称前缀。
- 新增菜单、双语、合成、掉落表、挖掘标签及45部件对称压印模型。材质引用原模组资源，没有复制第三方像素；工作状态切换材质，无新增粒子或BER。

精确依赖仍为 Forbidden & Arcanus **2.6.1** + Valhelsia Core **1.1.5**，Minecraft **1.21.1** / NeoForge。本轮不发布，不启动用户客户端。

## 校验入口

```text
gradlew runForbiddenGameTestServer runForbiddenAbsentGameTestServer -Pmekanism_magic.occultism_runtime=false --no-daemon
gradlew check assemble validatePackagedIntegrations -Pmekanism_magic.require_full_integrations=true --no-daemon
```

旧 `.22` 测试包保留，SHA-256：`955151C0901CA6F5B560FFBE4F5F6DC427CEB1768954091C8285085163766B71`。

## 最终验证与测试包

- 安装指定依赖的服务端69/69 GameTest、完全未安装F&A/Core的服务端1/1通过。新增4项修饰桥接、7项实际机器回归；原有58项锻炉/燃烧回归保留。
- 桥接与实际机器分别覆盖6×6输入排列、原生完整组件及序列化对照、三角色精确3→2、Eternal删除记录、同/异修饰与原生物品/附魔排除、200tick稳定缓存、输入和数据刷新失效。
- 缺电与输出阻塞不会重跑onApplied，已付款断电/红石/配方暂缺/拆机重放/待输出存档安全；SPEED=8连续工作和真实顶部能力、六面能量、输出-only权限与升级范围检查通过。
- `check assemble validatePackagedIntegrations`、52项机器契约、既有离线回归及精确依赖/可选加载边界通过。
- 待机/工作两份45部件模型与生成源一致，无越界或外露共面。所有F&A纹理引用均存在于原JAR；未分发第三方原资源或实现。
- 最终包内50份该集成class、8份新机器及双语关键资源与编译/源码字节一致，版本元数据正确，无GameTest类；客户端界面与外观尚未实机验收。
- 测试日志：`build/reports/forbidden-integration/1.0.6.23/installed-server.log`、`absent-server.log`。

测试包：`build/libs/mekanism_magic-1.0.6.23-neoforge-1.21.1.jar`

- 大小：2,889,558字节。
- SHA-256：`E2C9AC0D9CCBA342148089A9D7B8081EFABD75CEDD2F8460E4DE6376F8D5E18E`。
- 旧 `.22` 哈希复核未变，未发布。
