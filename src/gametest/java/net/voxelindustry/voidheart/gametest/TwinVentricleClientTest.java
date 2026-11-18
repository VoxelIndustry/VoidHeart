package net.voxelindustry.voidheart.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.voxelindustry.voidheart.common.content.repair.ExperienceSkullTile;
import net.voxelindustry.voidheart.common.content.research.Knowledge;
import net.voxelindustry.voidheart.common.content.secondheart.HeartRules;
import net.voxelindustry.voidheart.common.content.secondheart.SecondHeart;
import net.voxelindustry.voidheart.common.content.secondheart.ventricle.HeartCradleTile;
import net.voxelindustry.voidheart.common.content.secondheart.ventricle.TwinVentricleContent;
import net.voxelindustry.voidheart.common.content.secondheart.ventricle.VentricleBalance;
import net.voxelindustry.voidheart.common.content.secondheart.ventricle.VentricleCeremony;
import net.voxelindustry.voidheart.common.content.secondheart.ventricle.VentricleCeremony.Phase;
import net.voxelindustry.voidheart.common.content.secondheart.ventricle.VentricleLayout;
import net.voxelindustry.voidheart.common.content.secondheart.ventricle.VentricleTear;
import net.voxelindustry.voidheart.common.energy.creative.CreativeEnergyContent;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;

import static net.voxelindustry.voidheart.gametest.GameTestKit.*;

/**
 * The Twin Ventricle runs its rite: the chamber is checked, the offering and experience taken, the basins kindle and the heart beats, calling
 * creatures through tears; the celebrant's death starts the beat over, and when it stops the celebrant dies and reforms with a second heart.
 */
public class TwinVentricleClientTest implements FabricClientGameTest
{
    private static final BlockPos CRADLE = new BlockPos(0, FLAT_FEET, 14);
    private static final BlockPos SKULL  = CRADLE.offset(0, 0, 3);
    private static final VentricleLayout.Orientation ORIENTATION = new VentricleLayout.Orientation(Direction.Axis.X, true);

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
            server.runCommand("gamemode survival @a");
            server.runCommand("execute as @a run voidheart research grant voidheart:second_heart");
            server.runOnServer(minecraft -> Knowledge.attune(minecraft, player(minecraft).getUUID(), 3));

