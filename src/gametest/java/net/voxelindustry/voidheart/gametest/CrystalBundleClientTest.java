package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.locale.Language;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;

import java.util.List;

import static net.voxelindustry.voidheart.gametest.GameTestKit.*;

/**
 * Each crystal grows in three sizes of bundle: a pickaxe breaks them into shards, silk touch keeps them whole.
 */
public class CrystalBundleClientTest implements FabricClientGameTest
{
    private static final int GROUND = -60;
    private static final int ROLLS  = 60;

    private record Size(int minDrop, int maxDrop) {}

    private static final Size SMALL  = new Size(1, 1);
    private static final Size MEDIUM = new Size(1, 2);
    private static final Size LARGE  = new Size(2, 4);

    private record Bundle(Block block, Size size, Item shard, int light, int height) {}

    private static List<Bundle> bundles()
    {
        return List.of(
                new Bundle(VoidHeartBlocks.SMALL_AZURIUM_BUNDLE, SMALL, VoidHeartItems.AZURIUM_SHARD, 1, 6),
                new Bundle(VoidHeartBlocks.MEDIUM_AZURIUM_BUNDLE, MEDIUM, VoidHeartItems.AZURIUM_SHARD, 3, 10),
                new Bundle(VoidHeartBlocks.LARGE_AZURIUM_BUNDLE, LARGE, VoidHeartItems.AZURIUM_SHARD, 5, 15),
                new Bundle(VoidHeartBlocks.SMALL_RUBRUM_BUNDLE, SMALL, VoidHeartItems.RUBRUM_SHARD, 4, 5),
                new Bundle(VoidHeartBlocks.MEDIUM_RUBRUM_BUNDLE, MEDIUM, VoidHeartItems.RUBRUM_SHARD, 7, 9),
                new Bundle(VoidHeartBlocks.LARGE_RUBRUM_BUNDLE, LARGE, VoidHeartItems.RUBRUM_SHARD, 10, 14),
                new Bundle(VoidHeartBlocks.SMALL_MEMORIAM_BUNDLE, SMALL, VoidHeartItems.MEMORIAM_SHARD, 1, 6),
                new Bundle(VoidHeartBlocks.MEDIUM_MEMORIAM_BUNDLE, MEDIUM, VoidHeartItems.MEMORIAM_SHARD, 3, 10),
                new Bundle(VoidHeartBlocks.LARGE_MEMORIAM_BUNDLE, LARGE, VoidHeartItems.MEMORIAM_SHARD, 5, 14));
    }

    @Override
    public void runTest(ClientGameTestContext context)
    {
        try (var world = context.worldBuilder().create())
        {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            freezeTime(server, "noon");
            server.runCommand("gamemode survival @a");
            server.runCommand("fill -4 " + (GROUND - 1) + " -4 14 " + (GROUND - 1) + " 14 voidheart:voidstone_polished");
            server.runCommand("tp @a 5 " + GROUND + " -3");
            context.waitTicks(20);
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));

            checkTranslations(context);
            server.runOnServer(CrystalBundleClientTest::checkBundles);
            checkMinedByHandAndPickaxe(context, server);
            checkFallsWithoutSupport(context, server);

            buildGrid(server);
            context.getInput().pressKey(InputConstants.KEY_F1);
            server.runCommand("gamemode spectator @a");
            context.waitTicks(20);
            view(context, server, "crystal-grid-day", 5, GROUND + 3, -2, 5, GROUND, 6);
            view(context, server, "crystal-grid-close", 5, GROUND + 1.5, 0.2, 5, GROUND + 0.5, 3);
            view(context, server, "crystal-grid-side", -1, GROUND + 2.5, 6, 6, GROUND, 6);
            view(context, server, "crystal-facings", 5, GROUND + 4, 8, 5, GROUND + 4, 11);
            view(context, server, "crystal-facings-other-side", 8, GROUND + 6, 14, 5, GROUND + 4, 11);
            server.runCommand("time set midnight");
            context.waitTicks(10);
            view(context, server, "crystal-grid-night", 5, GROUND + 3, -2, 5, GROUND, 6);
            server.runCommand("time set noon");

