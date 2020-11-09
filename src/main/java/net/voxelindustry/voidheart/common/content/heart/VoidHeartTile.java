package net.voxelindustry.voidheart.common.content.heart;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Tickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.voxelindustry.steamlayer.tile.TileBase;
import net.voxelindustry.voidheart.common.content.altar.AltarVoidParticleEffect;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class VoidHeartTile extends TileBase implements Tickable
{
    @Getter
    @Setter
    private UUID playerID;

    private final Map<UUID, Long> lastPlayerHitCache = new HashMap<>();

    public VoidHeartTile()
    {
        super(VoidHeartTiles.VOID_HEART);
    }

    @Override
    public void fromTag(BlockState state, CompoundTag tag)
    {
        super.fromTag(state, tag);

        if (tag.containsUuid("playerID"))
            playerID = tag.getUuid("playerID");
    }

    @Override
    public CompoundTag toTag(CompoundTag tag)
    {
        if (playerID != null)
            tag.putUuid("playerID", playerID);

        return super.toTag(tag);
    }

    @Override
    public void tick()
    {
        if (isClient())
        {
            if (getWorld().getTime() % 2 == 0)
                getWorld().addParticle(new AltarVoidParticleEffect(0.05),
                        getPos().getX() + 0.5 + getWorld().random.nextGaussian(),
                        getPos().getY() + 0.5 + getWorld().random.nextGaussian(),
                        getPos().getZ() + 0.5 + getWorld().random.nextGaussian(),
                        getPos().getX() + 0.5,
                        getPos().getY() + 0.5,
                        getPos().getZ() + 0.5);
            return;
        }

        if (lastPlayerHitCache.isEmpty())
            return;

        lastPlayerHitCache.values().removeIf(timestamp -> System.currentTimeMillis() - timestamp > 5_000);
    }

    public void playerHit(PlayerEntity player)
    {
        UUID hitPlayerID = player.getUuid();
        if (lastPlayerHitCache.containsKey(hitPlayerID))
        {
            ServerPlayerEntity serverPlayer = (ServerPlayerEntity) player;

            ServerWorld destination = world.getServer().getWorld(serverPlayer.getSpawnPointDimension());

            BlockPos spawnPointPosition = serverPlayer.getSpawnPointPosition();
            Optional<Vec3d> respawnPosition = spawnPointPosition == null ? Optional.empty() : PlayerEntity.findRespawnPosition(destination, spawnPointPosition, ((ServerPlayerEntity) player).getSpawnAngle(), serverPlayer.isSpawnPointSet(), true);

            if (!respawnPosition.isPresent())
                destination = world.getServer().getOverworld();

            ServerWorld finalDestination = destination;

            Vec3d destinationPos = respawnPosition.orElseGet(() -> Vec3d.ofCenter(finalDestination.getSpawnPos()));
            ((ServerPlayerEntity) player).teleport(destination,
                    destinationPos.getX(),
                    destinationPos.getY(),
                    destinationPos.getZ(),
                    player.getHeadYaw(),
                    player.getPitch(0));
        }
        else
            lastPlayerHitCache.put(hitPlayerID, System.currentTimeMillis());
    }
}
