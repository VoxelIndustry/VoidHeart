package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.crystal.BundleSize;
import net.voxelindustry.voidheart.common.content.crystal.CradleResearch;
import net.voxelindustry.voidheart.common.content.crystal.CrystalBundleBlock;
import net.voxelindustry.voidheart.common.content.crystal.CrystalBundles;
import net.voxelindustry.voidheart.common.content.crystal.CrystalCradleTile;
import net.voxelindustry.voidheart.common.content.crystal.CrystalHabit;
import net.voxelindustry.voidheart.common.content.crystal.CrystalImprint;
import net.voxelindustry.voidheart.common.content.crystal.CrystalKind;
import net.voxelindustry.voidheart.common.content.crystal.ScouredSoak;
import net.voxelindustry.voidheart.common.content.mob.VoidMonster;
import net.voxelindustry.voidheart.common.content.research.Knowledge;
import net.voxelindustry.voidheart.common.energy.VoidSeep;
import net.voxelindustry.voidheart.common.energy.ley.LeyStoneBalance;
import net.voxelindustry.voidheart.common.energy.ley.LeyStoneTile;
import net.voxelindustry.voidheart.common.energy.ley.LeyWardTile;
import net.voxelindustry.voidheart.common.energy.ley.SpurTap;
import net.voxelindustry.voidheart.common.energy.spike.AzuriumSpikeTile;
import net.voxelindustry.voidheart.common.energy.spike.LeySpikeContent;
import net.voxelindustry.voidheart.common.ley.Ley;
import net.voxelindustry.voidheart.common.ley.LeyEdge;
import net.voxelindustry.voidheart.common.ley.LeyLattice;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartDataComponents;
import net.voxelindustry.voidheart.common.setup.VoidHeartEntities;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;

import java.util.List;

import static net.voxelindustry.voidheart.VoidHeart.MODID;
import static net.voxelindustry.voidheart.gametest.GameTestKit.*;

/**
 * Crystallography's second step, on a flat world: a cradle in a fissure's seep grows Fouled, a Scoured pylon halves the seep in its field, a cradle by a
 * stone on a real ley line scours Fouled azurium into that line's upstream node and stalls while the node is breached, Scoured rubrum by a node draws its
 * taint in until it turns Fouled, a spike driven around a Fouled bundle stays Fouled, and a ward holding an imprinted memory keeps that creature out.
 */
public class CrystalScouringClientTest implements FabricClientGameTest
{
    private static final int Y = FLAT_FEET;

    private static final BlockPos WOUND        = new BlockPos(100, Y, 0);
    private static final BlockPos SEEP_CRADLE  = new BlockPos(103, Y, 0);
    private static final BlockPos SEEP_POINT   = new BlockPos(106, Y, 0);
    private static final BlockPos SEEP_FAR     = new BlockPos(100, Y, -8);
    private static final BlockPos SCOUR_PYLON  = new BlockPos(106, Y, 3);
    private static final int      SEEP_RADIUS  = 10;

    private static final BlockPos SPIKE = new BlockPos(20, Y, 20);

    private static final BlockPos WARD_STONE = new BlockPos(0, Y, 40);
    private static final BlockPos WARD       = new BlockPos(2, Y, 40);
    private static final int      KEEP_TICKS = 160;

    private static final int STONE_OUT  = 6;
    private static final int CRADLE_OUT = 9;

    /**
     * A standing stone a few blocks off a real line and a cradle beyond it, so the ink thread runs from the cradle past the stone to the line.
     */
    private record Line(LeyEdge edge, BlockPos stone, BlockPos cradle, long upstream, Direction facing)
    {
    }

