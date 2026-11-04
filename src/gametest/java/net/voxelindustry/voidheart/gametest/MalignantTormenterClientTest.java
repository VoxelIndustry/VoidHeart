package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEventListener;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.client.render.tormenter.TormenterHeads;
import net.voxelindustry.voidheart.common.content.tormenter.MalignantTormenterTile;
import net.voxelindustry.voidheart.common.content.tormenter.TormenterDropletShape;
import net.voxelindustry.voidheart.common.content.tormenter.TormenterSouls;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartDataComponents;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Souls taken by a single tormenter of the hotbar, then a sated one yielding the loot of its mob and showing its heads.
 */
public class MalignantTormenterClientTest implements FabricClientGameTest
{
    private static final int      GROUND  = -61;
    private static final BlockPos PLACED  = new BlockPos(0, GROUND + 1, 3);
    private static final BlockPos ZOMBIES  = new BlockPos(-5, GROUND + 1, 14);
    private static final BlockPos BLAZES   = new BlockPos(0, GROUND + 1, 14);
    private static final BlockPos HOPPED   = new BlockPos(5, GROUND + 1, 14);
    private static final BlockPos CREEPERS = new BlockPos(10, GROUND + 1, 14);
    private static final BlockPos COWS     = new BlockPos(15, GROUND + 1, 14);
    private static final BlockPos SLIMES   = new BlockPos(20, GROUND + 1, 14);
    private static final BlockPos HUSK     = new BlockPos(-10, GROUND + 1, 14);
    private static final BlockPos PARTIAL  = new BlockPos(-15, GROUND + 1, 14);
    private static final BlockPos BLOCKED = new BlockPos(-5, GROUND + 1, 3);
    private static final BlockPos FRAMED  = new BlockPos(3, GROUND + 2, 8);
    private static final int      BREAKER = 778;

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
            server.runCommand("gamemode creative @a");
            context.waitTicks(20);
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));

            checkHotbarClaims(server);
            checkPlacedWithRealClick(context, server);
            checkLoot(context, server);
            checkDrops(server);
            checkSolid(context, server);
            checkSounds(context, server);
            showItem(context, server);

            context.getInput().pressKey(InputConstants.KEY_F1);
            showBlocks(context, server);
            context.getInput().pressKey(InputConstants.KEY_F1);

            // Jade names the tormenter when looking at a part of its droplet
            aimFrom(context, server, ZOMBIES.getX() + 0.5, GROUND + 2, ZOMBIES.getZ() - 3, ZOMBIES.getX() + 0.5, GROUND + 3.6, ZOMBIES.getZ() + 0.5, 10);
            context.takeScreenshot("tormenter-jade-part");
        }
    }

    private static void checkHotbarClaims(TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            var inventory = player.getInventory();
            inventory.clearContent();

            // Out of the hotbar, it takes nothing
            inventory.setItem(12, tormenter());
            kill(minecraft, EntityTypes.ZOMBIE, player);
            expect(inventory.getItem(12).get(VoidHeartDataComponents.TORMENTER_SOULS) == null, "a tormenter outside the hotbar took a soul");

            // With two in the hotbar, one takes it
            inventory.setItem(1, tormenter());
            inventory.setItem(4, tormenter());
            kill(minecraft, EntityTypes.ZOMBIE, player);
            expectSouls(player, 1, EntityTypes.ZOMBIE, 1);
            expect(inventory.getItem(4).get(VoidHeartDataComponents.TORMENTER_SOULS) == null, "a second tormenter of the hotbar reacted");

            // Another mob goes to the empty one, the first stays bound
            kill(minecraft, EntityTypes.SKELETON, player);
            expectSouls(player, 1, EntityTypes.ZOMBIE, 1);
            expectSouls(player, 4, EntityTypes.SKELETON, 1);

            // Another death nobody caused takes nothing
            var stray = spawned(minecraft, EntityTypes.ZOMBIE);
            stray.hurtServer(level(minecraft), level(minecraft).damageSources().generic(), Float.MAX_VALUE);
            expectSouls(player, 1, EntityTypes.ZOMBIE, 1);

            // A refused mob takes nothing either
            kill(minecraft, EntityTypes.WITHER, player);
            expect(player.getInventory().getItem(2).isEmpty(), "a refused mob took a tormenter");

            // Only one of a stack feeds, it comes out of it and the rest stays empty
            inventory.clearContent();
            inventory.setItem(0, new ItemStack(VoidHeartBlocks.MALIGNANT_TORMENTER, 3));
            kill(minecraft, EntityTypes.ZOMBIE, player);
            var fed = 0;
            var empty = 0;
            for (var slot = 0; slot < inventory.getContainerSize(); slot++)
            {
                var stack = inventory.getItem(slot);
                if (!stack.is(VoidHeartBlocks.MALIGNANT_TORMENTER.asItem()))
                    continue;
                if (stack.get(VoidHeartDataComponents.TORMENTER_SOULS) != null)
                    fed += stack.getCount();
                else
                    empty += stack.getCount();
            }
            expect(fed == 1 && empty == 2, "a stack of three should leave one fed and two empty, found " + fed + " and " + empty);

            // Sixteen kills sate one, and the seventeenth starts another empty one instead
            inventory.clearContent();
            inventory.setItem(0, tormenter());
            for (var kill = 0; kill < TormenterSouls.REQUIRED; kill++)
                kill(minecraft, EntityTypes.ZOMBIE, player);
            expectSouls(player, 0, EntityTypes.ZOMBIE, TormenterSouls.REQUIRED);

            inventory.setItem(1, tormenter());
            kill(minecraft, EntityTypes.ZOMBIE, player);
            expectSouls(player, 0, EntityTypes.ZOMBIE, TormenterSouls.REQUIRED);
            expectSouls(player, 1, EntityTypes.ZOMBIE, 1);
        });
    }

    private static void checkPlacedWithRealClick(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            var inventory = player(minecraft).getInventory();
            inventory.setSelectedSlot(0);
            inventory.setItem(0, sated(EntityTypes.ZOMBIE));
            level(minecraft).setBlockAndUpdate(PLACED.below(), Blocks.STONE.defaultBlockState());
        });
        context.waitTicks(2);

        aimFrom(context, server, 0.5, GROUND + 1, 0.5, 0.5, GROUND + 1, 3.5, 5);
        context.takeScreenshot("tormenter-item-in-hand");
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
        context.waitTicks(5);

        server.runOnServer(minecraft ->
        {
            var tormenter = tile(minecraft, PLACED);
            expect(tormenter.isSated(), "a sated item placed an unsated tormenter");
            expect(Objects.requireNonNull(tormenter.souls()).type() == EntityTypes.ZOMBIE, "the placed tormenter lost its mob");
            expect(level(minecraft).getBlockState(PLACED.above(2)).is(VoidHeartBlocks.MALIGNANT_TORMENTER_PART), "a sated tormenter placed no droplet");
        });

        // The client sees what it needs to draw
        context.runOnClient(minecraft ->
        {
            if (!(minecraft.level.getBlockEntity(PLACED) instanceof MalignantTormenterTile tormenter) || !tormenter.isSated())
                throw new AssertionError("the client does not see a sated tormenter");
        });

        // It survives being saved and loaded
        server.runOnServer(minecraft ->
        {
            var level = level(minecraft);
            var saved = tile(minecraft, PLACED).saveCustomOnly(level.registryAccess());
            var copy = new MalignantTormenterTile(PLACED, VoidHeartBlocks.MALIGNANT_TORMENTER.defaultBlockState());
            copy.loadCustomOnly(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess(), saved));
            expect(copy.isSated() && Objects.requireNonNull(copy.souls()).type() == EntityTypes.ZOMBIE, "the souls did not survive saving");
        });
    }

    private static void checkLoot(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            placeSated(minecraft, ZOMBIES, EntityTypes.ZOMBIE);
            placeSated(minecraft, BLAZES, EntityTypes.BLAZE);
            placeSated(minecraft, HOPPED, EntityTypes.ZOMBIE);
            level(minecraft).setBlockAndUpdate(HOPPED.below(), Blocks.HOPPER.defaultBlockState());
        });

        // Many rolls, since a zombie or a blaze can come up empty on any of them
        var interval = MalignantTormenterTile.LOOT_INTERVAL_TICKS;
        context.waitTicks(interval * 8 + 2);

        server.runOnServer(minecraft ->
        {
            var fleshFound = countLoot(minecraft, ZOMBIES, Items.ROTTEN_FLESH);
            var rodsFound = countLoot(minecraft, BLAZES, Items.BLAZE_ROD);
            expect(fleshFound > 0, "a zombie tormenter yielded no rotten flesh");
            expect(fleshFound <= 2 * 9 + 8, "a zombie tormenter yielded more flesh than eight or nine rolls can: " + fleshFound);
            expect(rodsFound > 0, "a blaze tormenter yielded no rods, it needs a player's kill");

            var hopper = (HopperBlockEntity) Objects.requireNonNull(level(minecraft).getBlockEntity(HOPPED.below()));
            var held = 0;
            for (var slot = 0; slot < hopper.getContainerSize(); slot++)
                held += hopper.getItem(slot).getCount();
            expect(held > 0, "a hopper under a tormenter caught nothing");

            var mobs = level(minecraft).getEntities(EntityTypes.ZOMBIE, new AABB(ZOMBIES).inflate(4), entity -> true);
            expect(mobs.isEmpty(), "a tormenter spawned an actual mob");

            level(minecraft).setBlockAndUpdate(HOPPED.below(), Blocks.STONE.defaultBlockState());
            clearItems(minecraft);
        });
    }

    private static void checkSolid(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            var level = level(minecraft);
            var player = player(minecraft);
            var at = new BlockPos(0, GROUND + 1, 28);
            clearItems(minecraft);

            placeSated(minecraft, at, EntityTypes.ZOMBIE);
            expect(partsAt(minecraft, at) == TormenterDropletShape.CELLS.size(), "not every part of the droplet was placed");
            for (var cell : TormenterDropletShape.CELLS)
                expect(!level.getBlockState(at.offset(cell.x(), cell.y(), cell.z())).getCollisionShape(level, at).isEmpty(), "a part of the droplet is not solid: " + cell);
            expect(level.getBlockState(at.east()).isAir(), "the floor around the tip of the droplet should stay free");

            // Mined from high up, it all goes and drops one tormenter with its souls
            player.setGameMode(GameType.SURVIVAL);
            player.gameMode.destroyBlock(at.above(2));
            player.setGameMode(GameType.CREATIVE);
            expect(level.getBlockState(at).isAir(), "breaking a part left the tormenter behind");
            expect(partsAt(minecraft, at) == 0, "breaking a part left others behind");
            var dropped = level.getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(4)).stream().filter(item -> item.getItem().is(VoidHeartBlocks.MALIGNANT_TORMENTER.asItem())).toList();
            expect(dropped.size() == 1 && dropped.getFirst().getItem().getCount() == 1, "breaking a part should drop one tormenter, found " + dropped.size());
            var souls = dropped.getFirst().getItem().get(VoidHeartDataComponents.TORMENTER_SOULS);
            expect(souls != null && souls.isComplete(), "the tormenter dropped from a part lost its souls");
            clearItems(minecraft);

            // In creative mode it all goes too, without a drop
            placeSated(minecraft, at, EntityTypes.ZOMBIE);
            player.gameMode.destroyBlock(at.above(3));
            expect(level.getBlockState(at).isAir() && partsAt(minecraft, at) == 0, "breaking a part in creative left some of the droplet");
            expect(level.getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(4)).isEmpty(), "breaking a part in creative dropped something");

            // Breaking the tormenter itself clears its droplet
            placeSated(minecraft, at, EntityTypes.ZOMBIE);
            level.destroyBlock(at, false);
            expect(partsAt(minecraft, at) == 0, "breaking the tormenter left parts behind");

            // An empty one stays in its block
            level.setBlockAndUpdate(at, VoidHeartBlocks.MALIGNANT_TORMENTER.defaultBlockState());
            expect(partsAt(minecraft, at) == 0, "an empty tormenter should not fill a droplet");
            level.setBlockAndUpdate(at, Blocks.AIR.defaultBlockState());

            // With something in the way, a real click places nothing
            player.getInventory().setSelectedSlot(0);
            player.getInventory().setItem(0, sated(EntityTypes.ZOMBIE));
            level.setBlockAndUpdate(BLOCKED.below(), Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(BLOCKED.above(2), Blocks.STONE.defaultBlockState());
        });
        context.waitTicks(2);

        aimFrom(context, server, BLOCKED.getX() + 0.5, GROUND + 1, BLOCKED.getZ() - 2.5, BLOCKED.getX() + 0.5, GROUND + 1, BLOCKED.getZ() + 0.5, 5);
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
        context.waitTicks(5);

        server.runOnServer(minecraft ->
        {
            expect(!level(minecraft).getBlockState(BLOCKED).is(VoidHeartBlocks.MALIGNANT_TORMENTER), "a sated tormenter was placed with its droplet obstructed");
            expect(player(minecraft).getInventory().getItem(0).getCount() == 1, "a refused placement used up the item");
            level(minecraft).setBlockAndUpdate(BLOCKED.above(2), Blocks.AIR.defaultBlockState());
            player(minecraft).getInventory().clearContent();
        });
    }

    private static int partsAt(MinecraftServer minecraft, BlockPos at)
    {
        var count = 0;
        for (var cell : TormenterDropletShape.CELLS)
            if (level(minecraft).getBlockState(at.offset(cell.x(), cell.y(), cell.z())).is(VoidHeartBlocks.MALIGNANT_TORMENTER_PART))
                count++;
        return count;
    }

    private static void checkDrops(TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            var level = level(minecraft);
            var player = player(minecraft);
            clearItems(minecraft);

            var at = new BlockPos(0, GROUND + 1, 22);
            level.setBlockAndUpdate(at, VoidHeartBlocks.MALIGNANT_TORMENTER.defaultBlockState());
            var state = level.getBlockState(at);
            var drops = net.minecraft.world.level.block.Block.getDrops(state, level, at, level.getBlockEntity(at), player, ItemStack.EMPTY);
            expect(drops.size() == 1 && drops.getFirst().getComponentsPatch().isEmpty(), "an empty tormenter should drop a plain item that stacks");

            level.setBlockAndUpdate(at, Blocks.AIR.defaultBlockState());

            placeSated(minecraft, at, EntityTypes.ZOMBIE);
            drops = net.minecraft.world.level.block.Block.getDrops(level.getBlockState(at), level, at, level.getBlockEntity(at), player, ItemStack.EMPTY);
            var souls = drops.getFirst().get(VoidHeartDataComponents.TORMENTER_SOULS);
            expect(souls != null && souls.isComplete() && souls.type() == EntityTypes.ZOMBIE, "a sated tormenter lost its souls when dropped");
            level.setBlockAndUpdate(at, Blocks.AIR.defaultBlockState());
        });
    }

    /**
     * Listens for what the client is told to play, the speakers themselves can't be checked here.
     */
    private static void checkSounds(ClientGameTestContext context, TestServerContext server)
    {
        var heard = Collections.synchronizedList(new ArrayList<String>());
        SoundEventListener listener = (SoundInstance sound, WeighedSoundEvents accessor, float range) ->
        {
            if (sound.getSource() == SoundSource.HOSTILE)
                heard.add(sound.getIdentifier().getPath());
        };
        context.runOnClient(minecraft -> minecraft.getSoundManager().addListener(listener));
        aimFrom(context, server, -2, GROUND + 1, 7, -2, GROUND + 2, 14, 5);
        context.waitTicks(MalignantTormenterTile.LOOT_INTERVAL_TICKS * 8);
        context.runOnClient(minecraft -> minecraft.getSoundManager().removeListener(listener));

        var names = List.copyOf(heard);
        System.out.println("[tormenter] sounds the client was told to play: " + names);
        expect(names.stream().anyMatch(name -> name.contains("zombie")), "no zombie sound reached the client, heard " + names);
        expect(names.stream().anyMatch(name -> name.contains("blaze")), "no blaze sound reached the client, heard " + names);
    }

    private static void showItem(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            var inventory = player(minecraft).getInventory();
            inventory.clearContent();
            inventory.setSelectedSlot(0);
            inventory.setItem(0, new ItemStack(VoidHeartBlocks.MALIGNANT_TORMENTER, 3));
            inventory.setItem(1, souls(EntityTypes.CREEPER, 5));
            inventory.setItem(2, sated(EntityTypes.ZOMBIE));
            inventory.setItem(3, sated(EntityTypes.CREEPER));
            inventory.setItem(4, sated(EntityTypes.BLAZE));
            inventory.setItem(5, sated(EntityTypes.COW));
            inventory.setItem(6, souls(EntityTypes.ZOMBIE, 12));
        });
        aimFrom(context, server, 0.5, GROUND + 1, 0.5, 5, GROUND + 2, 12, 5);
        context.takeScreenshot("tormenter-hotbar-empty-in-hand");

        context.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
        context.waitTicks(5);
        context.takeScreenshot("tormenter-inventory");
        context.setScreen(() -> null);
        context.waitTicks(2);

        context.runOnClient(minecraft -> minecraft.player.getInventory().setSelectedSlot(2));
        aimFrom(context, server, 0.5, GROUND + 1, 0.5, 5, GROUND + 2, 12, 5);
        context.takeScreenshot("tormenter-hotbar-sated-in-hand");

        context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
        context.waitTicks(2);
        context.takeScreenshot("tormenter-sated-third-person");
        context.runOnClient(minecraft ->
        {
            minecraft.options.setCameraType(CameraType.FIRST_PERSON);
            minecraft.player.getInventory().setSelectedSlot(0);
        });

        server.runOnServer(minecraft ->
        {
            player(minecraft).getInventory().clearContent();
            var level = level(minecraft);
            var shown = List.of(sated(EntityTypes.ZOMBIE), souls(EntityTypes.CREEPER, 8), tormenter());
            for (var index = 0; index < shown.size(); index++)
            {
                var item = new ItemEntity(level, -0.5 + index, GROUND + 1.0, 8.5, shown.get(index), 0, 0, 0);
                item.setNeverPickUp();
                item.setNoGravity(true);
                level.addFreshEntity(item);
            }

            level.setBlockAndUpdate(FRAMED.south(), Blocks.STONE.defaultBlockState());
            var frame = new ItemFrame(level, FRAMED, Direction.NORTH);
            frame.setItem(sated(EntityTypes.BLAZE));
            level.addFreshEntity(frame);
        });
        aimFrom(context, server, 0.5, GROUND + 1, 6, 1, GROUND + 1.6, 8.5, 5);
        context.takeScreenshot("tormenter-items-on-ground-and-framed");
        server.runOnServer(minecraft ->
        {
            for (var frame : level(minecraft).getEntitiesOfClass(ItemFrame.class, new AABB(FRAMED).inflate(1)))
                frame.discard();
            level(minecraft).setBlockAndUpdate(FRAMED.south(), Blocks.AIR.defaultBlockState());
        });
        server.runOnServer(MalignantTormenterClientTest::clearItems);
    }

    private static void showBlocks(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            placeSated(minecraft, CREEPERS, EntityTypes.CREEPER);
            placeSated(minecraft, COWS, EntityTypes.COW);
            placeSated(minecraft, SLIMES, EntityTypes.SLIME);
            level(minecraft).setBlockAndUpdate(HUSK, VoidHeartBlocks.MALIGNANT_TORMENTER.defaultBlockState());
            placeWith(minecraft, PARTIAL, EntityTypes.CREEPER, 8);
            clearItems(minecraft);
        });
        context.waitTicks(20);

        context.runOnClient(minecraft ->
        {
            for (var type : List.of(EntityTypes.ZOMBIE, EntityTypes.CREEPER, EntityTypes.COW, EntityTypes.SLIME, EntityTypes.BLAZE))
                expect(TormenterHeads.hasHead(type, minecraft.level), "no head could be copied for " + BuiltInRegistries.ENTITY_TYPE.getKey(type));
        });

        shot(context, server, "tormenter-row", -2, GROUND + 3, 30, -2, GROUND + 2, 14, 5);

        // The same head carried up, at moments of its climb
        for (var moment = 0; moment < 5; moment++)
            closeUp(context, server, "tormenter-zombie-close-" + moment, ZOMBIES, 30);

        closeUp(context, server, "tormenter-creeper-close", CREEPERS, 8);
        closeUp(context, server, "tormenter-cow-close", COWS, 8);
        closeUp(context, server, "tormenter-slime-close", SLIMES, 8);
        closeUp(context, server, "tormenter-blaze-close", BLAZES, 8);
        closeUp(context, server, "tormenter-husk", HUSK, 8);
        closeUp(context, server, "tormenter-partial", PARTIAL, 8);
        shot(context, server, "tormenter-zombie-behind", ZOMBIES.getX() + 0.5, GROUND + 1, ZOMBIES.getZ() + 5.6, ZOMBIES.getX() + 0.5, GROUND + 3, ZOMBIES.getZ() + 0.5, 8);
        shot(context, server, "tormenter-zombie-above", ZOMBIES.getX() + 3.5, GROUND + 6.5, ZOMBIES.getZ() - 3.5, ZOMBIES.getX() + 0.5, GROUND + 3, ZOMBIES.getZ() + 0.5, 8);

        context.runOnClient(minecraft -> minecraft.level.destroyBlockProgress(BREAKER, ZOMBIES.above(2), 6));
        closeUp(context, server, "tormenter-breaking", ZOMBIES, 4);
        context.runOnClient(minecraft -> minecraft.level.destroyBlockProgress(BREAKER, ZOMBIES.above(2), -1));

        server.runOnServer(MalignantTormenterClientTest::clearItems);
        server.runCommand("tick freeze");
        for (var step = 0; step < 8; step++)
        {
            aimFrom(context, server, ZOMBIES.getX() - 0.7 + step * 0.2, GROUND + 1, ZOMBIES.getZ() - 5.6 + step * 0.03, ZOMBIES.getX() + 0.5, GROUND + 3, ZOMBIES.getZ() + 0.5, 3);
            context.takeScreenshot("tormenter-sweep-" + step);
        }
        server.runCommand("tick unfreeze");
    }

    private static void closeUp(ClientGameTestContext context, TestServerContext server, String name, BlockPos at, int settleTicks)
    {
        shot(context, server, name, at.getX() + 0.5, GROUND + 1, at.getZ() - 5.6, at.getX() + 0.5, GROUND + 3, at.getZ() + 0.5, settleTicks);
    }

    /**
     * Without the loot that keeps popping out in front of the heads.
     */
    private static void shot(ClientGameTestContext context, TestServerContext server, String name, double feetX, double feetY, double feetZ, double x, double y, double z, int settleTicks)
    {
        aimFrom(context, server, feetX, feetY, feetZ, x, y, z, settleTicks);
        server.runOnServer(MalignantTormenterClientTest::clearItems);
        context.waitTicks(1);
        context.takeScreenshot(name);
    }

    private static ItemStack tormenter()
    {
        return new ItemStack(VoidHeartBlocks.MALIGNANT_TORMENTER);
    }

    private static ItemStack sated(EntityType<?> type)
    {
        return souls(type, TormenterSouls.REQUIRED);
    }

    private static ItemStack souls(EntityType<?> type, int count)
    {
        var stack = tormenter();
        stack.set(VoidHeartDataComponents.TORMENTER_SOULS, new TormenterSouls(type, count));
        return stack;
    }

    private static void placeSated(MinecraftServer minecraft, BlockPos at, EntityType<?> type)
    {
        placeWith(minecraft, at, type, TormenterSouls.REQUIRED);
    }

    private static void placeWith(MinecraftServer minecraft, BlockPos at, EntityType<?> type, int count)
    {
        var level = level(minecraft);
        var block = VoidHeartBlocks.MALIGNANT_TORMENTER;
        level.setBlockAndUpdate(at, block.defaultBlockState());
        block.setPlacedBy(level, at, block.defaultBlockState(), null, souls(type, count));
    }

    private static MalignantTormenterTile tile(MinecraftServer minecraft, BlockPos at)
    {
        if (level(minecraft).getBlockEntity(at) instanceof MalignantTormenterTile tormenter)
            return tormenter;
        throw new AssertionError("no tormenter at " + at);
    }

    private static LivingEntity spawned(MinecraftServer minecraft, EntityType<? extends LivingEntity> type)
    {
        var level = level(minecraft);
        var mob = Objects.requireNonNull(type.create(level, EntitySpawnReason.COMMAND));
        mob.setPos(new Vec3(30, GROUND + 1, 30));
        level.addFreshEntity(mob);
        return mob;
    }

    private static void kill(MinecraftServer minecraft, EntityType<? extends LivingEntity> type, ServerPlayer player)
    {
        var level = level(minecraft);
        var mob = spawned(minecraft, type);
        mob.hurtServer(level, level.damageSources().playerAttack(player), Float.MAX_VALUE);
        if (mob.isAlive())
            mob.kill(level);
        mob.discard();
    }

    private static int countLoot(MinecraftServer minecraft, BlockPos at, Item item)
    {
        var found = 0;
        for (var entity : level(minecraft).getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(3.5, 4, 3.5)))
            if (entity.getItem().is(item))
                found += entity.getItem().getCount();
        return found;
    }

    private static void clearItems(MinecraftServer minecraft)
    {
        for (var entity : level(minecraft).getEntitiesOfClass(ItemEntity.class, new AABB(new BlockPos(0, GROUND, 0)).inflate(48)))
            entity.discard();
    }

    private static void expectSouls(ServerPlayer player, int slot, EntityType<?> type, int count)
    {
        var souls = player.getInventory().getItem(slot).get(VoidHeartDataComponents.TORMENTER_SOULS);
        expect(souls != null && souls.type() == type && souls.souls() == count,
                "slot " + slot + " should hold " + count + " souls of " + BuiltInRegistries.ENTITY_TYPE.getKey(type) + ", found " + souls);
    }

    private static void expect(boolean condition, String failure)
    {
        if (!condition)
            throw new AssertionError(failure);
    }

    private static void aimFrom(ClientGameTestContext context, TestServerContext server, double feetX, double feetY, double feetZ, double x, double y, double z, int settleTicks)
    {
        double dx = x - feetX;
        double dy = y - (feetY + 1.62);
        double dz = z - feetZ;
        double yaw = Math.toDegrees(Math.atan2(-dx, dz));
        double pitch = -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
        server.runCommand("tp @a " + feetX + " " + feetY + " " + feetZ + " " + yaw + " " + pitch);
        // Landing ends creative flight, so it is set again each time or the player falls away from the framing
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
        });
        context.waitTicks(settleTicks);
    }

    private static ServerLevel level(MinecraftServer minecraft)
    {
        return minecraft.overworld();
    }

    private static ServerPlayer player(MinecraftServer minecraft)
    {
        return minecraft.getPlayerList().getPlayers().getFirst();
    }
}
