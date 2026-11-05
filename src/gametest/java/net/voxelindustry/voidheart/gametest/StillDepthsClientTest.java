package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.voxelindustry.voidheart.client.particle.DepthsShoals;
import net.voxelindustry.voidheart.common.content.depths.SmokerCycle;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.world.dungeon.DungeonAddress;
import net.voxelindustry.voidheart.common.world.dungeon.DungeonLayout;
import net.voxelindustry.voidheart.common.world.dungeon.DungeonBlueprint;
import net.voxelindustry.voidheart.common.world.dungeon.DungeonGrid;
import net.voxelindustry.voidheart.common.world.dungeon.DungeonMaterial;
import net.voxelindustry.voidheart.common.world.dungeon.DungeonPlan;
import net.voxelindustry.voidheart.common.world.dungeon.DungeonSize;
import net.voxelindustry.voidheart.common.world.dungeon.DungeonTheme;
import net.voxelindustry.voidheart.common.world.dungeon.VoidDungeons;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A smoker burns only while erupting and only inside its jet, its plants live without water, and no block of the theme is water.
 */
public class StillDepthsClientTest implements FabricClientGameTest
{
    private static final int MAX_SEARCH = 64;
    private static final int CHUNK_ARRIVAL_TICKS = 30;
    private static final int CHUNK_TIMEOUT_TICKS = 600;
    private static final int TEST_PAD_HEIGHT = 40;
    private static final float EXPECTED_DAMAGE = 4;
    private static final int MOMENT_TICKS = 12;
    private static final int ARENA_RADIUS = 10;
    private static final int ARENA_HEIGHT = 15;
    private static final int SWEEP_FRAMES = 5;
    private static final int SHOAL_ABSENT_TICKS = 300;
    private static final int SHOAL_WAIT_TICKS = 1200;
    private static final int SHOAL_FRAMES = 3;
    private static final int SHOAL_FRAME_TICKS = 40;
    private static final int SHOT_WIDTH = 1280;
    private static final int SHOT_HEIGHT = 720;
    private static final int SPOT_STEP = 4;
    private static final int SPOT_REACH = 24;

    @Override
    public void runTest(ClientGameTestContext context)
    {
        try (var world = context.worldBuilder().create())
        {
            var server = world.getServer();
            Runnable waitForChunks = () ->
            {
                context.waitTicks(CHUNK_ARRIVAL_TICKS);
                world.getConnection().waitForChunksRender(false, CHUNK_TIMEOUT_TICKS);
            };
            world.getConnection().waitForChunksRender();
            server.runCommand("gamemode spectator @a");
            context.waitTicks(10);
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
            context.getInput().pressKey(InputConstants.KEY_F1);

            long seed = server.computeOnServer(minecraft -> VoidDungeons.level(minecraft).getSeed());
            var medium = find(seed, DungeonSize.MEDIUM, DungeonLayout.BURIED);
            var floating = find(seed, DungeonSize.MEDIUM, DungeonLayout.FLOATING);

            server.runOnServer(minecraft -> checkNoWaterAndPlantsSurvive(minecraft, medium, seed));
            checkJet(context, server, medium, seed, waitForChunks);
            checkDrops(server, medium);
            shootVent(context, server, medium, seed, waitForChunks);
            shootRooms(context, server, medium, seed, waitForChunks);
            shootRooms(context, server, floating, seed, waitForChunks);
            shootWhaleFall(context, server, seed, waitForChunks);
            checkShoals(context, server, floating, seed, waitForChunks);

            context.getInput().pressKey(InputConstants.KEY_F1);
        }
    }

    private static DungeonAddress find(long seed, DungeonSize size, DungeonLayout layout)
    {
        for (int index = 0; index < MAX_SEARCH; index++)
        {
            var address = new DungeonAddress(size, DungeonTheme.DEPTHS, layout == DungeonLayout.BURIED ? 5 : 1, index);
            if (DungeonBlueprint.of(seed, address).layout() == layout)
                return address;
        }
        throw new AssertionError("No " + layout + " Still Depths dungeon found");
    }

