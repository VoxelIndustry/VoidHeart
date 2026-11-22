package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.voxelindustry.voidheart.common.content.altar.VoidAltarTile;
import net.voxelindustry.voidheart.common.content.beam.FormationError;
import net.voxelindustry.voidheart.common.content.essence.EssenceRegistry;
import net.voxelindustry.voidheart.common.content.essence.RavenousCollectorTile;
import net.voxelindustry.voidheart.common.content.pillar.VoidPillarTile;
import net.voxelindustry.voidheart.common.content.research.Knowledge;
import net.voxelindustry.voidheart.common.content.shatterforge.ShatterForgeLayout;
import net.voxelindustry.voidheart.common.content.shatterforge.ShatterForgeTile;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;
import net.voxelindustry.voidheart.common.setup.VoidHeartRecipes;

import java.util.List;
import java.util.UUID;

import static net.voxelindustry.voidheart.gametest.GameTestKit.*;

/**
 * The altar and the Shatter Forge can be worked with no hand involved: an altar rite runs in the name of an owner who isn't online, a pinned altar
 * rite never becomes a larger recipe sharing its catalyst, a pinned forge waits for essence rather than making something else, and both items of a
 * double forge output reach a chest through an Inventory Inserter.
 */
public class StationFoundationsClientTest implements FabricClientGameTest
{
    private static final int FEET = -60;
    private static final UUID ABSENT_OWNER = UUID.fromString("5a1d0c3e-0000-4000-8000-000000000001");

    private static final BlockPos ALTAR = new BlockPos(0, FEET, 40);
    private static final List<BlockPos> PILLARS = List.of(
            new BlockPos(-1, 0, 3), new BlockPos(1, 0, 3), new BlockPos(1, 0, -3), new BlockPos(-1, 0, -3),
            new BlockPos(3, 0, -1), new BlockPos(3, 0, 1), new BlockPos(-3, 0, -1), new BlockPos(-3, 0, 1));

    private static final BlockPos FORGE = new BlockPos(0, FEET, 0);
    private static final Direction TRACK = Direction.NORTH;
    private static final int TRACK_PAIRS = 2;
    private static final BlockPos SOURCE = ShatterForgeLayout.endSlot(FORGE, TRACK, TRACK_PAIRS);
    private static final BlockPos INSERTER = FORGE.south();
    private static final BlockPos CHEST = FORGE.south(2);

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

            checkAbsentOwner(context, server);
            checkPinnedAltar(context, server);
            checkPinnedForge(context, server);
            checkDoubleOutput(context, server);

