package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.common.content.altar.VoidAltarTile;
import net.voxelindustry.voidheart.common.content.crystal.BundleSize;
import net.voxelindustry.voidheart.common.content.crystal.CrystalBundles;
import net.voxelindustry.voidheart.common.content.crystal.CrystalKind;
import net.voxelindustry.voidheart.common.content.essence.EssenceRegistry;
import net.voxelindustry.voidheart.common.content.essence.EssenceStorage;
import net.voxelindustry.voidheart.common.content.essence.EssenceType;
import net.voxelindustry.voidheart.common.content.essence.RavenousCollectorTile;
import net.voxelindustry.voidheart.common.content.essence.RavenousStorageTile;
import net.voxelindustry.voidheart.common.content.essence.RelayNetwork;
import net.voxelindustry.voidheart.common.content.hoard.AspectFoldTile;
import net.voxelindustry.voidheart.common.content.hoard.HoardContent;
import net.voxelindustry.voidheart.common.content.hoard.HoardCofferTile;
import net.voxelindustry.voidheart.common.content.hoard.PocketHoard;
import net.voxelindustry.voidheart.common.content.research.Knowledge;
import net.voxelindustry.voidheart.common.content.works.OfficiantTile;
import net.voxelindustry.voidheart.common.content.works.WorksContent;
import net.voxelindustry.voidheart.common.energy.creative.CreativeEnergyContent;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartDataComponents;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;
import net.voxelindustry.voidheart.common.world.VoidPocketState;

import java.util.List;

import static net.voxelindustry.voidheart.gametest.GameTestKit.*;

/**
 * Essence through the pocket: storages set in a pocket pour into its reserve and leave the relays, a collector fills the reserve through an Aspect
 * Fold, Contravoid takes a storage's free room, a Fold without void energy closes, a rite worked by an Officiant drinks from the reserve through
 * another Fold, two Folds of one pocket count its essence once, and a storage broken in the pocket takes its share back.
 */
public class AspectPassageClientTest implements FabricClientGameTest
{
    private static final BlockPos WELL       = new BlockPos(0, FLAT_FEET - 3, 0);
    private static final BlockPos COLLECTOR  = new BlockPos(-12, FLAT_FEET, -4);
    private static final BlockPos FILL_RELAY = new BlockPos(-12, FLAT_FEET, -1);
    private static final BlockPos FILL_FOLD  = new BlockPos(-12, FLAT_FEET, 2);
    private static final BlockPos ALTAR      = new BlockPos(0, FLAT_FEET, 8);
    private static final List<BlockPos> PILLARS = List.of(
            new BlockPos(-1, 0, 3), new BlockPos(1, 0, 3), new BlockPos(1, 0, -3), new BlockPos(-1, 0, -3),
            new BlockPos(3, 0, -1), new BlockPos(3, 0, 1), new BlockPos(-3, 0, -1), new BlockPos(-3, 0, 1));
    private static final BlockPos ALTAR_RELAY = new BlockPos(0, FLAT_FEET, 2);
    private static final BlockPos DRAW_FOLD   = new BlockPos(2, FLAT_FEET, 2);
    private static final BlockPos TWIN_FOLD   = new BlockPos(-2, FLAT_FEET, 2);
    private static final BlockPos CLOSED_FOLD = new BlockPos(40, FLAT_FEET, 0);

    private static final long FIERY_HELD     = 10_000;
    private static final long STILLNESS_HELD = 20_000;
    private static final long FOLD_DRAUGHT   = 1_500;

    @Override
    public void runTest(ClientGameTestContext context)
    {
        try (var world = context.worldBuilder().create())
        {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            freezeTime(server, "noon");
            server.runCommand("gamemode creative @a");
            context.waitTicks(20);
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));

