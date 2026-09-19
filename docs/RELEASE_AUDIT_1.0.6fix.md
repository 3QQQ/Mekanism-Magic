# 1.0.6fix 发布核验

发布基线：`v1.0.6-neoforge-1.21.1`。原开发工作区保持 `main` / `1.0.6.45`，
未删除其未完成内容，也没有覆盖历史开发包。发布整理在独立 `codex/release-1.0.6fix` 工作区进行。

## 范围

- 34 个设备 ID 与正式版基线完全一致。
- 36 个原配方保持，另按用户明确要求保留 `mini_ritual_blank` 捕获配方。
- 保留神秘学／新生魔艺、现有工厂及 AE／MekE／Ars Énergistique／Pipez 等适配的后续修复。
- 包内不存在自然灵气、禁忌的专属适配类、机器资源和配方，也不存在新魔法存储元件／接口。
- 法阵捕获及其完整回归测试保留；不将此前未复现的玩家反馈宣称为已修复。

## 验证

- `runSpiritGameTestServer`，含 Ars Nouveau、Occultism、AE2、Mek Energistics、Ars Énergistique 等：141/141 通过。
- 增加原版 Nature's Aura 41.9、Forbidden & Arcanus 2.6.1、Valhelsia Core 1.1.5 共存的边界矩阵：143/143 通过。
  两项新增检查确认设备集合不变、捕获配方存在、排除内容不注册且不在发布类路径中。
- 完整 `check` 通过：34 个机器契约、26 对机器标题、升级名称冲突、灌注 JEI、侧栏、保留槽、身份及持久化等自测。
- `assemble validatePackagedIntegrations validateFixRelease -Pmekanism_magic.require_full_integrations=true` 通过。
- 444 个包内主类与当前编译输出一致，无丢失编译类；未打入测试类或第三方模组 JAR。
- IDEA 已尝试打开发布工作区，但其 MCP 未列出该项目；编译和检查由 Gradle 完成，不声称 IDEA 构建成功。

测试日志在发布工作区的 `build/reports/1.0.6fix/`。这是受控测试，不是所有整合包的游玩验证。

## 校验值

发布包：2,335,286 字节。

`B03EF8E34CFE7D9B44FD842417AD971E1F790356DC1C3E43334CD249538C8AEF`

原开发 `.45` 保持：

`7E488F5DDE3CD41B425D1C66CA14DDAC263793B0640149ABEE973CA0A599E333`
