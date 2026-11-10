# Mekanism Magic 1.0.7fix — NeoForge 1.21.1

## English

This is a compatibility hotfix based strictly on the original **1.0.7 release**.

- Fixed startup being blocked after upgrading Forbidden & Arcanus or Valhelsia Core solely because their version numbers differed from the development baseline.
- Replaced the exact-version lock with checks for the actual classes, methods and fields used by this integration. Compatible API updates can load without adding a new version-number whitelist entry.
- Kept clear startup protection when required dependencies or APIs are genuinely missing, preventing saved machine registrations and inventories from being silently omitted.
- Machine behavior, registry IDs, inventories, recipes, models, textures and interfaces remain unchanged from 1.0.7. This hotfix does **not** include the later Malum, Goety, Astral Sorcery or development UI/machine changes.
- Verified with Forbidden & Arcanus 2.6.1 and 2.6.2 (94 required tests each), the missing-mod case, 112 original machine-network tests, and a real client world opening all three original Forbidden machine interfaces.

Replace the previous Mekanism Magic JAR; do not install both versions together. Binary API checks do not guarantee compatibility with every future upstream behavior change.

## 中文

这是严格基于原正式版 **1.0.7** 制作的兼容修复版。

- 修复升级禁忌与奥秘或 Valhelsia Core 后，仅因版本号与开发基线不同就被阻止启动的问题。
- 将精确版本锁改为检查本兼容实际使用的类、方法和字段。接口兼容的更新不再需要逐个增加版本白名单。
- 缺少必要前置或真实接口不兼容时，仍会明确阻止启动，避免静默省略机器注册而危及原有库存和存档。
- 机器行为、注册 ID、库存、配方、模型、材质和界面保持正式版 1.0.7 原样；**不包含**后续 Malum、Goety、星辉魔法以及开发中的新机器、UI 改动。
- 已验证禁忌 2.6.1 与 2.6.2（各 94 项测试）、未安装场景、原有机器网络 112 项测试，并在真实客户端世界打开三台原有禁忌机器界面。

请替换旧 Mekanism Magic JAR，不要同时安装两个版本。接口检查不代表上游所有未来行为变化都能自动兼容。
