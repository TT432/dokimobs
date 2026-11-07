package io.github.tt432.dokimobs.molang;

import io.github.tt432.eyelib.molang.MolangScope;
import io.github.tt432.eyelib.molang.mapping.api.HostRole;
import io.github.tt432.eyelib.molang.mapping.api.MolangFunction;
import io.github.tt432.eyelib.molang.mapping.api.MolangMapping;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.item.DyeColor;
//? if <26.1 {
import net.minecraft.world.entity.animal.Sheep;
//?} else {
import net.minecraft.world.entity.animal.sheep.Sheep;
//?}

/**
 * dokimobs 侧补充的 Molang 查询。
 *
 * <p>监守者的 {@code query.time_since_last_vibration_detection} 在客户端拿不到服务端
 * Brain 的 VIBRATION_COOLDOWN 记忆，恒为 2.0，导致依赖它的 {@code tendril_animation}
 * 恒为 0：触须/光波动画（shiver）与触须透明度永远不播。客户端真正同步到的是
 * {@link Warden#getClientAngerLevel()}（DATA_CLIENT_ANGER_LEVEL），因此以怒气值作为
 * 客户端可用的“最近感知到振动”信号。
 *
 * @author TT432
 */
@MolangMapping("query")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DokiMolangQueries {
    private static final HostRole<Warden> WARDEN = HostRole.of("Warden", Warden.class);
    private static final HostRole<Sheep> SHEEP = HostRole.of("Sheep", Sheep.class);

    @MolangFunction(value = "doki_sheep_wool_r", description = "羊毛颜色 R 分量（含 jeb_ 彩虹）")
    public static float sheepWoolRed(MolangScope scope) {
        return sheepWoolChannel(scope, 0);
    }

    @MolangFunction(value = "doki_sheep_wool_g", description = "羊毛颜色 G 分量（含 jeb_ 彩虹）")
    public static float sheepWoolGreen(MolangScope scope) {
        return sheepWoolChannel(scope, 1);
    }

    @MolangFunction(value = "doki_sheep_wool_b", description = "羊毛颜色 B 分量（含 jeb_ 彩虹）")
    public static float sheepWoolBlue(MolangScope scope) {
        return sheepWoolChannel(scope, 2);
    }

    private static float sheepWoolChannel(MolangScope scope, int channel) {
        Sheep sheep = scope.getHostContext().get(SHEEP).orElse(null);
        if (sheep == null) {
            return 1F;
        }
        float[] color = isJebSheep(sheep)
                ? jebWoolColor(sheep, scope.get("variable.partial_tick").asFloat())
                : dyeWoolColor(sheep.getColor());
        return color[channel];
    }

    private static boolean isJebSheep(Sheep sheep) {
        return sheep.hasCustomName() && "jeb_".equals(sheep.getName().getString());
    }

    /**
     * 复刻各版本 vanilla {@code SheepFurLayer} 的 jeb_ 取色：
     * 1.20.1/1.21.1 = tickCount/25 + entityId 选相邻两色，tickCount%25 + partialTick 插值；
     * 26.1 = ageInTicks（tickCount + partialTick）按 25 tick 步进 DyeColor.values() 16 色顺序插值。
     */
    private static float[] jebWoolColor(Sheep sheep, float partialTick) {
        //? if <26.1 {
        int i = sheep.tickCount / 25 + sheep.getId();
        int k = i % DyeColor.values().length;
        int l = (i + 1) % DyeColor.values().length;
        float f = ((sheep.tickCount % 25) + partialTick) / 25.0F;
        float[] from = dyeWoolColor(DyeColor.byId(k));
        float[] to = dyeWoolColor(DyeColor.byId(l));
        return new float[]{lerp(from[0], to[0], f), lerp(from[1], to[1], f), lerp(from[2], to[2], f)};
        //?} else {
        // vanilla 26.1：ColorLerper.Type.SHEEP 仍以 DyeColor.values() 的 16 色顺序、
        // 25 tick 步进，只是改为以 ageInTicks（tickCount + partialTick）为时间轴、不再加 entityId。
        float age = sheep.tickCount + partialTick;
        int step = (int) Math.floor(age) / 25;
        int k = step % DyeColor.values().length;
        int l = (step + 1) % DyeColor.values().length;
        float f = (age % 25.0F) / 25.0F;
        float[] from = dyeWoolColor(DyeColor.byId(k));
        float[] to = dyeWoolColor(DyeColor.byId(l));
        return new float[]{lerp(from[0], to[0], f), lerp(from[1], to[1], f), lerp(from[2], to[2], f)};
        //?}
    }

    /**
     * 羊毛底色：与 vanilla {@code Sheep.createSheepColor} 一致——白色固定 0.902，
     * 其余取 DyeColor 的 diffuse 亮度乘 0.75（1.20.6+ 为 int 通道，按原版 floor 量化）。
     */
    private static float[] dyeWoolColor(DyeColor dye) {
        //? if <1.20.6 {
        if (dye == DyeColor.WHITE) {
            return new float[]{0.9019608F, 0.9019608F, 0.9019608F};
        }
        float[] diffuse = dye.getTextureDiffuseColors();
        return new float[]{diffuse[0] * 0.75F, diffuse[1] * 0.75F, diffuse[2] * 0.75F};
        //?} else {
        if (dye == DyeColor.WHITE) {
            return new float[]{0.9019608F, 0.9019608F, 0.9019608F};
        }
        int diffuse = dye.getTextureDiffuseColor();
        return new float[]{
                (int) (((diffuse >> 16) & 0xFF) * 0.75F) / 255.0F,
                (int) (((diffuse >> 8) & 0xFF) * 0.75F) / 255.0F,
                (int) ((diffuse & 0xFF) * 0.75F) / 255.0F
        };
        //?}
    }

    private static float lerp(float from, float to, float delta) {
        return from + (to - from) * delta;
    }

    @MolangFunction(value = "doki_warden_anger", description = "监守者客户端怒气值（0-150）")
    public static float wardenAnger(MolangScope scope) {
        return scope.getHostContext().get(WARDEN).map(w -> (float) w.getClientAngerLevel()).orElse(0F);
    }

    @MolangFunction(value = "doki_warden_sonic_boom", description = "监守者客户端 Sonic Boom 动画是否处于完整剪辑播放窗口")
    public static float wardenSonicBoom(MolangScope scope) {
        return scope.getHostContext().get(WARDEN).map(w -> {
            if (!w.sonicBoomAnimationState.isStarted()) {
                return 0F;
            }
            //? if modern {
            return w.sonicBoomAnimationState.getTimeInMillis(w.tickCount) < 4_300L ? 1F : 0F;
            //?} else {
            w.sonicBoomAnimationState.updateTime(w.tickCount, 1.0F);
            return w.sonicBoomAnimationState.getAccumulatedTime() < 4_300L ? 1F : 0F;
            //?}
        }).orElse(0F);
    }
}
