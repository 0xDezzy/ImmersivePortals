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
    private final Map<String, MiniatureBase> bases = new LinkedHashMap<>();
    public static MiniatureBaseStorage get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(MiniatureBaseStorage::new,
            (tag, lookup) -> load(tag), null), "imm_ptl_miniature_bases");
    }
    public Collection<MiniatureBase> entries() { return java.util.List.copyOf(bases.values()); }
    public MiniatureBase owned(String name, UUID owner) {
        MiniatureBase base = bases.get(name);
        if (base == null) throw new IllegalArgumentException("No miniature named " + name + ".");
        if (!base.owner().equals(owner)) throw new IllegalArgumentException("Only the owner can change this miniature.");
        return base;
    }
    public void create(MiniatureBase base) {
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
        if (tag.getInt("version") != 1) throw new IllegalArgumentException("Unsupported miniature registry version; refusing to discard backing references.");
        MiniatureBaseStorage result = new MiniatureBaseStorage();
        ListTag list = tag.getList("bases", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) result.create(MiniatureBase.load(list.getCompound(i)));
        result.setDirty(false); return result;
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
        tag.putInt("version", 1); ListTag list = new ListTag();
        bases.values().forEach(base -> list.add(base.save())); tag.put("bases", list); return tag;
    }
}
