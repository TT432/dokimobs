package io.github.tt432.dokimobs.item;

import io.github.tt432.dokimobs.TempVariantSetting;
import io.github.tt432.dokimobs.data.DokiData;
import io.github.tt432.dokimobs.data.EntityDokiData;
import io.github.tt432.eyelib.bridge.attachment.dataattach.mc.DataAttachmentHelper;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
//? if legacy {
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
//?} else {
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
//?}

/**
 * 萌化尘的交互优先级：在 {@code Entity.interact} 之前抢占，让「使用萌化尘」优先于生物自身交互。
 * <p>
 * 原版顺序（1.20.1 / 1.21.1 / 26.1.2 一致）：
 * <pre>
 * Player.interactOn
 *   ├─ 加载器钩子 onInteractEntity → PlayerInteractEvent.EntityInteract   ← 本类在这里
 *   ├─ Entity.interact → Mob.interact → mobInteract（村民交易 / 已驯服狼坐姿 …）
 *   └─ ItemStack.interactLivingEntity → {@link MoeDustItem}
 * </pre>
 * 物品侧 {@code interactLivingEntity} 是链尾：只要 {@code mobInteract} 返回
 * {@code consumesAction}，物品永远拿不到调用。因此优先级只能在这个「生物交互之前」的
 * 加载器事件里调整——取消事件并给出 {@code SUCCESS} 会让 {@code interactOn} 直接返回，
 * 既不调 {@code Entity.interact} 也不调物品，无需 mixin、不改原版。
 * <p>
 * 抢占范围严格限定为「可萌化」目标（{@link TempVariantSetting#isDokiManaged}，即
 * 有萌化模型覆盖的实体类型）：不可萌化的生物保持原版交互（例如无 cospack 的村民照常交易）。
 * 已萌化目标同样放行，保持「不消耗、不重复播放动画」的既有语义。
 * <p>
 * 事件在客户端与服务端都会触发：客户端只做预测（取消并返回 SUCCESS，不写状态、不扣物品），
 * 状态写入与物品消耗只发生在服务端。
 *
 * @author TT432
 */
public final class MoeDustInteraction {

    private MoeDustInteraction() {
    }

    //? if legacy {
    @Mod.EventBusSubscriber
    //?} else {
    @EventBusSubscriber
    //?}
    public static final class Events {

        /**
         * HIGHEST：尽量排在其他 mod 的同类处理之前，避免被更早的取消/放行覆盖。
         */
        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public static void onEvent(PlayerInteractEvent.EntityInteract event) {
            ItemStack stack = event.getItemStack();

            if (!stack.is(DokimobsItems.MOE_DUST.get())) {
                return;
            }

            if (!(event.getTarget() instanceof LivingEntity target) || target instanceof Player) {
                return;
            }

            // 不可萌化的目标不抢占：原版交互（交易、骑乘…）按原优先级执行
            if (!TempVariantSetting.isDokiManaged(target)) {
                return;
            }

            EntityDokiData data = DataAttachmentHelper.getOrNull(DokiData.DOKI.get(), target);

            if (data != null && data.doki()) {
                return;
            }

            if (!event.getLevel().isClientSide()) {
                MoeDustItem.moeify(stack, event.getEntity(), target);
            }

            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }
}
