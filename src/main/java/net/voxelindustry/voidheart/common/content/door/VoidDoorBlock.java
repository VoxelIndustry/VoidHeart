package net.voxelindustry.voidheart.common.content.door;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import org.jetbrains.annotations.Nullable;

public class VoidDoorBlock extends DoorBlock implements EntityBlock
{
    public VoidDoorBlock(Properties properties)
    {
        super(BlockSetType.STONE, properties
                .mapColor(MapColor.STONE)
                .instrument(NoteBlockInstrument.BASEDRUM)
                .strength(3F)
                .sound(SoundType.STONE));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new VoidDoorTile(pos, state);
    }
}
