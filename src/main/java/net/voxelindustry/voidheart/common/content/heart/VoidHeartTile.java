package net.voxelindustry.voidheart.common.content.heart;

import com.mojang.serialization.Codec;
import net.minecraft.world.level.portal.TeleportTransition;
import net.voxelindustry.voidheart.common.util.ProfileUtil;
import net.voxelindustry.voidheart.common.world.dungeon.DungeonTeleportLock;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.steamlayer.network.tilesync.PartialSyncedTile;
import net.voxelindustry.steamlayer.network.tilesync.PartialTileSync;
import net.voxelindustry.steamlayer.network.tilesync.TileSyncElement;
import net.voxelindustry.steamlayer.tile.TileBase;
import net.voxelindustry.voidheart.common.content.altar.AltarVoidParticleEffect;
import net.voxelindustry.voidheart.common.content.research.VoidGrafts;
import net.voxelindustry.voidheart.common.content.secondheart.SecondHeart;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;
import net.voxelindustry.voidheart.common.world.VoidPocketState;
import net.voxelindustry.voidheart.common.world.pocket.VoidHeartData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
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

    /** The owner's attunement, read on the server and synced, since the heart's form follows it. */
    @Getter
    private int ownerAttunement;
    /** Whether the owner's second life is ready. Kept from when they were last here, so an away owner's twin stays as it was. */
    @Getter
    private boolean twinBeats = true;
    /** On the client, the game time it saw the twin start or stop beating, so the change eases from then. Long ago until it does. */
    @Getter
    private long twinChangedAt = Long.MIN_VALUE / 2;

    /**
     * When each form past the dormant heart first showed, in game time: the veins, then the marrow, then the twin. The heart only takes a new form while its
     * owner is near, so they see it grow; a form they lose is dropped at once.
     */
    private final List<Long> grownAt = new ArrayList<>();

    /**
     * On the client, where there is floor around the heart for its veins to creep over, by block in a square this many blocks out each way. Kept as the
     * same array while it doesn't change, so what is grown on it can be kept too.
     */
    private boolean[][] floor;
    public static final int FLOOR_REACH = 4;
    private static final int FLOOR_CHECK_TICKS = 40;

    /** The last beat this client heard, so each is played once. */
    private int heardBeat = NO_BEAT;

    private static final int NO_BEAT = Integer.MIN_VALUE;
    /** The owner must be this close for the heart to take its new form. */
    private static final double GROWS_WITHIN = 16;
    private static final float  BEAT_VOLUME  = 0.3F;
    private static final float  BEAT_PITCH   = 0.75F;

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
        ownerAttunement = tag.getIntOr("ownerAttunement", 0);
        twinBeats = tag.getBooleanOr("twinBeats", true);
        grownAt.clear();
        grownAt.addAll(tag.read("grownAt", Codec.LONG.listOf()).orElse(List.of()));
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

        tag.putInt("ownerAttunement", ownerAttunement);
        tag.putBoolean("twinBeats", twinBeats);
        tag.store("grownAt", Codec.LONG.listOf(), grownAt);
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
            heart.hearBeat(world);
            if (heart.floor == null || world.getGameTime() % FLOOR_CHECK_TICKS == 0)
                heart.findFloor(world);
            return;
        }

        if (world.getGameTime() % 20 == 0)
        {
            var heartData = VoidPocketState.getVoidPocketState(world).getHeartData(heart.playerID);
            heart.updateHeartData(heartData);
            if (heart.playerID != null)
                heart.updateOwner((ServerLevel) world);

            if (heartData != null)
                PartialTileSync.syncPart(heart, VoidHeartDataSyncElement.IDENTIFIER);
        }

        if (heart.lastPlayerHitCache.isEmpty())
            return;

        heart.lastPlayerHitCache.values().removeIf(timestamp -> System.currentTimeMillis() - timestamp > 5_000);
    }

    private void updateOwner(ServerLevel world)
    {
        int attunement = VoidGrafts.attunementOf(world.getServer(), playerID);
        var owner = world.getServer().getPlayerList().getPlayer(playerID);
        boolean beats = owner == null ? twinBeats : SecondHeart.state(owner).ready();
        boolean grew = grow(world, owner, attunement);
        if (attunement == ownerAttunement && beats == twinBeats && !grew)
            return;
        ownerAttunement = attunement;
        twinBeats = beats;
        setChanged();
    }

    /** Takes the form the owner's attunement gives, at once when it shrinks and only while they're near when it grows. */
    private boolean grow(ServerLevel world, ServerPlayer owner, int attunement)
    {
        int form = Math.max(1, attunement);
        if (form < heartForm())
        {
            grownAt.subList(form - 1, grownAt.size()).clear();
            return true;
        }
        if (form == heartForm() || owner == null || owner.level() != world || owner.distanceToSqr(Vec3.atCenterOf(worldPosition)) > GROWS_WITHIN * GROWS_WITHIN)
            return false;
        while (heartForm() < form)
            grownAt.add(world.getGameTime());
        return true;
    }

    /** The form the heart shows: 1 dormant, 2 veined, 3 with marrow, 4 with its twin. */
    public int heartForm()
    {
        return 1 + grownAt.size();
    }

    /** When a form first showed in game time, 0 for the dormant heart. */
    public long grownAt(int form)
    {
        return form <= 1 || form > heartForm() ? 0 : grownAt.get(form - 2);
    }

    /** Fixed per owner, so their heart beats in its own time and grows its own veins. */
    public int beatSeed()
    {
        return playerID != null ? playerID.hashCode() : worldPosition.hashCode();
    }

    public boolean[][] floor()
    {
        return floor;
    }

    /** Floor is the sturdy top of a block level with the heart's own floor, with room above it. */
    private void findFloor(Level world)
    {
        int size = 2 * FLOOR_REACH + 1;
        var found = new boolean[size][size];
        for (int dx = -FLOOR_REACH; dx <= FLOOR_REACH; dx++)
        {
            for (int dz = -FLOOR_REACH; dz <= FLOOR_REACH; dz++)
            {
                var above = worldPosition.offset(dx, 0, dz);
                var below = above.below();
                found[dx + FLOOR_REACH][dz + FLOOR_REACH] = world.getBlockState(below).isFaceSturdy(world, below, Direction.UP)
                        && (dx == 0 && dz == 0 || !world.getBlockState(above).isCollisionShapeFullBlock(world, above));
            }
        }
        if (floor == null || !Arrays.deepEquals(floor, found))
            floor = found;
    }

    /** A rooted heart is heard on each lub. */
    private void hearBeat(Level world)
    {
        if (heartForm() < 2)
        {
            heardBeat = NO_BEAT;
            return;
        }
        int beat = HeartBeat.latest(world.getGameTime(), beatSeed(), HeartBeat.ROOTED_PERIOD);
        if (beat == heardBeat)
            return;
        if (heardBeat != NO_BEAT)
            world.playLocalSound(worldPosition, SoundEvents.WARDEN_HEARTBEAT, SoundSource.BLOCKS, BEAT_VOLUME, BEAT_PITCH, false);
        heardBeat = beat;
    }

    public void playerHit(Player player)
    {
        UUID hitPlayerID = player.getUUID();
        if (lastPlayerHitCache.containsKey(hitPlayerID))
        {
            var serverPlayer = (ServerPlayer) player;
            if (DungeonTeleportLock.refuse(serverPlayer))
                return;
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
            return Optional.of(new VoidHeartDataSyncElement(heartData, ownerAttunement, twinBeats, List.copyOf(grownAt)));
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
            this.ownerAttunement = heartDataSyncElement.attunement();
            if (heartDataSyncElement.twinBeats() != twinBeats && level != null)
                twinChangedAt = level.getGameTime();
            this.twinBeats = heartDataSyncElement.twinBeats();
            grownAt.clear();
            grownAt.addAll(heartDataSyncElement.grownAt());
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
