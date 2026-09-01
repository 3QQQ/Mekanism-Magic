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

## 新机器只声明差异

1. 在 `createMachineSlots` 中创建槽，并用稳定的逻辑编号调用
   `registerLogicalSlot`。发布后不得重排已有物理槽或复用逻辑编号。
2. 标准输入/输出可交给基类推断。特殊槽必须通过
   `addNativeItemSlotInfo` 明确 `canInput/canOutput`；纯手动槽不要加入侧面配置。
3. 只实现配方查找、资源消耗和完成行为。不要覆盖 final 生命周期方法。
4. 有额外 NBT 时只实现 `saveNativeMachineData` / `loadNativeMachineData`。
5. 将注册 ID 加到 `config/machine-contracts.json`。普通机器使用
   `machine`，传输器使用 `pipe`；Ars 条目设置 `requiresArs`。

继承 `ArsSourceMachineBlockEntity` 时改用 `onArsMachineLoaded`、
`saveArsMachineData` 和 `loadArsMachineData`。Source 缓存、六面模式和能力注册由
Ars 基类的 final 模板先处理，子类无法再因漏调 `super` 丢失魔源状态。

Mekanism 原生工厂与 Mekanism Extras 工厂不能继承此基类，仍必须在所有槽创建后调用
`DefaultMachineSideConfig.apply`。其资源和注册完整性同样由 catalog 校验。

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
- 模型超出单方块坐标，存在完全重合 cuboid，或引用了不存在的本地模型/纹理。

这样新增机器遗漏公共行为时会在编译或构建阶段暴露，而不是进游戏后逐台返工。
