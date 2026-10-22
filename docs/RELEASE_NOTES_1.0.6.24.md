# Mekanism Magic 1.0.6.24（未发布开发版）

## 实际耗电显示与红石停机

- 所有继承 `NativeMagicMachineBlockEntity` 的普通机器改用原生 `MachineEnergyContainer` 的计量子类，费用、容量、升级倍率和端口权限保持原机制。能源页改显示**上一完整世界tick实际扣除的J**，由Mek按当前显示单位换算，不再以Active动画乘额定功率估算。
- 堆叠批次、事件式增幅、部分产量均按真实执行的内部扣款累计；SIMULATE不计，正常充电不抵消，同tick失败或部分提交的操作退款会扣回统计。只同步打开的菜单，不增加全世界每tick广播；瞬时历史不进NBT，加载/卸载/恢复清零。
- 事件式设备会在发生收费的游戏刻显示峰值、其余游戏刻显示0，这是实际读数，不再把动画脉冲平摊成持续耗电。工厂原本使用真实能量差的显示保持不变。
- 德格米与维度矿机在新准备、推进和付费前检查原生红石许可；HIGH无信号、LOW有信号时停止加工，不重抽已有随机结果，不清空进度或重复支付。基础充电、已付款缓存交付仍可执行。PULSE仍遵循原版“不支持”的行为，没有新增脉冲模式。
- 德格米原有流体策略保持：只有AE接受时才输出，离线/拒收时按既有规则处理，不借本次红石修复改变产物流向。

## 单位修复与速度上限

- 灵气场接口真正按 `20 FE/Aura` 定价，先应用支持的ENERGY升级，再对实际批次做一次FE→J换算并向上取整。默认5000 Aura一批为100000 FE，每20tick满批持续搬运平均5000 FE/t。
- 旧代码误把20 FE当20 J，默认实际上仅8 FE/Aura；这项修复使实价回到原说明值。操作先预留电量，根据实际搬运量退款，仍只搬运已有世界Aura。
- FE魔源转换器保持现有基础5000 J/t、100tick产1000 Source，即默认200 FE/Source。新增正确J单位常量，旧名称留作弃用兼容别名；纠正旧文档500 FE/Source的错误，不按错误注释额外涨价。
- 动物生成机仍保留20tick最低周期。触及下限时，功率只按实际能实现的加速倍数平方计算，再应用ENERGY折扣；未触及下限直接用Mek原公式，避免浮点中间值导致额外多收1 J。
- 内置牛配方60tick示例：无升级仍14400 FE/只；8S0E为20tick、5400 J/t，共43200 FE/只（原先480000）；8S8E为540 J/t，共4320 FE/只。22tick封顶精确726 J/t；不靠epsilon忽略真实小数。
- 本轮没有统一降低其他机器基础功率、取消Ars既有纯魔源慢速模式、改变工厂并行或重写配方费用。

## IDEA 工作方式

已复用本机 IntelliJ IDEA 2026.2.0.1 的MCP服务确认当前工程与模块，并检查能耗容器、通用基类和动物生成机代码。项目 `AGENTS.md` 已记录：后续工作先检查IDEA；未启动时打开已安装IDEA及本项目，优先后台接口，不重复开实例、不关闭其他工程。此规则不是常驻桌面监控。

IDE执行临时自测的接口曾返回不确定结果；不把该调用视为IDE运行成功。正式结果以独立Gradle自测和服务端回归为准。

## 校验入口

```text
gradlew runForbiddenGameTestServer runForbiddenAbsentGameTestServer -Pmekanism_magic.occultism_runtime=false --no-daemon
gradlew runEnergyGameTestServer -Pmekanism_magic.ars_runtime=true --no-daemon
gradlew runNatureOnlyGameTestServer -Pmekanism_magic.occultism_runtime=false --no-daemon
gradlew check assemble validatePackagedIntegrations -Pmekanism_magic.require_full_integrations=true --no-daemon
```

未发布；旧 `.23` 测试包保留，SHA-256：`E2C9AC0D9CCBA342148089A9D7B8081EFABD75CEDD2F8460E4DE6376F8D5E18E`。

## 最终验证与测试包

- 禁忌奥秘/通用计量服务端矩阵74/74、无依赖启动矩阵1/1、自然灵气回归59/59、能耗/红石/在线AE专项18/18均通过。各矩阵存在重叠用例，不将数量相加宣称独立测试总数。
- 两个新增离线自测、既有离线回归、52项机器契约与 `check assemble validatePackagedIntegrations` 全部通过。
- 专项首轮两项失败源于测试将离线流体策略误当停机丢失；改为实际在线AE物品/流体仓，核对“队列＋本地＋ME”完整组件键总量，保留原离线策略和严格守恒断言，复跑通过。
- IDEA MCP确认当前工程、模块及3个核心修改文件无IDE错误；没有抢占键鼠或启动客户端。IDE运行接口的结果未确认，不计入测试通过声明。
- 最终JAR的10份关键变更class与当前编译字节一致，版本元数据正确，无GameTest类；旧 `.23` 哈希未变。
- 组合矩阵仍出现MekMM/MekEnergistics自有部分未注册工厂物品对应的资源解析日志；本轮没有修改第三方模组内容，不将“用例通过”视为整合包所有日志已清零。客户端能源页尚未实机目视验收。
- 原生日志：`build/reports/energy-integration/1.0.6.24/` 下的 `forbidden-server.log`、`absent-server.log`、`nature-server.log`、`energy-server.log`。

测试包：`build/libs/mekanism_magic-1.0.6.24-neoforge-1.21.1.jar`

- 大小：2,896,367字节。
- SHA-256：`099A62115998F71C6D150D37AB238BE257D4C8DD3171BFE2AF509337853FF249`。
- 客户端与服务端请使用同一版本；未发布。
