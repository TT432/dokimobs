package io.github.tt432.dokimobs.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.tt432.dokimobs.config.DokimobsConfig;
import io.github.tt432.eyelib.bridge.attachment.dataattach.mc.DataAttachmentHelper;
import io.github.tt432.eyelib.bridge.attachment.network.adapter.DataAttachmentSyncRuntime;
import io.github.tt432.eyelib.util.streamcodec.EyelibStreamCodecs;
import io.github.tt432.eyelib.util.streamcodec.StreamCodec;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
//? if legacy {
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
//?} else {
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
//?}

/**
 * 单个实体的 doki 外观开关：{@code true} → eyelib 自定义模型接管渲染；{@code false} → 原版渲染。
 * <p>
 * 生命周期（真源 = 服务端持久化附件）：
 * <ol>
 *   <li>实体加入世界时掷一次骰（{@link DokimobsConfig#spawnChance}），写入附件并同步给追踪者；</li>
 *   <li>「是否已掷骰」= <b>附件是否存在</b>（{@link DataAttachmentHelper#getOrNull}），
 *       不是额外的 init 标志——旧实现把「默认值」与「未掷骰」混为一谈，附件一旦被
 *       {@code getOrCreate} 创建（例如客户端渲染期）就只能靠 init 兜底；</li>
 *   <li>因此区块重载 / 世界重进 / 实体跨维度重建都不会重掷（离线存档里已有 init=true 的旧数据
 *       解码后同样视为已掷骰，多余字段被 codec 忽略）；</li>
 *   <li>客户端拿不到（尚未同步）时按 false 处理 = 原版渲染，不写默认值进客户端容器。</li>
 * </ol>
 * 只对 {@link LivingEntity}（排除 {@link Player}）掷骰：doki 只决定活体渲染，
 * 给掉落物/箭/经验球/玩家写状态只会无意义地撑大存档。
 *
 * @author TT432
 */
public record EntityDokiData(
        boolean doki
) {
    public static final Codec<EntityDokiData> CODEC = RecordCodecBuilder.create(ins -> ins.group(
            Codec.BOOL.fieldOf("doki").forGetter(EntityDokiData::doki)
    ).apply(ins, EntityDokiData::new));

    public static final StreamCodec<EntityDokiData> STREAM_CODEC = EyelibStreamCodecs.fromCodec(CODEC);

    //? if legacy {
    @Mod.EventBusSubscriber
    //?} else {
    @EventBusSubscriber
    //?}
    public static final class Events {

        @SubscribeEvent
        public static void onEvent(EntityJoinLevelEvent event) {
            if (event.getLevel().isClientSide()) {
                return;
            }

            Entity entity = event.getEntity();

            if (entity instanceof LivingEntity && !(entity instanceof Player)) {
                rollIfAbsent(entity);
            }
        }
    }

    /**
     * 附件不存在时掷骰并写入/同步；已掷过则原样返回。
     * 唯一调用点是 {@link Events#onEvent}（实体加入世界）：自然生成、刷怪蛋、区块重载、
     * 世界重进最终都会经过该事件，因此不需要额外的 FinalizeSpawn 入口。
     */
    public static EntityDokiData rollIfAbsent(Entity entity) {
        EntityDokiData existing = DataAttachmentHelper.getOrNull(DokiData.DOKI.get(), entity);

        if (existing != null) {
            return existing;
        }

        EntityDokiData rolled = new EntityDokiData(Math.random() < DokimobsConfig.CONFIG.spawnChance.get());
        DataAttachmentHelper.setLocal(DokiData.DOKI.get(), entity, rolled);
        // 附件无自动同步：滚出结果后立刻推给当前追踪者（新生成实体通常还没有追踪者，
        // 真正的首次同步由 eyelib 的 StartTracking 整包下发兜底）。
        DataAttachmentSyncRuntime.syncTrackedAndSelf(DokiData.DOKI.get(), entity, rolled);
        return rolled;
    }
}
