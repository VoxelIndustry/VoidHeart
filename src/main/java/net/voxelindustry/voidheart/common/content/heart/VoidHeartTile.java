package net.voxelindustry.voidheart.common.content.heart;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.voxelindustry.steamlayer.tile.TileBase;
import net.voxelindustry.voidheart.common.content.altar.AltarVoidParticleEffect;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class VoidHeartTile extends TileBase
{
    @Getter
    @Setter
    private UUID playerID;

    private final Map<UUID, Long> lastPlayerHitCache = new HashMap<>();

    public VoidHeartTile(BlockPos pos, BlockState state)
    {
        super(VoidHeartTiles.VOID_HEART, pos, state);
    }

    @Override
    public void readNbt(NbtCompound tag)
    {
        super.readNbt(tag);

        if (tag.containsUuid("playerID"))
            playerID = tag.getUuid("playerID");
    }

    @Override
    public void writeNbt(NbtCompound tag)
    {
        if (playerID != null)
            tag.putUuid("playerID", playerID);

        super.writeNbt(tag);
    }

    public static void tick(World world, BlockPos pos, BlockState state, VoidHeartTile heart)
    {
        if (heart.isClient())
        {
            if (world.getTime() % 2 == 0)
                world.addParticle(new AltarVoidParticleEffect(0.05),
                        pos.getX() + 0.5 + world.random.nextGaussian(),
                        pos.getY() + 0.5 + world.random.nextGaussian(),
                        pos.getZ() + 0.5 + world.random.nextGaussian(),
                        pos.getX() + 0.5,
                        pos.getY() + 0.5,
                        pos.getZ() + 0.5);
            return;
        }

        if (heart.lastPlayerHitCache.isEmpty())
            return;

        heart.lastPlayerHitCache.values().removeIf(timestamp -> System.currentTimeMillis() - timestamp > 5_000);
    }

    public void playerHit(PlayerEntity player)
    {
        UUID hitPlayerID = player.getUuid();
        if (lastPlayerHitCache.containsKey(hitPlayerID))
        {
            var serverPlayer = (ServerPlayerEntity) player;

            ServerWorld destination = world.getServer().getWorld(serverPlayer.getSpawnPointDimension());

            BlockPos spawnPointPosition = serverPlayer.getSpawnPointPosition();
            Optional<Vec3d> respawnPosition = spawnPointPosition == null ? Optional.empty() : PlayerEntity.findRespawnPosition(destination,
                    spawnPointPosition,
                    serverPlayer.getSpawnAngle(),
                    serverPlayer.isSpawnForced(),
                    true);

            if (!respawnPosition.isPresent())
                destination = world.getServer().getOverworld();

            ServerWorld finalDestination = destination;

            Vec3d destinationPos = respawnPosition.orElseGet(() -> Vec3d.ofCenter(finalDestination.getSpawnPos()));
            serverPlayer.teleport(destination,
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
