package io.github.tt432.dokimobs.item;

import io.github.tt432.dokimobs.Dokimobs;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
//? if legacy {
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
//?} else {
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
//?}

import java.util.function.Supplier;

/**
 * dokimobs 的物品与创造模式物品栏注册。
 * <p>
 * 字段统一声明成 {@link Supplier}：1.20.1 是 {@code RegistryObject}，1.21.1+ 是
 * {@code DeferredHolder/DeferredItem}，用 {@code Supplier} 才能三版本同一份声明。
 * 注册调用一律显式写类型实参（{@code ITEMS.<Item>register(...)}），否则推断出的
 * {@code Supplier<MoeDustItem>} 无法赋给 {@code Supplier<Item>}（泛型不变性）。
 *
 * @author TT432
 */
public final class DokimobsItems {
    //? if legacy {
    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, Dokimobs.MOD_ID);
    //?} else {
    private static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(Dokimobs.MOD_ID);
    //?}

    private static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Dokimobs.MOD_ID);

    /**
     * 萌化尘：对未萌化的生物使用 → 设为萌化 + 粉色粒子动画。
     */
    //? if legacy {
    public static final Supplier<Item> MOE_DUST =
            ITEMS.<Item>register("moe_dust", () -> new MoeDustItem(new Item.Properties()));
    //?} else {
    public static final Supplier<Item> MOE_DUST =
            ITEMS.<Item>registerItem("moe_dust", MoeDustItem::new);
    //?}

    /**
     * 模组自己的创造模式物品栏（排在原版栏位之后）。
     */
    public static final Supplier<CreativeModeTab> TAB = TABS.<CreativeModeTab>register(Dokimobs.MOD_ID,
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + Dokimobs.MOD_ID))
                    .icon(() -> new ItemStack(MOE_DUST.get()))
                    .displayItems((params, output) -> output.accept(MOE_DUST.get()))
                    .build());

    private DokimobsItems() {
    }

    /**
     * 挂到 mod 事件总线（必须在注册事件之前调用，即 mod 构造器内）。
     */
    public static void init(IEventBus modBus) {
        ITEMS.register(modBus);
        TABS.register(modBus);
    }
}
