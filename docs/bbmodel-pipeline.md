# bbmodel 直载管线（2026-10-03 起）

## 用户决策（需求层，当场落盘）

1. **守卫者整体替换**：新"鱼形"模型替换旧娘化守卫者（含贴图），不留旧模型。
2. **动画控制器沿用旧版**：不引入 rete 解析、不写简化控制器；复用旧 mdsj 控制器体系。
   落地机制（实现层）：旧控制器状态机引用的是**短名**，经实体 json 的 `animations` 表解析到全 id，
   因此旧控制器可原样复用；独占控制器（`controller.animation.mdsj_wanderer.move`、
   `controller.animation.mdsj_villager.ride`）迁移为资源文件，共享控制器仍由保留的 cospack 条目提供。
3. **仅迁移 5 个实体**：流浪商人/狼/守卫者/掠夺者/烈焰人走 bbmodel 直载；
  其余 22 个实体继续走 cospack 导出管线；pack.cospack 删除 wandering_trader/guardian 旧条目。

## 架构

bbmodel 文件即美术资产单一来源，不再走"游戏内导入 → 导出 cospack JSON"：

```
assets/dokimobs/bbmodels/<entity>.bbmodel
  ├─ 几何+内嵌贴图 → DokiBbModelLoader（本 mod @ResourceLoader）
  │     ModelImporter.importBlockbench → ModelManager（键 = bbmodel model_identifier = geometry.doki_*）
  │     图集 → NativeImageIO.uploadFromImportedImageData → dokimobs:textures/entity/bbmodel/<entity>.png
  └─ 内嵌动画 → eyelib BbModelAnimationLoader（id = dokimobs.<动画名>，低优先级可被 json 覆盖）
assets/dokimobs/eyelib/entity/<entity>.json            BrClientEntity（短名→动画/几何/贴图映射在此）
assets/dokimobs/eyelib/render_controllers/doki.json    5 个 controller.render.doki_*
assets/dokimobs/eyelib/animation_controllers/doki.json 迁移+新建控制器
```

## 关键约束（实机验证过的坑）

- **动画名冲突**：eyelib 注册 id = `命名空间.动画名`（不含文件路径），5 个 bbmodel 的中文动画名
  （待机/走路…）互相重复。入库时必须给 bbmodel 内动画名加实体前缀（`wolf.待机`），复制脚本同时
  补 `model_identifier = geometry.doki_<entity>`（bbmodel 原值为空或乱填如"羊驼"）。
- **bbmodel 无控制器数据**：`rete_animation_controller_layout` 字段 eyelib 不解析，且本批 5 个文件
  该字段全为空。控制器只能手写 json（BrAnimationControllerSet 格式，initial_state 为字符串）。
- **bbmodel 动画坐标约定（2026-10-06 以真 Blockbench 实测最终校准，取代 21.1.16 结论）**：
  bbmodel 内嵌动画值与 bedrock .animation.json 同为**剪辑空间**——Blockbench 显示 bedrock 动画时
  旋转取反 x/y、位置取反 x（实测：待机躯体 UpBody 显示 (-11.17,-48.92,2.56)°=flip_xy(文件值)）。
  关键帧**叠加**在 rest 上而非替换（实测：bone24 显示=rest(37.4,71,39.1)+flip(kf)）。
  正确链路 = 注册表存剪辑空间原值（转换期不取反）+ 渲染期 flipAnimation 翻转一次 == Blockbench 显示。
  21.1.16~21.1.18 的双重翻转（转换期+渲染期各翻一次，净恒等）使实机姿态为 Blockbench 镜像，
  表现为弩扭飞、手臂 15° 滚转、腿/躯干反向扭转；eyelib 21.1.19 修复（删 BbModelAnimations 取反）。
  历史误判链：9754bbea 单翻转 →"三叉戟换手"误判 →486b68c0 转换期取反（引入双翻转）
  →0a5eb246 bind 相对化（方向即错，Blockbench 实为加性）→a354f972 回退 →21.1.19 定版。
  **待核对**：守卫者等其余 4 个 mob 在修复后姿态同样翻转回 Blockbench 所见；若某资产反而"换手"，
  说明该资产本身按镜像 authored，需单独核对资产而非改库。