            context.getInput().pressKey(InputConstants.KEY_F1);
            server.runCommand("gamemode creative @a");
            server.runOnServer(CrystalBundleClientTest::fillHotbar);
            server.runCommand("tp @a 5 " + GROUND + " -3 facing 5 " + GROUND + " 3");
            context.waitTicks(10);
            for (int slot = 0; slot < 6; slot++)
            {
                int selected = slot;
                context.runOnClient(minecraft -> minecraft.player.getInventory().setSelectedSlot(selected));
                context.waitTicks(5);
                context.takeScreenshot("crystal-in-hand-" + slot);
            }
            context.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
            context.waitTicks(5);
            context.takeScreenshot("crystal-inventory");
            context.setScreen(() -> null);
            context.waitTicks(2);
        }
    }

    private static void view(ClientGameTestContext context, TestServerContext server, String name, double x, double y, double z, double targetX, double targetY, double targetZ)
    {
        server.runCommand("tp @a " + x + " " + y + " " + z + " facing " + targetX + " " + targetY + " " + targetZ);
        context.waitTicks(10);
        context.takeScreenshot(name);
    }

    private static void checkTranslations(ClientGameTestContext context)
    {
        context.runOnClient(minecraft ->
        {
            var language = Language.getInstance();
            for (var bundle : bundles())
            {
                if (!language.has(bundle.block().getDescriptionId()))
                    throw new AssertionError("Untranslated " + bundle.block().getDescriptionId());
                if (!VoidHeartItems.CREATIVE_TAB_ITEMS.contains(bundle.block().asItem()))
                    throw new AssertionError(bundle.block() + " is missing from the creative tab");
                if (!VoidHeartItems.CREATIVE_TAB_ITEMS.contains(bundle.shard()))
                    throw new AssertionError(bundle.shard() + " is missing from the creative tab");

                var shardName = bundle.shard().getDescriptionId();
                if (!language.has(shardName))
                    throw new AssertionError("Untranslated " + shardName);
                var loreKey = shardName.replace("item.voidheart.", "voidheart.") + ".lore";
                if (!language.has(loreKey))
                    throw new AssertionError("Untranslated " + loreKey);

                var lines = new ItemStack(bundle.shard()).getTooltipLines(Item.TooltipContext.of(minecraft.level), minecraft.player, net.minecraft.world.item.TooltipFlag.NORMAL);
                if (lines.stream().noneMatch(line -> line.getString().equals(language.getOrDefault(loreKey))))
                    throw new AssertionError(shardName + " tooltip has no lore: " + lines);
            }
        });
    }

    private static void checkBundles(MinecraftServer server)
    {
        var player = player(server);
        var level = player.level();
        var pos = new BlockPos(-3, GROUND, -3);
        var pickaxe = new ItemStack(Items.WOODEN_PICKAXE);
        var silkTouch = enchanted(level, Enchantments.SILK_TOUCH, 1);
        var fortune = enchanted(level, Enchantments.FORTUNE, 3);

        for (var bundle : bundles())
        {
            var state = bundle.block().defaultBlockState();
            level.setBlockAndUpdate(pos, state);

            if (state.getValue(AmethystClusterBlock.FACING) != Direction.UP)
                throw new AssertionError(bundle.block() + " should grow upward by default");
            if (state.getLightEmission() != bundle.light())
                throw new AssertionError(bundle.block() + " light " + state.getLightEmission() + ", expected " + bundle.light());
            double height = state.getShape(level, pos, CollisionContext.empty()).max(Direction.Axis.Y);
            if (Math.abs(height - bundle.height() / 16D) > 0.001)
                throw new AssertionError(bundle.block() + " is " + height + " high, expected " + bundle.height() / 16D);
            if (!pickaxe.isCorrectToolForDrops(state) || new ItemStack(Items.STICK).isCorrectToolForDrops(state))
                throw new AssertionError(bundle.block() + " should need a pickaxe");

            int lowest = Integer.MAX_VALUE;
            int highest = 0;
            for (int roll = 0; roll < ROLLS; roll++)
            {
                int count = shards(state, level, pos, player, pickaxe, bundle.shard());
                lowest = Math.min(lowest, count);
                highest = Math.max(highest, count);
            }
            if (lowest != bundle.size().minDrop() || highest != bundle.size().maxDrop())
                throw new AssertionError(bundle.block() + " dropped " + lowest + " to " + highest + ", expected " + bundle.size().minDrop() + " to " + bundle.size().maxDrop());

            var whole = Block.getDrops(state, level, pos, null, player, silkTouch);
            if (whole.size() != 1 || !whole.getFirst().is(bundle.block().asItem()) || whole.getFirst().getCount() != 1)
                throw new AssertionError(bundle.block() + " under silk touch dropped " + whole);

            int fortuneHighest = 0;
            for (int roll = 0; roll < ROLLS; roll++)
                fortuneHighest = Math.max(fortuneHighest, shards(state, level, pos, player, fortune, bundle.shard()));
            if (fortuneHighest <= bundle.size().maxDrop())
                throw new AssertionError(bundle.block() + " is not multiplied by fortune, at most " + fortuneHighest);
        }
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
    }

    private static int shards(net.minecraft.world.level.block.state.BlockState state, ServerLevel level, BlockPos pos, ServerPlayer player, ItemStack tool, Item shard)
    {
        return Block.getDrops(state, level, pos, null, player, tool).stream().filter(stack -> stack.is(shard)).mapToInt(ItemStack::getCount).sum();
    }

    private static ItemStack enchanted(ServerLevel level, ResourceKey<Enchantment> enchantment, int enchantLevel)
    {
        var stack = new ItemStack(Items.IRON_PICKAXE);
        stack.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(enchantment), enchantLevel);
        return stack;
    }

    /**
     * Through the real survival break, where the tool in hand decides whether anything drops.
     */
    private static void checkMinedByHandAndPickaxe(ClientGameTestContext context, TestServerContext server)
    {
        var bare = new BlockPos(-1, GROUND, -3);
        var mined = new BlockPos(3, GROUND, -3);
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            var level = player.level();
            var large = VoidHeartBlocks.LARGE_AZURIUM_BUNDLE.defaultBlockState();
            level.setBlockAndUpdate(bare, large);
            level.setBlockAndUpdate(mined, large);

            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.gameMode.destroyBlock(bare);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
            player.gameMode.destroyBlock(mined);
        });
        context.waitTicks(2);
        server.runOnServer(minecraft ->
        {
            var level = player(minecraft).level();
            if (!level.getBlockState(bare).isAir() || !level.getBlockState(mined).isAir())
                throw new AssertionError("Both bundles should have been broken");
            if (!level.getEntitiesOfClass(ItemEntity.class, new AABB(bare).inflate(1.5)).isEmpty())
                throw new AssertionError("A bundle broken by hand should drop nothing");

            int count = level.getEntitiesOfClass(ItemEntity.class, new AABB(mined).inflate(1.5)).stream()
                    .filter(entity -> entity.getItem().is(VoidHeartItems.AZURIUM_SHARD))
                    .mapToInt(entity -> entity.getItem().getCount())
                    .sum();
            if (count < 2 || count > 4)
                throw new AssertionError("A mined large bundle should drop 2 to 4 shards, got " + count);

            level.getEntitiesOfClass(ItemEntity.class, new AABB(mined).inflate(8)).forEach(ItemEntity::discard);
            player(minecraft).setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        });
    }

    private static void checkFallsWithoutSupport(ClientGameTestContext context, TestServerContext server)
    {
        var support = new BlockPos(1, GROUND, -3);
        var bundle = support.above();
        server.runOnServer(minecraft ->
        {
            var level = player(minecraft).level();
            level.setBlockAndUpdate(support, Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(bundle, VoidHeartBlocks.SMALL_RUBRUM_BUNDLE.defaultBlockState());
            if (!level.getBlockState(bundle).is(VoidHeartBlocks.SMALL_RUBRUM_BUNDLE))
                throw new AssertionError("The bundle should stand on the stone");
            level.setBlockAndUpdate(support, Blocks.AIR.defaultBlockState());
        });
        context.waitTicks(2);
        server.runOnServer(minecraft ->
        {
            var level = player(minecraft).level();
            if (!level.getBlockState(bundle).isAir())
                throw new AssertionError("A bundle should break with its support, got " + level.getBlockState(bundle));
            level.getEntitiesOfClass(ItemEntity.class, new AABB(bundle).inflate(4)).forEach(ItemEntity::discard);
        });
    }

    private static void buildGrid(TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            var level = player(minecraft).level();
            var all = bundles();
            for (int crystal = 0; crystal < 3; crystal++)
                for (int size = 0; size < 3; size++)
                    level.setBlockAndUpdate(new BlockPos(3 + size * 2, GROUND, 2 + crystal * 2), all.get(crystal * 3 + size).block().defaultBlockState());

            // A cube with a large bundle on each face, to show every facing
            var center = new BlockPos(5, GROUND + 4, 11);
            level.setBlockAndUpdate(center, VoidHeartBlocks.VOIDSTONE_POLISHED.defaultBlockState());
            var large = List.of(VoidHeartBlocks.LARGE_AZURIUM_BUNDLE, VoidHeartBlocks.LARGE_RUBRUM_BUNDLE, VoidHeartBlocks.LARGE_MEMORIAM_BUNDLE);
            int index = 0;
            for (var direction : Direction.values())
            {
                var pos = center.relative(direction);
                var state = large.get(index++ % 3).defaultBlockState().setValue(AmethystClusterBlock.FACING, direction);
                if (!state.canSurvive(level, pos))
                    throw new AssertionError(state + " should hold on the cube");
                level.setBlockAndUpdate(pos, state);
            }
        });
    }

    private static void fillHotbar(MinecraftServer server)
    {
        var inventory = player(server).getInventory();
        inventory.clearContent();
        inventory.setItem(0, new ItemStack(VoidHeartItems.AZURIUM_SHARD, 16));
        inventory.setItem(1, new ItemStack(VoidHeartItems.RUBRUM_SHARD, 16));
        inventory.setItem(2, new ItemStack(VoidHeartItems.MEMORIAM_SHARD, 16));
        inventory.setItem(3, new ItemStack(VoidHeartBlocks.LARGE_AZURIUM_BUNDLE));
        inventory.setItem(4, new ItemStack(VoidHeartBlocks.LARGE_RUBRUM_BUNDLE));
        inventory.setItem(5, new ItemStack(VoidHeartBlocks.LARGE_MEMORIAM_BUNDLE));
        inventory.setItem(6, new ItemStack(VoidHeartBlocks.SMALL_AZURIUM_BUNDLE));
        inventory.setItem(7, new ItemStack(VoidHeartBlocks.SMALL_RUBRUM_BUNDLE));
        inventory.setItem(8, new ItemStack(VoidHeartBlocks.SMALL_MEMORIAM_BUNDLE));
        inventory.setItem(9, new ItemStack(VoidHeartBlocks.MEDIUM_AZURIUM_BUNDLE));
        inventory.setItem(10, new ItemStack(VoidHeartBlocks.MEDIUM_RUBRUM_BUNDLE));
        inventory.setItem(11, new ItemStack(VoidHeartBlocks.MEDIUM_MEMORIAM_BUNDLE));
    }
}
