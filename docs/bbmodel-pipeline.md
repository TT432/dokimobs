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
- **睡眠平移必须早于实体缩放（2026-11-04 村民睡姿根因）**：eyelib 在 `RenderLivingEvent.Pre`
  之后复刻 vanilla 睡眠变换，`translate(-step*(eyeHeight(STANDING)-0.1))` 一旦放在
  `client_entity.scripts.scale`（村民 0.8）或幼体 `scale(0.5)` 之后，平移量就被缩放系数乘小：
  成年少移 **0.304** 格、幼体少移 **0.76** 格，表现为头睡不到枕头、身体沿床轴偏床尾（方向仍正确，
  四床向一致）。vanilla `LivingEntityRenderer` 的顺序是 sleep translate → entity scale →
  `setupRotations`。修复在 eyelib `ClientEntitySetup.setupEntityClientEntityData`（21.1.26），
  不在 cospack/动画侧。验证凭据：同相机 doki vs 原版渲染截图（`versions/1.20.1/run/sleep_shots/`）。
- **缺失引用安全降级**：控制器状态引用未映射的短名 → resolveAnimation 返回 null → 该状态视为播完，
  不崩溃不刷屏。

- **variant 落盘必须显式同步（2026-10-09 实机定位）**：渲染期 `query.variant`/`query.mark_variant`
  读的是 `SyncedBehaviorState`（EntityRenderOrchestrator.setupSyncedBehaviorContext），附件无自动同步，
  村民职业/类型变化后必须写 SyncedBehaviorState 并 `syncTrackedAndSelf`（TempVariantSetting.syncVariantIfChanged）。
  不这样做：首帧按默认 variant 解析贴图键并上传，次帧 variant 到位后键变化但 slot 已标记已上传，
  旧 eyelib（≤21.1.20）不再入队 clamped 派生 → 职业服装永久紫黑。eyelib 21.1.21 侧同时修复
  clamped 上传时机（见 eyelib docs/concepts/alpha-clamp-two-path.md「clamped 上传时机」），两层缺一不可。
  另：注册表 key 比较用 `.getPath()`（`minecraft:plains` → `plains`），勿用 toString。
- **实体状态生命周期：附件存在 = 已掷骰（2026-11-02 实机定位）**：doki 开关与 dokimobs 侧随机
  变体都遵循同一生命周期——**服务端掷一次并落盘，客户端只消费**。
  `DataAttachmentHelper.getOrNull(...) != null` 即"已掷骰"，不是额外的 `init` 标志；因此
  区块重载 / 世界重进 / 跨维度重建都不会重掷。旧实现有三处违背：
  ① 每次 `EntityJoinLevelEvent` 无条件 `nextInt` 覆盖已持久化的 `SyncedBehaviorState`
  （24 只 invulnerable 僵尸中 17 只在一次卸载重载后换皮）；
  ② 客户端渲染期用 `getOrCreate`，把默认值写进客户端容器；
  ③ 对掉落物/玩家等非活体掷骰，只会无意义撑大存档（ItemEntity 也落盘 `EntityDokiData`）。
  现行约定：
  - 掷骰入口只有 `EntityDokiData.rollIfAbsent`（服务端、`LivingEntity` 且非 `Player`）与
    `TempVariantSetting.rolledVariant`（zombie/chicken/fox），均为"附件不存在才掷"；
  - join 处理器用 `EventPriority.LOW`，排在 eyelib 行为包 spawn 之后，镜像 `SyncedBehaviorState`
    时原样保留行为包刚写下的 `scale`（僵尸 0.5）；
  - 客户端一律 `getOrNull`，`null` = 尚未同步 = 按原版渲染，每帧双向写
    `useBuiltInRenderSystem` 避免晚同步导致的 false 粘性；
  - 旧存档 `{init, doki}` 由 record codec 直接解码（多余键忽略），无需迁移脚本；
  - 老存档里没有 `eyelib:doki_variant` 的 zombie/chicken/fox 在升级后首次 join 会重掷一次
    （旧数据无从判断"是否掷过"），此后永久固定。
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
~~不是特效骨骼分层问题 → **控制器/材质侧无法修复**，只能改资产（UV/贴图/几何）。~~
**此结论已于 2026-10-28 实机复验后废弃**：最终根因与修复见文末同日记录；未修改 UV/贴图/几何，
独立 alpha-test 不剔除材质已消除正背视角黑片。特效骨骼（groundwave/wind/light/chain/ghost）
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

## 2026-10-27 飞书反馈（#24/#25/#26/#27，1.20.1 实机）

- **监守者透明环孔黑块（#24）**：第二基础主纹理引用 `minecraft:entity_nocull`，不能直接继承
  SOLID。`doki.material` 新增 `entity_nocull:entity_alphatest` 并加 `DisableCulling`；实机正视角
  环孔透明区域恢复，不再出现实体黑块。
- **村民睡眠朝向/悬浮（#25）**：`ClientEntitySetup` 在 sleeping 且床朝向有效时按床轴居中，
  offset 使用 `eyeHeight(STANDING)-0.1`，实体旋转用 `YP.rotationDegrees(-bed.toYRot())`。
  Bedrock `get_in_bed` 已包含平躺姿态，不再额外加 ZP 90°。四床向均贴床且头向正确；
  `stopSleeping` 后恢复 STANDING 与直立。
- **守卫者尾鳍反曲（#27）**：`guardian.jump` 的 DownBody4/DownBody5 全关键帧 X 分别 +19°/+17°；
  实机尾鳍连续顺弯。结构化 diff 应只含这两个骨骼的 10 个 X 字段。
- **蜘蛛移动反复下蹲（#26）**：`controller.animation.mdsj_spider.rest` 原在移动时按片长循环
  `default → stand → rest`。改为 initial/default 优先 `stand:q.is_moving`，rest→stand 不再等待
  动画结束，stand 仅在 `!q.is_moving` 时回 default；持续逐 tick 位移的多次采样均保持 stand。
- **cospack 同名控制器覆盖**：蜘蛛与洞穴蜘蛛条目各自内嵌同名 rest 控制器，全局扁平化后后加载
  条目覆盖先加载条目。只改蜘蛛条目不会生效，两份共享定义及 `work/entries` 解包副本必须同步；
  修改后需重启客户端，单次 `reloadResourcePacks` 未重载该 cospack 定义。
- **移动状态探针**：只设 `setDeltaMovement` 不足以稳定驱动 `q.is_moving`；应逐 tick 在服务端线程
  实际 `move(MoverType.SELF, ...)` 并设置 delta。100ms 间隔会留下无位移 tick，造成 stand/rest
  交替的探针假阴性；用 50ms 连续位移，跨多次短 eval 采样，禁止在渲染线程 sleep。

## 2026-10-28 飞书反馈最终复验（#2/#14/#15/#17/#20）

**验证范围**：1.20.1（Forge）、1.21.1（NeoForge）、26.1.2（NeoForge）均启动真实客户端逐项复验，
不是由单一版本推断其他版本。职业 variant、睡眠/行走、蜘蛛腿骨、守卫者垂直状态与尾鳍、监守者
材质与音爆均逐版采样；#20 三版均已目检音爆期间正、背截图，未见黑片。村民行走位移三版均验证；
行走控制器 base 在采样中保持 default，因此不宣称观察到独立 walk state。

- **监守者黑片（#20）**：废弃 10-25「只能改 UV/资产」结论。最终为基础通道使用独立
  `doki_warden_alphatest_nocull:entity_alphatest` 材质并启用 `DisableCulling`，运行时解析为
  `PortRenderPass[ALPHA_TEST,cull=false]`；固定实体正/背视角截图均不再出现黑片，未修改模型 UV 或贴图。
- **监守者音爆（#20）**：`query.doki_warden_sonic_boom` 窗口为 4300ms；default 控制器的
  initial/default/move/run/sonic_attack 五处统一使用该查询。常驻 `bob` 与 `look_at_target`
  在音爆期间停播，避免其零缩放/姿态轨道覆盖 sonic 子动画。实机 0~4200ms 为 `sonic_attack`，
  1800~3000ms 光波、风、幽灵等骨骼缩放非零；4300ms 回 `default` 且特效缩放全部归零。
- **守卫者游泳/尾鳍（#17）**：水中上升与下降保持 `default`，DownBody4/DownBody5 连续非零变化；
  陆地上升进入 `air`，尾鳍剪辑随 `query.vertical_speed` 推进，下降回 `default`。水内近景与陆地
  正视角截图均已核对。
- **村民（#2/#15）**：职业键必须使用注册表实际值 `weaponsmith`/`toolsmith`/`mason`，对应
  variant 9/10/13；实机切换后客户端 query 与贴图同步变化。NITWIT 使用工作方块不就职属于原版机制，
  不能与职业同步缺陷混同。`startSleeping` 后 base 控制器进入 `get_in_bed`，`query.is_sleeping=1`；
  傻子行走正/侧面连续帧已核对，腿部摆幅修正后未见互相穿模。
- **蜘蛛（#14）**：沿用已完成的移动实测结论，移动时 8 腿循环，无需追加代码改动。


## 2026-10-30 村民走路脚部穿模（#15/#25）修复与实测口径

