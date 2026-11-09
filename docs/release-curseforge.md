# CurseForge 发版流程（dokimobs）

把当前 `mod_version` 的三个 Stonecutter 节点产物发布到 CurseForge，并验证到公开可下载。
依赖项 `eyelib` 是独立项目（`../qylEyelib`），它的发版见该项目 `.opencode/skills/eyelib-release/SKILL.md`；本文档只覆盖 dokimobs 侧。

## 版本与坐标
- 版本唯一来源：`gradle.properties` 的 `mod_version`（当前 `0.3.13`）。
- 节点：`stonecutter.properties.toml` 的 `1.20.1`（legacy Forge）、`1.21.1`、`26.1.2`（NeoForge）。
- 产物名：`dokimobs-${mod_version}+${minecraft_version}-${forge|neoforge}.jar`。
- CurseForge 项目：ID `1234919`，slug `dokimobs`。
- 运行时依赖：`META-INF/mods.toml` / `META-INF/neoforge.mods.toml` 里 `eyelib` 的 `versionRange` 由 `gradle.properties` 的 `eyelib_version` 展开。**发布前必须确认同版本 eyelib 三节点已在该 MC 版本的 CF 页面可下载**，否则用户装上后缺前置。

## 凭据
- `%USERPROFILE%/.gradle/gradle.properties` 的 `CURSE_TOKEN`（36 字符，`https://authors.curseforge.com/#/settings/api-tokens` 生成的**作者 Upload API token**）。
- 该 token 只对 legacy Upload API 有效；带 `x-api-key` 打 `api.curseforge.com` 会 403。
- 有效性探测（非破坏）：`GET https://minecraft.curseforge.com/api/game/versions` + `X-Api-Token`，应返回 200 与 7000+ 条版本；同时可校验 `1.20.1` / `1.21.1` / `26.1.2` / `Forge` / `NeoForge` 这些 `gameVersionNames` 是否存在。

## 步骤 1：干净构建（强制）
```text
cmd.exe /c gradlew.bat clean assemble --no-daemon --console=plain --no-build-cache
```
- 必须加 `--no-build-cache`：否则 `compileJava FROM-CACHE` 只证明缓存键一致，不证明当前工作树源码被真实编译过。
- 三个节点的 `compileJava` 都要在输出里出现，末尾 `BUILD SUCCESSFUL`。
- Gradle 完成信号偶有异常，以输出里的 `BUILD SUCCESSFUL` 与任务清单为准，命令保留超时。

## 步骤 2：汇总并核对产物
```text
cmd.exe /c gradlew.bat collectArtifacts --no-daemon --console=plain
```
- 任务会把三个节点 jar（1.20.1 取 `reobfJar` 结果）与 mavenLocal 的 eyelib jar 平铺到 `artifacts/`，缺件直接失败。
- 核对方式：`sha256sum artifacts/dokimobs-<version>+*.jar` 与重新构建前的哈希比对——**一致才说明 `artifacts/` 与当前工作树同源**。
- 核对 jar 内元数据：`python -c` 打开 zip 读 `META-INF/*.mods.toml`，确认 `version`、`minecraft`/`neoforge`/`forge` 的 `versionRange` 与节点一致、`eyelib` 的 `versionRange` 等于 `[eyelib_version,)`。

## 步骤 3：上传（legacy Upload API）
- 端点：`POST https://www.curseforge.com/api/projects/1234919/upload-file`，头 `X-Api-Token: <CURSE_TOKEN>`，multipart 字段 `metadata`（JSON 字符串）与 `file`（jar）。
- 返回 `{"id": <fileId>}` 即上传成功；同一节点重复上传会产生重复文件，**不要用真实版本重试**，失败先看 HTTP 码。
- 2026-11-06 实测：Python/requests 直连 `upload-file` 被 Cloudflare 重置（`ConnectionResetError(10054)`），`GET /api/game/versions` 也可能 403；此时不要换版本重试，改用浏览器辅助上传（见本节末）。
- metadata 用 `gameVersionNames`（比 id 稳，某些 MC 版本有多个重复 id）：
  | 节点 | gameVersionNames |
  | --- | --- |
  | 1.20.1 | `["Client","Server","1.20.1","Forge"]` |
  | 1.21.1 | `["Client","Server","1.21.1","NeoForge"]` |
  | 26.1.2 | `["Client","Server","26.1.2","NeoForge"]` |