    private static void generate(ServerLevel level, DungeonAddress address)
    {
        var origin = address.origin();
        int edge = address.size().edge();
        for (int z = 0; z < edge; z += 16)
        {
            for (int x = 0; x < edge; x += 16)
                level.getChunk((origin.getX() + x) >> 4, (origin.getZ() + z) >> 4);
        }
    }

    /**
     * Every cell the plan calls for is there after generation, and a neighbour update doesn't kill what grows on it, as it would vanilla coral.
     */
    private static void checkNoWaterAndPlantsSurvive(MinecraftServer minecraft, DungeonAddress address, long seed)
    {
        var level = VoidDungeons.level(minecraft);
        generate(level, address);
        var origin = address.origin();
        var grid = VoidDungeons.plan(seed, address).grid();
        int edge = address.size().edge();

        int water = 0, life = 0, dead = 0, missing = 0;
        var pos = new BlockPos.MutableBlockPos();
        for (int y = 0; y < edge; y++)
        {
            for (int z = 0; z < edge; z++)
            {
                for (int x = 0; x < edge; x++)
                {
                    pos.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                    var state = level.getBlockState(pos);
                    if (!state.getFluidState().isEmpty())
                        water++;

                    var material = grid.material(x, y, z);
                    if (!isLife(material))
                        continue;
                    life++;
                    if (state.isAir())
                        missing++;
                    else if (!state.canSurvive(level, pos))
                        dead++;
                }
            }
        }
        System.out.println("[StillDepths] " + life + " living blocks, " + missing + " missing, " + dead + " unable to survive, " + water + " water");
        if (water > 0)
            throw new AssertionError("A Still Depths dungeon holds " + water + " fluid blocks");
        if (life == 0 || missing > 0 || dead > 0)
            throw new AssertionError("Of " + life + " living blocks, " + missing + " were not placed and " + dead + " cannot survive where they are");
    }

    private static boolean isLife(DungeonMaterial material)
    {
        return switch (material)
        {
            case CORAL_GROWTH, GLOW_ALGAE, BARNACLE, ANEMONE, KELP, KELP_TIP, DRIFT_ALGAE, DRIFT_ALGAE_TIP, TALL_PLANT -> true;
            default -> false;
        };
    }

