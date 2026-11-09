package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.curse.Corruption;
import net.voxelindustry.voidheart.common.energy.ley.LeyGrowerTile;
import net.voxelindustry.voidheart.common.energy.ley.LeySpurs;
import net.voxelindustry.voidheart.common.energy.ley.LeyStoneBalance;
import net.voxelindustry.voidheart.common.energy.ley.LeyStoneBlock;
import net.voxelindustry.voidheart.common.energy.ley.LeyStoneContent;
import net.voxelindustry.voidheart.common.energy.ley.LeyStoneTile;
import net.voxelindustry.voidheart.common.energy.ley.LeyWardTile;
import net.voxelindustry.voidheart.common.energy.ley.SpurTap;
import net.voxelindustry.voidheart.common.energy.ley.WardStrength;
import net.voxelindustry.voidheart.common.ley.Ley;
import net.voxelindustry.voidheart.common.ley.LeyLattice;

import static net.voxelindustry.voidheart.gametest.GameTestKit.*;

/**
 * Standing stones on a real ley line: one taps the line, a second chains from it beyond the line's reach, and a ward beside the second holds back
 * corruption, more weakly once the line is tainted, and not at all once the chain breaks. A grower quickens a wheat plot against an unpowered one.
 * The spur survives a reload.
 */
public class LeyStoneClientTest implements FabricClientGameTest
{
    private static final int TAP_OUT     = 20;
    private static final int CHAINED_OUT = 46;
    private static final int WARD_OUT    = 5;
    private static final int GROWER_ALONG  = 7;
    private static final int CONTROL_ALONG = -14;
    private static final int RISES       = 10;
    private static final int RISE        = 2;
    private static final int GROWTH_TICKS = 300;

    /**
     * Positions along a line: {@code along} runs with the line, {@code out} away from it, toward the base.
     */
    private record Scene(BlockPos tap, BlockPos chained, BlockPos ward, BlockPos grower, BlockPos control, long upstream, Vec3 tapPoint, Vec3 along, Vec3 out,
                         Direction facing)
    {
        BlockPos at(BlockPos from, double along, double out)
        {
            return BlockPos.containing(from.getX() + 0.5 + this.along.x * along + this.out.x * out, from.getY(), from.getZ() + 0.5 + this.along.z * along + this.out.z * out);
        }
    }

    @Override
    public void runTest(ClientGameTestContext context)
    {
        EnergyTestKit.expectRegistered("ley_stone", "ley_ward", "ley_grower");

        TestWorldSave save;
        Scene scene;
        try (var world = context.worldBuilder().create())
        {
            save = world.getWorldSave();
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            freezeTime(server, "noon");
            server.runCommand("gamerule spawn_mobs false");
            server.runCommand("gamemode creative @a");
            context.waitTicks(10);

            scene = server.computeOnServer(minecraft -> findScene(minecraft.overworld()));
            var middle = scene.at(scene.tap(), 0, (CHAINED_OUT - TAP_OUT) / 2.0);
            server.runCommand("forceload add " + (middle.getX() - 48) + " " + (middle.getZ() - 48) + " " + (middle.getX() + 48) + " " + (middle.getZ() + 48));
            tp(server, middle.getX() + 0.5, FLAT_FEET, middle.getZ() + 0.5);
            context.waitTicks(40);
            world.getConnection().waitForChunksRender();

            build(context, server, scene);
            checkSpur(server, scene);
            checkWard(context, server, scene);
            screenshots(context, server, scene);
            checkGrower(context, server, scene);
            checkBreaking(context, server, scene);
        }

        try (var world = save.open())
        {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            context.waitTicks(20);
            checkSpur(server, scene);
        }
    }

