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
- **bbmodel 动画坐标约定（21.1.16 修正）**：bbmodel 存的是 Blockbench 显示空间值（Blockbench
  基岩导出器在导出边界无条件 invertMolang：position.x、rotation.x/y，见 blockbench 源码
  bedrock_animation.js getKeyframeDataPoints）。eyelib BbModelAnimations 在转换期做同样取反，
  注册表内剪辑统一为基岩剪辑空间，渲染期 flipAnimation=true（geo/bbmodel 模型均为 true）补偿一次
  即还原编辑器姿态。eyelib 9754bbea 曾改为"透传+渲染期补偿"，实机姿态镜像（守卫者三叉戟换手），
  21.1.16 已回退该错误并补 molang 表达式 `-(...)` 包裹取反。21.1.15 直载管线渲染的 bbmodel 动画均为镜像。
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