**症状**：村民行走时脚陷进地面。**根因**：`animation.mdsj_villager.walking` 的 `root.position`
`y` 通道全周期为负（-0.25~-0.877 px），整具模型被下压，最低顶点落到地面以下。

**修法**（只改 cospack 内动画剪辑，不动 bind 几何，静止/睡觉不受影响）：
`animation.mdsj_villager.walking`、`animation.mdsj_illager.sprint_evoker` 的 `root.position.y`
逐相位抬升（只抬不降，24fps 关键帧求解，`work/cospack_footlift_20261030.py`）。
`work/pack.before_footlift.cospack` 保留穿模前基线用于 A/B。

**1.20.1 实机证据**（同一驱动 + 同一相机 + 18s 采样的顶点级探针）：
- 行走最低点：基线 **-0.0986 格** → 修复后 **-0.0682 格**；
- 站立/idle 自身最低点为 -0.0755 格，即**修复后走路已不比站着更深**（修复前比 idle 深 0.023 格）；
- 近景截图：修复后鞋子完整踩在地面，修复前小腿被地面平面切掉（`work/v3f_*.png` vs `work/v3b_3_crop.png`）。
- 离线几何求值（15 套 geometry × 全周期）基线最低 ∈[-0.0750,-0.0375]，修复后 ≥-0.0015。

**探针口径坑（重要，别再踩）**：
1. `RenderParams.poseStack().last().pose()` 变换 (0,0,0) 得到的"模型原点"**包含当帧动画/根骨骼平移**，
   不能当穿模基线：同一批静止村民换相机后该值在 -0.42~-1.20 间漂移，相对量随之漂移 0.05 格。
   可信口径只有「同条件相对比较」+「近景截图」。
2. 真实渲染走 `HighSpeedRenderModelVisitor`（`ActiveModelRenderVisitors.RENDER_VISITOR`）的烘焙快路径
   `renderBakedBone` + `static visitVertex(BakedModel.BakedBone, ...)`；`visitCube` 是空实现，
   插桩 `RenderModelVisitor` 对实体渲染无效。`BakedBone.position` 是 bind 模型空间，`positionResult` 是已变换结果。
3. GPU 蒙皮（`-Deyelib.gpuSkinning=false` 关闭）时经典顶点写入被跳过；该属性是 `static final`，必须启动前设置。
4. 开关值不要用 `Boolean.getBoolean("-Dx=1")`（`parseBoolean("1")==false`），用显式字符串比较。

**工具坑（本机 Windows）**：
- 端口 **44620-48715 整段 bind 失败**（WinError 10013，`netsh ... show excludedportrange` 不显示），
  `mcmcp_launch` 随机端口落进去会让 `AIDebugServer` 报 `BindException` 并让 /ping 静默超时；
  可用段 41000-44619，本项目调试固定 `port=43017`。
- `minecraft.reloadResourcePacks()` 对 cospack 的**动画剪辑**生效（实测改 walking 后同一实体最低点立即变化），
  换包试参数不必重启客户端；此前记录的「控制器定义不重载」结论不适用于剪辑数据。
- 截图 `Screenshot.grab(gameDirectory, name, mainRenderTarget, c->{})` 落到 `run/screenshots/<name>`（无扩展名）。

## 2026-11-06 幻翼翅膀「z 冲突闪烁」与 26.1.2 羊驼表情（复现失败，结论与口径）

**用户报告**：① 幻翼在特定角度下翅膀出现类似 z 冲突的闪烁；② 26.1.2 的羊驼没有「闭眼+小嘴」那个表情，
怀疑版本间资源不对称。

### 复现结果：两者均未复现

- 幻翼：1.20.1 / 1.21.1 / 26.1.2 三个客户端都实机绕拍（8 方位 × 1.0~2.4 格 + 俯仰 0/15/30/45 + 近距 0.6~1.2 格，
  合计 100+ 张）。全部版本翅膀膜面稳定，无条纹/半透明抖动。
- 羊驼：同 `Variant:0..3`、同机位、同 10 帧序列对比 1.21.1 vs 26.1.2：**脸部几何与表情层完全一致**
  （右眼深色眉块、浅蓝眼白/瞳孔、眼睑遮盖相位都相同），差异只有整体亮度/对比——26.1 实体走
  `LIGHTMAP_ONLY`（ADR-0040），不做逐面方向光，所以更亮更平。

### 资源不对称结论：不成立（dokimobs 侧）

- 三节点共用同一份 `src/main/resources`（stonecutter 生成同一份 `build/resources`），**没有 per-version 资源覆盖**；（单元：资源布局核对 / 本节结论 / `git ls-files` 仅一份资源树 + `versions/*` 只有构建产物）
  `pack.cospack`、`eyelib/entity|render_controllers|animation_controllers` 全版本同一份。
- 能造成版本差异的只剩 eyelib 的 per-version 构件与 26.1 渲染管线（LIGHTMAP_ONLY / PSO / 两阶段提交）。

### 数据侧发现（可复用的判定结论）

- 幻翼 `geometry.mdsj_phantom` 里唯一的零厚度双面四边形：`leftwing3.cube1` / `rightwing3.cube0`
  （面积各 1.875 格²，两面**顶点完全相同**、法线相反、UV 不同）。其中**正面 UV（u 0.121~0.238、v 0~0.0625）
  落在 alpha 全 0 区域**（整块贴图 region 全透明），即该面绘制不出任何像素。
  A/B：删掉这两个空面后同机位像素差只落在 wingtip/动画边缘，**无视觉变化** ⇒ 不是可见闪烁源。
- `phantom.png` 全局 alpha 只有 0 / 255（半透明像素仅 5 个）⇒ 该实体用 `translucent` 与 `cutout`
  **语义等价**，blending 只可能来自 mipmap（缩小采样把 0/255 平均成中间值）。
- 羊驼脸部是**亚毫米层叠**：`lefteyesbase` 4 层 z 间隔 0.0003~0.003 格、`mouth` 7 层、
  `lefteyebrownormal`/`lefteyebrowanger` 靠 z±0.0625 切换（`animation.mdsj_llama.shoot_anger`）。
  按 24-bit 深度、near=0.05 估算，2 格处精度 ~5e-6 格、30 格处 ~1e-3 格 ⇒ **近距离不会 z 冲突**，
  不能用「亚毫米层叠」解释近距离表情丢失。

### 排查口径坑（本次新增）

1. **粒子/天气会污染帧差分析**：幻翼自身粒子 + 下雨会在全屏产生竖条纹并让相邻帧差异暴涨
   （无雨 mean|Δ|=1.6 / >25 像素 8k，有雨 mean=8~11 / >25 像素 43k~82k）。做「闪烁」判定前必须先
   `weather clear` + `gamerule doWeatherCycle false`，并确认 `level.isRaining()==false`。
2. **截图 API 三版本不同**（都在 render 线程的 /eval 里可用）：
   - 1.20.1 / 1.21.1：`Screenshot.takeScreenshot(RenderTarget)` 返回 `NativeImage`，自己 `writeToFile`；
   - 26.1.2：只有 `takeScreenshot(RenderTarget, Consumer<NativeImage>)`，回调里 `writeToFile`，
     异步落盘，读文件前需等 ~200ms。
3. **相机朝向公式**：`yaw = atan2(-dx, dz)`（度）、`pitch = atan2(eyeY - targetY, horizDist)`（正值向下）；
   `player.setPos(x, eyeY-1.62, z)`。曾把 yaw 写成 `atan2(dx, dz)` 导致镜头对着反方向、
   空拍一整轮还以为实体隐形。
4. **`mcmcp_build(<version>)` 后 `clientRunVmArgs.txt` 会被重写**：临时加的 `-D` 开关（如
   `-Deyelib.gpuSkinning=false`）必须重新追加；GPU 蒙皮开关 A/B 实测对幻翼翅膀外观无差异
   （1.20.1 开/关同机位同结果），可排除 GPU 蒙皮。

### 2026-11-06 追加：羊驼「表情」根因 = eyelib 26.1 把观测写死（已修复）

上文「两版本脸部一致」的对照拍的是**平静态**，掩盖了真正的差异：用户参考图里的
「红色十字 + ε 嘴」不是发饰，而是 `expression_anger` + `mouth` 的**生气表情**
（`animation.mdsj_llama.shoot_anger`），只在
`controller.animation.mdsj_llama.shoot` 进入 `shooting` 时播放，转移条件是
`q.facing_target_to_range_attack`。

根因在 `qylEyelib` 的 `EntityExtraDataRuntimeHooks.observeGoalFlags`：
`>=26.1` 分支把 `RangedAttackGoal.attackTime` 观测写成常量 `false`、
`AvoidEntityGoal.toAvoid` 写成 `null`，且 `eyelib.mixins.json` 的 modern 分支
把两个 accessor 从列表里整体剔除。于是 26.1 上
`query.facing_target_to_range_attack` / `query.is_avoiding_mobs` 恒为 0：
生气表情、回避类动画一律不播。cospack 里前者被引用 24 处、后者 19 处。

修复（eyelib 21.1.29，PR #44）：两个观测统一走既有 accessor，modern 分支重新登记
`RangedAttackGoalAccessor` / `AvoidEntityGoalAccessor`。

