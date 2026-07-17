package io.github.tt432.dokimobs.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.tt432.dokimobs.config.DokimobsConfig;
import io.github.tt432.eyelib.bridge.attachment.dataattach.mc.DataAttachmentHelper;
import io.github.tt432.eyelib.bridge.attachment.network.adapter.DataAttachmentSyncRuntime;
import io.github.tt432.eyelib.util.streamcodec.EyelibStreamCodecs;
import io.github.tt432.eyelib.util.streamcodec.StreamCodec;
import lombok.With;
//? if !modern {
import net.minecraft.world.entity.MobSpawnType;
//?} else {
import net.minecraft.world.entity.EntitySpawnReason;
//?}
//? if legacy {
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
//?} else {
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
//?}

/**
 * @author TT432
 */
@With
public record EntityDokiData(
        boolean init,
        boolean doki
) {
    public static final Codec<EntityDokiData> CODEC = RecordCodecBuilder.create(ins -> ins.group(
            Codec.BOOL.fieldOf("init").forGetter(EntityDokiData::init),
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
        //? if legacy {
        public static void onFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
        //?} else {
        public static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        //?}
            // 处理刷怪蛋生成的实体，确保它们能正确设置doki属性
            //? if !modern {
            if (event.getSpawnType() == MobSpawnType.SPAWN_EGG) {
            //?} else {
            if (event.getSpawnType() == EntitySpawnReason.SPAWN_ITEM_USE) {
            //?}
                EntityDokiData data = DataAttachmentHelper.getOrCreate(DokiData.DOKI.get(), event.getEntity());
                if (!event.getLevel().isClientSide() && !data.init()) {
                    data = data
                            .withInit(true)
                            .withDoki(Math.random() < DokimobsConfig.CONFIG.spawnChance.get());

                    DataAttachmentHelper.setLocal(DokiData.DOKI.get(), event.getEntity(), data);
                    DataAttachmentSyncRuntime.syncTrackedAndSelf(DokiData.DOKI.get(), event.getEntity(), data);
                }
            }
        }

        @SubscribeEvent
        public static void onEvent(EntityJoinLevelEvent event) {
            EntityDokiData data = DataAttachmentHelper.getOrCreate(DokiData.DOKI.get(), event.getEntity());
            if (!data.init() && !event.getLevel().isClientSide()) {
                data = data
                        .withInit(true)
                        .withDoki(Math.random() < DokimobsConfig.CONFIG.spawnChance.get());

                DataAttachmentHelper.setLocal(DokiData.DOKI.get(), event.getEntity(), data);
                DataAttachmentSyncRuntime.syncTrackedAndSelf(DokiData.DOKI.get(), event.getEntity(), data);
            }
        }
    }
}
