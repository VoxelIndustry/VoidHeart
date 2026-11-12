package net.voxelindustry.voidheart.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.CreativeModeTabs;
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.common.energy.storage.RubrumBalance;
import net.voxelindustry.voidheart.common.energy.storage.RubrumCellItem;
import net.voxelindustry.voidheart.common.energy.storage.RubrumContent;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;

import java.util.List;

/**
 * The mod's creative tab is listed, holds every item meant for it, and opens in the creative inventory.
 */
public class CreativeTabClientTest implements FabricClientGameTest
{
    @Override
    public void runTest(ClientGameTestContext context)
    {
        try (var world = context.worldBuilder().create())
        {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            server.runCommand("gamemode creative @a");
            context.waitTicks(20);

            context.runOnClient(minecraft ->
            {
                var player = minecraft.player;
                CreativeModeTabs.tryRebuildTabContents(player.connection.enabledFeatures(), true, player.level().registryAccess());
                if (!CreativeModeTabs.tabs().contains(VoidHeart.ITEMGROUP))
                    throw new AssertionError("The Void Heart tab isn't listed among the creative tabs");
                int shown = VoidHeart.ITEMGROUP.getDisplayItems().size();
                int expected = VoidHeartItems.CREATIVE_TAB_ITEMS.size() + VoidHeartItems.CREATIVE_TAB_VARIANTS.values().stream().mapToInt(List::size).sum();
                if (shown != expected)
                    throw new AssertionError("The Void Heart tab shows " + shown + " items out of " + expected);
                boolean filledCell = VoidHeart.ITEMGROUP.getDisplayItems().stream()
                        .anyMatch(stack -> stack.is(RubrumContent.RUBRUM_CELL) && RubrumCellItem.charge(stack) == RubrumBalance.CELL_CAPACITY);
                if (!filledCell)
                    throw new AssertionError("The Void Heart tab shows no filled rubrum cell");
            });

            context.setScreen(() -> new CreativeModeInventoryScreen(Minecraft.getInstance().player, Minecraft.getInstance().player.connection.enabledFeatures(), true));
            context.waitTicks(5);
            context.runOnClient(minecraft ->
            {
                var screen = (CreativeModeInventoryScreen) minecraft.gui.screen();
                if (!screen.setSelectedTab(VoidHeart.ITEMGROUP) && screen.getSelectedTab() != VoidHeart.ITEMGROUP)
                    throw new AssertionError("The creative inventory can't select the Void Heart tab");
            });
            context.waitTicks(5);
            context.takeScreenshot("creative-tab-voidheart");
            context.setScreen(() -> null);
        }
    }
}
