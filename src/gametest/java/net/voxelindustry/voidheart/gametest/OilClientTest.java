package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.Arrow;
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
import net.voxelindustry.voidheart.common.setup.VoidHeartDataComponents;
import net.voxelindustry.voidheart.common.setup.VoidHeartEntities;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;

/**
 * Searing Oil coats a sword through real inventory clicks. The coated sword's hits count only as fire for a Dragoon, set a zombie alight, and use
 * a charge each, while an uncoated sword still counts as a blade. Concussive and Arcane Oil count as blunt and magic, throw a zombie further, break off
 * a Bishop's lance and strip a blessing. A flask coats sixteen arrows, and an oiled arrow counts as its oil.
 */
public class OilClientTest implements FabricClientGameTest
{
    private static final int GROUND_Y = -60;
    private static final int SHOT_DRAW_TICKS = 25;
    private static final int SHOT_FLIGHT_TICKS = 20;
    /**
     * Menu slots of the first two hotbar slots in the player's own inventory menu.
     */
    private static final int SWORD_SLOT = 36;
    private static final int OIL_SLOT   = 37;
    private static final int ARROW_SLOT = 40;
    private static final int ARCANE_SLOT = 41;
    private static final int ARROWS     = 20;
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
            server.runCommand("item replace entity @a hotbar.4 with arrow " + ARROWS);
            server.runCommand("item replace entity @a hotbar.5 with voidheart:arcane_oil");
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
                // A flask on a stack of arrows coats a flask's worth of them
                minecraft.gameMode.handleContainerInput(menu.containerId, ARCANE_SLOT, CLICK_PRIMARY, ContainerInput.PICKUP, minecraft.player);
                minecraft.gameMode.handleContainerInput(menu.containerId, ARROW_SLOT, CLICK_SECONDARY, ContainerInput.PICKUP, minecraft.player);
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
            server.runOnServer(minecraft ->
            {
                var inventory = player(minecraft).getInventory();
                var plain = inventory.getItem(4);
                expect(plain.is(Items.ARROW) && plain.getCount() == ARROWS - OilCoating.ARROWS_PER_FLASK && OilCoating.coatingOf(plain) == null,
                        "Expected the arrows past a flask's worth left plain in their slot, got " + plain + " " + plain.getComponents());
                int oiled = 0;
                for (int slot = 0; slot < inventory.getContainerSize(); slot++)
                {
                    var stack = inventory.getItem(slot);
                    var coating = OilCoating.coatingOf(stack);
                    if (stack.is(Items.ARROW) && coating != null)
                    {
                        expect(coating.type() == OilType.ARCANE && coating.charges() == 1, "Expected arrows coated with arcane oil for one hit, got " + coating);
                        oiled += stack.getCount();
                    }
                }
                expect(oiled == OilCoating.ARROWS_PER_FLASK, "Expected " + OilCoating.ARROWS_PER_FLASK + " oiled arrows, got " + oiled);
                expect(player(minecraft).containerMenu.getCarried().isEmpty(), "Expected the arcane flask used up, carrying " + player(minecraft).containerMenu.getCarried());
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
                player.getInventory().setItem(9, worn);
                player.getInventory().setItem(3, new ItemStack(Items.IRON_SWORD));
                var concussive = new ItemStack(Items.IRON_AXE);
                OilCoating.coat(concussive, OilType.CONCUSSIVE);
                player.getInventory().setItem(10, concussive);
                var arcane = new ItemStack(Items.GOLDEN_SWORD);
                OilCoating.coat(arcane, OilType.ARCANE);
                player.getInventory().setItem(11, arcane);
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
            otherOils(server);
            arrows(context, server);
            realShots(context, server);

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
            for (var oil : new OilType[]{ OilType.CONCUSSIVE, OilType.ARCANE })
            {
                server.runOnServer(minecraft -> OilCoating.coat(player(minecraft).getMainHandItem(), oil));
                context.waitTicks(10);
                context.getInput().pressKey(options -> options.keyAttack);
                context.waitTicks(2);
                context.takeScreenshot("oil-" + oil.getSerializedName() + "-third-person-swing");
            }
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
        }
    }

