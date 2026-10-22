# 新增机器契约

新增机器不再复制旧机器的初始化、保存和掉落代码。默认使用
`NativeMagicMachineBlockEntity`，公共行为由基类和构建校验共同保证。

## 基类自动提供

- 六面 FE 输入、顶部物品端口、Mekanism 侧面配置和自动弹出。
- 普通机器界面默认使用统一标题栏：标题位于共享坐标，右侧为状态标记，长标题滚动且不压住状态。
  物品名与容器标题必须在中英语言文件同步，构建会拒绝缺少或不一致的标题。
- 普通机器默认使用原生 `MachineEnergyContainer` 的计量子类：统计实际 `EXECUTE + INTERNAL`
  扣款，显示上一完整世界tick耗电。模拟不计、普通充电不抵消；同tick操作退款必须调用
  `refundProcessingEnergy`，不能把`insert`充电当退款。计量仅同步打开的菜单，不保存到NBT。
- 标准输入槽只允许外部输入，标准输出槽只允许外部抽取；顶部合并端口不会再扩大特殊槽权限。
- 忘记调用 `setupNativeItemIO` 时，根据 `InputInventorySlot`、
  `OutputInventorySlot` 自动补齐配置，并在日志中警告。
- 重复逻辑槽编号直接中止启动，避免旧存档槽位静默覆盖。
- `onLoad`、区块保存和读取采用 final 模板；子类只能实现
  `onNativeMachineLoaded`、`saveNativeMachineData`、
  `loadNativeMachineData` 等安全钩子。
- 拆机掉落统一通过 `saveToItem` 保存 Mekanism 组件和机器自定义状态，
  包括长输出缓存、Source、模式和配方选择。
- 升级迁移统一保存全部已声明输入/输出槽、进度、所需时间、能量和组件。
  必须测试带 ENERGY 升级且充电量超过新机器未升级容量的实际 installer 路径：
  Mekanism/Extras 上游可能先恢复 FE、后读取容量升级，需在组件恢复后再次写回原 FE，
  避免中间容量限幅丢电。ME 状态迁移需同时覆盖普通与 Extras 两种独立升级器，
  并在 AE 节点真正重连后验证状态，不能把待初始化节点误判为丢失。
- 外部物品处理器缓存会在卸载、移除和复活时清空，不能复用失效 capability。
- 可选网络节点必须使用弱宿主引用、独立 NBT tag、首 tick 创建、区块卸载挂起、复活
  重建和真正拆块销毁；生命周期通过 `MachineNetworkLifecycleHooks` 接入，直接输出才
  使用 `MachineDirectOutputHooks`。同一机器只能有一个网络节点所有者，不能与升级
  插件重复挂载。

## 新机器只声明差异

1. 在 `createMachineSlots` 中创建槽，并用稳定的逻辑编号调用
   `registerLogicalSlot`。发布后不得重排已有物理槽或复用逻辑编号。
2. 标准输入/输出可交给基类推断。特殊槽必须通过
   `addNativeItemSlotInfo` 明确 `canInput/canOutput`；纯手动槽不要加入侧面配置。
   自定义或需持久保留的输入槽必须统一使用 `BasicInventorySlot.at` 的六参数重载：
   显式提供两个 `BiPredicate<ItemStack, AutomationType>`、物品 validator、listener 和
   坐标，并让提取谓词明确拒绝 `AutomationType.EXTERNAL`。禁止使用更短的重载（包括
   `validator, listener, x, y` 与附加 `limit` 的形式）：它们会让提取无条件通过，或
   无法证明已区分外部自动化。也不要静态导入 `BasicInventorySlot.at`，否则构建契约
   无法可靠识别重载。
