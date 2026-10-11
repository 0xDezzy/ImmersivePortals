package qouteall.imm_ptl.core.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import qouteall.imm_ptl.core.portal.global_portals.GlobalPortalStorage;
import qouteall.imm_ptl.peripheral.miniature.MiniatureBase;
import qouteall.imm_ptl.peripheral.miniature.MiniatureBaseManager;
import qouteall.imm_ptl.peripheral.miniature.MiniatureBaseStorage;
import java.util.UUID;

@GameTestHolder("sable")
public final class MiniatureBaseGameTest {
    @PrefixGameTestTemplate(false)
    @GameTest(template = "physicstest.gravity", timeoutTicks = 40)
    public static void miniatureLifecyclePreservesOriginalInventoryAndRebuildsViews(GameTestHelper helper) {
        var world = helper.getLevel(); var server = world.getServer();
        var registry = MiniatureBaseStorage.get(server); var portals = GlobalPortalStorage.get(world);
        UUID owner = UUID.randomUUID(); String name = "test_" + owner.toString().substring(0, 8);
        BlockPos origin = helper.absolutePos(new BlockPos(1, 64, 1));
        BlockPos chestPos = origin.offset(1, 1, 1);
        world.setBlockAndUpdate(chestPos, Blocks.CHEST.defaultBlockState());
        ChestBlockEntity chest = (ChestBlockEntity) world.getBlockEntity(chestPos);
        chest.setItem(0, new ItemStack(Items.DIAMOND, 17));
        MiniatureBase base = new MiniatureBase(name, owner, world.dimension(), new AABB(Vec3.atLowerCornerOf(origin), Vec3.atLowerCornerOf(origin.offset(4, 4, 4))),
            world.dimension(), Vec3.atLowerCornerOf(origin.offset(10, 0, 0)), 2.5, true, UUID.randomUUID());
        String tagPrefix = "imm_ptl:miniature/" + base.revision() + "/";
        try {
            registry.create(base); MiniatureBaseManager.reconcile(server);
            helper.assertTrue(portals.data.stream().filter(p -> p.portalTag != null && p.portalTag.startsWith(tagPrefix)).count() == 12, "Six bidirectional faces must exist");
            helper.assertTrue(portals.data.stream().filter(p -> p.portalTag != null && p.portalTag.startsWith(tagPrefix)).allMatch(p -> !p.isTeleportChangesScale()), "Entry and exit must not double-scale occupants");
            MiniatureBaseStorage loaded = MiniatureBaseStorage.load(registry.save(new CompoundTag(), world.registryAccess()));
            helper.assertTrue(loaded.owned(name, owner).equals(base), "Registry must retain every backing and placement field through reload");
            // Simulate a missing derived portal after a partial save: repair once, never duplicate.
            var one = portals.data.stream().filter(p -> p.portalTag != null && p.portalTag.startsWith(tagPrefix)).findFirst().orElseThrow();
            portals.removePortal(one); MiniatureBaseManager.reconcile(server); MiniatureBaseManager.reconcile(server);
            helper.assertTrue(portals.data.stream().filter(p -> p.portalTag != null && p.portalTag.startsWith(tagPrefix)).count() == 12, "Repair must be idempotent");
            registry.replace(base.pickup(), owner); MiniatureBaseManager.reconcile(server);
            helper.assertTrue(portals.data.stream().noneMatch(p -> p.portalTag != null && p.portalTag.startsWith(tagPrefix)), "Pickup must remove all derived faces");
            helper.assertTrue(world.getBlockEntity(chestPos) == chest && chest.getItem(0).getCount() == 17, "Pickup must keep the same live inventory without copying");
            registry.replace(base.pickup().place(world.dimension(), base.bottomCenter().add(0, 0, 8)), owner);
            MiniatureBaseManager.reconcile(server); registry.remove(name, owner); MiniatureBaseManager.reconcile(server);
            helper.assertTrue(world.getBlockEntity(chestPos) == chest && chest.getItem(0).is(Items.DIAMOND) && chest.getItem(0).getCount() == 17, "Restoration must preserve the original inventory");
        } finally {
            if (registry.entries().stream().anyMatch(b -> b.name().equals(name))) registry.remove(name, owner);
            MiniatureBaseManager.reconcile(server); world.removeBlock(chestPos, false);
        }
        helper.succeed();
    }
    @PrefixGameTestTemplate(false)
    @GameTest(template = "physicstest.gravity", timeoutTicks = 40)
    public static void allThirtyTwoMiniatureDestinationsRemainInChunkLoading(GameTestHelper helper) {
        var world = helper.getLevel(); var server = world.getServer(); var registry = MiniatureBaseStorage.get(server);
        UUID owner = UUID.randomUUID(); var names = new java.util.ArrayList<String>();
        var player = net.neoforged.neoforge.common.util.FakePlayerFactory.get(world,
            new com.mojang.authlib.GameProfile(owner, "MiniatureLoaders"));
        player.setPos(10000, 82, 10000);
        var info = qouteall.imm_ptl.core.chunk_loading.ImmPtlChunkTracking.getPlayerInfo(player);
        info.performanceLevel = qouteall.imm_ptl.core.chunk_loading.PerformanceLevel.good;
        try {
            for (int i = 0; i < 32; i++) {
                String name = "load_" + owner.toString().substring(0, 8) + "_" + i; names.add(name);
                registry.create(new MiniatureBase(name, owner, world.dimension(),
                    new AABB(11000 + i * 16, 80, 10000, 11004 + i * 16, 84, 10004), world.dimension(),
                    new Vec3(10000 + i * 16, 80, 10000), 2, true, UUID.randomUUID()));
            }
            MiniatureBaseManager.reconcile(server);
            var expectedIds = new java.util.HashSet<UUID>();
            var revisions = registry.entries().stream().filter(b -> b.owner().equals(owner)).map(b -> b.revision().toString()).toList();
            var expected = GlobalPortalStorage.get(world).data.stream().filter(p -> p.portalTag != null
                && revisions.stream().anyMatch(r -> p.portalTag.startsWith("imm_ptl:miniature/" + r + "/"))).toList();
            expected.forEach(p -> expectedIds.add(p.getUUID()));
            helper.assertTrue(expected.size() == 384, "32 views must create 384 faces");
            var nearby = qouteall.imm_ptl.core.chunk_loading.ChunkVisibility.getNearbyPortals(world, player.position(), p -> true, 1, 256);
            helper.assertTrue(nearby.stream().filter(p -> expectedIds.contains(p.getUUID())).count() == 384,
                "Global faces must not collapse to one after the ordinary-portal limit");
            var loaders = new java.util.HashSet<qouteall.imm_ptl.core.chunk_loading.ChunkLoader>();
            qouteall.imm_ptl.core.chunk_loading.ChunkVisibility.foreachBaseChunkLoaders(player, loaders::add);
            for (var portal : expected) {
                BlockPos target = BlockPos.containing(portal.transformPoint(player.position()));
                helper.assertTrue(loaders.stream().anyMatch(l -> l.dimension().equals(portal.getDestDim())
                    && l.x() == (target.getX() >> 4) && l.z() == (target.getZ() >> 4)),
                    "Every face must retain its destination loader: " + portal.portalTag);
            }
        } finally {
            for (String name : names) if (registry.entries().stream().anyMatch(b -> b.name().equals(name))) registry.remove(name, owner);
            MiniatureBaseManager.reconcile(server);
            qouteall.imm_ptl.core.chunk_loading.ImmPtlChunkTracking.forceRemovePlayer(player);
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "physicstest.gravity", timeoutTicks = 40)
    public static void nativeSavedDataReloadPreservesUnknownRegistry(GameTestHelper helper) throws java.io.IOException {
        java.nio.file.Path directory = java.nio.file.Files.createTempDirectory("ip-miniature-registry-test-");
        java.nio.file.Path file = directory.resolve(MiniatureBaseStorage.DATA_NAME + ".dat");
        try {
            var original = new CompoundTag(); original.putInt("version", 77); original.putString("futurePayload", "must survive");
            var root = new CompoundTag(); root.put("data", original);
            net.minecraft.nbt.NbtUtils.addCurrentDataVersion(root);
            net.minecraft.nbt.NbtIo.writeCompressed(root, file);
            byte[] before = java.nio.file.Files.readAllBytes(file);
            var disk = new net.minecraft.world.level.storage.DimensionDataStorage(directory.toFile(),
                net.minecraft.util.datafix.DataFixers.getDataFixer(), helper.getLevel().registryAccess());
            var loaded = MiniatureBaseStorage.loadFrom(disk, file);
            helper.assertTrue(!loaded.isAvailable(), "Native computeIfAbsent must quarantine unsupported data, not construct an empty registry");
            helper.assertTrue(!loaded.isDirty(), "Quarantined data must not be scheduled for overwrite");
            disk.save();
            helper.assertTrue(java.util.Arrays.equals(before, java.nio.file.Files.readAllBytes(file)), "Native save must preserve the original file byte for byte");
            // Also exercise a supported registry through the native disk path.
            var valid = new MiniatureBaseStorage(); UUID owner = UUID.randomUUID();
            valid.create(new MiniatureBase("native_reload", owner, helper.getLevel().dimension(), new AABB(0, 64, 0, 8, 72, 8),
                helper.getLevel().dimension(), new Vec3(32, 64, 32), 2.5, true, UUID.randomUUID()));
            root.put("data", valid.save(new CompoundTag(), helper.getLevel().registryAccess()));
            net.minecraft.nbt.NbtIo.writeCompressed(root, file);
            disk = new net.minecraft.world.level.storage.DimensionDataStorage(directory.toFile(),
                net.minecraft.util.datafix.DataFixers.getDataFixer(), helper.getLevel().registryAccess());
            loaded = MiniatureBaseStorage.loadFrom(disk, file);
            helper.assertTrue(loaded.isAvailable() && loaded.entries().equals(valid.entries()), "Native disk round-trip must retain supported backing references");
        } finally {
            java.nio.file.Files.deleteIfExists(file); java.nio.file.Files.deleteIfExists(directory);
        }
        helper.succeed();
    }

}