    /**
     * A sword coated with concussive or arcane oil counts as blunt or magic, and does what its oil does to anything but the Dragoon.
     */
    private static void otherOils(TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            var player = player(minecraft);
            player.teleportTo(0.5, GROUND_Y, 0.5);
            var sword = new ItemStack(Items.IRON_SWORD);
            player.getInventory().setItem(8, sword);
            player.getInventory().setSelectedSlot(8);

            var dragoon = VoidHeartEntities.KERNACIAN_DRAGOON.create(level, EntitySpawnReason.COMMAND);
            dragoon.setNoAi(true);
            dragoon.snapTo(0.5, GROUND_Y, 3.5, 0, 0);
            level.addFreshEntity(dragoon);
            OilCoating.coat(sword, OilType.CONCUSSIVE);
            attack(player, dragoon);
            expect(DragoonAdaptation.count(dragoon.adaptation(), Kind.BLUNT) == 1, "Expected a concussive sword's hit to count as blunt");
            expect(DragoonAdaptation.count(dragoon.adaptation(), Kind.BLADE) == 0, "Expected a concussive sword's hit not to count as a blade");
            OilCoating.coat(sword, OilType.ARCANE);
            attack(player, dragoon);
            expect(DragoonAdaptation.count(dragoon.adaptation(), Kind.MAGIC) == 1, "Expected an arcane sword's hit to count as magic");
            dragoon.discard();

            // Concussive: the same blow throws a zombie much further
            sword.remove(VoidHeartDataComponents.WEAPON_OIL);
            double plain = knockedBy(player, level);
            OilCoating.coat(sword, OilType.CONCUSSIVE);
            double concussed = knockedBy(player, level);
            System.out.println("[Oil] a zombie was thrown at " + plain + " plain and " + concussed + " concussed");
            expect(concussed > plain * 1.8, "Expected a concussive hit to throw much harder, " + concussed + " against " + plain);

            // Concussive: a Bishop charging its lance loses it
            var bishop = VoidHeartEntities.FALLEN_BISHOP.create(level, EntitySpawnReason.COMMAND);
            bishop.setNoAi(true);
            bishop.snapTo(3.5, GROUND_Y, 0.5, 0, 0);
            level.addFreshEntity(bishop);
            bishop.showLance(player.position(), 6);
            attack(player, bishop);
            expect(bishop.lanceTicks() == 0, "Expected a concussive hit to break off a charging lance, at " + bishop.lanceTicks());
            bishop.discard();

            // Arcane: one blessing goes, the harmful effect stays
            OilCoating.coat(sword, OilType.ARCANE);
            var blessed = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
            blessed.setNoAi(true);
            blessed.snapTo(3.5, GROUND_Y, 0.5, 0, 0);
            level.addFreshEntity(blessed);
            blessed.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 1200));
            blessed.addEffect(new MobEffectInstance(MobEffects.SPEED, 1200));
            blessed.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 1200));
            expect(blessed.getActiveEffects().size() == 3, "Expected the zombie to take all three effects, got " + blessed.getActiveEffects());
            attack(player, blessed);
            long blessings = blessed.getActiveEffects().stream().filter(effect -> effect.getEffect().value().isBeneficial()).count();
            expect(blessings == 1, "Expected an arcane hit to strip one blessing, " + blessings + " left of " + blessed.getActiveEffects());
            expect(blessed.hasEffect(MobEffects.SLOWNESS), "Expected an arcane hit to leave harmful effects");
            blessed.discard();
            player.getInventory().setItem(8, ItemStack.EMPTY);
            player.getInventory().setSelectedSlot(0);
        });
    }

    /**
     * @return how fast the player's blow sent a fresh zombie away
     */
    private static double knockedBy(ServerPlayer player, ServerLevel level)
    {
        var zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
        zombie.setNoAi(true);
        zombie.snapTo(0.5, GROUND_Y, 2.5, 0, 0);
        level.addFreshEntity(zombie);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, zombie.getEyePosition());
        attack(player, zombie);
        double speed = zombie.getDeltaMovement().horizontalDistance();
        zombie.discard();
        return speed;
    }

    /**
     * An oiled arrow counts as its oil rather than a projectile and carries its effect.
     */
    private static void arrows(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            var dragoon = VoidHeartEntities.KERNACIAN_DRAGOON.create(level, EntitySpawnReason.COMMAND);
            dragoon.setNoAi(true);
            dragoon.snapTo(0.5, GROUND_Y, 6.5, 0, 0);
            level.addFreshEntity(dragoon);
            var zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
            zombie.setNoAi(true);
            zombie.snapTo(6.5, GROUND_Y, 0.5, 0, 0);
            level.addFreshEntity(zombie);
            shoot(player(minecraft), OilType.SEARING, dragoon);
            shoot(player(minecraft), OilType.SEARING, zombie);
        });
        context.waitTicks(20);
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            var dragoon = level.getEntities(VoidHeartEntities.KERNACIAN_DRAGOON, entity -> true).getFirst();
            int packed = dragoon.adaptation();
            expect(DragoonAdaptation.count(packed, Kind.FIRE) == 1, "Expected a searing arrow to count as fire, got " + DragoonAdaptation.count(packed, Kind.FIRE));
            expect(DragoonAdaptation.count(packed, Kind.PROJECTILE) == 0, "Expected a searing arrow not to count as a projectile too");
            var zombie = level.getEntities(EntityTypes.ZOMBIE, entity -> true).getFirst();
            expect(zombie.getHealth() < zombie.getMaxHealth(), "Expected the arrow to hit the zombie");
            expect(zombie.isOnFire(), "Expected a searing arrow to set the zombie alight");
            dragoon.discard();
            zombie.discard();
        });
    }

    /**
     * Through real input: a bow drawn and loosed with oiled arrows in the offhand shoots an oiled arrow, and a thrown coated trident keeps its coat.
     */
    private static void realShots(ClientGameTestContext context, TestServerContext server)
    {
        var held = server.computeOnServer(minecraft -> player(minecraft).getMainHandItem().copy());
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            var zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
            zombie.setNoAi(true);
            zombie.snapTo(6.5, GROUND_Y, 0.5, 0, 0);
            level.addFreshEntity(zombie);
            var dragoon = VoidHeartEntities.KERNACIAN_DRAGOON.create(level, EntitySpawnReason.COMMAND);
            dragoon.setNoAi(true);
            dragoon.snapTo(0.5, GROUND_Y, 3.5, 0, 0);
            level.addFreshEntity(dragoon);

            var player = player(minecraft);
            var arrows = new ItemStack(Items.ARROW, 4);
            OilCoating.coatArrows(arrows, OilType.SEARING);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOW));
            player.setItemInHand(InteractionHand.OFF_HAND, arrows);
        });
        server.runCommand("tp @a 0.5 " + GROUND_Y + " 0.5 facing 6.5 " + (GROUND_Y + 1.2) + " 0.5");
        context.waitTicks(5);
        context.getInput().holdKeyFor(options -> options.keyUse, SHOT_DRAW_TICKS);
        context.waitTicks(SHOT_FLIGHT_TICKS);
        server.runOnServer(minecraft ->
        {
            var zombie = minecraft.overworld().getEntities(EntityTypes.ZOMBIE, entity -> true).getFirst();
            expect(zombie.getHealth() < zombie.getMaxHealth(), "Expected the bow's arrow to hit the zombie");
            expect(zombie.isOnFire(), "Expected an arrow loosed from a bow with searing arrows to set the zombie alight");
            zombie.discard();

            var trident = new ItemStack(Items.TRIDENT);
            OilCoating.coat(trident, OilType.SEARING);
            var player = player(minecraft);
            player.setItemInHand(InteractionHand.MAIN_HAND, trident);
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        });
        server.runCommand("tp @a 0.5 " + GROUND_Y + " 0.5 facing 0.5 " + (GROUND_Y + 1.2) + " 3.5");
        context.waitTicks(5);
        context.getInput().holdKeyFor(options -> options.keyUse, SHOT_DRAW_TICKS);
        context.waitTicks(SHOT_FLIGHT_TICKS);
        server.runOnServer(minecraft ->
        {
            var dragoon = minecraft.overworld().getEntities(VoidHeartEntities.KERNACIAN_DRAGOON, entity -> true).getFirst();
            int packed = dragoon.adaptation();
            var thrown = minecraft.overworld().getEntities(EntityTypes.TRIDENT, entity -> true).stream()
                    .map(trident -> trident.position() + " " + OilCoating.coatingOf(trident.getPickupItemStackOrigin())).toList();
            expect(DragoonAdaptation.count(packed, Kind.FIRE) == 1, "Expected a thrown searing trident to count as fire, got " + DragoonAdaptation.count(packed, Kind.FIRE)
                    + ", projectile " + DragoonAdaptation.count(packed, Kind.PROJECTILE) + ", health " + dragoon.getHealth() + "/" + dragoon.getMaxHealth() + " at "
                    + dragoon.position() + ", tridents " + thrown + ", in hand " + player(minecraft).getMainHandItem() + ", pitch " + player(minecraft).getXRot());
            expect(DragoonAdaptation.count(packed, Kind.PROJECTILE) == 0, "Expected a thrown searing trident not to count as a projectile");
            dragoon.discard();
            minecraft.overworld().getEntities(EntityTypes.TRIDENT, entity -> true).forEach(net.minecraft.world.entity.Entity::discard);
            minecraft.overworld().getEntities(EntityTypes.ARROW, entity -> true).forEach(net.minecraft.world.entity.Entity::discard);
            player(minecraft).setItemInHand(InteractionHand.MAIN_HAND, held);
        });
    }

    private static void shoot(ServerPlayer player, OilType oil, LivingEntity target)
    {
        var level = (ServerLevel) player.level();
        var stack = new ItemStack(Items.ARROW);
        OilCoating.coatArrows(stack, oil);
        var arrow = new Arrow(level, player, stack, null);
        arrow.setPos(player.getEyePosition());
        var to = target.getBoundingBox().getCenter().subtract(player.getEyePosition());
        arrow.shoot(to.x, to.y, to.z, 2.5F, 0);
        level.addFreshEntity(arrow);
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
