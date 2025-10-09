package io.github.tt432.dokimobs;

import io.github.tt432.dokimobs.data.DokiData;
import io.github.tt432.eyelib.capability.EntityBehaviorData;
import io.github.tt432.eyelib.capability.EyelibAttachableData;
import io.github.tt432.eyelib.capability.RenderData;
import io.github.tt432.eyelib.common.behavior.component.MarkVariant;
import io.github.tt432.eyelib.common.behavior.component.Variant;
import io.github.tt432.eyelib.common.behavior.component.group.ComponentGroup;
import io.github.tt432.eyelib.util.data_attach.DataAttachmentHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.entity.animal.horse.Llama;
import net.minecraft.world.entity.animal.horse.TraderLlama;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerDataHolder;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author TT432
 */
public class TempVariantSetting {
    @Mod.EventBusSubscriber
    public static final class CommonEvents {
        static void variant(Entity entity, Variant variant) {
            var data = DataAttachmentHelper.getOrCreate(EyelibAttachableData.ENTITY_BEHAVIOR_DATA.get(), entity);
            List<ComponentGroup> componentGroups = data.getComponentGroups();

            if (componentGroups.isEmpty()) {
                componentGroups.add(new ComponentGroup(Map.of("a", Map.of(
                        "variant",
                        variant
                ))));
            }

            data.setup();

            DataAttachmentHelper.set(EyelibAttachableData.ENTITY_BEHAVIOR_DATA.get(), entity, data);
        }

        static void variant(Entity entity, Variant variant, MarkVariant markVariant) {
            var data = DataAttachmentHelper.getOrCreate(EyelibAttachableData.ENTITY_BEHAVIOR_DATA.get(), entity);
            List<ComponentGroup> componentGroups = new ArrayList<>();

            componentGroups.add(new ComponentGroup(Map.of("variants", Map.of(
                    "variant",
                    variant,
                    "mark_variant",
                    markVariant
            ))));

            DataAttachmentHelper.set(EyelibAttachableData.ENTITY_BEHAVIOR_DATA.get(), entity, new EntityBehaviorData(data.getBehavior(), componentGroups));
        }

        @SubscribeEvent
        public static void onEvent(LivingEvent.LivingTickEvent event) {
            if (!event.getEntity().level().isClientSide) {
                if (event.getEntity() instanceof VillagerDataHolder villager) {
                    variant((Entity) villager, new Variant(switch (villager.getVillagerData().getProfession().name()) {
                        case "unskilled" -> 0;
                        case "farmer" -> 1;
                        case "fisherman" -> 2;
                        case "shepherd" -> 3;
                        case "fletcher" -> 4;
                        case "librarian" -> 5;
                        case "cartographer" -> 6;
                        case "cleric" -> 7;
                        case "armorer" -> 8;
                        case "weapon_smith" -> 9;
                        case "tool_smith" -> 10;
                        case "butcher" -> 11;
                        case "leatherworker" -> 12;
                        case "stonemason" -> 13;
                        case "nitwit" -> 14;
                        default -> 0;
                    }), new MarkVariant(switch (BuiltInRegistries.VILLAGER_TYPE.getKey(villager.getVillagerData().getType()).toString()) {
                        case "plains" -> 0;
                        case "desert" -> 1;
                        case "jungle" -> 2;
                        case "savanna" -> 3;
                        case "snow" -> 4;
                        case "swamp" -> 5;
                        case "taiga" -> 6;
                        default -> 0;
                    }));
                }
            }
        }

        @SubscribeEvent
        public static void onEvent(EntityJoinLevelEvent event) {
            if (!event.getLevel().isClientSide) {
                var entity = event.getEntity();

                if (entity instanceof Zombie z) {
                    variant(z, new Variant(z.getRandom().nextInt(3)));
                } else if (entity instanceof Llama llama) {
                    variant(llama, new Variant(llama.getVariant().getId()), new MarkVariant(llama instanceof TraderLlama ? 1 : 0));
                } else if (entity instanceof Fox fox) {
                    if (fox.getVariant() == Fox.Type.SNOW) {
                        int choice = weightedRandom(fox.getRandom(), new int[]{5, 1, 1, 1});
                        int result;
                        if (choice == 0) {
                            result = 1;
                        } else if (choice == 1) {
                            result = 9;
                        } else if (choice == 2) {
                            result = 10;
                        } else if (choice == 3) {
                            result = 11;
                        } else {
                            result = -1;
                        }
                        variant(fox, new Variant(result));
                    } else {
                        int choice = weightedRandom(fox.getRandom(), new int[]{5, 1, 1, 1, 1, 1, 1, 1});
                        int result;
                        if (choice == 0) {
                            result = 0;
                        } else if (choice == 1) {
                            result = 6;
                        } else if (choice == 2) {
                            result = 2;
                        } else if (choice == 3) {
                            result = 3;
                        } else if (choice == 4) {
                            result = 4;
                        } else if (choice == 5) {
                            result = 5;
                        } else if (choice == 6) {
                            result = 8;
                        } else if (choice == 7) {
                            result = 7;
                        } else {
                            result = -1;
                        }
                        variant(fox, new Variant(result));
                    }
                } else if (entity instanceof Chicken chicken) {
                    variant(chicken, new Variant(chicken.getRandom().nextInt(3)));
                } else if (entity instanceof Villager) {
                    // Villager 处理（当前为空）
                } else {
                    // default 情况（当前为空）
                }
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

    @Mod.EventBusSubscriber
    public static final class ClientEvents {
        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public static void onEvent(RenderLivingEvent.Pre event) {
            if (!DataAttachmentHelper.getOrCreate(DokiData.DOKI.get(), event.getEntity()).doki()) {
                RenderData.getComponent(event.getEntity()).setUseBuiltInRenderSystem(false);
            }
        }
    }
}
