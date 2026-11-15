package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantments;
import net.voxelindustry.voidheart.client.render.sin.SinFlare;
import net.voxelindustry.voidheart.common.content.altar.VoidAltarTile;
import net.voxelindustry.voidheart.common.content.apparatus.ApparatusComponent;
import net.voxelindustry.voidheart.common.content.apparatus.ApparatusGrid;
import net.voxelindustry.voidheart.common.content.pillar.VoidPillarTile;
import net.voxelindustry.voidheart.common.content.repair.ExperienceSkullTile;
import net.voxelindustry.voidheart.common.content.sin.Sin;
import net.voxelindustry.voidheart.common.content.sin.SinBalance;
import net.voxelindustry.voidheart.common.content.sin.SinCombat;
import net.voxelindustry.voidheart.common.content.sin.SinProficiencies;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartDataComponents;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;

import java.util.List;

import static net.voxelindustry.voidheart.gametest.GameTestKit.FLAT_FEET;
import static net.voxelindustry.voidheart.gametest.GameTestKit.aimFrom;
import static net.voxelindustry.voidheart.gametest.GameTestKit.clearArea;
import static net.voxelindustry.voidheart.gametest.GameTestKit.clearScene;
import static net.voxelindustry.voidheart.gametest.GameTestKit.expect;
import static net.voxelindustry.voidheart.gametest.GameTestKit.expectClient;
import static net.voxelindustry.voidheart.gametest.GameTestKit.freezeTime;
import static net.voxelindustry.voidheart.gametest.GameTestKit.player;
import static net.voxelindustry.voidheart.gametest.GameTestKit.setIntField;
import static net.voxelindustry.voidheart.gametest.GameTestKit.spawn;
import static net.voxelindustry.voidheart.gametest.GameTestKit.useOn;
import static net.voxelindustry.voidheart.gametest.GameTestKit.waitFor;

/**
 * A sin is fused into an enchanted sword at the altar and salted back out, an apparatus is raised a tier keeping its grid, each of the five sins gives and
 * takes in a fight, and the inlay and the tooltip's lore are captured.
 */
public class SinInfusionClientTest implements FabricClientGameTest
{
    private static final int      X      = 60;
    private static final BlockPos ALTAR  = new BlockPos(X, FLAT_FEET, 0);
    private static final BlockPos SKULL  = ALTAR.offset(2, 0, 2);
    private static final List<BlockPos> PILLARS = List.of(
            new BlockPos(-1, 0, 3), new BlockPos(1, 0, 3), new BlockPos(1, 0, -3), new BlockPos(-1, 0, -3),
            new BlockPos(3, 0, -1), new BlockPos(3, 0, 1), new BlockPos(-3, 0, -1), new BlockPos(-3, 0, 1));

    private static final double ARENA_X = X + 12;
    private static final float  RELATIVE_TOLERANCE = 0.03F;

    @Override
    public void runTest(ClientGameTestContext context)
    {
        try (var world = context.worldBuilder().create())
        {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            freezeTime(server, "noon");
            server.runCommand("gamemode survival @a");
            server.runCommand("difficulty peaceful");
            server.runCommand("execute as @a run voidheart research grant all");
            clearArea(server, X);
            clearArea(server, ARENA_X);
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
            server.runCommand("tp @a " + (X + 0.5) + " " + FLAT_FEET + " " + -4.5 + " 0 20");
            context.waitTicks(10);

            buildAltar(server);
            infuseAtAltar(context, server);
            cleanseWithSalt(context, server);
            raiseApparatus(context, server);

            server.runCommand("difficulty normal");
            wrath(context, server);
            pride(context, server);
            gluttony(context, server);
            sloth(context, server);
            greed(context, server);

            tooltipSpeaksLore(context, server);
            captureInlays(context, server);
            clearScene(server);
        }
    }

    // The altar

