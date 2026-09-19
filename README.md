# Mekanism Magic

Minecraft 1.21.1 / NeoForge 的 Mekanism 魔法适配模组，提供神秘学与新生魔艺机器、
工厂、魔源传输，以及 AE2、Mek Energistics 等可选自动化兼容。

## 1.0.6fix 维护发布

本分支从正式版 `v1.0.6-neoforge-1.21.1` 整理后续修复，保留原有 34 个设备 ID。
修复范围与安装说明见 [1.0.6fix 发布说明](docs/RELEASE_NOTES_1.0.6fix.md)。

- 保留灌注连续加工、配方切换、微缩制作启动、机器状态保存、仪式蛋显示和侧栏布局等修复。
- 按用户确认保留空白微缩仪式捕获法阵，以及展开／回收和防重复收取检查。
- 不包含未完成的自然灵气、禁忌适配及其专属设备、配方、JEI／AE 适配。
- 不包含新魔法资源存储元件和接口；原有 Ars Énergistique 魔源网络兼容保留。

这不是开发版 `1.0.6.x` 的完整替代品。已经放置未发布机器或存入新资源元件的开发存档，
不要直接换成此包，以免失去对应内容。更新前备份存档；联机客户端和服务端应使用同一版本。

## 依赖

- Minecraft 1.21.1，Java 21，NeoForge 21.1.228+。
- 必需：Mekanism 10.7.19+。
- 魔法内容按需加载：Occultism 1.222.0+、Ars Nouveau 5.11.0+。
- 可选：JEI、AE2、Mek Energistics 3.0.6+、Mekanism Extras、
  Mekanism: More Machine、Ars Énergistique、ExtendedAE、Pipez。

没有安装对应魔法模组时，不注册该组机器。发布包不重新分发第三方模组。

## 构建与验证

版本唯一来源是 `gradle.properties` 的 `mod_version`，本次显式使用 `1.0.6fix`。
完整可选桥接依赖使用本地 `libs/` 和 `run/mods/` 中的参考 JAR；这些第三方文件不提交、不打入发布包。

```powershell
.\gradlew.bat check assemble validatePackagedIntegrations validateFixRelease "-Pmekanism_magic.require_full_integrations=true" --no-daemon
.\gradlew.bat runSpiritGameTestServer "-Pmekanism_magic.ars_runtime=true" "-Pmekanism_magic.arseng_runtime=true" --no-daemon
```

额外的排除边界测试可设置 `-Pmekanism_magic.verify_excluded_mods=true`，仅在测试运行时加载
自然灵气、禁忌及依赖，确认它们不会重新启用本发布包中已排除的适配；这不会将依赖装入 JAR。

`validateFixRelease` 校验原有设备集合、经确认保留的配方集合、版本元数据及排除内容。
GitHub Release 的附件经同样的发布约束检查后，由既有工作流同步到 CurseForge。

## License

MIT。第三方模组、名称与美术资源遵循各自许可。
