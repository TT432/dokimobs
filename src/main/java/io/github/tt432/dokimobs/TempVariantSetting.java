package io.github.tt432.dokimobs;

import io.github.tt432.dokimobs.data.DokiData;
import io.github.tt432.eyelib.behavior.SyncedBehaviorState;
import io.github.tt432.dokimobs.data.EntityDokiData;
import io.github.tt432.dokimobs.data.EntityDokiVariant;
import io.github.tt432.eyelib.bridge.attachment.dataattach.mc.DataAttachmentHelper;
import io.github.tt432.eyelib.bridge.attachment.network.adapter.DataAttachmentSyncRuntime;
import io.github.tt432.eyelib.bridge.capability.adapter.EyelibAttachableData;
import io.github.tt432.eyelib.capability.RenderData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
//? if !modern {
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.entity.animal.horse.Llama;
import net.minecraft.world.entity.animal.horse.TraderLlama;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerDataHolder;
//?} else {
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.fox.Fox;
import net.minecraft.world.entity.animal.equine.Llama;
import net.minecraft.world.entity.animal.equine.TraderLlama;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerDataHolder;
//?}
//? if modern {
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;
//?}
//? if legacy {
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
//?} else {
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
//?}

import java.util.Set;
import java.util.function.Supplier;

/**
 * @author TT432
 */
public class TempVariantSetting {
    /**
     * dokimobs 注册了 eyelib 模型替换的实体 identifier（assets/dokimobs/eyelib/entity/ 下 5 种）。
     */
    private static final Set<String> BUILT_IN_MANAGED = Set.of(
            "minecraft:blaze",
            "minecraft:wolf",
            "minecraft:wandering_trader",
            "minecraft:pillager",
            "minecraft:guardian");

    /**
     * cospack 动态注册到 eyelib ClientEntityManager 的实体 identifier，
     * 由 {@link CosPackLoader} 在资源重载时整体重建。
     */
    private static volatile Set<String> cosPackManaged = Set.of();

    public static void setCosPackManagedIdentifiers(Set<String> identifiers) {
        cosPackManaged = Set.copyOf(identifiers);
    }

    /**
     * doki 值是 dokimobs 受管实体的逐实例替换短路开关（false → 强制原版渲染）。
     * 仅受管类型允许写 eyelib 的 useBuiltInRenderSystem：该开关默认 true 且无人复位，
     * 对未受管实体写入 false 会永久旁路其他 eyelib mod 注册的模型替换。
     */
    static boolean isDokiManaged(Entity entity) {
        String key = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
        return BUILT_IN_MANAGED.contains(key) || cosPackManaged.contains(key);
    }