- **逐顶点验证定论（2026-10-06 修正版，旧结论作废）**：旧结论"7 剪辑单播==资产≤0.0002"是循环论证
  （真值取自游戏自身 ticked 值，只能证渲染器自洽，证不了姿态==Blockbench）。正确定性经
  **真 Blockbench 真值**（web.blockbench.net，Codecs.project.load 加载同一 bbmodel，选剪辑置时，
  Animator.preview() 后抽 mesh.matrixWorld/几何顶点）验证：修复前游戏姿态=Blockbench 镜像
  （upbody 旋转角差 99.7°）；修复后游戏发射顶点==组合真值 max 0.00023 方块（294/294 cube，
  待机躯体+放下双剪辑组合）。渲染器与链数学全程无 bug（M1 内部一致性 0.00000）。
- **"弩朝向偏 90°"根因：idle 控制器停播窗口（2026-10-05 修复，doki 侧）**：掠夺者待机拆为躯体+手臂两条剪辑，躯体剪辑内置恒定 +48.9°Y 躯干侧转（AllHead/Head -47° 补偿），手臂抬起/放下关键帧恰好抵消该侧转——两者必须同时播放弩管才朝正前（实测偏 0.6°）；只播手臂时弩管偏 50.4°（视角呈 90°）。旧 doki_pillager.idle 照抄 mdsj_entity.idle 的 still→idle 交替结构，still 态只播 idle_timer，躯体剪辑周期性停播数秒 → 停播窗口瞄准姿态塌缩。修复：idle 控制器改为两态（idle 持续播躯体+idle_timer，away 只播 idle_timer，转移条件互斥）。同类缺陷：狼/商人待机 allbody 内置 23° 侧转，同一结构同样修（狼修正后 23.75°、商人 23.0° 持续保持）。教训：① 对照截图复现时必须让目标进入与截图相同的控制器状态（still/idle 相位），"验证通过"若恰在播放期会漏掉空窗期缺陷；② 多剪辑组合姿态（躯体侧转+手臂抵消）对任一剪辑停播敏感，idle 控制器不得周期性中断这类剪辑。另：eyelib 21.1.17 曾据同一截图错误归因为"关键帧含 bind 需减 bind"并改 eyelib，引入弩臂塌缩+朝下的另一回归，21.1.18 已回退——归因前必须用渲染同源代码实测骨骼链，禁止凭单帧截图改渲染库。
- **剪辑采样对比器经验（仍有效）**：① 任一端关键帧标 catmullrom 即整条样条插值
  （Curves.catmullRom v0=(p2-p0)/2），缺端点回退线性，与 BrBoneAnimationSampler 一致；
  ② 采样时间不得经 `%.3f` 字符串往返。注意：此类"游戏 ticked vs 资产关键帧"对比只证加载/采样
  无损耗，**不能**证姿态==Blockbench（显示空间翻转在其外）——姿态结论必须经真 Blockbench 对照。
- **奔跑剪辑不瞄准是资产原意（用户决策 2026-10-06，保持原样）**：奔跑剪辑资产把弩置于实体右侧 ~90° 偏下 ~50°（bone19 局部 z≈(0.6,-0.75,-0.2)），行走剪辑弩保持瞄准姿态。行走/奔跑/跳跃是全骨骼轨道，与 抬起/放下 的手臂轨道在 eyelib 加性合并（rotation 相加×权重）下会双倍旋转——叠加覆盖不可行。用户拍板：冲锋时弩下垂为美术原意，不改资产；若未来要冲锋瞄准，唯一路径是改资产剪辑（把奔跑手臂轨道换为瞄准姿态），不能靠控制器叠加。
- **贴图上传键**：实体 json `textures.default = dokimobs:textures/entity/bbmodel/<entity>`
  （codec 自动补 .png），与 DokiBbModelLoader 的上传键一一对应。
- **1.21.1 run/mods 版本残留**：copyEyelibToMods 是 additive copy，已加 doFirst 清理同 artifact 旧 jar。
- **动画幅值异常翻倍先查双驱动（2026-10-12 村民睡姿根因）**：`scripts.animate` 里控制器条目与
  直接动画条目若引用**同一动画**（经 `animations` 短名表解析后同 id），eyelib
  `BrAnimationEntry.tickAnimation` 对骨骼 rotation/position 是 `add` 累加，两条路径每帧各加一次
  → 恒定 2× 幅值（叠加基岩→MC 转换 `mul(-1,-1,1)`，表现为「观测=-2×写入」）。实例：村民
  `base_general` 控制器 `get_in_bed` 状态播 `sleep`，animate 列表又直挂 `{"sleep":["query.is_sleeping"]}`，
  睡姿双倍过转。修复走数据侧（删直接条目、恢复原始剪辑值），不是 eyelib 管线 bug。
  排查法：枚举 entity description 的 `scripts.animate` 直接条目 ∩ 各控制器状态 animations，
  交集非空即双驱动。
