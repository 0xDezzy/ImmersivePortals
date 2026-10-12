package qouteall.imm_ptl.core.compat.voxy;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PortalRenderDistanceTrackerTest {
    private final Set<Long> live = new HashSet<>();
    private int additions;
    private int removals;

    private PortalRenderDistanceTracker<List<String>> tracker(int limit, int minY, int maxY) {
        return new PortalRenderDistanceTracker<>(minY, maxY, id -> {
            assertTrue(live.add(id), "Shared cells must be added once");
            additions++;
        }, id -> {
            assertTrue(live.remove(id), "Only the last camera may release a cell");
            removals++;
        }, limit);
    }

    private static long id(int x, int y, int z) {
        return PortalRenderDistanceTracker.sectionId(x, y, z);
    }

    @Test
    void overlappingCamerasHoldSharedCellsUntilTheLastReferenceLeaves() {
        var tracker = tracker(10, -1, 0);
        tracker.setRadius(0);
        tracker.process(null, 0, 0, 1, 0);
        tracker.process(List.of("a"), 0, 0, 1, 0);
        assertEquals(2, additions);
        tracker.process(null, 512, 0, 10, 1);
        assertEquals(0, removals);
        assertEquals(Set.of(id(0, -1, 0), id(0, 0, 0), id(1, -1, 0), id(1, 0, 0)), live);
        tracker.process(List.of("a"), 512, 0, 10, 2);
        assertEquals(2, removals);
        assertEquals(Set.of(id(1, -1, 0), id(1, 0, 0)), live);
    }

    @Test
    void floorCoordinatesAndSectionEncodingMatchVoxy() {
        var tracker = tracker(10, -2, -2);
        tracker.setRadius(0);
        tracker.process(null, -0.5, -512.5, 10, 0);
        assertEquals(Set.of(id(-1, -2, -2)), live);
        assertEquals(0x4FEFFFFFEFFFFFF0L, id(-1, -2, -2));
    }

    @Test
    void shrinkingWhileUpdatesArePendingCancelsUnsentAdds() {
        var tracker = tracker(10, 0, 0);
        tracker.setRadius(1); // disk has five cells
        assertTrue(tracker.process(null, 0, 0, 1, 0));
        assertEquals(1, additions);
        tracker.setRadius(0);
        while (tracker.process(null, 0, 0, 1, 1)) {}
        assertEquals(Set.of(id(0, 0, 0)), live);
        assertTrue(additions <= 2, "Shrinking must discard unsent requests rather than drain them");
        assertFalse(tracker.process(null, 0, 0, 1, 2));
    }

    @Test
    void radiusChangesApplyToEveryCameraWithoutLosingOverlap() {
        var tracker = tracker(10, 0, 0);
        tracker.setRadius(1);
        tracker.process(null, 0, 0, 100, 0);
        tracker.process(List.of("a"), 512, 0, 100, 0);
        assertEquals(8, live.size());
        tracker.setRadius(0);
        tracker.process(null, 0, 0, 100, 1);
        tracker.process(List.of("a"), 512, 0, 100, 1);
        assertEquals(Set.of(id(0, 0, 0), id(1, 0, 0)), live);
    }

    @Test
    void visibleTeleportPromotesRequestsWithoutAnUnloadReloadCycle() {
        var tracker = tracker(10, 0, 0);
        tracker.setRadius(0);
        tracker.process(null, 0, 0, 10, 0);
        tracker.process(List.of("a"), 4096, 0, 10, 0);
        tracker.promote(List.of("a"), List.of("reverse"));
        assertFalse(tracker.process(null, 4096, 0, 10, 1));
        assertFalse(tracker.process(List.of("reverse"), 0, 0, 10, 1));
        assertEquals(2, additions);
        assertEquals(0, removals);
        tracker.promote(List.of("reverse"), List.of("a"));
        assertFalse(tracker.process(null, 0, 0, 10, 2));
        assertEquals(0, removals);
    }

    @Test
    void unseenTeleportDoesNotAliasTheOuterAndReverseViews() {
        var tracker = tracker(10, 0, 0);
        tracker.setRadius(0);
        tracker.process(null, 0, 0, 10, 0);
        tracker.promote(List.of("unseen"), List.of("reverse"));
        tracker.process(null, 4096, 0, 10, 1);
        assertFalse(tracker.process(List.of("reverse"), 0, 0, 10, 1));
        assertEquals(Set.of(id(0, 0, 0), id(8, 0, 0)), live);
        tracker.promote(List.of("missing"), null);
        assertEquals(Set.of(id(0, 0, 0)), live, "One-way crossing releases the old outer only");
    }

    @Test
    void idleAndLruEvictionReleaseOnlyTheirOwnActiveRequests() {
        var tracker = tracker(1, 0, 0);
        tracker.setRadius(0);
        tracker.process(null, 0, 0, 10, 0);
        tracker.process(List.of("a"), 512, 0, 10, 0);
        tracker.process(List.of("b", "a"), 1024, 0, 10, 1);
        assertEquals(Set.of(id(0, 0, 0), id(2, 0, 0)), live);
        tracker.process(null, 0, 0, 10, 30_000_000_002L);
        assertEquals(Set.of(id(0, 0, 0)), live);
        assertEquals(2, removals);
    }

    @Test
    void repeatedUnseenTeleportsBoundReverseViewsEvenWithoutPortalRendering() {
        var tracker = tracker(1, 0, 0);
        tracker.setRadius(0);
        tracker.process(null, 0, 0, 10, 0);
        for (int crossing = 1; crossing <= 20; crossing++) {
            tracker.promote(List.of("unseen" + crossing), List.of("reverse" + crossing));
            tracker.process(null, crossing * 512.0, 0, 10, crossing);
            assertEquals(Set.of(id(crossing - 1, 0, 0), id(crossing, 0, 0)), live);
        }
    }

    @Test
    void updateBudgetCountsCellsNotVerticalSectionsAndDoesNotInventWork() {
        var tracker = tracker(10, -1, 1);
        tracker.setRadius(1);
        assertFalse(tracker.process(null, 0, 0, 0, 0));
        assertTrue(tracker.process(null, 0, 0, 2, 0));
        assertEquals(6, additions);
        while (tracker.process(null, 0, 0, 2, 0)) {}
        assertEquals(15, additions);
        assertFalse(tracker.process(null, 0, 0, 2, 0));
    }

    @Test
    void randomCameraMotionAndRadiusChangesMatchAnIndependentUnionModel() {
        var tracker = tracker(10, -1, 0);
        var cameras = new java.util.HashMap<List<String>, int[]>();
        var random = new java.util.Random(0xA11CE);
        int radius = 0;
        tracker.setRadius(radius);
        for (int tick = 0; tick < 400; tick++) {
            int camera = random.nextInt(5);
            List<String> key = camera == 0 ? null : List.of("portal" + camera);
            int x = random.nextInt(13) - 6;
            int z = random.nextInt(13) - 6;
            cameras.put(key, new int[]{x, z});
            if (tick % 7 == 0) {
                radius = random.nextInt(4);
                tracker.setRadius(radius);
                for (var entry : cameras.entrySet()) {
                    tracker.process(entry.getKey(), entry.getValue()[0] * 512.0,
                        entry.getValue()[1] * 512.0, 1000, tick);
                }
            } else {
                tracker.process(key, x * 512.0, z * 512.0, 1000, tick);
            }
            Set<Long> expected = new HashSet<>();
            for (int[] center : cameras.values()) {
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (dx * dx + dz * dz <= radius * radius) {
                            expected.add(id(center[0] + dx, -1, center[1] + dz));
                            expected.add(id(center[0] + dx, 0, center[1] + dz));
                        }
                    }
                }
            }
            assertEquals(expected, live, "Union mismatch after camera update " + tick);
        }
    }
}
