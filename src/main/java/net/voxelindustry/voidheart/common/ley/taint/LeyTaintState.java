package net.voxelindustry.voidheart.common.ley.taint;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.voxelindustry.voidheart.common.energy.FoundationBalance;

import java.util.ArrayList;
import java.util.List;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Each ley node's taint, keyed by node id and kept apart from the frozen lattice regions. Clean nodes aren't stored.
 */
public class LeyTaintState extends SavedData
{
    private record Entry(long node, float taint)
    {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.fieldOf("node").forGetter(Entry::node),
                Codec.FLOAT.fieldOf("taint").forGetter(Entry::taint)
        ).apply(instance, Entry::new));
    }

    public static final Codec<LeyTaintState> CODEC = Entry.CODEC.listOf()
            .fieldOf("nodes")
            .codec()
            .xmap(LeyTaintState::new, LeyTaintState::entries);

    public static final SavedDataType<LeyTaintState> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(MODID, "ley_taint"),
            LeyTaintState::new,
            CODEC,
            null);

    private final Long2FloatOpenHashMap taintByNode = new Long2FloatOpenHashMap();

    public LeyTaintState()
    {
    }

    private LeyTaintState(List<Entry> entries)
    {
        // Saved values may be stale or hand-edited, so they go through the same clamp as live changes
        entries.forEach(entry -> store(entry.node(), entry.taint()));
    }

    /**
     * Node ids belong to the overworld lattice, so taint is always kept there, whichever level asks.
     */
    public static LeyTaintState get(ServerLevel level)
    {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public float taint(long nodeId)
    {
        return taintByNode.getOrDefault(nodeId, 0F);
    }

    /**
     * Amount may be negative, as a node heals. Taint stays between 0 and {@link FoundationBalance#MAX_TAINT}.
     */
    public void add(long nodeId, float amount)
    {
        if (!Float.isNaN(amount))
            set(nodeId, taint(nodeId) + amount);
    }

    public void set(long nodeId, float taint)
    {
        if (Float.isNaN(taint) || taint(nodeId) == clamp(taint))
            return;
        store(nodeId, taint);
        setDirty();
    }

    /**
     * Every node holding some taint.
     */
    public LongSet tainted()
    {
        return LongSets.unmodifiable(taintByNode.keySet());
    }

    private void store(long nodeId, float taint)
    {
        float clamped = clamp(taint);
        if (clamped <= 0)
            taintByNode.remove(nodeId);
        else
            taintByNode.put(nodeId, clamped);
    }

    private static float clamp(float taint)
    {
        return Float.isNaN(taint) ? 0 : Mth.clamp(taint, 0, FoundationBalance.MAX_TAINT);
    }

    private List<Entry> entries()
    {
        var entries = new ArrayList<Entry>(taintByNode.size());
        taintByNode.long2FloatEntrySet().forEach(entry -> entries.add(new Entry(entry.getLongKey(), entry.getFloatValue())));
        return entries;
    }
}