- 其余字段：`releaseType: "release"`、`changelogType: "markdown"`、`displayName` 用**带 `.jar` 的完整文件名**（与本项目历史文件展示一致，eyelib 用的是去扩展名）。
- 现成脚本：`work/cf_upload_0.3.5.py`（最新一版，requests 实现，`--dry-run` 只打印 metadata；传节点名可只发指定节点），改顶部 `VERSION` / `CHANGELOG` / `IDS_OUT` 即可复用到下一版。changelog 从 `work/cf-<version>-changelog.md` 读取。
- 上传结果（本次 0.2.0）：`1.20.1 → 9395289`、`1.21.1 → 9395290`、`26.1.2 → 9395292`。
- 上传结果（本次 0.2.1）：`1.20.1 → 9406394`、`1.21.1 → 9406396`、`26.1.2 → 9406399`；脚本同版复制为 `work/cf_upload_0.2.1.py`、`work/cf_wait_verify_0.2.1.py`（后者要求三个文件名全部出现才退出）。2026-11-02 实测：20:40 上传完成 → 20:53 `files/all` 三个文件全部可见，各文件页 `R Release`、`Size 9.2 MB`、Loader 与 game version 正确、changelog 已渲染。
- 上传结果（本次 0.3.0）：`1.20.1 → 9415180`、`1.21.1 → 9415182`、`26.1.2 → 9415184`；脚本复制为 `work/cf_upload_0.3.0.py`、`work/cf_wait_verify_0.3.0.py`。2026-11-03 实测：三个节点串行上传共 32 秒，全部 HTTP 200（`{"id": ...}`）；14:28 上传完成 → 14:29/14:33/14:37 轮询仍 `0/3` → 14:41 `files/all` 三个文件全部可见（约 13 分钟），各自 `Latest release`、`R Release`、`Size 9.2 MB`、Mod Loaders 与 game version 正确、changelog（含 `无序合成` 段）已渲染。前置依赖已实测：CF 的 `eyelib` 项目同时存在 `eyelib-21.1.24+1.20.1-forge` / `+1.21.1-neoforge` / `+26.1.2-neoforge`。
- 上传结果（本次 0.3.1）：`1.20.1 → 9430937`、`1.21.1 → 9430940`、`26.1.2 → 9430942`；脚本为 `work/cf_upload_0.3.1.py`、`work/cf_wait_verify_0.3.1.py`。2026-11-04 实测：13:46 上传完成 → 13:50/13:54 轮询仍 `0/3` → 13:59 `files/all` 三个文件全部可见（约 12 分钟），各自 `Latest release`、`R Release`、`Size 9.2 MB`、Mod Loaders 与 game version 正确、changelog 已渲染。前置依赖：CF 的 `eyelib 21.1.25` 三节点已于 13:32 上传、13:45 全部可见（`9430864` / `9430866` / `9430868`）。
- 上传结果（本次 0.3.2）：`1.20.1 → 9432169`、`1.21.1 → 9432171`、`26.1.2 → 9432172`；脚本为 `work/cf_upload_0.3.2.py`、`work/cf_wait_verify_0.3.2.py`。2026-11-04 实测：17:52 上传完成 → 17:56 / 18:01 轮询仍 `0/3` → 18:05 `files/all` 三个文件全部可见（约 13 分钟），各自 `Latest release`、`R Release`、`Size 9.2 MB`、Mod Loaders 与 game version 正确。前置依赖：CF 的 `eyelib 21.1.26` 三节点 17:32 上传、17:52 全部可见（`9432072` / `9432073` / `9432074`）。
- 上传结果（本次 0.3.3）：`1.20.1 → 9434950`、`1.21.1 → 9434955`、`26.1.2 → 9434959`；脚本为 `work/cf_upload_0.3.3.py`、`work/cf_wait_verify_0.3.3.py`、changelog `work/cf-0.3.3-changelog.md`。2026-11-04 23:35 上传完成。前置依赖：CF 的 `eyelib 21.1.27` 三节点 23:26 上传、23:35 已可见（`9434633` / `9434634` / `9434635`）。本次内容：26.1 实体材质改走 LIGHTMAP_ONLY（去掉逐面方向光造成的模型暗面闪烁）+ `pack.mcmeta` 改 `supported_formats`（26.1 不再整体拒绝资源包）。
- 上传结果（本次 0.3.4）：`1.20.1 → 9450199`、`1.21.1 → 9450201`、`26.1.2 → 9450203`；脚本为 `work/cf_upload_0.3.4.py`、`work/cf_wait_verify_0.3.4.py`、changelog `work/cf-0.3.4-changelog.md`。2026-11-05 上传即 19 秒完成（三节点串行），14:54 上传完成 → 15:06 `files/all` 三个文件全部可见（约 12 分钟），各自 `Latest release`、`R Release`、版本/loader 正确；26.1.2 文件页 `Size 9.2 MB`、`Mod Loaders NeoForge`、`Type Release`、changelog 已渲染（`+` 在 File name 里显示为 `-`，与历史一致），1.20.1 / 1.21.1 文件页 Size 9.20 / 9.21 MB。前置依赖：CF 的 `eyelib 21.1.28` 三节点 14:41 上传、14:53 已可见（`9449975` / `9450004` / `9450034`）。本次内容：26.1.2 开光影后鸡/猪/牛/哞菇不显示（`entity` 不透明材质被 Iris 匹配到「永不通过」的顶点 alpha 测试），由 eyelib 21.1.28 按 `ShaderKey` 精确登记修复。
- 本次 0.3.4 构建与产物校验：`clean assemble --no-build-cache` 三节点 `compileJava` 均执行、`BUILD SUCCESSFUL`；`collectArtifacts` 汇总 6 个 jar（dokimobs 三节点 + eyelib 21.1.28 三节点）；三个 dokimobs jar 与各自 `build/libs` 的 sha256 逐一相符；`mods.toml` / `neoforge.mods.toml` 的 `version = 0.3.4`、`eyelib versionRange = [21.1.28,)`、MC 范围 `[1.20.1,1.21)` / `[1.21.1,1.22)` / `[26.1.2,26.2)` 均正确。
- 上传结果（本次 0.3.5）：`1.20.1 → 9453959`、`1.21.1 → 9453960`、`26.1.2 → 9453961`；脚本为 `work/cf_upload_0.3.5.py`、`work/cf_wait_verify_0.3.5.py`、changelog `work/cf-0.3.5-changelog.md`。2026-11-05 21:55 上传完成（三节点串行，17 秒）→ 21:59 / 22:03 轮询仍 `0/3` → 22:07 `files/all` 三个文件全部可见（约 13 分钟），各自 `Latest release`、`R`、game version 与 loader 正确、Size 9.2 MB；26.1.2 文件页 9453961 显示 `File ID 9453961`、`Type R Release`、`Size 9.2 MB`、`Mod Loaders NeoForge`、`Supported game versions 26.1.2`，`What's new` 已渲染完整 changelog（`+` 在 File name 里仍显示为 `-`）。**前置依赖未变**：CF 的 `eyelib 21.1.28` 三节点（`9449975` / `9450004` / `9450034`）已于 14:53 可见；发版前与用户确认真源是「eyelib 自 21.1.28 起无源码提交、线上已是最新」，因此本次**不发新 eyelib 版本**（不为空改动递增版本号）。本次内容：萌化尘对可萌化生物抢占交互优先级。
- 本次 0.3.5 构建与产物校验：`clean assemble --no-build-cache` 三节点 `compileJava` 均执行（无 FROM-CACHE）、`BUILD SUCCESSFUL`；`collectArtifacts` 汇总 6 个 jar（dokimobs 三节点 + eyelib 21.1.28 三节点）；三个 dokimobs jar 与各自 `build/libs` 的 sha256 逐一相符（`259e598c…` / `6e0e7f4f…` / `70f144cb…`）；`mods.toml` / `neoforge.mods.toml` 的 `version = 0.3.5`、`eyelib versionRange = [21.1.28,)`、MC 范围 `[1.20.1,1.21)` / `[1.21.1,1.22)` / `[26.1.2,26.2)` 均正确；jar 内含 `io/github/tt432/dokimobs/item/MoeDustInteraction{,$Events}.class`（本次改动确实进包）。

