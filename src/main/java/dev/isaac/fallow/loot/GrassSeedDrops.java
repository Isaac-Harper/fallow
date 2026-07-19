package dev.isaac.fallow.loot;

import dev.isaac.fallow.Fallow;
import dev.isaac.fallow.item.FallowItems;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;

/**
 * Adds one extra loot pool to the {@code minecraft:blocks/short_grass} and
 * {@code minecraft:blocks/tall_grass} loot tables: when the crop layer is active
 * ({@link CropsEnabledCondition}) and a random chance passes (config's seedDropChance),
 * drop one of the crop seeds at equal weight. Rice is deliberately excluded (it comes from
 * wild_rice only).
 *
 * <p>The {@code seedDropChance} constant is read from the config at the time this listener fires
 * (during loot-table rebuild, which happens at server start or {@code /reload}).
 * A {@code /fallow reload} does not rebuild loot tables and therefore does not re-read this value.
 */
public final class GrassSeedDrops {
    private static final ResourceKey<LootTable> SHORT_GRASS =
        ResourceKey.create(Registries.LOOT_TABLE,
            Identifier.withDefaultNamespace("blocks/short_grass"));
    private static final ResourceKey<LootTable> TALL_GRASS =
        ResourceKey.create(Registries.LOOT_TABLE,
            Identifier.withDefaultNamespace("blocks/tall_grass"));

    private GrassSeedDrops() {
    }

    /**
     * Register the {@link LootTableEvents#MODIFY} listener that injects the seed-drop pool.
     * Must be called after {@link CropsEnabledCondition#register()}.
     */
    public static void register() {
        LootTableEvents.MODIFY.register((key, tableBuilder, source, lookup) -> {
            if (key.equals(SHORT_GRASS) || key.equals(TALL_GRASS)) {
                tableBuilder.withPool(seedPool());
            }
        });
    }

    /**
     * One pool: conditions [crops_enabled, random_chance(seedDropChance)], entries: fifteen
     * LootItem entries at equal weight (pool rolls select among them uniformly).
     */
    private static LootPool.Builder seedPool() {
        float chance = (float) Fallow.CONFIG.crops.seedDropChance;
        // LootItemCondition.Builder is a functional interface: () -> LootItemCondition.
        return LootPool.lootPool()
            .setRolls(ConstantValue.exactly(1.0f))
            .when(() -> CropsEnabledCondition.INSTANCE)
            .when(LootItemRandomChanceCondition.randomChance(chance))
            .add(LootItem.lootTableItem(FallowItems.TURNIP_SEEDS))
            .add(LootItem.lootTableItem(FallowItems.CABBAGE_SEEDS))
            .add(LootItem.lootTableItem(FallowItems.PEA_SEEDS))
            .add(LootItem.lootTableItem(FallowItems.BARLEY_SEEDS))
            .add(LootItem.lootTableItem(FallowItems.RYE_SEEDS))
            .add(LootItem.lootTableItem(FallowItems.OAT_SEEDS))
            .add(LootItem.lootTableItem(FallowItems.RADISH_SEEDS))
            .add(LootItem.lootTableItem(FallowItems.PARSNIP_SEEDS))
            .add(LootItem.lootTableItem(FallowItems.LEEK_SEEDS))
            .add(LootItem.lootTableItem(FallowItems.FLAX_SEEDS))
            .add(LootItem.lootTableItem(FallowItems.PEPPER_SEEDS))
            .add(LootItem.lootTableItem(FallowItems.SQUASH_SEEDS))
            .add(LootItem.lootTableItem(FallowItems.TOMATO_SEEDS))
            .add(LootItem.lootTableItem(FallowItems.CUCUMBER_SEEDS))
            .add(LootItem.lootTableItem(FallowItems.CORN_SEEDS));
    }
}
