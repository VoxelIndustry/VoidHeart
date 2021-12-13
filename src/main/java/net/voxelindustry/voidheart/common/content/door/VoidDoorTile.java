package net.voxelindustry.voidheart.common.content.door;

import lombok.Setter;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity.RemovalReason;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

import java.util.UUID;

public class VoidDoorTile extends BlockEntity
{
    private UUID id;

    @Setter
    private UUID portalEntityID;
    private UUID portalDestinationEntityID;

    public VoidDoorTile(BlockPos pos, BlockState state)
    {
        super(VoidHeartTiles.VOID_DOOR, pos, state);
    }

    @Override
    public void readNbt(NbtCompound tag)
    {
        if (tag.contains("doorID"))
            id = tag.getUuid("doorID");
        else
            id = UUID.randomUUID();

        if (tag.containsUuid("portalEntityID"))
            portalEntityID = tag.getUuid("portalEntityID");
        if (tag.containsUuid("portalDestinationEntityID"))
            portalDestinationEntityID = tag.getUuid("portalDestinationEntityID");

        super.readNbt(tag);
    }

    @Override
    public void writeNbt(NbtCompound tag)
    {
        if (id == null)
            id = UUID.randomUUID();

        tag.putUuid("doorID", id);

        if (portalEntityID != null)
            tag.putUuid("portalEntityID", portalEntityID);
        if (portalDestinationEntityID != null)
            tag.putUuid("portalDestinationEntityID", portalDestinationEntityID);

        super.writeNbt(tag);
    }

    public UUID getId()
    {
        if (id == null)
            return id = UUID.randomUUID();
        return id;
    }

    public void setPortal(UUID portalID)
    {
        if (getWorld().isClient())
            return;

        if (portalDestinationEntityID != null)
        {
            var portalEntity = ((ServerWorld) getWorld()).getEntity(portalDestinationEntityID);

            if (portalEntity != null)
                portalEntity.remove(RemovalReason.DISCARDED);
        }

        portalDestinationEntityID = portalID;
    }
}
