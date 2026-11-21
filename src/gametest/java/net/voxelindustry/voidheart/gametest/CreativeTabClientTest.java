package net.voxelindustry.voidheart.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.common.content.crystal.BundleSize;
import net.voxelindustry.voidheart.common.content.crystal.CrystalBundles;
import net.voxelindustry.voidheart.common.content.crystal.CrystalHabit;
import net.voxelindustry.voidheart.common.content.crystal.CrystalKind;
import net.voxelindustry.voidheart.common.energy.storage.RubrumBalance;
import net.voxelindustry.voidheart.common.energy.storage.RubrumCellItem;
import net.voxelindustry.voidheart.common.energy.storage.RubrumContent;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;

import java.util.ArrayList;
import java.util.Arrays;
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
                checkCrystalHabits(List.copyOf(VoidHeart.ITEMGROUP.getDisplayItems()));
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

            context.runOnClient(minecraft -> ((CreativeModeInventoryScreen) minecraft.gui.screen()).setSelectedTab(CreativeModeTabs.searchTab()));
            context.waitTicks(2);
            context.getInput().typeChars("azurium");
            context.waitTicks(5);
            context.takeScreenshot("creative-tab-azurium-search");
            context.setScreen(() -> null);
        }
    }

    /**
     * Every habit a crystal can take shows right after its plain shard and each of its plain bundles, and no habit shows on a crystal that can't take it.
     */
    private static void checkCrystalHabits(List<ItemStack> shown)
    {
        for (var kind : CrystalKind.values())
        {
            var plain = new ArrayList<ItemStack>();
            plain.add(new ItemStack(CrystalBundles.shard(kind)));
            for (var size : BundleSize.values())
                plain.add(new ItemStack(CrystalBundles.bundle(kind, size)));
            var habits = Arrays.stream(CrystalHabit.values()).filter(habit -> habit.isHabit() && habit.takenBy(kind)).toList();
            for (var stack : plain)
            {
                int at = -1;
                for (int i = 0; i < shown.size(); i++)
                {
                    if (shown.get(i).is(stack.getItem()) && CrystalBundles.habitOf(shown.get(i)) == CrystalHabit.NONE)
                        at = i;
                }
                if (at < 0)
                    throw new AssertionError("The Void Heart tab shows no plain " + stack.getItem());
                for (int h = 0; h < habits.size(); h++)
                {
                    int next = at + 1 + h;
                    if (next >= shown.size() || !shown.get(next).is(stack.getItem()) || CrystalBundles.habitOf(shown.get(next)) != habits.get(h))
                        throw new AssertionError("The Void Heart tab should show " + habits.get(h) + " " + stack.getItem() + " among the habits after the plain one");
                }
            }
        }
        for (var stack : shown)
        {
            var habit = CrystalBundles.habitOf(stack);
            var kind = CrystalBundles.kindOfShard(stack) != null ? CrystalBundles.kindOfShard(stack) : CrystalBundles.bundleOf(stack) != null ? CrystalBundles.kindOfBundle(CrystalBundles.bundleOf(stack)) : null;
            if (habit != CrystalHabit.NONE && (kind == null || !habit.takenBy(kind)))
                throw new AssertionError("The Void Heart tab shows " + stack.getItem() + " with a habit it can't take: " + habit);
        }
    }
}