**实机验证（26.1.2 客户端）**：
- 女巫 `RangedAttackGoal running=true attackTime=45/20/53` → 查询 `true`（修复前恒 false）。
- 狼的 `AvoidEntityGoal<Llama>` → `is_avoiding_mobs=true / is_avoid=true`（修复前恒 false）。
- 羊驼客户端强制 `facing_target_to_range_attack=true`：脸上出现**怒气十字 + 生气眉 + ε 嘴**；
  置回 false 即消失（A/B 截图，`work/evidence_20261106/`）。
- 1.21.1 复测女巫查询仍为 `true`；该节点生成代码与 21.1.28 逐字节等价（行为零变化）。

排查口径教训：**版本对照必须覆盖「触发态」**，只在平静态对比会得出「两版本一致」的错误结论；
且模型里的 `expression_*` / `mouth` 这类埋在头部内部（z 在头骨面之后）的部件，天然只在被
动画推出来时可见——看不到表情时先查驱动它的 molang 查询，再怀疑深度/资源。

### 2026-11-06 复检与根治：实体 pass 状态三套构造导致「透明 texel 写深度」（qylEyelib ADR-0042）

上一条把差异归为「26.1 半透明不写深度」，方向对但归因不完整。用户「幻翼近距离 z 冲突式闪烁」
与「监守者/幻翼的透明片挡掉水面」在 21.1.29 上仍可复现的**共同**机理是：**实体 pass 的 alpha test
与深度写没有单一权威，三套构造函数各自重建**：

| 构造点 | alpha test | 深度写 | cull |
|---|---|---|---|
| `<26.1` `RenderPassAdapter` 的 vanilla 类型表 | 由 vanilla 着色器隐式提供（cutout/translucent/emissive 都 discard 0.1；仅 `entitySolid` 不 discard） | vanilla 默认 | **SOLID/EMISSIVE/ADDITIVE 分支丢弃 `disableCulling`** |
| `>=26.1` `entityPassState`（回退 RenderType） | 只在 CUTOUT 类加 `ALPHA_CUTOUT` | 自造 `!blend`（半透明不写） | 正确 |
| 材质驱动 `custom`/`buildPipeline` | 同上（只在 CUTOUT 类加） | Bedrock `DisableDepthWrite`（默认写） | 正确 |

26.1 的实体着色器是统一的 `core/entity.fsh`，**只有 `#ifdef ALPHA_CUTOUT` 才 discard**
（`work/mc261src` 抽取自 `minecraft-patched-26.1.2.78-sources.jar`）。而 vanilla 26.1 的
`RenderPipelines` 对 `ENTITY_CUTOUT(_CULL)`、`ENTITY_TRANSLUCENT(_CULL)`、`ENTITY_TRANSLUCENT_EMISSIVE`、
`ARMOR_TRANSLUCENT`、`BREEZE_WIND`、`ENERGY_SWIRL` **全部**定义 `ALPHA_CUTOUT=0.1`，只有
`ENTITY_SOLID`/`GLINT` 不定义；深度写方面 `ENTITY_TRANSLUCENT` 是 DEFAULT（写），
`ENTITY_TRANSLUCENT_EMISSIVE` 显式 `DepthStencilState(LESS_THAN_OR_EQUAL, false)`（不写）。

于 26.1 的材质驱动路径（`lighting=LIGHTMAP_ONLY` 使**所有**实体材质都走 custom pipeline，
ADR-0040）上表现为：**BLEND / TRANSLUCENT_EMISSIVE 的 pass 既没有 `ALPHA_CUTOUT` 又按 Bedrock
默认写深度**。alpha≈0 的 texel 照常光栅化并写深度；随后 `post/transparency.fsh` 按 main/translucent
各层**深度**做逐像素排序混合（`try_insert`：深度大的先混），main 层因此被当成「比水近」→
**透过透明区域看到的是天空而不是水**。同一模型多层半透明片之间则一层写、一层不写深度，
叠上 `sortOnUpload` 的顶点排序键不是相机距离 ⇒ 层间遮挡随姿态/视角翻转，观感即「z 冲突式闪烁」。

**静态审计证据**（渲染器真实双线性 UV 采样 cospack 贴图；脚本见 qylEyelib
`scripts/alpha_pass_audit.py`）：

| 实体 | 材质 | 通道 | 透明度 texel 占比（alpha≈0） | 典型骨骼 |
|---|---|---|---|---|
| minecraft:warden | `doki_warden_glow_nocull` | BLEND+EMISSIVE，写深度 | 0.95~1.00 | `groundwave_*`/`wind_*`/`light_*`/`ghost*` |
| minecraft:phantom | `translucent` | BLEND，写深度 | 0.67~0.83 | `mouth_*`/`leyesbase`/`upbody` |
| minecraft:creeper | `charged_creeper` | ADDITIVE，写深度 | 0.17 | 全身外壳 |

**修复**（qylEyelib 21.1.30，ADR-0042）：`BrRenderTypeFactory` 新增 `PassKind{ENTITY,PARTICLE}`
与两条 policy——
`alphaTest(state, kind)`（实体通道：除 OPAQUE/EMISSIVE/GLINT 外一律 0.1；粒子通道仅材质显式
`ALPHA_TEST`）与 `effectiveWriteDepth(state, kind)`（实体 `TRANSLUCENT_EMISSIVE` 不写深度，其余尊重
`DisableDepthWrite`）。26.1 pipeline 由 policy 决定 `ALPHA_CUTOUT`；`<26.1` 的 canonical→vanilla
映射改成**全组合**（vanilla 无法精确表达的组合走同一份 custom 构造），补齐丢失的 cull/blend。
渲染状态缓存键与 pipeline location 都带上 `PassKind`。ADR-0042 §5 显式登记了本轮**未改**的
跨实体 flush 时序问题（`flush()` 逐组件 vs vanilla 两段 flush）及修法。

**验证**：

- 三节点 `compileJava` 通过；新增 `BrRenderTypePassPolicyTest`（entity/particle 的 alpha test 与
  深度写表）三节点全绿；`material.*`/`bridge.material.*` 测试三节点只剩 HEAD 既有基线失败
  （26.1.2 的 `BrRenderStateLightingModelTest`、`BrRenderStateSpecTest`，见滞留反馈）。
- 26.1.2 实机（`/eval` 读真实 RenderPipeline）：
  - 修复前（21.1.29）幻翼翼 pass：`RendererPass[TRANSLUCENT, cull=false]`，
    `ShaderDefines[values={}, flags=[NO_CARDINAL_LIGHTING]]`，`depthWrite=true`，`blend=true`；
  - 修复后（21.1.30）：监守者 5 个 `TRANSLUCENT_EMISSIVE` 叠加层全部
    `values={ALPHA_CUTOUT=0.1}` + `depthWrite=false`；`ALPHA_TEST` 层 `ALPHA_CUTOUT=0.1` + 写深度；
    `SOLID` 层不定义（与 vanilla `ENTITY_SOLID` 一致）。
- 1.20.1 实机：监守者（含青色光环/发光层、sonic boom 窗口）+ 幻翼 + 牛 + 水墙同场截图，无
  实体消失/黑片，水面在片状几何后正常渲染（`versions/1.20.1/run/alpha_ab/screenshots/`）。
- **未做成的对照**：幻翼/监守者的「有 bug vs 修复」像素级 A/B 不成立——幻翼翅膀在动画中持续
  摆动、且两次独立启动的整帧亮度不同（世界时间/曝光差异），逐像素差被这两项噪声淹没
  （同 ms 帧的全屏 mean|Δ|≈57~77）。本轮的可证伪证据取**管线状态本身**（上面的 define/深度表），
  而不是截图统计。

## 2026-11-06 末影人搬运方块渲染

**结论先行**：末影人的 `carrying` 动画、`carrying_ctl` 控制器与 `query.is_carrying_block` 查询
本来就是接通的（实机 `currState=carry`、双臂 +40°）；缺的是**方块本体**。eyelib 取消原版
`RenderLivingEvent` 后原版 `CarriedBlockLayer` 不再执行，而 eyelib 没有对应渲染。基岩版资源包
同样只定义手臂姿态（`entity/enderman.entity.json` + `controller.animation.enderman.carrying`，
条件 `query.is_carrying_block`），方块由引擎按实体 carried block state 绘制。

**实现**（qylEyelib 21.1.31）：新增 `CarriedBlockRenderer`，在
`EntityRenderOrchestrator.renderComponents` 的实体 submit 阶段调用一次：
- 方块来源走 `RenderSystemPort.getCarriedBlockState`（bridge 读 `EnderMan#getCarriedBlock`），
  绘制走宿主 `BlockRenderDispatcher#renderSingleBlock`（26.1 为
  `BlockModelResolver.update` + `BlockModelRenderState#submit`）；不写死方块 id；
- 位置取 `lefthand`/`righthand` 骨骼 bind pivot 经当前动画矩阵后的世界中点，朝向/缩放取根骨骼
  当前姿态；不写死实体专属偏移；资源包没有双手骨骼则不画（不做错误位置兜底）；
