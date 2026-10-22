# 1.0.6fix 微缩法阵无法制作检测（2026-09-20）

## 结论

已复现发布代码的材料匹配缺陷，并非输出槽实际堵塞：

- 法阵扫描读取的是 Modonomicon 的预览方块，再将它转换为唯一允许的 ItemStack。
- 原生法阵的蜡烛位置接受方块标签 #minecraft:candles；机器没有保留该标签要求，
  而使用 ItemStack.isSameItemSameComponents 精确比较。
- 当前测试环境中因此只接受 minecraft:candle，拒绝同样属于该标签的
  minecraft:white_candle、occultism:large_candle、occultism:large_candle_white。
- 绑定世界的法阵预览还会按游戏时间轮换标签成员，导致生成材料受预览状态影响。
  这为客户端显示与服务端要求不一致提供了可复现条件，
  但本次未操作用户客户端、也未直接核验用户客户端/服务端缓存。

本次仅检测，未修改运行时代码、版本号或发布 JAR。

## 截图对应配方与实测

用户确认使用发布版。当前原生 18 法阵目录中，黄色染料标记对应
occultism:craft_foliot（埃兹维斯的灵体驱策）。

基线生成的完整材料：

- minecraft:yellow_dye ×1；
- minecraft:candle ×8（原版未染色蜡烛，不是白色蜡烛）；
- occultism:golden_sacrificial_bowl ×1（黄金仪式之碗）。

需要白色、紫色粉笔。按截图同时放白色、紫色、灰色、黄色粉笔不阻塞制作。
本次实际运行机器更新，得到正确微缩法阵：

| 蜡烛 ×8 | 碗 ×1 | 真实机器结果 |
| --- | --- | --- |
| 原版未染色蜡烛 | 黄金仪式之碗 | 成功 |
| 原版未染色蜡烛 | 普通献祭之碗 | 无产物、警告 |
| 原版白色蜡烛 | 黄金仪式之碗 | 无产物、警告 |
| 神秘学大号蜡烛 | 黄金仪式之碗 | 无产物、警告 |
| 神秘学大号白色蜡烛 | 黄金仪式之碗 | 无产物、警告 |
| 上述三种替代蜡烛 | 普通献祭之碗 | 均无产物、警告 |

普通献祭之碗不属于原生中央仪式碗要求，不应视为可互换材料。
其他原生中央碗变体本次未逐一测试。

截图仅显示物品图标，不能可靠读取蜡烛和碗的物品 ID 或附加组件；
因此不能仅凭此图断言用户具体用了哪一种。上述是已确认的代码缺陷与可用对照配方。
若用户实际已投入未染色蜡烛和黄金仪式之碗，仍需核验实际组件、数据包及服务器配方。

## 预览状态复现

对同一原生法阵调用与 Modonomicon 预览相同的 setLevel，
分别在游戏时间 0 和 20 重新生成配方目录：

- 时间 0：黄色染料 ×1、minecraft:candle ×8、黄金仪式之碗 ×1。
- 时间 20：黄色染料 ×1、minecraft:white_candle ×8、黄金仪式之碗 ×1。

实验后恢复原游戏时间、清除绑定世界并使测试缓存失效。
全部操作位于隔离 GameTest 世界，没有修改用户存档。

原生依赖代码依据：

- DenseMultiblock.getBlockState 使用绑定世界的 getGameTime（未绑定时为 0）。
- TagMatcher.getDisplayedState 对未指定 display 的标签按 time / 20 轮换成员。
- BookMultiblockPageRenderer 和 MultiblockPreviewRenderer 会绑定客户端世界。
- craft_foliot 的蜡烛映射是 #minecraft:candles，未指定固定 display。
- 本地 Occultism 1.222.0 与 1.224.2 的该法阵 JSON 相同；
  本轮运行验证使用 1.222.0，不冒充已运行 1.224.2。

## 为什么提示“输出堵塞等无法制作”

NativeMagicMachineBlockEntity 在 findRecipe 返回空时把
inputDoesntProduceOutputWarning 设为 true。
NativeMagicMachineScreen 将该状态绑定到 Mekanism 的
INPUT_DOESNT_PRODUCE_OUTPUT 通用警告。

因此材料或粉笔不匹配也会出现该文字。仅凭这条提示不能诊断为输出库存已满。

## 测试和版本保护

运行环境：1.0.6fix 发布提交 c2a0d01f1538298baf66540402906ec6026a9100，
Minecraft 1.21.1、NeoForge 21.1.244、Occultism 1.222.0、
Modonomicon 1.120.4。

发布工作区：
C:/Users/11207/.codex/worktrees/release-1-0-6-fix/Mekanism Magic

三份关键运行时文件与主工作区 1.0.6.45 逐项哈希一致：

- NativeMiniRitualAssemblerBlockEntity.java
- NativeMagicMachineBlockEntity.java
- OccultismRecipeBridge.java

仅增加 MiniRitualCraftingGameTests 的 3 个诊断用例，以及一个 build/ 下的临时 Gradle 测试筛选脚本。
运行 9 个测试，7 通过、2 失败：

- 全部 18 法阵、彩虹/虚空/对应颜色粉笔、重复加工、槽位和输入稳定等待等已有测试通过；
- 截图材料/碗组合对照用例通过；
- 原生标签蜡烛替代用例失败；
- 预览时间不应改变材料用例失败。

两项缺陷保留失败断言，未把故障改为“通过”。
上次附魔书检测的失败用例本轮未运行，也未删除或修改。

日志：
E:/IdeaProjects/Mekanism Magic/build/reports/mini-ritual-audit/2026-09-20-published.log

发布工作区复现命令：

~~~powershell
$env:JAVA_HOME='E:\Zulu\zulu-21'
.\gradlew.bat runSpiritGameTestServer -I build/diagnostics/mini-ritual-audit.gradle '-Pmekanism_magic.ars_runtime=true' '-Pmekanism_magic.arseng_runtime=true' --offline --no-daemon
~~~

1.0.6fix JAR 检测前后 SHA-256 相同：
B03EF8E34CFE7D9B44FD842417AD971E1F790356DC1C3E43334CD249538C8AEF。

未执行打包、提交、推送、上传或替换发布包。主工作区版本保持 1.0.6.45。