- 上传结果（本次 0.3.6）：`1.20.1 → 9468684`、`1.21.1 → 9468689`、`26.1.2 → 9468693`；脚本 `work/cf_upload_0.3.6.py`、`work/cf_wait_verify_0.3.6.py`、changelog `work/cf-0.3.6-changelog.md`。三节点串行上传共 200 秒。**前置依赖**：CF 的 eyelib 21.1.29 三节点（`9468327` / `9468328` / `9468329`）已于 11:2x 上传并 `files/all` 可见。本次内容：依赖 eyelib 21.1.29（26.1 恢复 `query.facing_target_to_range_attack` / `query.is_avoiding_mobs`，修复萌化羊驼生气表情「怒气十字 + 生气眉 + ε 嘴」及回避类动画）。
- 本次 0.3.6 构建与产物校验：`clean assemble --no-build-cache`（首次失败：运行中的 1.21.1 客户端锁住 `versions/1.21.1/build/moddev/artifacts/neoforge-21.1.115.jar` → `createMinecraftArtifacts AccessDeniedException`；`mcmcp_close` 后重跑 `BUILD SUCCESSFUL`，1.20.1/26.1.2 的 `compileJava` 在 clean 后已真实执行）；`collectArtifacts` 汇总 6 个 jar（dokimobs 0.3.6 三节点 + eyelib 21.1.29 三节点）；三个 dokimobs jar 与各自 `build/libs` 的 sha256 逐一相符（`6a97ad5b…` / `24d7c58c…` / `751e4fb5…`）；`mods.toml` / `neoforge.mods.toml` 的 `version = 0.3.6`、`eyelib versionRange = [21.1.29,)`、MC 范围 `[1.20.1, 1.21)` / `[1.21.1, 1.22)` / `[26.1.2, 26.2)` 均正确。