- **26.1 必须放实体 submit 阶段**：放进 `extraRender`（deferred geometry writer /
  `renderAllFeatures`）时 `BlockModelRenderState.submit` 不会出画；这是本轮唯一的版本差异坑。

**验证**：1.20.1 / 1.21.1 / 26.1.2 三节点实机，dirt 与 TNT 两种 block state 均显示在双手之间；
清除 `carriedBlockState` 后方块消失、手臂回 default；原版渲染（`doki=false`）不会双画。
证据与完整探针记录见 [work/enderman-carried-block.md](../work/enderman-carried-block.md)。

## 2026-11-06 蜜蜂背面看不到翅膀（材质剔除 + 背面 UV 空）

**现象**：1.20.1 实机从背后看 doki 蜜蜂，头发两侧应有翅膀的位置完全空白（正面正常）。

**根因（数据实测）**：`geometry.mdsj_bee` 的 4 个翅膀骨骼（`rightwing1/2`、`leftwing1/2`）
各是 1px 厚薄板（`z = 0.18125~0.24375`），正反两面各一套 UV：
- `-z` 面 UV（rightwing1 `(0.1016,0)~(0.1953,0.0547)`）画有翅膀图案，104 px 中 74 个不透明；
- `+z` 面 UV（rightwing1 `(0.1016,0.0625)~(0.1953,0.1172)`）4 张变体贴图均为空（rightwing1
  104 px 仅 1 个不透明像素，rightwing2 全透明，leftwing1 仅 8 个）。
`default` 材质 `cutout` 解析为 ALPHA_TEST + **背面剔除**，于是从背后看 `+z` 面透明、
`-z` 面被剔除 → 翅膀整体消失。头发（`z` 最远 0.4587）遮挡不到翅膀外段（`x` 0.46~0.94），
不是遮挡问题。

**修复（不动模型/贴图/动画）**：`doki.material` 新增
`doki_bee_alphatest_nocull:entity_alphatest` + `DisableCulling`（与
`doki_warden_alphatest_nocull` 同构）；`minecraft:bee` 的 client_entity `materials` 增加
`nocull`；`controller.render.mdsj_bee` 的 `materials` 由 `[{"*": ["Material.default"]}]`
改为追加 `"rightwing*"` / `"leftwing*"` → `Material.nocull`。双面渲染让 `-z` 面从背后也可见
（原版 bee 即 `entityCutoutNoCull` 语义）。注意与幻翼「双面重合面 z 冲突」不同：蜜蜂是**单面**
+ 背面 UV 空，删面/偏移不适用。脚本：`work/pack_patch_bee_wing_nocull.py`。

**验证**：1.20.1 / 1.21.1 / 26.1.2 三节点实机，summoned `minecraft:bee`（NoAI/NoGravity），
玩家正后 / 正前 3.5~4 格截图：三节点背面翅膀均可见、正面无回归
（`versions/{1.20.1,1.21.1,26.1.2}/run/debug_shots/screenshots/bee_{back,front}_fix*.png`；
放大对比 `work/evidence_20261106/bee_back_fix_zoom.png` / `bee_front_fix_zoom.png` /
`*_1211*` / `*_2612*`）。修复前背面 UV 的 alpha 证据见
`work/evidence_20261106/bee_default_uv_marked.png`（绿框 `-z` 有图案，蓝框 `+z` 空白）。

## 2026-11-07 羊染色与狼坐姿修复

### 羊染色（已实现）

**需求**：羊可被染料染色（`minecraft:sheep` 的 `Color`）；命名为 `jeb_` 时羊毛呈彩虹渐变。
不改模型/贴图/动画，只改渲染控制器与 Java 查询。

**数据侧**（`work/pack_patch_sheep_dye.py`，`pack.cospack`）：

- 主控制器 `controller.render.mdsj_sheep` 改为只匹配 `*` → `Material.default`，
  并用 `part_visibility: [{"wool*": ["0.0"]}]` 隐藏全部 `wool*` 骨骼（避免与羊毛层双画）。
- 新增 `controller.render.mdsj_sheep_wool`：`materials: [{"wool*": ["Material.wool"]}]`，
  几何仍为 `Array.geos[query.is_sheared]`，`color` 的 r/g/b 取
  `query.doki_sheep_wool_r/g/b`（a=1）。该控制器只把 `wool*` 骨骼作为基础可见集，
  因此非羊毛骨骼不会被着色或重画。
- **RenderController 可以连续取色**：`color` 是逐帧求值的 Molang 表达式，jeb_ 的两色插值
  就是靠它做的，不需要 16 张预染色贴图。限制是 `color` 作用于**整个控制器组件**（不是单个
  材质组）：若直接放在主控制器上，会把皮肤/头发一起乘色；因此必须把 `wool*` 拆成独立
  RenderController，这也是本次拆控制器的唯一原因。

**Java 侧**（`DokiMolangQueries`）：

- `doki_sheep_wool_r/g/b` 从渲染 host context 取 `Sheep`，按 `getColor()` / 自定义名取色。
- 静态色复刻 vanilla `Sheep.createSheepColor`：白色固定 `0.9019608`，其余
  `DyeColor` diffuse × 0.75（1.20.6+ 按原版 `floor` 量化到 8 bit）。
- `jeb_` 复刻各节点 `SheepFurLayer`：
  - 1.20.1 / 1.21.1：`i = tickCount / 25 + entityId`，取 `DyeColor.byId(i)` 与 `i+1`，
    `f = ((tickCount % 25) + partialTick) / 25` 线性插值；
  - 26.1：时间轴改为 `ageInTicks = tickCount + partialTick`，**不再加 entityId**；
    颜色顺序仍是 `DyeColor.values()` 的 16 色、25 tick 步进。
    **踩坑**：26.1 的 `ColorLerper.SHEEP` 构造用的是 `DyeColor.values()`，而
    `MUSIC_NOTE_COLORS` 的 12 色顺序只属于 `MUSIC_NOTE`；首版按 12 色写导致彩虹顺序错，
    已用 `ColorLerper.getLerpedColor(SHEEP, age)` 实机比对纠正。
- 剪毛后 `geometry.sheep_sheared2` 仍保留 8 个 `wool*` 毛边骨骼，这些骨骼同样随染料/彩虹着色；
  主控制器的隐藏集合与几何选择无关，两套几何下羊毛层都只出现一次。

**验证**（三节点实机）：

| 节点 | 静态色 16/16 对照基准 | jeb 对照 | 截图 |
|---|---|---|---|
| 1.20.1 | `Sheep.getColorArray(DyeColor)`，16 色逐通道 err=0 | 手算 `tickCount/id/partialTick` 插值，帧偏移内一致 | `versions/1.20.1/run/sheep_shots/screenshots/plain_{red,blue,lime}`、`jeb_rainbow*`、`sheared_red` |
| 1.21.1 | `Sheep.getColor(DyeColor)` 拆 ARGB，16 色 err=0 | 同上（`Sheep.getColor` 版本） | `versions/1.21.1/run/sheep_shots/screenshots/1211_{jeb,blue}` |
| 26.1.2 | `ColorLerper.SHEEP.getColor(DyeColor)`，16 色 err=0 | `ColorLerper.getLerpedColor(SHEEP, tickCount+partialTick)`，best err ≤0.009（组件帧与探针帧的部分 tick 差 + 8bit 量化） | `versions/26.1.2/run/sheep_shots/screenshots/2612_{blue,jeb_0..5}` |

另外：三节点都用 `Player.interactOn(...)` 走完整「玩家手持染料右击羊」链路，
颜色由白变 lime（26.1 需带 hitVec 的三参重载）；1.20.1 检查了剪毛前后组件结构——主组件 `rcColor=null` 且
`visibleWoolBones=0`，羊毛组件 `rcColor=<染料色>` 且 `visibleWoolBones=10`（剪毛后 8）。
`gradlew :<node>:molangCheck -PmolangTarget=<src assets>` 三节点均
`683 expressions, 0 error(s)`；`assemble` 三节点通过；三个 jar 内的 `pack.cospack` 均已包含
`controller.render.mdsj_sheep_wool`。

### 狼坐姿修复（无专用载具素材，基于现有剪辑 + 骑乘高度偏移）

**用户反馈**：命令坐下后模型看起来仍像站立（附图为旧姿态）；这是错误的坐姿，需要修复。

**素材核对**：
- `wolf.bbmodel` 的 `animations` 共 14 条，唯一坐姿是 `wolf.坐下`（loop=hold）；
  不存在 `骑/载具/ride/vehicle/sit_boat` 等第二条坐姿。
- 模型骨骼/元素中没有 `seat`/`saddle`/`ride`/`vehicle` 命名；14 条动画里 `wolf.攻击B`、
  `wolf.落地`、`wolf.坠落` 未被 `wolf.json` 引用，但均与坐姿无关。
- 迁移前的 `pack.cospack`（5ff34db^）本来就没有 `minecraft:wolf` 条目；当前 cospack 也没有。
- vanilla 1.20.1 `WolfModel`/`WolfRenderer` 同样只有单一 sitting pose，没有载具分支。
- 结论：没有第二条载具坐姿素材；但旧 `wolf.坐下` 的数值本身不对——`AllBody.y=-5px`、
  小腿 `+80°` 使身体过高、小腿竖直，视觉上读作站立。