3. 只实现配方查找、资源消耗和完成行为。不要覆盖 final 生命周期方法。
4. 有额外 NBT 时只实现 `saveNativeMachineData` / `loadNativeMachineData`。
5. 将注册 ID 加到 `config/machine-contracts.json` schema v2。普通机器使用
   `machine`，传输器使用 `pipe`，依赖模组写入 `requiredMods` 数组；不得增加
   `requiresNatureAura`、`requiresForbiddenArcanus` 一类专属布尔字段。静态 blockstate
   渲染的机器可显式设置 `"rendering": "blockstate"`，其余默认要求 BER 注册。若机器
   具有持久待产物、原生 AE 节点、专用升级载荷或 MekE 样板能力，还必须同步维护顶层
   `behaviorChecks`；该列表会与 Java 注册入口交叉校验。
6. 随机副产、多输出或动态组件结果必须在一次操作完成时只决定一次，并在等待输出时
   持久化；输出阻塞、区块卸载或重启不得重新抽取概率或再次调用有副作用的组装逻辑。
7. 外部样板输入必须先完成全量类型、数量与配方上下文验证，再执行完整 simulate；
   commit 失败必须保持输入槽与上下文不变，不能逐项插入后以“尽量回滚”代替事务。
8. 同一输入可对应多个配方时，样板上下文必须携带稳定 recipe ID 和配方内容签名；
   数据包以同 ID 改写输出、成本或 catalyst 后，旧签名必须清进度并拒绝投递。虚拟
   context 不得进入材料规划或物理槽，也不得通过新增内建节点与已有网络升级重复发布。
9. 样板一次提交多件且后续会清除物品上的临时 marker 时，必须按槽持久化上下文剩余
   件数；每次成功完成只递减一件，计数归零后才能释放 recipe 锁。输入被抽走时计数须
   向实际槽数量限幅，并纳入 NBT、机器升级载荷和事务回滚。
10. 自定义 `onUpdateServer` 必须在新准备/推进/扣费之前检查 `canFunction()`；公共基础更新
    不会替子类处理红石许可。已付款队列交付、充电等维护行为可继续，但不能把停机当取消任务。
11. 电力注册/储存/扣款使用J。定义为FE的单价须明确换算，最好对实际批次整体取整；不要直接
    将FE数字塞入J容器。GUI用 `getLastProcessingEnergyUsed()`，不以Active或额定功率猜实耗。
    有独立速度下限/上限的机器，还需同步限制功率倍率，避免速度不再增加却持续涨费。
12. 普通加工机器从 `MagicMachineEnergyProfiles` 选择Mek默认参考档位，注册属性、运行扣款
    和工厂每工位供应器必须共用同一基数。工厂能源属性按单工位定义，实际总功率由活跃工位相加；
    不把 `processes` 同时混入单工位基数。储能容量独立，不随调价缩减。
    资源转换/世界搬运/事件增幅的单位价格独立核算，不能套低价加工档位而保留原产量形成能量循环。
13. 名称先查所适配模组的语言文件/手册，保留辨识词并使用贴合功能的器械名。不得只填临时名称、
    注册 ID 或裸“处理机”。同步填写 `block.mekanism_magic.<id>` 与 `container.mekanism_magic.<id>`
    的 `zh_cn`、`en_us` 翻译，两者必须一致；相关 JEI 名称一并调整。更名不改注册 ID、配方 ID、
    NBT 字段，不覆盖玩家自定义名称。
14. 新普通机继承 `NativeMagicMachineScreen` 的标题栏；Mekanism/Extras 工厂通过
    `MagicThemedScreen.drawMachineHeader` 绘制标题和状态，不另抄坐标。两类 GUI 都使用统一滚动
    与状态留白。完成后运行 `validateMachineContracts` 和 `testMachineTitles`。
15. 需要收纳的辅助催化剂、核心、增强物和耐久工具可使用粉笔槽式侧栏。不要仅因物品
    不消耗就外置：维度采掘井的矿工、德格米的样本，以及魔灵驱策台和各级魔灵工厂的
    魔灵槽保留在主工作区。普通机在
    `RetainedSlotProfiles` 显式声明原逻辑槽编号；不能按“额外槽”或坐标猜测，避免把燃料、
    空瓶、金粉和树苗等耗材移入。界面只移动原菜单 `Slot`，不得新增库存、复制物品或更改
    槽顺序、NBT、自动化权限。初始化时先定位原槽，再让 Mekanism 创建边框；
    收起同步隐藏边框和停用实际槽，关闭/缩放恢复原位置。工具原耐久规则不因侧栏改变。
    原有粉笔、辞典和分页催化剂库继续使用自身容器，不能重复包装。运行
    `testRetainedModules`，并用实际 GameTest 菜单验证槽身份、组件、存档和权限。
