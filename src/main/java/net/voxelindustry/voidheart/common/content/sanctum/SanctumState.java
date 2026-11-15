package net.voxelindustry.voidheart.common.content.sanctum;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Which temple each sanctum belongs to, where its gate leads back to, and how far its guardian's end has gone. Kept with the dungeons'
 * dimension. A sanctum's index is its place in this list, and is never given twice.
 */
public final class SanctumState extends SavedData
{
    /**
     * @param door   the key of the temple door it was opened from, or 0 for one opened by a command
     * @param home   where its gate leads back to
     * @param slain  whether its guardian is dead
     * @param raised whether the pillar has risen where the guardian sat
     */
    public record Sanctum(long door, Optional<GlobalPos> home, float homeYaw, boolean slain, boolean raised)
    {
        static final Codec<Sanctum> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.fieldOf("door").forGetter(Sanctum::door),
                GlobalPos.CODEC.optionalFieldOf("home").forGetter(Sanctum::home),
                Codec.FLOAT.optionalFieldOf("home_yaw", 0F).forGetter(Sanctum::homeYaw),
                Codec.BOOL.optionalFieldOf("slain", false).forGetter(Sanctum::slain),
                Codec.BOOL.optionalFieldOf("raised", false).forGetter(Sanctum::raised)
        ).apply(instance, Sanctum::new));
    }

    public static final Codec<SanctumState> CODEC = Sanctum.CODEC.listOf().fieldOf("sanctums").codec().xmap(SanctumState::new, state -> state.sanctums);

    public static final SavedDataType<SanctumState> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(MODID, "sanctums"),
            SanctumState::new,
            CODEC,
            null);

    private final List<Sanctum> sanctums;

    public SanctumState()
    {
        sanctums = new ArrayList<>();
    }

    private SanctumState(List<Sanctum> sanctums)
    {
        this.sanctums = new ArrayList<>(sanctums);
    }

    /** @return the index of a temple's sanctum, giving it one the first time its door opens */
    public int indexFor(long door, GlobalPos home, float homeYaw)
    {
        for (int i = 0; i < sanctums.size(); i++)
        {
            if (sanctums.get(i).door() == door && door != 0)
                return i;
        }
        sanctums.add(new Sanctum(door, Optional.of(home), homeYaw, false, false));
        setDirty();
        return sanctums.size() - 1;
    }

    /** A sanctum of no temple, for review: its gate leads back to wherever it was opened from. */
    public int open(GlobalPos home, float homeYaw)
    {
        sanctums.add(new Sanctum(0, Optional.of(home), homeYaw, false, false));
        setDirty();
        return sanctums.size() - 1;
    }

    public @Nullable Sanctum get(int index)
    {
        return index >= 0 && index < sanctums.size() ? sanctums.get(index) : null;
    }

    public int size()
    {
        return sanctums.size();
    }

    public void markSlain(int index)
    {
        var sanctum = get(index);
        if (sanctum != null && !sanctum.slain())
            set(index, new Sanctum(sanctum.door(), sanctum.home(), sanctum.homeYaw(), true, sanctum.raised()));
    }

    public void markRaised(int index)
    {
        var sanctum = get(index);
        if (sanctum != null && !sanctum.raised())
            set(index, new Sanctum(sanctum.door(), sanctum.home(), sanctum.homeYaw(), true, true));
    }

    /** Lets a review run the fight again. Does nothing to the world, which a command has to put back. */
    public void reset(int index)
    {
        var sanctum = get(index);
        if (sanctum != null)
            set(index, new Sanctum(sanctum.door(), sanctum.home(), sanctum.homeYaw(), false, false));
    }

    public void moveHome(int index, GlobalPos home, float homeYaw)
    {
        var sanctum = get(index);
        if (sanctum != null)
            set(index, new Sanctum(sanctum.door(), Optional.of(home), homeYaw, sanctum.slain(), sanctum.raised()));
    }

    private void set(int index, Sanctum sanctum)
    {
        sanctums.set(index, sanctum);
        setDirty();
    }
}
