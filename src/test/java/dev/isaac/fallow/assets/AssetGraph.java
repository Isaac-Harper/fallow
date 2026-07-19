package dev.isaac.fallow.assets;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.isaac.fallow.block.FallowBlocks;
import dev.isaac.fallow.item.FallowItems;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Shared plumbing for the asset-graph tests. Bootstraps the vanilla registries once (so touching
 * {@link FallowBlocks}/{@link FallowItems} populates {@code BuiltInRegistries}), reads main
 * resources off the test classpath, and parses JSON with Gson.
 *
 * <p>Every check is registry-backed: the mod's real registered block/item ids drive the assertions,
 * not a hand-maintained contract list.
 */
final class AssetGraph {
    static final String MOD_ID = "fallow";

    private static volatile boolean bootstrapped = false;

    private AssetGraph() {
    }

    /** Bootstrap the game registries and force Fallow's static registration exactly once. */
    static synchronized void ensureBootstrapped() {
        if (bootstrapped) {
            return;
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        FallowBlocks.register();
        FallowItems.register();
        bootstrapped = true;
    }

    /** Registered fallow block ids (registry paths), sorted. */
    static List<String> fallowBlockIds() {
        ensureBootstrapped();
        return BuiltInRegistries.BLOCK.keySet().stream()
            .filter(id -> id.getNamespace().equals(MOD_ID))
            .map(Identifier::getPath)
            .sorted()
            .collect(Collectors.toList());
    }

    /** Registered fallow item ids (registry paths), sorted. */
    static List<String> fallowItemIds() {
        ensureBootstrapped();
        return BuiltInRegistries.ITEM.keySet().stream()
            .filter(id -> id.getNamespace().equals(MOD_ID))
            .map(Identifier::getPath)
            .sorted()
            .collect(Collectors.toList());
    }

    /** True when an item id (vanilla or fallow, "namespace:path") is registered. */
    static boolean itemExists(String id) {
        ensureBootstrapped();
        Identifier parsed = tryParse(id);
        return parsed != null && BuiltInRegistries.ITEM.containsKey(parsed);
    }

    /** True when a block id (vanilla or fallow, "namespace:path") is registered. */
    static boolean blockExists(String id) {
        ensureBootstrapped();
        Identifier parsed = tryParse(id);
        return parsed != null && BuiltInRegistries.BLOCK.containsKey(parsed);
    }

    private static Identifier tryParse(String id) {
        try {
            return Identifier.parse(id);
        } catch (RuntimeException e) {
            return null;
        }
    }

    // --- Resource access ------------------------------------------------------------------------

    private static String absolute(String path) {
        return path.startsWith("/") ? path : "/" + path;
    }

    /** True when a classpath resource exists at {@code /path} (leading slash optional). */
    static boolean resourceExists(String path) {
        return AssetGraph.class.getResource(absolute(path)) != null;
    }

    /** Parse a classpath JSON resource; throws if it is missing or malformed. */
    static JsonObject readJson(String path) {
        try (InputStream in = AssetGraph.class.getResourceAsStream(absolute(path))) {
            if (in == null) {
                throw new IllegalStateException("resource not found: " + path);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read " + path, e);
        }
    }

    /**
     * List the leaf file names (with extension) directly under a classpath resource directory,
     * e.g. {@code listDir("assets/fallow/models/block")}. Returns an empty list if the directory
     * does not exist.
     */
    static List<String> listDir(String dir) {
        return dirCache.computeIfAbsent(dir, AssetGraph::scanDir);
    }

    private static final Map<String, List<String>> dirCache = new ConcurrentHashMap<>();

    private static List<String> scanDir(String dir) {
        String resource = absolute(dir);
        Enumeration<URL> urls;
        try {
            urls = AssetGraph.class.getClassLoader().getResources(resource.substring(1));
        } catch (IOException e) {
            throw new UncheckedIOException("failed to list " + dir, e);
        }
        TreeSet<String> names = new TreeSet<>();
        while (urls.hasMoreElements()) {
            URL url = urls.nextElement();
            collect(url, names);
        }
        return List.copyOf(names);
    }

    private static void collect(URL url, TreeSet<String> names) {
        try {
            URI uri = url.toURI();
            if ("jar".equals(uri.getScheme())) {
                // Split the jar URI ("jar:file:/x.jar!/assets/...") into the jar and the inner path.
                String raw = uri.getSchemeSpecificPart();
                int bang = raw.indexOf("!/");
                URI jarUri = URI.create("jar:" + raw.substring(0, bang));
                String inner = raw.substring(bang + 1);
                try (FileSystem fs = openZip(jarUri)) {
                    listInto(fs.getPath(inner), names);
                }
            } else {
                listInto(Path.of(uri), names);
            }
        } catch (Exception e) {
            throw new UncheckedIOException(new IOException("failed to list " + url, e));
        }
    }

    private static FileSystem openZip(URI jarUri) throws IOException {
        try {
            return FileSystems.newFileSystem(jarUri, Collections.emptyMap());
        } catch (java.nio.file.FileSystemAlreadyExistsException e) {
            return FileSystems.getFileSystem(jarUri);
        }
    }

    private static void listInto(Path dir, TreeSet<String> names) throws IOException {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(Files::isRegularFile)
                .forEach(p -> names.add(p.getFileName().toString()));
        }
    }

    /** File names without the given extension, e.g. ".json". */
    static List<String> stemsIn(String dir, String extension) {
        List<String> out = new ArrayList<>();
        for (String name : listDir(dir)) {
            if (name.endsWith(extension)) {
                out.add(name.substring(0, name.length() - extension.length()));
            }
        }
        return out;
    }

    // --- Model graph helpers --------------------------------------------------------------------

    /**
     * Resolve a fallow-namespaced model reference ("fallow:block/foo" or "fallow:item/bar") to its
     * resource path, or return null for non-fallow references (which are assumed vanilla).
     */
    static String modelResourcePath(String modelRef) {
        Identifier id = tryParse(modelRef);
        if (id == null || !id.getNamespace().equals(MOD_ID)) {
            return null;
        }
        return "assets/" + MOD_ID + "/models/" + id.getPath() + ".json";
    }

    /**
     * Resolve a fallow-namespaced texture reference ("fallow:block/foo") to its resource path, or
     * return null for non-fallow or {@code #alias} references.
     */
    static String textureResourcePath(String textureRef) {
        if (textureRef.startsWith("#")) {
            return null;
        }
        Identifier id = tryParse(textureRef);
        if (id == null || !id.getNamespace().equals(MOD_ID)) {
            return null;
        }
        return "assets/" + MOD_ID + "/textures/" + id.getPath() + ".png";
    }

    /** Collect every {@code namespace:path} texture value in a model's {@code textures} block. */
    static List<String> textureRefs(JsonObject model) {
        List<String> out = new ArrayList<>();
        JsonElement textures = model.get("textures");
        if (textures != null && textures.isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : textures.getAsJsonObject().entrySet()) {
                if (e.getValue().isJsonPrimitive()) {
                    out.add(e.getValue().getAsString());
                }
            }
        }
        return out;
    }

    /** The model's {@code parent} value, or null. */
    static String parentOf(JsonObject model) {
        JsonElement parent = model.get("parent");
        return parent != null && parent.isJsonPrimitive() ? parent.getAsString() : null;
    }
}
