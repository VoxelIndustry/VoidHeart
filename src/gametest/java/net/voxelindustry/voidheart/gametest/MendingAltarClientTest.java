package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.voxelindustry.voidheart.common.content.pillar.VoidPillarTile;
import net.voxelindustry.voidheart.common.content.repair.ExperienceSkullTile;
import net.voxelindustry.voidheart.common.content.repair.MendingAltarTile;
import net.voxelindustry.voidheart.common.content.repair.MendingRepair;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;

import static net.voxelindustry.voidheart.gametest.GameTestKit.*;

/**
 * A damaged pickaxe laid on the mending altar with a real click is mended by a diamond eaten from a pillar beside it, then by experience drawn from a
 * skull nearby, and taken back with an empty hand.
 */
public class MendingAltarClientTest implements FabricClientGameTest
{
    private static final BlockPos ALTAR  = new BlockPos(0, FLAT_FEET, 0);
    private static final BlockPos EAST   = ALTAR.east();
    private static final BlockPos WEST   = ALTAR.west();
    private static final BlockPos SKULL  = ALTAR.offset(2, 0, 2);

    private static final int DAMAGE     = 1000;
    private static final int EXPERIENCE = 1000;

    @Override
    public void runTest(ClientGameTestContext context)
    {
        try (var world = context.worldBuilder().create())
        {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            var saved = new ClientSettings(context.computeOnClient(minecraft -> minecraft.options.getCameraType()),
                    context.computeOnClient(minecraft -> minecraft.gui.hud.isHidden()));
            try
            {
                runScenes(context, server);
            }
            finally
            {
                restoreClient(context, server, saved);
            }
        }
    }

    private static void runScenes(ClientGameTestContext context, TestServerContext server)
    {
        freezeTime(server, "noon");
        server.runCommand("gamerule spawn_mobs false");
        server.runCommand("gamemode survival @a");
        server.runCommand("difficulty peaceful");
        clearArea(server, ALTAR.getX());
        context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));

        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            level.setBlockAndUpdate(ALTAR, VoidHeartBlocks.MENDING_ALTAR.defaultBlockState());
            level.setBlockAndUpdate(EAST, VoidHeartBlocks.VOID_PILLAR.defaultBlockState());
            level.setBlockAndUpdate(WEST, VoidHeartBlocks.VOID_PILLAR.defaultBlockState());
            level.setBlockAndUpdate(SKULL, VoidHeartBlocks.EXPERIENCE_SKULL.defaultBlockState());
            skull(minecraft).setExperience(EXPERIENCE);
            pillar(minecraft, EAST).setStack(new ItemStack(Items.DIAMOND));
            player(minecraft).setItemInHand(InteractionHand.MAIN_HAND, damagedPickaxe(DAMAGE));
        });

        aimAtAltar(context, server);
        rightClick(context);
        server.runOnServer(minecraft ->
        {
            expect(altar(minecraft).getTool().is(Items.DIAMOND_PICKAXE), "the right click did not lay the pickaxe on the altar");
            expect(player(minecraft).getMainHandItem().isEmpty(), "the pickaxe stayed in the hand");
            expect(altar(minecraft).isRepairing() && minecraft.overworld().getBlockState(ALTAR).getValue(BlockStateProperties.LIT),
                    "the altar did not light up to mend");
        });

        context.waitTicks(30);
        context.getInput().pressKey(InputConstants.KEY_F1);
        context.takeScreenshot("mending-altar-eating");

        var afterDiamond = DAMAGE - MendingRepair.materialRepair(Items.DIAMOND_PICKAXE.getDefaultInstance().getMaxDamage(), DAMAGE);
        waitFor(context, server, 200, () -> "the diamond did not mend the pickaxe to " + afterDiamond, minecraft ->
                altar(minecraft).getTool().getDamageValue() <= afterDiamond);
        server.runOnServer(minecraft ->
        {
            expect(pillar(minecraft, EAST).getStack().isEmpty(), "the diamond was not eaten");
            expect(skull(minecraft).getExperience() == EXPERIENCE, "experience was drawn while a repair material was waiting");
        });

        context.waitTicks(25);
        context.takeScreenshot("mending-altar-drinking");

        waitFor(context, server, 600, () -> "the skull did not finish mending the pickaxe", minecraft -> !altar(minecraft).getTool().isDamaged());
        context.waitTicks(2);
        server.runOnServer(minecraft ->
        {
            var spent = (afterDiamond + MendingRepair.DURABILITY_PER_EXPERIENCE - 1) / MendingRepair.DURABILITY_PER_EXPERIENCE;
            var left = skull(minecraft).getExperience();
            expect(left == EXPERIENCE - spent, "the skull holds " + left + ", expected " + (EXPERIENCE - spent));
            expect(!altar(minecraft).isRepairing() && !minecraft.overworld().getBlockState(ALTAR).getValue(BlockStateProperties.LIT),
                    "the altar kept mending a whole tool");
        });
        context.takeScreenshot("mending-altar-whole");
        context.getInput().pressKey(InputConstants.KEY_F1);

        rightClick(context);
        server.runOnServer(minecraft ->
        {
            expect(altar(minecraft).getTool().isEmpty(), "an empty hand did not take the pickaxe back");
            var held = player(minecraft).getMainHandItem();
            expect(held.is(Items.DIAMOND_PICKAXE) && !held.isDamaged(), "the pickaxe did not come back whole, got " + held);

            // Nothing left to mend with
            skull(minecraft).setExperience(0);
            player(minecraft).setItemInHand(InteractionHand.MAIN_HAND, damagedPickaxe(DAMAGE));
        });
        context.waitTicks(2);
        rightClick(context);
        var message = overlay(context);
        expect(message.startsWith("Nothing to mend with"), "laying a tool with nothing to mend it said \"" + message + "\"");

        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            level.destroyBlock(ALTAR, false);
            var drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(ALTAR).inflate(2), item -> item.getItem().is(Items.DIAMOND_PICKAXE));
            expect(drops.size() == 1 && drops.getFirst().getItem().getDamageValue() == DAMAGE, "breaking the altar did not drop the tool it held");
        });
        clearScene(server);
    }

    private static void aimAtAltar(ClientGameTestContext context, TestServerContext server)
    {
        aimCamera(server, ALTAR.getX() + 0.5, FLAT_FEET, ALTAR.getZ() - 2.5, ALTAR.getX() + 0.5, FLAT_FEET + 0.9, ALTAR.getZ() + 0.5);
        context.waitTicks(5);
    }

    private static ItemStack damagedPickaxe(int damage)
    {
        var pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
        pickaxe.setDamageValue(damage);
        return pickaxe;
    }

    private static MendingAltarTile altar(MinecraftServer minecraft)
    {
        if (minecraft.overworld().getBlockEntity(ALTAR) instanceof MendingAltarTile altar)
            return altar;
        throw new AssertionError("No mending altar at " + ALTAR);
    }

    private static VoidPillarTile pillar(MinecraftServer minecraft, BlockPos pos)
    {
        if (minecraft.overworld().getBlockEntity(pos) instanceof VoidPillarTile pillar)
            return pillar;
        throw new AssertionError("No pillar at " + pos);
    }

    private static ExperienceSkullTile skull(MinecraftServer minecraft)
    {
        if (minecraft.overworld().getBlockEntity(SKULL) instanceof ExperienceSkullTile skull)
            return skull;
        throw new AssertionError("No experience skull at " + SKULL);
    }
}