- 上传结果（本次 0.3.7）：`1.20.1 → 9472929`、`1.21.1 → 9472947`、`26.1.2 → 9472962`；脚本 `work/cf_upload_0.3.7.py`、`work/cf_wait_verify_0.3.7.py`、changelog `work/cf-0.3.7-changelog.md`。三节点串行上传共 253 秒；19:56 上传完成 → 20:02 `files/all` 三个文件全部可见（约 10 分钟），各自 `Latest release`、R、game version 与 loader 正确；26.1.2 文件页 `9472962` 的 `What's new` 已渲染完整 changelog（含「透明材质不再遮挡水面」段）。**前置依赖**：CF 的 eyelib 21.1.30 三节点（`9472600` / `9472601` / `9472604`）19:31 上传、19:45 `files/all` 可见（约 14 分钟）。本次内容：依赖 eyelib 21.1.30（实体 pass 状态统一：透明 texel 不再写深度，幻翼/监守者透明片后面能正常看到水；多层半透明深度口径统一，消除近距离层序翻转造成的「z 冲突」闪烁；`<26.1` 补齐 entity_nocull 的背面剔除与 ADDITIVE 混合）。
- 本次 0.3.7 构建与产物校验：`clean assemble --no-daemon --no-build-cache` 三节点 `compileJava` 均执行（无 FROM-CACHE）、`BUILD SUCCESSFUL`；`collectArtifacts` 汇总 6 个 jar（dokimobs 0.3.7 三节点 + eyelib 21.1.30 三节点）；三个 dokimobs jar 与各自 `build/libs` 的 sha256 逐一相符（`7e4b11a7…` / `3fc5f6ad…` / `39460962…`）；`mods.toml` / `neoforge.mods.toml` 的 `version = 0.3.7`、`eyelib versionRange = [21.1.30,)`、MC 范围 `[1.20.1, 1.21)` / `[1.21.1, 1.22)` / `[26.1.2, 26.2)` 均正确。
- 上传结果（本次 0.3.8）：`1.20.1 → 9474406`、`1.21.1 → 9474408`、`26.1.2 → 9474410`；脚本 `work/cf_upload_0.3.8.py`、`work/cf_wait_verify_0.3.8.py`、changelog `work/cf-0.3.8-changelog.md`。2026-11-06 14:10Z 浏览器辅助上传完成（Python 直连被 Cloudflare 重置，见下）；14:15:59Z `files/all` 三个文件全部可见，各自 `Latest release`、`R`、game version 与 loader 正确；三个文件页均显示 `File ID`（9474406 / 9474408 / 9474410）、`Type R Release`、`Size 9.2 MB`、正确 Mod Loader 与 `Supported game versions`，`What's new` 已渲染完整 changelog。公开 API 的 `fileLength` 精确匹配本地 jar：9651527 / 9664582 / 9666195 B。**前置依赖**：CF 的 eyelib 21.1.31 三节点（`9474334` / `9474338` / `9474341`）于 14:03Z 浏览器辅助上传、14:09:35Z 可见（约 6 分钟），文件页 10.6 / 9.9 / 9.8 MB，Forge/NeoForge、game version 与 `What's new` 正确。
- 本次 0.3.8 构建与产物校验：`clean assemble --no-build-cache` 三节点 `compileJava` 均执行（无 FROM-CACHE）、`BUILD SUCCESSFUL`；`collectArtifacts` 汇总 6 个 jar（dokimobs 0.3.8 三节点 + eyelib 21.1.31 三节点）；三个 dokimobs jar 与 `work/cf-0.3.8-preclean.sha256` / `postclean.sha256` 逐一相符（`f6b0ee86…` / `17a0b0c6…` / `c0eb59bb…`）；`mods.toml` / `neoforge.mods.toml` 的 `version = 0.3.8`、`eyelib versionRange = [21.1.31,)`、MC 范围 `[1.20.1, 1.21)` / `[1.21.1, 1.22)` / `[26.1.2, 26.2)` 均正确；三个 eyelib jar 均含 `io/github/tt432/eyelib/client/render/CarriedBlockRenderer.class` 及新端口。
- 上传结果（本次 0.3.9）：`1.20.1 → 9474879`、`1.21.1 → 9474881`、`26.1.2 → 9474882`；脚本 `work/cf_upload_0.3.9.py`、`work/cf_wait_verify_0.3.9.py`、changelog `work/cf-0.3.9-changelog.md`。2026-11-06 14:52Z 浏览器辅助上传（本机静态服务 18777 → 页面内 fetch legacy Upload API，三节点均 HTTP 200）。**headless 受管浏览器卡在 Cloudflare `Just a moment`（15 分钟未过），换 `headed: true` 立即通过**——SOP 的"受管浏览器"必须带 `headed`，这是与 0.3.8 记录的补充。**前置依赖**：CF 的 eyelib 21.1.31 三节点（`9474334` / `9474338` / `9474341`）在 0.3.8 发版时已可见，本次不发新 eyelib。本次内容：蜜蜂背面看不到翅膀（翅膀骨骼改 `doki_bee_alphatest_nocull` 双面渲染）。
- 本次 0.3.9 构建与产物校验：`clean assemble --no-daemon --console=plain --no-build-cache` 三节点 `compileJava` 均执行（无 FROM-CACHE）、`BUILD SUCCESSFUL`；`collectArtifacts` 汇总 6 个 jar（dokimobs 0.3.9 三节点 + eyelib 21.1.31 三节点）；三个 dokimobs jar 与各自 `build/libs` 的 sha256 逐一相符（`4126f176…` / `4e6abb74…` / `3b2807cf…`）；`mods.toml` / `neoforge.mods.toml` 的 `version = 0.3.9`、`eyelib versionRange = [21.1.31,)`、MC 范围 `[1.20.1, 1.21)` / `[1.21.1, 1.22)` / `[26.1.2, 26.2)` 均正确；jar 内 `pack.cospack` 含 `"rightwing*"` 材质槽、`doki.material` 含 `doki_bee_alphatest_nocull`（本次改动确实进包）。公开验收：15:05:13Z `files/all` 三个文件名全部出现且各自标 `Latest release`（轮询脚本退出码 0，快照 `work/cf-0.3.9-files-page.txt`，约 13 分钟）；三个文件页 `9474879` / `9474881` / `9474882` 的 `File ID`、`Type R Release`、`Size 9.2 MB`、`Mod Loaders`（Forge / NeoForge / NeoForge）、`Supported game versions`（1.20.1 / 1.21.1 / 26.1.2）与 `What's new`（完整中文 changelog）均正确。本轮**未能**用公开 API 做 `fileLength` 精确核对：CF 对非浏览器请求该 API 返回 `Just a moment`（r.jina.ai 与页面内 fetch 均被拦），只核到页面 Size 量级。
- 上传结果（本次 0.3.10）：`1.20.1 → 9486779`、`1.21.1 → 9486781`、`26.1.2 → 9486783`；脚本 `work/cf_upload_0.3.10.py`、`work/cf_wait_verify_0.3.10.py`、changelog `work/cf-0.3.10-changelog.md`。2026-11-07 Python/requests 直连 legacy Upload API 三节点串行成功（先 `--dry-run`，实际共约 9 秒，未被 Cloudflare 拦）。**前置依赖**：CF 的 eyelib 21.1.31 三节点（`9474334` / `9474338` / `9474341`）在 0.3.8/0.3.9 发版时已可见，本次不发新 eyelib。本次内容：羊染色（16 色 + `jeb_` 彩虹，羊毛独立 RenderController）与狼坐姿修复（跪坐，`q.is_riding` 时抬高 6px 适配载具）。
- 本次 0.3.10 构建与产物校验：`clean assemble --no-daemon --console=plain --no-build-cache` 三节点 `compileJava` 均执行（无 FROM-CACHE）、`BUILD SUCCESSFUL`；`collectArtifacts` 汇总 6 个 jar（dokimobs 0.3.10 三节点 + eyelib 21.1.31 三节点）；三个 dokimobs jar 与各自 `build/libs` 的 sha256 逐一相符（`ca0217a1…` / `2455d5a1…` / `f2dab827…`，大小 9652961 / 9666044 / 9667699 B）；`mods.toml` / `neoforge.mods.toml` 的 `version = 0.3.10`、`eyelib versionRange = [21.1.31,)`、MC 范围 `[1.20.1, 1.21)` / `[1.21.1, 1.22)` / `[26.1.2, 26.2)` 均正确；三个 jar 内 `pack.cospack` 含 `controller.render.mdsj_sheep_wool` 与 `query.doki_sheep_wool_r`，`wolf.bbmodel` 的 `wolf.坐下` 含 `q.is_riding ? -3 : -9` 与小腿 `160`。
- 公开验收（0.3.10）：2026-11-07 13:23:28 `files/all` 三个文件名全部出现（轮询 12m39s，快照 `work/cf-0.3.10-files-page.txt`），各自 `Latest release`、`R Release`、Size `9.2 MB`、Forge/NeoForge 与 game version `1.20.1` / `1.21.1` / `26.1.2` 正确；三个文件页 `9486779` / `9486781` / `9486783` 显示对应 File ID、Size、Loader、Supported game versions，`What's new` 已渲染完整 changelog（含「前置：需要 eyelib 21.1.31 或更高版本」）。
- 上传结果（本次 0.3.11）：`1.20.1 → 9495482`、`1.21.1 → 9495484`、`26.1.2 → 9495485`；脚本 `work/cf_upload_0.3.11.py`、`work/cf_wait_verify_0.3.11.py`、changelog `work/cf-0.3.11-changelog.md`、ID 快照 `work/cf-0.3.11-ids.json`。2026-11-08 00:47 CST Python/requests 直连 legacy Upload API 三节点串行成功（先 `--dry-run`）。**前置依赖**：本次联动发布 eyelib 21.1.32，其 CF 三节点 `9495289` / `9495291` / `9495304` 于 2026-11-08 00:46:55 CST 全部可见（见 eyelib skill 发布记录）。本次内容：守卫者眨眼骨骼名（`EyeBalls`/`Eyebrows` → `eyelid`/`eyebrows`，同步 15 条动画 30 个 animator 引用）、26.1 受伤红色 overlay、萌化生物栓绳按 Bedrock `lead` locator 绘制（1.20.1 抑制原版双绳）、行商羊驼恢复 Bedrock 官方 masked multitexture 机制并删除临时自定义材质。
- 本次 0.3.11 构建与产物校验：`clean assemble --no-daemon --console=plain --no-build-cache` 三节点 `compileJava` 均执行（无 FROM-CACHE）、`BUILD SUCCESSFUL`（日志 `work/release_0311_build.log`）；`collectArtifacts` 汇总 6 个 jar（dokimobs 0.3.11 三节点 + eyelib 21.1.32 三节点）；三个 dokimobs jar 与各自 `build/libs` 的 sha256 逐一相符（`a8393d86…` / `40ccd15a…` / `156670ab…`，大小 9652968 / 9666050 / 9667706 B）；`mods.toml` / `neoforge.mods.toml` 的 `version = 0.3.11`、`eyelib versionRange = [21.1.32,)`、MC 范围 `[1.20.1, 1.21)` / `[1.21.1, 1.22)` / `[26.1.2, 26.2)` 均正确；jar 内 `guardian.bbmodel` 含 `eyelid` 且不再含 `EyeBalls`、`doki.material` 不再含 `doki_llama_multitexture`、`pack.cospack` 两处 `"default":"llama"` 且含 `trader_llama_decor`（本次四处修复确实进包）。
- 公开验收（0.3.11）：2026-11-08 00:55:34 CST `files/all` 三个文件名全部出现且各自标 `Latest release`（轮询 8m12s，快照 `work/cf-0.3.11-files-page.txt`），`R Release`、Size `9.2 MB`、Forge/NeoForge 与 game version `1.20.1` / `1.21.1` / `26.1.2` 正确（CF 页面显示日期 `Nov 7, 2026`，为 UTC 日期）。站点 API（`/api/v1/mods/1234919/files`）返回的 `fileLength` 与本地 jar 精确一致：9652968 / 9666050 / 9667706 B，`releaseType = 1`；文件页 `9495482` 显示 `File ID 9495482`、`Size 9.2 MB`、`Supported game versions` 与完整 `What's new`（快照 `work/cf-0.3.11-file-9495482.txt`，含「前置：需要 eyelib 21.1.32 或更高版本」）。
- **项目描述**：CurseForge 公开 API 只有 `GET /v1/mods/{modId}/description`（读），没有写入端点；作者 Upload API 也没有描述字段 ⇒ 描述只能由作者在网页后台编辑。描述正文的仓库副本是 `docs/curseforge-description.md`（当前实体表 27 个模型），改动后从该文件整段复制到 CurseForge 后台即可。
- 上传结果（本次 0.3.12）：`1.20.1 → 9506313`、`1.21.1 → 9506315`、`26.1.2 → 9506318`；脚本 `work/cf_upload_0.3.12.py`、`work/cf_wait_verify_0.3.12.py`、changelog `work/cf-0.3.12-changelog.md`、ID 快照 `work/cf-0.3.12-ids.json`。2026-11-08 15:32:36 CST Python/requests 直连 legacy Upload API 三节点串行成功（先 `--dry-run`，共 19 s）。**前置依赖**：本次联动发布 eyelib 21.1.33，其 CF 三节点 `9506218` / `9506220` / `9506221` 于 15:32:11 CST 全部可见（见 eyelib skill 发布记录）。本次内容：装备与盔甲挂载（依赖 eyelib 21.1.33）——手持物按 item 骨骼 `rightitem` / `leftitem` pivot 锚定并随手臂动画同步、盔甲四件按 body-part 骨骼渲染、26.1 手持物显示修复且不再按渲染组件重复绘制。
- 本次 0.3.12 构建与产物校验：`clean assemble --no-daemon --console=plain --no-build-cache`（52 s，32 actionable tasks 全部 executed，三节点 `compileJava` 均真实执行、无 `FROM-CACHE`，`BUILD SUCCESSFUL`；日志 `work/release_0312_build.log`）；`collectArtifacts` 汇总 6 个 jar（dokimobs 0.3.12 三节点 + eyelib 21.1.33 三节点）；三个 dokimobs jar 与各自 `build/libs` 的 sha256 逐一相符（`b434c50a…` / `b449801d…` / `5bc4c973…`，大小 9652968 / 9666051 / 9667706 B）；`mods.toml` / `neoforge.mods.toml` 的 `version = 0.3.12`、`eyelib versionRange = [21.1.33,)`、MC 范围 `[1.20.1, 1.21)` / `[1.21.1, 1.22)` / `[26.1.2, 26.2)` 均正确。本次三维 jar 与 0.3.11 字节数几乎相同（1.20.1 / 26.1.2 完全相等、1.21.1 多 1 B）符合预期：dokimobs 侧只有版本串变化，行为变化全部来自 eyelib 21.1.33。
- 公开验收（0.3.12）：2026-11-08 15:53:30 CST `files/all` 三个文件名全部出现且各自标 `Latest release`（轮询约 21 分钟；中途 15:45 一轮遇 `r.jina.ai` SSLError，视为失败轮次继续等；快照 `work/cf-0.3.12-files-page.txt`），`R`、Forge/NeoForge 与 game version `1.20.1` / `1.21.1` / `26.1.2` 正确；文件页 `9506318` 显示 `File ID 9506318`、`Size 9.2 MB`、`Mod Loaders NeoForge`、`Supported game versions` `26.1.2`，`What's new` 已渲染完整 changelog（含「前置：需要 eyelib 21.1.33 或更高版本」，快照 `work/cf-0.3.12-file-9506318.txt`）。

