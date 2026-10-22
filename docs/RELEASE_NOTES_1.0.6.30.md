# Mekanism Magic 1.0.6.30（未发布开发版）

## 魔灵配方按职业分页

将原先混排的魔灵处理 JEI 配方拆成独立职业页，减少查找时跨职业翻页。

| 分类标题 | 页面内容 | JEI ID |
| --- | --- | --- |
| 魔灵·碎矿者 | 四级粉碎魔灵配方 | mekanism_magic:spirit_crushing |
| 魔灵·铸晶者 | 四级结晶魔灵配方 | mekanism_magic:spirit_crystallizing |
| 魔灵·熔炼者 | 四级魔灵；熔炉、高炉、烟熏炉及营火配方 | mekanism_magic:spirit_smelting |
| 魔灵·异界岩交易者 | 原生 otherrock 交易职业 | mekanism_magic:spirit_trade_otherrock |
| 魔灵·异界石交易者 | 原生 otherstone 交易职业 | mekanism_magic:spirit_trade_otherstone |
| 魔灵·异界树苗交易者 | 原生 otherworld_saplings 交易职业 | mekanism_magic:spirit_trade_saplings |
| 魔灵·赌徒 | 原生 gambler；兼容旧 trader_gem 职业名 | mekanism_magic:spirit_gambler |
| 魔灵·通用灵火 | 不限定工作职业的四级灵火配方 | mekanism_magic:spirit |

另注册没有原生记录的“其他职业”兜底页 `mekanism_magic:spirit_other`，防止未来扩展的显示记录被静默丢弃；空页由 JEI 过滤。没有保留重复的“全部配方”大页。

- 依据原生配方种类与魔灵 `spiritJob.factoryId` 分类，不依赖物品译名或本地语言。
- 同职业的四级魔灵放在同一分类，并保留各自原有输入、输出、数量与组件。没有合并不同等级的产物。
- 每条显示记录恰好进入一页。展示 ID 包含职业、原配方 ID、实体等级与职业标识，避免同一原生配方的四级行共享收藏标识。
- 所有职业页保持原来的魔灵 `CATALYST`、原料 `INPUT` 和成品 `OUTPUT` 角色、槽名与布局；随机交易权重和不可作为确定性样板的提示保留。
- 普通魔灵处理机、Mekanism 四级魔灵工厂、Extras 四级魔灵工厂的 JEI 催化器与 GUI 配方按钮同步关联分类。
- 数据包/魔灵配置重载按职业增量更新：未变记录复用；职业改变时从旧页隐藏并加入新页；移除记录不残留。
- 仅改变配方浏览组织，不改魔灵匹配、产量、随机选择、FE、加工时间、工厂并行或 AE/MekE 投递逻辑。

替换 JAR 并重启游戏后，从机器配方按钮或 JEI 查看魔灵机器用途，即可切换职业标签。
旧版魔灵配方收藏的显示 ID 改变，必要时重新收藏；已编码的 AE 样板与原生配方 ID 不改变。

## 验证范围

- 离线执行真实 JEI 分类、注册、布局与重载方法，覆盖 9 类、13 种槽场景、三类 GUI 路由；不启动客户端、不调用 OpenGL。
- 安装 Occultism 后使用实际配方目录检查：分组前后逐条一致、无遗漏/重复、全部 16 个加工职业、四级魔灵、显示 ID 唯一性。
- 同时运行既有 Ars/Occultism 能量、持久化与 AE 无催化灌注回归；另跑可选集成缺席启动矩阵。

```powershell
.\gradlew.bat testSpiritJeiGrouping --console=plain
.\gradlew.bat runEnergyGameTestServer "-Pmekanism_magic.ars_runtime=true" --console=plain
.\gradlew.bat runForbiddenAbsentGameTestServer "-Pmekanism_magic.occultism_runtime=false" --console=plain
.\gradlew.bat check assemble validatePackagedIntegrations "-Pmekanism_magic.require_full_integrations=true" --console=plain
```

版本号来自 `gradle.properties`，本轮仅递增一次。不发布、上传或覆盖已完成的 `.29` 包。
自动化接口检查与服务端测试不等于客户端画面验收。

## 最终结果

- `testSpiritJeiGrouping` 通过：9 类、13 种真实槽构建场景、分类注册/增量重载、3 类 GUI 路由。
- Occultism/Ars 联合服务端 **57/57** 通过；可选集成缺席服务端 **1/1** 通过。
- `check assemble validatePackagedIntegrations` 通过，包括全部 55 项机器契约及既有离线自测。
- 本地联合环境拆分前后均为 **989** 条显示记录：碎矿 326、铸晶 93、熔炼 448、三类交易各 1、赌徒 11、通用灵火 108、其他 0。实际数量会随安装模组、数据包和配置变化。
- 包内 648 个主类逐一与当前编译结果核对一致；9 个职业翻译键中英配对；没有测试类混入，也没有删除 `.29` 包内的旧条目。
- 日志归档：`build/reports/spirit-jei/1.0.6.30/`。联合环境仍有既有的 MekMM/MekE 可选战利品引用日志；没有将测试通过表述为所有上游日志均无警告。

测试包：`build/libs/mekanism_magic-1.0.6.30-neoforge-1.21.1.jar`，**3,063,323 字节**。

SHA-256：`D24092CB782A6CF419A29E1771463A58FECD58ED63675F83A21BB936D15D48D9`。

历史 `.29` 包 SHA-256 仍为 `185A8606A34B06086FF0008829D6F32FF85144820FB3E7E7CF122B3866DF9221`，未覆盖。
