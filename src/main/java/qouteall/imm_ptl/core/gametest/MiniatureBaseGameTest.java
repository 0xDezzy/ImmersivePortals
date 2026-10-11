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
        MiniatureBase base = new MiniatureBase(name, owner, world.dimension(), new AABB(origin, origin.offset(4, 4, 4)),
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
}
