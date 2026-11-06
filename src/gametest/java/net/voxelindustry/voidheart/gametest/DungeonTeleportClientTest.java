package net.voxelindustry.voidheart.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.Hud;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.common.content.door.VoidDoorLinks;
import net.voxelindustry.voidheart.common.content.door.VoidDoorTile;
import net.voxelindustry.voidheart.common.content.eyebottle.EyeBottleTile;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartDataComponents;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;
import net.voxelindustry.voidheart.common.world.VoidPocketState;
import net.voxelindustry.voidheart.common.world.dungeon.DungeonAddress;
import net.voxelindustry.voidheart.common.world.dungeon.DungeonSize;
import net.voxelindustry.voidheart.common.world.dungeon.DungeonTheme;
import net.voxelindustry.voidheart.common.world.dungeon.VoidDungeons;

import static net.voxelindustry.voidheart.gametest.GameTestKit.*;

/**
 * Every way a survival player could teleport fails inside a dungeon, without using anything up, while a creative player stays free.
 */
public class DungeonTeleportClientTest implements FabricClientGameTest
{
    private static final DungeonAddress ADDRESS = new DungeonAddress(DungeonSize.SMALL, DungeonTheme.CATHEDRAL, 1, 7);
    private static final int EATING_TICKS = 50;
    private static final int POCKET_TELEPORT_DELAY = 110;
    private static final double SEARCH_RADIUS = 64;
    private static final String REFUSAL = "A force is interfering with your attempt";

    private BlockPos arrival;

    @Override
    public void runTest(ClientGameTestContext context)
    {
        try (var world = context.worldBuilder().create())
        {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            server.runCommand("gamemode survival @a");
            server.runCommand("gamerule show_advancement_messages false");
            context.waitTicks(10);
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));

            server.runOnServer(minecraft ->
            {
                var player = player(minecraft);
                VoidPocketState.getVoidPocketState(minecraft.overworld()).createPocket(minecraft.getLevel(VoidHeart.VOID_WORLD_KEY), player.getUUID());
                VoidDungeons.enter(player, ADDRESS);
            });
            context.waitTicks(20);
            world.getConnection().waitForChunksRender(false, 1200);
            arrival = server.computeOnServer(minecraft -> player(minecraft).blockPosition());
            server.runOnServer(minecraft -> player(minecraft).getAbilities().invulnerable = true);

            checkEnderPearl(context, server);
            checkChorusFruit(context, server);
            checkVoidPearl(context, server);
            checkPearlInFlight(context, server);
            checkEyeBottle(context, server);
            checkVoidKey(context, server);
            checkPortalFrame(context, server);
            checkNetherPortal(context, server);
            checkCreativeStaysFree(context, server);

