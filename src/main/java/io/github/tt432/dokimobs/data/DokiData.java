package io.github.tt432.dokimobs.data;

import io.github.tt432.dokimobs.Dokimobs;
import io.github.tt432.eyelib.capability.EyelibAttachableData;
import io.github.tt432.eyelib.util.data_attach.DataAttachmentType;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * @author TT432
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class DokiData {
    public static final DeferredRegister<DataAttachmentType<?>> DATA_ATTACHMENTS =
            DeferredRegister.create(EyelibAttachableData.DATA_ATTACHMENTS_KEY, Dokimobs.MOD_ID);

    public static final RegistryObject<DataAttachmentType<EntityDokiData>> DOKI =
            DATA_ATTACHMENTS.register("doki_data",
                    () -> new DataAttachmentType<>(new ResourceLocation(Dokimobs.MOD_ID, "doki_data"),
                            () -> new EntityDokiData(false, true),
                            EntityDokiData.CODEC,
                            EntityDokiData.STREAM_CODEC));
}
