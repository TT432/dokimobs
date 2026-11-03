package io.github.tt432.dokimobs.item;

import io.github.tt432.dokimobs.data.DokiData;
import io.github.tt432.dokimobs.data.EntityDokiData;
import io.github.tt432.eyelib.bridge.attachment.dataattach.mc.DataAttachmentHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 萌化尘：对生物使用 → 在服务端把 {@code DOKI} 置为 true（同步给追踪者）并播放粉色粒子动画。
 * <p>
 * 三条约束：
 * <ul>
 *   <li>状态写入只发生在服务端（客户端分支直接返回，不做任何写入）；</li>
 *   <li>已萌化的生物不消耗物品、不播放动画，返回 {@code PASS} 让交互继续向下传递；</li>
 *   <li>玩家不是萌化目标（doki 只决定活体渲染，且玩家交互不走 {@code interactLivingEntity}）。</li>
 * </ul>
 *
 * @author TT432
 */
public class MoeDustItem extends Item {

    public MoeDustItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        // 客户端只做预测（摆手），真正的判定与写入都在服务端。
        if (player.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        if (target instanceof Player) {
            return InteractionResult.PASS;
        }

        EntityDokiData data = DataAttachmentHelper.getOrNull(DokiData.DOKI.get(), target);

        if (data != null && data.doki()) {
            return InteractionResult.PASS;
        }

        EntityDokiData.setDoki(target, true);

        if (player.level() instanceof ServerLevel serverLevel) {
            MoeDustEffect.play(serverLevel, target);
        }

        //? if legacy {
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        //?} else {
        stack.consume(1, player);
        //?}
        return InteractionResult.SUCCESS;
    }
}
