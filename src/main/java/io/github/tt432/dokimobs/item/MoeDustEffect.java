package io.github.tt432.dokimobs.item;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
//? if legacy {
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
//?} else {
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
//?}
import net.minecraft.core.particles.DustParticleOptions;
//? if !modern {
import org.joml.Vector3f;
//?}

import java.util.ArrayList;
import java.util.List;

/**
 * 萌化尘的粉色粒子动画（纯服务端驱动：逐 tick 发粒子包，客户端无需安装任何东西）。
 * <p>
 * 动画设计（{@value #DURATION} tick = 2 秒）：
 * <ol>
 *   <li>四条手臂的螺旋：半径从 1.15 格收缩到 0.3 格，高度同时从脚下升到头顶，
 *       视觉上像粉色尘雾「卷」进生物身体；</li>
 *   <li>偶数 tick 再补一颗淡粉色外圈粒子，绕实体反向缓转，制造包裹感与层次；</li>
 *   <li>每 8 tick 在头部随机方位冒一颗心形粒子；</li>
 *   <li>最后一 tick 在头部炸开一圈心形粒子作为完成反馈。</li>
 * </ol>
 * 动画只在服务端列表里登记，实体被移除 / 换维度（{@code level} 不再一致）时立即丢弃。
 *
 * @author TT432
 */
public final class MoeDustEffect {
    /**
     * 动画时长（tick）。
     */
    private static final int DURATION = 40;

    /**
     * 螺旋手臂数量。
     */
    private static final int ARMS = 4;

    /**
     * 深粉（主螺旋）。
     */
    //? if modern {
    private static final ParticleOptions HOT_PINK = new DustParticleOptions(0xFF6FB8, 1.1F);
    //?} else {
    private static final ParticleOptions HOT_PINK = new DustParticleOptions(new Vector3f(1.0F, 0.435F, 0.722F), 1.1F);
    //?}

    /**
     * 淡粉（外圈）。
     */
    //? if modern {
    private static final ParticleOptions LIGHT_PINK = new DustParticleOptions(0xFFC2E2, 1.7F);
    //?} else {
    private static final ParticleOptions LIGHT_PINK = new DustParticleOptions(new Vector3f(1.0F, 0.761F, 0.886F), 1.7F);
    //?}

    private static final List<Spiral> ACTIVE = new ArrayList<>();

    private MoeDustEffect() {
    }

    /**
     * 开始一段动画。只在服务端调用。
     */
    public static void play(ServerLevel level, LivingEntity entity) {
        ACTIVE.add(new Spiral(level, entity));
    }

    /**
     * 推进所有进行中的动画。每服务端 tick 末尾调用一次。
     */
    private static void tickAll() {
        if (ACTIVE.isEmpty()) {
            return;
        }

        // tick() 返回「是否继续存活」，反向作为移除条件
        ACTIVE.removeIf(spiral -> !spiral.tick());
    }

    private static final class Spiral {
        private final ServerLevel level;
        private final LivingEntity entity;
        private int age;

        private Spiral(ServerLevel level, LivingEntity entity) {
            this.level = level;
            this.entity = entity;
        }

        /**
         * 发一帧粒子。
         *
         * @return 动画是否继续
         */
        private boolean tick() {
            if (entity.isRemoved() || entity.level() != level) {
                return false;
            }

            int t = age++;

            if (t >= DURATION) {
                return false;
            }

            float progress = t / (float) DURATION;
            double height = entity.getBbHeight();
            double radius = 1.15 - 0.85 * progress;
            double centerY = entity.getY() + height * (0.1 + 0.8 * progress);
            double spin = t * 0.55;

            for (int i = 0; i < ARMS; i++) {
                double angle = spin + i * (Math.PI * 2 / ARMS);
                double x = entity.getX() + Math.cos(angle) * radius;
                double z = entity.getZ() + Math.sin(angle) * radius;
                // 手臂本身上下起伏，让螺旋看起来是「绕着」而不是「贴着」
                double y = centerY + 0.12 * Math.sin(angle * 2 + t * 0.3);
                level.sendParticles(HOT_PINK, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
            }

            if ((t & 1) == 0) {
                double angle = -t * 0.4;
                double outer = radius + 0.35;
                level.sendParticles(LIGHT_PINK,
                        entity.getX() + Math.cos(angle) * outer,
                        entity.getY() + height * 0.5,
                        entity.getZ() + Math.sin(angle) * outer,
                        1, 0.0, 0.0, 0.0, 0.0);
            }

            if (t % 8 == 0) {
                double angle = t * 1.7;
                level.sendParticles(ParticleTypes.HEART,
                        entity.getX() + Math.cos(angle) * radius * 0.9,
                        entity.getY() + height + 0.15,
                        entity.getZ() + Math.sin(angle) * radius * 0.9,
                        1, 0.05, 0.05, 0.05, 0.0);
            }

            if (t == DURATION - 1) {
                level.sendParticles(ParticleTypes.HEART,
                        entity.getX(), entity.getY() + height * 0.75, entity.getZ(),
                        12, 0.45, height * 0.35, 0.45, 0.02);
            }

            return true;
        }
    }

    //? if legacy {
    @Mod.EventBusSubscriber
    //?} else {
    @EventBusSubscriber
    //?}
    public static final class Events {

        //? if legacy {
        @SubscribeEvent
        public static void onServerTick(TickEvent.ServerTickEvent event) {
            if (event.phase == TickEvent.Phase.END) {
                tickAll();
            }
        }
        //?} else {
        @SubscribeEvent
        public static void onServerTick(ServerTickEvent.Post event) {
            tickAll();
        }
        //?}
    }
}
