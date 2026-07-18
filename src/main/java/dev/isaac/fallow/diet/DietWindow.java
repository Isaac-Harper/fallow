package dev.isaac.fallow.diet;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Rolling window of meals eaten by one player. Pure Java - no Minecraft or codec imports; all
 * serialization is handled by {@link DietData}. Thread-unsafe by design: callers coordinate on
 * the server thread.
 *
 * <p>Each {@link Meal} records the diet groups covered by one eating event and the in-game day it
 * happened on. The window keeps the most recent {@code windowSize} meals; older entries are pruned
 * on push. Time-based expiry is applied separately via {@link #prune(long, long)}.
 */
public final class DietWindow {

    /** One eating event: the diet groups it covered and the in-game day it occurred. */
    public record Meal(Set<String> groups, long day) {
    }

    private final List<Meal> meals;

    public DietWindow() {
        this.meals = new ArrayList<>();
    }

    public DietWindow(List<Meal> meals) {
        this.meals = new ArrayList<>(meals);
    }

    /** Returns the raw meal list (for codec use in {@link DietData}). */
    public List<Meal> getMeals() {
        return meals;
    }

    /**
     * Records a new meal and trims the window to at most {@code windowSize} entries (dropping the
     * oldest). A no-op when {@code groups} is empty.
     */
    public void push(Set<String> groups, long day, int windowSize) {
        if (groups.isEmpty()) {
            return;
        }
        meals.add(new Meal(groups, day));
        while (meals.size() > windowSize) {
            meals.remove(0);
        }
    }

    /**
     * Removes meals whose in-game day is at least {@code expiryDays} behind {@code currentDay}.
     * Skipped entirely when {@code expiryDays} is 0 (time-based expiry disabled).
     */
    public void prune(long currentDay, long expiryDays) {
        if (expiryDays == 0) {
            return;
        }
        meals.removeIf(m -> (currentDay - m.day()) >= expiryDays);
    }

    /**
     * Returns the union of all groups in the current window, in encounter order (the order the
     * groups first appeared across the oldest-to-newest meal list).
     */
    public Set<String> distinctGroups() {
        Set<String> result = new LinkedHashSet<>();
        for (Meal m : meals) {
            result.addAll(m.groups());
        }
        return result;
    }

    /**
     * Returns the groups currently in the window that are not in {@code previousGroups}. Used to
     * detect which groups are newly covered after a push.
     */
    public Set<String> newGroups(Set<String> previousGroups) {
        Set<String> result = new LinkedHashSet<>(distinctGroups());
        result.removeAll(previousGroups);
        return result;
    }

    /** Number of meals currently stored in the window. */
    public int mealCount() {
        return meals.size();
    }
}