### 2026-11-06：Cloudflare 拦截 → 浏览器辅助上传
- 背景：`POST /api/projects/{id}/upload-file` 从 Python/requests 直连（含代理、UA、浏览器 cookie）返回 403 / `ConnectionResetError(10054)`；CF 页面本身可通过浏览器访问，且浏览器内 `fetch` 携带 `X-Api-Token` 能完成上传。
- 做法：
  1. 本地临时起只读静态服务（`127.0.0.1:18777`），服务 `artifacts/`，响应带 `Access-Control-Allow-Origin: *`，并从 `%USERPROFILE%/.gradle/gradle.properties` 的 `CURSE_TOKEN` 提供 `/token`；用完即删，不要长期运行。
  2. 用受管浏览器打开 `https://www.curseforge.com/minecraft/mc-mods/<slug>/files/all`，确认已通过 Cloudflare（页面不是 `Just a moment`）。
  3. 在该页面上下文执行 `page.evaluate`：先从本地服务取 jar → `blob()`，再组 `FormData`（`metadata` JSON 字符串 + `file`）POST 到 legacy Upload API，头 `X-Api-Token`。token 由 eval 传入或从本地 `/token` 取，**不要打印到日志**。
  4. 返回 `{"id":...}` 后**不要重传**；用页面内 `fetch` 调公开 API（URL 加唯一 `cb` 绕缓存）轮询 `id` / `status` / `fileLength`，或用 `r.jina.ai` 读 `files/all`。
