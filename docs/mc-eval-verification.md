# 游戏内验证（mcmcp / AIDebugServer）踩坑

三版本客户端都用 `.mcmcp` → `.mcmcp-run.cmd {port} <ver>` 启动，`/eval` 执行 Java 方法体，
注入 `minecraft` / `player` / `level`。下列事实均在 2026-11-03 实机观察到。

## 线程：`/eval` 里 `Thread.sleep` 会冻住客户端

`/eval` 跑在渲染线程上。在 eval 里 `Thread.sleep(...)` 循环采样时，客户端 tick 与粒子 tick
全部停摆：

- `minecraft.level.addParticle(...)` 加入的粒子会一直留在 `particlesToAdd` 队列里不消失；
- 服务端下发的粒子看起来"没到"，实际是客户端从未 tick。

采样必须放进后台线程（`new Thread(...)`，`setDaemon(true)` 后立刻 `start()`），eval 立即返回，
再用文件（run 目录下的相对路径）回读结果。**服务端状态**（如自己类的静态列表）不受此影响，
可以在 eval 里同步读。

## 死亡界面会锁死调试端点

玩家处于 `DeathScreen` 时，`/eval` 与 `/command` 都返回 `state=idle` 拒绝执行，但 `/loaded`
仍报告 `inWorld:true`。此时只能：

1. 若还能 eval —— `minecraft.player.respawn()`；
2. 否则关闭客户端重启，再 `mcmcp_enter_world` 进世界（存档里玩家血量为 0，会立刻又出现死亡界面，
   重启后仍要再 `respawn()` 一次）。

测试世界里先 `/difficulty peaceful` + `/gamerule doMobSpawning false`，否则挂机期间会被
史莱姆打死（实测两次，掉落物丢失）。

## 交互链路必须走真实数据包

`minecraft.gameMode.interact(player, entity, hand)`（1.20.1/1.21.1）或
`(player, entity, EntityHitResult, hand)`（26.1）会发包给服务端，等价于玩家右键。
两条前置条件：

- 服务端手上的物品必须一致 —— 客户端 `setItemInHand` **不会**同步到服务端，
  用 `/item replace entity @p weapon.mainhand with <item> <n>`；
- 距离要在服务端允许的交互范围内（实测 3.5 格被服务端丢弃，1.5 格通过）。

## 版本 API 差异（26.1.2）

| 1.21.1 | 26.1.2 |
|---|---|
| `net.minecraft.resources.ResourceLocation` | `net.minecraft.resources.Identifier` |
| `BuiltInRegistries.X.get(rl)` 返回对象 | 返回 `Optional<Holder.Reference<X>>`；要对象用 `getValue(rl)` |
| `net.minecraft.world.entity.animal.Cow` | `net.minecraft.world.entity.animal.cow.Cow` |
| `Recipe#getResultItem(RegistryAccess)` | 无此方法；结果走 `Recipe#display()` 的 `RecipeDisplay` |
| `ParticleEngine#iterateParticles(Consumer)` | 无；改反射读私有 `particles` / `particlesToAdd` |
| `gameMode.interact(p, e, hand)` | `gameMode.interact(p, e, EntityHitResult, hand)` |

26.1.2 下 `EntityType.COW.create(level)` 在 eval 里会抛 `ConcurrentModificationException`，
改用 `/summon` 命令创建实体。

## 其它

- 26.1.2 的 `ParticleEngine#countParticles()` 恒返回 `0`，不能当观测手段。
- eval 有多轮迭代时优先一次编译通过：`var x = () -> ...` 无法推断（要显式函数式接口），
  lambda 捕获的局部变量必须是 effectively final（先 `final var copy = found;`）。
