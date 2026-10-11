package qouteall.imm_ptl.peripheral.miniature;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** The overworld registry is authoritative; portal entities are a rebuildable view. */
public final class MiniatureBaseStorage extends SavedData {
    public static final String DATA_NAME = "imm_ptl_miniature_bases";
    private String loadFailure;
    private CompoundTag preservedData;
    private final Map<String, MiniatureBase> bases = new LinkedHashMap<>();
    public static MiniatureBaseStorage get(MinecraftServer server) {
        return loadFrom(server.overworld().getDataStorage(), server.getWorldPath(
            net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data").resolve(DATA_NAME + ".dat"));
    }
    /** Also used by native disk round-trip tests; the path must match the storage's data directory. */
    public static MiniatureBaseStorage loadFrom(net.minecraft.world.level.storage.DimensionDataStorage storage, java.nio.file.Path dataFile) {
        return storage.computeIfAbsent(new Factory<>(() -> {
            // Vanilla catches deserialization/IO errors and calls the constructor.
            // An existing or unreadable file must never become a new empty registry.
            if (!java.nio.file.Files.notExists(dataFile)) return quarantine(null, "Cannot read the existing miniature registry.");
            return new MiniatureBaseStorage();
        }, (tag, lookup) -> load(tag), null), DATA_NAME);
    }
    private static MiniatureBaseStorage quarantine(CompoundTag original, String reason) {
        MiniatureBaseStorage result = new MiniatureBaseStorage();
        result.loadFailure = reason; result.preservedData = original == null ? null : original.copy();
        com.mojang.logging.LogUtils.getLogger().warn("{} Miniature commands and reconciliation are disabled; preserve the world backup before repair.", reason);
        return result;
    }
    public boolean isAvailable() { return loadFailure == null; }
    private void requireAvailable() {
        if (!isAvailable()) throw new IllegalArgumentException(loadFailure + " Miniature data is preserved; an administrator must repair it before changes.");
    }
    public Collection<MiniatureBase> entries() { requireAvailable(); return java.util.List.copyOf(bases.values()); }
    public MiniatureBase owned(String name, UUID owner) {
        requireAvailable();
        MiniatureBase base = bases.get(name);
        if (base == null) throw new IllegalArgumentException("No miniature named " + name + ".");
        if (!base.owner().equals(owner)) throw new IllegalArgumentException("Only the owner can change this miniature.");
        return base;
    }
    public void create(MiniatureBase base) {
        requireAvailable();
        if (bases.containsKey(base.name())) throw new IllegalArgumentException("That miniature name is already in use.");
        if (bases.size() >= 32) throw new IllegalArgumentException("This server already has 32 registered miniature views.");
        for (MiniatureBase other : bases.values()) {
            if (other.backingDimension().equals(base.backingDimension()) && other.backing().inflate(1).intersects(base.backing()))
                throw new IllegalArgumentException("Backing regions must not overlap or touch another miniature's backing region.");
        }
        bases.put(base.name(), base); setDirty();
    }
    public void replace(MiniatureBase base, UUID owner) { owned(base.name(), owner); bases.put(base.name(), base); setDirty(); }
    public void remove(String name, UUID owner) { owned(name, owner); bases.remove(name); setDirty(); }
    public static MiniatureBaseStorage load(CompoundTag tag) {
        try {
            if (!tag.contains("version", Tag.TAG_INT) || tag.getInt("version") != 1)
                return quarantine(tag, "Unsupported miniature registry version.");
            if (!tag.contains("bases", Tag.TAG_LIST)) return quarantine(tag, "Missing miniature registry entries.");
            MiniatureBaseStorage result = new MiniatureBaseStorage();
            ListTag list = (ListTag) tag.get("bases");
            if (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND)
                return quarantine(tag, "Invalid miniature registry entry type.");
            for (int i = 0; i < list.size(); i++) result.create(MiniatureBase.load(list.getCompound(i)));
            result.setDirty(false); return result;
        } catch (RuntimeException invalid) {
            return quarantine(tag, "Invalid miniature registry: " + invalid.getMessage());
        }
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
        if (!isAvailable()) {
            if (preservedData != null) return preservedData.copy();
            throw new IllegalStateException("Refusing to overwrite unreadable miniature registry.");
        }
        tag.putInt("version", 1); ListTag list = new ListTag();
        bases.values().forEach(base -> list.add(base.save())); tag.put("bases", list); return tag;
    }
}