16. 所有侧栏边框与槽框留白通过 `DockedPanelLayout` / `CatalystLibraryLayout` 共用计算：
    外框四边均为 2px，网格按完整 18px 槽框居中，不足一行也居中；顶部/底部内容间隔
    为 4px。标题、槽、翻页按钮和点击区域不能各自硬编码旧坐标。粉笔、辞典与
    催化剂库的实际菜单坐标必须和客户端使用同一份布局，变更位置不改变逻辑索引或存储。
17. 自然灵气和禁忌机器的常用读数使用透明的 `GuiMachineInstrument`，按 `MachinePanelLayout`
    对齐加工区与资源区。主界面只显示必要数值、进度、一次性操作与资源状态；
    不常驻主材、辅料、材料或产物标题，槽位用途、完整说明、精确数值与容量放在对应悬停提示。
    不要额外堆砌提醒图标、提醒框、
    长状态条或隐藏提醒热区，也不要把长说明缩成小字。调整界面高度时同步容器背包偏移，并测试实际槽框与信息、
    按钮和背包不重叠。经验仓只显示等级数字，不添加经验条；按级存取仍以真实经验点守恒，
    不能把等级当作线性货币直接加减库存。
18. 主题、AE 与机器工作模式统一放入 `GuiMachineSettingsWindow`，通过客户端
    `MachineSettingsSections.Section` 注册适用页面，不再另加调节页签。通用设置代码不能硬引用可选
    模组 API；可选页由该集成自己的客户端入口注册。页面只显示客户端同步状态，机器配置仍走
    服务器原有菜单按钮及校验；打开、切页、缩放和主题切换都不能改变槽身份、权限、库存或网络节点。
    仅收纳配置，保留 Mek 原生升级、侧面配置，以及催化剂、字典、粉笔等真实库存模块。
    `Context.x/y` 用于创建相对主 GUI 的控件；控件内调用 Mek 的滚动文字辅助方法时传本地坐标，
    不要再传一次 `relativeX/relativeY`，这些辅助方法会自动加控件偏移。
    设置窗采用原生 `InteractionStrategy.ALL`：窗口外的槽位与页签继续操作，窗口内空白、
    禁用控件及右键仍须拦截，不能穿透到背后的控件。窗口按当前页面的内容收缩，常用按钮为
    14px 高；提示说明放入悬停，不为简短设置保留大块空白。验收必须覆盖无材质包的深浅主题，
    并从屏幕真实鼠标分派验证操作，不能只直接调用按钮回调。
19. `MachineFrameLayout` 区分菜单内容坐标与真实外壳：本模组机器的原生背景和主题外壳统一向四周
    外扩 3px，原工作槽不随装饰边距移动；`GuiMachineFrameMixin` 的背景与点击范围必须共用
    `outerBounds`。左右页签同步移到新壳边，模块仅整体锚点外移，内部 2px 边框／4px 留白不叠加。
    手绘的 MekE 入口也需同步其渲染、点击及 JEI 共用 bounds，不能只平移图片。
    工作区最下方完整槽框／读数到背包标题、标题到背包完整槽框均至少留 3px；不要把槽内 16px
    物品区当作外沿，读条须使用原生含边框的实际尺寸。验证携物点击新增外壳边缘不掉物，
    并验证侧栏打开／关闭、重复初始化和缩放不会累计偏移。