**修复**（`work/patch_wolf_sit.py`，只改 `wolf.bbmodel` 的嵌入动画 `wolf.坐下`，不改几何/贴图）：
- `AllBody.position.y = q.is_riding ? -3 : -9`（bbmodel 剪辑空间像素）：
  地面 `-9px` 让身体接近地面；骑乘 `-3px` 抬高 6px（0.375 格）适配船/矿车乘客高度。
- 大腿 `x=-80` 保持；小腿 `x=80 → 160`（游戏内 `-160°`），小腿折到身下；脚 `x=15` 保持。
- 结果为明确的跪坐；载具中同一坐姿抬高，避免下半身陷进船体。骑乘差异是同一剪辑的 Molang
  高度分支，不是第二条美术动画；若以后要真正不同的载具腿部姿势，仍需新增剪辑。

**验证**：
- 1.20.1 / 1.21.1 / 26.1.2 实机召唤 doki 狼并 `Sitting:1b`：
  - 地面 `AllBody.position.y = -0.5625`（-9px）；
  - 船载 `AllBody.position.y = -0.1875`（-3px，`q.is_riding` 生效）。
- 1.20.1 还用空手 `Player.interactOn` 对已驯服狼走 `setOrderedToSit` 链路，下一 tick
  `isInSittingPose=true`；坐姿动画正常进入。
- 截图：`versions/1.20.1/run/sheep_shots/screenshots/dog_sit_cond_{ground,boat}`、
  `versions/1.21.1/run/dog_shots/screenshots/1211_dog_sit_{ground_clean,boat}`、
  `versions/26.1.2/run/dog_shots/screenshots/2612_dog_sit_{ground_clear,boat}.png`。
- 注意：旧实现的逐骨骼采样只证明「动画值已写入」，不能证明视觉姿态正确；本次以地面/船载
  实机截图 + `AllBody` 偏移为证。

## 2026-11-07 守卫者眨眼 / 受伤 overlay / 栓绳 / 行商羊驼装饰

四项用户反馈，根因都在 eyelib 渲染层或资源绑名，不在动画数值/几何/贴图本身。

### 守卫者眨眼不覆盖眼眶（骨骼名不匹配）

**根因**：共享剪辑 `animation.mdsj_entity.blink` 只驱动两个骨骼名——`eyelid`
（`scale.y` 1→0）与 `eyebrows`（`position.y` −1.2px）；守卫者 bbmodel 的组名是
`EyeBalls` / `Eyebrows`，eyelib 按 animator `name`（而不是 uuid）绑定骨骼，名字对不上，
`eyelid` 通道没有落点，于是只有眉毛动、眼睑不动。

**修复**（`work/patch_guardian_blink.py`，只改名字，不改几何/贴图/动画曲线）：
将 group `EyeBalls → eyelid`、`Eyebrows → eyebrows`，并把该 bbmodel 15 条动画里
30 个 animator 的 `name` 同步改名（`uuid` 不变）；动画值、时间轴、时长一律未改。

**验证**（三节点）：
- 1.20.1：`eyelid` `scale.y` 采样 min 0.0 / max 1.0，240 tick 内 12 个闭合 tick；
  截图闭眼帧瞳孔像素骤降。证据 `work/evidence_20261107/samples_1201.txt`、
  `work/guardian_blink_{open,closed}_1201.png`。
- 1.21.1：`work/evidence_20261107/blink1211_{6,51}`（睁/闭）。
- 26.1.2：`work/evidence_20261107/blink_data_2612.txt`（240 tick 逐 tick 采样）
  显示 `eyelid` 每约 80 tick 出现一次 1.0→0.0→1.0，闭合 2–3 tick，闭合时
  `eyebrows.position.y = -0.075`（= −1.2px）；截图文件名带采样值
  `work/evidence_20261107/f_{054,061}_lid*.png`，
  其中 `lid+1.00` 睁眼、`lid+0.00` 眼睑完全覆盖（`work/evidence_20261107/blink2612_lid_seq.png`
  为睁眼/半闭/全闭并排对照）。

### 萌化生物受伤无红色 overlay（26.1 适配器写死）

**根因**：`RenderLivingEventAdapter` 的 26.1 分支把 overlay 坐标写死为
`OverlayTexture.NO_OVERLAY`，1.20.1/1.21.1 分支则已按 `hurtTime` 计算；因此只有 26.1 没红闪。

**修复**：26.1 分支改用 `LivingEntityRenderer.getOverlayCoords(state,
accessor.callGetWhiteOverlayProgress(state))`；`LivingEntityRendererAccessor` 增加 26.1
`EntityRenderState` 重载，`eyelib.mixins.json` 全版本注册该 mixin（旧重载仅 legacy 存在）。

**验证**：三版本实机对 doki 生物造成 1 点伤害，`hurtTime` 9→1 的每 tick 帧全身明显变红，
`hurtTime=0` 恢复。证据 `work/evidence_20261107/hurt_1201.png`、
`work/evidence_20261107/hurt_1211.png`、
`work/evidence_20261107/h_{02,06}_ht*.png`（文件名带 hurtTime，
`work/evidence_20261107/hurt2612_seq.png` 为 ht00/08/06/04/02/00 并排）。

### 栓绳不渲染 / 锚点不对（事件早退 + 缺 Bedrock locator）

**根因**：eyelib 取消 `RenderLivingEvent.Pre`；1.21.1+ 原版栓绳在
`LivingEntityRenderer.render` 末尾调用的 `EntityRenderer.render` 内绘制，事件早退把绳一起跳过
（1.20.1 的 `MobRenderer.render` 在 super 之后仍画原版绳，但用原版 `getLeashOffset`，
与基岩版模型绑点不一致）。

**修复**（qylEyelib）：
- 新增 `client/render/LeashRenderer`，在 `EntityRenderOrchestrator.renderComponents` 末尾、
  每实体每帧调用一次。
- 锚点按 Bedrock 约定优先取模型 `lead` locator（`ModelPoseTransforms.resolveLocatorPose` +
  basePose 逆变换到世界轴位移）；模型无 `lead` 时回落原版 `getLeashOffset` + 体旋转算法。
- `<26.1` 用 `RenderType.leash()` 手写 24 段绳；26.1 构造 `EntityRenderState.LeashState`
  后调 `submitLeash`。
- 1.20.1 新增 `MobRendererLeashMixin`，仅抑制被 `LeashSuppression` 标记实体的原版绳
  （标记由 `<26.1` 适配器在事件 cancel 后写入），避免非 doki 实体丢绳、也避免双绳。

**验证**：三版本把羊驼拴到栅栏 knot，绳可见、单根、无原版双线；端点在模型
`rightitem` 的 `lead` locator（offset `[0.1875, 1.125, 0]`）处（1.21.1 近景
`work/evidence_20261107/leash_1211_close{,_zoom}.png` 可见绳从该点连到 knot；
26.1.2 见 `work/evidence_20261107/leash_2612_close.png`）。1.20.1 另留
`work/leash_1201_{vanilla_,}zoom.png` 对照（原版偏移 vs 本修复）与
`work/evidence_20261107/leash_1201_a.png`；把 `LeashSuppression` 移入
`bridge/client/render/adapter` 后重装 jar 复测：
`work/evidence_20261107/leash_1201_pkgmove.png`（仍为单绳，mixin 抑制生效）。

### 行商羊驼与普通羊驼的区分（同模型同底图，只有第二层装饰）

**素材核对**（对照 Mojang bedrock-samples）：两者共用 `geometry.llama` 与同一组
`llama_{creamy,white,brown,gray}` 底图，没有独立模型；区分只靠 render controller 第二层
`(query.mark_variant == 1 && variable.DecorTextureIndex == 0) ? Texture.decor_wandering_trader
: Array.decor[variable.DecorTextureIndex]`。`work/llama_tex/` 下的 `trader_llama_decor.png`
即基岩版商队装饰贴图。JE 侧 `TempVariantSetting` 已为 `TraderLlama` 写 `mark_variant=1`。

**标准实现（按 Bedrock 语义补齐 eyelib 能力，不再有 `setupLlamaDecor`）**：

1. **引擎变量**：`variable.DecorTextureIndex` 是 Bedrock 引擎按羊驼装备提供的内置 entity
   variable（官方 RC 直接引用，资源包不写）。eyelib 新增
   `EngineEntityVariable` 枚举（application）+ `RenderSystemPort#engineVariable(entity, variable)`
   （宿主解析，`NaN` = 不适用）+ `EngineEntityVariables` 统一绑定：惰性 supplier，读时取当前值，
   包内脚本已写同名变量时不覆盖。`ClientEntitySetup` 只调用
   `EngineEntityVariables.bind(entity, scope)`；JE `Llama` 包名/装备槽差异留在 bridge adapter。
2. **相等语义**：编译产物的 `==`/`!=` 走 `MolangObject.equalsF`，旧实现用对象 `equals`，
   惰性供应商变量与字面量 `0` 判为不等（实测 `variable.DecorTextureIndex == 0` 恒 false）。
   改为数值类型之间按 `asFloat` 比较（官方「all numerical values are floats」），
   `nEqualsF` 由 `equalsF` 取反。