- **缺失引用安全降级**：控制器状态引用未映射的短名 → resolveAnimation 返回 null → 该状态视为播完，
  不崩溃不刷屏。

- **variant 落盘必须显式同步（2026-10-09 实机定位）**：渲染期 `query.variant`/`query.mark_variant`
  读的是 `SyncedBehaviorState`（EntityRenderOrchestrator.setupSyncedBehaviorContext），附件无自动同步，
  村民职业/类型变化后必须写 SyncedBehaviorState 并 `syncTrackedAndSelf`（TempVariantSetting.syncVariantIfChanged）。
  不这样做：首帧按默认 variant 解析贴图键并上传，次帧 variant 到位后键变化但 slot 已标记已上传，
  旧 eyelib（≤21.1.20）不再入队 clamped 派生 → 职业服装永久紫黑。eyelib 21.1.21 侧同时修复
  clamped 上传时机（见 eyelib docs/concepts/alpha-clamp-two-path.md「clamped 上传时机」），两层缺一不可。
  另：注册表 key 比较用 `.getPath()`（`minecraft:plains` → `plains`），勿用 toString。
- **狼/烈焰人控制器修正（2026-10-09）**：狼 movement 改用专属 `controller.animation.doki_wolf.movement`
  （不再借用 mdsj_sheep），实体 scale 0.75；烈焰人移除头发 ambient 控制器改 `doki_blaze.spear`，scale 0.8。

- **掠夺者躯体/手臂分层（2026-10-04 美术语义落地）**：行走/奔跑/跳跃是全身动画（含手臂骨骼），
  待机（躯体）/游泳（躯体）不碰手臂 → 移动时手臂控制器必须进 quiet 态（无动画）让位全身剪辑，
  静止或游泳时由手臂控制器播 放下/抬起。`q.has_target` 判别遇敌：奔跑=遇敌移动、抬起=遇敌静止。
  行走/奔跑与 idle 的权重惯例 `q.modified_move_speed*1.6`：速度 0 时权重 0 不冻结姿态（基岩混合
  语义，权重 0 = 不贡献）。待机躯体与游泳躯体的互斥靠 idle 控制器动画条件加 `&&!q.is_in_water`。
- **状态机验证法（免截图）**：反射 `BrAnimationController.Data.owner.currState` 对照
  `BrAnimationController.states()` 按引用反查状态名，eval 轮询即可断言控制器所处状态
  （air/float/quiet 等），比追相机截图可靠。注意 eval 的 /eval HTTP 端点 body 是纯文本 Java，
  不是 JSON。测试移动动画别用 navigation.moveTo——会被 RandomStrollGoal 抢导航；
  用 setDeltaMovement 冲量让实体滑行（速度属性 0.35 的掠夺者自然移动极快，追拍极易失败）。

## 2026-10-21 飞书反馈（#2/#12/#14/#18/#19/#20/#21）

**约束**：不改模型（models/贴图）与动画剪辑（`animation.*`）；只改 client entity、render controller、
animation controller。已用脚本对照 HEAD 逐项确认 `models`/`animation.*`/`textures` 无变化。

- **监守者光波/触须不播（#18/#20）**：`pre_animation` 用 `query.time_since_last_vibration_detection`
  驱动 `variable.tendril_animation`，但该查询依赖服务端 Brain 的 `VIBRATION_COOLDOWN` 记忆，客户端
  恒得 2.0 → tendril=0，shiver 永不进入。改为 dokimobs 侧新查询 `query.doki_warden_anger`
  （`DokiMolangQueries`，读客户端同步的 `Warden.getClientAngerLevel()`），
  `variable.vibration_phase = 1 - min(anger/150, 1)`。实机：anger=150 时 `tendril_animation=1`、
  `controller.animation.mdsj_warden.default → shiver`、`leftj/rightj` 旋转 ±0.10 rad。
- **监守者片片从背后消失/面剔除黑（#18/#20）**：基础 RC 用 `Material.default`（`warden:entity`，无
  DisableCulling）。给实体 materials 增加 `nocull: entity_nocull`，`controller.render.warden` 对
  `patch` 骨骼使用 `Material.nocull`。实机正/背视角 `patch` 上的片状几何均可见。
  新增 `assets/dokimobs/eyelib/materials/doki.material`：`doki_warden_glow_nocull:warden_bioluminescent_layer`
  （继承 `USE_EMISSIVE`/Blending，`+states: [DisableCulling]`）。实体 materials 增加 `glow_nocull`，
  全部 warden overlay RC（bioluminescent/spots/tendrils/heart）改用该材质；实机 `ModelComponent`
  renderType 解析为 `PortRenderPass[TRANSLUCENT_EMISSIVE, cull=false]`。
