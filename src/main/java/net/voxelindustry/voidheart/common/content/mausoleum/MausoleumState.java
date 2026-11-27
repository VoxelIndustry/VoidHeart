package net.voxelindustry.voidheart.common.content.mausoleum;

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
 * Which shroud gate each mausoleum belongs to, where its gate leads back to, and how far the end of its rider has gone. Kept with the dungeons'
 * dimension. A mausoleum's index is its place in this list, and is never given twice.
 */
public final class MausoleumState extends SavedData
{
    /**
     * @param gate   the packed place of the shroud gate it was opened from, or 0 for one opened by a command
     * @param home   where its gate leads back to
     * @param slain  whether its rider is dead
     * @param opened whether the sarcophagus has cracked open
     */
    public record Mausoleum(long gate, Optional<GlobalPos> home, float homeYaw, boolean slain, boolean opened)
    {
        static final Codec<Mausoleum> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.fieldOf("gate").forGetter(Mausoleum::gate),
                GlobalPos.CODEC.optionalFieldOf("home").forGetter(Mausoleum::home),
                Codec.FLOAT.optionalFieldOf("home_yaw", 0F).forGetter(Mausoleum::homeYaw),
                Codec.BOOL.optionalFieldOf("slain", false).forGetter(Mausoleum::slain),
                Codec.BOOL.optionalFieldOf("opened", false).forGetter(Mausoleum::opened)
        ).apply(instance, Mausoleum::new));
    }

    public static final Codec<MausoleumState> CODEC = Mausoleum.CODEC.listOf().fieldOf("mausoleums").codec().xmap(MausoleumState::new, state -> state.mausoleums);

    public static final SavedDataType<MausoleumState> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(MODID, "mausoleums"),
            MausoleumState::new,
            CODEC,
            null);

    private final List<Mausoleum> mausoleums;

    public MausoleumState()
    {
        mausoleums = new ArrayList<>();
    }

    private MausoleumState(List<Mausoleum> mausoleums)
    {
        this.mausoleums = new ArrayList<>(mausoleums);
    }

    /** @return the index of a gate's mausoleum, giving it one the first time someone passes through */
    public int indexFor(long gate, GlobalPos home, float homeYaw)
    {
        for (int i = 0; i < mausoleums.size(); i++)
        {
            if (mausoleums.get(i).gate() == gate && gate != 0)
                return i;
        }
        mausoleums.add(new Mausoleum(gate, Optional.of(home), homeYaw, false, false));
        setDirty();
        return mausoleums.size() - 1;
    }

    /** A mausoleum of no gate, for review: its gate leads back to wherever it was opened from. */
    public int open(GlobalPos home, float homeYaw)
    {
        mausoleums.add(new Mausoleum(0, Optional.of(home), homeYaw, false, false));
        setDirty();
        return mausoleums.size() - 1;
    }

    public @Nullable Mausoleum get(int index)
    {
        return index >= 0 && index < mausoleums.size() ? mausoleums.get(index) : null;
    }

    public int size()
    {
        return mausoleums.size();
    }

    public void markSlain(int index)
    {
        var mausoleum = get(index);
        if (mausoleum != null && !mausoleum.slain())
            set(index, new Mausoleum(mausoleum.gate(), mausoleum.home(), mausoleum.homeYaw(), true, mausoleum.opened()));
    }

    public void markOpened(int index)
    {
        var mausoleum = get(index);
        if (mausoleum != null && !mausoleum.opened())
            set(index, new Mausoleum(mausoleum.gate(), mausoleum.home(), mausoleum.homeYaw(), true, true));
    }

    /** Lets a review run the fight again. Does nothing to the world, which a command has to put back. */
    public void reset(int index)
    {
        var mausoleum = get(index);
        if (mausoleum != null)
            set(index, new Mausoleum(mausoleum.gate(), mausoleum.home(), mausoleum.homeYaw(), false, false));
    }

    private void set(int index, Mausoleum mausoleum)
    {
        mausoleums.set(index, mausoleum);
        setDirty();
    }
}