            server.runCommand("gamemode spectator @a");
        }
    }

    private void give(ClientGameTestContext context, TestServerContext server, ItemStack stack)
    {
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            player.getInventory().clearContent();
            player.getInventory().setSelectedSlot(0);
            player.getInventory().setItem(0, stack);
            player.teleportTo(arrival.getX() + 0.5, arrival.getY(), arrival.getZ() + 0.5);
            player.containerMenu.broadcastChanges();
        });
        context.waitTicks(5);
    }

    private static void clearMessage(ClientGameTestContext context)
    {
        context.runOnClient(minecraft -> minecraft.gui.hud.setOverlayMessage(Component.empty(), false));
    }

    private static void assertRefusalShown(ClientGameTestContext context, String what)
    {
        var shown = context.computeOnClient(minecraft ->
        {
            try
            {
                var field = Hud.class.getDeclaredField("overlayMessageString");
                field.setAccessible(true);
                var message = (Component) field.get(minecraft.gui.hud);
                return message == null ? "" : message.getString();
            } catch (ReflectiveOperationException e)
            {
                throw new AssertionError(e);
            }
        });
        if (!REFUSAL.equals(shown))
            throw new AssertionError(what + " should tell the player \"" + REFUSAL + "\", the action bar shows \"" + shown + "\"");
    }

    private void assertStayed(TestServerContext server, String what)
    {
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            if (player.level().dimension() != VoidDungeons.WORLD_KEY || player.blockPosition().distManhattan(arrival) > 2)
                throw new AssertionError(what + " moved the player to " + player.position() + " in " + player.level().dimension());
        });
    }

    private static long pearls(MinecraftServer minecraft, BlockPos around)
    {
        return VoidDungeons.level(minecraft).getEntitiesOfClass(ThrownEnderpearl.class, new AABB(around).inflate(SEARCH_RADIUS)).size();
    }

    private void checkEnderPearl(ClientGameTestContext context, TestServerContext server)
    {
        give(context, server, new ItemStack(Items.ENDER_PEARL, 4));
        clearMessage(context);
        context.getInput().pressKey(options -> options.keyUse);
        context.waitTicks(5);
        context.takeScreenshot("dungeon-teleport-refused");
        assertRefusalShown(context, "An ender pearl");
        context.waitTicks(10);
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            if (pearls(minecraft, arrival) != 0)
                throw new AssertionError("An ender pearl was thrown inside a dungeon");
            if (player.getMainHandItem().getCount() != 4)
                throw new AssertionError("A refused ender pearl should not be used up, " + player.getMainHandItem().getCount() + " left");
            if (player.getCooldowns().isOnCooldown(player.getMainHandItem()))
                throw new AssertionError("A refused ender pearl should not start its cooldown");
        });
        assertStayed(server, "An ender pearl");
    }

    private void holdUse(ClientGameTestContext context)
    {
        context.getInput().holdKey(options -> options.keyUse);
        context.waitTicks(EATING_TICKS);
        context.getInput().releaseKey(options -> options.keyUse);
        context.waitTicks(5);
    }

    private void checkChorusFruit(ClientGameTestContext context, TestServerContext server)
    {
        give(context, server, new ItemStack(Items.CHORUS_FRUIT, 3));
        server.runCommand("effect give @a minecraft:hunger 3 100 true");
        context.waitTicks(30);
        clearMessage(context);
        holdUse(context);
        assertRefusalShown(context, "A chorus fruit");
        server.runOnServer(minecraft ->
        {
            if (player(minecraft).getMainHandItem().getCount() != 3)
                throw new AssertionError("A refused chorus fruit should not be eaten");
        });
        assertStayed(server, "A chorus fruit");
    }

    private void checkVoidPearl(ClientGameTestContext context, TestServerContext server)
    {
        give(context, server, new ItemStack(VoidHeartItems.VOID_PEARL, 2));
        clearMessage(context);
        holdUse(context);
        assertRefusalShown(context, "A void pearl");
        context.waitTicks(POCKET_TELEPORT_DELAY);
        server.runOnServer(minecraft ->
        {
            if (player(minecraft).getMainHandItem().getCount() != 2)
                throw new AssertionError("A refused void pearl should not be used up");
        });
        assertStayed(server, "A void pearl");
    }

    /**
     * A pearl thrown before entering the dungeon still lands, and must not pull its owner away.
     */
    private void checkPearlInFlight(ClientGameTestContext context, TestServerContext server)
    {
        give(context, server, ItemStack.EMPTY);
        server.runOnServer(minecraft ->
        {
            var level = VoidDungeons.level(minecraft);
            var pearl = new ThrownEnderpearl(level, player(minecraft), new ItemStack(Items.ENDER_PEARL));
            pearl.setPos(arrival.getX() + 0.5, arrival.getY() + 2.5, arrival.getZ() + 0.5);
            pearl.setDeltaMovement(new Vec3(0, -0.5, 0));
            level.addFreshEntity(pearl);
        });
        context.waitTicks(40);
        server.runOnServer(minecraft ->
        {
            if (pearls(minecraft, arrival) != 0)
                throw new AssertionError("A pearl that landed in a dungeon should be gone");
        });
        assertStayed(server, "A pearl in flight");
    }

    private void checkEyeBottle(ClientGameTestContext context, TestServerContext server)
    {
        give(context, server, ItemStack.EMPTY);
        clearMessage(context);
        var at = arrival.above(1).east();
        server.runOnServer(minecraft ->
        {
            var level = VoidDungeons.level(minecraft);
            var player = player(minecraft);
            level.setBlockAndUpdate(at, VoidHeartBlocks.EYE_BOTTLE.defaultBlockState());
            ((EyeBottleTile) level.getBlockEntity(at)).setPlayerID(player.getUUID());
            level.getBlockState(at).useItemOn(ItemStack.EMPTY, level, player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at, false));
        });
        context.waitTicks(10);
        assertRefusalShown(context, "An eye bottle");
        context.waitTicks(POCKET_TELEPORT_DELAY);
        assertStayed(server, "An eye bottle");
        server.runOnServer(minecraft -> VoidDungeons.level(minecraft).removeBlock(at, false));
    }

    private void checkVoidKey(ClientGameTestContext context, TestServerContext server)
    {
        give(context, server, new ItemStack(VoidHeartItems.VOID_KEY));
        server.runOnServer(minecraft ->
        {
            var level = VoidDungeons.level(minecraft);
            var player = player(minecraft);
            var voidWorld = minecraft.getLevel(VoidHeart.VOID_WORLD_KEY);
            var voidDoor = new BlockPos(3000, 70, 3000);
            voidWorld.setBlockAndUpdate(voidDoor, VoidHeartBlocks.VOID_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
            voidWorld.setBlockAndUpdate(voidDoor.above(), VoidHeartBlocks.VOID_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
            var tile = (VoidDoorTile) voidWorld.getBlockEntity(voidDoor);

            var door = arrival.south();
            level.setBlockAndUpdate(door, Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
            level.setBlockAndUpdate(door.above(), Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));

            var key = player.getMainHandItem();
            key.set(VoidHeartDataComponents.DOOR_LINK, new VoidHeartDataComponents.DoorLink(voidDoor, tile.getId()));
            key.getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(door), Direction.NORTH, door, false)));

            var links = VoidDoorLinks.get(minecraft);
            if (links.getVoidDoor(GlobalPos.of(level.dimension(), door)).isPresent())
                throw new AssertionError("A door inside a dungeon should refuse a void key");
            level.removeBlock(door.above(), false);
            level.removeBlock(door, false);
        });
    }

    private void checkPortalFrame(ClientGameTestContext context, TestServerContext server)
    {
        give(context, server, new ItemStack(VoidHeartItems.PORTAL_CORE));
        var corner = arrival.offset(2, 0, 2);
        server.runOnServer(minecraft ->
        {
            var level = VoidDungeons.level(minecraft);
            var player = player(minecraft);
            for (int y = 0; y < 5; y++)
            {
                for (int x = 0; x < 5; x++)
                {
                    if (x == 0 || y == 0 || x == 4 || y == 4)
                        level.setBlockAndUpdate(corner.offset(x, y, 0), VoidHeartBlocks.PORTAL_FRAME.defaultBlockState());
                    else
                        level.removeBlock(corner.offset(x, y, 0), false);
                }
            }
            var clicked = corner.offset(2, 0, 0);
            level.getBlockState(clicked).useItemOn(player.getMainHandItem(), level, player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(clicked), Direction.NORTH, clicked, false));
            if (level.getBlockState(clicked).is(VoidHeartBlocks.PORTAL_FRAME_CORE) || player.getMainHandItem().has(VoidHeartDataComponents.PORTAL_LINK))
                throw new AssertionError("A portal should not form inside a dungeon");
        });
    }

    private void checkNetherPortal(ClientGameTestContext context, TestServerContext server)
    {
        give(context, server, ItemStack.EMPTY);
        var corner = arrival.offset(-6, 0, -2);
        server.runOnServer(minecraft ->
        {
            var level = VoidDungeons.level(minecraft);
            for (int y = 0; y < 5; y++)
            {
                for (int x = 0; x < 4; x++)
                {
                    if (x == 0 || y == 0 || x == 3 || y == 4)
                        level.setBlockAndUpdate(corner.offset(x, y, 0), Blocks.OBSIDIAN.defaultBlockState());
                    else
                        level.removeBlock(corner.offset(x, y, 0), false);
                }
            }
            level.setBlockAndUpdate(corner.offset(1, 1, 0), Blocks.FIRE.defaultBlockState());
            if (level.getBlockState(corner.offset(1, 1, 0)).is(Blocks.NETHER_PORTAL))
                throw new AssertionError("Nether portals should not light inside a dungeon");
        });
    }

    private void checkCreativeStaysFree(ClientGameTestContext context, TestServerContext server)
    {
        server.runCommand("gamemode creative @a");
        give(context, server, new ItemStack(Items.ENDER_PEARL, 4));
        context.getInput().pressKey(options -> options.keyUse);
        context.waitTicks(2);
        server.runOnServer(minecraft ->
        {
            if (pearls(minecraft, arrival) == 0)
                throw new AssertionError("A creative player should still throw ender pearls in a dungeon");
        });
    }
}