    //? if legacy {
    @Mod.EventBusSubscriber
    //?} else {
    @EventBusSubscriber
    //?}
    public static final class CommonEvents {
        @SubscribeEvent
        //? if legacy {
        public static void onEvent(LivingEvent.LivingTickEvent event) {
        //?} else {
        public static void onEvent(EntityTickEvent.Pre event) {
        //?}
            if (!event.getEntity().level().isClientSide()) {
                if (event.getEntity() instanceof VillagerDataHolder villager) {
                    // VillagerProfession.name() 在 26.1 起返回 Component，统一走注册表 key 取 path
                    //? if !modern {
                    int variantId = switch (BuiltInRegistries.VILLAGER_PROFESSION.getKey(villager.getVillagerData().getProfession()).getPath()) {
                    //?} else {
                    // 26.1: VillagerData 访问器改为 profession()/type()（返回 Holder）
                    int variantId = switch (BuiltInRegistries.VILLAGER_PROFESSION.getKey(villager.getVillagerData().profession().value()).getPath()) {
                    //?}
                        case "farmer" -> 1;
                        case "fisherman" -> 2;
                        case "shepherd" -> 3;
                        case "fletcher" -> 4;
                        case "librarian" -> 5;
                        case "cartographer" -> 6;
                        case "cleric" -> 7;
                        case "armorer" -> 8;
                        case "weaponsmith" -> 9;
                        case "toolsmith" -> 10;
                        case "butcher" -> 11;
                        case "leatherworker" -> 12;
                        case "mason" -> 13;
                        case "nitwit" -> 14;
                        default -> 0;
                    };
                    //? if !modern {
                    int markVariantId = switch (BuiltInRegistries.VILLAGER_TYPE.getKey(villager.getVillagerData().getType()).getPath()) {
                    //?} else {
                    int markVariantId = switch (BuiltInRegistries.VILLAGER_TYPE.getKey(villager.getVillagerData().type().value()).getPath()) {
                    //?}
                        case "desert" -> 1;
                        case "jungle" -> 2;
                        case "savanna" -> 3;
                        case "snow" -> 4;
                        case "swamp" -> 5;
                        case "taiga" -> 6;
                        default -> 0;
                    };
                    syncVariantIfChanged((Entity) villager, variantId, markVariantId);
                }
            }
        }

        /**
         * 变体落盘 + 同步。渲染期 query.variant/mark_variant 读 SyncedBehaviorState
         * （EntityRenderOrchestrator.setupSyncedBehaviorContext），不是 EntityBehaviorData；
         * 且附件无自动同步，值变化后必须显式 syncTrackedAndSelf——
         * 旧实现两者皆错（皮肤不随职业切换的根因）。仅在值变化时写，避免逐 tick 分配与发包。
         */
        static void syncVariantIfChanged(Entity entity, int variantId, int markVariantId) {
            var data = DataAttachmentHelper.getOrCreate(EyelibAttachableData.syncedBehaviorState(), entity);

            if (data.variant() == variantId && data.markVariant() == markVariantId) {
                return;
            }

            var newState = new SyncedBehaviorState(variantId, data.scale(), markVariantId);
            DataAttachmentHelper.setLocal(EyelibAttachableData.syncedBehaviorState(), entity, newState);
            DataAttachmentSyncRuntime.syncTrackedAndSelf(EyelibAttachableData.syncedBehaviorState(), entity, newState);
        }

        /**
         * 掷骰落盘 + 镜像 {@link SyncedBehaviorState}。
         * <p>
         * 掷骰语义：<b>dokimobs 自己的 variant 附件存在 = 已掷骰</b>。旧实现在每次
         * EntityJoinLevelEvent（= 每次区块重载 / 世界重进）都无条件重新 {@code nextInt}，
         * 覆盖掉已持久化的 SyncedBehaviorState——僵尸/鸡/狐狸换一次存档就换一次皮肤
         * （2026-11-02 实机：24 只僵尸中 17 只重载后变体改变）。
         * <p>
         * 用 priority = LOW 保证排在 eyelib 的 spawn 逻辑（默认 NORMAL）之后，
         * 这样镜像时能拿到行为包刚写下的 scale（如僵尸 0.5）并原样保留。
         */
        @SubscribeEvent(priority = EventPriority.LOW)
        public static void onEvent(EntityJoinLevelEvent event) {
            if (event.getLevel().isClientSide()) {
                return;
            }

            Entity entity = event.getEntity();

            if (entity instanceof Zombie z) {
                EntityDokiVariant rolled = rolledVariant(z, () -> new EntityDokiVariant(z.getRandom().nextInt(3), 0));
                syncVariantIfChanged(z, rolled.variant(), rolled.markVariant());
            } else if (entity instanceof Llama llama) {
                // 羊驼变体来自原版 JE 数据（固定），可直接对齐，无需掷骰
                syncVariantIfChanged(llama, llama.getVariant().getId(), llama instanceof TraderLlama ? 1 : 0);
            } else if (entity instanceof Fox fox) {
                EntityDokiVariant rolled = rolledVariant(fox, () -> new EntityDokiVariant(randomFoxVariant(fox), 0));
                syncVariantIfChanged(fox, rolled.variant(), rolled.markVariant());
            } else if (entity instanceof Chicken chicken) {
                EntityDokiVariant rolled = rolledVariant(chicken, () -> new EntityDokiVariant(chicken.getRandom().nextInt(3), 0));
                syncVariantIfChanged(chicken, rolled.variant(), rolled.markVariant());
            } else if (entity instanceof Villager) {
                // handled by tick event above
            }
        }

        /**
         * 已掷过骰则返回落盘的变体，否则掷一次并写入附件。
         * 附件是否存在即「是否已掷骰」，因此区块重载/世界重进/跨维度重建都不会重掷。
         */
        static EntityDokiVariant rolledVariant(Entity entity, Supplier<EntityDokiVariant> roll) {
            EntityDokiVariant existing = DataAttachmentHelper.getOrNull(DokiData.VARIANT.get(), entity);

            if (existing != null) {
                return existing;
            }

            EntityDokiVariant rolled = roll.get();
            DataAttachmentHelper.setLocal(DokiData.VARIANT.get(), entity, rolled);
            return rolled;
        }

        private static int randomFoxVariant(Fox fox) {
            //? if !modern {
            if (fox.getVariant() == Fox.Type.SNOW) {
            //?} else {
            if (fox.getVariant() == Fox.Variant.SNOW) {
            //?}
                return switch (weightedRandom(fox.getRandom(), new int[]{5, 1, 1, 1})) {
                    case 0 -> 1;
                    case 1 -> 9;
                    case 2 -> 10;
                    case 3 -> 11;
                    default -> -1;
                };
            } else {
                return switch (weightedRandom(fox.getRandom(), new int[]{5, 1, 1, 1, 1, 1, 1, 1})) {
                    case 0 -> 0;
                    case 1 -> 6;
                    case 2 -> 2;
                    case 3 -> 3;
                    case 4 -> 4;
                    case 5 -> 5;
                    case 6 -> 8;
                    case 7 -> 7;
                    default -> -1;
                };
            }
        }

        private static int weightedRandom(RandomSource source, int[] weights) {
            int totalWeight = 0;
            for (int weight : weights) {
                totalWeight += weight;
            }
            int randomValue = source.nextInt(totalWeight);
            int cumulativeWeight = 0;
            for (int i = 0; i < weights.length; i++) {
                cumulativeWeight += weights[i];
                if (randomValue < cumulativeWeight) {
                    return i;
                }
            }
            return -1;
        }
    }

    //? if legacy {
    @Mod.EventBusSubscriber(Dist.CLIENT)
    //?} else {
    @EventBusSubscriber(value = Dist.CLIENT)
    //?}
    public static final class ClientEvents {
        @SubscribeEvent(priority = EventPriority.HIGHEST)
        //? if !modern {
        public static void onEvent(RenderLivingEvent.Pre<?, ?> event) {
            Entity entity = event.getEntity();
            if (!isDokiManaged(entity)) return;

            // 附件存在 = 服务端已掷骰并同步到客户端；未同步前按 false（原版渲染）处理，
            // 这里用 getOrNull 而不是 getOrCreate：客户端不该把「默认值」写进自己的容器。
            EntityDokiData dokiData = DataAttachmentHelper.getOrNull(DokiData.DOKI.get(), entity);
            // 每帧双向设置：doki 同步晚于首帧渲染时，避免 false 粘性导致永久原版渲染
            RenderData.getComponent(entity).setUseBuiltInRenderSystem(dokiData != null && dokiData.doki());
        }
        //?} else {
        public static <T extends LivingEntity, S extends LivingEntityRenderState, M extends net.minecraft.client.model.EntityModel<? super S>>
        void onEvent(RenderLivingEvent.Pre<T, S, M> event) {
            var state = event.getRenderState();
            LivingEntity entity = findEntityByRenderState(state.entityType, state.x, state.y, state.z, event.getPartialTick());
            if (entity == null) return;
            if (!isDokiManaged(entity)) return;

            // 附件存在 = 服务端已掷骰并同步到客户端；未同步前按 false（原版渲染）处理，
            // 这里用 getOrNull 而不是 getOrCreate：客户端不该把「默认值」写进自己的容器。
            EntityDokiData dokiData = DataAttachmentHelper.getOrNull(DokiData.DOKI.get(), entity);
            // 每帧双向设置：doki 同步晚于首帧渲染时，避免 false 粘性导致永久原版渲染
            RenderData.getComponent(entity).setUseBuiltInRenderSystem(dokiData != null && dokiData.doki());
        }

        /**
         * 26.1+ RenderLivingEvent 不再直接暴露实体，按渲染状态的位置/类型反查
         * （与 eyelib RenderLivingEventAdapter 同一模式）。
         */
        @Nullable
        private static LivingEntity findEntityByRenderState(
                net.minecraft.world.entity.EntityType<?> entityType,
                double targetX,
                double targetY,
                double targetZ,
                float partialTick) {
            var level = Minecraft.getInstance().level;
            if (level == null) return null;

            for (var entity : level.entitiesForRendering()) {
                if (!(entity instanceof LivingEntity livingEntity)) continue;
                if (entity.getType() != entityType) continue;

                double renderX = Mth.lerp(partialTick, entity.xOld, entity.getX());
                double renderY = Mth.lerp(partialTick, entity.yOld, entity.getY());
                double renderZ = Mth.lerp(partialTick, entity.zOld, entity.getZ());
                if (Math.abs(renderX - targetX) < 0.5
                        && Math.abs(renderY - targetY) < 0.5
                        && Math.abs(renderZ - targetZ) < 0.5) {
                    return livingEntity;
                }
            }

            return null;
        }
        //?}
    }
}
