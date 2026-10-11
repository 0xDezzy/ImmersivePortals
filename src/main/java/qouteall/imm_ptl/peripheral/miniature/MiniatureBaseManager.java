package qouteall.imm_ptl.peripheral.miniature;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.PortalManipulation;
import qouteall.imm_ptl.core.portal.global_portals.GlobalPortalStorage;
import qouteall.q_misc_util.Helper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class MiniatureBaseManager {
    private static final String PREFIX = "imm_ptl:miniature/";
    private MiniatureBaseManager() {}
    public static void init() {
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Pre.class, event -> {
            if (event.getServer().getTickCount() % 20 == 0) reconcile(event.getServer());
        });
    }
    /** Rebuild from the single authority after restart or interrupted cross-dimension saves. */
    public static void reconcile(MinecraftServer server) {
        MiniatureBaseStorage registry = MiniatureBaseStorage.get(server);
        if (!registry.isAvailable()) return; // Never erase derived faces after a failed authoritative load.
        Map<ServerLevel, List<Portal>> expected = new HashMap<>();
        for (MiniatureBase base : registry.entries()) {
            if (!base.placed()) continue;
            ServerLevel backing = server.getLevel(base.backingDimension());
            ServerLevel exterior = server.getLevel(base.exteriorDimension());
            // Keep the durable reference when a dimension is unavailable. Never delete its blocks.
            if (backing == null || exterior == null) continue;
            for (Direction direction : Direction.values()) {
                Portal outer = PortalManipulation.createOrthodoxPortal(Portal.ENTITY_TYPE, exterior, backing,
                    direction, Helper.getBoxSurface(base.exterior(), direction), Helper.getBoxSurface(base.backing(), direction).getCenter());
                outer.setScaling(base.divisor());
                // Interiors retain ordinary entity sizes; return traversal does not apply a second scale.
                outer.setTeleportChangesScale(false);
                outer.setCrossPortalCollisionEnabled(false);
                outer.setFuseView(true);
                outer.renderingMergable = false;
                Portal inner = PortalManipulation.createReversePortal(outer, Portal.ENTITY_TYPE);
                inner.setFuseView(false);
                tag(outer, base, direction, "outer"); tag(inner, base, direction, "inner");
                expected.computeIfAbsent(exterior, key -> new ArrayList<>()).add(outer);
                expected.computeIfAbsent(backing, key -> new ArrayList<>()).add(inner);
            }
        }
        for (ServerLevel world : server.getAllLevels()) {
            GlobalPortalStorage storage = GlobalPortalStorage.get(world);
            List<Portal> wanted = expected.getOrDefault(world, List.of());
            var wantedIds = new HashSet<UUID>(); wanted.forEach(portal -> wantedIds.add(portal.getUUID()));
            var seen = new HashSet<UUID>();
            List<Portal> stale = storage.data.stream().filter(portal -> isManaged(portal)
                && (!wantedIds.contains(portal.getUUID()) || !seen.add(portal.getUUID()))).toList();
            stale.forEach(storage::removePortal);
            var present = new HashSet<UUID>(); storage.data.forEach(portal -> present.add(portal.getUUID()));
            for (Portal portal : wanted) if (!present.contains(portal.getUUID())) storage.addPortal(portal);
        }
    }
    private static boolean isManaged(Portal portal) { return portal.portalTag != null && portal.portalTag.startsWith(PREFIX); }
    private static void tag(Portal portal, MiniatureBase base, Direction direction, String side) {
        String identity = PREFIX + base.revision() + "/" + direction + "/" + side;
        portal.portalTag = identity;
        portal.setUUID(UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8)));
    }
    public static void validateWorld(ServerLevel world, AABB box) {
        if (box.minY < world.getMinBuildHeight() || box.maxY > world.getMaxBuildHeight()
            || !world.getWorldBorder().isWithinBounds(BlockPos.containing(box.minX, box.minY, box.minZ))
            || !world.getWorldBorder().isWithinBounds(BlockPos.containing(Math.nextDown(box.maxX), Math.nextDown(box.maxY), Math.nextDown(box.maxZ))))
            throw new IllegalArgumentException("The region must be inside the dimension's build height and world border.");
        for (int x = ((int)Math.floor(box.minX)) >> 4; x <= ((int)Math.floor(Math.nextDown(box.maxX))) >> 4; x++)
            for (int z = ((int)Math.floor(box.minZ)) >> 4; z <= ((int)Math.floor(Math.nextDown(box.maxZ))) >> 4; z++)
                if (!world.hasChunk(x, z)) throw new IllegalArgumentException("Load every chunk of the region before changing its miniature.");
    }
    public static void validatePlacement(MinecraftServer server, MiniatureBase base) {
        ServerLevel backing = server.getLevel(base.backingDimension()), exterior = server.getLevel(base.exteriorDimension());
        if (backing == null || exterior == null) throw new IllegalArgumentException("Both miniature dimensions must be available.");
        validateWorld(backing, base.backing()); validateWorld(exterior, base.exterior());
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(base.exterior().minX, base.exterior().minY, base.exterior().minZ),
            BlockPos.containing(Math.nextDown(base.exterior().maxX), Math.nextDown(base.exterior().maxY), Math.nextDown(base.exterior().maxZ)))) {
            if (!exterior.getBlockState(pos).isAir()) throw new IllegalArgumentException("The exterior miniature volume must contain only air.");
        }
        for (MiniatureBase other : MiniatureBaseStorage.get(server).entries()) {
            if (other.name().equals(base.name())) continue;
            if (overlaps(base.backingDimension(), base.backing(), other.backingDimension(), other.backing())
                || overlaps(base.exteriorDimension(), base.exterior(), other.backingDimension(), other.backing())
                || other.placed() && (overlaps(base.backingDimension(), base.backing(), other.exteriorDimension(), other.exterior())
                    || overlaps(base.exteriorDimension(), base.exterior(), other.exteriorDimension(), other.exterior())))
                throw new IllegalArgumentException("Miniature and backing regions must be separated from other managed regions.");
        }
    }
    private static boolean overlaps(Object dimension, AABB box, Object otherDimension, AABB other) {
        return dimension.equals(otherDimension) && box.inflate(1).intersects(other);
    }
}
