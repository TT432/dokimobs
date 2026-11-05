# CurseForge 发版流程（dokimobs）

把当前 `mod_version` 的三个 Stonecutter 节点产物发布到 CurseForge，并验证到公开可下载。
依赖项 `eyelib` 是独立项目（`../qylEyelib`），它的发版见该项目 `.opencode/skills/eyelib-release/SKILL.md`；本文档只覆盖 dokimobs 侧。

## 版本与坐标
- 版本唯一来源：`gradle.properties` 的 `mod_version`（当前 `0.3.4`）。
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
- metadata 用 `gameVersionNames`（比 id 稳，某些 MC 版本有多个重复 id）：
  | 节点 | gameVersionNames |
  | --- | --- |
  | 1.20.1 | `["Client","Server","1.20.1","Forge"]` |
  | 1.21.1 | `["Client","Server","1.21.1","NeoForge"]` |
  | 26.1.2 | `["Client","Server","26.1.2","NeoForge"]` |
- 其余字段：`releaseType: "release"`、`changelogType: "markdown"`、`displayName` 用**带 `.jar` 的完整文件名**（与本项目历史文件展示一致，eyelib 用的是去扩展名）。
- 现成脚本：`work/cf_upload_0.3.2.py`（最新一版，requests 实现，`--dry-run` 只打印 metadata；传节点名可只发指定节点），改顶部 `VERSION` / `CHANGELOG` / `IDS_OUT` 即可复用到下一版。changelog 从 `work/cf-<version>-changelog.md` 读取。
- 上传结果（本次 0.2.0）：`1.20.1 → 9395289`、`1.21.1 → 9395290`、`26.1.2 → 9395292`。
- 上传结果（本次 0.2.1）：`1.20.1 → 9406394`、`1.21.1 → 9406396`、`26.1.2 → 9406399`；脚本同版复制为 `work/cf_upload_0.2.1.py`、`work/cf_wait_verify_0.2.1.py`（后者要求三个文件名全部出现才退出）。2026-11-02 实测：20:40 上传完成 → 20:53 `files/all` 三个文件全部可见，各文件页 `R Release`、`Size 9.2 MB`、Loader 与 game version 正确、changelog 已渲染。
- 上传结果（本次 0.3.0）：`1.20.1 → 9415180`、`1.21.1 → 9415182`、`26.1.2 → 9415184`；脚本复制为 `work/cf_upload_0.3.0.py`、`work/cf_wait_verify_0.3.0.py`。2026-11-03 实测：三个节点串行上传共 32 秒，全部 HTTP 200（`{"id": ...}`）；14:28 上传完成 → 14:29/14:33/14:37 轮询仍 `0/3` → 14:41 `files/all` 三个文件全部可见（约 13 分钟），各自 `Latest release`、`R Release`、`Size 9.2 MB`、Mod Loaders 与 game version 正确、changelog（含 `无序合成` 段）已渲染。前置依赖已实测：CF 的 `eyelib` 项目同时存在 `eyelib-21.1.24+1.20.1-forge` / `+1.21.1-neoforge` / `+26.1.2-neoforge`。
- 上传结果（本次 0.3.1）：`1.20.1 → 9430937`、`1.21.1 → 9430940`、`26.1.2 → 9430942`；脚本为 `work/cf_upload_0.3.1.py`、`work/cf_wait_verify_0.3.1.py`。2026-11-04 实测：13:46 上传完成 → 13:50/13:54 轮询仍 `0/3` → 13:59 `files/all` 三个文件全部可见（约 12 分钟），各自 `Latest release`、`R Release`、`Size 9.2 MB`、Mod Loaders 与 game version 正确、changelog 已渲染。前置依赖：CF 的 `eyelib 21.1.25` 三节点已于 13:32 上传、13:45 全部可见（`9430864` / `9430866` / `9430868`）。
- 上传结果（本次 0.3.2）：`1.20.1 → 9432169`、`1.21.1 → 9432171`、`26.1.2 → 9432172`；脚本为 `work/cf_upload_0.3.2.py`、`work/cf_wait_verify_0.3.2.py`。2026-11-04 实测：17:52 上传完成 → 17:56 / 18:01 轮询仍 `0/3` → 18:05 `files/all` 三个文件全部可见（约 13 分钟），各自 `Latest release`、`R Release`、`Size 9.2 MB`、Mod Loaders 与 game version 正确。前置依赖：CF 的 `eyelib 21.1.26` 三节点 17:32 上传、17:52 全部可见（`9432072` / `9432073` / `9432074`）。
- 上传结果（本次 0.3.3）：`1.20.1 → 9434950`、`1.21.1 → 9434955`、`26.1.2 → 9434959`；脚本为 `work/cf_upload_0.3.3.py`、`work/cf_wait_verify_0.3.3.py`、changelog `work/cf-0.3.3-changelog.md`。2026-11-04 23:35 上传完成。前置依赖：CF 的 `eyelib 21.1.27` 三节点 23:26 上传、23:35 已可见（`9434633` / `9434634` / `9434635`）。本次内容：26.1 实体材质改走 LIGHTMAP_ONLY（去掉逐面方向光造成的模型暗面闪烁）+ `pack.mcmeta` 改 `supported_formats`（26.1 不再整体拒绝资源包）。
- 上传结果（本次 0.3.4）：`1.20.1 → 9450199`、`1.21.1 → 9450201`、`26.1.2 → 9450203`；脚本为 `work/cf_upload_0.3.4.py`、`work/cf_wait_verify_0.3.4.py`、changelog `work/cf-0.3.4-changelog.md`。2026-11-05 上传即 19 秒完成（三节点串行），14:54 上传完成 → 15:06 `files/all` 三个文件全部可见（约 12 分钟），各自 `Latest release`、`R Release`、版本/loader 正确；26.1.2 文件页 `Size 9.2 MB`、`Mod Loaders NeoForge`、`Type Release`、changelog 已渲染（`+` 在 File name 里显示为 `-`，与历史一致），1.20.1 / 1.21.1 文件页 Size 9.20 / 9.21 MB。前置依赖：CF 的 `eyelib 21.1.28` 三节点 14:41 上传、14:53 已可见（`9449975` / `9450004` / `9450034`）。本次内容：26.1.2 开光影后鸡/猪/牛/哞菇不显示（`entity` 不透明材质被 Iris 匹配到「永不通过」的顶点 alpha 测试），由 eyelib 21.1.28 按 `ShaderKey` 精确登记修复。
- 本次 0.3.4 构建与产物校验：`clean assemble --no-build-cache` 三节点 `compileJava` 均执行、`BUILD SUCCESSFUL`；`collectArtifacts` 汇总 6 个 jar（dokimobs 三节点 + eyelib 21.1.28 三节点）；三个 dokimobs jar 与各自 `build/libs` 的 sha256 逐一相符；`mods.toml` / `neoforge.mods.toml` 的 `version = 0.3.4`、`eyelib versionRange = [21.1.28,)`、MC 范围 `[1.20.1,1.21)` / `[1.21.1,1.22)` / `[26.1.2,26.2)` 均正确。