- **监守者斑点 alpha 负值**：`0.25*cos(...)` 在 180° 相位产生负 alpha（第二组斑点不可见/发黑）。
  改为 `0.25*(0.5+0.5*cos(...))`，两组交替 0~0.25，始终非负。
- **掠夺者回正抽搐（#21）**：`arms` 控制器 `down`/`up` 的转移把 `up`/`down` 排在 `quiet` 之前，
  移动开始/停止时先闪一帧目标态再进 quiet。改为所有状态优先 `quiet`，`quiet` 停止时按是否有目标
  直接进 `up`（遇敌）或 `down`（无目标）。实机 tick 日志：停止时 `quiet → up` 直达，无 `down` 中间帧。
- **村民傻子走路腿穿模（#2）**：`mdsj_villager_anim.move` 的 `move`/`move_arm` 权重对
  `query.variant==14`（傻子）降为 0.6（逃跑 0.75），减少腿摆幅；其他职业不变。
- **已验证无需改动**：#12 溺尸鱼骨（`fish`/`fish2` 每帧位置/旋转变化）、#14 蜘蛛 walk
  （`modified_distance_moved` 驱动 `anim_time_update`，腿骨位置随距离循环）、#19 羊跳跃
  （`controller.animation.mdsj_quadruped_up.up_0` 进入，`leg0/leg1` 位置从 0 → -0.24 方块）、
  #2 村民职业换装（服务端改 profession 后客户端 `query.variant` 从 14 → 1）。

**Molang 检查器**：见 [molang-checker.md](molang-checker.md)。本次全量扫描 678 条表达式 0 错误。

## 2026-10-25 飞书复检（#12/#14/#15/#17/#19/#20 状态回退为「未修复」）

**约束（用户本轮明确）**：不允许修改 model 与 animation（`models`/`animation.*`/贴图/`bbmodels`）。
本轮只做控制器/材质/Java/构建侧改动，且最终产物为三个 MC 版本的 jar 汇总。

**复现结论（HEAD 构建，三版本探针）**

| 项 | 现象 | 复测结果 | 证据 |
|---|---|---|---|
| #12 溺尸鱼 | 鱼不动 | **未复现** | `fish`/`fish2` 骨骼旋转/位置逐帧变化；鱼身区域像素 diff 2~9（天空对照 0.0） |
| #14 蜘蛛走路 | 平移不摆腿 | **未复现** | 静止时 `anim_time` 冻结、腿骨保持；移动时 8 条腿位置随位移循环（1.20.1/1.21.1/26.1.2 一致） |
| #15 村民睡觉 | 无睡觉动画 | **未复现** | `startSleeping` 后 `base` 控制器进 `get_in_bed`，`waist` rot.x=1.571、`leg` rot.x=-1.571、`waist` pos.y=-0.5（平躺） |
| #15 村民换装 | 职业不换装 | **未复现** | `/data merge` 改 profession farmer→librarian 后贴图由草帽棕围裙变为红白学者 |
| #17 守卫者跳跃 | 无跳跃动画 | **未复现** | 空中态进 `air`；按正确大小写重测：`UpBody` rot≈(-0.16,0.13,0)、`UpperBody` rot≈(0.16,0.07,0)、`LeftArm` rot≈(0.21,0.09,-0.49 rad≈-28°)，剪辑在播（幅度是否够"看得出"属美术判断）|
| #17 守卫者横游 | 游泳横躺 | **属资产设计** | `guardian.bbmodel` 名 `海洋怪物-守卫者-鱼形`，`guardian.swim` 对 `AllBody` 给 -82.5° X，鱼形模型横置即为设计意图；要改只能改动画（本轮禁止） |
| #19 羊跳跃 | 无跳跃动画 | **未复现** | 强制升空时 `leg0`/`body` 位置在 -0.04~-0.26 之间随剪辑推进（剪辑目标值 -0.25 方块） |
| #20 监守者片片 | 面剔除部分发黑 | **已定位，未提交修复** | 见下 |