    @Override
    public void runTest(ClientGameTestContext context)
    {
        try (var world = context.worldBuilder().create())
        {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            freezeTime(server, "noon");
            server.runCommand("gamerule spawn_mobs false");
            server.runCommand("difficulty normal");
            server.runCommand("gamemode creative @a");
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
            server.runOnServer(minecraft ->
            {
                var knowledge = Knowledge.of(minecraft, player(minecraft).getUUID());
                knowledge.grant(CradleResearch.CRYSTAL_CRADLE);
                knowledge.grant(CradleResearch.researchFor(CrystalHabit.FOULED));
                knowledge.grant(CradleResearch.researchFor(CrystalHabit.SCOURED));
            });
            context.waitTicks(10);

            checkTranslations(context);
            checkSeepGrowth(context, server);
            checkScouredField(context, server);
            checkFouledSpike(context, server);
            checkWardKeepsOut(context, server);
            checkTooltips(context);

            var line = server.computeOnServer(minecraft -> findLine(minecraft.overworld()));
            var middle = line.cradle();
            server.runCommand("forceload add " + (middle.getX() - 40) + " " + (middle.getZ() - 40) + " " + (middle.getX() + 40) + " " + (middle.getZ() + 40));
            tp(server, line.cradle().getX() + 0.5, Y, line.cradle().getZ() + 4.5);
            context.waitTicks(40);
            world.getConnection().waitForChunksRender();
            checkScouring(context, server, line);
            checkSoaking(server, line);
        }
    }

    private static String at(BlockPos pos)
    {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    private static void checkTranslations(ClientGameTestContext context)
    {
        context.runOnClient(minecraft ->
        {
            var language = Language.getInstance();
            for (var key : List.of("research." + MODID + ".seep_growth", "research." + MODID + ".scouring", MODID + ".jade.cradle.scouring",
                    MODID + ".jade.cradle.breached", MODID + ".jade.cradle.fed.seep", MODID + ".habit.fouled.greedy", MODID + ".habit.scoured.holds",
                    MODID + ".habit.scoured.draws", MODID + ".habit.scoured.blank", MODID + ".jade.ley_ward.keeps_out", MODID + ".crystal.imprint.done"))
                expect(language.has(key), "Missing translation " + key);
        });
    }

    /**
     * A cradle standing in a strong seep drinks it, grows half again as fast as on ley, and grows Fouled for an owner who studied it.
     */
    private static void checkSeepGrowth(ClientGameTestContext context, TestServerContext server)
    {
        server.runCommand("voidheart energy fissure wound " + at(WOUND));
        server.runCommand("voidheart energy fissure " + at(WOUND) + " seep " + SEEP_RADIUS);
        server.runCommand("setblock " + at(SEEP_CRADLE) + " voidheart:crystal_cradle");
        context.waitTicks(20);
        server.runOnServer(minecraft ->
        {
            var cradle = cradle(minecraft, SEEP_CRADLE);
            cradle.claim(player(minecraft).getUUID());
            cradle.seed(CrystalKind.AZURIUM, 4, CrystalHabit.NONE);
        });
        context.waitTicks(40);
        server.runOnServer(minecraft ->
        {
            var cradle = cradle(minecraft, SEEP_CRADLE);
            expect(cradle.seep() >= 0.25F, "The cradle should stand in a strong seep, got " + cradle.seep());
            expect(cradle.source() == CrystalCradleTile.Source.SEEP && cradle.feed() == CrystalHabit.FOULED,
                    "A cradle in a seep should drink it and grow Fouled, got " + cradle.source() + " " + cradle.feed());
            expect(cradle.growth().grown() > 0, "A cradle in a seep needs no ley to grow");
            expect(cradle.preview() == CrystalHabit.FOULED, "It should come out Fouled for its owner, got " + cradle.preview());
            cradle.finish(CrystalHabit.FOULED);
            var items = cradle.harvest();
            expect(items.size() == 1 && items.getFirst().is(VoidHeartBlocks.LARGE_AZURIUM_BUNDLE.asItem()) && CrystalBundles.habitOf(items.getFirst()) == CrystalHabit.FOULED,
                    "A seep-grown cradle should give a Fouled large bundle, got " + items);
            cradle.seed(CrystalKind.AZURIUM, 4, CrystalHabit.NONE);
        });
    }

    /**
     * A pylon set with Scoured azurium halves the seep inside its field, and leaves it whole outside.
     */
    private static void checkScouredField(ClientGameTestContext context, TestServerContext server)
    {
        float[] before = server.computeOnServer(minecraft -> new float[] {
                VoidSeep.strengthAt(minecraft.overworld(), SEEP_POINT), VoidSeep.strengthAt(minecraft.overworld(), SEEP_FAR) });
        expect(before[0] > 0.1F && before[1] > 0.05F, "Both points should stand in the seep, got " + before[0] + " and " + before[1]);
        server.runCommand("setblock " + at(SCOUR_PYLON) + " voidheart:azurium_pylon");
        server.runCommand("setblock " + at(SCOUR_PYLON.above()) + " voidheart:small_azurium_bundle[facing=up,habit=scoured]");
        context.waitTicks(45);
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            float inside = VoidSeep.strengthAt(level, SEEP_POINT);
            float outside = VoidSeep.strengthAt(level, SEEP_FAR);
            expect(Math.abs(inside - before[0] / 2) < 0.02F, "A Scoured field should halve the seep inside it: " + before[0] + " became " + inside);
            expect(Math.abs(outside - before[1]) < 0.02F, "The seep outside the field should stay: " + before[1] + " became " + outside);
        });

