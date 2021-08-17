package net.voxelindustry.voidheart.common.content.door;

import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.Material;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

public class VoidDoorBlock extends DoorBlock implements BlockEntityProvider
{
    public VoidDoorBlock()
    {
        super(Settings.of(Material.STONE)
                .strength(3F)
                .sounds(BlockSoundGroup.STONE));
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state)
    {
        return new VoidDoorTile(pos, state);
    }
}
