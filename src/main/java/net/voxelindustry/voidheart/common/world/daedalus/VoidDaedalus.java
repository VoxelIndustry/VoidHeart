package net.voxelindustry.voidheart.common.world.daedalus;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.util.TeleportUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * What the rest of the mod uses to reach the Daedalus, and the announcing of districts as players walk into them.
 */
public final class VoidDaedalus
{
    public static final ResourceKey<Level> WORLD_KEY = ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath(MODID, "daedalus"));

    private static final int ENTRY_RANGE_DISTRICTS = 512;
    private static final int THEME_SEARCH_RADIUS = 32;
    private static final int MIDDLE_OF_SPAN = DistrictPos.CHASM / 2;
    private static final int LANE_CENTRE = 3;
    private static final int ANNOUNCE_INTERVAL_TICKS = 10;
    private static final int TITLE_FADE_IN_TICKS = 20;
    private static final int TITLE_STAY_TICKS = 50;
    private static final int TITLE_FADE_OUT_TICKS = 30;
    private static final float FACING_EAST = -90;
    private static final float FACING_SOUTH = 0;

    private static final Map<UUID, DistrictPos> lastAnnounced = new HashMap<>();

    private VoidDaedalus()
    {
    }

    public static ServerLevel level(MinecraftServer server)
    {
        return server.getLevel(WORLD_KEY);
    }

    /**
     * Sends the player somewhere new each time, onto a bridge of a random district far out.
     */
    public static void enter(ServerPlayer player)
    {
        var server = player.level().getServer();
        var random = player.getRandom();
        var pos = new DistrictPos(random.nextInt(ENTRY_RANGE_DISTRICTS * 2) - ENTRY_RANGE_DISTRICTS, random.nextInt(ENTRY_RANGE_DISTRICTS * 2) - ENTRY_RANGE_DISTRICTS);
        var bridges = DaedalusBridges.between(level(server).getSeed(), pos, random.nextBoolean() ? Compass.EAST : Compass.SOUTH);
        arriveOn(player, bridges.get(random.nextInt(bridges.size())));
    }

    /**
     * @return the district chosen, the closest one of that theme to the middle of the world
     */
    public static DistrictPos visit(ServerPlayer player, DaedalusTheme theme)
    {
        var server = player.level().getServer();
        for (int radius = 0; radius <= THEME_SEARCH_RADIUS; radius++)
        {
            for (int z = -radius; z <= radius; z++)
            {
                for (int x = -radius; x <= radius; x++)
                {
                    if (Math.max(Math.abs(x), Math.abs(z)) != radius)
                        continue;
                    var pos = new DistrictPos(x, z);
                    if (DaedalusTheme.at(pos) != theme)
                        continue;
                    arriveOn(player, DaedalusBridges.between(level(server).getSeed(), pos, Compass.EAST).getFirst());
                    return pos;
                }
            }
        }
        throw new IllegalStateException("No " + theme + " district within " + THEME_SEARCH_RADIUS + " districts of the middle");
    }

    public static void arriveOn(ServerPlayer player, DaedalusBridge bridge)
    {
        var level = level(player.level().getServer());
        double x = bridge.worldX(MIDDLE_OF_SPAN, LANE_CENTRE) + 0.5;
        double z = bridge.worldZ(MIDDLE_OF_SPAN, LANE_CENTRE) + 0.5;
        float facing = bridge.isAlongX() ? FACING_EAST : FACING_SOUTH;
        TeleportUtil.teleport(player, level, new Vec3(x, bridge.walkY(MIDDLE_OF_SPAN), z), facing, 0);
    }

    public static void leave(ServerPlayer player)
    {
        var overworld = player.level().getServer().overworld();
        var spawn = overworld.getRespawnData().pos();
        TeleportUtil.teleport(player, overworld, Vec3.atBottomCenterOf(spawn), player.getYHeadRot(), player.getViewXRot(0));
    }

    public static Component nameOf(long worldSeed, DistrictPos pos)
    {
        var name = DistrictName.of(worldSeed, pos);
        var words = name.words().stream().map(word -> (Object) Component.translatable(word)).toArray();
        return Component.translatable(name.pattern(), words).withColor(DaedalusTheme.at(pos).color());
    }

    public static void tickServer(MinecraftServer server)
    {
        if (server.getTickCount() % ANNOUNCE_INTERVAL_TICKS != 0)
            return;

        var level = level(server);
        if (level == null)
            return;

        for (var player : level.players())
            announceDistrict(player, level.getSeed());
    }

    /**
     * Shown as a subtitle under an empty title, since the title's size runs a long name off the screen.
     */
    private static void announceDistrict(ServerPlayer player, long worldSeed)
    {
        if (!DistrictPos.isInsideDistrict(player.getBlockX(), player.getBlockZ()))
            return;

        var pos = DistrictPos.at(player.getBlockX(), player.getBlockZ());
        if (pos.equals(lastAnnounced.put(player.getUUID(), pos)))
            return;

        player.connection.send(new ClientboundSetTitlesAnimationPacket(TITLE_FADE_IN_TICKS, TITLE_STAY_TICKS, TITLE_FADE_OUT_TICKS));
        player.connection.send(new ClientboundSetSubtitleTextPacket(nameOf(worldSeed, pos)));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.empty()));
    }

    public static void clear()
    {
        lastAnnounced.clear();
    }
}