    /**
     * Looks along nearby lines for a place where the first stone taps the line and the second stands beyond every line's reach, so it can only chain.
     */
    private static Scene findScene(ServerLevel level)
    {
        var lattice = LeyLattice.get(level).orElseThrow(() -> new AssertionError("Expected a ley lattice in the overworld"));
        for (int probe = 0; probe < 25; probe++)
        {
            var edge = lattice.nearestEdge((probe % 5 - 2) * 400, (probe / 5 - 2) * 400).orElse(null);
            if (edge == null || edge.length() < 48)
                continue;
            var along = new Vec3(edge.toX() - edge.fromX(), 0, edge.toZ() - edge.fromZ()).normalize();
            for (double t : new double[] { 0.5, 0.35, 0.65 })
            {
                var onLine = BlockPos.containing(edge.fromX() + (edge.toX() - edge.fromX()) * t, FLAT_FEET, edge.fromZ() + (edge.toZ() - edge.fromZ()) * t);
                for (int side : new int[] { 1, -1 })
                {
                    var out = new Vec3(-along.z * side, 0, along.x * side);
                    var facing = Direction.getApproximateNearest(-out.x, 0, -out.z);
                    var probeScene = new Scene(null, null, null, null, null, 0, Vec3.ZERO, along, out, facing);
                    var tap = probeScene.at(onLine, 0, TAP_OUT);
                    var chained = probeScene.at(onLine, 0, CHAINED_OUT);
                    var tapLine = Ley.lineNear(level, tap, LeyStoneBalance.STONE_REACH);
                    if (tapLine.isEmpty() || Ley.lineNear(level, chained, LeyStoneBalance.STONE_REACH).isPresent())
                        continue;
                    var spurTap = SpurTap.of(tapLine.get(), tap.getX() + 0.5, tap.getZ() + 0.5);
                    if (Ley.lineFlowAt(level, spurTap.claimant(tap.getY())) <= 0)
                        continue;

                    return new Scene(tap, chained, probeScene.at(chained, 0, WARD_OUT), probeScene.at(tap, GROWER_ALONG, 0), probeScene.at(tap, CONTROL_ALONG, 0),
                            spurTap.fromNode(), new Vec3(spurTap.x(), FLAT_GROUND + 1, spurTap.z()), along, out, facing);
                }
            }
        }
        throw new AssertionError("Found no line with room for a spur near the origin");
    }

