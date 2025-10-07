package io.github.tt432.dokimobs;

import io.github.tt432.dokimobs.data.DokiData;
import io.github.tt432.eyelib.capability.EntityBehaviorData;
import io.github.tt432.eyelib.capability.EyelibAttachableData;
import io.github.tt432.eyelib.capability.RenderData;
import io.github.tt432.eyelib.common.behavior.component.MarkVariant;
import io.github.tt432.eyelib.common.behavior.component.Variant;
import io.github.tt432.eyelib.common.behavior.component.group.ComponentGroup;
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
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author TT432
 */
public class TempVariantSetting {
    @EventBusSubscriber
    public static final class CommonEvents {
        static void variant(Entity entity, Variant variant) {
            var data = entity.getData(EyelibAttachableData.ENTITY_BEHAVIOR_DATA);
            List<ComponentGroup> componentGroups = data.getComponentGroups();

            if (componentGroups.isEmpty()) {
                componentGroups.add(new ComponentGroup(Map.of("a", Map.of(
                        "variant",
                        variant
                ))));
            }

            data.setup();

            entity.setData(EyelibAttachableData.ENTITY_BEHAVIOR_DATA, data);
        }

        static void variant(Entity entity, Variant variant, MarkVariant markVariant) {
            var data = entity.getData(EyelibAttachableData.ENTITY_BEHAVIOR_DATA);
            List<ComponentGroup> componentGroups = new ArrayList<>();

            componentGroups.add(new ComponentGroup(Map.of("variants", Map.of(
                    "variant",
                    variant,
                    "mark_variant",
                    markVariant
            ))));

            entity.setData(EyelibAttachableData.ENTITY_BEHAVIOR_DATA, new EntityBehaviorData(data.getBehavior(), componentGroups));
        }

        @SubscribeEvent
        public static void onEvent(EntityTickEvent.Pre event) {
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
                switch (event.getEntity()) {
                    case Zombie z -> {
                        variant(z, new Variant(z.getRandom().nextInt(3)));
                    }
                    case Llama llama -> {
                        variant(llama, new Variant(llama.getVariant().getId()), new MarkVariant(llama instanceof TraderLlama ? 1 : 0));
                    }
                    case Fox fox -> {
                        if (fox.getVariant() == Fox.Type.SNOW) {
                            variant(fox, new Variant(switch (weightedRandom(fox.getRandom(), new int[]{5, 1, 1, 1})) {
                                case 0 -> 1;
                                case 1 -> 9;
                                case 2 -> 10;
                                case 3 -> 11;
                                default -> -1;
                            }));
                        } else {
                            variant(fox, new Variant(switch (weightedRandom(fox.getRandom(), new int[]{5, 1, 1, 1, 1, 1, 1, 1})) {
                                case 0 -> 0;
                                case 1 -> 6;
                                case 2 -> 2;
                                case 3 -> 3;
                                case 4 -> 4;
                                case 5 -> 5;
                                case 6 -> 8;
                                case 7 -> 7;
                                default -> -1;
                            }));
                        }
                    }
                    case Chicken chicken -> {
                        variant(chicken, new Variant(chicken.getRandom().nextInt(3)));
                    }
                    case Villager villager -> {

                    }
                    default -> {
                    }
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

    @EventBusSubscriber
    public static final class ClientEvents {
        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public static void onEvent(RenderLivingEvent.Pre event) {
            if (!event.getEntity().getData(DokiData.DOKI).doki()) {
                RenderData.getComponent(event.getEntity()).setUseBuiltInRenderSystem(false);
            }
        }
    }
}
