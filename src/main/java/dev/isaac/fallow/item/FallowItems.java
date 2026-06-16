package dev.isaac.fallow.item;

import dev.isaac.fallow.Fallow;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;

/**
 * The mod's items. Phase 4 ships one: the {@code season_clock} - a copper-and-redstone analogue of
 * the vanilla clock whose face shows the current Fallow season instead of the time of day. The
 * texture swap is entirely client-side (a custom item-model property; see
 * {@code dev.isaac.fallow.client.SeasonClockModelProperty}); the item itself is a plain {@link Item}.
 */
public final class FallowItems {
    /** Vanilla's "Tools &amp; Utilities" creative tab - same one the vanilla clock lives in. */
    private static final ResourceKey<CreativeModeTab> TOOLS_AND_UTILITIES =
        ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("tools_and_utilities"));

    public static final Item SEASON_CLOCK = register("season_clock", Item::new, new Item.Properties());

    private FallowItems() {
    }

    /** Resolve, build, and register an item, wiring its registry id into its properties. */
    private static Item register(String name, java.util.function.Function<Item.Properties, Item> factory,
                                 Item.Properties properties) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM,
            Identifier.fromNamespaceAndPath(Fallow.MOD_ID, name));
        Item item = factory.apply(properties.setId(key));
        return Registry.register(BuiltInRegistries.ITEM, key, item);
    }

    /** Touch the static fields (forcing registration) and place the items in their creative tabs. */
    public static void register() {
        CreativeModeTabEvents.modifyOutputEvent(TOOLS_AND_UTILITIES)
            .register(output -> output.accept(SEASON_CLOCK));
    }
}
