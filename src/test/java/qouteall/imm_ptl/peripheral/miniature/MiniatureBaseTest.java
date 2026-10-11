package qouteall.imm_ptl.peripheral.miniature;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class MiniatureBaseTest {
    private static final UUID OWNER = UUID.randomUUID();
    private static MiniatureBase base(double divisor) {
        return new MiniatureBase("home", OWNER, Level.OVERWORLD, new AABB(0, 64, 0, 8, 72, 8),
            Level.OVERWORLD, new Vec3(32, 64, 32), divisor, true, UUID.randomUUID());
    }
    @Test void fractionalRatiosAndMinimumExteriorSize() {
        assertEquals(3.2, base(2.5).exterior().getXsize(), 0.000001);
        assertEquals(0.25, base(32).exterior().getXsize(), 0.000001);
        assertEquals(64, base(2.5).exterior().minY);
    }
    @Test void invalidScaleAndCoordinatesCannotEnterRegistry() {
        for (double scale : new double[]{0, 1.99, 32.01, Double.NaN, Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class, () -> base(scale));
        assertThrows(IllegalArgumentException.class, () -> base(2).place(Level.OVERWORLD, new Vec3(Double.NaN, 64, 0)));
        assertThrows(IllegalArgumentException.class, () -> base(2).place(Level.OVERWORLD, new Vec3(4, 64, 4)));
    }
    @Test void placementPickupAndSerializationNeverChangeBackingOrOwner() {
        MiniatureBase original = base(2.5), picked = original.pickup();
        assertFalse(picked.placed()); assertNotEquals(original.revision(), picked.revision());
        MiniatureBase placed = picked.place(Level.NETHER, new Vec3(12, 70, 12));
        assertEquals(original.backing(), placed.backing()); assertEquals(OWNER, placed.owner());
        assertEquals(original.backingDimension(), placed.backingDimension());
        assertEquals(placed, MiniatureBase.load(placed.save()));
    }
    @Test void registryPersistsOwnershipAndRejectsOverlapAndDuplicateNames() {
        MiniatureBaseStorage storage = new MiniatureBaseStorage(); storage.create(base(2));
        assertThrows(IllegalArgumentException.class, () -> storage.create(base(2)));
        assertThrows(IllegalArgumentException.class, () -> storage.owned("home", UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class, () -> storage.remove("home", UUID.randomUUID()));
        MiniatureBaseStorage restored = MiniatureBaseStorage.load(storage.save(new CompoundTag(), null));
        assertEquals(storage.entries(), restored.entries());
        restored.replace(restored.owned("home", OWNER).pickup(), OWNER);
        assertFalse(restored.owned("home", OWNER).placed());
        restored.remove("home", OWNER); assertTrue(restored.entries().isEmpty());
    }
    @Test void futureRegistryVersionFailsInsteadOfSilentlyLosingReferences() {
        CompoundTag tag = new CompoundTag(); tag.putInt("version", 2);
        MiniatureBaseStorage quarantined = MiniatureBaseStorage.load(tag);
        assertFalse(quarantined.isAvailable());
        assertThrows(IllegalArgumentException.class, quarantined::entries);
        assertThrows(IllegalArgumentException.class, () -> quarantined.create(base(2)));
        assertEquals(tag, quarantined.save(new CompoundTag(), null));
        assertFalse(quarantined.isDirty());
    }
}
