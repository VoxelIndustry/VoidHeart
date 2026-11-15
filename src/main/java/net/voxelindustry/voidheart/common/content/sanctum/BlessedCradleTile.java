package net.voxelindustry.voidheart.common.content.sanctum;

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
 * The top of the pillar that rises where a sanctum's guardian died, holding what was kept there until someone takes it.
 */
public class BlessedCradleTile extends TileBase
{
    @Getter
    private ItemStack contents = ItemStack.EMPTY;

    public BlessedCradleTile(BlockPos pos, BlockState state)
    {
        super(VoidHeartTiles.BLESSED_CRADLE, pos, state);
    }

    /** Lays one of the blessed contraptions in it, if any are known. */
    public void bless(RandomSource random)
    {
        hold(BuiltInRegistries.ITEM.getRandomElementOf(VoidHeartTags.BLESSED_CONTRAPTIONS, random).map(ItemStack::new).orElse(ItemStack.EMPTY));
    }

    public void hold(ItemStack stack)
    {
        contents = stack;
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
        contents = input.read("contents", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
    }

    @Override
    protected void saveAdditional(ValueOutput output)
    {
        output.store("contents", ItemStack.OPTIONAL_CODEC, contents);
        super.saveAdditional(output);
    }
}
