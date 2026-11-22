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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.voxelindustry.voidheart.common.content.altar.VoidAltarTile;
import net.voxelindustry.voidheart.common.content.armor.VoidArmorContent;
import net.voxelindustry.voidheart.common.content.essence.EssenceRegistry;
import net.voxelindustry.voidheart.common.content.essence.RavenousStorageTile;
import net.voxelindustry.voidheart.common.content.pillar.VoidPillarTile;
import net.voxelindustry.voidheart.common.content.research.Knowledge;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;
import net.voxelindustry.voidheart.common.setup.VoidHeartRecipes;

import java.util.List;

import static net.voxelindustry.voidheart.gametest.GameTestKit.*;

/**
 * A late rite drinks its essence through the relays reaching the altar once its ingredients are eaten: it waits while the network falls short, gives
 * back what it drank when stopped, and finishes once the stores hold enough.
 */
public class AltarEssenceClientTest implements FabricClientGameTest
{
    private static final int      FEET    = -60;
    private static final BlockPos ALTAR   = new BlockPos(0, FEET, 80);
    private static final BlockPos STORAGE = ALTAR.offset(-7, 0, 0);
    private static final List<BlockPos> PILLARS = List.of(
            new BlockPos(-1, 0, 3), new BlockPos(1, 0, 3), new BlockPos(1, 0, -3), new BlockPos(-1, 0, -3),
            new BlockPos(3, 0, -1), new BlockPos(3, 0, 1), new BlockPos(-3, 0, -1), new BlockPos(-3, 0, 1));
    private static final int DRAUGHT = 6_000;
    private static final int SHORT   = DRAUGHT / 2;

    @Override
    public void runTest(ClientGameTestContext context)
    {
        try (var world = context.worldBuilder().create())
        {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            freezeTime(server, "noon");
            server.runCommand("gamemode creative @a");
            context.waitTicks(20);
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
            context.getInput().pressKey(InputConstants.KEY_F1);

            server.runOnServer(minecraft ->
            {
                var level = minecraft.overworld();
                level.setBlockAndUpdate(ALTAR, VoidHeartBlocks.VOID_ALTAR.defaultBlockState());
                for (var offset : PILLARS)
                    level.setBlockAndUpdate(ALTAR.offset(offset), VoidHeartBlocks.VOID_PILLAR.defaultBlockState());
                var frame = id("altar/void_armor");
                Knowledge.lockOf(minecraft.registryAccess(), frame).ifPresent(knowledge(minecraft)::grant);

                var recipe = VoidHeartRecipes.ALTAR_CATEGORY.getRecipe(frame).orElseThrow();
                expect(recipe.getAspects().getOrDefault(EssenceRegistry.FIERY, 0) == DRAUGHT, "the frame drinks " + recipe.getAspects());

                essenceForAltar(level, ALTAR, EssenceRegistry.FIERY);
                storage(minecraft).extract(EssenceRegistry.FIERY, RavenousStorageTile.CAPACITY - SHORT);
            });
            // Relays look for what to link every two seconds
            context.waitTicks(50);

            checkShortNetworkWaits(context, server);
            checkStoppedRiteGivesBack(context, server);
            checkFullNetworkFinishes(context, server);
            checkRefundsKeepTheirOwners(context, server);

            context.getInput().pressKey(InputConstants.KEY_F1);
        }
    }

