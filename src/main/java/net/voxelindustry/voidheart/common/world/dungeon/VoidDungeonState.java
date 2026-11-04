package net.voxelindustry.voidheart.common.world.dungeon;

import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.Map;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * How many dungeons of each kind have been handed out. World generation never reads this: what a dungeon holds comes from where it lies.
 */
public final class VoidDungeonState extends SavedData
{
    public static final Codec<VoidDungeonState> CODEC = Codec.unboundedMap(Codec.STRING, Codec.INT)
            .fieldOf("next_index")
            .codec()
            .xmap(VoidDungeonState::new, state -> state.nextIndexByRow);

    public static final SavedDataType<VoidDungeonState> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(MODID, "dungeon_storage"),
            VoidDungeonState::new,
            CODEC,
            null);

    private final Map<String, Integer> nextIndexByRow;

    public VoidDungeonState()
    {
        nextIndexByRow = new HashMap<>();
    }

    private VoidDungeonState(Map<String, Integer> nextIndexByRow)
    {
        this.nextIndexByRow = new HashMap<>(nextIndexByRow);
    }

    public DungeonAddress allocate(DungeonSize size, DungeonTheme theme, int difficulty)
    {
        var first = new DungeonAddress(size, theme, difficulty, 0);
        var row = String.valueOf(first.row());
        int index = nextIndexByRow.getOrDefault(row, 0);
        nextIndexByRow.put(row, index + 1);
        setDirty();
        return new DungeonAddress(size, theme, difficulty, index);
    }
}
