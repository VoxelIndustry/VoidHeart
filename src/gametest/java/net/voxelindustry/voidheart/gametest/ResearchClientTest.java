package net.voxelindustry.voidheart.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CrafterBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.client.screen.ResearchScreen;
import net.voxelindustry.voidheart.common.block.StateProperties;
import net.voxelindustry.voidheart.common.content.altar.VoidAltarTile;
import net.voxelindustry.voidheart.common.content.research.Knowledge;
import net.voxelindustry.voidheart.common.content.research.KnowledgePayloads;
import net.voxelindustry.voidheart.common.content.research.KnowledgeColumnBlock;
import net.voxelindustry.voidheart.common.content.research.KnowledgeRepositoryBlock;
import net.voxelindustry.voidheart.common.content.research.KnowledgeRepositoryTile;
import net.voxelindustry.voidheart.common.content.research.KnowledgeState;
import net.voxelindustry.voidheart.common.content.research.PlayerKnowledge;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;

import java.util.List;
import java.util.Optional;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Walks the whole loop: a monolith gives a tablet, the tablet makes a repository, the repository feeds and refines, and research opens up a locked recipe.
 */
public class ResearchClientTest implements FabricClientGameTest
{
    private static final int GROUND = -61;

    private static final BlockPos REPOSITORY = new BlockPos(0, GROUND + 1, 3);
    private static final BlockPos ALTAR = new BlockPos(0, GROUND + 1, 12);
    private static final List<BlockPos> ALTAR_PILLARS = List.of(
            new BlockPos(-1, 0, 3), new BlockPos(1, 0, 3), new BlockPos(1, 0, -3), new BlockPos(-1, 0, -3),
            new BlockPos(3, 0, -1), new BlockPos(3, 0, 1), new BlockPos(-3, 0, -1), new BlockPos(-3, 0, 1));

    private static final Identifier ALTAR_RITE = Identifier.fromNamespaceAndPath(MODID, "altar_rite");
    private static final Identifier VOID_FLESH = Identifier.fromNamespaceAndPath(MODID, "void_flesh");
    private static final Identifier PATIENT_STUDY = Identifier.fromNamespaceAndPath(MODID, "patient_study");

    @Override
    public void runTest(ClientGameTestContext context)
    {
        try (var world = context.worldBuilder().create())
        {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            server.runCommand("time set noon");
            server.runCommand("gamerule advance_time false");
            server.runCommand("gamemode survival @a");
            server.runCommand("tp @a 0.5 " + (GROUND + 1) + " 0.5 0 10");
            context.waitTicks(5);

            server.runOnServer(ResearchClientTest::monolithDropsTablets);
            context.waitTicks(5);
            server.runOnServer(ResearchClientTest::monolithDropsFewerTablets);
            server.runOnServer(ResearchClientTest::lecternWithBookResistsTablet);
            server.runOnServer(ResearchClientTest::tabletCorruptsLectern);
            server.runOnServer(ResearchClientTest::craftingWaitsForResearch);
            server.runOnServer(ResearchClientTest::crafterNeverKnowsLockedRecipes);
            server.runOnServer(ResearchClientTest::feedingDiminishes);

            altarWaitsForResearch(context, server);
            server.runCommand("tp @a 0.5 " + (GROUND + 1) + " 0.5 0 10");
            context.waitTicks(5);

            server.runOnServer(minecraft -> player(minecraft).setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(VoidHeartItems.KNOWLEDGE_TABLET)));
            context.waitTicks(10);
            context.takeScreenshot("research-repository");
            server.runOnServer(minecraft -> player(minecraft).setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY));

            var before = server.computeOnServer(minecraft -> data(minecraft).getKnowledge());
            server.runOnServer(minecraft -> data(minecraft).addKnowledge(-before));
            context.waitTicks(100);
            server.runOnServer(minecraft ->
            {
                double gathered = data(minecraft).getKnowledge();
                if (gathered < 0.4 || gathered > 0.7)
                    throw new AssertionError("Expected 5s of passive knowledge at 0.1/s, got " + gathered);
            });

