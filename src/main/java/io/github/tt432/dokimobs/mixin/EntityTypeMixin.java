//? if >=1.21 {
package io.github.tt432.dokimobs.mixin;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
//? if modern {
import net.minecraft.resources.ResourceKey;
//?}
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
//? if !modern {
import net.minecraft.world.entity.monster.Spider;
//?} else {
import net.minecraft.world.entity.monster.spider.Spider;
//?}
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 仅 >=1.21：passengerAttachments / eyeHeight 是 1.21+ 的 EntityType.Builder API。
 *
 * @author TT432
 */
@Mixin(EntityType.class)
public class EntityTypeMixin {
    //? if !modern {
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
    //?} else {
    // 26.1：原版注册走 register(ResourceKey, Builder) 重载，mixin 必须匹配该描述符
    @Inject(method = "register", at = @At("HEAD"), cancellable = true)
    private static <T extends Entity> void registerMixin(ResourceKey<?> key, EntityType.Builder<T> builder, CallbackInfoReturnable<EntityType<T>> cir) {
        if (key.identifier().getPath().equals("spider")) {
            var id = key.identifier();
            EntityType<Spider> build = EntityType.Builder.of(Spider::new, MobCategory.MONSTER)
                    .sized(1.4F, 0.9F)
                    .eyeHeight(0.65F)
                    .clientTrackingRange(8)
                    .passengerAttachments(new Vec3(0, 1.25, -0.5))
                    .build(ResourceKey.create(BuiltInRegistries.ENTITY_TYPE.key(), id));
            cir.setReturnValue((EntityType<T>) Registry.register(BuiltInRegistries.ENTITY_TYPE, id, build));
        }
    }
    //?}
}
//?}
