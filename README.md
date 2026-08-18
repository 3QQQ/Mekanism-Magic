# Mekanism Magic

NeoForge 1.21.1 addon for Mekanism and Occultism.

最低核心兼容基线为 NeoForge `21.1.194`、Mekanism `10.7.15` 和 Occultism
`1.222.0`；同时兼容已验证的新版本 Mekanism `10.7.19.85` 与 Occultism
`1.224.2`。

Ars Nouveau `5.13.0` 为可选兼容；其内置依赖要求 NeoForge 至少
`21.1.205`。开发环境需要测试该兼容时，将
`mekanism_magic.ars_runtime` 改为 `true`。

## 当前实现

* **魔灵处理器**：使用 Mekanism/NeoForge 能量和物品能力，自动发现并处理 Occultism 的 `spirit_fire`、`crushing`、`crystallize` 配方。合成使用异界石、灵魂调谐水晶和黄金献祭碗等神秘学材料。
* **微缩仪式制作机**：输入最多 16 个法阵成型材料，配合 16 个颜色粉笔槽自动识别 Occultism 原始法阵并输出 18 种五芒星微缩仪式之一，不要求生存中无法正常获取的 `ritual_dummy/*` 物品。材料按原仪式配方逐槽匹配并消耗；彩虹粉笔和虚空粉笔可在任意颜色槽中作为全颜色通配，粉笔本身不消耗。粉笔槽收纳在右侧可展开的 Mekanism 风格模块中，关闭时不占用主工作界面。具体仪式仍由原仪式机器根据五芒星、材料、激活物品和献祭条件确定。
  额外魔灵槽中的物品不消耗；可直接放入 Occultism 的 Foliot、Djinni、Afrit、未绑定 Afrit、Marid 或未绑定 Marid 刷怪蛋，也兼容已装入这些魔灵的 Soul Gem、Fragile Soul Gem、Trinity Gem、Magic Lamp 和 Ars Nouveau `mob_jar`。机器按实体类型映射为 Occultism 的 1–4 阶魔灵；其他生物刷怪蛋和装有普通生物的容器不会进入该槽。
  粉碎与结晶会直接读取 Occultism 当前服务器配置中的等级、处理时间、产量倍率和单次处理数量。默认产量倍率依次为 `1.0× / 1.5× / 2.0× / 3.0×`；标记为忽略倍率的配方保持基础产量。灵火配方会像原模组一样按投入堆叠数量等量转换。
* **魔灵工厂**：新增 Mekanism 风格的基础/高级/精英/终极四级工厂，分别拥有 3/5/7/9 个并行工位。使用 Mekanism 基础、进阶、精英和终极 Tier Installer 可将魔灵处理器逐级升级；工厂共享一个不消耗的魔灵槽，每个工位独立输入、输出和进度。工厂采用 Mekanism 原版 `ACA / IPI / ACA` 合成结构，中心机器按基础→高级→精英→终极逐级升级。
  安装 Mekanism Extras `1.4.0` 后会额外注册绝对、至尊、宇宙、无限四级
  魔灵工厂，分别提供 `11 / 13 / 15 / 17` 个并行工位，并支持 Extras
  对应 Tier Installer 从终极工厂继续逐级升级。
* **仪式引擎**：读取 Occultism 的 `ritual` 配方，拥有 16 个普通输入槽和 1 个输出槽。另有不消耗的仪式选择槽、激活物品槽，以及可消耗的献祭槽。会生成实体的仪式改为输出对应的刷怪蛋，不在世界中直接召唤实体。
  合成需要 Occultism 异界石、灵魂调谐水晶和黄金献祭碗，并结合 Mekanism 钢制外壳、终极控制电路与金块。
  献祭物可用刷怪蛋、Occultism 的已填充灵魂容器或 Ars Nouveau 的已填充 `mob_jar`；容器献祭时只清除内部实体并保留空容器。召唤工作魔灵的仪式会把 `spirit_job_type` 写入刷怪蛋，因此交易者等职业在魔灵处理器中仍可被识别。
* **灵火与魔灵交易**：Occultism 1.224.2 的 27 个 `spirit_fire` 配方均不要求交易者职业，任意有效魔灵来源都可以处理；14 个 `spirit_trade` 配方则必须由 `spiritJob.factoryId` 精确匹配的交易者执行。真正的交易者会优先检查交易配方，输入不匹配时再回退处理灵火配方。
* `mekanism_magic:mini_ritual` 仅由微缩仪式制作机输出有效的五芒星微缩仪式，不再提供空白创造物品或空白合成配方。微缩仪式保存五芒星投影 ID，仪式引擎再结合材料、激活物品和献祭条件确定具体仪式。
* 两台机器现在直接使用 Mekanism 原生 `Machine`/`BlockTile`、`TileEntityMekanism`、`MekanismTileContainer` 和 `GuiConfigurableTile` 框架；魔灵处理器只显示收容罐槽，仪式引擎只显示仪式选择、激活和献祭槽。
* 两个升级槽接受 Mekanism 的 `speed_upgrade` 与 `energy_upgrade`。速度升级按 Mekanism 的 8 阶倍率缩短处理时间；能量升级按相同倍率降低能耗，速度升级增加的能耗也会计入。
* 两台机器都支持自动化输入/输出和能量输入，能量容量分别为 1,000,000 FE 与 4,000,000 FE。
* 手持材料右键机器会放入输入槽；空手右键会取出输出，适合先用单机验证配方，再接 Mekanism 管道自动化。
* Occultism 配方通过运行时桥接读取，不把 Occultism 的内部类写死到编译期，便于小版本升级。

## 在 IntelliJ IDEA 中打开

1. 以 Gradle 项目打开根目录 `E:\IdeaProjects\Mekanism Magic`，选择项目内的 `build.gradle`。
2. 使用 Java 21 SDK（NeoForge 1.21.1 的开发环境要求）。
3. Gradle 面板执行 `build`；运行配置由 ModDevGradle 自动生成，也可执行 `client` 或 `server`。
4. 请自行将依赖 JAR 放入本地 `libs/`（这些第三方 JAR 不包含在源码仓库中）：
   * `Mekanism-1.21.1-10.7.15.81.jar`
   * `occultism-1.21.1-neoforge-1.222.0.jar`
   * `ars_nouveau-1.21.1-5.13.0.jar`（可选兼容）

命令行等价操作：

```powershell
.\gradlew.bat idea build
```

构建产物位于 `build/libs/mekanism_magic-0.1.0.jar`。

附属模组兼容性记录见 [docs/COMPATIBILITY.md](<E:/IdeaProjects/Mekanism Magic/docs/COMPATIBILITY.md>)。Mekanism Extras 与 Mekanism: MoreMachine 的 JAR 已加入编译参考，但默认不会加载到 `runClient`；当前提供的两个 `1.4.0` JAR 在自身集成阶段会抛出 `MatchException`。确认使用兼容版本后，可将 `gradle.properties` 中的 `mekanism_magic.compat_runtime` 改为 `true`。

已验证的联合运行组合为 Mekanism Extras `1.4.0` + Mekanism:
MoreMachine `1.3.3`；两者同时使用 `1.4.0` 会因 MoreMachine 新增
`PRESSING` 工厂类型而在 Extras 的类型映射中触发 `MatchException`。

## 设计边界

真正需要实体召唤、献祭、命令或世界中的魔法阵布局的仪式不会被仪式引擎接管，以免改变 Occultism 原本的世界交互语义；这些配方仍应在神秘学魔法阵上完成。
