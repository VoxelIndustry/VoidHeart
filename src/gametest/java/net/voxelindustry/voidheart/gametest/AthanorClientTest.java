package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.voxelindustry.voidheart.common.content.athanor.AthanorTile;
import net.voxelindustry.voidheart.common.content.athanor.CrucibleTile;
import net.voxelindustry.voidheart.common.content.essence.EssenceRegistry;
import net.voxelindustry.voidheart.common.content.essence.RavenousRelayTile;
import net.voxelindustry.voidheart.common.content.essence.RavenousStorageTile;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;
import net.voxelindustry.voidheart.common.setup.VoidHeartRecipes;

/**
 * A glass bottle set in the athanor brews into searing oil once an alembic tops it: the crucibles' sulfur and quicksilver go in, and the intake draws
 * the fiery essence it needs from a storage its relay reaches.
 */
public class AthanorClientTest implements FabricClientGameTest
{
    private static final int GROUND_Y = -60;
    private static final BlockPos INTAKE = new BlockPos(0, GROUND_Y, 0);
    private static final BlockPos CORE = INTAKE.above();
    private static final BlockPos ALEMBIC = CORE.above();
    private static final BlockPos SULFUR_CRUCIBLE = CORE.east();
    private static final BlockPos QUICKSILVER_CRUCIBLE = CORE.west();
    // On the diagonal, so the stone under the crucibles stays out of the relay's sight of the intake
    private static final BlockPos RELAY = new BlockPos(-3, GROUND_Y, -3);
    private static final BlockPos STORAGE = new BlockPos(-5, GROUND_Y, -3);

    private static final int STORED = 2000;
    private static final int FIERY_NEEDED = 500;

    private static final double MOUTH_Y = CORE.getY() + 7 / 16D;

