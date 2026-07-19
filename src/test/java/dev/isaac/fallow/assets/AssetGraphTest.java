package dev.isaac.fallow.assets;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cross-checks the client asset graph against the mod's registered content: every block/item has a
 * resolvable blockstate/model/texture chain, no texture is orphaned, and the lang file covers every
 * id without carrying stale keys.
 *
 * <p>Registry-backed: {@link AssetGraph#fallowBlockIds()} / {@link AssetGraph#fallowItemIds()} read
 * the live registries after bootstrap, so any added block or item is picked up automatically.
 */
class AssetGraphTest {
    private static final String ASSETS = "assets/fallow";

    // Textures that are intentionally not wired into a blockstate-reachable model.
    // - season_clock_* : the 32 range-dispatch clock frames (referenced by their own item models).
    // - *leaves* : seasonal foliage tints applied programmatically for the season swap, not via a
    //   registered block's blockstate.
    private static boolean isExemptOrphan(String texturePath) {
        return texturePath.contains("season_clock_") || texturePath.contains("leaves");
    }

    @Test
    void everyBlockHasResolvingBlockstateAndModelChain() {
        List<String> problems = new ArrayList<>();
        for (String id : AssetGraph.fallowBlockIds()) {
            String blockstatePath = ASSETS + "/blockstates/" + id + ".json";
            if (!AssetGraph.resourceExists(blockstatePath)) {
                problems.add("missing blockstate: " + blockstatePath);
                continue;
            }
            JsonObject blockstate = AssetGraph.readJson(blockstatePath);
            for (String modelRef : blockstateModelRefs(blockstate)) {
                resolveModelChain(modelRef, "blockstate " + id, problems);
            }
        }
        assertNoProblems("block asset chain", problems);
    }

    @Test
    void everyItemHasResolvingModelChain() {
        List<String> problems = new ArrayList<>();
        for (String id : AssetGraph.fallowItemIds()) {
            String itemPath = ASSETS + "/items/" + id + ".json";
            if (!AssetGraph.resourceExists(itemPath)) {
                problems.add("missing item model definition: " + itemPath);
                continue;
            }
            JsonObject item = AssetGraph.readJson(itemPath);
            for (String modelRef : itemModelRefs(item)) {
                resolveModelChain(modelRef, "item " + id, problems);
            }
        }
        assertNoProblems("item asset chain", problems);
    }

    @Test
    void noOrphanTextures() {
        Set<String> referenced = referencedTexturePaths();
        List<String> problems = new ArrayList<>();
        for (String sub : List.of("block", "item")) {
            for (String stem : AssetGraph.stemsIn(ASSETS + "/textures/" + sub, ".png")) {
                String path = ASSETS + "/textures/" + sub + "/" + stem + ".png";
                if (isExemptOrphan(path)) {
                    continue;
                }
                if (!referenced.contains(path)) {
                    problems.add("orphan texture (no model references it): " + path);
                }
            }
        }
        assertNoProblems("orphan textures", problems);
    }

    @Test
    void langCoversEveryIdWithoutStaleKeys() {
        JsonObject lang = AssetGraph.readJson(ASSETS + "/lang/en_us.json");
        List<String> problems = new ArrayList<>();

        for (String id : AssetGraph.fallowBlockIds()) {
            String key = "block.fallow." + id;
            if (!lang.has(key)) {
                problems.add("missing lang key: " + key);
            }
        }
        for (String id : AssetGraph.fallowItemIds()) {
            String key = "item.fallow." + id;
            if (!lang.has(key)) {
                problems.add("missing lang key: " + key);
            }
        }

        Set<String> blockIds = new LinkedHashSet<>(AssetGraph.fallowBlockIds());
        Set<String> itemIds = new LinkedHashSet<>(AssetGraph.fallowItemIds());
        for (String key : lang.keySet()) {
            if (key.startsWith("block.fallow.") && !blockIds.contains(key.substring("block.fallow.".length()))) {
                problems.add("stale lang key references an unregistered block: " + key);
            } else if (key.startsWith("item.fallow.") && !itemIds.contains(key.substring("item.fallow.".length()))) {
                problems.add("stale lang key references an unregistered item: " + key);
            }
        }
        assertNoProblems("lang completeness", problems);
    }

    // --- model-graph resolution -----------------------------------------------------------------

    /** Walk a fallow model reference: the model file, its parent (if fallow), and its textures. */
    private static void resolveModelChain(String modelRef, String origin, List<String> problems) {
        String modelPath = AssetGraph.modelResourcePath(modelRef);
        if (modelPath == null) {
            return; // vanilla (or other) model, assumed present.
        }
        if (!AssetGraph.resourceExists(modelPath)) {
            problems.add(origin + " -> missing model: " + modelPath + " (ref " + modelRef + ")");
            return;
        }
        JsonObject model = AssetGraph.readJson(modelPath);

        String parent = AssetGraph.parentOf(model);
        if (parent != null) {
            String parentPath = AssetGraph.modelResourcePath(parent);
            if (parentPath != null && !AssetGraph.resourceExists(parentPath)) {
                problems.add(modelPath + " -> missing fallow parent model: " + parentPath);
            }
        }

        for (String textureRef : AssetGraph.textureRefs(model)) {
            String texturePath = AssetGraph.textureResourcePath(textureRef);
            if (texturePath != null && !AssetGraph.resourceExists(texturePath)) {
                problems.add(modelPath + " -> missing texture: " + texturePath + " (ref " + textureRef + ")");
            }
        }
    }

    /** Every model referenced by a blockstate (variants and multipart apply entries). */
    private static List<String> blockstateModelRefs(JsonObject blockstate) {
        List<String> refs = new ArrayList<>();
        JsonElement variants = blockstate.get("variants");
        if (variants != null && variants.isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : variants.getAsJsonObject().entrySet()) {
                addVariantModels(e.getValue(), refs);
            }
        }
        JsonElement multipart = blockstate.get("multipart");
        if (multipart != null && multipart.isJsonArray()) {
            for (JsonElement part : multipart.getAsJsonArray()) {
                if (part.isJsonObject()) {
                    addVariantModels(part.getAsJsonObject().get("apply"), refs);
                }
            }
        }
        return refs;
    }

    private static void addVariantModels(JsonElement variant, List<String> refs) {
        if (variant == null) {
            return;
        }
        if (variant.isJsonArray()) {
            for (JsonElement each : variant.getAsJsonArray()) {
                addVariantModels(each, refs);
            }
        } else if (variant.isJsonObject()) {
            JsonElement model = variant.getAsJsonObject().get("model");
            if (model != null && model.isJsonPrimitive()) {
                refs.add(model.getAsString());
            }
        }
    }

    /** Every model referenced by an item model definition (plain, range_dispatch, composite, ...). */
    private static List<String> itemModelRefs(JsonObject item) {
        List<String> refs = new ArrayList<>();
        collectItemModelRefs(item.get("model"), refs);
        return refs;
    }

    private static void collectItemModelRefs(JsonElement node, List<String> refs) {
        if (node == null || !node.isJsonObject()) {
            return;
        }
        JsonObject obj = node.getAsJsonObject();
        JsonElement model = obj.get("model");
        if (model != null && model.isJsonPrimitive()) {
            refs.add(model.getAsString());
        }
        // Recurse into any nested model-bearing structures (entries, fallback, cases, ...).
        for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
            JsonElement value = e.getValue();
            if (value.isJsonObject()) {
                collectItemModelRefs(value, refs);
            } else if (value.isJsonArray()) {
                for (JsonElement each : value.getAsJsonArray()) {
                    collectItemModelRefs(each, refs);
                }
            }
        }
    }

    /** All texture resource paths reachable from any model file under models/block and models/item. */
    private static Set<String> referencedTexturePaths() {
        Set<String> paths = new TreeSet<>();
        for (String sub : List.of("block", "item")) {
            for (String stem : AssetGraph.stemsIn(ASSETS + "/models/" + sub, ".json")) {
                JsonObject model = AssetGraph.readJson(ASSETS + "/models/" + sub + "/" + stem + ".json");
                for (String textureRef : AssetGraph.textureRefs(model)) {
                    String texturePath = AssetGraph.textureResourcePath(textureRef);
                    if (texturePath != null) {
                        paths.add(texturePath);
                    }
                }
            }
        }
        return paths;
    }

    private static void assertNoProblems(String label, List<String> problems) {
        assertTrue(problems.isEmpty(),
            () -> problems.size() + " " + label + " problem(s):\n  " + String.join("\n  ", problems));
    }
}
