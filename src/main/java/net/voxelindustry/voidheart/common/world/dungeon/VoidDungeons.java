package net.voxelindustry.voidheart.common.world.dungeon;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.util.TeleportUtil;

import java.util.concurrent.ExecutionException;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * What the rest of the mod uses to reach dungeons. They live in their own dimension so that nothing happening there can touch a player's pocket.
 */
public final class VoidDungeons
{
    public static final ResourceKey<Level> WORLD_KEY = ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath(MODID, "dungeons"));

    private static final int PLAN_CACHE_SIZE = 6;
    private static final int HAZARD_INTERVAL_TICKS = 20;
    private static final int TOXIC_WATER_POISON_TICKS = 60;

    private record PlanKey(long worldSeed, DungeonAddress address)
    {
    }

    private static final Cache<PlanKey, DungeonPlan> PLANS = CacheBuilder.newBuilder().maximumSize(PLAN_CACHE_SIZE).build();

    private VoidDungeons()
    {
    }

    public static ServerLevel level(MinecraftServer server)
    {
        return server.getLevel(WORLD_KEY);
    }

    public static VoidDungeonState state(MinecraftServer server)
    {
        return level(server).getDataStorage().computeIfAbsent(VoidDungeonState.TYPE);
    }

    /**
     * Thread safe: chunks of the same dungeon generating at once wait for a single plan.
     */
    public static DungeonPlan plan(long worldSeed, DungeonAddress address)
    {
        try
        {
            return PLANS.get(new PlanKey(worldSeed, address), () -> DungeonPlan.generate(DungeonBlueprint.of(worldSeed, address)));
        } catch (ExecutionException e)
        {
            throw new IllegalStateException("Could not plan dungeon " + address, e);
        }
    }

    /**
     * Reserves a dungeon nobody has been given yet.
     */
    public static DungeonAddress open(MinecraftServer server, DungeonSize size, DungeonTheme theme, int difficulty)
    {
        return state(server).allocate(size, theme, difficulty);
    }

    public static BlockPos entry(ServerLevel level, DungeonAddress address)
    {
        int[] entry = plan(level.getSeed(), address).entry();
        return address.origin().offset(entry[0], entry[1], entry[2]);
    }

    public static void enter(ServerPlayer player, DungeonAddress address)
    {
        var level = level(player.level().getServer());
        var entry = entry(level, address);
        TeleportUtil.teleport(player, level, Vec3.atBottomCenterOf(entry), player.getYHeadRot(), player.getViewXRot(0));
    }

    public static void tickServer(MinecraftServer server)
    {
        if (server.getTickCount() % HAZARD_INTERVAL_TICKS != 0)
            return;

        var level = level(server);
        if (level == null)
            return;

        for (var player : level.players())
            poisonInToxicWater(player);
    }

    private static void poisonInToxicWater(ServerPlayer player)
    {
        if (!player.isInWater() || player.isSpectator())
            return;

        var address = DungeonAddress.at(player.getBlockX(), player.getBlockZ());
        if (address != null && address.theme() == DungeonTheme.FOREST)
            player.addEffect(new MobEffectInstance((Holder<net.minecraft.world.effect.MobEffect>) MobEffects.POISON, TOXIC_WATER_POISON_TICKS));
    }
}
