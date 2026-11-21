package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.AABB;
import net.voxelindustry.voidheart.common.content.armor.ArmorBalance;
import net.voxelindustry.voidheart.common.content.armor.ArmorGrids;
import net.voxelindustry.voidheart.common.content.armor.ArmorGrids.Placement;
import net.voxelindustry.voidheart.common.content.armor.ArmorHotbar;
import net.voxelindustry.voidheart.common.content.armor.ArmorModule;
import net.voxelindustry.voidheart.common.content.armor.ArmorWear;
import net.voxelindustry.voidheart.common.content.armor.ParkedArmor;
import net.voxelindustry.voidheart.common.content.armor.VoidArmorContent;
import net.voxelindustry.voidheart.common.energy.storage.RubrumContent;

import java.util.List;

import static net.voxelindustry.voidheart.common.content.armor.ArmorGridKind.CORE;
import static net.voxelindustry.voidheart.common.content.armor.ArmorGridKind.HELM;
import static net.voxelindustry.voidheart.gametest.GameTestKit.FLAT_FEET;
import static net.voxelindustry.voidheart.gametest.GameTestKit.expect;
import static net.voxelindustry.voidheart.gametest.GameTestKit.player;
import static net.voxelindustry.voidheart.gametest.GameTestKit.waitFor;

/**
 * The Void Armor's own hotbar. Stepping in sets its racks in the first four slots and puts the wearer's hotbar and off hand aside; the keys after the racks
 * switch its sights without moving the selection, and the scroll wheel turns through the racks only. Nothing drops, swaps, or comes in from pickups or menu
 * clicks. A bow shoots from its rack with arrows from the inventory, and a loyal trident comes back to its rack. Stepping out puts the racks back on the
 * frame and the wearer's hotbar back in place, and on death the racks stay in the frame while the wearer's own things drop.
 */
public class VoidArmorHotbarClientTest implements FabricClientGameTest
{
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
            server.runCommand("gamerule immediate_respawn true");
            server.runCommand("gamemode survival @a");
            server.runCommand("clear @a");
            server.runCommand("item replace entity @a hotbar.0 with minecraft:dirt 5");
            server.runCommand("item replace entity @a hotbar.5 with minecraft:stone_sword");
            server.runCommand("item replace entity @a weapon.offhand with minecraft:shield");
            server.runCommand("item replace entity @a inventory.10 with minecraft:arrow 64");
            server.runCommand("tp @a 0.5 " + FLAT_FEET + " 0.5 0 0");
            context.waitTicks(10);
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
            // The wearer's own selection, given back when they step out
            context.getInput().pressKey(options -> options.keyHotbarSlots[5]);
            context.waitTicks(3);

            server.runOnServer(minecraft ->
            {
                var player = player(minecraft);
                ArmorWear.stepIn(player, frame(minecraft), player.position());
            });
            context.waitTicks(ArmorBalance.STEP_IN_TICKS + 5);