    private static void build(ClientGameTestContext context, TestServerContext server, Scene scene)
    {
        var facing = scene.facing().getSerializedName();
        setblock(server, scene.tap(), "voidheart:ley_stone[half=lower,facing=" + facing + "]");
        setblock(server, scene.tap().above(), "voidheart:ley_stone[half=upper,facing=" + facing + "]");
        setblock(server, scene.ward(), "voidheart:ley_ward[facing=" + scene.facing().getClockWise().getSerializedName() + "]");
        setblock(server, scene.grower().below(), "grass_block");
        setblock(server, scene.grower(), "voidheart:ley_grower");

        // The second stone is raised the way a player does, so its upper half comes from placement
        placeStone(context, server, scene);
        context.waitTicks(5);
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            var upper = level.getBlockState(scene.chained().above());
            expect(upper.is(LeyStoneContent.LEY_STONE) && upper.getValue(LeyStoneBlock.HALF) == DoubleBlockHalf.UPPER,
                    "Placing a standing stone should raise its upper half too, found " + upper);
        });
    }

    private static void placeStone(ClientGameTestContext context, TestServerContext server, Scene scene)
    {
        var stand = scene.at(scene.chained(), 0, -3);
        aimCamera(server, stand.getX() + 0.5, FLAT_FEET, stand.getZ() + 0.5, scene.chained().getX() + 0.5, FLAT_FEET, scene.chained().getZ() + 0.5);
        context.waitTicks(2);
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(LeyStoneContent.LEY_STONE));
            useOn(player, scene.chained().below());
        });
    }

    /**
     * The first stone taps the line, the second chains from it, and the ward beside the second draws its share of what the line gives the spur.
     */
    private static void checkSpur(TestServerContext server, Scene scene)
    {
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            var plan = LeySpurs.get(level).plan();
            var tapLink = plan.link(scene.tap());
            var chainedLink = plan.link(scene.chained());
            expect(tapLink != null && tapLink.isRoot(), "The stone " + TAP_OUT + " blocks from the line should tap it, got " + tapLink);
            expect(chainedLink != null && !chainedLink.isRoot() && scene.tap().equals(chainedLink.parent()),
                    "The stone beyond the line's reach should chain to the first, got " + chainedLink);
            expect(level.getBlockEntity(scene.tap()) instanceof LeyStoneTile stone && stone.isConnected() && stone.isRoot() && stone.flow() > 0,
                    "The tapping stone should show the line's flow");
        });
    }

    private static void checkWard(ClientGameTestContext context, TestServerContext server, Scene scene)
    {
        context.waitTicks(30);
        float supply = server.computeOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            var tap = SpurTap.of(Ley.lineNear(level, scene.tap(), LeyStoneBalance.STONE_REACH).orElseThrow(), scene.tap().getX() + 0.5, scene.tap().getZ() + 0.5);
            double flow = Ley.lineFlowAt(level, tap.claimant(scene.tap().getY()));
            // The ward and the grower share the spur, so each gets the same share of its need
            float expected = (float) Math.min(1, flow / (LeyStoneBalance.WARD_NEED + LeyStoneBalance.GROWER_NEED));
            var ward = ward(minecraft, scene);
            expect(Math.abs(ward.supply() - expected) < 0.02F, "The ward should get " + expected + " of its need from a " + flow + " line, got " + ward.supply());
            return ward.supply();
        });

        var inside = scene.at(scene.ward(), 0, 3);
        var outside = scene.at(scene.ward(), 0, LeyStoneBalance.WARD_RADIUS + 6);
        int unwarded = raiseAt(server, outside);
        int clean = raiseAt(server, inside);
        float expectedClean = RISES * RISE * WardStrength.multiplier(supply);
        expect(unwarded == RISES * RISE, "Outside the ward corruption should rise in full, rose " + unwarded);
        expect(Math.abs(clean - expectedClean) <= 1, "Inside the ward corruption should rise about " + expectedClean + ", rose " + clean);
        expect(clean < unwarded, "Corruption should rise slower inside the ward (" + clean + ") than outside (" + unwarded + ")");

        server.runOnServer(minecraft -> Ley.addTaint(minecraft.overworld(), scene.upstream(), 50));
        context.waitTicks(30);
        float health = server.computeOnServer(minecraft -> ward(minecraft, scene).health());
        expect(Math.abs(health - WardStrength.health(50, 0)) < 0.02F, "A line tainted to 50 should leave the ward " + WardStrength.health(50, 0) + ", left " + health);
        int tainted = raiseAt(server, inside);
        expect(tainted > clean && tainted < unwarded, "A tainted line should weaken the ward: rose " + tainted + ", clean " + clean + ", unwarded " + unwarded);
        server.runOnServer(minecraft -> Ley.addTaint(minecraft.overworld(), scene.upstream(), -50));
        context.waitTicks(30);
    }

    private static LeyWardTile ward(MinecraftServer minecraft, Scene scene)
    {
        if (minecraft.overworld().getBlockEntity(scene.ward()) instanceof LeyWardTile ward)
            return ward;
        throw new AssertionError("Expected a ward at " + scene.ward());
    }

    private static int raiseAt(TestServerContext server, BlockPos pos)
    {
        tp(server, pos.getX() + 0.5, FLAT_FEET, pos.getZ() + 0.5);
        return server.computeOnServer(minecraft ->
        {
            var player = player(minecraft);
            Corruption.set(player, 0);
            for (int i = 0; i < RISES; i++)
                Corruption.raise(player, RISE);
            int risen = Corruption.get(player);
            Corruption.set(player, 0);
            return risen;
        });
    }

    private static void screenshots(ClientGameTestContext context, TestServerContext server, Scene scene)
    {
        context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
        context.getInput().pressKey(InputConstants.KEY_F1);

        var view = scene.at(scene.chained(), -12, 14);
        var target = scene.at(scene.tap(), 0, 12);
        aimFrom(context, server, view.getX() + 0.5, FLAT_FEET + 14, view.getZ() + 0.5, target.getX() + 0.5, FLAT_FEET, target.getZ() + 0.5, 20);
        context.takeScreenshot("ley-spur-overview-noon");

        server.runCommand("time set midnight");
        context.waitTicks(30);
        context.takeScreenshot("ley-spur-overview-night");

        var face = scene.tap().relative(scene.facing(), 3);
        server.runCommand("tick freeze");
        for (int step = 0; step < 5; step++)
        {
            aimFrom(context, server, face.getX() + 0.5 + step * 0.06, FLAT_FEET + 0.3, face.getZ() + 0.5 + step * 0.09,
                    scene.tap().getX() + 0.5, FLAT_FEET + 1, scene.tap().getZ() + 0.5, 2);
            context.takeScreenshot("ley-stone-channel-sweep-" + step);
        }
        server.runCommand("tick unfreeze");

        var swirl = scene.tapPoint().add(scene.along().scale(3)).add(scene.out().scale(2));
        aimFrom(context, server, swirl.x, FLAT_FEET + 2, swirl.z, scene.tapPoint().x, FLAT_FEET, scene.tapPoint().z, 10);
        context.takeScreenshot("ley-spur-tap-night");

        var wardSide = scene.ward().relative(scene.facing().getClockWise(), 3);
        aimFrom(context, server, wardSide.getX() + 0.5, FLAT_FEET + 0.5, wardSide.getZ() + 0.5, scene.ward().getX() + 0.5, FLAT_FEET + 0.5, scene.ward().getZ() + 0.5, 60);
        context.takeScreenshot("ley-ward-night");

        var basin = scene.at(scene.grower(), -1.5, 1.5);
        aimFrom(context, server, basin.getX() + 0.5, FLAT_FEET + 1.2, basin.getZ() + 0.5, scene.grower().getX() + 0.5, FLAT_FEET + 0.3, scene.grower().getZ() + 0.5, 20);
        context.takeScreenshot("ley-grower-night");

        server.runOnServer(minecraft -> Ley.addTaint(minecraft.overworld(), scene.upstream(), 65));
        context.waitTicks(45);
        aimFrom(context, server, view.getX() + 0.5, FLAT_FEET + 14, view.getZ() + 0.5, target.getX() + 0.5, FLAT_FEET, target.getZ() + 0.5, 10);
        context.takeScreenshot("ley-spur-tainted-night");
        server.runOnServer(minecraft -> Ley.addTaint(minecraft.overworld(), scene.upstream(), -65));

        server.runCommand("time set noon");
        context.getInput().pressKey(InputConstants.KEY_F1);
        server.runOnServer(minecraft -> player(minecraft).setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(LeyStoneContent.LEY_WARD)));
        server.runOnServer(minecraft -> player(minecraft).getInventory().setItem(1, new ItemStack(LeyStoneContent.LEY_STONE)));
        server.runOnServer(minecraft -> player(minecraft).getInventory().setItem(2, new ItemStack(LeyStoneContent.LEY_GROWER)));
        var high = scene.at(scene.ward(), -6, 10);
        aimFrom(context, server, high.getX() + 0.5, FLAT_FEET + 20, high.getZ() + 0.5, scene.ward().getX() + 0.5, FLAT_FEET, scene.ward().getZ() + 0.5, 20);
        context.takeScreenshot("ley-rings-holding-ward");

        server.runOnServer(minecraft -> player(minecraft).setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(LeyStoneContent.LEY_STONE)));
        context.waitTicks(15);
        context.takeScreenshot("ley-stone-held");
        server.runOnServer(minecraft -> player(minecraft).setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY));
        context.waitTicks(15);
    }

    /**
     * A wheat plot around the grower and the same plot out of its reach, each watered at one corner. Random ticks are sped up so growth shows quickly,
     * and the grower scales its own picks by the same rule, so it still doubles what vanilla gives.
     */
    private static void checkGrower(ClientGameTestContext context, TestServerContext server, Scene scene)
    {
        plot(server, scene.grower());
        plot(server, scene.control());
        setblock(server, scene.control(), "cobblestone");
        var between = scene.at(scene.tap(), (GROWER_ALONG + CONTROL_ALONG) / 2.0, -4);
        tp(server, between.getX() + 0.5, FLAT_FEET, between.getZ() + 0.5);
        server.runCommand("gamerule random_tick_speed 60");
        context.waitTicks(GROWTH_TICKS);
        server.runCommand("gamerule random_tick_speed 3");

        int[] ages = server.computeOnServer(minecraft -> new int[] { ages(minecraft.overworld(), scene.grower()), ages(minecraft.overworld(), scene.control()) });
        float supply = server.computeOnServer(minecraft -> minecraft.overworld().getBlockEntity(scene.grower()) instanceof LeyGrowerTile grower ? grower.supply() : -1F);
        expect(supply > 0.5F, "The grower should draw from the spur, supply " + supply);
        expect(ages[0] > ages[1] * 1.3F && ages[0] > 0, "Wheat around a powered grower should grow faster: " + ages[0] + " stages against " + ages[1]);
    }

    private static void plot(TestServerContext server, BlockPos centre)
    {
        int x = centre.getX(), z = centre.getZ();
        server.runCommand(String.format("fill %d %d %d %d %d %d farmland[moisture=7]", x - 3, FLAT_GROUND, z - 3, x + 3, FLAT_GROUND, z + 3));
        server.runCommand(String.format("fill %d %d %d %d %d %d wheat[age=0] replace air", x - 3, FLAT_FEET, z - 3, x + 3, FLAT_FEET, z + 3));
        setblock(server, centre.below(), "grass_block");
        setblock(server, new BlockPos(x + 3, FLAT_FEET, z + 3), "air");
        setblock(server, new BlockPos(x + 3, FLAT_GROUND, z + 3), "water");
    }

    private static int ages(ServerLevel level, BlockPos centre)
    {
        int total = 0;
        for (var pos : BlockPos.betweenClosed(centre.offset(-3, 0, -3), centre.offset(3, 0, 3)))
        {
            var state = level.getBlockState(pos);
            if (state.getBlock() instanceof CropBlock crop)
                total += crop.getAge(state);
        }
        return total;
    }

    /**
     * Breaking either half takes the whole stone and drops one, and the spur beyond it goes dark, so the ward stops holding.
     */
    private static void checkBreaking(ClientGameTestContext context, TestServerContext server, Scene scene)
    {
        breakAndCount(context, server, scene, scene.chained(), "the lower half");
        int unwarded = raiseAt(server, scene.at(scene.ward(), 0, 3));
        expect(unwarded == RISES * RISE, "With its stone gone, the ward should hold nothing back, corruption rose " + unwarded);

        placeStone(context, server, scene);
        context.waitTicks(5);
        breakAndCount(context, server, scene, scene.chained().above(), "the upper half");

        placeStone(context, server, scene);
        context.waitTicks(30);
        float supply = server.computeOnServer(minecraft -> ward(minecraft, scene).supply());
        expect(supply > 0, "A replaced stone should power the ward again, supply " + supply);
    }

    private static void breakAndCount(ClientGameTestContext context, TestServerContext server, Scene scene, BlockPos broken, String what)
    {
        server.runCommand("kill @e[type=item]");
        setblock(server, broken, "air destroy");
        context.waitTicks(30);
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            expect(level.getBlockState(scene.chained()).isAir() && level.getBlockState(scene.chained().above()).isAir(), "Breaking " + what + " should take the whole stone");
            var drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(scene.chained()).inflate(3));
            int stones = drops.stream().filter(item -> item.getItem().is(LeyStoneContent.LEY_STONE.asItem())).mapToInt(item -> item.getItem().getCount()).sum();
            expect(stones == 1, "Breaking " + what + " should drop exactly one stone, dropped " + stones);
            expect(!LeySpurs.get(level).plan().isConnected(scene.chained()), "A broken stone should leave its spur");
            expect(ward(minecraft, scene).strength() == 0, "The ward should go dark once its stone breaks, strength " + ward(minecraft, scene).strength());
        });
        server.runCommand("kill @e[type=item]");
    }

    private static void setblock(TestServerContext server, BlockPos pos, String block)
    {
        server.runCommand("setblock " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " " + block);
    }
}
