package net.voxelindustry.voidheart.common.content.heart;

import com.mojang.serialization.Codec;
import net.minecraft.world.level.portal.TeleportTransition;
import net.voxelindustry.voidheart.common.util.ProfileUtil;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
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
    private final Collection<Identifier> syncElements = singletonList(VoidHeartDataSyncElement.IDENTIFIER);

    @Getter
    @Setter
    private UUID playerID;

    @Getter
    private ResolvableProfile playerProfile;

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
            ProfileUtil.resolve(world.getServer(), playerID, profile ->
            {
                playerProfile = profile;
                sync();
            });
        }
    }

    @Override
    protected void loadAdditional(ValueInput tag)
    {
        super.loadAdditional(tag);

        if (tag.read("playerID", UUIDUtil.CODEC).isPresent())
            playerID = tag.read("playerID", UUIDUtil.CODEC).orElseThrow();

        if (isClient())
            playerProfile = tag.read("ownerProfile", ResolvableProfile.CODEC).orElse(null);
    }

    @Override
    protected void saveAdditional(ValueOutput tag)
    {
        if (playerID != null)
        {
            tag.store("playerID", UUIDUtil.CODEC, playerID);

            if (isServer() && playerProfile != null)
                tag.store("ownerProfile", ResolvableProfile.CODEC, playerProfile);
        }

        super.saveAdditional(tag);
    }

    public static void tick(Level world, BlockPos pos, BlockState state, VoidHeartTile heart)
    {
        if (heart.isClient())
        {
            if (world.getGameTime() % 2 == 0)
                world.addParticle(new AltarVoidParticleEffect(0.05),
                        pos.getX() + 0.5 + world.getRandom().nextGaussian(),
                        pos.getY() + 0.5 + world.getRandom().nextGaussian(),
                        pos.getZ() + 0.5 + world.getRandom().nextGaussian(),
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
            serverPlayer.teleport(serverPlayer.findRespawnPositionAndUseSpawnBlock(true, TeleportTransition.DO_NOTHING));
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