            server.runOnServer(minecraft ->
            {
                var player = player(minecraft);
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                use(player, REPOSITORY);
                data(minecraft).addKnowledge(25);
            });
            context.waitFor(client -> client.gui.screen() instanceof ResearchScreen);
            context.waitTicks(30);
            context.takeScreenshot("research-screen-start");

            var snapshot = context.computeOnClient(client -> ResearchScreen.snapshot);
            if (snapshot.knowledge() < 25 || snapshot.cap() != 30 || Math.abs(snapshot.nextInsightCost() - 10) > 1E-6)
                throw new AssertionError("Unexpected first snapshot: " + snapshot);

            send(context, KnowledgePayloads.Action.Kind.BUY_INSIGHT, Optional.empty());
            send(context, KnowledgePayloads.Action.Kind.REVEAL, Optional.of(PATIENT_STUDY));
            send(context, KnowledgePayloads.Action.Kind.REVEAL, Optional.of(Identifier.fromNamespaceAndPath(MODID, "archive_expansion")));
            context.waitTicks(10);

            var bought = context.computeOnClient(client -> ResearchScreen.snapshot);
            if (bought.insights() != 1 || !bought.revealed().contains(Identifier.fromNamespaceAndPath(MODID, "archive_expansion")) || bought.revealed().contains(PATIENT_STUDY))
                throw new AssertionError("Only the root should have been revealed, the other one is not connected yet: " + bought);
            if (Math.abs(bought.nextInsightCost() - 12) > 1E-6)
                throw new AssertionError("Expected the next insight 20% pricier, got " + bought.nextInsightCost());

            send(context, KnowledgePayloads.Action.Kind.TOGGLE, Optional.of(Identifier.fromNamespaceAndPath(MODID, "archive_expansion")));
            context.waitFor(client -> ResearchScreen.snapshot.completed().contains(Identifier.fromNamespaceAndPath(MODID, "archive_expansion")), 400 * 20);
            context.waitTicks(30);

            if (context.computeOnClient(client -> ResearchScreen.snapshot.cap()) != 30)
                throw new AssertionError("Expected the finished archive to do nothing without columns");
            server.runOnServer(minecraft -> player(minecraft).level().setBlockAndUpdate(REPOSITORY.offset(3, 0, 0), VoidHeartBlocks.KNOWLEDGE_COLUMN.defaultBlockState()
                    .setValue(KnowledgeColumnBlock.NORTH_EAST, true).setValue(KnowledgeColumnBlock.SOUTH_WEST, true)));
            context.waitFor(client -> ResearchScreen.snapshot.columns().count() == 3, 100);
            context.waitTicks(30);
            if (context.computeOnClient(client -> ResearchScreen.snapshot.cap()) != 150)
                throw new AssertionError("Expected three columns to raise the cap to 150, got " + context.computeOnClient(client -> ResearchScreen.snapshot.cap()));
            context.takeScreenshot("research-screen-researched");

            context.runOnClient(client -> client.player.closeContainer());
            context.waitFor(client -> client.gui.screen() == null);

