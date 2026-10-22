# Mekanism Magic 1.0.6.31（未发布开发版）

## 魔法磁盘资源显示

修正 ME 魔力磁盘中的魔源、灵气显示成宝石和瓶子的问题。此前实际存储的仍是魔法资源键，
只是客户端渲染将它们转成 `AEItemKey` 代画，造成“在存物品”的观感；本轮不迁移存档数据。

- 魔源参考本地 Ars Énergistique `2.1.1-beta` 的 `SourceRenderer`，使用方块图集中的
  `ars_nouveau:block/mana_still`，以 16×16 原生动态魔源纹理显示，不再用魔源宝石。
- 灵气使用 Nature's Aura `41.9` 自己的 `textures/particles/magic_round.png` 光点，
  颜色来自实际 `IAuraType.getColor()`；主世界、下界、末地及扩展类型分别取自身颜色，
  不再显示瓶子，也不借用 Ars 的贴图来表示 Aura。
- 磁盘内容提示、AE 终端、元件工作台/接口配置槽和存储监视器均使用统一资源渲染。
  监视器保持原生魔源盘的居中留边、光照、UV 方向和朝向；透明灵气光点使用独立透明层。
- 图集精灵在绘制时解析，保留资源包重载与原生动画；颜色按本次绘制传入，不污染相邻槽位。
- 有 Ars Énergistique 时继续复用其原生 SourceKey/渲染器，不额外注册一份魔源，
  无该模组时使用相同 Ars Nouveau 原生纹理，未增加对 Ars Énergistique 客户端类的硬依赖。
- 公共扩展 API 为第三方资源显式注册的物品图标保留原行为；本轮修正所有当前内置魔源/灵气条目。
- 不改磁盘外壳、物品合成、容量、数值单位、资源 ID、组件编解码、分区、AE 存取和管道。

只更新显示代码，开发号递增一次为 `.31`；`.30` 测试包保持不变。需要完整重启客户端，
仅重载资源包不能替换已经加载的 Java 类。现有 `Ars Compatibility Client` 启动配置已包含
自然灵气、禁忌奥秘及其前置；本轮不主动终止用户正在运行的游戏或修改用户存档。

## 验证

- `testMagicResourceRendering`：魔源/灵气视觉参数、实际监视器四向顶点/UV/颜色/光照、
  异常后的姿态恢复、真实 GUI 绘制与客户端注册路径。测试不创建 GL 窗口。
- 独立 `runMagicStorageGameTestServer` 复用真实磁盘、驱动器、ME 箱子、分区与接口往返测试，
  另检查所有原生灵气类型颜色及资源身份不被转成物品/流体。
- 分别验证无 Ars Énergistique 的自有魔源键、有 Ars Énergistique 的原生 SourceKey。

```powershell
.\gradlew.bat testMagicResourceRendering --console=plain
.\gradlew.bat runMagicStorageGameTestServer "-Pmekanism_magic.ars_runtime=true" --console=plain
.\gradlew.bat runMagicStorageGameTestServer "-Pmekanism_magic.ars_runtime=true" "-Pmekanism_magic.arseng_runtime=true" --console=plain
.\gradlew.bat check assemble validatePackagedIntegrations "-Pmekanism_magic.require_full_integrations=true" --console=plain
```

参照 Ars Énergistique JAR SHA-256：`9E2B78101F08BF325589CB98FFBA9D91171FDD76BA587D54BDEF9D5DE18080BC`。
自动化接口/顶点检查不等同于本轮客户端最终画面验收；未关闭或重启用户当前客户端。

## 最终结果

- 自有魔源键服务端 **12/12**、Ars Énergistique 原生魔源键服务端 **12/12** 通过；
  原生分支实际验证 `SourceKey.KEY` 单例与别名不能形成第二份余额。
- 可选依赖缺席服务端 **1/1** 通过，`testMagicResourceRendering` 及完整 `check assemble
  validatePackagedIntegrations` 通过，包括 55 项机器契约和全部既有离线自测。
- IDEA 对三个修改的主类未报告错误。包内 649 个主类逐一与当前编译结果核对一致，无测试类。
- 相比 `.30`，仅替换旧匿名客户端渲染器、增加具名资源渲染器/视觉描述、增加原生灵气颜色查询，
  以及更新版本元数据；磁盘存量、接口存取和其他机器类的编译内容未变化，没有新增或复制第三方贴图。
- 日志：`build/reports/magic-resource-display/1.0.6.31/` 下的 `own-source-server.log`、
  `native-source-server.log`、`optional-absent-server.log`。联合环境仍有既有上游可选目标/战利品警告，
  测试通过不等于所有上游日志均无警告。

测试包：`build/libs/mekanism_magic-1.0.6.31-neoforge-1.21.1.jar`，**3,067,019 字节**。

SHA-256：`B63926284BB0CBF64B9F190D6CA39715427D4E04087354C5D97993D43E64A9DB`。

旧 `.30` 包保持 `D24092CB782A6CF419A29E1771463A58FECD58ED63675F83A21BB936D15D48D9`，没有覆盖。
