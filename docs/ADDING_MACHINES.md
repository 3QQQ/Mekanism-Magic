# 新增机器契约

新增机器不再复制旧机器的初始化、保存和掉落代码。默认使用
`NativeMagicMachineBlockEntity`，公共行为由基类和构建校验共同保证。

## 基类自动提供

- 六面 FE 输入、顶部物品端口、Mekanism 侧面配置和自动弹出。
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
   `requiresSpecificMagicMod` 一类专属布尔字段。静态 blockstate
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

继承 `ArsSourceMachineBlockEntity` 时改用 `onArsMachineLoaded`、
`saveArsMachineData` 和 `loadArsMachineData`。Source 缓存、六面模式和能力注册由
Ars 基类的 final 模板先处理，子类无法再因漏调 `super` 丢失魔源状态。

Mekanism 原生工厂与 Mekanism Extras 工厂不能继承此基类，仍必须在所有槽创建后调用
`DefaultMachineSideConfig.apply`。其资源和注册完整性同样由 catalog 校验。

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