    private static void buildAltar(TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            level.setBlockAndUpdate(ALTAR, VoidHeartBlocks.VOID_ALTAR.defaultBlockState());
            for (var offset : PILLARS)
                level.setBlockAndUpdate(ALTAR.offset(offset), VoidHeartBlocks.VOID_PILLAR.defaultBlockState());
            level.setBlockAndUpdate(SKULL, VoidHeartBlocks.EXPERIENCE_SKULL.defaultBlockState());
            ((ExperienceSkullTile) level.getBlockEntity(SKULL)).setExperience(1000);
        });
    }

    private static void lay(MinecraftServer minecraft, Item... items)
    {
        var level = minecraft.overworld();
        for (int index = 0; index < PILLARS.size(); index++)
        {
            var pillar = (VoidPillarTile) level.getBlockEntity(ALTAR.offset(PILLARS.get(index)));
            pillar.setStack(index < items.length ? new ItemStack(items[index]) : ItemStack.EMPTY);
        }
    }

    private static VoidAltarTile altar(MinecraftServer minecraft)
    {
        return (VoidAltarTile) minecraft.overworld().getBlockEntity(ALTAR);
    }

    private static boolean pillarsEmpty(MinecraftServer minecraft)
    {
        return PILLARS.stream().allMatch(offset -> ((VoidPillarTile) minecraft.overworld().getBlockEntity(ALTAR.offset(offset))).getStack().isEmpty());
    }

    private static ItemStack enchantedSword(MinecraftServer minecraft)
    {
        var sword = new ItemStack(Items.IRON_SWORD);
        var sharpness = minecraft.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS);
        sword.enchant(sharpness, 3);
        sword.set(DataComponents.CUSTOM_NAME, Component.literal("Old Friend"));
        return sword;
    }

    private static int sharpness(MinecraftServer minecraft, ItemStack stack)
    {
        var sharpness = minecraft.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS);
        return stack.getEnchantments().getLevel(sharpness);
    }

    /**
     * The sword goes down through a real use of the altar, after the ingots and flask are laid.
     */
    private static void infuseAtAltar(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            lay(minecraft, VoidHeartItems.SEETHING_STEEL_INGOT, VoidHeartItems.SEETHING_STEEL_INGOT, VoidHeartItems.SEETHING_STEEL_INGOT,
                    VoidHeartItems.SEETHING_STEEL_INGOT, VoidHeartItems.SEETHING_STEEL_FLASK);
            var player = player(minecraft);
            player.setItemInHand(InteractionHand.MAIN_HAND, enchantedSword(minecraft));
            useOn(player, ALTAR);
        });
        context.waitTicks(5);
        expect(server.computeOnServer(minecraft -> altar(minecraft).isCrafting()), "Expected the altar to start the wrath infusion");
        context.takeScreenshot("sin-altar-infusing");

        waitFor(context, server, 2000, "Expected the altar to give back a wrath sword", minecraft ->
                !altar(minecraft).isCrafting() && altar(minecraft).getStack().get(VoidHeartDataComponents.SIN) == Sin.WRATH);

        server.runOnServer(minecraft ->
        {
            var sword = altar(minecraft).getStack();
            expect(sword.is(Items.IRON_SWORD), "Expected the same sword back, got " + sword);
            expect(sharpness(minecraft, sword) == 3, "Expected the sword to keep Sharpness III");
            expect("Old Friend".equals(sword.getHoverName().getString()), "Expected the sword to keep its name, got " + sword.getHoverName().getString());
            expect(pillarsEmpty(minecraft), "Expected the ingots and flask eaten");
            var skull = (ExperienceSkullTile) minecraft.overworld().getBlockEntity(SKULL);
            expect(skull.getExperience() == 1000 - 315, "Expected 315 experience drawn from the skull, it holds " + skull.getExperience());
        });
        context.waitTicks(70);
        context.takeScreenshot("sin-altar-infused");
    }

    /**
     * Salt laid after the sword already sits on the idle altar wakes it.
     */
    private static void cleanseWithSalt(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft -> lay(minecraft, VoidHeartItems.SALT, VoidHeartItems.SALT, VoidHeartItems.SALT, VoidHeartItems.SALT));
        waitFor(context, server, 200, "Expected the salt laid on the pillars to wake the idle altar", minecraft -> altar(minecraft).isCrafting());
        waitFor(context, server, 2000, "Expected the salt to take the sin out", minecraft ->
                !altar(minecraft).isCrafting() && !altar(minecraft).getStack().has(VoidHeartDataComponents.SIN));

        server.runOnServer(minecraft ->
        {
            var sword = altar(minecraft).getStack();
            expect(sword.is(Items.IRON_SWORD) && sharpness(minecraft, sword) == 3, "Expected the cleansed sword to keep Sharpness III, got " + sword);
            expect(pillarsEmpty(minecraft), "Expected the salt eaten");
        });
        context.waitTicks(70);
    }

    private static void raiseApparatus(ClientGameTestContext context, TestServerContext server)
    {
        var grid = ApparatusGrid.EMPTY.with(new ApparatusGrid.Placement(ApparatusComponent.CELL, 0, 0, 0));
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            altar(minecraft).setStack(player, ItemStack.EMPTY);
            lay(minecraft, VoidHeartItems.RAVENOUS_GOLD_INGOT, VoidHeartItems.RAVENOUS_GOLD_INGOT, VoidHeartItems.RAVENOUS_GOLD_INGOT,
                    VoidHeartItems.SEETHING_STEEL_INGOT, VoidHeartItems.SEETHING_STEEL_INGOT, VoidHeartItems.VOID_PEARL, VoidHeartItems.VOID_PEARL,
                    VoidHeartItems.AZURIUM_SHARD);
            var apparatus = new ItemStack(VoidHeartItems.LESSER_SHIELDING_APPARATUS);
            apparatus.set(VoidHeartDataComponents.APPARATUS_GRID, grid);
            altar(minecraft).setStack(player, apparatus);
        });
        waitFor(context, server, 2000, "Expected the lesser apparatus raised to a greater one", minecraft ->
                !altar(minecraft).isCrafting() && altar(minecraft).getStack().is(VoidHeartItems.GREATER_SHIELDING_APPARATUS));
        server.runOnServer(minecraft ->
        {
            var raised = altar(minecraft).getStack();
            expect(grid.equals(raised.get(VoidHeartDataComponents.APPARATUS_GRID)), "Expected the raised apparatus to keep its grid, got " + raised.get(VoidHeartDataComponents.APPARATUS_GRID));
            altar(minecraft).setStack(player(minecraft), ItemStack.EMPTY);
        });
    }

    // The fight

    private static ItemStack weapon(Item item, Sin sin)
    {
        var stack = new ItemStack(item);
        if (sin != null)
            stack.set(VoidHeartDataComponents.SIN, sin);
        return stack;
    }

    private static Zombie foe(MinecraftServer minecraft, double z)
    {
        var zombie = spawn(minecraft, EntityTypes.ZOMBIE, ARENA_X + 1.5, FLAT_FEET, z);
        zombie.setNoAi(true);
        zombie.setTarget(player(minecraft));
        return zombie;
    }

    /**
     * Hits a fresh zombie at full attack strength, standing still beside it.
     *
     * @return the health it lost
     */
    private static float strike(MinecraftServer minecraft, ItemStack weapon, double z)
    {
        var player = player(minecraft);
        player.setItemInHand(InteractionHand.MAIN_HAND, weapon);
        var zombie = foe(minecraft, z);
        setIntField(player, LivingEntity.class, "attackStrengthTicker", 1000);
        var before = zombie.getHealth();
        player.attack(zombie);
        return before - zombie.getHealth();
    }

    private static void standInArena(ClientGameTestContext context, TestServerContext server)
    {
        clearScene(server);
        server.runCommand("tp @a " + ARENA_X + " " + FLAT_FEET + " 0.5 -90 0");
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            player.removeAllEffects();
            player.setHealth(player.getMaxHealth());
            player.getFoodData().setFoodLevel(20);
            player.getFoodData().setSaturation(5);
            for (var sin : Sin.values())
                SinProficiencies.set(player, sin, 0);
        });
        context.waitTicks(5);
    }

    private static void expectRatio(float actual, float base, float ratio, String what)
    {
        expect(Math.abs(actual / base - ratio) < RELATIVE_TOLERANCE, what + ": expected " + ratio + " times " + base + ", got " + actual);
    }

    private static void wrath(ClientGameTestContext context, TestServerContext server)
    {
        standInArena(context, server);
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            var plain = strike(minecraft, weapon(Items.IRON_SWORD, null), 3);
            player.setHealth(2);
            var burning = strike(minecraft, weapon(Items.IRON_SWORD, Sin.WRATH), -3);
            expectRatio(burning, plain, 1 + SinBalance.WRATH_BONUS_NOVICE, "Wrath at one heart");
            expect(SinProficiencies.of(player).points(Sin.WRATH) > 0, "Expected a hit below half health to teach wrath");

            player.heal(4);
            expect(Math.abs(player.getHealth() - (2 + 4 * (1 - SinBalance.WRATH_HEAL_CUT_NOVICE))) < 0.01F, "Expected wrath to cut healing, health is " + player.getHealth());
        });

        // Just short of the second stage, the next lesson crosses it and the inlay of the sword already in hand flares
        server.runOnServer(minecraft -> player(minecraft).setItemInHand(InteractionHand.MAIN_HAND, weapon(Items.IRON_SWORD, Sin.WRATH)));
        context.waitTicks(20);
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            SinProficiencies.set(player, Sin.WRATH, SinBalance.STAGE_STARTS[1] * SinBalance.DEVOTED_POINTS - 0.1F);
            player.setHealth(2);
            strike(minecraft, player.getMainHandItem(), 6);
            expect(SinProficiencies.of(player).stage(Sin.WRATH) == 2, "Expected wrath at its second stage");
        });
        // Past the equip swing of the fresh sword, inside the flare
        context.waitTicks(8);
        expectClient(context, "Expected the client to flare the wrath inlay after the stage", minecraft -> SinFlare.isFlaring(Sin.WRATH));
        context.takeScreenshot("sin-wrath-flare");
        context.waitTicks(SinFlare.FLARE_TICKS);
        expectClient(context, "Expected the flare to fade", minecraft -> !SinFlare.isFlaring(Sin.WRATH));
        context.takeScreenshot("sin-wrath-held");
    }

    private static void pride(ClientGameTestContext context, TestServerContext server)
    {
        standInArena(context, server);
        var plain = server.computeOnServer(minecraft -> strike(minecraft, weapon(Items.IRON_SWORD, null), 3));
        server.runOnServer(minecraft -> player(minecraft).hurtServer(minecraft.overworld(), minecraft.overworld().damageSources().generic(), 1));
        server.runOnServer(minecraft -> player(minecraft).setItemInHand(InteractionHand.MAIN_HAND, weapon(Items.IRON_SWORD, Sin.PRIDE)));
        context.waitTicks(SinBalance.PRIDE_STACK_TICKS * SinBalance.PRIDE_MAX_STACKS_NOVICE + 5);

        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            var proud = strike(minecraft, weapon(Items.IRON_SWORD, Sin.PRIDE), -3);
            expectRatio(proud, plain, 1 + SinBalance.PRIDE_MAX_STACKS_NOVICE * SinBalance.PRIDE_STACK_BONUS_NOVICE, "Pride at full stacks");
            expect(SinProficiencies.of(player).points(Sin.PRIDE) > 0, "Expected a hit with three stacks or more to teach pride");

            player.hurtServer(minecraft.overworld(), minecraft.overworld().damageSources().generic(), 1);
            expect(player.hasEffect(MobEffects.WEAKNESS), "Expected a hit taken to shatter pride into weakness");
        });
    }

    private static void gluttony(ClientGameTestContext context, TestServerContext server)
    {
        standInArena(context, server);
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            player.setHealth(10);
            var dealt = strike(minecraft, weapon(Items.IRON_SWORD, Sin.GLUTTONY), 3);
            var expected = 10 + dealt * SinBalance.GLUTTONY_HEAL_NOVICE;
            expect(Math.abs(player.getHealth() - expected) < 0.01F, "Expected gluttony to heal " + dealt * SinBalance.GLUTTONY_HEAL_NOVICE + ", health is " + player.getHealth());
            expect(Math.abs(player.getFoodData().getSaturationLevel() - (5 - SinBalance.GLUTTONY_COST_NOVICE)) < 0.01F,
                    "Expected the hit to eat saturation, it is " + player.getFoodData().getSaturationLevel());
            expect(SinProficiencies.of(player).points(Sin.GLUTTONY) > 0, "Expected healing to teach gluttony");
        });
    }

    private static void sloth(ClientGameTestContext context, TestServerContext server)
    {
        standInArena(context, server);
        var plain = server.computeOnServer(minecraft -> strike(minecraft, weapon(Items.IRON_SWORD, null), 3));
        server.runOnServer(minecraft -> player(minecraft).setItemInHand(InteractionHand.MAIN_HAND, weapon(Items.IRON_SWORD, Sin.SLOTH)));
        context.waitTicks(SinBalance.SLOTH_CHARGE_TICKS_NOVICE + 5);

        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            expect(player.getAttribute(Attributes.MOVEMENT_SPEED).hasModifier(SinCombat.SLOTH_DRAG), "Expected a sloth blade to slow its wielder");
            expect(SinCombat.isCharged(player), "Expected standing still to charge the blow");

            player.setItemInHand(InteractionHand.MAIN_HAND, weapon(Items.IRON_SWORD, Sin.SLOTH));
            var zombie = foe(minecraft, -3);
            setIntField(player, LivingEntity.class, "attackStrengthTicker", 1000);
            var before = zombie.getHealth();
            player.attack(zombie);
            expectRatio(before - zombie.getHealth(), plain, 1 + SinBalance.SLOTH_BONUS_NOVICE, "Charged sloth blow");
            var pinned = zombie.getEffect(MobEffects.SLOWNESS);
            expect(pinned != null && pinned.getAmplifier() >= 3, "Expected the charged blow to stagger its target");
            expect(!SinCombat.isCharged(player), "Expected the blow to spend the charge");
            expect(SinProficiencies.of(player).points(Sin.SLOTH) > 0, "Expected a charged blow to teach sloth");

            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        });
        context.waitTicks(2);
        expect(!server.computeOnServer(minecraft -> player(minecraft).getAttribute(Attributes.MOVEMENT_SPEED).hasModifier(SinCombat.SLOTH_DRAG)),
                "Expected the slowness to leave with the blade");
    }

    private static void greed(ClientGameTestContext context, TestServerContext server)
    {
        standInArena(context, server);
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            player.setItemInHand(InteractionHand.MAIN_HAND, weapon(Items.IRON_SWORD, Sin.GREED));
            var zombie = foe(minecraft, 3);
            zombie.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 400, 1));
            setIntField(player, LivingEntity.class, "attackStrengthTicker", 1000);
            player.attack(zombie);

            expect(!zombie.hasEffect(MobEffects.STRENGTH), "Expected greed to take the zombie's strength");
            var stolen = player.getEffect(MobEffects.STRENGTH);
            expect(stolen != null && stolen.getAmplifier() == 0, "Expected a novice to steal strength at its lowest level, got " + stolen);
            expect(SinProficiencies.of(player).points(Sin.GREED) > 0, "Expected a theft to teach greed");

            player.removeEffect(MobEffects.STRENGTH);
        });
        context.waitTicks(2);
        expect(server.computeOnServer(minecraft -> player(minecraft).hasEffect(MobEffects.WEAKNESS)), "Expected stolen strength to be repaid as weakness");
    }

    // What the player sees

    private static void tooltipSpeaksLore(ClientGameTestContext context, TestServerContext server)
    {
        standInArena(context, server);
        server.runOnServer(minecraft -> SinProficiencies.set(player(minecraft), Sin.WRATH, SinBalance.STAGE_STARTS[2] * SinBalance.DEVOTED_POINTS));
        context.waitTicks(5);
        var lines = context.computeOnClient(minecraft ->
                weapon(Items.IRON_SWORD, Sin.WRATH).getTooltipLines(net.minecraft.world.item.Item.TooltipContext.of(minecraft.level), minecraft.player, TooltipFlag.NORMAL)
                        .stream().map(Component::getString).toList());
        expect(lines.contains("Wrath"), "Expected the sin named in the tooltip, got " + lines);
        expect(lines.contains("It remembers every wound."), "Expected the third stage's lore, got " + lines);
        expect(lines.stream().noneMatch(line -> line.matches(".*\\d.*") && !line.contains("Attack")), "Expected no numbers in the sin lines, got " + lines);

        // A player devoted to pride feels it resent another sin's weapon
        server.runOnServer(minecraft -> SinProficiencies.set(player(minecraft), Sin.PRIDE, SinBalance.STAGE_STARTS[4] * SinBalance.DEVOTED_POINTS));
        context.waitTicks(5);
        var resented = context.computeOnClient(minecraft ->
                weapon(Items.IRON_SWORD, Sin.WRATH).getTooltipLines(net.minecraft.world.item.Item.TooltipContext.of(minecraft.level), minecraft.player, TooltipFlag.NORMAL)
                        .stream().map(Component::getString).toList());
        expect(resented.contains("Your pride resents this."), "Expected pride to resent a wrath blade, got " + resented);
    }

    private static void captureInlays(ClientGameTestContext context, TestServerContext server)
    {
        standInArena(context, server);
        server.runOnServer(minecraft ->
        {
            var inventory = player(minecraft).getInventory();
            inventory.clearContent();
            inventory.setItem(0, weapon(Items.DIAMOND_SWORD, Sin.WRATH));
            inventory.setItem(1, weapon(Items.IRON_AXE, Sin.PRIDE));
            inventory.setItem(2, weapon(Items.MACE, Sin.GLUTTONY));
            inventory.setItem(3, weapon(Items.IRON_SPEAR, Sin.SLOTH));
            inventory.setItem(4, weapon(Items.TRIDENT, Sin.GREED));
            inventory.setItem(5, weapon(Items.BOW, Sin.WRATH));
            inventory.setItem(6, weapon(Items.CROSSBOW, Sin.PRIDE));
            inventory.setItem(7, weapon(Items.NETHERITE_SWORD, Sin.SLOTH));
            inventory.setItem(8, weapon(Items.GOLDEN_SWORD, Sin.GREED));
            inventory.setItem(9, weapon(Items.IRON_SWORD, null));
            inventory.setItem(10, weapon(Items.DIAMOND_AXE, Sin.WRATH));
            inventory.setItem(11, weapon(Items.WOODEN_SPEAR, Sin.GREED));
        });
        context.waitTicks(5);
        context.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
        context.waitTicks(5);
        context.takeScreenshot("sin-inlays-inventory");
        context.setScreen(() -> null);

        context.getInput().pressKey(InputConstants.KEY_F1);
        context.runOnClient(minecraft -> minecraft.player.getInventory().setSelectedSlot(0));
        context.waitTicks(5);
        context.takeScreenshot("sin-inlay-sword-first-person");

        context.runOnClient(minecraft -> minecraft.player.getInventory().setSelectedSlot(4));
        context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
        context.waitTicks(10);
        context.takeScreenshot("sin-inlay-trident-third-person");

        // A thrown trident keeps its inlay in flight
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            var trident = new ThrownTrident(level, ARENA_X + 2.5, FLAT_FEET + 1.2, 0.5, weapon(Items.TRIDENT, Sin.GREED));
            trident.setNoGravity(true);
            trident.setDeltaMovement(0, 0, 0);
            trident.setYRot(0);
            trident.setXRot(-60);
            level.addFreshEntity(trident);
        });
        context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
        aimFrom(context, server, ARENA_X, FLAT_FEET, -1.5, ARENA_X + 2.5, FLAT_FEET + 1.2, 0.5, 10);
        expect(server.computeOnServer(minecraft -> !minecraft.overworld().getEntities(EntityTypes.TRIDENT, trident -> true).isEmpty()), "Expected the thrown trident in the scene");
        context.takeScreenshot("sin-inlay-trident-thrown");

        context.getInput().pressKey(InputConstants.KEY_F1);
        context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            player.getInventory().clearContent();
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
        });
    }
}