        server.runCommand("gamemode spectator @a");
        context.getInput().pressKey(InputConstants.KEY_F1);
        aimFrom(context, server, SEEP_CRADLE.getX() - 1.5, Y + 1.4, SEEP_CRADLE.getZ() - 2.2, SEEP_CRADLE.getX() + 0.5, Y + 0.2, SEEP_CRADLE.getZ() + 0.5, 20);
        context.takeScreenshot("cradle-seep-pool");
        server.runCommand("time set midnight");
        context.waitTicks(10);
        context.takeScreenshot("cradle-seep-pool-night");
        server.runCommand("time set noon");
        context.getInput().pressKey(InputConstants.KEY_F1);
        server.runCommand("gamemode creative @a");
    }

    /**
     * Driven around a Fouled bundle, a spike keeps its foulness, and gives it back when taken up.
     */
    private static void checkFouledSpike(ClientGameTestContext context, TestServerContext server)
    {
        tp(server, SPIKE.getX() + 0.5, Y, SPIKE.getZ() - 3.5);
        context.waitTicks(5);
        server.runOnServer(minecraft ->
        {
            var spike = new ItemStack(LeySpikeContent.AZURIUM_SPIKE);
            spike.set(VoidHeartDataComponents.CRYSTAL_HABIT, CrystalHabit.FOULED);
            player(minecraft).setItemInHand(InteractionHand.MAIN_HAND, spike);
            useOn(player(minecraft), SPIKE.below());
        });
        context.waitTicks(5);
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            expect(level.getBlockEntity(SPIKE) instanceof AzuriumSpikeTile tile && tile.isFouled(), "A spike placed from a Fouled spike should be Fouled");
            level.destroyBlock(SPIKE, true);
            var drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(SPIKE).inflate(3)).stream().map(ItemEntity::getItem)
                    .filter(stack -> stack.is(LeySpikeContent.AZURIUM_SPIKE.asItem())).toList();
            expect(drops.size() == 1 && drops.getFirst().get(VoidHeartDataComponents.CRYSTAL_HABIT) == CrystalHabit.FOULED,
                    "A broken Fouled spike should drop a Fouled spike, got " + drops);
            level.getEntitiesOfClass(ItemEntity.class, new AABB(SPIKE).inflate(3)).forEach(ItemEntity::discard);
            player(minecraft).setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        });
    }

    /**
     * A blank Scoured memoriam remembers a Void Ghoul, but never a great one; set in a ward on ley, the ward keeps ghouls from walking in and walks one
     * that stood inside back out.
     */
    private static void checkWardKeepsOut(ClientGameTestContext context, TestServerContext server)
    {
        server.runCommand("setblock " + at(WARD_STONE) + " voidheart:creative_ley_stone");
        server.runCommand("setblock " + at(WARD) + " voidheart:ley_ward");
        tp(server, WARD.getX() + 0.5, Y, WARD.getZ() - 30.5);
        context.waitTicks(20);
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            var player = player(minecraft);
            var dragoon = VoidHeartEntities.KERNACIAN_DRAGOON.create(level, EntitySpawnReason.COMMAND);
            expect(dragoon != null && !CrystalImprint.canImprint(dragoon), "A great one of the void should not be remembered");

            var ghoul = VoidHeartEntities.VOID_GHOUL.create(level, EntitySpawnReason.COMMAND);
            player.setItemInHand(InteractionHand.MAIN_HAND, CrystalBundles.bundleItem(CrystalKind.MEMORIAM, BundleSize.SMALL, CrystalHabit.SCOURED));
            var result = CrystalImprint.use(player, level, InteractionHand.MAIN_HAND, ghoul, null);
            // In creative the blank one stays in hand and the imprinted one goes to the inventory
            var memory = player.getInventory().getNonEquipmentItems().stream()
                    .filter(stack -> stack.get(VoidHeartDataComponents.CRYSTAL_IMPRINT) != null).findFirst().orElse(ItemStack.EMPTY).copy();
            player.setItemInHand(InteractionHand.MAIN_HAND, memory);
            expect(result == InteractionResult.SUCCESS && memory.get(VoidHeartDataComponents.CRYSTAL_IMPRINT) == VoidHeartEntities.VOID_GHOUL,
                    "A blank memory used on a ghoul should remember ghouls, got " + memory.get(VoidHeartDataComponents.CRYSTAL_IMPRINT));
            useOn(player, WARD);
            expect(level.getBlockEntity(WARD) instanceof LeyWardTile ward && ward.keepsOut() == VoidHeartEntities.VOID_GHOUL,
                    "The ward should take the memory and keep ghouls out");
        });
        context.waitTicks(20);

        var center = Vec3.atCenterOf(WARD);
        int[] ids = server.computeOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            var outside = spawnGhoul(level, center.add(LeyStoneBalance.WARD_RADIUS + 6, -0.5, 0));
            var inside = spawnGhoul(level, center.add(-5, -0.5, 0));
            expect(level.getBlockEntity(WARD) instanceof LeyWardTile ward && ward.strength() > 0, "The ward should hold on the creative stone's ley");
            expect(!outside.mayGoTo(center), "A ghoul should not be let step into the ward's reach");
            outside.getNavigation().moveTo(center.x, center.y, center.z, 1.0);
            return new int[] { outside.getId(), inside.getId() };
        });
        context.waitTicks(KEEP_TICKS);
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            var outside = level.getEntity(ids[0]);
            var inside = level.getEntity(ids[1]);
            expect(outside != null && outside.position().distanceTo(center) > LeyStoneBalance.WARD_RADIUS - 1,
                    "A ghoul sent at the ward should stop at its edge, it is " + (outside == null ? "gone" : outside.position().distanceTo(center)) + " away");
            expect(inside != null && inside.position().distanceTo(center) > LeyStoneBalance.WARD_RADIUS - 1,
                    "A ghoul inside the ward should walk out, it is " + (inside == null ? "gone" : inside.position().distanceTo(center)) + " away");
            if (outside != null)
                outside.discard();
            if (inside != null)
                inside.discard();
        });
    }

    private static VoidMonster spawnGhoul(ServerLevel level, Vec3 at)
    {
        var ghoul = VoidHeartEntities.VOID_GHOUL.create(level, EntitySpawnReason.COMMAND);
        ghoul.snapTo(at.x, Y, at.z, 0, 0);
        level.addFreshEntity(ghoul);
        return ghoul;
    }

    private static void checkTooltips(ClientGameTestContext context)
    {
        context.runOnClient(minecraft ->
        {
            var spike = new ItemStack(LeySpikeContent.AZURIUM_SPIKE);
            spike.set(VoidHeartDataComponents.CRYSTAL_HABIT, CrystalHabit.FOULED);
            expectLines(minecraft, spike, "Fouled", "Twice the Contravoid and taint");
            expect(lines(minecraft, spike).stream().noneMatch(line -> line.startsWith("Regrows")), "A Fouled spike should not say it regrows");

            expectLines(minecraft, CrystalBundles.bundleItem(CrystalKind.AZURIUM, BundleSize.SMALL, CrystalHabit.SCOURED), "Scoured", "Pylon: halves the seep in its field");
            expectLines(minecraft, CrystalBundles.bundleItem(CrystalKind.RUBRUM, BundleSize.LARGE, CrystalHabit.FOULED),
                    "Construct: holds twice as much", "Construct: leaks seep when over half full");
            expectLines(minecraft, CrystalBundles.bundleItem(CrystalKind.RUBRUM, BundleSize.LARGE, CrystalHabit.SCOURED), "Near a ley node: draws its taint in");
            expectLines(minecraft, CrystalBundles.bundleItem(CrystalKind.MEMORIAM, BundleSize.SMALL, CrystalHabit.SCOURED), "Use on a void creature to imprint it");
            var memory = CrystalBundles.bundleItem(CrystalKind.MEMORIAM, BundleSize.SMALL, CrystalHabit.SCOURED);
            memory.set(VoidHeartDataComponents.CRYSTAL_IMPRINT, VoidHeartEntities.VOID_GHOUL);
            expectLines(minecraft, memory, "Ley Ward: keeps them out");
            expect(lines(minecraft, memory).stream().anyMatch(line -> line.startsWith("Remembers: ")), "An imprinted memory should say what it remembers");
        });
    }

    private static void expectLines(Minecraft minecraft, ItemStack stack, String... expected)
    {
        var lines = lines(minecraft, stack);
        for (var line : expected)
            expect(lines.contains(line), stack.getItem() + "'s tooltip should read \"" + line + "\": " + lines);
    }

    private static List<String> lines(Minecraft minecraft, ItemStack stack)
    {
        return stack.getTooltipLines(Item.TooltipContext.of(minecraft.level), minecraft.player, TooltipFlag.NORMAL).stream().map(Component::getString).toList();
    }

    /**
     * Looks along nearby lines for one with flow, and stands a stone within its reach on the flat ground beside it.
     */
    private static Line findLine(ServerLevel level)
    {
        var lattice = LeyLattice.get(level).orElseThrow(() -> new AssertionError("Expected a ley lattice in the overworld"));
        for (int probe = 0; probe < 25; probe++)
        {
            var edge = lattice.nearestEdge((probe % 5 - 2) * 400, (probe / 5 - 2) * 400).orElse(null);
            if (edge == null || edge.length() < 48)
                continue;
            var along = new Vec3(edge.toX() - edge.fromX(), 0, edge.toZ() - edge.fromZ()).normalize();
            var out = new Vec3(-along.z, 0, along.x);
            var onLine = new Vec3(edge.fromX() + (edge.toX() - edge.fromX()) * 0.5, 0, edge.fromZ() + (edge.toZ() - edge.fromZ()) * 0.5);
            var stone = BlockPos.containing(onLine.x + out.x * STONE_OUT, Y, onLine.z + out.z * STONE_OUT);
            var cradle = BlockPos.containing(onLine.x + out.x * CRADLE_OUT, Y, onLine.z + out.z * CRADLE_OUT);
            var line = Ley.lineNear(level, stone, LeyStoneBalance.STONE_REACH);
            if (line.isEmpty())
                continue;
            var tap = SpurTap.of(line.get(), stone.getX() + 0.5, stone.getZ() + 0.5);
            if (Ley.lineFlowAt(level, tap.claimant(stone.getY())) <= 0)
                continue;
            return new Line(line.get(), stone, cradle, tap.fromNode(), Direction.getApproximateNearest(-out.x, 0, -out.z));
        }
        throw new AssertionError("Found no line with flow near the origin");
    }

    /**
     * Fouled azurium beside a stone on a real line is scoured: the thread of ink runs down the spur, the node upstream takes five taint for each shard,
     * the scouring stalls while the node is breached, and a Scoured bundle comes out.
     */
    private static void checkScouring(ClientGameTestContext context, TestServerContext server, Line line)
    {
        var facing = line.facing().getSerializedName();
        server.runCommand("setblock " + at(line.stone()) + " voidheart:ley_stone[half=lower,facing=" + facing + "]");
        server.runCommand("setblock " + at(line.stone().above()) + " voidheart:ley_stone[half=upper,facing=" + facing + "]");
        server.runCommand("setblock " + at(line.cradle()) + " voidheart:crystal_cradle");
        context.waitTicks(60);
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            expect(level.getBlockEntity(line.stone()) instanceof LeyStoneTile stone && stone.isConnected() && stone.isRoot(), "The stone should tap the line");
            Ley.addTaint(level, line.upstream(), -100);
            var cradle = cradle(minecraft, line.cradle());
            cradle.claim(player(minecraft).getUUID());
            cradle.seed(CrystalKind.AZURIUM, 4, CrystalHabit.FOULED);
        });
        context.waitTicks(60);
        server.runOnServer(minecraft ->
        {
            var cradle = cradle(minecraft, line.cradle());
            expect(cradle.source() == CrystalCradleTile.Source.SCOURING, "Fouled azurium by a stone should be scoured, got " + cradle.source());
            expect(line.stone().equals(cradle.scourStone()), "It should scour down the stone it draws from, got " + cradle.scourStone());
            expect(cradle.scourNode() != null && cradle.scourNode() == line.upstream(), "Its foulness should go to the line's upstream node");
            // Far enough for the thread to show while the bundle rises
            cradle.advance(CrystalHabit.SCOURED, 1.5);
        });

        server.runCommand("gamemode spectator @a");
        context.getInput().pressKey(InputConstants.KEY_F1);
        server.runCommand("tick freeze");
        var cradle = Vec3.atCenterOf(line.cradle());
        var stone = Vec3.atCenterOf(line.stone());
        var side = new Vec3(-(stone.z - cradle.z), 0, stone.x - cradle.x).normalize();
        var eye = cradle.add(side.scale(5)).add(cradle.subtract(stone).normalize().scale(3));
        aimFrom(context, server, eye.x, Y + 4, eye.z, (cradle.x + stone.x) / 2, Y, (cradle.z + stone.z) / 2, 20);
        context.takeScreenshot("cradle-scouring-thread");
        aimFrom(context, server, cradle.x - side.x * 1.6, Y + 1.3, cradle.z - side.z * 1.6, cradle.x, Y + 0.2, cradle.z, 5);
        context.takeScreenshot("cradle-scouring-close");
        server.runCommand("time set midnight");
        context.waitTicks(5);
        aimFrom(context, server, eye.x, Y + 4, eye.z, (cradle.x + stone.x) / 2, Y, (cradle.z + stone.z) / 2, 5);
        context.takeScreenshot("cradle-scouring-thread-night");
        server.runCommand("tick unfreeze");

        server.runOnServer(minecraft -> Ley.addTaint(minecraft.overworld(), line.upstream(), 85));
        context.waitTicks(30);
        double stalledAt = server.computeOnServer(minecraft ->
        {
            var tile = cradle(minecraft, line.cradle());
            expect(tile.source() == CrystalCradleTile.Source.BREACHED, "A breached line should stall the scouring, got " + tile.source());
            return tile.growth().grown();
        });
        context.waitTicks(30);
        server.runOnServer(minecraft -> expect(cradle(minecraft, line.cradle()).growth().grown() == stalledAt, "A stalled cradle should not grow"));
        server.runCommand("tick freeze");
        context.waitTicks(2);
        context.takeScreenshot("cradle-scouring-stalled-night");
        server.runCommand("tick unfreeze");
        server.runCommand("time set noon");
        context.getInput().pressKey(InputConstants.KEY_F1);
        server.runCommand("gamemode creative @a");

        server.runOnServer(minecraft -> Ley.addTaint(minecraft.overworld(), line.upstream(), -100));
        context.waitTicks(30);
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            var tile = cradle(minecraft, line.cradle());
            expect(tile.source() == CrystalCradleTile.Source.SCOURING, "Scouring should go on once the line heals, got " + tile.source());
            float before = Ley.taint(level, line.upstream());
            double left = 4 - tile.growth().grown();
            tile.advance(CrystalHabit.SCOURED, 4);
            float after = Ley.taint(level, line.upstream());
            expect(Math.abs(after - before - left * 5) < 0.5, "Scouring the rest should push " + left * 5 + " taint, pushed " + (after - before));
            var items = tile.harvest();
            expect(items.size() == 1 && CrystalBundles.habitOf(items.getFirst()) == CrystalHabit.SCOURED,
                    "A scoured cradle should give a Scoured bundle, got " + items);
            expect(Math.abs(tile.taintPushed() - 20) < 0.01, "Scouring four shards should cost the line 20 taint in all, cost " + tile.taintPushed());
        });
    }

    /**
     * Scoured rubrum by the line's node draws a taint a minute in, then turns Fouled once it holds what it can; broken while holding any, it gives Fouled
     * shards.
     */
    private static void checkSoaking(TestServerContext server, Line line)
    {
        var bundle = new BlockPos(line.edge().fromX() + 3, Y, line.edge().fromZ());
        var holding = bundle.offset(0, 0, 2);
        server.runCommand("forceload add " + (bundle.getX() - 8) + " " + (bundle.getZ() - 8) + " " + (bundle.getX() + 8) + " " + (bundle.getZ() + 8));
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            long node = line.edge().from();
            Ley.addTaint(level, node, 30 - Ley.taint(level, node));
            level.setBlockAndUpdate(bundle, CrystalBundles.bundle(CrystalKind.RUBRUM, BundleSize.SMALL).defaultBlockState()
                    .setValue(CrystalBundleBlock.HABIT, CrystalHabit.SCOURED));
            expect(level.getBlockTicks().hasScheduledTick(bundle, level.getBlockState(bundle).getBlock()), "Scoured rubrum should soak on a schedule");
            expect(CrystalBundleBlock.nodeNear(level, bundle) != null, "The bundle should lie within reach of the node");

            float start = Ley.taint(level, node);
            int capacity = ScouredSoak.capacity(BundleSize.SMALL);
            for (int soak = 1; soak < capacity; soak++)
            {
                level.getBlockState(bundle).tick(level, bundle, level.getRandom());
                expect(CrystalBundles.habitOf(level.getBlockState(bundle)) == CrystalHabit.SCOURED && CrystalBundles.soakOf(level.getBlockState(bundle)) == soak,
                        "After " + soak + " soak(s) it should still be Scoured holding " + soak + ", got " + level.getBlockState(bundle));
            }
            level.getBlockState(bundle).tick(level, bundle, level.getRandom());
            expect(CrystalBundles.habitOf(level.getBlockState(bundle)) == CrystalHabit.FOULED, "Full, it should turn Fouled, got " + level.getBlockState(bundle));
            expect(Math.abs(start - Ley.taint(level, node) - capacity) < 0.01, "It should have drawn " + capacity + " taint off the node, drew " + (start - Ley.taint(level, node)));

            var soaked = CrystalBundles.bundle(CrystalKind.RUBRUM, BundleSize.SMALL).defaultBlockState()
                    .setValue(CrystalBundleBlock.HABIT, CrystalHabit.SCOURED).setValue(CrystalBundleBlock.SOAKED, 1);
            level.setBlockAndUpdate(holding, soaked);
            var shards = Block.getDrops(soaked, level, holding, null, null, new ItemStack(Items.IRON_PICKAXE));
            expect(shards.size() == 1 && shards.getFirst().is(VoidHeartItems.RUBRUM_SHARD) && CrystalBundles.habitOf(shards.getFirst()) == CrystalHabit.FOULED,
                    "Scoured rubrum broken while holding taint should give Fouled shards, got " + shards);
            var whole = CrystalBundles.itemOf(soaked);
            expect(CrystalBundles.soakOf(whole) == 1 && CrystalBundles.habitOf(whole) == CrystalHabit.SCOURED, "Taken whole it should keep what it holds");
            level.removeBlock(holding, false);
        });
    }

    private static CrystalCradleTile cradle(MinecraftServer minecraft, BlockPos pos)
    {
        if (minecraft.overworld().getBlockEntity(pos) instanceof CrystalCradleTile cradle)
            return cradle;
        throw new AssertionError("Expected a cradle at " + pos);
    }
}
