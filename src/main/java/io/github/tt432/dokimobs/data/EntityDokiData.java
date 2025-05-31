package io.github.tt432.dokimobs.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.tt432.dokimobs.config.DokimobsConfig;
import io.github.tt432.eyelib.capability.EyelibAttachableData;
import lombok.With;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

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

    @EventBusSubscriber
    public static final class Events {
        @SubscribeEvent
        public static void onEvent(EntityJoinLevelEvent event) {
            EntityDokiData data = event.getEntity().getData(DokiData.DOKI);
            if (!data.init) {
                data = data
                        .withInit(true)
                        .withDoki(Math.random() < DokimobsConfig.CONFIG.spawnChance.get());

                if (!data.doki)
                    event.getEntity().getData(EyelibAttachableData.RENDER_DATA).setUseBuiltInRenderSystem(false);

                event.getEntity().setData(DokiData.DOKI, data);
            } else {
                if (!data.doki)
                    event.getEntity().getData(EyelibAttachableData.RENDER_DATA).setUseBuiltInRenderSystem(false);
            }
        }
    }
}
