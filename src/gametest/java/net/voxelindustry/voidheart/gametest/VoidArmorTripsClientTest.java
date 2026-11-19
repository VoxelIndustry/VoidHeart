package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.client.CameraType;
import net.minecraft.core.NonNullList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import net.voxelindustry.voidheart.common.content.armor.ArmorBalance;
import net.voxelindustry.voidheart.common.content.armor.ArmorGrids;
import net.voxelindustry.voidheart.common.content.armor.ArmorGrids.Placement;
import net.voxelindustry.voidheart.common.content.armor.ArmorHotbar;
import net.voxelindustry.voidheart.common.content.armor.ArmorWear;
import net.voxelindustry.voidheart.common.content.armor.VoidArmorContent;
import net.voxelindustry.voidheart.common.content.armor.WornArmor;
import net.voxelindustry.voidheart.common.content.blight.RapierStrikes;
import net.voxelindustry.voidheart.common.content.kleinstar.KleinStarBalance;
import net.voxelindustry.voidheart.common.content.kleinstar.KleinStarItem;
import net.voxelindustry.voidheart.common.energy.storage.RubrumContent;
import net.voxelindustry.voidheart.common.setup.VoidHeartEntities;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;

import java.util.UUID;

import static net.voxelindustry.voidheart.common.content.armor.ArmorGridKind.CORE;
import static net.voxelindustry.voidheart.common.content.armor.ArmorGridKind.LIMBS;
import static net.voxelindustry.voidheart.common.content.armor.ArmorModule.BATTERY_CELL;
import static net.voxelindustry.voidheart.common.content.armor.ArmorModule.EXOSKELETON_POWER;
import static net.voxelindustry.voidheart.gametest.GameTestKit.FLAT_FEET;
import static net.voxelindustry.voidheart.gametest.GameTestKit.expect;
import static net.voxelindustry.voidheart.gametest.GameTestKit.player;
import static net.voxelindustry.voidheart.gametest.GameTestKit.rightClick;
import static net.voxelindustry.voidheart.gametest.GameTestKit.spawn;

/**
 * The Void Armor out on a sortie. It walks a corridor three blocks high and stops at a gap two high, the mace, crossbow, rapier and Klein Star all work from
 * its racks, a trip to the Nether and back leaves it on with its hotbar, and so does saving and reopening the world, after which stepping out still gives
 * the wearer their own hotbar back.
 */
public class VoidArmorTripsClientTest implements FabricClientGameTest
{
    private static final int CORRIDOR_Z = 10;
    private static final int LOW_FROM   = 11;
    private static final int STURDY     = 200;

    @Override
    public void runTest(ClientGameTestContext context)
    {
        TestWorldSave save;
        try (var world = context.worldBuilder().create())
        {
            save = world.getWorldSave();
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            server.runCommand("time set noon");
            server.runCommand("gamerule advance_time false");
            server.runCommand("gamerule spawn_mobs false");
            server.runCommand("gamemode survival @a");
            server.runCommand("clear @a");
            server.runCommand("item replace entity @a hotbar.0 with minecraft:dirt 5");
            server.runCommand("item replace entity @a inventory.10 with minecraft:arrow 64");
            server.runCommand("tp @a 0.5 " + FLAT_FEET + " 0.5 0 0");
            context.waitTicks(10);
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));

            server.runOnServer(minecraft -> ArmorWear.stepIn(player(minecraft), frame(), player(minecraft).position()));
            context.waitTicks(ArmorBalance.STEP_IN_TICKS + 5);
            expectWorn(server, "after stepping in");

