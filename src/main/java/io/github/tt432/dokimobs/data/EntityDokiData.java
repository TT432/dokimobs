package io.github.tt432.dokimobs.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.tt432.dokimobs.config.DokimobsConfig;
import io.github.tt432.eyelib.util.codec.stream.StreamCodec;
import io.github.tt432.eyelib.util.data_attach.DataAttachmentHelper;
import lombok.With;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

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

    public static final StreamCodec<EntityDokiData> STREAM_CODEC = StreamCodec.create((obj, buf) -> {
        buf.writeBoolean(obj.init());
        buf.writeBoolean(obj.doki());
    }, buf -> new EntityDokiData(buf.readBoolean(), buf.readBoolean()));

    @Mod.EventBusSubscriber
    public static final class Events {
        @SubscribeEvent
        public static void onFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
            // 处理刷怪蛋生成的实体，确保它们能正确设置doki属性
            if (event.getSpawnType() == MobSpawnType.SPAWN_EGG) {
                EntityDokiData data = DataAttachmentHelper.getOrCreate(DokiData.DOKI.get(), event.getEntity());
                if (!event.getLevel().isClientSide() && !data.init) {
                    data = data
                            .withInit(true)
                            .withDoki(Math.random() < DokimobsConfig.CONFIG.spawnChance.get());

                    DataAttachmentHelper.set(DokiData.DOKI.get(), event.getEntity(), data);
                }
            }
        }

        @SubscribeEvent
        public static void onEvent(EntityJoinLevelEvent event) {
            EntityDokiData data = DataAttachmentHelper.getOrCreate(DokiData.DOKI.get(), event.getEntity());
            if (!data.init && !event.getLevel().isClientSide) {
                data = data
                        .withInit(true)
                        .withDoki(Math.random() < DokimobsConfig.CONFIG.spawnChance.get());

                DataAttachmentHelper.set(DokiData.DOKI.get(), event.getEntity(), data);
            }
        }
    }
}
