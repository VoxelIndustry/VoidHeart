package net.voxelindustry.voidheart.common.content.door;

import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.enums.Instrument;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

public class VoidDoorBlock extends DoorBlock implements BlockEntityProvider
{
    public VoidDoorBlock()
    {
        super(Settings.create()
                .mapColor(MapColor.STONE_GRAY)
                .instrument(Instrument.BASEDRUM)
                .strength(3F)
                .sounds(BlockSoundGroup.STONE),
                BlockSetType.STONE);
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state)
    {
        return new VoidDoorTile(pos, state);
    }
}