**#20 根因定位（黑色片状几何）**：按骨骼 UV 采样 `warden.png`，以下骨骼的 UV 全部落在纯黑像素上
（mean RGB ≈ 0/255，且 `translucent` 层 alpha≈0），在基础 RC（`Material.default` + `Texture.default`）
里被当成不透明几何绘制 → 表现为头/肩周围的黑色碎片：
`groundwave_1..5`、`wind_2`、`light_1_2`、`light_2_1`、`light_2_2`、`chain`、`chain2`、`ghost1`、`ghost2`、
`lefthandlocator`、`righthandlocator`（后两者本就是隐形挂点）。
**RC 侧能否修？实测否**：固定相机、只替换 `versions/1.21.1/build/resources/.../pack.cospack`
并 `reloadResourcePacks` 做 A/B（三种 RC：原样 / 隐藏上述 15 根特效骨骼 / 只隐藏 `patch`）：
近黑像素 14757 / 14512 / 15134，三者相差在动画相位噪声量级；画面上那几片大的黑色块（手臂
`punch_left` 29 cubes、`punch_right` 27 cubes 与头部装甲）**不随 part_visibility 变化**。
即：肉眼可见的黑色来自这些装甲 cubes 本身 UV 落在 `warden.png` 深色区（mean RGB 5~8/255），
不是特效骨骼分层问题 → **控制器/材质侧无法修复**，只能改资产（UV/贴图/几何），
而资产修改在本轮约束（不允许改 model & animation）之外。特效骨骼（groundwave/wind/light/chain/ghost）
在基础通道确为纯黑不可见，可另行确认是否需要 `part_visibility`，但它不是本次反馈的黑色来源。

**#2 傻子村民用工作方块不换皮肤（10-25 被测试者改回「未修复」）**：属原版机制而非同步缺陷。
`VillagerProfession.NITWIT` 注册时 workstation 与 secondary job site 都是 `PoiType.NONE`
（1.21.1 反编译 `VillagerProfession.java:51`），傻子永远匹配不到工作方块；实测把傻子放在堆肥桶旁 12 秒
职业仍为 `nitwit`。而皮肤跟随职业的同步链路本身正常：服务端 `setVillagerData(... LIBRARIAN)`
后客户端贴图立即变化（farmer→librarian 同样正常，见 `TempVariantSetting.syncVariantIfChanged`）。
若要让傻子也能就职换皮肤，必须打破原版规则，属玩法改动（需求层），不能当 bug 修。

**打包与产物（GRADLE）**

- `stonecutter.gradle` 新增根项目任务：
  - `collectArtifacts`：`dependsOn :<mc>:assemble`，把 `dokimobs-<mod_version>+<mc>-<loader>.jar` 与
    `mavenLocal` 的 `eyelib-<eyelib_version>+<mc>-<loader>.jar` 平铺复制到 `artifacts/`；缺件直接失败。
  - `cleanOldArtifacts`：按 `mod_version`/`eyelib_version`（从 `gradle.properties` 读，不硬编码）删除
    `artifacts/` 内其它版本文件，保留当前 jar 及其 `.asc/.sha256/.sha1/.md5` 衍生文件。
  - 两个任务均通过 configuration cache（执行期不调用 `project.*`，目录在配置期取定）。
- `build.gradle` 发布仓库改为 `url = project.rootProject.file('repo')`：原 `"file://${projectDir}/repo"`
  在 Windows 上生成 `file://E:\...` 非法 URI，`publish` 因 configuration cache 序列化失败而整体报错。
- 产物内容（0.2.0 / eyelib 21.1.23）：`dokimobs-0.2.0+{1.20.1-forge,1.21.1-neoforge,26.1.2-neoforge}.jar`
  + 对应三个 `eyelib-21.1.23+...jar`，共 6 个 jar 平铺。

**26.1.2 开发运行环境坑（本轮踩到）**：`versions/26.1.2/build/moddev` 的 run classpath 是生成物，
若 `build.gradle` 改了 `eyelib_version` 而没重建，客户端会以旧 eyelib 启动并因
`Mod dokimobs requires eyelib 21.1.23 or above / Currently, eyelib is 21.1.15` 直接崩溃。
处理：先 `mcmcp_build(version="26.1.2")` 刷新 `clientRunClasspath.txt` 再 launch。
另：**运行中的 MC 客户端会占用 `build/moddev/artifacts/*.jar`**，此时跑任何触发
`createMinecraftArtifacts` 的 Gradle 任务会以 `AccessDeniedException ... .jar.tmp -> .jar` 失败，
必须先关闭客户端。
