package io.github.tt432.dokimobs.molang;

import io.github.tt432.eyelib.molang.MolangScope;
import io.github.tt432.eyelib.molang.mapping.api.HostRole;
import io.github.tt432.eyelib.molang.mapping.api.MolangFunction;
import io.github.tt432.eyelib.molang.mapping.api.MolangMapping;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.minecraft.world.entity.monster.warden.Warden;

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
