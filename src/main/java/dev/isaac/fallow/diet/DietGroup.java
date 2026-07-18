package dev.isaac.fallow.diet;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The six diet groups used by the diet window mechanic. Each group maps to an item tag under
 * {@code fallow:diet/<id>} (e.g. {@code fallow:diet/grain}).
 */
public enum DietGroup {
    GRAIN("grain"),
    VEGETABLE("vegetable"),
    FRUIT("fruit"),
    PROTEIN("protein"),
    FUNGI("fungi"),
    SUGAR_OIL("sugar_oil");

    private final String id;

    DietGroup(String id) {
        this.id = id;
    }

    /** The tag path segment used to build this group's item tag key. */
    public String id() {
        return id;
    }

    /**
     * Returns every {@link DietGroup} that {@code stack}'s item belongs to, by checking each
     * group's {@code fallow:diet/<id>} item tag. An item may belong to multiple groups.
     */
    public static Set<DietGroup> groupsOf(ItemStack stack, Level level) {
        Set<DietGroup> result = new LinkedHashSet<>();
        for (DietGroup group : values()) {
            TagKey<net.minecraft.world.item.Item> tagKey = TagKey.create(
                Registries.ITEM,
                Identifier.fromNamespaceAndPath("fallow", "diet/" + group.id())
            );
            if (stack.is(tagKey)) {
                result.add(group);
            }
        }
        return result;
    }
}