    @Override
    public void runTest(ClientGameTestContext context)
    {
        try (var world = context.worldBuilder().create())
        {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            server.runCommand("time set noon");
            server.runCommand("gamerule advance_time false");
            server.runCommand("gamemode creative @a");
            server.runOnServer(AthanorClientTest::buildWithoutAlembic);
            context.waitTicks(20);
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
            context.getInput().pressKey(InputConstants.KEY_F1);

            aimFrom(context, server, 0.5, GROUND_Y, -2.5, 0.5, MOUTH_Y, 0.5, 10);
            context.takeScreenshot("athanor-parts-idle");

            // The bottle goes in by a real click on the core's mouth
            server.runCommand("item replace entity @a weapon.mainhand with glass_bottle");
            context.getInput().pressKey(options -> options.keyUse);
            context.waitTicks(5);
            if (!server.computeOnServer(minecraft -> athanor(minecraft).getStack().is(Items.GLASS_BOTTLE)))
                throw new AssertionError("Expected the clicked glass bottle in the athanor");

            // One reagent clicked in, the other set
            aimFrom(context, server, 3.5, GROUND_Y, 0.5, SULFUR_CRUCIBLE.getX() + 0.5, CORE.getY() + 0.5, 0.5, 5);
            server.runCommand("item replace entity @a weapon.mainhand with sulfur");
            context.getInput().pressKey(options -> options.keyUse);
            context.waitTicks(5);
            server.runOnServer(minecraft ->
            {
                if (!crucible(minecraft, SULFUR_CRUCIBLE).getStack().is(Items.SULFUR))
                    throw new AssertionError("Expected the clicked sulfur in its crucible");
                crucible(minecraft, QUICKSILVER_CRUCIBLE).setStack(new ItemStack(VoidHeartItems.QUICKSILVER));
            });

            context.waitTicks(3 * AthanorTile.CHECK_INTERVAL);
            if (server.computeOnServer(minecraft -> athanor(minecraft).isBrewing()))
                throw new AssertionError("Expected no brew without an alembic");

            server.runOnServer(minecraft -> minecraft.overworld().setBlockAndUpdate(ALEMBIC, VoidHeartBlocks.ALEMBIC.defaultBlockState()));
            // Searing oil is locked until whoever set the base has its research
            context.waitTicks(3 * AthanorTile.CHECK_INTERVAL);
            if (server.computeOnServer(minecraft -> athanor(minecraft).isBrewing()))
                throw new AssertionError("Expected no brew before its research");
            server.runCommand("execute as @a run voidheart research grant voidheart:sulfur_rite");
            // The relay links what it sees on its next scan, then the intake draws on the next check
            try
            {
                context.waitFor(minecraft -> athanor(minecraft).isBrewing(), 200);
            }
            catch (AssertionError timedOut)
            {
                var state = server.computeOnServer(minecraft ->
                {
                    var athanor = athanor(minecraft);
                    return "layout " + athanor.getLayout() + ", recipe " + athanor.getCurrentRecipe() + ", gathered " + athanor.getGathered()
                            + ", recipes known " + VoidHeartRecipes.ATHANOR_CATEGORY.getVanillaRecipes().size()
                            + ", storage fiery " + storage(minecraft).amountOf(EssenceRegistry.FIERY)
                            + (minecraft.overworld().getBlockEntity(RELAY) instanceof RavenousRelayTile relay ? ", relay receivers " + relay.linkedReceivers() : ", no relay");
                });
                throw new AssertionError("Expected the athanor to start brewing: " + state, timedOut);
            }
            server.runOnServer(minecraft ->
            {
                int left = storage(minecraft).amountOf(EssenceRegistry.FIERY);
                if (left != STORED - FIERY_NEEDED)
                    throw new AssertionError("Expected the intake to draw " + FIERY_NEEDED + " fiery, the storage holds " + left);
            });

            aimFrom(context, server, 0.5, GROUND_Y, -2.5, 0.5, MOUTH_Y, 0.5, 10);
            context.takeScreenshot("athanor-brewing-front");
            aimFrom(context, server, 0.5, GROUND_Y + 0.6, -1.4, 0.5, MOUTH_Y, 0.5, 4);
            context.takeScreenshot(TestScreenshotOptions.of("athanor-brewing-mouth-close").withSize(1280, 720));
            aimFrom(context, server, -2.8, GROUND_Y + 2, -2.8, 0.5, CORE.getY() + 0.8, 0.5, 4);
            context.takeScreenshot("athanor-brewing-corner");
            aimFrom(context, server, 2.5, GROUND_Y + 3, 3.5, 0.5, CORE.getY() + 0.8, 0.5, 4);
            context.takeScreenshot("athanor-brewing-back");

            // Jade over the core while it brews
            context.getInput().pressKey(InputConstants.KEY_F1);
            aimFrom(context, server, 0.5, GROUND_Y, -2.5, 0.5, MOUTH_Y, 0.5, 20);
            context.takeScreenshot("athanor-brewing-jade");
            context.getInput().pressKey(InputConstants.KEY_F1);

            context.waitFor(minecraft -> !athanor(minecraft).isBrewing(), 400);
            server.runOnServer(minecraft ->
            {
                if (!athanor(minecraft).getStack().is(VoidHeartItems.SEARING_OIL))
                    throw new AssertionError("Expected searing oil in the athanor, got " + athanor(minecraft).getStack());
                if (!crucible(minecraft, SULFUR_CRUCIBLE).getStack().isEmpty() || !crucible(minecraft, QUICKSILVER_CRUCIBLE).getStack().isEmpty())
                    throw new AssertionError("Expected the brew to use both reagents");
                if (!athanor(minecraft).getGathered().isEmpty())
                    throw new AssertionError("Expected the brew to use the gathered essence, left " + athanor(minecraft).getGathered());
                if (storage(minecraft).amountOf(EssenceRegistry.FIERY) != STORED - FIERY_NEEDED)
                    throw new AssertionError("Expected no essence drawn beyond the brew's");
            });

            aimFrom(context, server, 0.5, GROUND_Y, -2.5, 0.5, MOUTH_Y, 0.5, 10);
            context.takeScreenshot("athanor-done-front");

            // Jade over the core, then the items in hand
            context.getInput().pressKey(InputConstants.KEY_F1);
            server.runCommand("item replace entity @a weapon.mainhand with voidheart:athanor");
            context.waitTicks(20);
            context.takeScreenshot("athanor-jade-and-hand");
            for (var item : new String[]{"alembic", "crucible", "aspect_intake", "salt", "quicksilver", "searing_oil"})
            {
                server.runCommand("item replace entity @a weapon.mainhand with voidheart:" + item);
                // Long enough for the hand to finish raising the new item
                context.waitTicks(15);
                context.takeScreenshot("athanor-hand-" + item);
            }

            server.runCommand("clear @a");
            server.runCommand("give @a voidheart:athanor");
            server.runCommand("give @a voidheart:alembic");
            server.runCommand("give @a voidheart:crucible");
            server.runCommand("give @a voidheart:aspect_intake");
            server.runCommand("give @a voidheart:salt");
            server.runCommand("give @a voidheart:quicksilver");
            server.runCommand("give @a voidheart:searing_oil");
            context.waitTicks(5);
            context.getInput().pressKey(options -> options.keyInventory);
            context.waitTicks(10);
            context.takeScreenshot("athanor-inventory");
            context.getInput().pressKey(InputConstants.KEY_ESCAPE);
            context.waitTicks(5);

            // Breaking the core and a filled crucible drops them with what they held
            server.runOnServer(minecraft ->
            {
                crucible(minecraft, SULFUR_CRUCIBLE).setStack(new ItemStack(Items.SULFUR));
                var level = minecraft.overworld();
                level.destroyBlock(CORE, true);
                level.destroyBlock(SULFUR_CRUCIBLE, true);
            });
            context.waitTicks(5);
            server.runOnServer(minecraft ->
            {
                var drops = minecraft.overworld().getEntitiesOfClass(ItemEntity.class, new AABB(CORE).inflate(3)).stream().map(ItemEntity::getItem).toList();
                for (var expected : new ItemStack[]{new ItemStack(VoidHeartBlocks.ATHANOR), new ItemStack(VoidHeartItems.SEARING_OIL),
                        new ItemStack(VoidHeartBlocks.CRUCIBLE), new ItemStack(Items.SULFUR)})
                {
                    if (drops.stream().noneMatch(drop -> drop.is(expected.getItem())))
                        throw new AssertionError("Expected " + expected.getItem() + " among the drops, got " + drops);
                }
            });
        }
    }

