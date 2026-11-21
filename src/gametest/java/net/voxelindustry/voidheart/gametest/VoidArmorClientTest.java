package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.voxelindustry.voidheart.client.screen.VoidArmorScreen;
import net.voxelindustry.voidheart.common.content.armor.ArmorBalance;
import net.voxelindustry.voidheart.common.content.armor.ArmorGridKind;
import net.voxelindustry.voidheart.common.content.armor.ArmorGrids;
import net.voxelindustry.voidheart.common.content.armor.ArmorModule;
import net.voxelindustry.voidheart.common.content.armor.VoidArmorBaseBlock;
import net.voxelindustry.voidheart.common.content.armor.VoidArmorBaseTile;
import net.voxelindustry.voidheart.common.content.armor.VoidArmorContent;
import net.voxelindustry.voidheart.common.content.armor.VoidArmorItem;
import net.voxelindustry.voidheart.common.content.armor.VoidArmorMenu;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;
import org.jspecify.annotations.Nullable;

import java.util.function.Function;

import static net.voxelindustry.voidheart.gametest.GameTestKit.expect;
import static net.voxelindustry.voidheart.gametest.GameTestKit.player;

/**
 * A frame used on an empty Void Armor Base docks there, and a sneaking use on the docked base opens its grids. Modules are set with real clicks in the helm, core and limbs,
 * refused in the wrong grid, on the apparatus socket and away from the night vision a lens rests on, then a weapon and a Shielding Apparatus are shift
 * clicked into a rack and the socket. Everything lands on the frame the base holds, and a base holding a frame can't be mined in survival.
 */
public class VoidArmorClientTest implements FabricClientGameTest
{
    private static final int      GROUND_Y = -60;
    private static final BlockPos BASE     = new BlockPos(0, GROUND_Y, 2);

