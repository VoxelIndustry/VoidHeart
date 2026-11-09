package net.voxelindustry.voidheart.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.voxelindustry.voidheart.client.screen.ResearchScreen;
import net.voxelindustry.voidheart.common.content.research.Knowledge;
import net.voxelindustry.voidheart.common.content.research.KnowledgeState;
import net.voxelindustry.voidheart.common.content.research.PlayerKnowledge;
import net.voxelindustry.voidheart.common.content.research.Research;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import static net.voxelindustry.voidheart.VoidHeart.MODID;
import static net.voxelindustry.voidheart.gametest.GameTestKit.*;

/**
 * Opens a world saved before energy, whose player already owns the Armillary and the Beholder: they are granted the research those now hang from on
 * joining, told so once, and another player from that save who never got that far is left to research them.
 */
public class EnergyResearchClientTest implements FabricClientGameTest
{
    private static final int      GROUND     = -61;
    private static final BlockPos REPOSITORY = new BlockPos(0, GROUND + 1, 3);

    /** A second player of the old save, offline, who stopped at the Shatter Forge. */
    private static final UUID NEWCOMER = UUID.fromString("6a1f0c52-2e3b-4c1d-9f7e-1b2c3d4e5f60");

    private static final Identifier VOID_ENERGY    = id("void_energy");
    private static final Identifier LEY_SENSE      = id("ley_sense");
    private static final Identifier ARMILLARY      = id("armillary");
    private static final Identifier BEHOLDER       = id("beholder");
    private static final Identifier TRIA_PRIMA     = id("tria_prima");
    private static final Identifier LEY_CONVERSION = id("ley_conversion");

    private static final List<String> OLD_PLAYER   = List.of("altar_rite", "void_flesh", "shatter_forge", "armillary", "far_sight", "beholder");
    private static final List<String> OLD_NEWCOMER = List.of("altar_rite", "void_flesh", "shatter_forge");

    /** What the server told this client, heard as it arrives. */
    private static final List<String>  RECEIVED  = new CopyOnWriteArrayList<>();
    private static final AtomicBoolean LISTENING = new AtomicBoolean();

    @Override
    public void runTest(ClientGameTestContext context)
    {
        TestWorldSave save;
        UUID player;
        try (var world = context.worldBuilder().create())
        {
            save = world.getWorldSave();
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            player = server.computeOnServer(minecraft -> player(minecraft).getUUID());
            // Saved so the file exists to be replaced, as a world played before energy would have it
            server.runOnServer(minecraft -> KnowledgeState.get(minecraft).setDirty());
            context.waitTicks(5);
        }

        if (!LISTENING.getAndSet(true))
            ClientReceiveMessageEvents.GAME.register((message, overlay) -> RECEIVED.add(message.getString()));
        RECEIVED.clear();

        writeOldKnowledge(save.getSaveDirectory(), player);

        try (var world = save.open())
        {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            freezeTime(server, "noon");
            server.runCommand("gamemode survival @a");
            server.runCommand("tp @a 0.5 " + (GROUND + 1) + " 0.5 0 10");

            checkOldPlayerInherits(context, server);
            checkNewcomerStillResearches(server);
            checkResearchScreen(context, server);
        }
    }

