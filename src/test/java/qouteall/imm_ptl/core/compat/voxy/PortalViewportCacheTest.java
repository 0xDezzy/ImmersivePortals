package qouteall.imm_ptl.core.compat.voxy;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PortalViewportCacheTest {
    private final Object original = new Object();
    private final List<Object> created = new ArrayList<>();
    private final List<Object> deleted = new ArrayList<>();

    private PortalViewportCache<List<String>, Object> cache(int limit) {
        return new PortalViewportCache<>(original, () -> {
            Object viewport = new Object();
            created.add(viewport);
            return viewport;
        }, viewport -> {
            assertNotSame(original, viewport, "Voxy must release its own default viewport");
            assertFalse(deleted.contains(viewport), "No viewport may be freed twice");
            deleted.add(viewport);
        }, limit);
    }

    @Test
    void fullPathsHaveIndependentPersistentStateAndLruEviction() {
        var cache = cache(2);
        var a = List.of("a");
        var nested = List.of("b", "a");
        Object first = cache.portal(a);
        Object second = cache.portal(nested);
        assertNotSame(first, second);
        assertSame(first, cache.portal(List.of("a")));
        cache.portal(List.of("c"));
        assertEquals(List.of(second), deleted, "Recently used a must outlive nested b/a");
        assertSame(original, cache.outer());
        cache.close();
        cache.close();
        assertEquals(created.size(), deleted.size());
    }

    @Test
    void reverseCrossingsTransferOwnershipWithoutDoubleFreeing() {
        var cache = cache(2);
        Object destination = cache.portal(List.of("a"));
        Object displaced = cache.portal(List.of("reverse"));
        cache.promote(List.of("a"), List.of("reverse"));
        assertSame(destination, cache.outer());
        assertSame(original, cache.portal(List.of("reverse")));
        assertEquals(List.of(displaced), deleted);
        cache.promote(List.of("reverse"), List.of("a"));
        assertSame(original, cache.outer());
        assertSame(destination, cache.portal(List.of("a")));
        cache.promote(List.of("a"), null);
        assertSame(destination, cache.outer());
        cache.close();
        assertEquals(created.size(), deleted.size());
    }

    @Test
    void unseenAndOneWayTeleportsCreateFreshStateAndReleaseOwnedOuter() {
        var cache = cache(1);
        cache.promote(List.of("unseen"), List.of("back"));
        Object promoted = cache.outer();
        assertNotSame(original, promoted);
        // Evicting a borrowed Voxy viewport must not delete it.
        cache.portal(List.of("other"));
        assertFalse(deleted.contains(original));
        cache.promote(List.of("another unseen"), null);
        assertTrue(deleted.contains(promoted));
        cache.close();
        assertEquals(created.size(), deleted.size());
    }

    @Test
    void liveOuterNeverBecomesTheLruVictim() {
        var cache = cache(1);
        Object destination = cache.portal(List.of("a"));
        cache.promote(List.of("a"), List.of("reverse"));
        for (int i = 0; i < 20; i++) cache.portal(List.of("portal" + i));
        assertSame(destination, cache.outer());
        assertFalse(deleted.contains(destination));
        cache.close();
        assertEquals(created.size(), deleted.size());
    }
}