            racksTakeTheHotbar(server);
            keysAndWheel(context, server);
            nothingComesOrGoes(context, server);
            weaponsWorkFromRacks(context, server);
            // Nothing in the chat over the charge row
            context.runOnClient(minecraft -> minecraft.gui.hud.getChat().clearMessages(false));
            server.runCommand("execute as @a run voidheart armor charge 0.65");
            context.waitTicks(5);
            context.takeScreenshot("void-armor-hotbar");
            server.runCommand("execute as @a run voidheart armor charge 0.12");
            context.waitTicks(3);
            context.runOnClient(minecraft -> minecraft.gui.hud.getChat().clearMessages(false));
            context.waitTicks(2);
            context.takeScreenshot("void-armor-hotbar-low");
            stepOutGivesHotbarBack(context, server);
            deathKeepsRacks(context, server);
        }
    }

    private static ItemStack frame(MinecraftServer minecraft)
    {
        var trident = new ItemStack(Items.TRIDENT);
        trident.enchant(minecraft.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.LOYALTY), 3);
        var grids = ArmorGrids.EMPTY
                .with(HELM, new Placement(ArmorModule.NIGHT_VISION, 0, 0, 0))
                .with(HELM, new Placement(ArmorModule.SPECTRAL_LENS, 0, 1, 0))
                .with(CORE, new Placement(ArmorModule.BATTERY_CELL, 0, 0, 0));
        var frame = new ItemStack(VoidArmorContent.VOID_ARMOR);
        frame.set(VoidArmorContent.ARMOR_GRIDS, grids);
        frame.set(RubrumContent.VOID_CHARGE, grids.stats().capacity() * 3 / 4);
        frame.set(VoidArmorContent.ARMOR_SLOTS, ItemContainerContents.fromItems(List.of(
                new ItemStack(Items.DIAMOND_SWORD), new ItemStack(Items.BOW), trident, ItemStack.EMPTY, ItemStack.EMPTY)));
        return frame;
    }

    private static void racksTakeTheHotbar(TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            var inventory = player(minecraft).getInventory();
            expect(inventory.getItem(0).is(Items.DIAMOND_SWORD) && inventory.getItem(1).is(Items.BOW) && inventory.getItem(2).is(Items.TRIDENT)
                    && inventory.getItem(3).isEmpty(), "Expected the racks in the first four hotbar slots, got " + hotbar(minecraft));
            for (int i = ArmorHotbar.RACKS; i < 9; i++)
                expect(inventory.getItem(i).isEmpty(), "Expected the slots after the racks empty, got " + hotbar(minecraft));
            expect(inventory.getItem(ArmorHotbar.OFFHAND).isEmpty() && inventory.getSelectedSlot() == 0, "Expected the off hand empty and the first rack selected");
            var stash = player(minecraft).getAttached(ArmorHotbar.STASH);
            expect(stash != null && stash.get(0).is(Items.DIRT) && stash.get(5).is(Items.STONE_SWORD) && stash.get(9).is(Items.SHIELD),
                    "Expected the wearer's own hotbar and off hand put aside, got " + stash);
            var racks = slots(ArmorWear.worn(player(minecraft)).frame());
            expect(racks.get(0).isEmpty() && racks.get(1).isEmpty(), "Expected the worn frame's racks emptied into the hotbar, got " + racks);
        });
    }

    private static void keysAndWheel(ClientGameTestContext context, TestServerContext server)
    {
        context.getInput().pressKey(options -> options.keyHotbarSlots[1]);
        context.waitTicks(3);
        expect(selected(server) == 1, "Expected key 2 to select the second rack, got " + selected(server));

        context.getInput().pressKey(options -> options.keyHotbarSlots[4]);
        context.waitTicks(3);
        context.getInput().pressKey(options -> options.keyHotbarSlots[5]);
        context.waitTicks(3);
        context.getInput().pressKey(options -> options.keyHotbarSlots[6]);
        context.waitTicks(3);
        var on = server.computeOnServer(minecraft -> ArmorWear.worn(player(minecraft)).on());
        int clientSelected = context.computeOnClient(minecraft -> minecraft.player.getInventory().getSelectedSlot());
        expect(on.contains(ArmorModule.NIGHT_VISION) && on.contains(ArmorModule.SPECTRAL_LENS), "Expected keys 5 and 6 to switch the sights on, got " + on);
        expect(selected(server) == 1 && clientSelected == 1, "Expected the sights' keys to leave the selection on its rack, server " + selected(server)
                + ", client " + clientSelected);

        for (int turn = 0; turn < 6; turn++)
        {
            context.getInput().scroll(-1);
            context.waitTicks(2);
            int now = context.computeOnClient(minecraft -> minecraft.player.getInventory().getSelectedSlot());
            expect(now < ArmorHotbar.RACKS, "Expected the wheel to turn through the racks only, reached " + now);
        }
        context.getInput().pressKey(options -> options.keyHotbarSlots[0]);
        context.waitTicks(3);
        expect(selected(server) == 0, "Expected key 1 back on the first rack");
    }

    private static void nothingComesOrGoes(ClientGameTestContext context, TestServerContext server)
    {
        context.getInput().pressKey(options -> options.keyDrop);
        context.getInput().pressKey(options -> options.keySwapOffhand);
        context.waitTicks(5);
        server.runOnServer(minecraft ->
        {
            var inventory = player(minecraft).getInventory();
            expect(inventory.getItem(0).is(Items.DIAMOND_SWORD) && inventory.getItem(ArmorHotbar.OFFHAND).isEmpty() && items(minecraft).isEmpty(),
                    "Expected drop and swap to leave the rack's sword in hand, got " + hotbar(minecraft) + ", dropped " + items(minecraft));
        });

        server.runCommand("give @a minecraft:cobblestone 3");
        context.waitTicks(3);
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            var inventory = player.getInventory();
            int cobble = inventory.findSlotMatchingItem(new ItemStack(Items.COBBLESTONE));
            expect(cobble >= 9, "Expected a pickup to go past the hotbar, landed in " + cobble);

            var menu = player.inventoryMenu;
            menu.clicked(InventoryMenu.USE_ROW_SLOT_START, InputConstants.MOUSE_BUTTON_LEFT, ContainerInput.PICKUP, player);
            expect(menu.getCarried().isEmpty() && inventory.getItem(0).is(Items.DIAMOND_SWORD), "Expected a click unable to take a rack's weapon");
            menu.clicked(cobble, 3, ContainerInput.SWAP, player);
            menu.clicked(cobble, 0, ContainerInput.QUICK_MOVE, player);
            for (int i = ArmorHotbar.RACKS - 1; i < 9; i++)
                expect(!inventory.getItem(i).is(Items.COBBLESTONE), "Expected no menu click to put cobblestone in the hotbar, got " + hotbar(minecraft));
            menu.setCarried(ItemStack.EMPTY);
        });
    }

    private static void weaponsWorkFromRacks(ClientGameTestContext context, TestServerContext server)
    {
        server.runCommand("tp @a 0.5 " + FLAT_FEET + " 0.5 0 -10");
        context.getInput().pressKey(options -> options.keyHotbarSlots[1]);
        context.waitTicks(3);
        context.getInput().holdKey(options -> options.keyUse);
        context.waitTicks(22);
        context.getInput().releaseKey(options -> options.keyUse);
        context.waitTicks(3);
        server.runOnServer(minecraft ->
        {
            int arrows = player(minecraft).getInventory().countItem(Items.ARROW);
            var shot = minecraft.overworld().getEntitiesOfClass(Arrow.class, new AABB(player(minecraft).blockPosition()).inflate(64));
            expect(arrows == 63 && !shot.isEmpty(), "Expected the rack's bow to shoot an arrow from the inventory, arrows left " + arrows);
        });

        server.runCommand("tp @a 0.5 " + FLAT_FEET + " 0.5 0 35");
        context.getInput().pressKey(options -> options.keyHotbarSlots[2]);
        context.waitTicks(3);
        context.getInput().holdKey(options -> options.keyUse);
        context.waitTicks(15);
        context.getInput().releaseKey(options -> options.keyUse);
        context.waitTicks(3);
        boolean out = server.computeOnServer(minecraft -> player(minecraft).getInventory().getItem(2).isEmpty()
                && !minecraft.overworld().getEntitiesOfClass(ThrownTrident.class, new AABB(player(minecraft).blockPosition()).inflate(32)).isEmpty());
        expect(out, "Expected the trident thrown from its rack");
        waitFor(context, server, 200, "Expected the loyal trident back in its rack", minecraft -> player(minecraft).getInventory().getItem(2).is(Items.TRIDENT));
        int elsewhere = server.computeOnServer(minecraft ->
        {
            var inventory = player(minecraft).getInventory();
            int count = 0;
            for (int i = 0; i < inventory.getContainerSize(); i++)
                if (i != 2 && inventory.getItem(i).is(Items.TRIDENT))
                    count++;
            return count;
        });
        expect(elsewhere == 0, "Expected the trident only in its rack, found " + elsewhere + " more");
        context.getInput().pressKey(options -> options.keyHotbarSlots[0]);
        server.runCommand("tp @a 0.5 " + FLAT_FEET + " 0.5 0 10");
        context.waitTicks(5);
    }

    private static void stepOutGivesHotbarBack(ClientGameTestContext context, TestServerContext server)
    {
        context.getInput().holdKey(options -> options.keyShift);
        context.waitTicks(ArmorBalance.STEP_OUT_HOLD_TICKS + 2);
        context.getInput().releaseKey(options -> options.keyShift);
        context.waitTicks(ArmorBalance.STEP_OUT_TICKS + 3);
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            var inventory = player.getInventory();
            expect(ArmorWear.worn(player) == null, "Expected the wearer out of the frame");
            expect(inventory.getItem(0).is(Items.DIRT) && inventory.getItem(5).is(Items.STONE_SWORD) && inventory.getItem(ArmorHotbar.OFFHAND).is(Items.SHIELD),
                    "Expected the wearer's own hotbar and off hand back, got " + hotbar(minecraft));
            expect(player.getAttached(ArmorHotbar.STASH) == null, "Expected nothing left put aside");
            var parked = parked(minecraft);
            expect(parked.size() == 1, "Expected the frame parked, found " + parked.size());
            var racks = slots(parked.getFirst().frame());
            expect(racks.get(0).is(Items.DIAMOND_SWORD) && racks.get(1).is(Items.BOW) && racks.get(2).is(Items.TRIDENT), "Expected the racks back on the frame, got " + racks);
            for (int i = 0; i < inventory.getContainerSize(); i++)
                expect(!inventory.getItem(i).is(Items.DIAMOND_SWORD) && !inventory.getItem(i).is(Items.BOW), "Expected no rack weapon left in the inventory");
        });
        int server5 = server.computeOnServer(minecraft -> player(minecraft).getInventory().getSelectedSlot());
        int client5 = context.computeOnClient(minecraft -> minecraft.player.getInventory().getSelectedSlot());
        expect(server5 == 5 && client5 == 5, "Expected the slot selected before stepping in selected again, server " + server5 + ", client " + client5);
    }

    private static void deathKeepsRacks(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            var parked = parked(minecraft).getFirst();
            var frame = parked.frame();
            parked.discard();
            ArmorWear.stepIn(player, frame, player.position());
        });
        context.waitTicks(ArmorBalance.STEP_IN_TICKS + 5);
        server.runCommand("kill @a");
        context.waitTicks(30);
        server.runOnServer(minecraft ->
        {
            var dropped = items(minecraft).stream().map(ItemEntity::getItem).map(ItemStack::getItem).toList();
            expect(dropped.contains(Items.DIRT) && dropped.contains(Items.SHIELD) && dropped.contains(Items.ARROW),
                    "Expected the wearer's own things dropped on death, got " + dropped);
            for (Item rack : List.of(Items.DIAMOND_SWORD, Items.BOW, Items.TRIDENT))
                expect(!dropped.contains(rack), "Expected the racks kept in the frame on death, but " + rack + " dropped");
            var parked = parked(minecraft);
            expect(parked.size() == 1 && slots(parked.getFirst().frame()).get(0).is(Items.DIAMOND_SWORD),
                    "Expected the frame with no base beside the respawned wearer, racks kept");
        });
    }

    // Helpers

    private static int selected(TestServerContext server)
    {
        return server.computeOnServer(minecraft -> player(minecraft).getInventory().getSelectedSlot());
    }

    private static NonNullList<ItemStack> slots(ItemStack frame)
    {
        var slots = NonNullList.withSize(ArmorBalance.FRAME_SLOTS, ItemStack.EMPTY);
        frame.getOrDefault(VoidArmorContent.ARMOR_SLOTS, ItemContainerContents.EMPTY).copyInto(slots);
        return slots;
    }

    private static String hotbar(MinecraftServer minecraft)
    {
        var inventory = player(minecraft).getInventory();
        var names = new StringBuilder();
        for (int i = 0; i < 9; i++)
            names.append(i).append('=').append(inventory.getItem(i)).append(' ');
        return names.append("off=").append(inventory.getItem(ArmorHotbar.OFFHAND)).toString();
    }

    private static List<ItemEntity> items(MinecraftServer minecraft)
    {
        return minecraft.overworld().getEntitiesOfClass(ItemEntity.class, new AABB(player(minecraft).blockPosition()).inflate(64, 32, 64));
    }

    private static List<ParkedArmor> parked(MinecraftServer minecraft)
    {
        return minecraft.overworld().getEntitiesOfClass(ParkedArmor.class, new AABB(player(minecraft).blockPosition()).inflate(64, 32, 64));
    }
}
