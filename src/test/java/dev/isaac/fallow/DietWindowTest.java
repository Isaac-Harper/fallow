package dev.isaac.fallow;

import dev.isaac.fallow.diet.DietWindow;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DietWindowTest {

    private static Set<String> groups(String... ids) {
        return new java.util.LinkedHashSet<>(java.util.Arrays.asList(ids));
    }

    @Test
    void pushFillsWindow() {
        DietWindow w = new DietWindow();
        // Push 14 meals into a window of size 12; only the last 12 should remain.
        for (int i = 0; i < 14; i++) {
            w.push(groups("grain"), i, 12);
        }
        assertEquals(12, w.mealCount());
    }

    @Test
    void distinctGroupsCounts() {
        // Two meals with the same group should count as 1 distinct group.
        DietWindow w = new DietWindow();
        w.push(groups("grain"), 0, 12);
        w.push(groups("grain"), 1, 12);
        assertEquals(1, w.distinctGroups().size());
    }

    @Test
    void distinctGroupsAcrossMeals() {
        DietWindow w = new DietWindow();
        w.push(groups("grain"), 0, 12);
        w.push(groups("fruit", "vegetable"), 1, 12);
        Set<String> distinct = w.distinctGroups();
        assertEquals(3, distinct.size());
        assertTrue(distinct.contains("grain"));
        assertTrue(distinct.contains("fruit"));
        assertTrue(distinct.contains("vegetable"));
    }

    @Test
    void pruneByDayExpiry() {
        DietWindow w = new DietWindow();
        w.push(groups("grain"), 0, 12);  // day 0 - old
        w.push(groups("fruit"), 1, 12);  // day 1 - old
        w.push(groups("protein"), 5, 12); // day 5 - recent
        // With currentDay=4, expiryDays=3: meals from day 0 and 1 (age 4 and 3) should be pruned.
        w.prune(4, 3);
        assertEquals(1, w.mealCount());
        assertTrue(w.distinctGroups().contains("protein"));
    }

    @Test
    void pruneZeroDisablesExpiry() {
        DietWindow w = new DietWindow();
        w.push(groups("grain"), 0, 12);
        w.push(groups("fruit"), 1, 12);
        // expiryDays=0 means prune does nothing.
        w.prune(1000, 0);
        assertEquals(2, w.mealCount());
    }

    @Test
    void newGroupsDetection() {
        DietWindow w = new DietWindow();
        w.push(groups("grain", "fruit"), 0, 12);
        Set<String> previous = groups("grain"); // grain was already known
        Set<String> added = w.newGroups(previous);
        assertEquals(1, added.size());
        assertTrue(added.contains("fruit"));
    }

    @Test
    void emptyWindowScore() {
        DietWindow w = new DietWindow();
        assertEquals(0, w.distinctGroups().size());
    }

    @Test
    void windowCapAfterPush() {
        // Push windowSize+2 meals and verify the count caps at windowSize.
        int windowSize = 8;
        DietWindow w = new DietWindow();
        for (int i = 0; i < windowSize + 2; i++) {
            w.push(groups("grain"), i, windowSize);
        }
        assertEquals(windowSize, w.mealCount());
    }
}
