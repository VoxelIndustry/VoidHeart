package net.voxelindustry.voidheart.common.content.door;

import lombok.Setter;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

import java.util.UUID;

public class VoidDoorTile extends BlockEntity
{
    private UUID id;

    @Setter
    private UUID portalEntityID;
    @Setter
    private UUID portalDestinationEntityID;

    public VoidDoorTile()
    {
        super(VoidHeartTiles.VOID_DOOR);
    }

    @Override
    public void fromTag(BlockState state, CompoundTag tag)
    {
        if (tag.contains("doorID"))
            id = tag.getUuid("doorID");
        else
            id = UUID.randomUUID();

        if (tag.containsUuid("portalEntityID"))
            portalEntityID = tag.getUuid("portalEntityID");
        if (tag.containsUuid("portalDestinationEntityID"))
            portalDestinationEntityID = tag.getUuid("portalDestinationEntityID");

        super.fromTag(state, tag);
    }

    @Override
    public CompoundTag toTag(CompoundTag tag)
    {
        if (id == null)
            id = UUID.randomUUID();

        tag.putUuid("doorID", id);

        if (portalEntityID != null)
            tag.putUuid("portalEntityID", portalEntityID);
        if (portalDestinationEntityID != null)
            tag.putUuid("portalDestinationEntityID", portalDestinationEntityID);

        return super.toTag(tag);
    }

    public UUID getId()
    {
        if (id == null)
            return id = UUID.randomUUID();
        return id;
    }
}
