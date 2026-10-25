package net.voxelindustry.voidheart.common.content.heart;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.Codec;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
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
    private final Collection<ResourceLocation> syncElements = singletonList(VoidHeartDataSyncElement.IDENTIFIER);

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
    public void setLevel(Level world)
    {
        super.setLevel(world);

        if (isServer() && playerID != null)
        {
            var gameProfileOpt = world.getServer().getProfileCache().get(playerID);

            if (gameProfileOpt.isEmpty())
                return;

            playerProfile = gameProfileOpt.get();
            sync();
        }
    }

    @Override
    public void load(CompoundTag tag)
    {
        super.load(tag);

        if (tag.hasUUID("playerID"))
            playerID = tag.getUUID("playerID");

        if (isClient() && tag.contains("ownerProfile"))
        {
            playerProfile = NbtUtils.readGameProfile(tag.getCompound("ownerProfile"));

            if (playerProfile != null && playerProfile.isComplete())
                Minecraft.getInstance().getMinecraftSessionService().fillProfileProperties(playerProfile, true);
        }
    }

    @Override
    public void saveAdditional(CompoundTag tag)
    {
        if (playerID != null)
        {
            tag.putUUID("playerID", playerID);

            if (isServer() && playerProfile != null)
            {
                var profileTag = new CompoundTag();
                NbtUtils.writeGameProfile(profileTag, playerProfile);
                tag.put("ownerProfile", profileTag);
            }
        }

        super.saveAdditional(tag);
    }

    public static void tick(Level world, BlockPos pos, BlockState state, VoidHeartTile heart)
    {
        if (heart.isClient())
        {
            if (world.getGameTime() % 2 == 0)
                world.addParticle(new AltarVoidParticleEffect(0.05),
                        pos.getX() + 0.5 + world.random.nextGaussian(),
                        pos.getY() + 0.5 + world.random.nextGaussian(),
                        pos.getZ() + 0.5 + world.random.nextGaussian(),
                        pos.getX() + 0.5,
                        pos.getY() + 0.5,
                        pos.getZ() + 0.5);
            return;
        }

        if (world.getGameTime() % 20 == 0)
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

    public void playerHit(Player player)
    {
        UUID hitPlayerID = player.getUUID();
        if (lastPlayerHitCache.containsKey(hitPlayerID))
        {
            var serverPlayer = (ServerPlayer) player;

            ServerLevel destination = level.getServer().getLevel(serverPlayer.getRespawnDimension());

            BlockPos spawnPointPosition = serverPlayer.getRespawnPosition();
            Optional<Vec3> respawnPosition = spawnPointPosition == null ? Optional.empty() : Player.findRespawnPositionAndUseSpawnBlock(destination,
                    spawnPointPosition,
                    serverPlayer.getRespawnAngle(),
                    serverPlayer.isRespawnForced(),
                    true);

            if (!respawnPosition.isPresent())
                destination = level.getServer().overworld();

            ServerLevel finalDestination = destination;

            Vec3 destinationPos = respawnPosition.orElseGet(() -> Vec3.atCenterOf(finalDestination.getSharedSpawnPos()));
            serverPlayer.teleportTo(destination,
                    destinationPos.x(),
                    destinationPos.y(),
                    destinationPos.z(),
                    player.getYHeadRot(),
                    player.getViewXRot(0));
        }
        else
            lastPlayerHitCache.put(hitPlayerID, System.currentTimeMillis());
    }

    @Override
    public Optional<TileSyncElement<?>> getSyncElement(ResourceLocation identifier)
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
    public Codec<?> getSyncElementCodec(ResourceLocation identifier)
    {
        if (identifier.equals(VoidHeartDataSyncElement.IDENTIFIER))
            return VoidHeartDataSyncElement.CODEC;
        return null;
    }

    @Override
    public Collection<ResourceLocation> getAllSyncElements()
    {
        return syncElements;
    }
}
