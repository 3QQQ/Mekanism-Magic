# Mekanism Magic 1.0.6.26（未发布开发版）

## 禁忌奥秘：机器专用JEI

继续原有适配里程碑4，给现有三台机器接入专用JEI，不新增工厂、AE样板、模型或精华传输系统。

| 机器 | 页面内容 |
| --- | --- |
| 禁忌燃烧处理机 | 原版15条配方，单/双输入、最低火焰、三种火焰时长、增强物、经验及概率残渣聚合 |
| 物品修饰处理机 | 原版6条修饰，合格目标、模板、追加材料及对应原生组装结果 |
| 自动赫菲斯托斯锻炉 | 原版22条仪式，10条创建、8条护甲转化、4条机内核心升阶 |

- 三类页面绑定现有机器，保留原版可用页面；无F&A或无JEI时不会强制加载不适用的类别。
- 修饰与转化输入/输出关联轮换，不会将一种护甲显示成另一种目标的结果。示例为合法默认物品；真实输入的组件/附魔仍须满足原版资格，机器继续使用真实输入组装。
- 核心、启动锤、增强物与消耗材料分开，锤的原版耐久规则不省略。机内升阶不展示为输出槽产物；残渣明确说明高级火概率、累积数量，普通火没有残渣。
- Aureal、Souls、Blood、Experience保持四项独立原始费用，页面不把客户端缺失的增强物效果算成实际折扣。

## 远程客户端与重载

独立显示快照从客户端同步的RecipeManager/RegistryAccess读取，不依赖集成服务端，也不更改服务端配方缓存。JEI布局/绘制只读快照，不扫描物品或动态注册表、不执行世界仪式、不抽随机残渣、不损锤。

原版2.6.1的网络仪式数据不包含法阵，增强物网络数据不包含执行效果：本轮单独处理这些缺失字段，保留“使用原版仪式法阵”提示，不猜法阵ID、折扣或增强后的燃烧时长。

同ID配方变化按显示内容签名替换，未变化的对象复用；切换注册表、断开世界或JEI关闭时清理旧引用。用户单纯停留在界面不会触发全目录扫描。

## 范围与校验

- 不改`.25`耗电档位、速度/能量升级、配方时长、精华费用、库存或旧存档格式。
- 精确依赖保持F&A 2.6.1 + Valhelsia Core 1.1.5；使用JEI 19.20.0.239 API编译。
- 本轮不注册AE转移，不宣称这些新页面已经支持精确AE下单。
- 通过IDEA后台检查，不启动或操作用户游戏；完整客户端JEI画面仍需实机验收。

```text
gradlew runForbiddenGameTestServer runForbiddenAbsentGameTestServer -Pmekanism_magic.occultism_runtime=false --no-daemon
gradlew check assemble validatePackagedIntegrations -Pmekanism_magic.require_full_integrations=true --no-daemon
```

旧`.25`测试包保留，SHA-256：`7535CE80D4E7A27D712F8208B4221FF8A20AE70E84FE70EDD8AC373A5378C3B3`。未发布或上传。

## 最终验证与测试包

- 指定F&A/Core安装矩阵80/80 GameTest通过，缺少F&A/Core的启动矩阵1/1通过；包含原有76项加工、输出、升阶、耗电回归以及4项新显示目录测试。
- 新测试实际执行原版NETWORK_CODEC和RecipeHolder.STREAM_CODEC往返，使用独立RecipeManager及重建的远程注册表验证15/6/22目录、增强物缺失effects、仪式缺失法阵、原modifier资格及组件结果；显示读取不改变服务端快照或revision。
- 6种真实category `setRecipe` API代理场景、提示回调、角色/坐标、成对索引、空资格拒绝及可见输入轮换校正通过。还验证隐藏对应产物时留空、切换输入时不残留旧结果。测试只初始化独立进程的空模组列表和物品注册，不启动窗口；不等于GL绘制或完整JEI运行时验收。
- 8项重载增删/复用测试、三类页面坐标边界、全部45项双语键、IDE后台错误检查通过。
- `check assemble validatePackagedIntegrations`成功；52台机器契约、既有离线回归、可选包隔离和精确依赖校验通过。
- 安装/未安装服务端最终日志归档于`build/reports/forbidden-integration/1.0.6.26/`。

测试包：`build/libs/mekanism_magic-1.0.6.26-neoforge-1.21.1.jar`，2,949,440字节。

SHA-256：`3B7D38E8AAED692907BA3F26C4FCDD9F883EE9E13471857CA8C7291808F3B515`。

包内617份main编译类逐字节核验一致，无GameTest/SelfTest类、无删除旧条目；资源仅版本元数据和双语文本变化。旧`.25`JAR哈希复核不变。工厂和AE样板仍属后续工作，未发布。