3. **变量名大小写**：官方 Molang「除字符串外大小写不敏感」；`MolangScope` 与 `MolangStruct`
   统一规范化名字（无大写时零分配），`variable.DecorTextureIndex` 与
   `variable.decortextureindex` 等价，不再双写。
4. **材质**：官方 `entity/llama.entity.json` 声明 `materials.default = "llama"`，官方 RC 输出
   3 层（base / decor / decor_none）→ `llama` 就是 masked-multitexture 材质。eyelib 内置
   `bedrock_entity.material` 的 `"llama:entity_alphatest"` 补 `"+defines": ["MASKED_MULTITEXTURE"]`；
   cospack 两实体恢复 `materials.default = "llama"`，删除临时 `doki_llama_multitexture`。

**验证**：三版本运行时 `TraderLlama` 的 `modelComponents=3`，第二层为
`decor/trader_llama_decor.png`，普通羊驼第二层为 `decor_none.png`（全透明）；
1.20.1 用**小写** `variable.decortextureindex` 读回 `0.0`，证明大小写不敏感生效。
截图 `work/evidence_20261107/llama_std_{2612,1211,1201}.png`（行商羊驼均显示蓝金商队装）。
回归（标准实现落地后三版本实机）：眨眼开/闭对照
`work/evidence_20261107/blink_1211_std_pair.png`、`blink_1201_std_pair.png`，
26.1.2 闭合帧 `blink_std_2612.png`；受伤红闪 `hurt_std_2612.png`、
`hurt_1211_std.png`、`hurt_1201_std.png`；栓绳在装饰截图同帧可见（单绳）。

**口径坑（本次踩到）**：两处缓存会让「源码已改、实机还是旧行为」：
- dokimobs 资源（`pack.cospack`/`doki.material`/bbmodel）必须重跑该节点 build，否则 run
  目录仍用旧资源。26.1.2 首次实机时 `build/resources/main/.../doki.material` 比源文件早 8
  分钟、不含新材质，表现为「装饰丢失」。判定：比对 `versions/<node>/build/resources/main/...`
  与 `src/main/resources/...` 的内容，或运行时查 `modelComponents` 的 `renderType`。
- eyelib 以 `21.1.31` 固定版本发布到 mavenLocal；Gradle/MDG 会缓存该 jar 的 transform
  产物。重发布后必须重跑 `mcmcp_build`/`copyEyelibToMods`（1.21.1 的 `run/mods` 还要复检
  md5），否则客户端仍加载旧 class（本次表现为新变量未绑定、`==` 仍用旧语义）。

### 验证与静态检查汇总

- `molangCheck`：`:1.20.1` / `:1.21.1` / `:26.1.2` 均 `683 expressions, 0 error(s)`
  （注意必须传 `-PmolangTarget=<repo>/src/main/resources/assets/dokimobs`，默认相对路径在
  子节点下不解析；此坑已记账）。
- NullAway（qylEyelib）：三节点错误与 HEAD 基线一致——1.20.1 / 1.21.1 各 3 条
  `ClientEntitySetup:95/96/115`；26.1.2 额外 `IrisPipelineCompat:38`，共 4 条，无新增。
- `:test`（qylEyelib）与基线一致：1.20.1 1734 tests / 1 failed（`StonecutterCommentPlacementTest`），
  1.21.1 1736 / 1 failed（同），26.1.2 1736 / 3 failed（再 +`BrRenderStateLightingModelTest`、
  `BrRenderStateSpecTest`）。本轮新增的 `LeashSuppression` 最初放在
  `bridge.client.render`，触发 `ArchitectureTest.aclPublicApiMustBeInterfaceOrAnnotation`
  （ADR-0018 I-5：bridge 只暴露接口，实现放 `adapter/` 子包）；移入
  `bridge.client.render.adapter` 后该类测试恢复通过。`EngineEntityVariable` 放在 bridge
  又触发 `applicationMustNotDependOnBridgeConcreteClasses`，最终放到 `molang`（bridge 可依赖
  application 类型），复测通过，仅剩上述基线失败。新增测试
  `MolangEqualityTest`（数值跨惰性供应商按值相等）与 `MolangScopeTest` 三条大小写不敏感用例。
- 帧捕获口径：2 个 tick 的眨眼用「eval 逐次截图」必然漏拍。有效做法是在
  `ClientTickEvent.Post` 注册监听，每 tick 截图并把当帧状态值写进文件名
  （`f_061_lid+0.00.png`、`h_06_ht06.png`），事后按文件名挑帧；截图回调在渲染线程，
  仍属游戏真实渲染输出，不是自建代理。
- 证据时效：四项功能在「标准实现」落地后重新跑过三版本实机（26.1.2 12:37、1.21.1 12:39、
  1.20.1 12:41 启动的客户端，资源与 mavenLocal jar 均为最终版）；此前 16:56–17:44 的一轮
  证据保留作历史对照（旧材质/双写变量方案）。26.1.2 的标准实现回归截图
  `blink_std_2612.png`、`hurt_std_2612.png`、`llama_std_2612.png`。
- 未覆盖：26.1.2 只验了「拴栅栏 knot」链路，未验玩家手持拴绳的 holder 位移路径；
  1.20.1 的双绳抑制只对照了一头被拴实体，未单独构造「同帧存在 doki 与非 doki 被拴实体」
  的组合场景。

## 2026-11-08 装备挂载（手持物 + 盔甲）

**用户需求**：以基岩版为基准检查 attachment 是否正确附加到手部（入口 = 原生能持有工具的僵尸），
同时查盔甲；解决抽象而非表象。

真源是 qylEyelib 的挂载实现（ADR-0043），dokimobs 侧不改数据：

- **手持物**：挂点 = `q.item_slot_to_bone_name` 对应的 item 骨骼（`rightitem`/`leftitem`）。修复前
  `HandItemRenderer` 在骨骼帧上再叠 `Rx(-90)·Ry(180)` + 硬编码 `translate(-0.25,0.1,-1.15)`
  （该平移在骨骼基下放大到约 1 格），剑飘在头肩附近。现在只剩 `Rx(-90)`：基岩版模型空间相对
  JE 实体模型空间是绕 Z 轴 180°，代入 vanilla `ItemInHandLayer` 的 `Rx(-90)·Ry(180)` 后
  `Ry(180)` 被抵消。
- **盔甲**：萌化实体此前完全没有盔甲——`RenderLivingEvent` 被 cancel 后原版
  `HumanoidArmorLayer` 不执行，而 eyelib 只 tick 盔甲槽从不渲染。现在 vanilla 人形盔甲部件按
  body-part 骨骼名绑定（`head`/`body`/`rightarm`/`leftarm`/`rightleg`/`leftleg`），空间换算
  `scale(-1,-1,1)`，pass = cutout + 禁剔除。
- **26.1 手持物完全不可见**：挂载物原先渲染在几何 writer 内，26.1 的 `SubmitNodeCollector`
  在 `renderAllFeatures` 阶段不再接收新节点 → 物品几何被丢弃。现在挂载物在模型几何**提交之前**
  渲染，骨骼帧由 `ModelPoseTransforms.resolveBonePose`（模型 + 本帧动画采样 + 模型根位姿）现算。
  同时去掉 `extraRender` 钩子（按渲染组件重复调用 → 多组件实体重复画手持物）。
- **doki 模型的挂点现状**：`geometry.zombie_anim` 只有 `rightitem`（locator `lead_hold`），
  没有 `leftitem` → 副手物品不显示，与基岩版「无骨骼不挂载」一致，不做原点兜底。
  body-part 骨骼齐全（`head`/`body`/`rightarm`/`leftarm`/`rightleg`/`leftleg`），因此盔甲可直接绑定。
- **姿态随机性坑**：zombie 的 `scripts.initialize` 里 `variable.pose = math.random_integer(0,7)`，
  每个实例/每次重载的手臂姿态不同。跨会话比对截图会把「姿态不同」误判为「挂载方向改了」；
  判定要在同一实例、同一帧内和原版渲染（`doki=false`）对照。

**验证**（三节点实机，铁剑 + 全套铁甲 + 屋顶遮阳 + `NoAI`）：

| 节点 | 结果 | 截图 |
|------|------|------|
| 1.20.1 | 剑在拳内，盔甲四件全部绑定且随抬臂一起动 | `versions/1.20.1/run/debug_shots/screenshots/attach_1201_ship.png`、`attach_armor_doki.png`、`attach_swing1.png`、`attach_offhand_missing.png` |
| 1.21.1 | 同上 | `versions/1.21.1/run/debug_shots/screenshots/attach_1211_ship.png` |
| 26.1.2 | 同上；修复前同场景无剑（`attach_2612.png`） | `versions/26.1.2/run/debug_shots/screenshots/attach_2612_ship.png` |

静态核对：`resolveBonePose` 与手写骨骼链（root→waist→body→rightarm→righthandxia→rightitem）
矩阵逐元素同值。未覆盖：盔甲 trim/glint、`hide_armor`、`BODY` 槽（狼铠）、自定义 `binding`
（Molang）与基岩版 ±24px 绑定偏移——见 ADR-0043 §3。

## 2026-11-09 掠夺者旗帜/头部/弩、盔甲锚点、CarryOn、闪电苦力怕