            corridor(context, server);
            mace(context, server);
            crossbow(context, server);
            rapier(context, server);
            kleinStar(context, server);
            nether(context, server);
        }

        try (var world = save.open())
        {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            context.waitTicks(10);
            reopened(context, server);
        }
    }

    private static ItemStack frame()
    {
        var grids = ArmorGrids.EMPTY
                .with(LIMBS, new Placement(EXOSKELETON_POWER, 0, 0, 0))
                .with(CORE, new Placement(BATTERY_CELL, 0, 0, 0));
        var frame = new ItemStack(VoidArmorContent.VOID_ARMOR);
        frame.set(VoidArmorContent.ARMOR_GRIDS, grids);
        frame.set(RubrumContent.VOID_CHARGE, grids.stats().capacity());
        var star = new ItemStack(VoidHeartItems.KLEIN_STAR);
        KleinStarItem.setCharge(star, KleinStarBalance.CAPACITY);
        var slots = NonNullList.withSize(ArmorBalance.FRAME_SLOTS, ItemStack.EMPTY);
        slots.set(0, new ItemStack(Items.MACE));
        slots.set(1, new ItemStack(Items.CROSSBOW));
        slots.set(2, new ItemStack(VoidHeartItems.VOID_BLIGHT_RAPIER));
        slots.set(3, star);
        frame.set(VoidArmorContent.ARMOR_SLOTS, ItemContainerContents.fromItems(slots));
        return frame;
    }

    private static void expectWorn(TestServerContext server, String when)
    {
        var phase = server.computeOnServer(minecraft ->
        {
            var worn = player(minecraft).getAttached(ArmorWear.WORN);
            return worn == null ? null : worn.phase();
        });
        double scale = server.computeOnServer(minecraft -> player(minecraft).getAttributeValue(Attributes.SCALE));
        boolean racks = server.computeOnServer(minecraft -> player(minecraft).getInventory().getItem(0).is(Items.MACE)
                                                            && player(minecraft).getAttached(ArmorHotbar.STASH) != null);
        expect(phase == WornArmor.Phase.WORN && Math.abs(scale - ArmorBalance.SCALE) < 1E-6 && racks,
                "Expected the frame on, at its size and with its racks in the hotbar " + when + ": phase " + phase + ", scale " + scale + ", racks " + racks);
    }

    private static void select(ClientGameTestContext context, int rack)
    {
        context.getInput().pressKey(options -> options.keyHotbarSlots[rack]);
        context.waitTicks(10);
    }

    /**
     * Walking along a corridor three high it goes on, and the frame stops where the ceiling drops to two.
     */
    private static void corridor(ClientGameTestContext context, TestServerContext server)
    {
        int top = FLAT_FEET + 3;
        server.runCommand("fill -4 " + FLAT_FEET + " " + (CORRIDOR_Z - 1) + " 20 " + top + " " + (CORRIDOR_Z + 1) + " stone_bricks");
        server.runCommand("fill -4 " + FLAT_FEET + " " + CORRIDOR_Z + " 20 " + (top - 1) + " " + CORRIDOR_Z + " air");
        server.runCommand("fill " + LOW_FROM + " " + (top - 1) + " " + CORRIDOR_Z + " 20 " + (top - 1) + " " + CORRIDOR_Z + " stone_bricks");
        server.runCommand("execute as @a run tp @s -3.5 " + FLAT_FEET + " " + (CORRIDOR_Z + 0.5) + " -90 0");
        context.waitTicks(5);
        context.getInput().holdKey(options -> options.keyUp);
        context.waitTicks(80);
        context.getInput().releaseKey(options -> options.keyUp);
        context.waitTicks(5);
        double x = server.computeOnServer(minecraft -> player(minecraft).getX());
        expect(x > LOW_FROM - 3 && x < LOW_FROM - ArmorBalance.WEARER_WIDTH / 2 + 0.01,
                "Expected the frame to walk the three-high corridor and stop where it drops to two, stopped at x " + x);
        server.runCommand("fill -4 " + FLAT_FEET + " " + (CORRIDOR_Z - 1) + " 20 " + top + " " + (CORRIDOR_Z + 1) + " air");
        server.runCommand("execute as @a run tp @s 0.5 " + FLAT_FEET + " 0.5 0 0");
        context.waitTicks(5);
    }

    private static UUID sturdy(MinecraftServer minecraft, double x, double z)
    {
        var husk = spawn(minecraft, EntityTypes.HUSK, x, FLAT_FEET, z);
        husk.setNoAi(true);
        husk.getAttribute(Attributes.MAX_HEALTH).setBaseValue(STURDY);
        husk.setHealth(STURDY);
        husk.setPersistenceRequired();
        return husk.getUUID();
    }

    private static LivingEntity living(MinecraftServer minecraft, UUID id)
    {
        if (!(minecraft.overworld().getEntity(id) instanceof LivingEntity living))
            throw new AssertionError("Expected the target alive");
        return living;
    }

    /**
     * The mace in the first rack lands a smash from a fall, which only works if the swing reads the rack as the hand.
     */
    private static void mace(ClientGameTestContext context, TestServerContext server)
    {
        select(context, 0);
        float taken = server.computeOnServer(minecraft ->
        {
            var target = living(minecraft, sturdy(minecraft, 0.5, 3.5));
            var player = player(minecraft);
            player.fallDistance = 5;
            player.attack(target);
            float damage = STURDY - target.getHealth();
            target.discard();
            player.fallDistance = 0;
            return damage;
        });
        // A plain mace hit with the frame's power is under 10; a five-block smash adds more than that
        expect(taken > 18, "Expected the racked mace to smash from a fall, dealt " + taken);
    }

    /**
     * The crossbow in the second rack loads an arrow from the inventory and fires it.
     */
    private static void crossbow(ClientGameTestContext context, TestServerContext server)
    {
        select(context, 1);
        server.runCommand("execute as @a run tp @s 0.5 " + FLAT_FEET + " 0.5 0 -10");
        context.getInput().holdMouse(InputConstants.MOUSE_BUTTON_RIGHT);
        context.waitTicks(35);
        context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_RIGHT);
        context.waitTicks(5);
        rightClick(context);
        context.waitTicks(5);
        int arrows = server.computeOnServer(minecraft -> player(minecraft).getInventory().countItem(Items.ARROW));
        int flying = server.computeOnServer(minecraft -> minecraft.overworld().getEntitiesOfClass(AbstractArrow.class,
                player(minecraft).getBoundingBox().inflate(64), arrow -> arrow.getOwner() == player(minecraft)).size());
        expect(arrows == 63 && flying >= 1, "Expected the racked crossbow to load from the inventory and fire, arrows left " + arrows + ", flying " + flying);
        server.runCommand("kill @e[type=arrow]");
    }

    /**
     * The rapier in the third rack punctures with a charged thrust, through real clicks.
     */
    private static void rapier(ClientGameTestContext context, TestServerContext server)
    {
        select(context, 2);
        var target = server.computeOnServer(minecraft -> sturdy(minecraft, 0.5, 3.5));
        // From the frame's eyes, half again as high as a player's, down at the husk's chest
        double eye = server.computeOnServer(minecraft -> player(minecraft).getEyeHeight());
        double pitch = Math.toDegrees(Math.atan2(eye - 1.1, 3));
        server.runCommand(String.format(java.util.Locale.ROOT, "execute as @a run tp @s 0.5 %d 0.5 0 %.3f", FLAT_FEET, pitch));
        context.waitTicks(20);
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        context.waitTicks(3);
        int open = server.computeOnServer(minecraft ->
        {
            var husk = living(minecraft, target);
            long now = minecraft.overworld().getGameTime();
            return RapierStrikes.of(husk).wounds().values().stream().mapToInt(wound -> wound.open(now)).sum();
        });
        expect(open == 1, "Expected the racked rapier's charged thrust to leave a puncture, got " + open);
        server.runOnServer(minecraft -> living(minecraft, target).discard());
        server.runCommand("execute as @a run tp @s 0.5 " + FLAT_FEET + " 0.5 0 -10");
        context.waitTicks(5);
    }

    /**
     * The Klein Star in the fourth rack opens a star and burns its Contravoid.
     */
    private static void kleinStar(ClientGameTestContext context, TestServerContext server)
    {
        select(context, 3);
        rightClick(context);
        context.waitTicks(5);
        int stars = server.computeOnServer(minecraft -> minecraft.overworld().getEntities(VoidHeartEntities.KLEIN_STAR, star -> true).size());
        int charge = server.computeOnServer(minecraft -> KleinStarItem.charge(player(minecraft).getMainHandItem()));
        expect(stars == 1 && charge < KleinStarBalance.CAPACITY, "Expected the racked Klein Star to open a star, stars " + stars + ", Contravoid " + charge);
        // Let it burst and settle before moving on
        context.waitTicks(120);
        server.runCommand("kill @e[type=!player]");
        server.runOnServer(GameTestKit::heal);
        select(context, 0);
    }

    private static void nether(ClientGameTestContext context, TestServerContext server)
    {
        server.runCommand("execute in minecraft:the_nether run forceload add 0 0");
        server.runCommand("execute in minecraft:the_nether run fill -2 99 -2 2 99 2 obsidian");
        server.runCommand("execute in minecraft:the_nether run fill -2 100 -2 2 103 2 air");
        server.runCommand("execute in minecraft:the_nether run tp @a 0.5 100 0.5");
        context.waitTicks(40);
        var dimension = server.computeOnServer(minecraft -> player(minecraft).level().dimension());
        expect(dimension == Level.NETHER, "Expected the wearer in the Nether, in " + dimension);
        expectWorn(server, "in the Nether");
        boolean clientWorn = context.computeOnClient(minecraft -> ArmorWear.wearing(minecraft.player));
        expect(clientWorn, "Expected the client to know the frame is on after the trip");

        server.runCommand("execute in minecraft:overworld run tp @a 0.5 " + FLAT_FEET + " 0.5");
        context.waitTicks(40);
        expectWorn(server, "back in the overworld");
    }

    private static void reopened(ClientGameTestContext context, TestServerContext server)
    {
        expectWorn(server, "after the world was saved and reopened");
        boolean clientWorn = context.computeOnClient(minecraft -> ArmorWear.wearing(minecraft.player));
        expect(clientWorn, "Expected the client to know the frame is on after reopening");

        context.getInput().holdKey(options -> options.keyShift);
        context.waitTicks(ArmorBalance.STEP_OUT_HOLD_TICKS + 2);
        context.getInput().releaseKey(options -> options.keyShift);
        context.waitTicks(ArmorBalance.STEP_OUT_TICKS + 5);
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            expect(ArmorWear.worn(player) == null, "Expected the wearer out after reopening");
            expect(player.getInventory().getItem(0).is(Items.DIRT) && player.getAttached(ArmorHotbar.STASH) == null,
                    "Expected the wearer's own hotbar back after reopening, slot 0 holds " + player.getInventory().getItem(0));
        });
    }
}