## 步骤 4：验收
- `https://r.jina.ai/https://www.curseforge.com/minecraft/mc-mods/dokimobs/files/all?page=1&pageSize=20&sortBy=dateCreated&sortOrder=desc&cb=<唯一串>`：出现新文件名、标 `Latest release`、Game Versions 与 Mod Loaders 正确、Size 与本地 jar 一致。
- 文件详情页 `.../files/<fileId>`：确认 `File ID`、`Type R Release`、`Size`、`Mod Loaders`、`Supported game versions` 与预期节点一致，且 `What's new` 段渲染出 markdown changelog。
- 上传接口返回 200 只证明「已受理」，不证明已公开——**以 `files/all` 为准**。
- 2026-11-01 实测时间线（0.2.0）：22:11 上传完成 → 22:31 `files/all` 出现第 1 个文件（1.21.1）→ 22:44 三个文件全部出现且各自标 `Latest release`；详情页在 22:50 左右可读。即「分钟级到约半小时」，不必重传。
- Size 只到 0.1 MB 精度（本地 9636031 B 显示 `9.2 MB`），可作量级核对；**无法用哈希核对已上传字节**：`https://mediafilez.forgecdn.net/files/<id/1000>/<id%1000>/<文件名>` 与 `.../download/<fileId>` 无签名直连一律 403。
- 轮询脚本：`work/cf_wait_verify_0.3.1.py`（每 4 分钟取一次 `files/all`，**三个文件名全部出现**才保存页面到 `work/cf-<version>-files-page.txt` 并退出；0.2.0 版的 `cf_wait_verify.py` 只要求出现任意一个，容易误判为已全部公开）；遇到 `Just a moment` 视为一次失败轮次，继续等。

## Common Pitfalls
- [when 用 `x-api-key` + `api.curseforge.com` 上传] 403；作者 token 只认 legacy Upload API。
- [when 在 `www.curseforge.com` 取 `/api/game/versions`] 返回的是别的游戏版本；必须用 `minecraft.curseforge.com/api/game/versions`。
- [when 直接 curl / 无头浏览器读 CF 页面] 被 Cloudflare 拦成 403 或 `Just a moment`；一律用 `https://r.jina.ai/<url>` 读取，URL 上加唯一 query 参数绕缓存。
- [when 只跑 `clean assemble` 就宣称「当前源码可构建」] Gradle build cache 会命中 `compileJava FROM-CACHE`；必须 `--no-build-cache`，并用 jar 哈希与 `artifacts/` 比对来证明产物与工作树同源（对应 2026-10-28 交付过度的反馈）。
- [when 想删 CF 上的错误文件] 无可用删除 API；只能作者在网页文件页手动删除。
- [when 想用第三方镜像 `https://api.cfwidget.com/minecraft/mc-mods/dokimobs` 验收] 它返回 JSON 且不被 Cloudflare 拦，但索引明显滞后（0.2.0 已公开 40 分钟后仍只列 11 个旧文件），只能作旁证，不能当验收依据。
- [when 26.1.2 节点报 CF 不认识该游戏版本] 先用 `minecraft.curseforge.com/api/game/versions` 确认名字存在（2026-11-01 实测存在），不要退回旧版托管字段。
- [when 发现详情页标题是 `dokimobs-0.2.0+1.20.1-forge.jar` 而 `File name` 字段显示 `dokimobs-0.2.0-1.20.1-forge.jar`] 本次实测两者不一致：`displayName` 保留 `+`，CF 的 file-name 字段把 `+` 显示成 `-`。列表页与标题都取 `displayName`，因此不影响识别；若要绝对确定，下载后看实际文件名。
