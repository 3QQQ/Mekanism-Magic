# Mekanism Magic 1.0.5fix

## English

- Fixed the NeoForge release artifact omitting the Mekanism Extras factory
  bridges, which prevented the mod from loading when Mekanism Extras was
  installed.
- Restored the packaged Mekanism Extras, AE2, and Mek Energistics integration
  classes and Mixin configurations.
- Missing optional factory bridges now degrade safely instead of stopping the
  base Ars Nouveau or Occultism integration from loading.
- Added hard release checks for the JAR version, loader, Minecraft version, and
  required optional-integration entries to prevent incomplete artifacts from
  being published again.
- Corrected the declared minimum Ars Nouveau version to 5.11.0.

## 中文

- 修复 NeoForge 发布包遗漏 Mekanism Extras 工厂桥接类，导致安装
  Mekanism Extras 后模组无法启动的问题。
- 恢复发布包中的 Mekanism Extras、AE2 与 Mek Energistics 联动类及
  Mixin 配置。
- 可选工厂桥接缺失时改为安全降级，不再阻止新生魔艺或神秘学基础联动加载。
- 增加 JAR 版本、加载器、Minecraft 版本及必需联动内容的发布硬校验，防止再次
  上传不完整产物。
- 将 Ars Nouveau 声明的最低兼容版本修正为 5.11.0。
