package io.github.tt432.dokimobs.data;

import io.github.tt432.eyelib.Eyelib;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * @author TT432
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class DokiData {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Eyelib.MOD_ID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<EntityDokiData>> DOKI =
            ATTACHMENT_TYPES.register("doki_data",
                    () -> AttachmentType.builder(() -> new EntityDokiData(false, false))
                            .serialize(EntityDokiData.CODEC)
                            .sync(EntityDokiData.STREAM_CODEC)
                            .build());
}