            server.runOnServer(ResearchClientTest::repositoryOutlivesItsBlock);
            server.runOnServer(ResearchClientTest::knowledgeSurvivesSaving);
        }
    }

    private static void send(ClientGameTestContext context, KnowledgePayloads.Action.Kind kind, Optional<Identifier> research)
    {
        context.runOnClient(client -> ClientPlayNetworking.send(new KnowledgePayloads.Action(kind, research)));
    }

    private static ServerPlayer player(MinecraftServer minecraft)
    {
        return minecraft.getPlayerList().getPlayers().getFirst();
    }

    private static PlayerKnowledge data(MinecraftServer minecraft)
    {
        return Knowledge.of(minecraft, player(minecraft).getUUID());
    }

    private static void use(ServerPlayer player, BlockPos pos)
    {
        var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        player.gameMode.useItemOn(player, player.level(), player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
    }

    private static double tabletRate(ServerPlayer player)
    {
        var level = player.level();
        var state = VoidHeartBlocks.VOID_MONOLITH.defaultBlockState().setValue(StateProperties.BROKEN, true);
        var tool = new ItemStack(Items.DIAMOND_PICKAXE);

        int rolls = 4000;
        int tablets = 0;
        for (int roll = 0; roll < rolls; roll++)
        {
            var drops = Block.getDrops(state, level, BlockPos.ZERO, null, player, tool);
            if (drops.stream().noneMatch(stack -> stack.is(VoidHeartBlocks.VOIDSTONE.asItem())))
                throw new AssertionError("A broken monolith should still drop voidstone: " + drops);
            if (drops.stream().anyMatch(stack -> stack.is(VoidHeartItems.KNOWLEDGE_TABLET)))
                tablets++;
        }
        return tablets / (double) rolls;
    }

    private static void monolithDropsTablets(MinecraftServer minecraft)
    {
        var player = player(minecraft);

        double first = tabletRate(player);
        if (first < 0.25 || first > 0.35)
            throw new AssertionError("Expected about 30% before the first tablet, got " + first);

        var whole = Block.getDrops(VoidHeartBlocks.VOID_MONOLITH.defaultBlockState(), player.level(), BlockPos.ZERO, null, player, new ItemStack(Items.DIAMOND_PICKAXE));
        if (whole.size() != 1 || !whole.getFirst().is(VoidHeartBlocks.VOID_MONOLITH.asItem()))
            throw new AssertionError("A monolith that is not broken should only drop itself: " + whole);

        player.getInventory().add(new ItemStack(VoidHeartItems.KNOWLEDGE_TABLET));
    }

    private static void monolithDropsFewerTablets(MinecraftServer minecraft)
    {
        var player = player(minecraft);
        var advancement = minecraft.getAdvancements().get(Identifier.fromNamespaceAndPath(MODID, "knowledge_tablet"));
        if (!player.getAdvancements().getOrStartProgress(advancement).isDone())
            throw new AssertionError("Holding a tablet should earn its advancement");

        double later = tabletRate(player);
        if (later < 0.03 || later > 0.07)
            throw new AssertionError("Expected about 5% after the first tablet, got " + later);

        player.getInventory().clearContent();
    }

    private static void lecternWithBookResistsTablet(MinecraftServer minecraft)
    {
        var player = player(minecraft);
        var level = player.level();

        level.setBlockAndUpdate(REPOSITORY, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.HAS_BOOK, true));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(VoidHeartItems.KNOWLEDGE_TABLET, 2));
        use(player, REPOSITORY);
        player.closeContainer();

        if (!level.getBlockState(REPOSITORY).is(Blocks.LECTERN))
            throw new AssertionError("A lectern holding a book should stay a lectern, got " + level.getBlockState(REPOSITORY));
        if (player.getMainHandItem().getCount() != 2)
            throw new AssertionError("The tablet should not be used up on a lectern holding a book");
    }

    private static void tabletCorruptsLectern(MinecraftServer minecraft)
    {
        var player = player(minecraft);
        var level = player.level();

        level.setBlockAndUpdate(REPOSITORY, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, Direction.EAST));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(VoidHeartItems.KNOWLEDGE_TABLET, 2));
        use(player, REPOSITORY);

        if (!level.getBlockState(REPOSITORY).is(VoidHeartBlocks.KNOWLEDGE_REPOSITORY))
            throw new AssertionError("The tablet should turn the lectern into a repository, got " + level.getBlockState(REPOSITORY));
        if (level.getBlockState(REPOSITORY).getValue(KnowledgeRepositoryBlock.FACING) != Direction.EAST)
            throw new AssertionError("The repository should keep the way the lectern faced");
        if (player.getMainHandItem().getCount() != 1)
            throw new AssertionError("The tablet should be used up, " + player.getMainHandItem().getCount() + " left of 2");
        if (!(level.getBlockEntity(REPOSITORY) instanceof KnowledgeRepositoryTile tile) || tile.getOwner().filter(player.getUUID()::equals).isEmpty())
            throw new AssertionError("The repository should answer to who placed the tablet");
        if (data(minecraft).getRepository().isEmpty())
            throw new AssertionError("The player should be bound to the repository");
        if (data(minecraft).rate(Knowledge.rules(), Knowledge.tree(minecraft.registryAccess())) != (double) 0.1F)
            throw new AssertionError("A bound player should gather at the base rate");

        // The rest of the test looks at it from the north
        level.setBlockAndUpdate(REPOSITORY, level.getBlockState(REPOSITORY).setValue(KnowledgeRepositoryBlock.FACING, Direction.NORTH));
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }

    private static CraftingMenu menuFilledForAltar(ServerPlayer player)
    {
        var menu = new CraftingMenu(99, player.getInventory(), ContainerLevelAccess.create(player.level(), REPOSITORY));
        var pattern = List.of(
                VoidHeartBlocks.VOIDSTONE_BRICKS.asItem(), Items.ENDER_EYE, VoidHeartBlocks.VOIDSTONE_BRICKS.asItem(),
                Items.OBSIDIAN, Items.OBSIDIAN, Items.OBSIDIAN,
                VoidHeartBlocks.VOIDSTONE_BRICKS.asItem(), VoidHeartBlocks.VOIDSTONE_BRICKS.asItem(), VoidHeartBlocks.VOIDSTONE_BRICKS.asItem());
        for (int slot = 0; slot < pattern.size(); slot++)
            menu.getSlot(slot + 1).set(new ItemStack(pattern.get(slot)));
        return menu;
    }

    private static void craftingWaitsForResearch(MinecraftServer minecraft)
    {
        var player = player(minecraft);

        if (!menuFilledForAltar(player).getSlot(0).getItem().isEmpty())
            throw new AssertionError("The altar recipe should give nothing before its research");

        data(minecraft).grant(ALTAR_RITE);

        if (!menuFilledForAltar(player).getSlot(0).getItem().is(VoidHeartBlocks.VOID_ALTAR.asItem()))
            throw new AssertionError("The altar recipe should work once researched");
    }

    private static void crafterNeverKnowsLockedRecipes(MinecraftServer minecraft)
    {
        var level = player(minecraft).level();
        var grid = NonNullList.withSize(9, ItemStack.EMPTY);
        var pattern = List.of(
                VoidHeartBlocks.VOIDSTONE_BRICKS.asItem(), Items.ENDER_EYE, VoidHeartBlocks.VOIDSTONE_BRICKS.asItem(),
                Items.OBSIDIAN, Items.OBSIDIAN, Items.OBSIDIAN,
                VoidHeartBlocks.VOIDSTONE_BRICKS.asItem(), VoidHeartBlocks.VOIDSTONE_BRICKS.asItem(), VoidHeartBlocks.VOIDSTONE_BRICKS.asItem());
        for (int slot = 0; slot < pattern.size(); slot++)
            grid.set(slot, new ItemStack(pattern.get(slot)));

        if (CrafterBlock.getPotentialResults(level, CraftingInput.of(3, 3, grid)).isPresent())
            throw new AssertionError("A crafter should refuse the locked altar recipe");
    }

    private static void feedingDiminishes(MinecraftServer minecraft)
    {
        var player = player(minecraft);
        data(minecraft).addKnowledge(-data(minecraft).getKnowledge());

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GHAST_TEAR, 3));
        use(player, REPOSITORY);
        use(player, REPOSITORY);

        double knowledge = data(minecraft).getKnowledge();
        if (knowledge < 6.5 || knowledge > 6.6)
            throw new AssertionError("Expected 5 + 1.5 from two ghast tears, got " + knowledge);
        if (player.getMainHandItem().getCount() != 1)
            throw new AssertionError("Each use should feed a single item, " + player.getMainHandItem().getCount() + " left of 3");

        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }

    /**
     * Goes through a real click, since the client is the side that could wrongly light up a craft the server refuses.
     */
    private static void altarWaitsForResearch(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            var level = player.level();
            level.setBlockAndUpdate(ALTAR, VoidHeartBlocks.VOID_ALTAR.defaultBlockState());
            for (var offset : ALTAR_PILLARS)
                level.setBlockAndUpdate(ALTAR.offset(offset), VoidHeartBlocks.VOID_PILLAR.defaultBlockState());

            player.teleportTo(ALTAR.getX() + 0.5, ALTAR.getY(), ALTAR.getZ() - 1.5);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.ENDER_PEARL, 2));
        });
        context.waitTicks(10);

        clickAltar(context);
        context.waitTicks(10);

        server.runOnServer(minecraft ->
        {
            var level = player(minecraft).level();
            var altar = (VoidAltarTile) level.getBlockEntity(ALTAR);
            if (altar.isCrafting() || level.getBlockState(ALTAR).getValue(BlockStateProperties.LIT))
                throw new AssertionError("The altar should refuse a recipe its user has not researched");
            if (!altar.getStack().is(Items.ENDER_PEARL))
                throw new AssertionError("The refused item should stay on the altar to be taken back");
        });
        if (context.computeOnClient(ResearchClientTest::clientAltarLit))
            throw new AssertionError("The client should not light up a refused craft");

        // Takes the item back, then tries again with the research done
        clickAltar(context);
        context.waitTicks(10);
        server.runOnServer(minecraft -> data(minecraft).grant(VOID_FLESH));
        clickAltar(context);
        context.waitTicks(10);

        server.runOnServer(minecraft ->
        {
            var altar = (VoidAltarTile) player(minecraft).level().getBlockEntity(ALTAR);
            if (!altar.isCrafting())
                throw new AssertionError("The altar should accept the recipe once researched");
        });
        if (!context.computeOnClient(ResearchClientTest::clientAltarLit))
            throw new AssertionError("The client should show the craft started by the server");

        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            player.level().destroyBlock(ALTAR, false);
            player.getInventory().clearContent();
        });
    }

    private static boolean clientAltarLit(Minecraft client)
    {
        return client.level.getBlockState(ALTAR).getValue(BlockStateProperties.LIT);
    }

    private static void clickAltar(ClientGameTestContext context)
    {
        context.runOnClient(client ->
        {
            var hit = new BlockHitResult(Vec3.atCenterOf(ALTAR), Direction.UP, ALTAR, false);
            client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, hit);
        });
    }

    private static void repositoryOutlivesItsBlock(MinecraftServer minecraft)
    {
        var player = player(minecraft);
        var level = player.level();
        double knowledge = data(minecraft).getKnowledge();

        var drops = Block.getDrops(level.getBlockState(REPOSITORY), level, REPOSITORY, level.getBlockEntity(REPOSITORY), player, new ItemStack(Items.DIAMOND_PICKAXE));
        if (drops.stream().noneMatch(stack -> stack.is(Items.LECTERN)) || drops.stream().noneMatch(stack -> stack.is(VoidHeartItems.KNOWLEDGE_TABLET)))
            throw new AssertionError("A broken repository should give back its lectern and tablet: " + drops);

        level.destroyBlock(REPOSITORY, false);

        if (data(minecraft).getRepository().isPresent())
            throw new AssertionError("The player should no longer be bound to a destroyed repository");
        if (data(minecraft).getKnowledge() < knowledge)
            throw new AssertionError("Knowledge should survive the repository");
    }

    private static void knowledgeSurvivesSaving(MinecraftServer minecraft)
    {
        var state = KnowledgeState.get(minecraft);
        var saved = KnowledgeState.CODEC.encodeStart(NbtOps.INSTANCE, state).getOrThrow();
        var loaded = KnowledgeState.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow().of(player(minecraft).getUUID());

        var original = data(minecraft);
        if (!loaded.getCompleted().equals(original.getCompleted()) || loaded.getInsightsBought() != original.getInsightsBought())
            throw new AssertionError("Saved knowledge should load back the same");
    }
}
