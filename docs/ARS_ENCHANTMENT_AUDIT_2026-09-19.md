# 新生魔艺附魔配方检测（2026-09-19）

后续状态（2026-09-24）：本文记录的配方匹配、羊皮纸与 JEI 输入问题已在
[1.0.6.47 修复](RELEASE_NOTES_1.0.6.47.md)，两套完整回归各 160/160 通过。
以下保留当时的故障复现和证据，不代表当前版本仍有这些已修复故障。

## 范围与结论

本次为检测，不实施修复、不递增版本、不重新打包或发布。

在 `codex/release-1.0.6fix` 工作区、已发布提交
`c2a0d01f1538298baf66540402906ec6026a9100` 的运行时代码上执行。
使用 Minecraft 1.21.1、NeoForge 21.1.244、Ars Nouveau 5.13.0，
并加载 AE2、Mek Energistics、Ars Énergistique 等本地测试依赖。

已逐项确认主开发工作区 1.0.6.45 的配方桥接、精确输出解析、
机器 JEI 分类、JEI 主材料索引及 AE 转移文件与该发布代码一致。
本次只增加 GameTest 用例与本文档。

发现两个运行时匹配问题和一项 JEI 功能缺口。不是“反应附魔书全部不能合成”。

## 1. 反应 I–IV 的精确投料正常

- 反应 I 使用普通书、**已写入法术的法术羊皮纸**、青金石块、魔源宝石块。
- 真实 AE CPU 合成任务完成，返回正确附魔书；保留羊皮纸上的法术数据。
- 同一台附魔装置处理机从反应 I 连续升级到 IV，四级全部完成。
- 升级保留原法术与测试中追加的自定义名称；没有丢失附魔组件。
- AE 样板校验拒绝声明错误附魔或缺失法术组件的产物。
- 空白羊皮纸被 Ars 原生 ReactiveEnchantmentRecipe 拒绝，是原模组规则。
- 高等级附魔需已有前一级附魔，不能用普通书跳级；本次与原模组匹配/组装结果逐一对照。

## 2. 九条附魔书配方会被材料子集配方抢先匹配

检查当前加载的全部 97 条原生 EnchantmentRecipe（含 ReactiveEnchantmentRecipe）：
88 条精确输入通过 JEI 输出解析、AE 样板准入和真实机器连续加工。
其余 9 条输入均通过原生 Ars 的 matches/assemble，却被本模组机器选成其他配方：

| 目标附魔书配方 | 本次机器实际选择 |
| --- | --- |
| 保护 II、III、IV | 穿透 I |
| 横扫之刃 II、III | 穿透 I |
| 深海探索者 I、II、III | 水下呼吸 I |
| 击退 II | 冲击 I |

对应 ID：
`protection_2/3/4`、`sweeping_edge_2/3`、
`depth_strider_1/2/3`、`knockback_2`（均为 ars_nouveau 命名空间）。

### 真实机器复现：保护 IV

投入保护 III 附魔书，以及原生保护 IV 配方的全部八个副材料：

- 操纵精华 ×2；
- 防御精华 ×2；
- 魔源宝石块 ×2；
- 青金石块 ×2。

原生结果为保护 IV 附魔书。
真实机器结果却为 **保护 III＋穿透 I** 附魔书，剩余操纵精华 ×1、防御精华 ×2。
不是仅预览失败，也不是原生配方材料错误。

这里中文精华名称仅为说明；精确物品 ID 为
`ars_nouveau:manipulation_essence` 与 `ars_nouveau:abjuration_essence`。

### 根因与影响

`ArsNouveauRecipeBridge.findApparatusRecipe` 遍历配方时，
`matchIngredientStacks` 允许从整个输入库存抽取所需材料子集，
随后第一个匹配成功的配方便立即返回。没有优先识别完整目标配方。
在书能够接受多种附魔的条件下，低级配方会抢走高级配方的一部分材料。

`ArsApparatusRecipeViews.resolveExact` 又核验机器实际选中的配方 ID/产物，
因此这些目标的精确预览被拒绝；AE 样板还要求完整消耗所声明的输入，
因此不是简单地改 JEI 图片就能解决。

相关源码：
- `src/main/java/com/example/mekanismmagic/integration/arsnouveau/ArsNouveauRecipeBridge.java:731`
- `src/main/java/com/example/mekanismmagic/integration/arsnouveau/ArsApparatusRecipeViews.java:210`
- `src/main/java/com/example/mekanismmagic/integration/arsnouveau/ArsApparatusRecipeViews.java:147`

以上“被选中的配方”是当前依赖/数据集的实测结果；其他整合包或数据包的配方顺序可能改变具体误选对象。

