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
- **缺失引用安全降级**：控制器状态引用未映射的短名 → resolveAnimation 返回 null → 该状态视为播完，
  不崩溃不刷屏。

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