    /**
     * Writes the knowledge file as it was before energy: research is plain ids, so an old save is the same data without the new parents.
     */
    private static void writeOldKnowledge(Path saveDirectory, UUID player)
    {
        try (var files = Files.walk(saveDirectory))
        {
            var found = files.filter(path -> path.endsWith(Path.of(MODID, "knowledge.dat"))).toList();
            expect(found.size() == 1, "Expected one saved knowledge file under " + saveDirectory + ", found " + found);

            var state = new KnowledgeState();
            OLD_PLAYER.forEach(path -> state.of(player).grant(id(path)));
            state.of(player).addInsights(2);
            OLD_NEWCOMER.forEach(path -> state.of(NEWCOMER).grant(id(path)));

            var tag = NbtIo.readCompressed(found.getFirst(), NbtAccounter.unlimitedHeap());
            tag.put("data", KnowledgeState.CODEC.encodeStart(NbtOps.INSTANCE, state).getOrThrow());
            NbtIo.writeCompressed(tag, found.getFirst());

            var written = NbtIo.readCompressed(found.getFirst(), NbtAccounter.unlimitedHeap()).getCompound("data").orElseGet(CompoundTag::new).toString();
            expect(written.contains("voidheart:armillary") && !written.contains("voidheart:void_energy"), "The old save should hold the Armillary without Void Current: " + written);
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }

    private static void checkOldPlayerInherits(ClientGameTestContext context, TestServerContext server)
    {
        waitFor(context, server, 100, "The player owning the Armillary should be granted Void Current on joining",
                minecraft -> stored(minecraft).getCompleted().contains(VOID_ENERGY));

        server.runOnServer(minecraft ->
        {
            var knowledge = Knowledge.of(minecraft, player(minecraft).getUUID());
            var completed = knowledge.getCompleted();
            expect(completed.contains(LEY_SENSE), "Owning the Beholder should grant Listening to the Ley, completed: " + completed);
            expect(completed.contains(ARMILLARY) && completed.contains(BEHOLDER), "The old research should be kept, completed: " + completed);
            expect(!completed.contains(TRIA_PRIMA), "Nothing the player never had should come with it, completed: " + completed);
            expect(knowledge.getInsights() == 3, "Insights should be untouched, got " + knowledge.getInsights());

            var tree = Knowledge.tree(minecraft.registryAccess());
            expect(knowledge.isDiscoverable(tree.get(TRIA_PRIMA)), "Tria Prima should now be in reach, after Void Current");
            expect(knowledge.isDiscoverable(tree.get(LEY_CONVERSION)), "Bleeding the Ley should be in reach with both energies known");
        });

        // Proves the client hears the server at all, so a missing note is the note's fault
        server.runOnServer(minecraft -> player(minecraft).sendSystemMessage(Component.literal("probe")));
        context.waitTicks(40);
        var expected = Component.translatable(MODID + ".research.inherited", Component.translatable(Research.nameKey(VOID_ENERGY))).getString();
        var texts = List.copyOf(RECEIVED);
        var visibility = server.computeOnServer(minecraft -> String.valueOf(player(minecraft).clientInformation().chatVisibility()))
                         + " / client " + context.computeOnClient(minecraft -> String.valueOf(minecraft.options.chatVisibility().get()));
        expect(texts.contains("probe"), "The client should hear a message from the server, received: " + texts + ", chat visibility " + visibility);
        expect(texts.stream().filter(expected::equals).count() == 1, "The player should be told once that Void Current came with what they knew, received: " + texts);
        expect(texts.stream().filter(text -> text.endsWith(": Listening to the Ley")).count() == 1, "The player should be told once about Listening to the Ley, received: " + texts);
    }

    private static void checkNewcomerStillResearches(TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            var knowledge = Knowledge.of(minecraft, NEWCOMER);
            var tree = Knowledge.tree(minecraft.registryAccess());
            expect(!knowledge.getCompleted().contains(VOID_ENERGY), "A player who never reached the Armillary should research Void Current themselves");
            expect(knowledge.isDiscoverable(tree.get(VOID_ENERGY)), "Void Current should be in reach after the Shatter Forge");
            expect(!knowledge.isDiscoverable(tree.get(ARMILLARY)), "The Armillary should wait for Void Current");
            expect(!knowledge.isDiscoverable(tree.get(TRIA_PRIMA)), "Tria Prima should wait for Void Current");
        });
    }

    /**
     * The research map, wide enough to show the whole tree, with the new research done where the Armillary and the Beholder hang from them.
     */
    private static void checkResearchScreen(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            player.level().setBlockAndUpdate(REPOSITORY.below(), Blocks.STONE.defaultBlockState());
            player.level().setBlockAndUpdate(REPOSITORY, VoidHeartBlocks.KNOWLEDGE_REPOSITORY.defaultBlockState());
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            useOn(player, REPOSITORY);
        });
        context.waitFor(client -> client.gui.screen() instanceof ResearchScreen);
        context.waitTicks(10);

        var snapshot = context.computeOnClient(client -> ResearchScreen.snapshot);
        expect(snapshot.completed().containsAll(List.of(VOID_ENERGY, LEY_SENSE, ARMILLARY, BEHOLDER)), "The screen should show both energies done, it has " + snapshot.completed());

        int width = context.computeOnClient(minecraft -> minecraft.getWindow().getWidth());
        int height = context.computeOnClient(minecraft -> minecraft.getWindow().getHeight());
        int scale = context.computeOnClient(minecraft -> minecraft.options.guiScale().get());
        resize(context, 1920, 1080, 1);
        context.waitTicks(30);
        context.takeScreenshot("energy-research-inherited");
        resize(context, width, height, scale);

        context.runOnClient(minecraft -> minecraft.player.closeContainer());
        context.waitTicks(5);
    }

    private static PlayerKnowledge stored(MinecraftServer minecraft)
    {
        return KnowledgeState.get(minecraft).of(player(minecraft).getUUID());
    }
}
