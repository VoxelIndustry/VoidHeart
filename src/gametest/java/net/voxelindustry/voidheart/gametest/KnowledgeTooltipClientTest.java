package net.voxelindustry.voidheart.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Blocks;
import net.voxelindustry.voidheart.client.screen.ResearchScreen;
import net.voxelindustry.voidheart.common.content.research.KnowledgePayloads;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;

import java.util.List;

import static net.voxelindustry.voidheart.gametest.GameTestKit.*;

/**
 * The lore of an item tells what the next one would yield and how drained that kind of item is, without the repository being open.
 */
public class KnowledgeTooltipClientTest implements FabricClientGameTest
{
    private static final BlockPos REPOSITORY = new BlockPos(0, -60, 3);

    @Override
    public void runTest(ClientGameTestContext context)
    {
        context.runOnClient(client -> ResearchScreen.snapshot = KnowledgePayloads.Snapshot.EMPTY);

        try (var world = context.worldBuilder().create())
        {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            context.waitFor(client -> ResearchScreen.snapshot != KnowledgePayloads.Snapshot.EMPTY, 100);

            server.runCommand("gamemode survival @a");
            server.runOnServer(minecraft ->
            {
                var player = player(minecraft);
                player.level().setBlockAndUpdate(REPOSITORY, Blocks.LECTERN.defaultBlockState());
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(VoidHeartItems.KNOWLEDGE_TABLET));
                use(player);
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GHAST_TEAR, 16));
            });
            context.waitTicks(5);

            expectLore(context, Items.GHAST_TEAR, "Yields 5 knowledge", "Untouched");
            expectLore(context, Items.DIRT);
            captureLore(context, "research-lore-untouched");

            feed(context, server, 1);
            expectLore(context, Items.GHAST_TEAR, "Yields 1.5 knowledge", "Partly drained");
            captureLore(context, "research-lore-drained");

            feed(context, server, 4);
            expectLore(context, Items.GHAST_TEAR, "Yields 0.5 knowledge", "Nearly exhausted");

            feed(context, server, 3);
            expectLore(context, Items.GHAST_TEAR, "Exhausted: nothing more can be learned from it");
            captureLore(context, "research-lore-spent");
        }
    }

    private static void feed(ClientGameTestContext context, TestServerContext server, int count)
    {
        server.runOnServer(minecraft ->
        {
            for (int fed = 0; fed < count; fed++)
                use(player(minecraft));
        });
        context.waitTicks(5);
    }

    private static void expectLore(ClientGameTestContext context, Item item, String... expected)
    {
        var lines = context.computeOnClient(client -> new ItemStack(item)
                .getTooltipLines(Item.TooltipContext.of(client.level), client.player, TooltipFlag.NORMAL)
                .stream().map(Component::getString).toList());

        var lore = lines.subList(1, lines.size());
        if (!lore.equals(List.of(expected)))
            throw new AssertionError("Expected the lore of " + item + " to be " + List.of(expected) + ", got " + lore);
    }

    private static void captureLore(ClientGameTestContext context, String name)
    {
        context.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
        context.waitTicks(3);

        double scale = context.computeOnClient(client -> client.getWindow().getGuiScale());
        int x = context.computeOnClient(client -> client.getWindow().getGuiScaledWidth() / 2 - 88 + 16);
        int y = context.computeOnClient(client -> client.getWindow().getGuiScaledHeight() / 2 - 83 + 150);
        context.getInput().setCursorPos(x * scale, y * scale);
        context.waitTicks(3);
        context.takeScreenshot(name);

        context.setScreen(() -> null);
        context.waitTicks(2);
    }

    private static void use(ServerPlayer player)
    {
        useOn(player, REPOSITORY);
    }
}
