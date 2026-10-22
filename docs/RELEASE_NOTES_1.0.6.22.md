# Mekanism Magic 1.0.6.22（未发布开发版）

## 自动锻炉内升级核心

自动赫菲斯托斯锻炉现在直接执行原版四条升阶仪式，无需拆出核心到外面升级。已有18条创建/转化仪式继续可用，共覆盖2.6.1全部22条内置锻炉仪式。

### 使用

1. 核心槽放原版锻炉核心，启动工具槽放锻造锤。
2. 主材料放主料槽，副材料放八个无序副料槽，供足FE及原版精华，即自动匹配当前等级的升阶仪式。
3. 开始后机器暂存一件旧核心，界面显示升级目标。完成后自动装回高一级核心；核心槽若有多余旧核心，请取出留空，机器不会覆盖或一并升级它们。

| 升级 | 主材料×1 | 副材料 | 耀金 / 灵魂 / 血 / 经验 |
| --- | --- | --- | --- |
| 1→2 | `forbidden_arcanus:edelwood_planks` | `arcane_crystal`×4、`spawner_scrap`×4 | 500 / 10 / 6000 / 0 |
| 2→3 | `forbidden_arcanus:chiseled_polished_darkstone` | `arcane_crystal`×4、`deorum_ingot`×4 | 1000 / 50 / 9000 / 0 |
| 3→4 | `forbidden_arcanus:chiseled_polished_darkstone` | `stellarite_piece`×4、`rune`×4 | 2000 / 100 / 12000 / 0 |
| 4→5 | `forbidden_arcanus:stellarite_block` | `minecraft:sculk_catalyst`×4、`dark_nether_star`×2、`dragon_scale`×2 | 5000 / 500 / 20000 / 0 |

副料中未写命名空间的均属 `forbidden_arcanus`。表为原版2.6.1数据，不是复制到本模组的新配方；数据包仍通过原目录刷新。四条基础持续时间均500tick，正常支持Mek速度升级；原生锤基础通常消耗50耐久。

### 安全与规则

- 升级目标按原版结果的绝对级别读取，保留原精确当前等级校验；另外限制逐级升至最高5级，不允许跳级、降级或重复同级。
- 普通仪式结果与核心升阶明确区分。不会调用原升级的世界方块替换逻辑，不会让本模组机器变成原版锻炉，也不会清掉原机器库存、FE、精华、侧面配置或插件。
- 升级只拥有一件旧核心：付款时从核心槽精确取出到持久任务中，期间禁止插入新核心。超堆/多余核心原样保留，玩家可手动取出；不会免费升级一整堆，也不把旧核心和新核心各返一份。
- 任务期间按托管旧核心计算容量和增强物资格，装回新核心后才启用新等级容量/槽位；核心暂存不会造成零级停工。
- 目标核心保留原单件核心的组件修改记录，不复制整机资源到核心物品。主、副材料和四精华、锤损耗一次提交，断电/红石暂停/存档重载/拆机放回不重扣。
- 完成核心保存在独立待安装区，只进入空核心槽，不会被当成普通产物自动弹出。普通输出满不挡升阶；核心槽被占用时等待清空，不覆盖物品。
- 创造魔力仍只免精华并停止精华输入，材料、锤和FE不豁免；速度/能量插件不直接改变锻炉等级。
- 旧版本已付款的物品任务兼容加载，不会被误当成核心升级。客户端增加升级目标及暂存/待安装说明，沿用现有槽位和原生升级法阵纹理；没有新增世界粒子。

精确依赖不变：Forbidden & Arcanus **2.6.1** + Valhelsia Core **1.1.5**，Minecraft **1.21.1** / NeoForge。本轮不新增工厂、物品修饰处理机或专用JEI/AE，不发布、不启动用户客户端。

## 校验入口

```text
gradlew runForbiddenGameTestServer runForbiddenAbsentGameTestServer -Pmekanism_magic.occultism_runtime=false --no-daemon
gradlew check assemble validatePackagedIntegrations -Pmekanism_magic.require_full_integrations=true --no-daemon
node tools/validate_hephaestus_model.mjs
```

旧 `.21` 测试包保留，SHA-256：`650FD68E5FD4E8F85C0706A299511F07323983DC1E694F466DBEC40C26A34BC6`。

## 最终验证与测试包

- 指定F&A/Core安装矩阵58/58 GameTest、完全未安装两依赖的服务端1/1通过。新增4项升级桥接及5项真实机器测试，原有49项回归保留。
- 同一机器实际连续完成1→2→3→4→5，检查单核心组件、等级/容量同tick刷新、费用及普通输出满不阻塞；67件超堆核心只暂存一件，剩余66件等待玩家取回，升级结果不会混入普通物品队列。
- 已验证托管核心下的精华补充、断电/红石/增强物变化暂停、数据包目录暂缺、存档重载和拆机重放；旧任务无新标记按物品任务恢复。升级工作连续200tick不增加上下文解析次数，世界同步不携带整份付款存档。
- 桥接拒绝错阶、满级、伪核心、过期核心组件预览及伪造目标；拒绝时不损锤、不推进损耗随机数、不替换世界方块或生成原生视觉闪电。
- `check assemble validatePackagedIntegrations`、51项机器契约、既有离线回归及精确依赖哈希/可选边界均通过。
- 最终JAR内44份集成class与当前编译字节一致；双语、模型/方块状态等关键资源与源码一致，版本正确，没有旧预览类、测试类或第三方原实现入包。
- 模型未改，单方块/共面/法阵范围检查通过；客户端界面及外观尚未实机验收。
- 日志：`build/reports/forbidden-integration/1.0.6.22/installed-server.log`、`absent-server.log`。

测试包：`build/libs/mekanism_magic-1.0.6.22-neoforge-1.21.1.jar`

- 大小：2,860,087字节。
- SHA-256：`955151C0901CA6F5B560FFBE4F5F6DC427CEB1768954091C8285085163766B71`。
- 旧 `.21` 哈希复核未变，未发布。
