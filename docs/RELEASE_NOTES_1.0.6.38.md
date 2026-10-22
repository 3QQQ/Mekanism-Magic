# 1.0.6.38 — 可选 AE Terminal 界面资源包

新增资源包式界面入口，未启用资源包时继续使用现有深浅主题和星光背景。
默认模组 JAR 不包含 `mekanism_magic:gui/style.json`；外部包通过该资源启用双主题终端配色、
细边框面板、凹槽信息卡和无阴影按钮文字。移除包或配置无效后清除调色板与纹理缓存，恢复原样式。
补齐直接绘制的槽位图标、侧栏图标及九宫格按钮的换肤入口；不存在的附属图标回退到来源资源。

独立交付 `dist/resourcepacks/Mekanism-AE-Terminal-1.0.zip`，供 1.21/1.21.1 原版 Mekanism、
Generators、Extras、MoreMachine、Mek Energistics 与 Mekanism Magic 使用。
AE2 的 AE2Button、IconButton、TabButton、Scrollbar 与色表作为控件参考，不要求安装 AE2。
628 张贴图保留来源尺寸，覆盖两套 Magic 主题；原版 Mekanism 与附属使用浅色终端风格。
不包含方块/物品模型、配方、语言替换或 AE2 贴图，不自动启用或发布资源包。

验证：IDEA 构建；资源包尺寸/包格式/覆盖路径检查；文字色与按钮三态的最低对比度 5.05:1；
资源包启用、替换、损坏与停用回退；默认和终端双主题的信息卡离屏绘制；
机器契约、双语标题、侧挂模块布局及完整集成打包检查。
预览为使用输出贴图的样式检查图，尚未逐台进行游戏内视觉验收。