五项用户反馈，根因全部定位后在 eyelib 21.1.33（三节点重新发布）与 dokimobs 资源侧修复；
最终验证为 1.20.1 / 1.21.1 / 26.1.2 三节点实机截图，不是单节点推断。

### ① 掠夺者不祥旗帜不可见 → eyelib 新增 head 槽物品渲染

- **根因**：掠夺者的不祥旗帜是 `Raid` 装备到 `EquipmentSlot.HEAD` 的普通物品；原版由
  `CustomHeadLayer` 画。eyelib 取消 `LivingEntityRenderer` 后，`ArmorAttachmentRenderer` 只处理
  `ArmorItem`，HEAD 槽非盔甲物品没有任何渲染路径。
- **修复**：eyelib 新增 `client/render/HeadItemRenderer`，在 `renderAttachments` 中先于盔甲/手持物
  调用。变换与 vanilla 完全一致：head 骨骼帧 → pivot → `scale(-1,-1,1)`（JE→Bedrock 模型空间）
  → vanilla `CustomHeadLayer.translateToHead`（`(0,-0.25,0)`、Y 180°、`0.625` 缩放）→
  `renderItemDirect(..., ItemDisplayContext.HEAD, ...)`。盔甲物品跳过，避免与
  `ArmorAttachmentRenderer` 双画。
- **实机**：doki 掠夺者 HEAD 白旗现在显示为头顶上方的大白板，与同场景 `doki=false` 的 vanilla
  掠夺者一致；1.20.1/1.21.1/26.1.2 均验证。
  截图：`versions/26.1.2/run/debug_shots/screenshots/pillager_after_fix_wide_2612.png`、
  `banner_ref_2612.png`、`versions/1.20.1/run/debug_shots/screenshots/pillager_1201_controlled.png`、
  `versions/1.21.1/run/debug_shots/screenshots/fix_1211_scene.png`。

### ② 掠夺者弩模型常显 → 按主手是否持弩显隐

- **根因**：`pillager.bbmodel` 把弩烘焙在右前臂的 `bone19`→`bone28` 子树里，渲染控制器没有任何
  `part_visibility`，因此即使主手为空也一直显示。
- **修复**：`DokiMolangQueries` 新增 `query.doki_holding_crossbow`（`getMainHandItem().getItem()
  instanceof CrossbowItem`，兼容自定义弩）；`controller.render.doki_pillager` 对 `bone19`…`bone28`
  **逐个**骨骼绑定该条件。注意 `DFSModel.PreBoneFrame` 不继承父骨骼隐藏，只写 `bone19` 会让
  子骨骼继续画；`bone2/12/17/18` 属于头发/眉毛，不能误伤。
- **实机**：主手空 → 弩隐藏；主手 crossbow → 弩显示；换成弩后下一帧立即出现（part_visibility
  逐帧求值）。1.20.1 与 26.1.2 已验证，1.21.1 同资源。
  截图：`pillager_after_fix_wide_2612.png`、`pillager_1201_controlled.png`。

### ③ 掠夺者头/身体未分离、无独立转动 → 新增 look_at_target 剪辑

- **根因**：`pillager.json` 没有绑定任何 `look_at_target`；bbmodel 的骨骼是
  `Root>AllBody>UpBody>AllHead(pivot 28.05)>Head(pivot 29.5)`，待机/行走剪辑用
  AllHead+Head 旋转抵消 UpBody 的 +48.9° 侧转，所以头部从不随视线独立转动。
- **修复**：新增 `assets/dokimobs/animations/doki_pillager.json`（`BrAnimationLoader` 加载
  `animations/*.json`），剪辑 `animation.doki_pillager.look_at_target` 只驱动 **AllHead**（颈部），
  使用 `query.target_x_rotation` / `query.target_y_rotation`；实体 JSON 加短名并在
  `scripts.animate` 中权重 1.0。共享的 `animation.mdsj_zombie.look_at_target.default` 只驱动
  `head`，在本模型上会转内部 Head（枢轴在头内），不能用。
- **实机**：设置 `YHeadRot=135°` 而 `YBodyRot=180°`，头部相对身体独立转动；1.20.1 与 26.1.2
  截图可见脸/发朝向改变。截图：`pillager_headturn_2612.png`、`pillager_headturn_1201.png`。

### ④ 部分生物盔甲偏移 → body 部件锚点改为颈部

- **根因**：`ArmorAttachmentRenderer` 用持有者同名骨骼 pivot 作为盔甲部件原点。vanilla 人形盔甲
  的 `body` 部件原点在**颈部**；而 cospack 中 skeleton/villager/zombie_villager/husk/drowned 的
  `body` 骨骼 pivot 在**腰部**（例：skeleton head=1.889 vs body=1.114；villager 1.8/1.0625），
  胸甲/护腿整体下移约 0.6~0.8 格；zombie 的 body pivot=1.875=head pivot，所以此前未暴露。
- **修复**：对 `piece.part()=="body"`，仍在 **body 骨骼帧**内（继承躯干动画），但把平移锚点换成
  该模型 `head` 骨骼 pivot（颈部）；无 head 骨骼时回退 body pivot。`head`/`arm`/`leg` 部件不变。
- **实机**：skeleton 全套铁甲修复前是漂浮白条+腰位胸甲，修复后胸甲回到躯干、头盔回到头；
  zombie control 不变。1.20.1 / 26.1.2 截图：
  `armor_skeleton_close_2612.png`（修复前） vs `armor_skeleton_after_2612.png`（修复后）、
  `armor_skeleton_1201.png`、`armor_zombie_after_2612.png`。

### ⑤ CarryOn 抱起萌化生物 → 26.1 off-level 实体 render state 映射

- **根因**：26.1 的 `RenderLivingEvent` 不再携带实体，`RenderLivingEventAdapter` 只能按
  类型+坐标在 `level.entitiesForRendering()` 中反查。CarryOn `CarriedObjectRender.drawEntity`
  用 `EntityType.create(NBT)` 造一个**不加入世界**的假实体，再 `extractEntity` + `submit`，
  所以反查永远失败，doki 渲染不接管 → 抱起后显示 vanilla 模型（实测为一只 vanilla 牛）。
- **修复**：新增 26.1 专用 `EntityRenderDispatcherMixin`，在 `extractEntity` RETURN 处把
  **不在 level 中**（`level.getEntity(id) != entity`）的活体 render state → 实体写入弱引用表
  `ExtractedEntityStates`；`RenderLivingEventAdapter` 先查直接映射，再走坐标反查。
  `extractEntity` 每次 `createRenderState` 都返回新对象，state 身份可安全作 key。
- **实机**：26.1.2 装入 CarryOn 2.10.0，服务端 `tryPickupEntity` 返回 true；第三人称正面截图
  显示抱着的是一只 **doki 牛**（有角、chibi 比例），不再是 vanilla 牛。
  截图：`carryon_cow_front_after_2612.png`（修复后） vs `carryon_cow_2612.png`（修复前）。
  **验证口径**：本轮走的是服务端 `tryPickupEntity` 直调 + 客户端渲染；未在自动化环境里模拟
  客户端按键+右击的完整输入链路。

### ⑥ 闪电苦力怕 charged 层不显示 → 26.1 `query.is_powered` 写死 false

- **根因**：`EntityPortAdapter.isPowered` 的 `>=26.1` 分支直接 `return false`（`PowerableMob` 在
  26.1 被移除）。运行时探针：`Creeper.isPowered()=true`，但编译求值
  `query.is_powered`=0.0，基础 RC 的 `Array.creeperskin[query.is_powered]` 与 armor RC 的
  `part_visibility:*` 都走 false → charged 层完全不显示。
- **修复**：26.1 分支改为 `entity instanceof Creeper c && c.isPowered()`。同时给
  `controller.render.mdsj_creeper_armor` 加 `ignore_lighting:true`（官方 Bedrock armor RC
  还带 `uv_anim`/`light_color_multiplier`；eyelib 当前不解析 uv_anim，本轮未实现滚动）。
- **实机**：26.1.2 召唤 `{powered:1b}` 苦力怕，蓝色 charged 层现在显示在头/身/腿；普通苦力怕
  无该层；1.20.1 / 1.21.1 同样显示。截图：
  `creeper_charged_baseline_2612.png`（修复前无层）、`creeper_charged_ignorelight_2612.png`、
  `versions/1.21.1/run/debug_shots/screenshots/creeper_charged_1211.png`、
  `versions/1.20.1/run/debug_shots/screenshots/creeper_charged_1201.png`。

### 本轮验证与未覆盖

- `molangCheck`：1.20.1 / 1.21.1 / 26.1.2 均 **686 expressions, 0 error(s)**。
- qylEyelib 最终 `:test`：1.20.1 / 1.21.1 / 26.1.2 全部 **BUILD SUCCESSFUL**（`StonecutterCommentPlacementTest`
  通过：head 槽物品的版本判断已收敛到 `EntityRenderPorts.isArmorItem`，client/render 不再出现 `//?`）。
  `:nullawayMain` 三节点全部 BUILD SUCCESSFUL。
