package dev.isaac.fallow.assets;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.isaac.fallow.FallowConfig;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coherence checks between the config defaults / datapack content and the registries: config keys
 * must name registered ids, tag members and recipe ids must exist, and every block loot table must
 * match a registered block (and vice versa).
 *
 * <p>Registry-backed: item/block existence is resolved against the bootstrapped registries, so both
 * vanilla and fallow ids are validated.
 */
class ContentConfigTest {
    private static final String DATA = "data/fallow";

    @Test
    void configCropSeasonsReferenceRegisteredBlocks() {
        FallowConfig config = new FallowConfig();
        List<String> problems = new ArrayList<>();
        for (String blockId : config.crops.cropSeasons.keySet()) {
            if (!AssetGraph.blockExists(blockId)) {
                problems.add("crops.cropSeasons key is not a registered block: " + blockId);
            }
        }
        assertNoProblems("crops.cropSeasons", problems);
    }

    @Test
    void configWildHomesReferenceRegisteredBlocks() {
        FallowConfig config = new FallowConfig();
        List<String> problems = new ArrayList<>();
        for (String plantId : config.crops.wild.homes.keySet()) {
            if (!AssetGraph.blockExists(plantId)) {
                problems.add("crops.wild.homes key is not a registered block: " + plantId);
            }
        }
        assertNoProblems("crops.wild.homes", problems);
    }