            server.runOnServer(AspectPassageClientTest::buildPocket);
            checkVessels(context, server);
            server.runOnServer(AspectPassageClientTest::buildOverworld);
            context.waitTicks(25);
            checkFilling(context, server);
            checkContravoidAndClosed(server);
            checkRite(context, server);
            checkTwinFolds(context, server);
            checkBreaking(context, server);
        }
    }

    private static PocketHoard.Hoard hoard(MinecraftServer minecraft)
    {
        return PocketHoard.get(minecraft).of(player(minecraft).getUUID());
    }

    private static long reserve(MinecraftServer minecraft, EssenceType aspect)
    {
        return hoard(minecraft).reserve().amountOf(aspect);
    }

    private static ServerLevel voidLevel(MinecraftServer minecraft)
    {
        return minecraft.getLevel(VoidHeart.VOID_WORLD_KEY);
    }

    private static BlockPos pocket(MinecraftServer minecraft)
    {
        return VoidPocketState.getVoidPocketState(minecraft.overworld()).getHeartData(player(minecraft).getUUID()).pocketPos();
    }

    private static BlockPos fieryVessel(MinecraftServer minecraft)
    {
        return pocket(minecraft).offset(2, 0, 2);
    }

    private static BlockPos stillVessel(MinecraftServer minecraft)
    {
        return pocket(minecraft).offset(-2, 0, 2);
    }

    private static BlockPos freeVessel(MinecraftServer minecraft)
    {
        return pocket(minecraft).offset(0, 0, -3);
    }

    private static void storage(ServerLevel level, BlockPos pos, EssenceType aspect, long amount)
    {
        var stack = new ItemStack(VoidHeartBlocks.RAVENOUS_STORAGE);
        if (aspect != EssenceRegistry.EMPTY)
        {
            var stored = new EssenceStorage(aspect, RavenousStorageTile.CAPACITY);
            stored.fill(aspect, (int) amount);
            stack.set(VoidHeartDataComponents.ESSENCE_STORAGE, stored);
        }
        level.setBlockAndUpdate(pos, VoidHeartBlocks.RAVENOUS_STORAGE.defaultBlockState());
        VoidHeartBlocks.RAVENOUS_STORAGE.setPlacedBy(level, pos, VoidHeartBlocks.RAVENOUS_STORAGE.defaultBlockState(), null, stack);
    }

    private static void fold(ServerLevel level, BlockPos pos, MinecraftServer minecraft)
    {
        level.setBlockAndUpdate(pos, HoardContent.ASPECT_FOLD.defaultBlockState());
        ((AspectFoldTile) level.getBlockEntity(pos)).setOwner(player(minecraft).getUUID());
    }

    /**
     * A pocket with a coffer for the rite's ingredients, a Fiery storage, a Stillness storage and an empty one, its chunks kept loaded.
     */
    private static void buildPocket(MinecraftServer minecraft)
    {
        var owner = player(minecraft).getUUID();
        var pockets = VoidPocketState.getVoidPocketState(minecraft.overworld());
        pockets.createPocket(voidLevel(minecraft), owner);
        var level = voidLevel(minecraft);
        var center = pocket(minecraft);
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                level.setChunkForced((center.getX() >> 4) + dx, (center.getZ() >> 4) + dz, true);

        var coffer = center.offset(3, 0, -3);
        level.setBlockAndUpdate(coffer, HoardContent.HOARD_COFFER.defaultBlockState());
        ((HoardCofferTile) level.getBlockEntity(coffer)).setBundle(new ItemStack(CrystalBundles.bundle(CrystalKind.RUBRUM, BundleSize.LARGE)));

        storage(level, fieryVessel(minecraft), EssenceRegistry.FIERY, FIERY_HELD);
        storage(level, stillVessel(minecraft), EssenceRegistry.STILLNESS, STILLNESS_HELD);
        storage(level, freeVessel(minecraft), EssenceRegistry.EMPTY, 0);
    }

    /**
     * Within a second the storages pour into the reserve, leave the relays and show its fill.
     */
    private static void checkVessels(ClientGameTestContext context, TestServerContext server)
    {
        waitFor(context, server, 80, "the pocket's storages never joined its reserve",
                minecraft -> reserve(minecraft, EssenceRegistry.FIERY) == FIERY_HELD && reserve(minecraft, EssenceRegistry.STILLNESS) == STILLNESS_HELD);
        server.runOnServer(minecraft ->
        {
            var reserve = hoard(minecraft).reserve();
            expect(reserve.capacityOf(EssenceRegistry.FIERY) == RavenousStorageTile.CAPACITY, "Fiery room " + reserve.capacityOf(EssenceRegistry.FIERY));
            expect(reserve.freeCapacity() == RavenousStorageTile.CAPACITY, "free room " + reserve.freeCapacity());
            var vessel = (RavenousStorageTile) voidLevel(minecraft).getBlockEntity(fieryVessel(minecraft));
            expect(vessel.isVessel(), "the Fiery storage is no vessel");
            expect(vessel.amountOf(EssenceRegistry.FIERY) == 0 && !vessel.linkable(), "the vessel still offers essence to relays");
            expect(vessel.storage().amount() == FIERY_HELD, "the vessel shows " + vessel.storage().amount());
        });
    }

    /**
     * A collector, a relay and an Aspect Fold under the well's field; the altar worked by an Officiant, its relay and the Fold it drinks through;
     * and a Fold far from any field.
     */
    private static void buildOverworld(MinecraftServer minecraft)
    {
        var level = minecraft.overworld();
        level.setBlockAndUpdate(WELL, CreativeEnergyContent.CREATIVE_VOID_WELL.defaultBlockState());

        level.setBlockAndUpdate(COLLECTOR, VoidHeartBlocks.RAVENOUS_COLLECTOR.defaultBlockState());
        var collector = (RavenousCollectorTile) level.getBlockEntity(COLLECTOR);
        collector.storage().attune(EssenceRegistry.FIERY);
        collector.storage().fill(EssenceRegistry.FIERY, RavenousCollectorTile.CAPACITY);
        level.setBlockAndUpdate(FILL_RELAY, VoidHeartBlocks.RAVENOUS_RELAY.defaultBlockState());
        fold(level, FILL_FOLD, minecraft);

        level.setBlockAndUpdate(ALTAR, VoidHeartBlocks.VOID_ALTAR.defaultBlockState());
        for (var offset : PILLARS)
            level.setBlockAndUpdate(ALTAR.offset(offset), VoidHeartBlocks.VOID_PILLAR.defaultBlockState());
        level.setBlockAndUpdate(ALTAR.below(), WorksContent.OFFICIANT.defaultBlockState());
        ((OfficiantTile) level.getBlockEntity(ALTAR.below())).setOwner(player(minecraft).getUUID());
        level.setBlockAndUpdate(ALTAR_RELAY, VoidHeartBlocks.RAVENOUS_RELAY.defaultBlockState());
        fold(level, DRAW_FOLD, minecraft);

        fold(level, CLOSED_FOLD, minecraft);
        // A plain Fold beside the Aspect Fold, so the two read as a pair
        level.setBlockAndUpdate(FILL_FOLD.west(2), HoardContent.FOLD.defaultBlockState());
    }

    /**
     * The collector's Fiery crosses into the reserve through the Fold.
     */
    private static void checkFilling(ClientGameTestContext context, TestServerContext server)
    {
        waitFor(context, server, 400, () -> "the collector filled nothing into the reserve: "
                        + server.computeOnServer(minecraft -> reserve(minecraft, EssenceRegistry.FIERY)),
                minecraft -> reserve(minecraft, EssenceRegistry.FIERY) >= FIERY_HELD + 1_000);

        server.runCommand("gamemode spectator @a");
        aimFrom(context, server, FILL_RELAY.getX() + 4.5, FLAT_FEET + 1.5, FILL_RELAY.getZ() + 0.5, FILL_RELAY.getX() + 0.5, FLAT_FEET + 0.5,
                FILL_RELAY.getZ() + 0.5, 10);
        context.getInput().pressKey(InputConstants.KEY_F1);
        context.takeScreenshot("aspect-fold-filling");
        context.getInput().pressKey(InputConstants.KEY_F1);

        // Looked at, Jade shows the reserve
        aimFrom(context, server, FILL_FOLD.getX() + 0.5, FLAT_FEET + 0.2, FILL_FOLD.getZ() + 2.5, FILL_FOLD.getX() + 0.5, FLAT_FEET + 0.5,
                FILL_FOLD.getZ() + 0.5, 20);
        context.takeScreenshot("aspect-fold-jade");

        aimFrom(context, server, FILL_FOLD.getX() - 0.5, FLAT_FEET + 0.4, FILL_FOLD.getZ() + 3.2, FILL_FOLD.getX() - 0.5, FLAT_FEET + 0.5,
                FILL_FOLD.getZ() + 0.5, 10);
        context.getInput().pressKey(InputConstants.KEY_F1);
        context.takeScreenshot("folds-side-by-side");
        context.getInput().pressKey(InputConstants.KEY_F1);
        server.runCommand("gamemode creative @a");
    }

    /**
     * Contravoid takes the empty storage's room; a Fold with no void energy around it neither takes nor gives.
     */
    private static void checkContravoidAndClosed(TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            var fold = (AspectFoldTile) minecraft.overworld().getBlockEntity(DRAW_FOLD);
            var taken = fold.receive(EssenceRegistry.CONTRAVOID, 500);
            expect(taken == 500, "the Fold let in " + taken + " Contravoid");
            var free = hoard(minecraft).reserve().slots().get(net.voxelindustry.voidheart.common.content.hoard.ReserveVessels.slot(freeVessel(minecraft), 0));
            expect(free != null && free.aspect() == EssenceRegistry.CONTRAVOID, "the empty storage was not attuned to Contravoid: " + free);

            var closed = (AspectFoldTile) minecraft.overworld().getBlockEntity(CLOSED_FOLD);
            expect(!closed.isOpen(), "a Fold far from any field is open");
            expect(closed.spaceFor(EssenceRegistry.FIERY) == 0 && closed.amountOf(EssenceRegistry.FIERY) == 0 && closed.extract(EssenceRegistry.FIERY, 10) == 0,
                    "a closed Fold still passes essence");
        });
    }

    /**
     * A Fold ordered from the hoard: the altar's Officiant lays it out and the rite drinks its Stillness from the reserve through the Fold beside it.
     */
    private static void checkRite(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            Knowledge.lockOf(minecraft.registryAccess(), id("altar/fold")).ifPresent(knowledge(minecraft)::grant);
            var hoard = hoard(minecraft);
            hoard.insert(ItemVariant.of(Items.HOPPER), 1);
            hoard.insert(ItemVariant.of(VoidHeartItems.COVETOUS_COPPER_INGOT), 4);
            hoard.insert(ItemVariant.of(VoidHeartItems.VOID_PEARL), 1);
            hoard.insert(ItemVariant.of(VoidHeartItems.ENDER_SHARD), 2);
            hoard.insert(ItemVariant.of(CrystalBundles.shard(CrystalKind.AZURIUM)), 1);
            var plan = WorksContent.order(minecraft, player(minecraft).getUUID(), ItemVariant.of(HoardContent.FOLD), 1).plan();
            expect(plan.isComplete() && plan.tasks().size() == 1, "the Fold planned " + plan.tasks() + ", lacking " + plan.lacking());
        });
        waitFor(context, server, 600, "the altar never began drinking for the Fold",
                minecraft -> ((VoidAltarTile) minecraft.overworld().getBlockEntity(ALTAR)).isCrafting());
        aimFrom(context, server, ALTAR.getX() + 0.5, FLAT_FEET + 2, ALTAR.getZ() - 7.5, ALTAR.getX() + 0.5, FLAT_FEET + 0.5, ALTAR.getZ() + 0.5, 10);
        context.getInput().pressKey(InputConstants.KEY_F1);
        context.takeScreenshot("aspect-fold-rite");
        context.getInput().pressKey(InputConstants.KEY_F1);

        waitFor(context, server, 2000, () -> "no Fold reached the hoard; Stillness left " + server.computeOnServer(minecraft -> reserve(minecraft, EssenceRegistry.STILLNESS)),
                minecraft -> hoard(minecraft).count(ItemVariant.of(HoardContent.FOLD)) == 1);
        server.runOnServer(minecraft -> expect(reserve(minecraft, EssenceRegistry.STILLNESS) == STILLNESS_HELD - FOLD_DRAUGHT,
                "the rite drank " + (STILLNESS_HELD - reserve(minecraft, EssenceRegistry.STILLNESS)) + " Stillness from the reserve"));
    }

    /**
     * A second Fold of the same pocket beside the altar's relay: the altar counts the reserve once.
     */
    private static void checkTwinFolds(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft -> fold(minecraft.overworld(), TWIN_FOLD, minecraft));
        waitFor(context, server, 120, "the relay never linked the second Fold",
                minecraft -> RelayNetwork.suppliersOfReceiver(minecraft.overworld(), ALTAR, EssenceRegistry.STILLNESS).size() >= 2);
        server.runOnServer(minecraft ->
        {
            var suppliers = RelayNetwork.suppliersOfReceiver(minecraft.overworld(), ALTAR, EssenceRegistry.STILLNESS).values();
            var held = RelayNetwork.heldBy(suppliers, EssenceRegistry.STILLNESS);
            expect(held == reserve(minecraft, EssenceRegistry.STILLNESS), "two Folds of one pocket count " + held + " Stillness, the reserve holds "
                    + reserve(minecraft, EssenceRegistry.STILLNESS));
        });
    }

    /**
     * Broken in the pocket, the Fiery storage drops holding the whole Fiery reserve, being its only vessel, and the reserve keeps nothing of it.
     */
    private static void checkBreaking(ClientGameTestContext context, TestServerContext server)
    {
        var before = server.computeOnServer(minecraft -> reserve(minecraft, EssenceRegistry.FIERY));
        server.runOnServer(minecraft -> voidLevel(minecraft).destroyBlock(fieryVessel(minecraft), true));
        context.waitTicks(5);
        server.runOnServer(minecraft ->
        {
            var pos = fieryVessel(minecraft);
            var drops = voidLevel(minecraft).getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2));
            var dropped = drops.stream().map(ItemEntity::getItem).filter(stack -> stack.is(VoidHeartBlocks.RAVENOUS_STORAGE.asItem())).findFirst();
            expect(dropped.isPresent(), "the storage dropped nothing: " + drops);
            var held = dropped.get().get(VoidHeartDataComponents.ESSENCE_STORAGE);
            var expected = Math.min(RavenousStorageTile.CAPACITY, before);
            expect(held != null && held.type() == EssenceRegistry.FIERY && held.amount() == expected,
                    "the storage took " + (held == null ? "nothing" : held.amount() + " " + held.type().identifier()) + ", expected " + expected);
            expect(reserve(minecraft, EssenceRegistry.FIERY) == before - expected, "the reserve keeps " + reserve(minecraft, EssenceRegistry.FIERY) + " Fiery");
            expect(hoard(minecraft).reserve().capacityOf(EssenceRegistry.FIERY) == 0, "the broken storage's room stayed");
        });
    }
}