- dokimobs `assemble` 三节点成功；jar 内已包含 `assets/dokimobs/animations/doki_pillager.json` 与
  cospack 的 `ignore_lighting:true`（`versions/<node>/build/libs/dokimobs-0.3.12+...jar`）。
- 未覆盖：CarryOn 客户端键位+右击的原始输入（已走服务端 `Player#interactOn` 事件链 +
  `tryPickupEntity` + 渲染三层验证，`interact=Success, carry=true`）；charged 层 `uv_anim` 纹理滚动
  （eyelib 尚不支持）；1.21.1 盔甲仅场景截图（1.20.1 与 26.1.2 分别覆盖 legacy / modern 两条
  ArmorRenderAdapter 分支）。

## 2026-11-09 追记：溺尸盔甲「附加不正确」＝ vanilla 人形盔甲几何与萌化比例不符

**用户要求**：以溺尸复核萌化生物的盔甲 attachable 附加不正确。

**结论**：挂点（骨骼关节）没错，错的是几何来源。萌化实体的盔甲从不走 attachable：
`ArmorAttachmentRenderer` 把 vanilla JE 人形盔甲部件按名绑到持有者骨骼关节上，几何尺寸恒为 vanilla
玩家（外甲每面 +1px、护腿内甲 +0.5px、头盔帽层再 +0.5px，见 `LayerDefinitions` 的
`OUTER/INNER_ARMOR_DEFORMATION`）。萌化模型是 chibi 比例，于是有甲/无甲 A/B 可见：**甲片埋进躯干、
袖甲比手臂粗长约 2 倍、两条腿甲并成一柱并低于脚面、盔顶高出头顶 0.156 格**。

**实机证据（1.20.1 / 0.3.13 构建，同机位同实例，NoAI + 铁套装，夜视下拍摄）**
`versions/1.20.1/run/debug_shots/screenshots/drowned_nv_noarmor`（无甲） vs `drowned_nv_armor`（有甲）。
形状之外还有一条旁证：正午无甲时躯干着火，装上甲后火焰被甲片遮住 → 甲片确实渲染且覆盖躯干，
问题不是「没画」。本轮只实机 1.20.1；1.21.1 / 26.1.2 未复测（失败机理与版本无关，版本分支只差
纹理与部件集合）。

**数值（`geometry.drowned2`，模型空间，格；关节 head 1.809 / body 1.125 / arm 1.672 / leg 1.0625）**

| 部件 | 萌化模型 AABB（相对关节） | 盔甲几何 AABB | 结果 |
|---|---|---|---|
| 头（HEAD = head+hat） | x ±0.36，y -0.064..+0.438，z -0.286..+0.233 | x ±0.344，y -0.094..+0.594，z ±0.344 | 盔顶高出头 0.156；头比盔宽 0.016/侧 |
| 躯干（CHEST body，锚在 head pivot） | x -0.354..+0.453，y -0.13..+0.748，z -0.244..+0.393 | x ±0.313，y -0.813..+0.063 | 高度贴躯干（颈部锚点所致），横向窄 0.09~0.14、进深窄 0.13 → 埋在躯干内，项链/衣领布在甲片之前 |
| 右臂（CHEST right_arm） | 0.26 × 0.45 × 0.28 | 0.375 × 0.875 × 0.375 | 粗 1.4×、长 1.9×；甲尖低于手掌约 0.30 |
| 右腿（LEGS right_leg） | 0.234 × 0.5 × 0.272 | 0.375 × 0.75 × 0.375 | 两片在 x=0 相接成单柱；甲底低于脚面 0.31 |

**同一次复核确认的三条结构性缺口**

1. 所有萌化实体 json 都没有 `enable_attachables`，`BrClientEntity` 的 codec 默认 `false`
   （`BrClientEntity.java:181`）→ `AttachableResolver.isAttachableEnabled` 对萌化 holder 恒 false。
   运行态核对：`minecraft:drowned ce=minecraft:drowned enable_attachables=false`。因此盔甲/手持
   attachable 在萌化实体上完全不参与；`ArmorAttachmentRenderer` 也不查 attachable，`binding` Molang
   无求值点。
2. 非萌化路径 `HumanoidArmorLayerMixin` 把**整件** attachable 挂在单个 `ModelPart`
   （CHEST→body、LEGS/FEET→rightLeg）→ 多骨骼 attachable（胸甲=body+双臂、护腿=body+双腿）刚性跟随一根骨。
3. 骨骼名硬编码 head/body/rightarm/leftarm/rightleg/leftleg。运行态 `geometry.doki_pillager`
   bones=67、`body=false`（只有 allbody/upbody）；wandering_trader/wolf/blaze 同样无 `body`，
   guardian 无 `head` → 这些生物的胸甲躯干与护腿躯干整件不画。

**用户决策（2026-11-10）**：选 (a) 走 Bedrock attachable 语义——按 attachable 定义取几何 + 逐骨骼绑定 +
`binding`，萌化实体开 `enable_attachables`；**本轮范围 = 只打通管线，盔甲外观维持现状**（本仓库/cospack
没有任何盔甲 attachable 定义，故原版甲仍走 vanilla 人形盔甲回退）。落地记录见下一节。

## 2026-11-10 盔甲槽 attachable 管线打通（eyelib 21.1.35 + dokimobs 0.3.13）

**eyelib 侧**（ADR-0043 §5.5 有决策细节）：

- `AttachableItemRenderSetup.renderAttachableBound`：盔甲槽 attachable 按基岩语义逐骨骼绑定渲染。锚点规则：
  骨骼 `binding` Molang 求值出持有者骨骼名 → 用该骨骼；否则骨骼名与持有者同名（`head`/`body`/`rightarm`…）
  → 用同名骨骼；都没有 → 持有者模型根位姿（基岩模型空间）。骨骼帧 = 持有者骨骼帧 × `T(持有者 pivot)` ×
  `T(-挂载物 pivot)`，即挂载物骨骼 pivot 对齐到持有者骨骼 pivot。
- `EntityRenderOrchestrator.renderSlotAttachables`：HEAD/CHEST/LEGS/FEET 先查 `AttachableResolver`，
  命中则由 attachable 接管该槽，`HeadItemRenderer`（头部物品）与 `ArmorAttachmentRenderer`（vanilla 人形甲）
  跳过该槽，避免双画。
- 每个顶层骨骼单独渲染，用 `part_visibility` 逐骨骼关掉其它子树（`PreBoneFrame` 不继承父骨骼隐藏）。
- `ModelPoseTransforms.bedrockRootPose` 提供「持有者根位姿 → 基岩模型空间」换算。
- `binding` 求值抽出 `AttachableItemRenderSetup.bindingTargetName`，clientsmoke 断言它按当前
  `context.item_slot` 求值（夹具 `q.item_slot_to_bone_name(context.item_slot)` → `rightitem`）。

**dokimobs 侧**：22 个 cospack 条目 + 5 个 bbmodel 实体（`assets/dokimobs/eyelib/entity/*.json`）全部
`enable_attachables: true`（脚本 `work/pack_patch_enable_attachables_20261109.py`）；`eyelib_version`
升到 `21.1.35`。

**验证（三节点）**

| 节点 | 网格 | 结果 | 证据 |
|---|---|---|---|
| 1.20.1 | 探针 attachable：HEAD 槽木棍 → 几何 `binding: "'head'"`、CHEST 槽打火石 → 骨骼名 `body`（同名绑定）；同时穿铁护腿/铁靴（回退路径） | 头顶 6px 方块（木棍贴图）+ 胸口方块出现在正确位置；铁甲正常；`binding''head'' -> head`、`stick → minecraft:stick`、`flint → minecraft:flint`、铁甲 `attachable=null` | `versions/1.20.1/run/debug_shots/screenshots/attachable_probe_front2` |
| 1.21.1 | 同一探针（4px 方块 + 红石贴图便于观察） | 头顶方块可见；铁护腿/靴正常 | `versions/1.21.1/run/debug_shots/screenshots/attachable_probe_1211_red2` |
| 26.1.2 | 同一探针 | 头顶方块可见；铁护腿/靴正常 | `versions/26.1.2/run/debug_shots/screenshots/attachable_probe_2612.png` |

其余闸门：eyelib 三节点 `compileJava` / `:test` / `:nullawayMain` 全绿（`GATE_OK`）；eyelib clientsmoke
1.20.1 **12/12 passed**（含新断言 `bone root binding -> rightitem`）；dokimobs `assemble` 三节点成功，
最终产物不含探针资源（`assets/probe` 已删除）。

**运行时发现（未修，记反馈）**：1.21.1 上把 6px 立方体的贴图换成 16x16 后，重载时
`ModelBakeInfo.pixelAnyMatch` 读取越界像素 `(17,6) outside of image bounds (16,16)` 抛
`IllegalArgumentException` 使客户端崩溃（4px 立方体正常）。这是几何 UV 越界时烘焙缺边界检查的既有缺陷，
与本轮挂载管线无关。

**未覆盖**：真实盔甲 attachable 定义（仓库与 cospack 都没有；本轮的探针已删）；`BODY` 槽（狼铠）；
Bedrock 的 ±24px 绑定偏移；非萌化实体仍走 `HumanoidArmorLayerMixin`（整件 attachable 挂单个 ModelPart）。