- 最小页面代码：
  ```js
  const jr = await fetch("http://127.0.0.1:18777/" + encodeURIComponent(fileName));
  const fd = new FormData();
  fd.append("metadata", JSON.stringify(metadata));
  fd.append("file", await jr.blob(), fileName);
  const resp = await fetch(`https://www.curseforge.com/api/projects/${projectId}/upload-file`, {
    method: "POST", headers: {"X-Api-Token": token}, body: fd
  });
  // resp.json() -> {"id": ...}
  ```
- 2026-11-06 实测时间线：eyelib 21.1.31 14:03Z 上传 → 14:09:35Z 公开；dokimobs 0.3.8 14:10Z 上传 → 14:15:59Z 公开。公开 API `status=4`、`fileLength` 与本地 jar 字节数完全一致；公开前公共 API 查不到该 ID 是正常的，**不要据此判断上传失败**。
- 清理：用完关闭本地服务/浏览器 tab，删除临时静态服务脚本与 `work/cf_browser_session.json`（含 cookie）等临时载体。

## 步骤 4：验收
- `https://r.jina.ai/https://www.curseforge.com/minecraft/mc-mods/dokimobs/files/all?page=1&pageSize=20&sortBy=dateCreated&sortOrder=desc&cb=<唯一串>`：出现新文件名、标 `Latest release`、Game Versions 与 Mod Loaders 正确、Size 与本地 jar 一致。
- 文件详情页 `.../files/<fileId>`：确认 `File ID`、`Type R Release`、`Size`、`Mod Loaders`、`Supported game versions` 与预期节点一致，且 `What's new` 段渲染出 markdown changelog。
- 页面内 `fetch` 公开 API：`GET /api/v1/mods/{projectId}/files?pageIndex=0&pageSize=50&sort=dateCreated&sortOrder=desc&cb=<唯一串>`；`status=4` 且 `fileLength` 与本地 jar 字节数一致可作精确核对。`displayName` 保留 `+`，`fileName` 把 `+` 显示为 `-`。
- 上传接口返回 200 只证明「已受理」，不证明已公开——**以 `files/all` 为准**。
- 2026-11-01 实测时间线（0.2.0）：22:11 上传完成 → 22:31 `files/all` 出现第 1 个文件（1.21.1）→ 22:44 三个文件全部出现且各自标 `Latest release`；详情页在 22:50 左右可读。即「分钟级到约半小时」，不必重传。
- Size 只到 0.1 MB 精度（本地 9636031 B 显示 `9.2 MB`），可作量级核对；**无法用哈希核对已上传字节**：`https://mediafilez.forgecdn.net/files/<id/1000>/<id%1000>/<文件名>` 与 `.../download/<fileId>` 无签名直连一律 403。
- 轮询脚本：`work/cf_wait_verify_0.3.1.py`（每 4 分钟取一次 `files/all`，**三个文件名全部出现**才保存页面到 `work/cf-<version>-files-page.txt` 并退出；0.2.0 版的 `cf_wait_verify.py` 只要求出现任意一个，容易误判为已全部公开）；遇到 `Just a moment` 视为一次失败轮次，继续等。

