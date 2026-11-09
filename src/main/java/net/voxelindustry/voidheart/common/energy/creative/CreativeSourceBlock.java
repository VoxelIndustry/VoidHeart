package net.voxelindustry.voidheart.common.energy.creative;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * A creative-only test block: the void well projects a field of endless energy that takes any offer, and the ley stone supplies all the ley asked of it nearby.
 */
public class CreativeSourceBlock extends Block implements EntityBlock
{
    public enum Kind
    {
        VOID_WELL,
        LEY_STONE
    }

    private final Kind kind;

    public CreativeSourceBlock(Kind kind, Properties properties)
    {
        super(properties);
        this.kind = kind;
    }

    public Kind kind()
    {
        return kind;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new CreativeSourceTile(pos, state);
    }
}
