package qouteall.imm_ptl.peripheral.miniature;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;

/** A view of existing blocks, never a second copy of the building. */
public record MiniatureBase(String name, UUID owner, ResourceKey<Level> backingDimension,
    AABB backing, ResourceKey<Level> exteriorDimension, Vec3 bottomCenter, double divisor,
    boolean placed, UUID revision) {
    public MiniatureBase {
        if (!name.matches("[a-zA-Z0-9_-]{1,32}")) throw new IllegalArgumentException("Use a name of 1–32 letters, numbers, underscores or hyphens.");
        if (!Double.isFinite(divisor) || divisor < 2 || divisor > 32) throw new IllegalArgumentException("Scale divisor must be between 2 and 32.");
        double[] values = {backing.minX, backing.minY, backing.minZ, backing.maxX, backing.maxY, backing.maxZ,
            bottomCenter.x, bottomCenter.y, bottomCenter.z};
        for (double value : values) if (!Double.isFinite(value) || Math.abs(value) > 30_000_000) throw new IllegalArgumentException("Coordinates must be finite and inside world limits.");
        for (double size : new double[]{backing.getXsize(), backing.getYsize(), backing.getZsize()}) {
            if (size < 1 || size > 64 || size / divisor < 0.25) throw new IllegalArgumentException("Select 1–64 blocks per axis; each miniature axis must be at least a quarter block.");
        }
        if (backingDimension.equals(exteriorDimension) && backing.inflate(1).intersects(exteriorBox(backing, bottomCenter, divisor)))
            throw new IllegalArgumentException("The miniature must be separated from its backing region by at least one block.");
    }

    public AABB exterior() { return exteriorBox(backing, bottomCenter, divisor); }
    private static AABB exteriorBox(AABB backing, Vec3 bottom, double divisor) {
        double x = backing.getXsize() / divisor / 2, y = backing.getYsize() / divisor, z = backing.getZsize() / divisor / 2;
        return new AABB(bottom.x - x, bottom.y, bottom.z - z, bottom.x + x, bottom.y + y, bottom.z + z);
    }
    public MiniatureBase place(ResourceKey<Level> dimension, Vec3 pos) {
        return new MiniatureBase(name, owner, backingDimension, backing, dimension, pos, divisor, true, UUID.randomUUID());
    }
    public MiniatureBase pickup() {
        return new MiniatureBase(name, owner, backingDimension, backing, exteriorDimension, bottomCenter, divisor, false, UUID.randomUUID());
    }
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("name", name); tag.putUUID("owner", owner); tag.putUUID("revision", revision);
        tag.putString("backingDimension", backingDimension.location().toString());
        tag.putString("exteriorDimension", exteriorDimension.location().toString());
        tag.putDouble("minX", backing.minX); tag.putDouble("minY", backing.minY); tag.putDouble("minZ", backing.minZ);
        tag.putDouble("maxX", backing.maxX); tag.putDouble("maxY", backing.maxY); tag.putDouble("maxZ", backing.maxZ);
        tag.putDouble("x", bottomCenter.x); tag.putDouble("y", bottomCenter.y); tag.putDouble("z", bottomCenter.z);
        tag.putDouble("divisor", divisor); tag.putBoolean("placed", placed);
        return tag;
    }
    public static MiniatureBase load(CompoundTag tag) {
        return new MiniatureBase(tag.getString("name"), tag.getUUID("owner"), dimension(tag.getString("backingDimension")),
            new AABB(tag.getDouble("minX"), tag.getDouble("minY"), tag.getDouble("minZ"), tag.getDouble("maxX"), tag.getDouble("maxY"), tag.getDouble("maxZ")),
            dimension(tag.getString("exteriorDimension")), new Vec3(tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z")),
            tag.getDouble("divisor"), tag.getBoolean("placed"), tag.getUUID("revision"));
    }
    private static ResourceKey<Level> dimension(String id) { return ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(id)); }
}