    @Test
    void configFruitingTypesReferenceRegisteredItems() {
        FallowConfig config = new FallowConfig();
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, FallowConfig.Fruiting.FruitType> e : config.fruiting.types.entrySet()) {
            if (!AssetGraph.blockExists(e.getKey())) {
                problems.add("fruiting.types key is not a registered block: " + e.getKey());
            }
            if (!AssetGraph.itemExists(e.getValue().item)) {
                problems.add("fruiting.types[" + e.getKey() + "].item is not a registered item: "
                    + e.getValue().item);
            }
        }
        assertNoProblems("fruiting.types", problems);
    }

    @Test
    void itemTagMembersExist() {
        List<String> problems = new ArrayList<>();
        // jam_fruits is a fallow item tag consumed by the jam recipe; validate its members.
        String jamPath = DATA + "/tags/item/jam_fruits.json";
        for (String member : tagValues(AssetGraph.readJson(jamPath))) {
            checkTagMember(member, jamPath, problems);
        }
        assertNoProblems("item tags", problems);
    }

    @Test
    void recipeIdsExist() {
        List<String> problems = new ArrayList<>();
        for (String stem : AssetGraph.stemsIn(DATA + "/recipe", ".json")) {
            String path = DATA + "/recipe/" + stem + ".json";
            JsonObject recipe = AssetGraph.readJson(path);

            String result = recipeResultId(recipe);
            if (result != null && !AssetGraph.itemExists(result)) {
                problems.add(path + " -> result id is not a registered item: " + result);
            }
            for (String ingredient : recipeIngredientIds(recipe)) {
                if (ingredient.startsWith("#")) {
                    checkTagFileExists(ingredient, path, problems);
                } else if (!AssetGraph.itemExists(ingredient)) {
                    problems.add(path + " -> ingredient id is not a registered item: " + ingredient);
                }
            }
        }
        assertNoProblems("recipes", problems);
    }

    @Test
    void blockLootTablesMatchRegisteredBlocks() {
        Set<String> blockIds = new LinkedHashSet<>(AssetGraph.fallowBlockIds());
        Set<String> lootStems = new LinkedHashSet<>(AssetGraph.stemsIn(DATA + "/loot_table/blocks", ".json"));
        List<String> problems = new ArrayList<>();
        for (String id : blockIds) {
            if (!lootStems.contains(id)) {
                problems.add("registered block has no block loot table: " + id);
            }
        }
        for (String stem : lootStems) {
            if (!blockIds.contains(stem)) {
                problems.add("loot table references an unregistered block: " + stem);
            }
        }
        assertNoProblems("block loot tables", problems);
    }

    // --- helpers --------------------------------------------------------------------------------

    private static void checkTagMember(String member, String path, List<String> problems) {
        // Tag files may reference other tags with a leading '#'; only plain ids resolve to items.
        if (member.startsWith("#")) {
            checkTagFileExists(member, path, problems);
        } else if (!AssetGraph.itemExists(member)) {
            problems.add(path + " -> tag member is not a registered item: " + member);
        }
    }

    /**
     * A '#namespace:path' tag reference resolves to data/&lt;namespace&gt;/tags/item/&lt;path&gt;.json.
     * Only fallow tags live in the mod resources; vanilla/other tags are assumed present.
     */
    private static void checkTagFileExists(String tagRef, String origin, List<String> problems) {
        String id = tagRef.substring(1);
        int colon = id.indexOf(':');
        String namespace = colon >= 0 ? id.substring(0, colon) : "minecraft";
        String path = colon >= 0 ? id.substring(colon + 1) : id;
        if (!namespace.equals(AssetGraph.MOD_ID)) {
            return;
        }
        String resource = "data/" + namespace + "/tags/item/" + path + ".json";
        if (!AssetGraph.resourceExists(resource)) {
            problems.add(origin + " -> references a missing fallow tag file: " + resource);
        }
    }

    private static List<String> tagValues(JsonObject tag) {
        List<String> out = new ArrayList<>();
        JsonElement values = tag.get("values");
        if (values != null && values.isJsonArray()) {
            for (JsonElement e : values.getAsJsonArray()) {
                if (e.isJsonPrimitive()) {
                    out.add(e.getAsString());
                } else if (e.isJsonObject() && e.getAsJsonObject().has("id")) {
                    // Optional tag entry form: { "id": "...", "required": false }.
                    out.add(e.getAsJsonObject().get("id").getAsString());
                }
            }
        }
        return out;
    }

    private static String recipeResultId(JsonObject recipe) {
        JsonElement result = recipe.get("result");
        if (result == null) {
            return null;
        }
        if (result.isJsonObject() && result.getAsJsonObject().has("id")) {
            return result.getAsJsonObject().get("id").getAsString();
        }
        if (result.isJsonPrimitive()) {
            return result.getAsString();
        }
        return null;
    }

    /** Plain-string ingredient ids from shapeless {@code ingredients} and shaped {@code key}/cooking. */
    private static List<String> recipeIngredientIds(JsonObject recipe) {
        List<String> out = new ArrayList<>();
        JsonElement ingredients = recipe.get("ingredients");
        if (ingredients != null && ingredients.isJsonArray()) {
            for (JsonElement e : ingredients.getAsJsonArray()) {
                addIngredient(e, out);
            }
        }
        JsonElement key = recipe.get("key");
        if (key != null && key.isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : key.getAsJsonObject().entrySet()) {
                addIngredient(e.getValue(), out);
            }
        }
        addIngredient(recipe.get("ingredient"), out); // single-ingredient cooking recipes.
        return out;
    }

    private static void addIngredient(JsonElement ingredient, List<String> out) {
        if (ingredient == null) {
            return;
        }
        if (ingredient.isJsonPrimitive()) {
            out.add(ingredient.getAsString());
        } else if (ingredient.isJsonArray()) {
            for (JsonElement e : ingredient.getAsJsonArray()) {
                addIngredient(e, out);
            }
        } else if (ingredient.isJsonObject() && ingredient.getAsJsonObject().has("item")) {
            out.add(ingredient.getAsJsonObject().get("item").getAsString());
        }
    }

    private static void assertNoProblems(String label, List<String> problems) {
        assertTrue(problems.isEmpty(),
            () -> problems.size() + " " + label + " problem(s):\n  " + String.join("\n  ", problems));
    }
}
