package qouteall.imm_ptl.core.compat.voxy;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Per-selector temporal state. Voxy retains ownership of its original viewport. */
public final class PortalViewportCache<K, V> {
    private final LinkedHashMap<K, V> portals = new LinkedHashMap<>(8, 0.75f, true);
    private final Set<V> owned = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Supplier<V> creator;
    private final Consumer<V> delete;
    private final int limit;
    private V outer;

    public PortalViewportCache(V original, Supplier<V> creator, Consumer<V> delete, int limit) {
        this.outer = original;
        this.creator = creator;
        this.delete = delete;
        this.limit = limit;
        if (limit < 1) throw new IllegalArgumentException("Viewport cache limit must be positive");
    }

    public V outer() {
        return outer;
    }

    public V portal(K path) {
        V viewport = portals.get(path);
        if (viewport == null) {
            viewport = create();
            portals.put(path, viewport);
            evict();
        }
        return viewport;
    }

    private V create() {
        V viewport = creator.get();
        owned.add(viewport);
        return viewport;
    }

    public void promote(K path, K reversePath) {
        V promoted = portals.remove(path);
        // An unseen destination still needs fresh temporal/occlusion state.
        if (promoted == null) promoted = create();
        V previous = outer;
        outer = promoted;
        if (reversePath == null) {
            release(previous);
        } else {
            V displaced = portals.put(reversePath, previous);
            if (displaced != previous) release(displaced);
            evict();
        }
    }

    private void evict() {
        while (portals.size() > limit) {
            var iterator = portals.entrySet().iterator();
            V viewport = iterator.next().getValue();
            iterator.remove();
            release(viewport);
        }
    }

    private void release(V viewport) {
        if (owned.remove(viewport)) delete.accept(viewport);
    }

    public void close() {
        // Identity ownership prevents double deletes after repeated reverse crossings.
        for (V viewport : owned) delete.accept(viewport);
        owned.clear();
        portals.clear();
        outer = null;
    }
}