20. 加工箭头以完整 18px 槽框之间的空隙计算居中和间距，不能按 16px 物品图标或旧工厂行坐标猜测。
    工厂共用 `GuiMachineProgress.factoryDown` / `MachineArrowLayout`：原生 8×20 箭头只裁短杆身到
    8×17，置于 y36，与输入／产物槽框各留 3px；不得直接改高度裁掉箭头头部，也不拉伸像素。
    填充显示、警告和配方点击仍使用原生 `GuiProgress`，裁剪、位移及临时原生尺寸必须在绘制后恢复。
    横向箭头保留原生尺寸，按实际左右槽组居中；辅助箭头必须有真实上下游槽，不能为无加工进度的
    机器添加假箭头或把资源吞吐脉冲误画成物品配方流。验证 0／50／100%、警告、停用和无材质包／资源包。

继承 `ArsSourceMachineBlockEntity` 时改用 `onArsMachineLoaded`、
`saveArsMachineData` 和 `loadArsMachineData`。Source 缓存、六面模式和能力注册由
Ars 基类的 final 模板先处理，子类无法再因漏调 `super` 丢失魔源状态。

Mekanism 原生工厂与 Mekanism Extras 工厂不能继承此基类，仍必须在所有槽创建后调用
`DefaultMachineSideConfig.apply`。其资源和注册完整性同样由 catalog 校验。

普通 AE2 外置图样供应器通过 `IMekanismMagicAutomation` API v6 的
`mekanismMagicPlanExternalItemInputs` 获取完整物品批次的角色分组。
单角色输入可复用默认实现；具有主副材料、激活物等专用槽的机器必须覆盖此规划方法，
以当前配方和精确产物确定分槽。规划阶段不得修改槽位或持久配置。
公共路由器核对所有分组的物品数量守恒、当前接触面的槽位权限，再模拟并提交整批输入。
见 [普通供应器说明](EXTERNAL_PATTERN_PROVIDER.md)。

顶部端口是为操作方便合并出来的 `INPUT_OUTPUT` 复合口，其中可能同时列出输入、输出和
特殊槽；侧面配置的 `canOutput=false` 不能替代槽本身的提取权限。Mekanism 自动弹出会先
直接对槽执行 `EXTERNAL` 模拟提取，再通过侧面物品处理器提交。若两条路径判断不一致，
目标容器可能收到物品而源槽未扣除，形成刷物品。因此不可弹出的槽必须在槽级拒绝
`EXTERNAL`，任何自定义弹出/AE 导出也必须保证模拟与提交命中同一权限层，并在提交数量
小于模拟数量时停止或回滚，不能先向目标插入再假定源槽一定能扣除。

## 构建硬校验

执行：

```powershell
.\gradlew validateMachineContracts
```

开发客户端所需的 `classes`、`check` 和正式打包都会自动执行该任务。以下任一问题会
直接令构建失败：

- 已注册机器未进入 catalog，或 catalog 中存在未注册 ID；
- 缺少 blockstate、物品模型、方块模型、掉落表、双语名称或挖掘标签；
- 缺少机器容器标题，或同一语言中的物品/方块名称与默认 GUI 标题不一致；
- 普通机器不是四向乘 active/inactive 共八种状态；
- 机器掉落未复制库存、能量、升级和侧面配置等 Mekanism 组件；
- 工厂遗漏统一默认侧面配置、专用升级载荷或长缓存待产物存档；
- 使用少于六个参数的 `BasicInventorySlot.at(...)` 创建提取权限不感知
  `AutomationType` 的特殊槽，或通过静态导入绕过该检查；
- 原生 AE 节点或 Mek Energistics 手工注册表与 `behaviorChecks` 不一致；
- 模型超出单方块坐标，存在完全重合 cuboid，或引用了不存在的本地模型/纹理。

`check` 还会扫描全部 `src/test/java/**/*SelfTest.java`，任何带 `main` 的离线回归测试
若没有对应 `JavaExec` 任务都会直接失败，避免新增测试存在但从未在发布构建中执行。

这样新增机器遗漏公共行为时会在编译或构建阶段暴露，而不是进游戏后逐台返工。
