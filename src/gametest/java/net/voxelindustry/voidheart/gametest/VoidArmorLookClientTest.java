package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.voxelindustry.voidheart.client.armor.VoidArmorLook;
import net.voxelindustry.voidheart.common.content.armor.ArmorBalance;
import net.voxelindustry.voidheart.common.content.armor.ArmorGrids;
import net.voxelindustry.voidheart.common.content.armor.ArmorGrids.Placement;
import net.voxelindustry.voidheart.common.content.armor.ArmorWear;
import net.voxelindustry.voidheart.common.content.armor.VoidArmorBaseBlock;
import net.voxelindustry.voidheart.common.content.armor.VoidArmorBaseTile;
import net.voxelindustry.voidheart.common.content.armor.VoidArmorContent;
import net.voxelindustry.voidheart.common.content.armor.VoidArmorItem;
import net.voxelindustry.voidheart.common.energy.storage.RubrumContent;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;

import static net.voxelindustry.voidheart.common.content.armor.ArmorGridKind.CORE;
import static net.voxelindustry.voidheart.common.content.armor.ArmorGridKind.HELM;
import static net.voxelindustry.voidheart.common.content.armor.ArmorGridKind.LIMBS;
import static net.voxelindustry.voidheart.common.content.armor.ArmorModule.BATTERY_CELL;
import static net.voxelindustry.voidheart.common.content.armor.ArmorModule.EXOSKELETON_POWER;
import static net.voxelindustry.voidheart.common.content.armor.ArmorModule.NIGHT_VISION;
import static net.voxelindustry.voidheart.common.content.armor.ArmorModule.SPECTRAL_LENS;
import static net.voxelindustry.voidheart.gametest.GameTestKit.FLAT_FEET;
import static net.voxelindustry.voidheart.gametest.GameTestKit.expect;
import static net.voxelindustry.voidheart.gametest.GameTestKit.player;

/**
 * How the Void Armor looks. Closing around its wearer in stages, then worn from the front, back and side, by day and by night, in first person, limp, parked
 * open where it was left and docked on its base. Worn, the wearer's own body, armor and cape aren't drawn.
 */
public class VoidArmorLookClientTest implements FabricClientGameTest
{
    private static final BlockPos BASE = new BlockPos(6, FLAT_FEET, 6);

    @Override
    public void runTest(ClientGameTestContext context)
    {
        try (var world = context.worldBuilder().create())
        {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            server.runCommand("time set noon");
            server.runCommand("gamerule advance_time false");
            server.runCommand("gamerule spawn_mobs false");
            server.runCommand("gamemode survival @a");
            server.runCommand("clear @a");
            server.runCommand("tp @a 0.5 " + FLAT_FEET + " 0.5 180 10");
            context.waitTicks(10);
            context.getInput().pressKey(InputConstants.KEY_F1);

            // The wearer's own armor, which the frame hides
            server.runOnServer(minecraft ->
            {
                var player = player(minecraft);
                player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
                player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET));
            });

            closing(context, server);
            worn(context, server);
            limp(context, server);
            parkedAndDocked(context, server);