## 3. 反应 I 混放空白与已写羊皮纸存在槽位顺序依赖

在同一组输入中放入书、空白羊皮纸、已写法术羊皮纸、青金石块、魔源宝石块：

- 空白羊皮纸在前时，配方桥接返回带“反应 I”及法术数据的**羊皮纸**，不是书。
- 交换两张羊皮纸的位置后，桥接返回无匹配。
- 输入物品与组件完全相同，仅交换无序输入槽的位置。

这项复现检查的是实际机器所调用的配方桥接；未单独对混放场景运行机器 tick。
原生反应附魔支持任意物品，羊皮纸也可成为主材料。
当前副材料匹配只寻找第一套 Ingredient 组合，
组合未通过原生的“羊皮纸必须有法术”检查时不会继续寻找其他组合；
这与主材料歧义检测共同产生了顺序相关的结果。

相关源码：
- `ArsNouveauRecipeBridge.java:759`：取得第一组副材料；
- `ArsNouveauRecipeBridge.java:769`：原生匹配失败后直接换主材料；
- `ArsNouveauRecipeBridge.java:804`：歧义则返回空；
- `ArsNouveauRecipeBridge.java:870`：副材料组合搜索。

暂时规避：一次仅投入一份准确配方，不混入额外空白羊皮纸或其他法术版本。

## 4. 反应 I 的机器 JEI 无法带入已写法术羊皮纸

`ArsApparatusJeiCategory.setRecipe` 用 Ingredient 的默认物品生成副材料，
只把 JEI 的真实输入焦点应用到主材料。副材料上的组件未被带入。

因此反应 I 页面始终使用空白羊皮纸进行组装：
原生匹配失败，没有可编码的 OUTPUT，无法从此机器 JEI 页面正确一键录入反应 I 样板。
实际带法术的精确输入可以通过机器与 AE 校验，问题位于 JEI 输入表示层。

另外，97 条配方中 68 条没有“无焦点默认产物预览”：
主要是必须使用前一级附魔物品的高等级配方，加上反应 I。
高等级配方在提供正确的前一级实际物品焦点后可以解析（不含前述冲突配方），
这属于当前精确输入设计的限制，不能把这 68 条全部统计为加工故障。

相关源码：
- `src/main/java/com/example/mekanismmagic/integration/arsnouveau/client/ArsApparatusJeiCategory.java:49`
- `src/main/java/com/example/mekanismmagic/integration/arsnouveau/client/ArsApparatusJeiCategory.java:83`
- `src/main/java/com/example/mekanismmagic/integration/arsnouveau/ArsApparatusRecipeViews.java:162`

本次 JEI 结论来自代码路径和服务端精确组装验证，未运行客户端 UI 自动化。

## 测试与证据

新增测试位于
`src/gameTest/java/com/example/mekanismmagic/gametest/ArsApparatusDynamicGameTests.java`：

- `reactiveBookKeepsWrittenSpellAndCompletesRealAeCpuJob`：通过；
- `reactiveBooksCraftAllFourLevelsInOneMachine`：通过；
- `allNativeEnchantmentBookRecipesCraftAndRejectWrongLevels`：失败，列出全部 9 条冲突；
- `protectionFourBookMustNotBeHijackedByPiercingOne`：失败，真实错误产物；
- `reactiveBlankAndWrittenPedestalSelectionAudit`：失败，槽位交换改变结果。

最终整套运行 **148 个 GameTest：145 通过、3 失败**。
失败断言保留为后续修复的回归用例，没有把已知缺陷改成“通过”。

运行命令（发布工作区）：

```powershell
$env:JAVA_HOME='E:\Zulu\zulu-21'
.\gradlew.bat runSpiritGameTestServer '-Pmekanism_magic.ars_runtime=true' '-Pmekanism_magic.arseng_runtime=true' --offline --no-daemon
```

最终日志：
`E:/IdeaProjects/Mekanism Magic/build/reports/reactive-audit/2026-09-19.log`。

发布工作区同时保留 initial.log、matrix.log、final.log：
`C:/Users/11207/.codex/worktrees/release-1-0-6-fix/Mekanism Magic/build/reports/reactive-audit/`。

未调用 jar/assemble/check 打包任务；运行时代码未修改。
1.0.6fix JAR 检测前后 SHA-256 相同：
`B03EF8E34CFE7D9B44FD842417AD971E1F790356DC1C3E43334CD249538C8AEF`。
开发版本保持 `1.0.6.45`，发布工作区保持 `1.0.6fix`；未提交、推送或替换已发布包。
