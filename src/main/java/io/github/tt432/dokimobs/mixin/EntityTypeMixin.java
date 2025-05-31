package io.github.tt432.dokimobs.mixin;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * @author TT432
 */
@Mixin(EntityType.class)
public class EntityTypeMixin {
    @Inject(method = "register", at = @At("HEAD"), cancellable = true)
    private static <T extends Entity> void registerMixin(String key, EntityType.Builder<T> builder, CallbackInfoReturnable<EntityType<T>> cir) {
        if (key.equals("spider")) {
            EntityType<Spider> build = EntityType.Builder.of(Spider::new, MobCategory.MONSTER)
                    .sized(1.4F, 0.9F)
                    .eyeHeight(0.65F)
                    .clientTrackingRange(8)
                    .passengerAttachments(new Vec3(0, 1.25, -0.5))
                    .build(key);
            cir.setReturnValue((EntityType<T>) Registry.register(BuiltInRegistries.ENTITY_TYPE, key, build));
        }
    }
}