    private static void buildWithoutAlembic(MinecraftServer minecraft)
    {
        var level = minecraft.overworld();
        level.setBlockAndUpdate(INTAKE, VoidHeartBlocks.ASPECT_INTAKE.defaultBlockState());
        level.setBlockAndUpdate(CORE, VoidHeartBlocks.ATHANOR.defaultBlockState());
        level.setBlockAndUpdate(SULFUR_CRUCIBLE.below(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(QUICKSILVER_CRUCIBLE.below(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(SULFUR_CRUCIBLE, VoidHeartBlocks.CRUCIBLE.defaultBlockState());
        level.setBlockAndUpdate(QUICKSILVER_CRUCIBLE, VoidHeartBlocks.CRUCIBLE.defaultBlockState());
        level.setBlockAndUpdate(RELAY, VoidHeartBlocks.RAVENOUS_RELAY.defaultBlockState());
        level.setBlockAndUpdate(STORAGE, VoidHeartBlocks.RAVENOUS_STORAGE.defaultBlockState());
        storage(minecraft).receive(EssenceRegistry.FIERY, STORED);
    }

    /**
     * Places the player's feet there and turns their eyes toward the point.
     */
    private static void aimFrom(ClientGameTestContext context, TestServerContext server, double feetX, double feetY, double feetZ, double x, double y, double z, int settleTicks)
    {
        double dx = x - feetX;
        double dy = y - (feetY + 1.62);
        double dz = z - feetZ;
        double yaw = Math.toDegrees(Math.atan2(-dx, dz));
        double pitch = -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
        server.runCommand("tp @a " + feetX + " " + feetY + " " + feetZ + " " + yaw + " " + pitch);
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
        });
        context.waitTicks(settleTicks);
    }

    private static ServerPlayer player(MinecraftServer minecraft)
    {
        return minecraft.getPlayerList().getPlayers().getFirst();
    }

    private static AthanorTile athanor(MinecraftServer minecraft)
    {
        return (AthanorTile) minecraft.overworld().getBlockEntity(CORE);
    }

    private static AthanorTile athanor(Minecraft minecraft)
    {
        return (AthanorTile) minecraft.level.getBlockEntity(CORE);
    }

    private static CrucibleTile crucible(MinecraftServer minecraft, BlockPos pos)
    {
        return (CrucibleTile) minecraft.overworld().getBlockEntity(pos);
    }

    private static RavenousStorageTile storage(MinecraftServer minecraft)
    {
        return (RavenousStorageTile) minecraft.overworld().getBlockEntity(STORAGE);
    }
}
