package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantments;
import net.voxelindustry.voidheart.common.content.mob.DragoonAdaptation;
import net.voxelindustry.voidheart.common.content.mob.DragoonAdaptation.Kind;
import net.voxelindustry.voidheart.common.content.mob.KernacianDragoon;
import net.voxelindustry.voidheart.common.content.oil.OilCoating;
import net.voxelindustry.voidheart.common.content.oil.OilType;
import net.voxelindustry.voidheart.common.setup.VoidHeartEntities;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;

/**
 * Searing Oil coats a sword through real inventory clicks. The coated sword's hits count only as fire for a Dragoon, set a zombie alight, and use
 * a charge each, while an uncoated sword still counts as a blade.
 */
public class OilClientTest implements FabricClientGameTest
{
    private static final int GROUND_Y = -60;
    /**
     * Menu slots of the first two hotbar slots in the player's own inventory menu.
     */
    private static final int SWORD_SLOT = 36;
    private static final int OIL_SLOT   = 37;
    /**
     * Button numbers a container click carries, which a menu reads as primary and secondary. They are not the mouse button codes.
     */
    private static final int CLICK_PRIMARY   = 0;
    private static final int CLICK_SECONDARY = 1;

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
            server.runCommand("clear @a");
            server.runCommand("item replace entity @a hotbar.0 with iron_sword");
            server.runCommand("item replace entity @a hotbar.1 with voidheart:searing_oil 2");
            context.waitTicks(10);
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));

            // Pick up a flask and use it on the sword, as a player would with the inventory open
            context.getInput().pressKey(options -> options.keyInventory);
            context.waitTicks(5);
            context.runOnClient(minecraft ->
            {
                var menu = minecraft.player.inventoryMenu;
                minecraft.gameMode.handleContainerInput(menu.containerId, OIL_SLOT, CLICK_PRIMARY, ContainerInput.PICKUP, minecraft.player);
                minecraft.gameMode.handleContainerInput(menu.containerId, SWORD_SLOT, CLICK_SECONDARY, ContainerInput.PICKUP, minecraft.player);
                minecraft.gameMode.handleContainerInput(menu.containerId, OIL_SLOT, CLICK_PRIMARY, ContainerInput.PICKUP, minecraft.player);
            });
            context.waitTicks(5);

            server.runOnServer(minecraft ->
            {
                var player = player(minecraft);
                var sword = player.getInventory().getItem(0);
                var coating = OilCoating.coatingOf(sword);
                expect(coating != null && coating.type() == OilType.SEARING, "Expected the sword coated with searing oil, got " + sword.getComponents());
                expect(coating.charges() == OilType.SEARING.charges(), "Expected a fresh coat of " + OilType.SEARING.charges() + " charges, got " + coating.charges());
                expect(player.getInventory().getItem(1).is(VoidHeartItems.SEARING_OIL) && player.getInventory().getItem(1).getCount() == 1,
                        "Expected one flask used, the other back in its slot, got " + player.getInventory().getItem(1));
                // The bottle lands in the flask's emptied slot, so putting the other flask back there picks it up
                expect(player.getInventory().contains(stack -> stack.is(Items.GLASS_BOTTLE)) || player.containerMenu.getCarried().is(Items.GLASS_BOTTLE),
                        "Expected the empty bottle given back");
            });
            context.computeOnClient(minecraft ->
            {
                var lines = minecraft.player.getInventory().getItem(0).getTooltipLines(net.minecraft.world.item.Item.TooltipContext.of(minecraft.level),
                        minecraft.player, TooltipFlag.NORMAL);
                expect(lines.stream().anyMatch(line -> line.getString().contains("Searing Oil") && line.getString().contains(String.valueOf(OilType.SEARING.charges()))),
                        "Expected the coating in the sword's tooltip, got " + lines);
                return null;
            });

            // A worn, enchanted sword shows the drop, the charges above its durability, and its glint
            server.runOnServer(minecraft ->
            {
                var player = player(minecraft);
                var worn = new ItemStack(Items.DIAMOND_SWORD);
                worn.setDamageValue(900);
                worn.enchant(minecraft.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS), 3);
                OilCoating.coat(worn, OilType.SEARING);
                player.getInventory().setItem(2, worn);
                player.getInventory().setItem(3, new ItemStack(Items.IRON_SWORD));
            });
            context.waitTicks(5);
            context.takeScreenshot(TestScreenshotOptions.of("oil-inventory").withSize(1280, 720));
            context.getInput().pressKey(InputConstants.KEY_ESCAPE);
            context.waitTicks(5);

            server.runOnServer(minecraft ->
            {
                var level = minecraft.overworld();
                var player = player(minecraft);
                player.teleportTo(0.5, GROUND_Y, 0.5);
                player.getInventory().setSelectedSlot(0);

                var dragoon = VoidHeartEntities.KERNACIAN_DRAGOON.create(level, EntitySpawnReason.COMMAND);
                dragoon.setNoAi(true);
                dragoon.snapTo(0.5, GROUND_Y, 3.5, 0, 0);
                level.addFreshEntity(dragoon);

                attack(player, dragoon);
                int packed = dragoon.adaptation();
                expect(DragoonAdaptation.count(packed, Kind.FIRE) == 1, "Expected the coated hit to count as fire, got " + DragoonAdaptation.count(packed, Kind.FIRE));
                expect(DragoonAdaptation.count(packed, Kind.BLADE) == 0, "Expected the coated hit not to count as a blade too");
                expect(!dragoon.isOnFire(), "Expected the Dragoon not set alight, its burning would teach it fire again");
                expect(OilCoating.coatingOf(player.getMainHandItem()).charges() == OilType.SEARING.charges() - 1, "Expected the hit to use a charge");

                var zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
                zombie.setNoAi(true);
                zombie.snapTo(3.5, GROUND_Y, 0.5, 0, 0);
                level.addFreshEntity(zombie);
                attack(player, zombie);
                expect(zombie.isOnFire(), "Expected the coated hit to set the zombie alight");
                expect(OilCoating.coatingOf(player.getMainHandItem()).charges() == OilType.SEARING.charges() - 2, "Expected a charge per landed hit");
                zombie.discard();

                // Uncoated, a sword is a blade again
                player.getInventory().setSelectedSlot(3);
                attack(player, dragoon);
                expect(DragoonAdaptation.count(dragoon.adaptation(), Kind.BLADE) == 1, "Expected an uncoated sword to count as a blade");
                dragoon.discard();

                // The last charge wears the coat off
                player.getInventory().setSelectedSlot(0);
                var sword = player.getMainHandItem();
                OilCoating.coat(sword, OilType.SEARING);
                for (int hit = 0; hit < OilType.SEARING.charges(); hit++)
                {
                    var target = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
                    target.setNoAi(true);
                    target.snapTo(3.5, GROUND_Y, 0.5, 0, 0);
                    level.addFreshEntity(target);
                    attack(player, target);
                    target.discard();
                }
                expect(OilCoating.coatingOf(sword) == null, "Expected the coat to wear off after its last charge");
                OilCoating.coat(sword, OilType.SEARING);
            });

            // Embers off the held blade, in first person and from the front
            server.runCommand("tp @a 0.5 " + GROUND_Y + " 0.5 0 10");
            context.waitTicks(10);
            context.getInput().pressKey(options -> options.keyAttack);
            context.waitTicks(2);
            context.takeScreenshot("oil-embers-first-person-swing");
            context.waitTicks(30);
            context.takeScreenshot("oil-embers-first-person-idle");
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
            context.waitTicks(10);
            context.getInput().pressKey(options -> options.keyAttack);
            context.waitTicks(2);
            context.takeScreenshot("oil-embers-third-person-swing");
            context.waitTicks(30);
            context.takeScreenshot("oil-embers-third-person-idle");
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
        }
    }

    private static void attack(ServerPlayer player, LivingEntity target)
    {
        target.setInvulnerableTime(0);
        set(target, LivingEntity.class, "damageCooldownTime", 0);
        player.resetAttackStrengthTicker();
        player.attack(target);
    }

    private static void set(Object target, Class<?> owner, String name, int value)
    {
        try
        {
            var field = owner.getDeclaredField(name);
            field.setAccessible(true);
            field.setInt(target, value);
        }
        catch (ReflectiveOperationException e)
        {
            throw new AssertionError(e);
        }
    }

    private static ServerPlayer player(MinecraftServer minecraft)
    {
        return minecraft.getPlayerList().getPlayers().getFirst();
    }

    private static void expect(boolean condition, String message)
    {
        if (!condition)
            throw new AssertionError(message);
    }
}
