# AE Terminal Complete 2.0 亮色资源包

2026-09-24，按用户提供的 CozyUI+ v1.10（JEI 新版兼容版）结构扩展此前 1.8 GUI 材质。
只产出亮色主皮肤，资源格式 34，目标 Minecraft Java 1.21.1。

## 交付

- `dist/resourcepacks/AE-Terminal-Complete-Light-2.0.zip`：可直接启用的资源包，4,874,949 字节。
- 同名 `-source.zip`：生成器、校验器、精选原始 GUI 输入和原作者许可；不是安装到游戏的资源包。
- 同名 `-preview.png`：实际输出纹理的合成预览，不是世界截图。
- 同名 `-client.png` / `-client.json`：隔离 Minecraft 客户端验收屏与报告。
- 同名 `-coverage.png` / `-checks.json`：跨命名空间的贴图检查图及检查结果。

资源包 SHA-256：`176763c3491ed099cebd4d4423f3414856927d212bd25e40229f356477577cb8`。

安装与授权来源说明见 [资源包 README](../resourcepacks/ae-complete-light/README.md)。
停用旧版 AE Terminal、单独 MEK-AE Ui、CozyUI+ 等重叠 UI 包，将新包置于最高优先级。

## 完成的范围

覆盖 27 个命名空间，共 1,502 张贴图资源；其中 205 个内部路径是亮色兼容别名，
并不代表另外制作了暗色版。`style.json` 的两个必需字段也使用同一亮色色表。
原版 GUI 531 张、JEI 73 张、AE2 49 张；其余为 Mekanism 系列和参考包中的辅助模组 GUI。

按本地 Minecraft 1.21.1、JEI 19.39.0.369、AE2 19.2.17 的真实资源尺寸和元数据建立
优先级，不把高分辨率参考的动画／切片定义错配到原版贴图。AE2 原生灰紫面板原色保留，
新版 JEI 的酿造组件／箭头不作为普通背景覆盖，动态准星的特殊功能像素保持原样。

排除参考中的字体、表情、语言、音效、原版物品外观、全景图、品牌标题和 RGB 动画。
不改变机器槽位、代码绘制布局或玩家存档。QIO/RGB 等硬编码浅字限制仍需单独的代码支持，
不是增加资源路径就能全部消除；本次不修改运行时代码。

## 验证

- 每个 PNG 解码、资源 ID、ZIP 路径、源／输出尺寸与清单哈希检查。
- 73 份九宫格元数据与对应贴图匹配，检查中心切片不为空，合成后无透明洞。
- 815 个功能图形透明遮罩保护；字体、shader、游戏数据、JAR 和 class 未混入资源包。
- 白字原生按钮／悬停按钮／灰字禁用按钮、输入框、提示框、面板及 JEI 文字的
  关键对比度为 4.68–9.63；原生按钮实际截图中的文字像素再次检查。
- 隔离验收客户端实际重载完整 ZIP，确认全部 1,502 张资源来自新包，450 张原版 GUI
  sprite 已进入真实图集且不是缺失纹理；日志无资源加载错误。
- 客户端测试环境为 Minecraft、NeoForge、Mekanism、Mekanism Magic。
  AE2/JEI 面板在验收屏中读取真实输出纹理，不等于启动了这些模组的完整界面。
  辅助模组未逐个安装／游玩实测，不能声称所有版本均兼容。
- 从交付源码 ZIP 提取到新的临时目录重建，输出资源包 SHA-256 完全相同。

复验：

```powershell
python tools/build_complete_light_pack.py
python tools/validate_complete_light_pack.py
python tools/build_complete_light_pack.py --source-bundle
.\gradlew.bat -I tools/ui-pack-smoke.init.gradle runUiPackClient "-Pmekanism_magic.occultism_runtime=false" --no-daemon
```

客户端夹具位于 `tools/ui-pack-smoke/`，只由显式 init script 启用，运行目录是
`build/ui-pack-client/`，结束后自动退出，不进入正常运行或模组 JAR。

## 主项目与历史文件保护

未修改 `src/main`、`build.gradle`、运行时版本或模组 JAR，也没有发布／上传。
`gradle.properties` 仍为原来的 `1.0.6.47`，文件 SHA-256：
`6B19FB7EE95EBCFEC47246E56CD84B9557A0FB0354E11B7015327AB80E57E442`。

既有 1.8 资源包 SHA-256 保持：
`062163C700AB9FFBB582097E8146B5AF507E60B14B0C4550DDCCA89B74F0EFFE`。

主项目历史 `.45` JAR SHA-256 保持：
`7E488F5DDE3CD41B425D1C66CA14DDAC263793B0640149ABEE973CA0A599E333`。