    private static final int BATTERY_SLOT   = 9;
    private static final int NIGHT_SLOT     = 10;
    private static final int SPECTRAL_SLOT  = 11;
    private static final int POWER_SLOT     = 12;
    private static final int SWORD_SLOT     = 13;
    private static final int APPARATUS_SLOT = 14;

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
            // An arch facing the player, who stands north of it
            server.runOnServer(minecraft -> VoidArmorBaseBlock.build(minecraft.overworld(), BASE, Direction.NORTH));
            // Looking down at the top of the base two blocks ahead, from the eyes 1.62 above the feet
            double pitch = -Math.toDegrees(Math.atan2(GROUND_Y + 0.25 - (GROUND_Y + 1.62), 2));
            server.runCommand("tp @a 0.5 " + GROUND_Y + " 0.5 0 " + pitch);
            context.waitTicks(10);
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));

            dock(context, server);
            setModules(context, server);
            fillRacks(context, server);
            context.getInput().pressKey(InputConstants.KEY_ESCAPE);
            context.waitTicks(5);
            cantMineWhileDocked(context, server);
        }
    }

    private static void dock(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            var inventory = player(minecraft).getInventory();
            inventory.setItem(0, VoidArmorItem.full(VoidArmorContent.VOID_ARMOR));
            inventory.setSelectedSlot(0);
            inventory.setItem(BATTERY_SLOT, module(ArmorModule.BATTERY_CELL, 2));
            inventory.setItem(NIGHT_SLOT, module(ArmorModule.NIGHT_VISION, 1));
            inventory.setItem(SPECTRAL_SLOT, module(ArmorModule.SPECTRAL_LENS, 1));
            inventory.setItem(POWER_SLOT, module(ArmorModule.EXOSKELETON_POWER, 1));
            inventory.setItem(SWORD_SLOT, new ItemStack(Items.DIAMOND_SWORD));
            inventory.setItem(APPARATUS_SLOT, new ItemStack(VoidHeartItems.LESSER_SHIELDING_APPARATUS));
        });
        context.waitTicks(5);
        context.takeScreenshot("void-armor-base-empty");

        context.getInput().pressKey(options -> options.keyUse);
        context.waitTicks(10);
        boolean docked = server.computeOnServer(minecraft ->
                minecraft.overworld().getBlockEntity(BASE) instanceof VoidArmorBaseTile base && base.hasFrame()
                && minecraft.overworld().getBlockState(BASE).getValue(VoidArmorBaseBlock.DOCKED)
                && player(minecraft).getInventory().getItem(0).isEmpty());
        expect(docked, "Expected the frame used on the empty base to dock there and leave the hand");
        context.takeScreenshot("void-armor-base-docked");

        // A use on the frame steps into it; a use on the plinth opens its grids
        server.runOnServer(minecraft -> player(minecraft).getInventory().setSelectedSlot(1));
        context.waitTicks(3);
        context.getInput().pressKey(options -> options.keyUse);
        context.waitTicks(10);
        boolean open = context.computeOnClient(minecraft -> minecraft.gui.screen() instanceof VoidArmorScreen);
        String state = server.computeOnServer(minecraft -> "wearing " + net.voxelindustry.voidheart.common.content.armor.ArmorWear.wearing(player(minecraft))
                + ", docked " + (minecraft.overworld().getBlockEntity(BASE) instanceof VoidArmorBaseTile base && base.hasFrame())
                + ", screen " + player(minecraft).containerMenu);
        expect(open, "Expected a use on the docked arch's plinth to open the frame's grids: " + state);
        context.getInput().setCursorPos(10, 10);
        context.waitTicks(2);
        context.takeScreenshot("void-armor-screen-empty");
    }

    private static void setModules(ClientGameTestContext context, TestServerContext server)
    {
        double scale = context.computeOnClient(minecraft -> minecraft.getWindow().getGuiScale());

        // Night vision is refused in the core, then set in the helm's top left
        click(context, scale, screen -> screen.slotCenter(menuSlot(NIGHT_SLOT)));
        aim(context, scale, screen -> screen.cellCenter(ArmorGridKind.CORE, 0, 0));
        context.takeScreenshot("void-armor-screen-wrong-grid");
        click(context, scale, screen -> screen.cellCenter(ArmorGridKind.CORE, 0, 0));
        context.waitTicks(5);
        expect(grids(server).core().isEmpty() && carriedCount(context) == 1, "Expected night vision refused in the core and kept on the cursor");
        click(context, scale, screen -> screen.cellCenter(ArmorGridKind.HELM, 0, 0));
        context.waitTicks(5);
        expectAt(server, ArmorGridKind.HELM, 0, 0, ArmorModule.NIGHT_VISION, "night vision clicked into the helm");

        // A lens away from the night vision is refused, then set right under it
        click(context, scale, screen -> screen.slotCenter(menuSlot(SPECTRAL_SLOT)));
        context.getInput().pressKey(InputConstants.KEY_R);
        context.getInput().pressKey(InputConstants.KEY_R);
        context.waitTicks(2);
        aim(context, scale, screen -> screen.cellCenter(ArmorGridKind.HELM, 1, 1));
        context.takeScreenshot("void-armor-screen-needs-support");
        click(context, scale, screen -> screen.cellCenter(ArmorGridKind.HELM, 1, 1));
        context.waitTicks(5);
        expect(grids(server).at(ArmorGridKind.HELM, 2, 2) == null && carriedCount(context) == 1, "Expected a lens touching the night vision only at a corner refused");
        context.getInput().pressKey(InputConstants.KEY_R);
        context.getInput().pressKey(InputConstants.KEY_R);
        context.waitTicks(2);
        click(context, scale, screen -> screen.cellCenter(ArmorGridKind.HELM, 0, 1));
        context.waitTicks(5);
        expectAt(server, ArmorGridKind.HELM, 0, 1, ArmorModule.SPECTRAL_LENS, "a spectral lens set against the night vision");

        // A battery over the socket is refused, then two go into the core's corners
        click(context, scale, screen -> screen.slotCenter(menuSlot(BATTERY_SLOT)));
        aim(context, scale, screen -> screen.cellCenter(ArmorGridKind.CORE, 1, 1));
        context.takeScreenshot("void-armor-screen-socket");
        click(context, scale, screen -> screen.cellCenter(ArmorGridKind.CORE, 1, 1));
        context.waitTicks(5);
        expect(grids(server).core().isEmpty() && carriedCount(context) == 2, "Expected a battery over the apparatus socket refused");
        click(context, scale, screen -> screen.cellCenter(ArmorGridKind.CORE, 0, 0));
        click(context, scale, screen -> screen.cellCenter(ArmorGridKind.CORE, 3, 3));
        context.waitTicks(5);
        expectAt(server, ArmorGridKind.CORE, 0, 0, ArmorModule.BATTERY_CELL, "a battery in the core's top left corner");
        expectAt(server, ArmorGridKind.CORE, 3, 3, ArmorModule.BATTERY_CELL, "a battery in the core's bottom right corner");

        // Exoskeleton power covers its middle cell with the click, so it lands one row up
        click(context, scale, screen -> screen.slotCenter(menuSlot(POWER_SLOT)));
        click(context, scale, screen -> screen.cellCenter(ArmorGridKind.LIMBS, 0, 1));
        context.waitTicks(5);
        expectAt(server, ArmorGridKind.LIMBS, 0, 0, ArmorModule.EXOSKELETON_POWER, "exoskeleton power in the limbs");

        // A piece taken back with shift goes straight into the inventory, and comes back with a shift click into the first spot it fits
        click(context, scale, screen -> screen.cellCenter(ArmorGridKind.CORE, 3, 3), true);
        context.waitTicks(5);
        expect(grids(server).core().size() == 1 && count(server, BATTERY_SLOT) == 1, "Expected a shift clicked battery back in its slot");
        click(context, scale, screen -> screen.slotCenter(menuSlot(BATTERY_SLOT)), true);
        context.waitTicks(5);
        expect(grids(server).core().size() == 2 && count(server, BATTERY_SLOT) == 0, "Expected a shift clicked battery set in the core");

        var stats = grids(server).stats();
        expect(stats.batteries() == 2 && stats.nightVision() && stats.spectralLens() && stats.exoskeleton(), "Expected every module working, got " + stats);
    }

    private static void fillRacks(ClientGameTestContext context, TestServerContext server)
    {
        double scale = context.computeOnClient(minecraft -> minecraft.getWindow().getGuiScale());
        click(context, scale, screen -> screen.slotCenter(menuSlot(SWORD_SLOT)), true);
        click(context, scale, screen -> screen.slotCenter(menuSlot(APPARATUS_SLOT)), true);
        context.waitTicks(5);
        var slots = server.computeOnServer(minecraft ->
        {
            var items = NonNullList.withSize(ArmorBalance.FRAME_SLOTS, ItemStack.EMPTY);
            frame(minecraft.overworld().getBlockEntity(BASE)).getOrDefault(VoidArmorContent.ARMOR_SLOTS, ItemContainerContents.EMPTY).copyInto(items);
            return items;
        });
        expect(slots.get(0).is(Items.DIAMOND_SWORD), "Expected the sword shift clicked into the first rack, got " + slots);
        expect(slots.get(ArmorBalance.SOCKET_SLOT).is(VoidHeartItems.LESSER_SHIELDING_APPARATUS), "Expected the apparatus in the socket, got " + slots);

        // A block can't go in a rack
        server.runOnServer(minecraft -> player(minecraft).getInventory().setItem(SWORD_SLOT, new ItemStack(Items.STONE)));
        context.waitTicks(3);
        click(context, scale, screen -> screen.slotCenter(menuSlot(SWORD_SLOT)));
        click(context, scale, screen -> screen.slotCenter(VoidArmorMenu.FIRST_RACK + 1));
        context.waitTicks(5);
        boolean refused = server.computeOnServer(minecraft ->
        {
            var items = NonNullList.withSize(ArmorBalance.FRAME_SLOTS, ItemStack.EMPTY);
            frame(minecraft.overworld().getBlockEntity(BASE)).getOrDefault(VoidArmorContent.ARMOR_SLOTS, ItemContainerContents.EMPTY).copyInto(items);
            return items.get(1).isEmpty();
        });
        expect(refused && carriedCount(context) == 1, "Expected stone refused by a rack");
        click(context, scale, screen -> screen.slotCenter(menuSlot(SWORD_SLOT)));

        aim(context, scale, screen -> screen.cellCenter(ArmorGridKind.CORE, 0, 0));
        context.takeScreenshot("void-armor-screen-filled");
    }

    private static void cantMineWhileDocked(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft -> player(minecraft).getInventory().setItem(1, new ItemStack(Items.NETHERITE_PICKAXE)));
        context.waitTicks(3);
        context.getInput().holdKey(options -> options.keyAttack);
        context.waitTicks(60);
        context.getInput().releaseKey(options -> options.keyAttack);
        boolean standing = server.computeOnServer(minecraft -> minecraft.overworld().getBlockState(BASE).is(VoidArmorContent.VOID_ARMOR_BASE));
        expect(standing, "Expected a base holding a frame to stay unmined in survival");
    }

    private static ItemStack frame(@Nullable Object tile)
    {
        return tile instanceof VoidArmorBaseTile base ? base.frame() : ItemStack.EMPTY;
    }

    private static ArmorGrids grids(TestServerContext server)
    {
        return server.computeOnServer(minecraft -> frame(minecraft.overworld().getBlockEntity(BASE)).getOrDefault(VoidArmorContent.ARMOR_GRIDS, ArmorGrids.EMPTY));
    }

    private static void expectAt(TestServerContext server, ArmorGridKind kind, int x, int y, ArmorModule module, String how)
    {
        var placement = grids(server).at(kind, x, y);
        expect(placement != null && placement.module() == module && placement.x() == x && placement.y() == y,
                "Expected " + how + " at " + x + "," + y + ", got " + placement);
    }

    private static ItemStack module(ArmorModule module, int count)
    {
        return new ItemStack(VoidArmorContent.MODULES.get(module), count);
    }

    /**
     * The menu slot holding an inventory index: the main rows first, then the hotbar.
     */
    private static int menuSlot(int inventoryIndex)
    {
        return inventoryIndex < 9 ? VoidArmorMenu.MAIN_SLOTS + inventoryIndex : inventoryIndex - 9;
    }

    private static int count(TestServerContext server, int inventoryIndex)
    {
        return server.computeOnServer(minecraft -> player(minecraft).getInventory().getItem(inventoryIndex).getCount());
    }

    private static int carriedCount(ClientGameTestContext context)
    {
        return context.computeOnClient(minecraft -> minecraft.player.containerMenu.getCarried().getCount());
    }

    private static void aim(ClientGameTestContext context, double scale, Function<VoidArmorScreen, int[]> point)
    {
        int[] at = context.computeOnClient(minecraft -> point.apply((VoidArmorScreen) minecraft.gui.screen()));
        context.getInput().setCursorPos(at[0] * scale, at[1] * scale);
        context.waitTicks(2);
    }

    private static void click(ClientGameTestContext context, double scale, Function<VoidArmorScreen, int[]> point)
    {
        click(context, scale, point, false);
    }

    /**
     * A shift click is sent to the screen as one event carrying the modifier, as the apparatus test does, since the test input can't hold shift.
     */
    private static void click(ClientGameTestContext context, double scale, Function<VoidArmorScreen, int[]> point, boolean shift)
    {
        aim(context, scale, point);
        if (!shift)
            context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        else
            context.runOnClient(minecraft ->
            {
                var screen = (VoidArmorScreen) minecraft.gui.screen();
                int[] at = point.apply(screen);
                var event = new MouseButtonEvent(at[0], at[1], new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, InputConstants.MOD_SHIFT));
                screen.mouseClicked(event, false);
                screen.mouseReleased(event);
            });
        context.waitTicks(2);
    }
}