            context.getInput().pressKey(InputConstants.KEY_F1);
        }
    }

    /**
     * A Void Pearl, locked behind research, waits while its owner lacks it and starts once they have it, with nobody online in their name.
     */
    private static void checkAbsentOwner(ClientGameTestContext context, TestServerContext server)
    {
        var pearl = id("altar/void_pearl");
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            level.setBlockAndUpdate(ALTAR, VoidHeartBlocks.VOID_ALTAR.defaultBlockState());
            for (var offset : PILLARS)
                level.setBlockAndUpdate(ALTAR.offset(offset), VoidHeartBlocks.VOID_PILLAR.defaultBlockState());

            expect(minecraft.getPlayerList().getPlayer(ABSENT_OWNER) == null, "the absent owner is online");
            expect(Knowledge.lockOf(minecraft.registryAccess(), pearl).isPresent(), "the Void Pearl is not locked behind research");
            expect(!Knowledge.canUse(minecraft, ABSENT_OWNER, pearl), "the absent owner already knows the Void Pearl");

            layIngredients(level, VoidHeartItems.OBSIDIAN_SHARD, VoidHeartItems.OBSIDIAN_SHARD, VoidHeartItems.OBSIDIAN_SHARD, VoidHeartItems.OBSIDIAN_SHARD,
                    VoidHeartItems.OBSIDIAN_SHARD, VoidHeartItems.OBSIDIAN_SHARD, VoidHeartItems.OBSIDIAN_SHARD, VoidHeartItems.OBSIDIAN_SHARD);
            expect(altar(minecraft).layCatalyst(ABSENT_OWNER, new ItemStack(VoidHeartItems.ENDER_SHARD), pearl), "the vacant altar refused the catalyst");
            expect(!altar(minecraft).isCrafting(), "the altar started a recipe its owner has not researched");
        });

        context.waitTicks(40);
        server.runOnServer(minecraft ->
        {
            expect(!altar(minecraft).isCrafting(), "the altar started a locked recipe while waiting");
            expect(!altar(minecraft).layCatalyst(ABSENT_OWNER, new ItemStack(VoidHeartItems.ENDER_SHARD), pearl), "the altar took a second catalyst");
            Knowledge.lockOf(minecraft.registryAccess(), pearl).ifPresent(Knowledge.of(minecraft, ABSENT_OWNER)::grant);
        });

        waitFor(context, server, 40, "the altar did not start once its absent owner had the research", minecraft -> altar(minecraft).isCrafting());
        waitFor(context, server, 2000, "the altar did not finish the Void Pearl", minecraft -> !altar(minecraft).isCrafting());
        server.runOnServer(minecraft ->
        {
            expect(altar(minecraft).getStack().is(VoidHeartItems.VOID_PEARL), "the altar made " + altar(minecraft).getStack() + ", expected a Void Pearl");
            var taken = altar(minecraft).takeResult();
            expect(taken.is(VoidHeartItems.VOID_PEARL), "took " + taken + " off the altar");
            expect(altar(minecraft).isVacant(), "the altar still holds something after its result was taken");
        });
    }

    /**
     * Three ravenous gold and two ender shards are a Beam Focus, and also hold a Ravenous Relay's two of each. Pinned to the relay, the altar makes the relay
     * and leaves the third ingot.
     */
    private static void checkPinnedAltar(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            layIngredients(minecraft.overworld(), VoidHeartItems.RAVENOUS_GOLD_INGOT, VoidHeartItems.RAVENOUS_GOLD_INGOT, VoidHeartItems.RAVENOUS_GOLD_INGOT,
                    VoidHeartItems.ENDER_SHARD, VoidHeartItems.ENDER_SHARD);
            expect(altar(minecraft).layCatalyst(ABSENT_OWNER, new ItemStack(VoidHeartBlocks.VOID_CONDUIT), id("altar/ravenous_relay")), "the altar refused the conduit");
            expect(altar(minecraft).isCrafting(), "the pinned relay did not start");
        });

        waitFor(context, server, 2000, "the altar did not finish the relay", minecraft -> !altar(minecraft).isCrafting());
        aimFrom(context, server, ALTAR.getX() + 0.5, FEET + 2, ALTAR.getZ() - 5.5, ALTAR.getX() + 0.5, FEET + 0.5, ALTAR.getZ() + 0.5, 5);
        context.takeScreenshot("station-altar-pinned-relay");

        server.runOnServer(minecraft ->
        {
            var made = altar(minecraft).takeResult();
            expect(made.is(VoidHeartBlocks.RAVENOUS_RELAY.asItem()) && made.getCount() == 2, "the pinned altar made " + made + ", expected 2 Ravenous Relays");
            var left = PILLARS.stream().map(offset -> pillar(minecraft, ALTAR.offset(offset)).getStack()).filter(stack -> !stack.isEmpty()).toList();
            expect(left.size() == 1 && left.getFirst().is(VoidHeartItems.RAVENOUS_GOLD_INGOT), "expected the third ingot left on a pillar, found " + left);
            layIngredients(minecraft.overworld());
        });
    }

    /**
     * Iron on a fiery build makes Seething Steel, or Arrogant Iron when short of essence. Pinned to the steel, the forge waits for essence and never
     * takes the iron for anything else.
     */
    private static void checkPinnedForge(ClientGameTestContext context, TestServerContext server)
    {
        var steel = id("shatterforge/seething_steel_ingot_from_iron_ingot");
        var arrogant = id("shatterforge/arrogant_iron_ingot_from_iron_ingot");
        server.runOnServer(minecraft ->
        {
            buildForge(minecraft.overworld());
            forge(minecraft).pin(steel);
        });
        context.waitTicks(25);

        server.runOnServer(minecraft ->
        {
            var runnable = forge(minecraft).runnableRecipes().stream().map(recipe -> recipe.getId()).toList();
            expect(runnable.contains(steel) && runnable.contains(arrogant), "a fiery build should be able to run both iron recipes, runs " + runnable);
            var steelRecipe = VoidHeartRecipes.SHATTER_FORGE_CATEGORY.getRecipe(steel).orElseThrow();
            expect(!forge(minecraft).canSupply(steelRecipe), "empty collectors claim to supply Seething Steel");
            expect(forge(minecraft).placeInput(new ItemStack(Items.IRON_INGOT)), "the forge refused iron on its source pillar");
            expect(!forge(minecraft).placeInput(new ItemStack(Items.IRON_INGOT)), "the forge put a second input on an occupied pillar");
        });

        context.waitTicks(60);
        server.runOnServer(minecraft ->
        {
            expect(!forge(minecraft).isCrafting(), "the pinned forge started without the essence for Seething Steel");
            expect(forge(minecraft).getFormationError() == FormationError.NOT_ENOUGH_ESSENCE, "the forge shows " + forge(minecraft).getFormationError());
            expect(pillar(minecraft, SOURCE).getStack().is(Items.IRON_INGOT), "the pinned forge let go of its iron");
        });
        aimFrom(context, server, 6.5, FEET + 2, FORGE.getZ() - 4, 0.5, FEET + 1.5, SOURCE.getZ() + 3.5, 5);
        context.takeScreenshot("station-forge-pinned-waiting");

        server.runOnServer(minecraft -> fillCollectors(minecraft.overworld()));
        waitFor(context, server, 60, "the pinned forge did not start once its collectors were full", minecraft -> forge(minecraft).isCrafting());
        waitFor(context, server, 600, "the pinned forge did not finish", minecraft -> !forge(minecraft).getStack().isEmpty());
        server.runOnServer(minecraft ->
        {
            var made = forge(minecraft).takeOutput();
            expect(made.is(VoidHeartItems.SEETHING_STEEL_INGOT), "the pinned forge made " + made + ", expected Seething Steel");
            expect(forge(minecraft).getStack().isEmpty(), "the output stayed on the forge after it was taken");
        });
    }

    /**
     * Raw iron comes out as two iron ingots, and the inserter carries both to the chest.
     */
    private static void checkDoubleOutput(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            fillCollectors(level);
            level.setBlockAndUpdate(CHEST, Blocks.CHEST.defaultBlockState());
            level.setBlockAndUpdate(INSERTER, VoidHeartBlocks.INVENTORY_INSERTER.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.SOUTH));
            forge(minecraft).pin(id("shatterforge/iron_ingot_from_raw_iron"));
            expect(forge(minecraft).placeInput(new ItemStack(Items.RAW_IRON)), "the forge refused raw iron");
        });

        waitFor(context, server, 900, "the chest never held both iron ingots", minecraft -> ironInChest(minecraft) >= 2);
        context.waitTicks(40);
        server.runOnServer(minecraft ->
        {
            expect(ironInChest(minecraft) == 2, "the chest holds " + ironInChest(minecraft) + " iron ingots, expected 2");
            expect(forge(minecraft).getStack().isEmpty(), "the inserter left " + forge(minecraft).getStack() + " on the forge");
        });
    }

    private static void layIngredients(ServerLevel level, ItemLike... ingredients)
    {
        for (int index = 0; index < PILLARS.size(); index++)
        {
            var pillar = (VoidPillarTile) level.getBlockEntity(ALTAR.offset(PILLARS.get(index)));
            pillar.setStack(index < ingredients.length ? new ItemStack(ingredients[index]) : ItemStack.EMPTY);
        }
    }

    private static void buildForge(ServerLevel level)
    {
        level.setBlockAndUpdate(FORGE, VoidHeartBlocks.SHATTER_FORGE.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_AXIS, Direction.Axis.Z));
        for (var expected : ShatterForgeLayout.expected(FORGE, TRACK, TRACK_PAIRS))
        {
            var block = switch (expected.part())
            {
                case CONDUIT -> VoidHeartBlocks.VOID_CONDUIT;
                case PILLAR -> VoidHeartBlocks.VOID_PILLAR;
                default -> VoidHeartBlocks.VOID_MONOLITH;
            };
            level.setBlockAndUpdate(expected.pos(), block.defaultBlockState());
        }
        for (var spot : ShatterForgeLayout.optionalCollectors(FORGE, TRACK, TRACK_PAIRS))
        {
            level.setBlockAndUpdate(spot, VoidHeartBlocks.RAVENOUS_COLLECTOR.defaultBlockState());
            ((RavenousCollectorTile) level.getBlockEntity(spot)).storage().attune(EssenceRegistry.FIERY);
        }
    }

    private static void fillCollectors(ServerLevel level)
    {
        for (var spot : ShatterForgeLayout.optionalCollectors(FORGE, TRACK, TRACK_PAIRS))
        {
            var collector = (RavenousCollectorTile) level.getBlockEntity(spot);
            collector.storage().fill(EssenceRegistry.FIERY, RavenousCollectorTile.CAPACITY);
            collector.setChanged();
        }
    }

    private static int ironInChest(MinecraftServer minecraft)
    {
        var chest = (Container) minecraft.overworld().getBlockEntity(CHEST);
        var count = 0;
        for (int slot = 0; slot < chest.getContainerSize(); slot++)
        {
            if (chest.getItem(slot).is(Items.IRON_INGOT))
                count += chest.getItem(slot).getCount();
        }
        return count;
    }

    private static VoidAltarTile altar(MinecraftServer minecraft)
    {
        return (VoidAltarTile) minecraft.overworld().getBlockEntity(ALTAR);
    }

    private static ShatterForgeTile forge(MinecraftServer minecraft)
    {
        return (ShatterForgeTile) minecraft.overworld().getBlockEntity(FORGE);
    }

    private static VoidPillarTile pillar(MinecraftServer minecraft, BlockPos pos)
    {
        return (VoidPillarTile) minecraft.overworld().getBlockEntity(pos);
    }
}