    /**
     * On a flat test pad over a vent placed by hand, so the player can stand in the jet: 4 damage per hit, and only while erupting.
     */
    private static void checkJet(ClientGameTestContext context, TestServerContext server, DungeonAddress address, long seed, Runnable waitForChunks)
    {
        var origin = address.origin();
        var vent = origin.offset(address.size().edge() / 2, TEST_PAD_HEIGHT, address.size().edge() / 2);
        server.runOnServer(minecraft ->
        {
            var level = VoidDungeons.level(minecraft);
            generate(level, address);
            for (var pos : BlockPos.betweenClosed(vent.offset(-ARENA_RADIUS, 0, -ARENA_RADIUS), vent.offset(ARENA_RADIUS, ARENA_HEIGHT, ARENA_RADIUS)))
                level.setBlockAndUpdate(pos, pos.getY() == vent.getY() ? VoidHeartBlocks.ABYSSAL_SILT.defaultBlockState() : Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(vent, VoidHeartBlocks.SMOKER_VENT.defaultBlockState());
        });
        long ventSeed = SmokerCycle.seed(vent);
        // Clearing the arena pops every plant that grew on what was removed
        server.runCommand("kill @e[type=item]");

        server.runCommand("gamemode survival @a");
        place(server, vent.getX() + 0.5, vent.getY() + 1, vent.getZ() + 0.5);
        context.waitTicks(10);
        waitForChunks.run();

        waitForPhase(context, server, ventSeed, SmokerCycle.Phase.QUIET);
        heal(server);
        place(server, vent.getX() + 0.5, vent.getY() + 1, vent.getZ() + 0.5);
        context.waitTicks(10);
        float afterQuiet = health(server);
        if (afterQuiet < 20)
            throw new AssertionError("A quiet smoker should not burn, health is " + afterQuiet);

        waitForPhase(context, server, ventSeed, SmokerCycle.Phase.ERUPTING);
        heal(server);
        place(server, vent.getX() + 0.5, vent.getY() + 1, vent.getZ() + 0.5);
        context.waitTicks(25);
        float afterJet = health(server);
        if (afterJet > 20 - EXPECTED_DAMAGE + 0.01F)
            throw new AssertionError("A player in the jet for 25 ticks should lose at least 2 hearts, health is " + afterJet);
        if (afterJet < 20 - EXPECTED_DAMAGE * 3 - 0.01F)
            throw new AssertionError("A player in the jet for 25 ticks should be hit at most 3 times, health is " + afterJet);
        String deathCheck = server.computeOnServer(minecraft ->
        {
            var player = minecraft.getPlayerList().getPlayers().getFirst();
            var source = player.getLastDamageSource();
            return source == null ? "none" : source.typeHolder().unwrapKey().map(key -> key.identifier().toString()).orElse("unknown");
        });
        if (!deathCheck.equals("voidheart:scalding_jet"))
            throw new AssertionError("Damage should come from the jet, got " + deathCheck);

        waitForPhase(context, server, ventSeed, SmokerCycle.Phase.ERUPTING);
        heal(server);
        place(server, vent.getX() + 2.5, vent.getY() + 1, vent.getZ() + 0.5);
        context.waitTicks(25);
        float beside = health(server);
        if (beside < 20)
            throw new AssertionError("Standing two blocks beside a jet should be safe, health is " + beside);

        server.runCommand("gamemode creative @a");
        waitForPhase(context, server, ventSeed, SmokerCycle.Phase.ERUPTING);
        heal(server);
        place(server, vent.getX() + 0.5, vent.getY() + 1, vent.getZ() + 0.5);
        context.waitTicks(25);
        if (health(server) < 20)
            throw new AssertionError("Creative players should not be burned");

        server.runCommand("gamemode spectator @a");
        System.out.println("[StillDepths] hurt in jet: " + (20 - afterJet) + " health in 25 ticks, beside it: " + (20 - beside));
    }

    private static void checkDrops(TestServerContext server, DungeonAddress address)
    {
        server.runOnServer(minecraft ->
        {
            var level = VoidDungeons.level(minecraft);
            var at = address.origin().offset(8, 8, 8);
            var bare = new ItemStack(Items.DIAMOND_PICKAXE);
            var silk = new ItemStack(Items.DIAMOND_PICKAXE);
            silk.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH), 1);
            var player = minecraft.getPlayerList().getPlayers().getFirst();

            expectDrop(level, player, at, VoidHeartBlocks.SMOKER_VENT.defaultBlockState(), bare, VoidHeartBlocks.SULFIDE_CRUST);
            expectDrop(level, player, at, VoidHeartBlocks.SMOKER_VENT.defaultBlockState(), silk, VoidHeartBlocks.SMOKER_VENT);

            var alive = List.of(VoidHeartBlocks.ABYSSAL_ROCK, VoidHeartBlocks.ABYSSAL_ROCK_STAIRS, VoidHeartBlocks.ABYSSAL_ROCK_SLAB, VoidHeartBlocks.ABYSSAL_ROCK_WALL, VoidHeartBlocks.ABYSSAL_SILT,
                    VoidHeartBlocks.SULFIDE_CRUST, VoidHeartBlocks.PALLID_CORAL_BLOCK, VoidHeartBlocks.VIOLET_CORAL_BLOCK, VoidHeartBlocks.LUMEN_CORAL_BLOCK,
                    VoidHeartBlocks.PALLID_CORAL, VoidHeartBlocks.VIOLET_CORAL, VoidHeartBlocks.LUMEN_CORAL, VoidHeartBlocks.STILL_KELP, VoidHeartBlocks.DRIFT_ALGAE,
                    VoidHeartBlocks.GLOW_ALGAE, VoidHeartBlocks.STILL_ANEMONE, VoidHeartBlocks.BARNACLE_CRUST, VoidHeartBlocks.GIANT_TUBE_WORMS);
            for (var block : alive)
                expectDrop(level, player, at, block.defaultBlockState(), bare, block);
        });
    }

    private static void expectDrop(ServerLevel level, ServerPlayer player, BlockPos at, BlockState state, ItemStack tool, Block expected)
    {
        var drops = new ArrayList<ItemStack>(Block.getDrops(state, level, at, null, player, tool));
        boolean found = drops.stream().anyMatch(stack -> stack.is(expected.asItem()));
        if (!found)
            throw new AssertionError(BuiltInRegistries.BLOCK.getKey(state.getBlock()) + " broken with " + tool + " should drop " + BuiltInRegistries.BLOCK.getKey(expected) + ", got " + drops);
    }

    private static void place(TestServerContext server, double x, double y, double z)
    {
        server.runCommand("execute in voidheart:dungeons run tp @a " + x + " " + y + " " + z);
    }

    private static void heal(TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            var player = minecraft.getPlayerList().getPlayers().getFirst();
            player.setHealth(player.getMaxHealth());
        });
    }

    private static float health(TestServerContext server)
    {
        return server.computeOnServer(minecraft -> minecraft.getPlayerList().getPlayers().getFirst().getHealth());
    }

    private static void waitForPhase(ClientGameTestContext context, TestServerContext server, long ventSeed, SmokerCycle.Phase phase)
    {
        for (int tick = 0; tick < 600; tick++)
        {
            long time = server.computeOnServer(minecraft -> VoidDungeons.level(minecraft).getGameTime());
            if (SmokerCycle.phase(ventSeed, time) == phase)
                return;
            context.waitTicks(1);
        }
        throw new AssertionError("A smoker never reached " + phase);
    }

    /**
     * Named moments of one cycle, from two opposite sides: the look depends on the plume reading against the fog.
     */
    private static void shootVent(ClientGameTestContext context, TestServerContext server, DungeonAddress address, long seed, Runnable waitForChunks)
    {
        var origin = address.origin();
        var vent = origin.offset(address.size().edge() / 2, TEST_PAD_HEIGHT, address.size().edge() / 2);
        long ventSeed = SmokerCycle.seed(vent);
        double x = vent.getX() + 0.5;
        double z = vent.getZ() + 0.5;
        double y = vent.getY();

        look(server, x + 7, y + 3.5, z, x, y + 3, z);
        waitForPhase(context, server, ventSeed, SmokerCycle.Phase.QUIET);
        context.waitTicks(10);
        waitForChunks.run();
        waitForPhase(context, server, ventSeed, SmokerCycle.Phase.QUIET);
        context.waitTicks(MOMENT_TICKS);
        context.takeScreenshot("depths-jet-1-quiet");

        waitForPhase(context, server, ventSeed, SmokerCycle.Phase.WARNING);
        context.waitTicks(MOMENT_TICKS);
        context.takeScreenshot("depths-jet-2-warning");

        waitForPhase(context, server, ventSeed, SmokerCycle.Phase.ERUPTING);
        context.waitTicks(MOMENT_TICKS);
        context.takeScreenshot("depths-jet-3-eruption-start");
        context.waitTicks(MOMENT_TICKS * 3);
        context.takeScreenshot("depths-jet-4-eruption-full");

        look(server, x - 7, y + 3.5, z, x, y + 3, z);
        context.waitTicks(2);
        context.takeScreenshot("depths-jet-5-eruption-other-side");

        waitForPhase(context, server, ventSeed, SmokerCycle.Phase.QUIET);
        context.waitTicks(MOMENT_TICKS * 2);
        context.takeScreenshot("depths-jet-6-dispersing");

        waitForPhase(context, server, ventSeed, SmokerCycle.Phase.ERUPTING);
        context.waitTicks(MOMENT_TICKS * 3);
        server.runCommand("tick freeze");
        context.waitTicks(2);
        for (int step = 0; step < SWEEP_FRAMES; step++)
        {
            look(server, x + 4 + step * 0.1, y + 2.5, z + 1 + step * 0.15, x, y + 3, z);
            context.waitTicks(2);
            context.takeScreenshot("depths-jet-sweep-" + step);
        }
        server.runCommand("tick unfreeze");
    }

    private static void look(TestServerContext server, double x, double y, double z, double targetX, double targetY, double targetZ)
    {
        server.runCommand("execute in voidheart:dungeons run tp @a " + x + " " + y + " " + z + " facing " + targetX + " " + targetY + " " + targetZ);
    }

    /**
     * Shoals only pass by inside this theme: one must come along in open water, and none while standing in another dungeon.
     */
    private static void checkShoals(ClientGameTestContext context, TestServerContext server, DungeonAddress address, long seed, Runnable waitForChunks)
    {
        var other = new DungeonAddress(DungeonSize.SMALL, DungeonTheme.CATHEDRAL, 1, 0);
        var otherOrigin = other.origin();
        look(server, otherOrigin.getX() + 16, otherOrigin.getY() + 16, otherOrigin.getZ() + 16, otherOrigin.getX() + 20, otherOrigin.getY() + 16, otherOrigin.getZ() + 16);
        context.waitTicks(40);
        var before = context.computeOnClient(minecraft -> DepthsShoals.lastSent());
        context.waitTicks(SHOAL_ABSENT_TICKS);
        if (!Objects.equals(before, context.computeOnClient(minecraft -> DepthsShoals.lastSent())))
            throw new AssertionError("A shoal passed by in a cathedral");

        var plan = VoidDungeons.plan(seed, address);
        var origin = address.origin();
        int[] open = mostOpenSpot(plan.grid());
        double x = origin.getX() + open[0] + 0.5;
        double y = origin.getY() + open[1] + 0.5;
        double z = origin.getZ() + open[2] + 0.5;
        look(server, x, y, z, x + 1, y, z);
        context.waitTicks(10);
        waitForChunks.run();

        for (int tick = 0; tick < SHOAL_WAIT_TICKS; tick += 5)
        {
            var sent = context.computeOnClient(minecraft -> DepthsShoals.lastSent());
            if (!Objects.equals(before, sent) && sent != null)
            {
                look(server, x, y, z, sent.x, sent.y, sent.z);
                for (int frame = 0; frame < SHOAL_FRAMES; frame++)
                {
                    context.waitTicks(SHOAL_FRAME_TICKS);
                    context.takeScreenshot(TestScreenshotOptions.of("depths-shoal-" + frame).withSize(SHOT_WIDTH, SHOT_HEIGHT));
                }
                return;
            }
            context.waitTicks(5);
        }
        throw new AssertionError("No shoal passed by in " + SHOAL_WAIT_TICKS + " ticks in the open");
    }

    /**
     * The lattice point with the longest free views around it, so that a shoal has a clear path to take.
     */
    private static int[] mostOpenSpot(DungeonGrid grid)
    {
        int[] best = { grid.width / 2, grid.height / 2, grid.depth / 2 };
        int bestScore = -1;
        int edge = DungeonPlan.SHELL + 2;
        for (int y = edge; y < grid.height - edge; y += SPOT_STEP)
        {
            for (int z = edge; z < grid.depth - edge; z += SPOT_STEP)
            {
                for (int x = edge; x < grid.width - edge; x += SPOT_STEP)
                {
                    if (!grid.material(x, y, z).isOpen())
                        continue;
                    int score = 0;
                    for (int dz = -1; dz <= 1; dz++)
                    {
                        for (int dx = -1; dx <= 1; dx++)
                        {
                            for (int reach = 1; reach <= SPOT_REACH && grid.material(x + dx * reach, y, z + dz * reach).isOpen() && grid.isInside(x + dx * reach, y, z + dz * reach); reach++)
                                score++;
                        }
                    }
                    if (score > bestScore)
                    {
                        bestScore = score;
                        best = new int[] { x, y, z };
                    }
                }
            }
        }
        return best;
    }

    private static void shootWhaleFall(ClientGameTestContext context, TestServerContext server, long seed, Runnable waitForChunks)
    {
        for (int index = 0; index < MAX_SEARCH; index++)
        {
            var address = new DungeonAddress(DungeonSize.MEDIUM, DungeonTheme.DEPTHS, 3, index);
            var plan = VoidDungeons.plan(seed, address);
            for (var room : plan.rooms())
            {
                if (!room.kind.name().equals("whale_fall"))
                    continue;
                var origin = address.origin();
                boolean alongX = room.maxX() - room.minX() >= room.maxZ() - room.minZ();
                double cx = origin.getX() + (room.minX() + room.maxX()) / 2.0;
                double cz = origin.getZ() + (room.minZ() + room.maxZ()) / 2.0;
                double y = origin.getY() + room.minY();
                double span = (alongX ? room.maxZ() - room.minZ() : room.maxX() - room.minX()) * 0.4;
                look(server, alongX ? cx : cx + span, y + 7, alongX ? cz + span : cz, cx, y + 2, cz);
                context.waitTicks(10);
                waitForChunks.run();
                context.waitTicks(30);
                context.takeScreenshot("depths-whale-fall");

                double endX = alongX ? origin.getX() + room.minX() + 3 : cx;
                double endZ = alongX ? cz : origin.getZ() + room.minZ() + 3;
                look(server, endX, y + 4, endZ, alongX ? origin.getX() + room.maxX() : cx, y + 3, alongX ? cz : origin.getZ() + room.maxZ());
                context.waitTicks(10);
                waitForChunks.run();
                context.waitTicks(30);
                context.takeScreenshot("depths-whale-fall-along-spine");
                return;
            }
        }
        throw new AssertionError("No whale fall among the first " + MAX_SEARCH + " dungeons");
    }

    /**
     * Inside each kind of room the dungeon holds, from its middle toward the far end, plus the entry.
     */
    private static void shootRooms(ClientGameTestContext context, TestServerContext server, DungeonAddress address, long seed, Runnable waitForChunks)
    {
        DungeonPlan plan = VoidDungeons.plan(seed, address);
        var origin = address.origin();
        var name = "depths-" + plan.blueprint().layout().name().toLowerCase();
        int[] entry = plan.entry();
        double centre = address.size().edge() / 2.0;
        look(server, origin.getX() + entry[0] + 0.5, origin.getY() + entry[1] + 1.6, origin.getZ() + entry[2] + 0.5, origin.getX() + centre, origin.getY() + entry[1] + 3, origin.getZ() + centre);
        context.waitTicks(10);
        waitForChunks.run();
        context.waitTicks(30);
        context.takeScreenshot(name + "-entry");

        var seen = new ArrayList<String>();
        for (var room : plan.rooms())
        {
            if (seen.contains(room.kind.name()))
                continue;
            seen.add(room.kind.name());
            boolean alongX = room.maxX() - room.minX() >= room.maxZ() - room.minZ();
            double cx = origin.getX() + (room.minX() + room.maxX()) / 2.0 + 0.5;
            double cz = origin.getZ() + (room.minZ() + room.maxZ()) / 2.0 + 0.5;
            double eye = origin.getY() + room.minY() + 2.6;
            look(server, cx, eye, cz, alongX ? origin.getX() + room.maxX() : cx, eye + 1.4, alongX ? cz : origin.getZ() + room.maxZ());
            context.waitTicks(10);
            waitForChunks.run();
            context.waitTicks(30);
            context.takeScreenshot(name + "-" + room.kind.name());
        }

        var overlook = origin.offset(address.size().edge() * 3 / 4, address.size().edge() * 2 / 3, address.size().edge() * 3 / 4);
        look(server, overlook.getX(), overlook.getY(), overlook.getZ(), origin.getX() + centre / 2, origin.getY() + centre / 2, origin.getZ() + centre / 2);
        context.waitTicks(10);
        waitForChunks.run();
        context.waitTicks(30);
        context.takeScreenshot(name + "-overview");
    }
}