## Common Pitfalls
- [when 用 `x-api-key` + `api.curseforge.com` 上传] 403；作者 token 只认 legacy Upload API。
- [when 在 `www.curseforge.com` 取 `/api/game/versions`] 返回的是别的游戏版本；必须用 `minecraft.curseforge.com/api/game/versions`。
- [when 直接 curl / 无头浏览器读 CF 页面] 被 Cloudflare 拦成 403 或 `Just a moment`；一律用 `https://r.jina.ai/<url>` 读取，URL 上加唯一 query 参数绕缓存。
- [when 2026-11-06 起 Python/requests 上传返回 403 / `ConnectionResetError(10054)`] Cloudflare 在拦非浏览器客户端；不要换版本重试，改用浏览器辅助上传（见步骤 3 末）。
- [when 上传返回 `{"id":...}` 但 `files/all` 暂时没有] 文件已受理但尚未公开；等待约 6–15 分钟并轮询，重复上传会产生重复文件。2026-11-06 实测 eyelib 21.1.31 约 6 分钟、dokimobs 0.3.8 约 6 分钟公开。
- [when 只跑 `clean assemble` 就宣称「当前源码可构建」] Gradle build cache 会命中 `compileJava FROM-CACHE`；必须 `--no-build-cache`，并用 jar 哈希与 `artifacts/` 比对来证明产物与工作树同源（对应 2026-10-28 交付过度的反馈）。
- [when 想删 CF 上的错误文件] 无可用删除 API；只能作者在网页文件页手动删除。
- [when 想用第三方镜像 `https://api.cfwidget.com/minecraft/mc-mods/dokimobs` 验收] 它返回 JSON 且不被 Cloudflare 拦，但索引明显滞后（0.2.0 已公开 40 分钟后仍只列 11 个旧文件），只能作旁证，不能当验收依据。
- [when 26.1.2 节点报 CF 不认识该游戏版本] 先用 `minecraft.curseforge.com/api/game/versions` 确认名字存在（2026-11-01 实测存在），不要退回旧版托管字段。
- [when 发现详情页标题是 `dokimobs-0.2.0+1.20.1-forge.jar` 而 `File name` 字段显示 `dokimobs-0.2.0-1.20.1-forge.jar`] 本次实测两者不一致：`displayName` 保留 `+`，CF 的 file-name 字段把 `+` 显示成 `-`。列表页与标题都取 `displayName`，因此不影响识别；若要绝对确定，下载后看实际文件名。
- 上传结果（本次 0.3.13）：`1.20.1 → 9526781`、`1.21.1 → 9526794`、`26.1.2 → 9526795`；脚本 `work/cf_upload_0.3.13.py`、`work/cf_wait_verify_0.3.13.py`、changelog `work/cf-0.3.13-changelog.md`、ID 快照 `work/cf-0.3.13-ids.json`。2026-11-09 21:49:07 CST Python/requests 直连 legacy Upload API 三节点串行成功（先 `--dry-run`，共约 170 s）。**前置依赖**：本次联动发布 eyelib 21.1.34，其 CF 三节点 `9526576` / `9526579` / `9526580` 于 21:46:12 CST 全部可见（见 eyelib skill 发布记录）。本次内容：掠夺者不祥旗帜、头部独立转动、弩按主手显隐、skeleton 等 body 盔甲颈部锚点、CarryOn off-level 实体接管、26.1 charged creeper 闪电层。
- 本次 0.3.13 构建与产物校验：`clean assemble --no-daemon --console=plain --no-build-cache`（52 s，32 actionable tasks 全部 executed，三节点 `compileJava` 均真实执行、无 `FROM-CACHE`，`BUILD SUCCESSFUL`）；`collectArtifacts` 汇总 6 个 jar（dokimobs 0.3.13 三节点 + eyelib 21.1.34 三节点）；三个 dokimobs jar 与各自 `build/libs` 的 sha256 逐一相符（`e079aa0f…` / `9dd2fe3f…` / `4fa2bcd7…`，大小 9653935 / 9666964 / 9668621 B）；`mods.toml` / `neoforge.mods.toml` 的 `version = 0.3.13`、`eyelib versionRange = [21.1.34,)`、MC 范围 `[1.20.1, 1.21)` / `[1.21.1, 1.22)` / `[26.1.2, 26.2)` 均正确；三个 jar 均含 `assets/dokimobs/animations/doki_pillager.json`。
- 公开验收（0.3.13）：2026-11-09 22:05 CST 公开 API `files` 三节点全部可见，`fileLength` 精确一致（9653935 / 9666964 / 9668621），`releaseType = 1`；文件页 `9526795` 的 `What's new` 已渲染完整 changelog（含「掠夺者不祥旗帜」「前置：需要 eyelib 21.1.34 或更高版本」，快照 `work/cf-0.3.13-file-9526795.txt`）。
