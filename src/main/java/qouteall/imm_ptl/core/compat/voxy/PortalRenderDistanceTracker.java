package qouteall.imm_ptl.core.compat.voxy;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.LinkedHashMap;
import java.util.function.LongConsumer;

/** Keeps the union of camera requests alive in one Voxy world/renderer. */
public final class PortalRenderDistanceTracker<K> {
    private static final long IDLE_NANOS = 30_000_000_000L;
    private final LinkedHashMap<K, View> portals = new LinkedHashMap<>(8, 0.75f, true);
    private final Long2IntOpenHashMap references = new Long2IntOpenHashMap();
    private final LongConsumer add;
    private final LongConsumer remove;
    private final int minY;
    private final int maxY;
    private final int limit;
    private int radius = 2;
    private View outer = new View();

    public PortalRenderDistanceTracker(int minY, int maxY, LongConsumer add, LongConsumer remove, int limit) {
        this.minY = minY;
        this.maxY = maxY;
        this.add = add;
        this.remove = remove;
        this.limit = limit;
        if (limit < 1) throw new IllegalArgumentException("View cache limit must be positive");
    }

    public void setRadius(int radius) {
        if (radius < 0) throw new IllegalArgumentException("Negative render distance");
        if (this.radius == radius) return;
        this.radius = radius;
        outer.rebuild();
        portals.values().forEach(View::rebuild);
    }

    /** Null path denotes the outer camera; portal keys are immutable full paths. */
    public boolean process(K path, double x, double z, int budget, long now) {
        discardIdle(now);
        View view;
        if (path == null) {
            view = outer;
        } else {
            view = portals.computeIfAbsent(path, key -> new View());
            evict();
        }
        view.lastSeen = now;
        // Floor before conversion: truncation maps -0.5 to the wrong 512-block cell.
        int cellX = (int) Math.floor(x / 512.0);
        int cellZ = (int) Math.floor(z / 512.0);
        if (!view.initialized || view.x != cellX || view.z != cellZ) {
            view.x = cellX;
            view.z = cellZ;
            view.initialized = true;
            view.rebuild();
        }
        return view.drain(Math.max(0, budget));
    }

    public void promote(K path, K reversePath) {
        View promoted = portals.remove(path);
        View previous = outer;
        // Never alias the old outer view into both keys when the destination was unseen.
        outer = promoted == null ? new View() : promoted;
        if (reversePath == null) {
            previous.clear();
        } else {
            View displaced = portals.put(reversePath, previous);
            if (displaced != null && displaced != previous) displaced.clear();
            evict();
        }
    }

    private void evict() {
        while (portals.size() > limit) {
            var iterator = portals.entrySet().iterator();
            View evicted = iterator.next().getValue();
            iterator.remove();
            evicted.clear();
        }
    }

    private void discardIdle(long now) {
        var iterator = portals.values().iterator();
        while (iterator.hasNext()) {
            View view = iterator.next();
            if (now - view.lastSeen > IDLE_NANOS) {
                iterator.remove();
                view.clear();
            }
        }
    }

    private void reference(long cell, int delta) {
        int previous = references.get(cell);
        int next = previous + delta;
        if (next < 0) throw new IllegalStateException("Unbalanced Voxy camera request");
        if (next == 0) references.remove(cell);
        else references.put(cell, next);
        if (previous == 0 || next == 0) {
            LongConsumer callback = next == 0 ? remove : add;
            int x = (int) cell;
            int z = (int) (cell >>> 32);
            for (int y = minY; y <= maxY; y++) callback.accept(sectionId(x, y, z));
        }
    }

    // Voxy WorldEngine.getWorldSectionId at level 4. No link to optional Voxy classes.
    public static long sectionId(int x, int y, int z) {
        return (4L << 60) | ((y & 0xFFL) << 52) | ((z & 0xFFFFFFL) << 28) | ((x & 0xFFFFFFL) << 4);
    }

    private final class View {
        private final LongOpenHashSet active = new LongOpenHashSet();
        private final LongOpenHashSet additions = new LongOpenHashSet();
        private final LongOpenHashSet removals = new LongOpenHashSet();
        private int x;
        private int z;
        private boolean initialized;
        private long lastSeen;

        private void rebuild() {
            if (!initialized) return;
            additions.clear();
            removals.clear();
            removals.addAll(active);
            long radiusSquared = (long) radius * radius;
            for (int dx = -radius; dx <= radius; dx++) {
                int bound = (int) Math.floor(Math.sqrt(radiusSquared - (long) dx * dx));
                for (int dz = -bound; dz <= bound; dz++) {
                    long cell = Integer.toUnsignedLong(x + dx) | (Integer.toUnsignedLong(z + dz) << 32);
                    removals.remove(cell);
                    if (!active.contains(cell)) additions.add(cell);
                }
            }
        }

        private boolean drain(int budget) {
            boolean changed = false;
            while (budget > 0 && (!removals.isEmpty() || !additions.isEmpty())) {
                boolean removing = !removals.isEmpty();
                var iterator = (removing ? removals : additions).iterator();
                long cell = iterator.nextLong();
                iterator.remove();
                if (removing) active.remove(cell);
                else active.add(cell);
                reference(cell, removing ? -1 : 1);
                budget--;
                changed = true;
            }
            return changed;
        }

        private void clear() {
            for (long cell : active) reference(cell, -1);
            active.clear();
            additions.clear();
            removals.clear();
        }
    }
}
