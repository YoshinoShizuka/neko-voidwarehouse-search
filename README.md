# Neko VoidWarehouse Search（虚空仓库搜索框）

纯客户端 Fabric mod：在「虚空仓库」的界面（一个 6 行原版大箱子）上叠加一个搜索框，支持**中文 / 拼音全拼 / 拼音首字母 / 注册名**搜索筛选物品。服务端 GUI 一个字节不变，纯原版玩家零感知。

![Side](https://img.shields.io/badge/side-%E7%BA%AF%E5%AE%A2%E6%88%B7%E7%AB%AF-informational) ![Minecraft](https://img.shields.io/badge/Minecraft-1.21.11%20%7C%2026.x-2ea043)

## 工作方式

```
打开仓库界面 ──search_hello──▶ 服务端确认是虚空仓库
             ◀─search_catalog─ 仓库物品 id 目录（≈30 KB）
输入 ─▶ 本地匹配（中文名 / 全拼 / 首字母 / 注册名，jpinyin 建索引）
     ──search_apply──▶ 服务端按 Item 身份过滤出页（空列表 = 恢复完整列表）
```

- 匹配在客户端做（只有客户端有中文语言文件），服务端只做集合求交；
- 过滤是**会话级**状态：关掉界面即消失，不写存档；页码回第一页；「可兑」虚拟条目同样参与搜索；
- 服务端限流：hello 100ms / apply 50ms，目录上限 4096 种，未知 id 静默忽略。

## 安装

mod id `neko_voidwarehouse_search`，构件名 `neko-voidwarehouse-search`。五个 Minecraft 版本各一个 jar：

| 客户端版本 | jar | 构建管线 |
| --- | --- | --- |
| 26.3 / 26.2 / 26.1.2 / 26.1.1 | `neko-voidwarehouse-search-mc<ver>-0.1.0.jar` | fabric-loom（非 remap，Java 25） |
| 1.21.11 | `neko-voidwarehouse-search-mc1.21.11-0.1.0.jar` | fabric-loom-remap + officialMojangMappings（Java 21） |

| 位置 | 需要什么 |
| --- | --- |
| 客户端 | 本 mod（按版本选 jar）+ Fabric API（需搭配服务端 `carpet-Neko-Addition` 1.0.16+） |
| 服务端 | `carpet-Neko-Addition` 1.0.16+（对应版本 jar；本 mod 不是它的硬依赖，没有它时搜索框自动隐藏） |
| 纯原版客户端 | 什么都不用装，看不到任何变化 |

## 使用

- 打开虚空仓库界面，点击界面上方的搜索框；
- 输入 `铁锭` / `tieding` / `td` / `iron_ingot` 均可命中；清空输入框 = 恢复完整列表；
- 🔴 聚焦搜索框时**所有按键都会被吞掉**（ESC = 退出搜索框，再按一次关界面）——否则数字键 `1-9` 会触发原版「把快捷栏整组存进仓库」的 SWAP 语义。

## 构建

- JDK 25、Gradle 9.7.0（wrapper 已固定）。多子工程结构：`common/`（纯逻辑 SearchIndex + 单测）+ `modern/`（26.x 四版本）+ `versions/1.21.11`：
  ```bat
  .\gradlew.bat build dist
  .\gradlew.bat :common:test          # SearchIndex 单测
  .\gradlew.bat :versions:1.21.11:dist
  ```
- 🔴 **逐版本构建**（与 carpet 同款铁律）：全量 `build` 在 26.x 与 1.21.11 混跑时可能撞 TinyRemapper；按子工程逐个跑最稳。
- 产物统一复制到工作区 `deliver/neko-voidwarehouse-search/`。
- 🔴 **与服务端 jar 成对更新**：`neko/voidwarehouse/search/` 三个 payload 类与 carpet-Neko-Addition 里同名类**必须逐字节一致**（同 FQCN 双份、Fabric knot 只加载一份），改一边务必同步改另一边。payload 源文件按 carpet 26.3 基准 md5 校验：hello `ee8150d8` / catalog `fc27b613` / apply `856c374a`（五版本 + 服务端六份逐字节一致）。
- 1.21.11 走 loom-remap 管线：mixin 注解名编译期硬 remap 成 intermediary（无 refmap 文件，与 carpet-Neko-Addition 1.21.11 同机制）；`dist` 任务必须依赖 `remapJar` 而不是 `jar`（jar 产出的是 dev jar）。

## 版本差异（仅两处）

| 差异点 | 26.x | 1.21.11 |
| --- | --- | --- |
| 网络注册 | `serverboundPlay()` / `clientboundPlay()` | `playC2S()` / `playS2C()` |
| 渲染注入 | `extractRenderState(GuiGraphicsExtractor,...)` TAIL 后手动 `box.extractRenderState(...)` | `render(GuiGraphics,int,int,float)` TAIL 后手动 `box.render(...)` |

两代容器屏都不渲染 renderables，搜索框都必须手动驱动。

## 版本兼容

| 服务端 \ 客户端 | 装了本 mod | 没装 / 老版本 |
| --- | --- | --- |
| carpet-Neko-Addition 1.0.16+（对应版本） | 搜索框可用 | 无任何影响 |
| 其它版本 / 没装 carpet | 搜索框自动隐藏（`canSend` 判定） | 无任何影响 |

## 许可证

MIT。拼音库 [jpinyin](https://github.com/stuxuhai/jpinyin)（Apache-2.0）以 Jar-in-Jar 打包。
