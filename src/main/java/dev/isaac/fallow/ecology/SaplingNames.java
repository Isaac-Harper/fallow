package dev.isaac.fallow.ecology;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure log-name -> sapling-name derivation (split out for unit testing). Works on registry
 * paths so any namespace follows its own convention: {@code acacia_log -> acacia_sapling},
 * {@code stripped_birch_wood -> birch_sapling}, {@code mangrove_log -> mangrove_propagule}
 * (via the fallback candidate). Names with no registered candidate (e.g. crimson_stem) just
 * never match - nether "trees" don't propagate, which is the intended behavior.
 */
public final class SaplingNames {
    private static final List<String> LOG_SUFFIXES = List.of("_log", "_wood", "_stem", "_hyphae");

    private SaplingNames() {
    }

    /** Ordered sapling-path candidates for a log path, or empty if it doesn't look like a log. */
    public static List<String> saplingCandidates(String logPath) {
        String base = logPath.startsWith("stripped_") ? logPath.substring("stripped_".length()) : logPath;
        for (String suffix : LOG_SUFFIXES) {
            if (base.endsWith(suffix)) {
                String species = base.substring(0, base.length() - suffix.length());
                List<String> candidates = new ArrayList<>(2);
                candidates.add(species + "_sapling");
                candidates.add(species + "_propagule");
                return candidates;
            }
        }
        return List.of();
    }
}