    /**
     * Half the draught in the network: the altar eats the ingredients, drinks what it can reach and waits for the rest.
     */
    private static void checkShortNetworkWaits(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            var recipe = VoidHeartRecipes.ALTAR_CATEGORY.getRecipe(id("altar/void_armor")).orElseThrow();
            expect(!altar(minecraft).canSupply(recipe), "the altar claims a network holding " + SHORT + " can pay " + DRAUGHT);
            startFrame(minecraft);
        });

        waitFor(context, server, 2000, "the altar never began drinking", minecraft -> altar(minecraft).drinkFill() > 0.15F);
        aimFrom(context, server, ALTAR.getX() - 3.5, FEET + 1.5, ALTAR.getZ() + 2.5, ALTAR.getX() + 0.5, FEET + 1.25, ALTAR.getZ() + 0.5, 3);
        context.takeScreenshot("altar-drink-flowing");
        aimFrom(context, server, ALTAR.getX() + 3.5, FEET + 2, ALTAR.getZ() - 2.5, ALTAR.getX() + 0.5, FEET + 1.25, ALTAR.getZ() + 0.5, 3);
        context.takeScreenshot("altar-drink-flowing-opposite");

        // Frozen, the column and threads hold still while the camera steps, so anything popping between frames flickers
        server.runCommand("tick freeze");
        for (int step = 0; step < 6; step++)
        {
            aimFrom(context, server, ALTAR.getX() - 2.5 + step * 0.05, FEET + 1.2, ALTAR.getZ() + 1.5 + step * 0.08,
                    ALTAR.getX() + 0.5, FEET + 1.25, ALTAR.getZ() + 0.5, 2);
            context.takeScreenshot(TestScreenshotOptions.of("altar-drink-sweep-" + step).withSize(1280, 720));
        }
        server.runCommand("tick unfreeze");

        waitFor(context, server, 400, "the altar did not drink the whole store", minecraft -> storage(minecraft).storage().amount() == 0);
        context.waitTicks(60);
        server.runOnServer(minecraft ->
        {
            expect(altar(minecraft).isCrafting(), "the rite finished on half its draught");
            var drunk = altar(minecraft).getDrunk().getOrDefault(EssenceRegistry.FIERY, 0);
            expect(drunk == SHORT, "the altar drank " + drunk + ", expected " + SHORT);
            expect(PILLARS.stream().allMatch(offset -> pillar(minecraft, offset).getStack().isEmpty()), "the altar drank before eating every ingredient");
        });
        expectClient(context, "the client does not see the altar half full", minecraft -> Math.abs(clientAltar(minecraft).drinkFill() - 0.5F) < 0.01F);
        aimFrom(context, server, ALTAR.getX() - 3.5, FEET + 1.5, ALTAR.getZ() + 2.5, ALTAR.getX() + 0.5, FEET + 1.25, ALTAR.getZ() + 0.5, 3);
        context.takeScreenshot("altar-drink-half");
    }

    /**
     * Taking the catalyst off stops the rite: the ingredients drop and the essence drunk goes back to the store.
     */
    private static void checkStoppedRiteGivesBack(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            altar(minecraft).setStack(player(minecraft), ItemStack.EMPTY);
            expect(!altar(minecraft).isCrafting(), "the rite kept running without its catalyst");
            expect(altar(minecraft).getDrunk().isEmpty(), "the altar kept the essence of a stopped rite");
            var back = storage(minecraft).storage().amount();
            expect(back == SHORT, "the store got back " + back + ", expected " + SHORT);
        });
        server.runCommand("kill @e[type=item]");
    }

    /**
     * With the store full, the rite drinks its draught and gives the frame, taking only the draught from the store.
     */
    private static void checkFullNetworkFinishes(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            storage(minecraft).receive(EssenceRegistry.FIERY, RavenousStorageTile.CAPACITY);
            var recipe = VoidHeartRecipes.ALTAR_CATEGORY.getRecipe(id("altar/void_armor")).orElseThrow();
            expect(altar(minecraft).canSupply(recipe), "a full store does not cover the frame's draught");
            startFrame(minecraft);
        });

        waitFor(context, server, 2000, "the frame never finished", minecraft -> !altar(minecraft).isCrafting());
        server.runOnServer(minecraft ->
        {
            expect(altar(minecraft).getStack().is(VoidArmorContent.VOID_ARMOR), "the altar made " + altar(minecraft).getStack());
            var left = storage(minecraft).storage().amount();
            expect(left == RavenousStorageTile.CAPACITY - DRAUGHT, "the store holds " + left + ", expected " + (RavenousStorageTile.CAPACITY - DRAUGHT));
        });
    }

    private static final java.util.UUID SECOND_OWNER = java.util.UUID.fromString("5a1d0c3e-0000-4000-8000-0000000000b2");

    /**
     * With no store to give back to, each owner's undrunk essence goes to their own reserve: one player's rite is stopped by taking its catalyst off,
     * another's then drinks from a collector, and the altar is broken.
     */
    private static void checkRefundsKeepTheirOwners(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            altar(minecraft).takeResult();
            startFrame(minecraft);
        });
        waitFor(context, server, 2000, "the first rite never began drinking", minecraft -> altar(minecraft).drinkFill() > 0.15F);
        var first = server.computeOnServer(minecraft ->
        {
            minecraft.overworld().removeBlock(STORAGE, false);
            altar(minecraft).setStack(player(minecraft), ItemStack.EMPTY);
            var pending = altar(minecraft).pendingRefund().getOrDefault(EssenceRegistry.FIERY, 0);
            expect(pending > 0, "taking the catalyst off left no refund with no store to take it");
            return pending;
        });
        server.runCommand("kill @e[type=item]");

        // A collector, which feeds machines but takes no refund back
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            level.setBlockAndUpdate(STORAGE, VoidHeartBlocks.RAVENOUS_COLLECTOR.defaultBlockState());
            var collector = (net.voxelindustry.voidheart.common.content.essence.RavenousCollectorTile) level.getBlockEntity(STORAGE);
            collector.storage().attune(EssenceRegistry.FIERY);
            collector.storage().fill(EssenceRegistry.FIERY, net.voxelindustry.voidheart.common.content.essence.RavenousCollectorTile.CAPACITY);
            Knowledge.lockOf(minecraft.registryAccess(), id("altar/void_armor")).ifPresent(Knowledge.of(minecraft, SECOND_OWNER)::grant);
        });
        context.waitTicks(50);
        server.runOnServer(minecraft ->
        {
            lay(minecraft.overworld(), Items.NETHERITE_INGOT, Items.NETHERITE_INGOT, VoidHeartItems.SEETHING_STEEL_INGOT, VoidHeartItems.SEETHING_STEEL_INGOT,
                    VoidHeartItems.RAVENOUS_GOLD_INGOT, VoidHeartItems.RAVENOUS_GOLD_INGOT, VoidHeartItems.RUBRUM_SHARD, VoidHeartItems.RUBRUM_SHARD);
            altar(minecraft).layCatalyst(SECOND_OWNER, new ItemStack(VoidHeartItems.KERNACIAN_CORE), id("altar/void_armor"));
            expect(altar(minecraft).isCrafting(), "the second owner's frame did not start");
        });
        waitFor(context, server, 2000, "the second rite never began drinking", minecraft -> altar(minecraft).getDrunk().getOrDefault(EssenceRegistry.FIERY, 0) > 500);
        server.runOnServer(minecraft ->
        {
            expect(altar(minecraft).pendingRefund().getOrDefault(EssenceRegistry.FIERY, 0).intValue() == first.intValue(), "the first owner's refund changed while the second rite drank: " + altar(minecraft).pendingRefund() + " from " + first + ", drunk " + altar(minecraft).getDrunk() + ", crafting " + altar(minecraft).isCrafting());
            minecraft.overworld().removeBlock(STORAGE, false);
            var second = altar(minecraft).getDrunk().getOrDefault(EssenceRegistry.FIERY, 0);
            minecraft.overworld().removeBlock(ALTAR, false);

            var hoards = net.voxelindustry.voidheart.common.content.hoard.PocketHoard.get(minecraft);
            var toFirst = hoards.of(player(minecraft).getUUID()).reserve().amountOf(EssenceRegistry.FIERY);
            var toSecond = hoards.of(SECOND_OWNER).reserve().amountOf(EssenceRegistry.FIERY);
            expect(toFirst == first && toSecond == second, "the refunds went " + toFirst + " to the first owner and " + toSecond
                    + " to the second, expected " + first + " and " + second);
        });
        server.runCommand("kill @e[type=item]");
    }

    private static void startFrame(MinecraftServer minecraft)
    {
        lay(minecraft.overworld(), Items.NETHERITE_INGOT, Items.NETHERITE_INGOT, VoidHeartItems.SEETHING_STEEL_INGOT, VoidHeartItems.SEETHING_STEEL_INGOT,
                VoidHeartItems.RAVENOUS_GOLD_INGOT, VoidHeartItems.RAVENOUS_GOLD_INGOT, VoidHeartItems.RUBRUM_SHARD, VoidHeartItems.RUBRUM_SHARD);
        altar(minecraft).setStack(player(minecraft), new ItemStack(VoidHeartItems.KERNACIAN_CORE));
        expect(altar(minecraft).isCrafting(), "the frame did not start");
    }

    private static void lay(ServerLevel level, Item... items)
    {
        for (int index = 0; index < PILLARS.size(); index++)
            ((VoidPillarTile) level.getBlockEntity(ALTAR.offset(PILLARS.get(index)))).setStack(new ItemStack(items[index]));
    }

    private static VoidAltarTile altar(MinecraftServer minecraft)
    {
        return (VoidAltarTile) minecraft.overworld().getBlockEntity(ALTAR);
    }

    private static VoidAltarTile clientAltar(Minecraft minecraft)
    {
        return (VoidAltarTile) minecraft.level.getBlockEntity(ALTAR);
    }

    private static RavenousStorageTile storage(MinecraftServer minecraft)
    {
        return (RavenousStorageTile) minecraft.overworld().getBlockEntity(STORAGE);
    }

    private static VoidPillarTile pillar(MinecraftServer minecraft, BlockPos offset)
    {
        return (VoidPillarTile) minecraft.overworld().getBlockEntity(ALTAR.offset(offset));
    }
}
