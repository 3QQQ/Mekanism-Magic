# Mekanism Magic

Mekanism 风格的魔法自动化附属，提供神秘学、Ars Nouveau、Nature's Aura 与 Forbidden & Arcanus 的独立可选兼容，以及 AE2 / Mek Energistics / Mekanism Extras 等自动化桥接。

## 正式版 1.0.7

以已验证的 1.0.6.66 为正式基线。全部 786 个类文件与运行资源逐条目一致，仅版本元数据改为 1.0.7；不包含后续 Malum、Goety、Astral Sorcery 新机。

完整更新记录和升级提醒见 [1.0.7 更新日志](docs/RELEASE_NOTES_1.0.7.md)。

**先备份存档。** 使用过开发版旧灵魂提取机、模拟采血机或经验装瓶机的玩家，应先在旧版取出内容。新版本加载其所在区块时会直接删除旧机器，不迁移、掉落或返还库存及资源。

## 环境与可选兼容

- Minecraft 1.21.1、Java 21、NeoForge 21.1.228 或更高；本次使用 21.1.244 验证。
- Mekanism 10.7.19 或更高；验证版本 10.7.19.85。
- 神秘学、Ars Nouveau、Nature's Aura、Forbidden & Arcanus 均按实际安装情况独立启用，不需要全部安装。
- Nature's Aura 基线 41.9；Forbidden & Arcanus 基线 2.6.1 + Valhelsia Core 1.1.5。
- 高四档工厂需要 Mekanism Extras；网络功能按需安装 AE2、Mek Energistics、Applied Flux 等。第三方 JAR 不包含在本附属内。

## 主要功能

- 神秘学魔灵驱策、八档工厂、维度采掘、仪式加工与微缩法阵捕获／展开。
- Ars 魔源生产与转换、灌注及工厂、附魔装置、催化剂库和德格米活体产物。
- 自然灵气独立单方块机器、八档自然灌注工厂、供奉、森林仪式与刷怪蛋培育。
- 禁忌奥秘秘炉、机内升阶锻造台与四相精华凝聚器；原版容器及可选 PackagedFAA 流体互通。
- 八级魔力管道、1K–256K ME 魔力磁盘、适用机器的 AE 补给与真实产物回网、普通图样供应器整批分槽投料。
- 统一深浅主题、紧凑原生 GUI 与机器设置；保留 Mekanism 侧面配置、升级、真实库存及工具规则。

## 开发与验证

在 IntelliJ IDEA 中以 Gradle 项目打开仓库。普通开发启动默认保持较少可选运行依赖，按项目文档启用对应测试组合。

```powershell
.\gradlew.bat check assemble validatePackagedIntegrations "-Pmekanism_magic.require_full_integrations=true" --no-daemon --no-parallel
```

完整可选桥接构建需要开发环境所提供的二进制 API，不能把缺少桥接的核心构建当作正式完整包。
版本仅由 gradle.properties 的 mod_version 生成，规则见 [版本规则](docs/VERSIONING.md)。

本次发布仅面向 1.21.1 NeoForge，不变更 1.20.1 Forge 分支；两者不能混用 JAR。
