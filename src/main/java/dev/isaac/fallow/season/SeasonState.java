package dev.isaac.fallow.season;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.isaac.fallow.api.Season;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * The whole persisted footprint of seasons: one small record in the overworld's saved data
 * ({@code data/fallow_seasons.dat}). No per-block or per-chunk state anywhere in the mod, so
 * removing Fallow leaves nothing behind but this orphaned (harmless) file.
 *
 * <p>{@code lastDay} is the overworld-clock day index at the last advance, used to detect day
 * rollovers (including multi-day jumps from {@code /time add}). A value of -1 means "not yet
 * initialized": the first tick adopts the current day without advancing, so installing Fallow
 * into an existing world starts cleanly at day 0 of the configured starting season.
 */
public final class SeasonState extends SavedData {
    public static final Codec<SeasonState> CODEC = RecordCodecBuilder.create(i -> i.group(
        Season.CODEC.fieldOf("season").forGetter(s -> s.season),
        Codec.INT.fieldOf("day_in_season").forGetter(s -> s.dayInSeason),
        Codec.LONG.fieldOf("last_day").forGetter(s -> s.lastDay)
    ).apply(i, SeasonState::new));

    // Saved data created at the current data version never gets datafixed in practice, but the
    // type requires a DataFixTypes; RANDOM_SEQUENCES is the narrowest vanilla surface.
    public static final SavedDataType<SeasonState> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath("fallow", "seasons"),
        SeasonState::new,
        CODEC,
        DataFixTypes.SAVED_DATA_RANDOM_SEQUENCES
    );

    private Season season;
    private int dayInSeason;
    private long lastDay;

    public SeasonState() {
        this(Season.SPRING, 0, -1L);
    }

    public SeasonState(Season season, int dayInSeason, long lastDay) {
        this.season = season;
        this.dayInSeason = dayInSeason;
        this.lastDay = lastDay;
    }

    public static SeasonState get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public Season season() {
        return season;
    }

    public int dayInSeason() {
        return dayInSeason;
    }

    /** Admin/test override (the {@code /fallow season set} command). */
    public void set(Season season, int dayInSeason) {
        this.season = season;
        this.dayInSeason = Math.max(0, dayInSeason);
        setDirty();
    }

    /**
     * Advance to the current overworld-clock day. Time moving backwards (e.g. {@code /time set 0})
     * re-anchors without advancing, so seasons never run backwards or double-advance afterwards.
     */
    public void observeDay(long nowDay, int daysPerSeason) {
        SeasonMath.Advance result = SeasonMath.advance(season, dayInSeason, lastDay, nowDay, daysPerSeason);
        if (result.changed()) {
            season = result.season();
            dayInSeason = result.dayInSeason();
            lastDay = result.lastDay();
            setDirty();
        }
    }
}