            var saved = new ClientSettings(context.computeOnClient(minecraft -> minecraft.options.getCameraType()), context.computeOnClient(minecraft -> minecraft.gui.hud.isHidden()));
            // Its scenes toggle the HUD with F1, so they start from it shown
            context.runOnClient(minecraft ->
            {
                if (minecraft.gui.hud.isHidden())
                    minecraft.gui.hud.toggle();
            });
            try
            {
                brokenCradleGivesBack(context, server);
                build(server);
                chamberIsChecked(context, server);
                offeringIsTaken(context, server);
                basinsKindle(context, server);
                beatCallsWaves(context, server);
                deathStartsTheBeatOver(context, server);
                heartStops(context, server);
            } finally
            {
                restoreClient(context, server, saved);
            }
        }
    }

    private static HeartCradleTile cradle(MinecraftServer minecraft)
    {
        return (HeartCradleTile) minecraft.overworld().getBlockEntity(CRADLE);
    }

    /**
     * A lone cradle, given part of an offering and broken, drops itself and what it held.
     */
    private static void brokenCradleGivesBack(ClientGameTestContext context, TestServerContext server)
    {
        var lone = new BlockPos(-10, FLAT_FEET, 0);
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            level.setBlockAndUpdate(lone, TwinVentricleContent.HEART_CRADLE.defaultBlockState());
            ((HeartCradleTile) level.getBlockEntity(lone)).offer(player(minecraft), new ItemStack(VoidHeartItems.MEMORIAM_SHARD, 3));
            level.destroyBlock(lone, true);
        });
        context.waitTicks(5);
        server.runOnServer(minecraft ->
        {
            var drops = minecraft.overworld().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new AABB(lone).inflate(2));
            int shards = drops.stream().filter(drop -> drop.getItem().is(VoidHeartItems.MEMORIAM_SHARD)).mapToInt(drop -> drop.getItem().getCount()).sum();
            boolean cradle = drops.stream().anyMatch(drop -> drop.getItem().is(TwinVentricleContent.HEART_CRADLE.asItem()));
            expect(shards == 3 && cradle, "Expected the broken cradle to drop itself and its 3 shards, got " + drops);
            drops.forEach(net.minecraft.world.entity.Entity::discard);
        });
    }

    private static void build(TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            level.setBlockAndUpdate(CRADLE, TwinVentricleContent.HEART_CRADLE.defaultBlockState());
            for (var part : VentricleLayout.expected(ORIENTATION))
            {
                var block = switch (part.part())
                {
                    case DAIS -> VoidHeartBlocks.VOIDSTONE_POLISHED;
                    case VALVE -> TwinVentricleContent.RAVENOUS_GOLD_VALVE;
                    case BASIN -> TwinVentricleContent.VENTRICLE_BASIN;
                    case RUBRUM_WALL -> TwinVentricleContent.RUBRUM_VEINED_VOIDSTONE;
                    case AZURIUM_WALL -> TwinVentricleContent.AZURIUM_VEINED_VOIDSTONE;
                };
                level.setBlockAndUpdate(CRADLE.offset(part.offset()), block.defaultBlockState());
            }
            // One wall left out at first
            level.setBlockAndUpdate(CRADLE.offset(ORIENTATION.along(-4, 1)), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(CRADLE.below(3), CreativeEnergyContent.CREATIVE_VOID_WELL.defaultBlockState());
            level.setBlockAndUpdate(SKULL, VoidHeartBlocks.EXPERIENCE_SKULL.defaultBlockState());
            ((ExperienceSkullTile) level.getBlockEntity(SKULL)).setExperience(ExperienceSkullTile.MAX_EXPERIENCE);
        });
    }

    private static void chamberIsChecked(ClientGameTestContext context, TestServerContext server)
    {
        context.waitTicks(25);
        expect(!server.computeOnServer(minecraft -> cradle(minecraft).isComplete()), "Expected a chamber missing a wall to be incomplete");
        server.runOnServer(minecraft -> minecraft.overworld().setBlockAndUpdate(CRADLE.offset(ORIENTATION.along(-4, 1)),
                TwinVentricleContent.AZURIUM_VEINED_VOIDSTONE.defaultBlockState()));
        waitFor(context, server, 40, "Expected the whole chamber to be complete", minecraft -> cradle(minecraft).isComplete());
    }

    private static void offeringIsTaken(ClientGameTestContext context, TestServerContext server)
    {
        // The core and the Dark Amalgam through real right clicks on the cradle, standing on its dais, and the rest straight in
        server.runOnServer(minecraft -> player(minecraft).setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(VoidHeartItems.KERNACIAN_CORE)));
        aimCamera(server, CRADLE.getX() + 0.5, CRADLE.getY() + 1, CRADLE.getZ() - 0.6, CRADLE.getX() + 0.5, CRADLE.getY() + 0.2, CRADLE.getZ() + 0.5);
        context.waitTicks(5);
        rightClick(context);
        waitFor(context, server, 20, "Expected right clicking the cradle with the core to offer it", minecraft -> player(minecraft).getMainHandItem().isEmpty());
        server.runOnServer(minecraft -> player(minecraft).setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(VoidHeartItems.DARK_AMALGAM)));
        context.waitTicks(5);
        rightClick(context);
        waitFor(context, server, 20, "Expected right clicking the cradle with a Dark Amalgam to offer it", minecraft -> player(minecraft).getMainHandItem().isEmpty());
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            var cradle = cradle(minecraft);
            expect(cradle.ceremony().offered(VentricleCeremony.Ingredient.KERNACIAN_CORE) == 1, "Expected the core in the cradle");
            expect(cradle.ceremony().offered(VentricleCeremony.Ingredient.DARK_AMALGAM) == 1, "Expected the Dark Amalgam in the cradle");
            cradle.offer(player, new ItemStack(VoidHeartItems.MEMORIAM_SHARD, 12));
            cradle.offer(player, new ItemStack(VoidHeartItems.WRAITH_INK, 4));
            expect(cradle.ceremony().itemsComplete(), "Expected every item of the offering in");
        });
        waitFor(context, server, 200, "Expected the cradle to drink the skull and kindle", minecraft -> cradle(minecraft).ceremony().phase() == Phase.KINDLING);
        expect(server.computeOnServer(minecraft -> ((ExperienceSkullTile) minecraft.overworld().getBlockEntity(SKULL)).getExperience()) == 0,
                "Expected the skull drained");
    }

    private static void basinsKindle(ClientGameTestContext context, TestServerContext server)
    {
        expect(server.computeOnServer(minecraft -> cradle(minecraft).ceremony().kindling()) == 0, "Expected no kindling without essence");
        server.runCommand("execute as @a run voidheart secondheart ventricle " + CRADLE.getX() + " " + CRADLE.getY() + " " + CRADLE.getZ() + " fill");
        waitFor(context, server, 40, "Expected the basins to kindle once filled", minecraft -> cradle(minecraft).ceremony().kindling() > 10);

        server.runCommand("gamemode creative @a");
        context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
        aimFrom(context, server, CRADLE.getX() + 0.5, CRADLE.getY() + 3.5, CRADLE.getZ() - 5.5, CRADLE.getX() + 0.5, CRADLE.getY() + 0.5, CRADLE.getZ() + 0.5);
        context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_F1);
        var seen = context.computeOnClient(minecraft -> minecraft.level.getBlockEntity(CRADLE) instanceof HeartCradleTile tile
                ? tile.ceremony().phase() + " complete=" + tile.isComplete() + " running=" + tile.isRunning() + " basins=" + (tile.basins(minecraft.level) != null)
                : "no tile");
        expect(seen.startsWith("KINDLING complete=true running=true basins=true"), "Expected the client to see the rite kindling, saw " + seen);
        for (int shot = 0; shot < 3; shot++)
        {
            context.takeScreenshot("twin-ventricle-kindling-" + shot);
            context.waitTicks(9);
        }
        aimFrom(context, server, CRADLE.getX() + 0.5, CRADLE.getY() + 4.5, CRADLE.getZ() - 1.5, CRADLE.getX() + 0.5, CRADLE.getY(), CRADLE.getZ() + 0.5);
        for (int shot = 0; shot < 2; shot++)
        {
            context.takeScreenshot("twin-ventricle-kindling-above-" + shot);
            context.waitTicks(7);
        }
        // The basins, strands and glow held still while the camera steps along the chamber, so flicker between layers shows
        server.runCommand("tick freeze");
        try
        {
            for (int step = 0; step < 8; step++)
            {
                aimFrom(context, server, CRADLE.getX() + 0.5 + step * 0.06, CRADLE.getY() + 2.6, CRADLE.getZ() - 3.5 + step * 0.08,
                        CRADLE.getX() + 0.5, CRADLE.getY() + 0.5, CRADLE.getZ() + 0.5, 2);
                context.takeScreenshot("twin-ventricle-kindling-sweep-" + step);
            }
        } finally
        {
            server.runCommand("tick unfreeze");
        }
        server.runCommand("execute as @a run voidheart secondheart ventricle " + CRADLE.getX() + " " + CRADLE.getY() + " " + CRADLE.getZ() + " skip kindling "
                + (VentricleBalance.KINDLING_TICKS - 5));
        waitFor(context, server, 40, "Expected the kindling to end in the beat", minecraft -> cradle(minecraft).ceremony().phase() == Phase.BEATING);
        context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_F1);
    }

    private static void standInCradle(ClientGameTestContext context, TestServerContext server)
    {
        server.runCommand("tp @a " + (CRADLE.getX() + 0.5) + " " + (CRADLE.getY() + 0.375) + " " + (CRADLE.getZ() + 0.5) + " 0 20");
        context.waitTicks(3);
    }

    private static void beatCallsWaves(ClientGameTestContext context, TestServerContext server)
    {
        server.runCommand("gamemode survival @a");
        standInCradle(context, server);
        expect(server.computeOnServer(minecraft -> cradle(minecraft).inCradle(player(minecraft).position())), "Expected the player in the cradle");
        // A tick short of the first wave's beat
        server.runCommand("execute as @a run voidheart secondheart ventricle " + CRADLE.getX() + " " + CRADLE.getY() + " " + CRADLE.getZ() + " skip beating "
                + (VentricleBalance.FIRST_WAVE_BEAT * VentricleBalance.BEAT_TICKS - 2));
        waitFor(context, server, 40, "Expected the first wave's tears", minecraft -> !minecraft.overworld().getEntities(TwinVentricleContent.VENTRICLE_TEAR,
                tear -> true).isEmpty());
        server.runCommand("effect give @a resistance 30 4 true");

        context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK));
        context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_F1);
        context.waitTicks(6);
        context.takeScreenshot("twin-ventricle-tears-open");
        context.waitTicks(VentricleBalance.TEAR_RELEASE_TICK);
        context.takeScreenshot("twin-ventricle-wave");
        context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_F1);

        int called = server.computeOnServer(minecraft -> minecraft.overworld().getEntitiesOfClass(Mob.class, new AABB(CRADLE).inflate(16),
                mob -> mob.entityTags().contains(VentricleTear.WAVE_TAG)).size());
        expect(called >= 1, "Expected the wave's creatures out of their tears, got " + called);
        server.runCommand("kill @e[tag=" + VentricleTear.WAVE_TAG + "]");
        server.runCommand("kill @e[type=voidheart:ventricle_tear]");
    }

    private static void deathStartsTheBeatOver(ClientGameTestContext context, TestServerContext server)
    {
        server.runCommand("gamerule immediate_respawn true");
        server.runCommand("effect clear @a");
        server.runCommand("execute as @a run voidheart secondheart ventricle " + CRADLE.getX() + " " + CRADLE.getY() + " " + CRADLE.getZ() + " skip beating 600");
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            player.hurtServer(player.level(), player.damageSources().generic(), 1000);
            expect(player.isDeadOrDying(), "Expected the celebrant, with no second heart yet, to die");
        });
        waitFor(context, server, 100, "Expected the celebrant to respawn", minecraft -> player(minecraft).isAlive());
        context.waitTicks(5);
        server.runOnServer(minecraft ->
        {
            var ceremony = cradle(minecraft).ceremony();
            expect(ceremony.phase() == Phase.BEATING, "Expected the rite still beating, got " + ceremony.phase());
            expect(ceremony.beating() == 0 && ceremony.waves() == 0, "Expected the beat to start over, at " + ceremony.beating());
            expect(ceremony.offered(net.voxelindustry.voidheart.common.content.secondheart.ventricle.VentricleCeremony.Ingredient.KERNACIAN_CORE) == 1,
                    "Expected the offering kept");
        });
        server.runCommand("kill @e[tag=" + VentricleTear.WAVE_TAG + "]");
    }

    private static void heartStops(ClientGameTestContext context, TestServerContext server)
    {
        server.runCommand("kill @e[type=!player]");
        standInCradle(context, server);
        server.runCommand("execute as @a run voidheart secondheart ventricle " + CRADLE.getX() + " " + CRADLE.getY() + " " + CRADLE.getZ() + " skip beating "
                + (VentricleBalance.BEATING_TICKS - 5));
        waitFor(context, server, 40, "Expected the beat to stop", minecraft -> cradle(minecraft).ceremony().phase() == Phase.IDLE);
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            expect(SecondHeart.state(player).owned(), "Expected the celebrant to own a second heart");
            expect(SecondHeart.sequence(player) != null, "Expected the rite to be the heart's first use");
            expect(knowledge(minecraft).getAttunement() == 4, "Expected attunement 4, got " + knowledge(minecraft).getAttunement());
        });
        context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK));
        context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_F1);
        waitFor(context, server, 40, "Expected the dissolve under way", minecraft -> SecondHeart.elapsed(player(minecraft), 0) >= 15);
        context.takeScreenshot("twin-ventricle-heart-stops");
        // Checked in the grace after the burst, before any creature left about could strike
        waitFor(context, server, HeartRules.SEQUENCE_TICKS, "Expected the reform", minecraft -> SecondHeart.elapsed(player(minecraft), 0) > HeartRules.BURST_TICK);
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            expect(player.isAlive() && player.getHealth() == player.getMaxHealth(), "Expected the celebrant whole after the rite, health " + player.getHealth());
            expect(cradle(minecraft).inCradle(player.position()), "Expected the celebrant reformed in the cradle, at " + player.position());
        });
        waitFor(context, server, HeartRules.SEQUENCE_TICKS + 20, "Expected the sequence to end", minecraft -> SecondHeart.sequence(player(minecraft)) == null);
        context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_F1);
    }
}
