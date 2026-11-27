package net.voxelindustry.voidheart.common.content.mausoleum;

import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.voxelindustry.steamlayer.tile.TileBase;
import net.voxelindustry.voidheart.common.setup.VoidHeartTags;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

/**
 * The middle of a sarcophagus: whether its lid has cracked open, when, and what lies inside until someone takes it. A client times the cracking from
 * when it sees it, since its clock can trail the server's by a few ticks.
 */
public class SarcophagusTile extends TileBase
{
    /** Ticks from the first crack until the lid's halves lie still on the dais. */
    public static final int CRACK_TICKS = 24;
    public static final int SPLIT_TICKS = 22;

    @Getter
    private ItemStack contents = ItemStack.EMPTY;
    @Getter
    private boolean open;
    private long openedAt;
    private boolean loaded;

    public SarcophagusTile(BlockPos pos, BlockState state)
    {
        super(VoidHeartTiles.SARCOPHAGUS, pos, state);
    }

    /** @return ticks since the lid began to crack, or a negative number while it is whole */
    public double sinceOpened(double gameTime)
    {
        return open ? gameTime - openedAt : -1;
    }

    /** Cracks the lid open on one of the blessed contraptions, if any are known. Does nothing to one already open. */
    public void open(RandomSource random)
    {
        if (open || level == null)
            return;
        open = true;
        openedAt = level.getGameTime();
        contents = BuiltInRegistries.ITEM.getRandomElementOf(VoidHeartTags.BLESSED_CONTRAPTIONS, random).map(ItemStack::new).orElse(ItemStack.EMPTY);
        setChanged();
        sync();
    }

    /** Lays the lid back whole over an empty chest, for a review to run the fight again. */
    public void close()
    {
        open = false;
        contents = ItemStack.EMPTY;
        setChanged();
        sync();
    }

    public ItemStack take()
    {
        var taken = contents;
        contents = ItemStack.EMPTY;
        setChanged();
        sync();
        return taken;
    }

    @Override
    public void setLevel(Level level)
    {
        super.setLevel(level);
        sync();
    }

    @Override
    protected void loadAdditional(ValueInput input)
    {
        super.loadAdditional(input);
        boolean wasOpen = open;
        contents = input.read("contents", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        open = input.getBooleanOr("open", false);
        openedAt = input.getLongOr("openedAt", 0);
        if (loaded && level != null && level.isClientSide() && open && !wasOpen)
            openedAt = level.getGameTime();
        loaded = true;
    }

    @Override
    protected void saveAdditional(ValueOutput output)
    {
        output.store("contents", ItemStack.OPTIONAL_CODEC, contents);
        output.putBoolean("open", open);
        output.putLong("openedAt", openedAt);
        super.saveAdditional(output);
    }
}