            context.getInput().pressKey(InputConstants.KEY_F1);
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
        }
    }

    private static ItemStack frame()
    {
        var grids = ArmorGrids.EMPTY
                .with(HELM, new Placement(NIGHT_VISION, 0, 0, 0))
                .with(HELM, new Placement(SPECTRAL_LENS, 0, 1, 0))
                .with(LIMBS, new Placement(EXOSKELETON_POWER, 0, 0, 0))
                .with(CORE, new Placement(BATTERY_CELL, 0, 0, 0))
                .with(CORE, new Placement(BATTERY_CELL, 3, 3, 0));
        var frame = new ItemStack(VoidArmorContent.VOID_ARMOR);
        frame.set(VoidArmorContent.ARMOR_GRIDS, grids);
        frame.set(RubrumContent.VOID_CHARGE, grids.stats().capacity());
        var slots = NonNullList.withSize(ArmorBalance.FRAME_SLOTS, ItemStack.EMPTY);
        slots.set(0, new ItemStack(Items.NETHERITE_SWORD));
        slots.set(ArmorBalance.SOCKET_SLOT, new ItemStack(VoidHeartItems.LESSER_SHIELDING_APPARATUS));
        frame.set(VoidArmorContent.ARMOR_SLOTS, ItemContainerContents.fromItems(slots));
        return frame;
    }

    /**
     * Time held still while the frame closes, stepped through the moments the brief names.
     */
    private static void closing(ClientGameTestContext context, TestServerContext server)
    {
        context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
        server.runCommand("tick freeze");
        context.waitTicks(2);
        server.runOnServer(minecraft -> ArmorWear.stepIn(player(minecraft), frame(), player(minecraft).position()));
        context.waitTicks(5);
        int at = 0;
        for (int tick : new int[] { 0, 6, 14, 20, 26, 30 })
        {
            if (tick > at)
            {
                server.runCommand("tick step " + (tick - at));
                context.waitTicks(tick - at + 3);
                at = tick;
            }
            context.takeScreenshot("void-armor-closing-" + tick);
        }
        server.runCommand("tick unfreeze");
        context.waitTicks(10);
        boolean shown = context.computeOnClient(minecraft -> VoidArmorLook.of(minecraft.player, 1).shown());
        expect(shown, "Expected the client to draw the frame on its wearer");
    }

    private static void worn(ClientGameTestContext context, TestServerContext server)
    {
        context.takeScreenshot("void-armor-worn-front");
        context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK));
        context.waitTicks(3);
        context.takeScreenshot("void-armor-worn-back");

        // A swing of the sword it holds, and the sights on at night
        context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
        server.runCommand("execute as @a run tp @s 0.5 " + FLAT_FEET + " 0.5 210 10");
        server.runCommand("time set midnight");
        context.getInput().pressKey(options -> options.keyHotbarSlots[4]);
        context.getInput().pressKey(options -> options.keyHotbarSlots[5]);
        context.waitTicks(10);
        context.takeScreenshot("void-armor-worn-night");
        // A quarter of a breath apart, so the rows of lacing are seen moving out of step
        for (int beat = 1; beat <= 3; beat++)
        {
            context.waitTicks(16);
            context.takeScreenshot("void-armor-breath-" + beat);
        }
        context.getInput().pressKey(options -> options.keyHotbarSlots[4]);
        context.getInput().pressKey(options -> options.keyHotbarSlots[5]);
        server.runCommand("time set noon");

        // A blow from the front: sparks at the joints and the plates knocked out of line
        server.runCommand("execute as @a run tp @s 0.5 " + FLAT_FEET + " 0.5 180 10");
        context.waitTicks(5);
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            var level = minecraft.overworld();
            var zombie = net.minecraft.world.entity.EntityTypes.ZOMBIE.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            zombie.setNoAi(true);
            zombie.snapTo(player.getX(), player.getY(), player.getZ() - 2, 0, 0);
            level.addFreshEntity(zombie);
            player.hurtServer(level, player.damageSources().mobAttack(zombie), 2);
            zombie.discard();
        });
        context.waitTicks(2);
        context.takeScreenshot("void-armor-hit");
        server.runOnServer(GameTestKit::heal);

        // At a run, steam puffs from the back now and then
        context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK));
        context.getInput().holdKey(options -> options.keySprint);
        context.getInput().holdKey(options -> options.keyUp);
        context.waitTicks(30);
        context.takeScreenshot("void-armor-running");
        context.getInput().releaseKey(options -> options.keyUp);
        context.getInput().releaseKey(options -> options.keySprint);
        context.waitTicks(10);
        server.runCommand("execute as @a run tp @s 0.5 " + FLAT_FEET + " 0.5 180 10");

        // The hand is hidden with the HUD, so these show it
        context.getInput().pressKey(InputConstants.KEY_F1);
        context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
        server.runCommand("execute as @a run tp @s 0.5 " + FLAT_FEET + " 0.5 180 0");
        context.waitTicks(5);
        context.takeScreenshot("void-armor-first-person");
        // Vanilla draws the arm only for an empty hand, once it has risen back into view
        server.runOnServer(minecraft -> player(minecraft).setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY));
        context.waitTicks(15);
        context.takeScreenshot("void-armor-first-person-empty-hand");
        context.getInput().pressKey(InputConstants.KEY_F1);
    }

    private static void limp(ClientGameTestContext context, TestServerContext server)
    {
        context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
        server.runCommand("execute as @a run voidheart armor charge 0");
        context.waitTicks(10);
        boolean limp = context.computeOnClient(minecraft -> VoidArmorLook.of(minecraft.player, 1).limp());
        expect(limp, "Expected the client to draw an empty frame limp");
        context.takeScreenshot("void-armor-limp");
        server.runCommand("execute as @a run voidheart armor charge 1");
        context.waitTicks(5);
    }

    private static void parkedAndDocked(ClientGameTestContext context, TestServerContext server)
    {
        // Parked where its wearer stepped out, seen from a few steps away
        server.runCommand("execute as @a run voidheart armor leave");
        context.waitTicks(3);
        context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
        server.runCommand("tp @a 0.5 " + FLAT_FEET + " -4.5 0 15");
        context.waitTicks(5);
        context.takeScreenshot("void-armor-parked");
        server.runCommand("tp @a -4.5 " + FLAT_FEET + " 0.5 -90 15");
        context.waitTicks(5);
        context.takeScreenshot("void-armor-parked-side");

        // Docked on its base, half charged
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            // Placed by someone standing north of it, so the frame faces them
            VoidArmorBaseBlock.build(level, BASE, Direction.NORTH);
            var frame = frame();
            frame.set(RubrumContent.VOID_CHARGE, VoidArmorItem.stats(frame).capacity() / 2);
            ((VoidArmorBaseTile) level.getBlockEntity(BASE)).dock(frame);
        });
        server.runCommand("tp @a 6.5 " + FLAT_FEET + " -0.5 0 8");
        context.waitTicks(10);
        context.takeScreenshot("void-armor-docked");
        server.runCommand("time set midnight");
        context.waitTicks(5);
        context.takeScreenshot("void-armor-docked-night");
        server.runCommand("time set noon");
    }
}
