package net.voxelindustry.voidheart.common.content.heart;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.Codec;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.voxelindustry.steamlayer.network.tilesync.PartialSyncedTile;
import net.voxelindustry.steamlayer.network.tilesync.PartialTileSync;
import net.voxelindustry.steamlayer.network.tilesync.TileSyncElement;
import net.voxelindustry.steamlayer.tile.TileBase;
import net.voxelindustry.voidheart.common.content.altar.AltarVoidParticleEffect;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;
import net.voxelindustry.voidheart.common.world.VoidPocketState;
import net.voxelindustry.voidheart.common.world.pocket.VoidHeartData;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static java.util.Collections.singletonList;

public class VoidHeartTile extends TileBase implements PartialSyncedTile
{
    private final Collection<Identifier> syncElements = singletonList(VoidHeartDataSyncElement.IDENTIFIER);

    @Getter
    @Setter
    private UUID playerID;

    @Getter
    private GameProfile playerProfile;

    private final Map<UUID, Long> lastPlayerHitCache = new HashMap<>();

    private VoidHeartData heartData;

    public VoidHeartTile(BlockPos pos, BlockState state)
    {
        super(VoidHeartTiles.VOID_HEART, pos, state);
    }

    private void updateHeartData(VoidHeartData data)
    {
        this.heartData = data;
    }

    public VoidHeartData heartData()
    {
        return heartData;
    }

    @Override
    public void setWorld(World world)
    {
        super.setWorld(world);

        if (isServer() && playerID != null)
        {
            var gameProfileOpt = world.getServer().getUserCache().getByUuid(playerID);

            if (gameProfileOpt.isEmpty())
                return;

            playerProfile = gameProfileOpt.get();
            sync();
        }
    }

    @Override
    public void readNbt(NbtCompound tag)
    {
        super.readNbt(tag);

        if (tag.containsUuid("playerID"))
            playerID = tag.getUuid("playerID");

        if (isClient() && tag.contains("ownerProfile"))
        {
            playerProfile = NbtHelper.toGameProfile(tag.getCompound("ownerProfile"));

            if (playerProfile != null && playerProfile.isComplete())
                MinecraftClient.getInstance().getSessionService().fillProfileProperties(playerProfile, true);
        }
    }

    @Override
    public void writeNbt(NbtCompound tag)
    {
        if (playerID != null)
        {
            tag.putUuid("playerID", playerID);

            if (isServer() && playerProfile != null)
            {
                var profileTag = new NbtCompound();
                NbtHelper.writeGameProfile(profileTag, playerProfile);
                tag.put("ownerProfile", profileTag);
            }
        }

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

        if (world.getTime() % 20 == 0)
        {
            var heartData = VoidPocketState.getVoidPocketState(world).getHeartData(heart.playerID);
            heart.updateHeartData(heartData);

            if (heartData != null)
                PartialTileSync.syncPart(heart, VoidHeartDataSyncElement.IDENTIFIER);
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

    @Override
    public Optional<TileSyncElement<?>> getSyncElement(Identifier identifier)
    {
        if (identifier.equals(VoidHeartDataSyncElement.IDENTIFIER) && heartData != null)
        {
            return Optional.of(new VoidHeartDataSyncElement(heartData));
        }
        return Optional.empty();
    }

    @Override
    public void receiveSyncElement(TileSyncElement<?> element)
    {
        if (element.getIdentifier().equals(VoidHeartDataSyncElement.IDENTIFIER))
        {
            var heartDataSyncElement = (VoidHeartDataSyncElement) element;

            this.heartData = heartDataSyncElement.heartData();
        }
    }

    @Override
    public Codec<?> getSyncElementCodec(Identifier identifier)
    {
        if (identifier.equals(VoidHeartDataSyncElement.IDENTIFIER))
            return VoidHeartDataSyncElement.CODEC;
        return null;
    }

    @Override
    public Collection<Identifier> getAllSyncElements()
    {
        return syncElements;
    }
}
