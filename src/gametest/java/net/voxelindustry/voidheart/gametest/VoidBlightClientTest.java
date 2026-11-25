package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.voxelindustry.voidheart.client.render.blight.EclipseThreadRender;
import net.voxelindustry.voidheart.client.screen.BlueprintScreen;
import net.voxelindustry.voidheart.common.content.blight.BlightBalance;
import net.voxelindustry.voidheart.common.content.blight.Blueprints;
import net.minecraft.resources.Identifier;
import net.voxelindustry.voidheart.common.content.blight.Harvest;
import net.voxelindustry.voidheart.common.content.blight.HarvestWraith;
import net.voxelindustry.voidheart.common.setup.VoidHeartEntities;
import net.minecraft.world.phys.AABB;
import net.voxelindustry.voidheart.common.content.blight.RapierStrikes;
import net.voxelindustry.voidheart.common.content.blight.ThrownBlightSpear;
import net.voxelindustry.voidheart.common.content.blight.EclipseLink;
import net.voxelindustry.voidheart.common.content.blight.EclipseLinks;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Items;
import net.voxelindustry.voidheart.common.content.blight.Wound;
import net.voxelindustry.voidheart.common.content.research.Knowledge;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static net.voxelindustry.voidheart.gametest.GameTestKit.*;

/**
 * The void blights: a blessed contraption offers the designs its holder lacks and spends itself teaching the one picked, which brings that design's
 * research into sight. The rapier's charged hits puncture, uncharged ones don't, and the seventh folds the target away and unseams it.
 */
public class VoidBlightClientTest implements FabricClientGameTest
{
    private static final String RAPIER    = "voidheart:void_blight_rapier";
    private static final double TARGET_Z  = 2.8;
    private static final float  STURDY    = 200;
    /** Rooted mobs outlast every crush of a hole, so the spear test can count them. */
    private static final float  ROOTED    = 120;
    private static final int    CHARGED   = 13;
    private static final int    SCYTHE_CHARGED = 24;
    private static final List<Identifier> BLIGHTS = List.of(id("void_blight_rapier"), id("void_blight_scythe"), id("void_blight_spear"), id("void_blight_crossbow"));
    /** Where the spear is thrown at, on the floor ahead, and where its hole then hangs. */
    private static final double SPEAR_Z  = 8.5;

    @Override
    public void runTest(ClientGameTestContext context)
    {
        // Every test shares this client, so its camera and HUD go back as they were however this one ends
        var camera = context.computeOnClient(minecraft -> minecraft.options.getCameraType());
        boolean hudHidden = context.computeOnClient(minecraft -> minecraft.gui.hud.isHidden());
        try (var world = context.worldBuilder().create())
        {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            freezeTime(server, "noon");
            server.runCommand("gamemode survival @a");
            server.runCommand("difficulty normal");
            clearArea(server, 0);
            server.runCommand("tp @a 0.5 " + FLAT_FEET + " 0.5 0 0");
            context.runOnClient(minecraft ->
            {
                minecraft.options.setCameraType(CameraType.FIRST_PERSON);
                // Its scenes toggle the HUD with F1, so they start from it shown
                if (minecraft.gui.hud.isHidden())
                    minecraft.gui.hud.toggle();
            });

            contraption(context, server);
            rapier(context, server);
            scythe(context, server);
            spear(context, server);
            spearFarOut(context, server);
            crossbow(context, server);
        }
        finally
        {
            context.runOnClient(minecraft ->
            {
                minecraft.options.setCameraType(camera);
                if (minecraft.gui.hud.isHidden() != hudHidden)
                    minecraft.gui.hud.toggle();
            });
        }
    }

    private static void contraption(ClientGameTestContext context, TestServerContext server)
    {
        server.runCommand("execute as @a run voidheart blueprint forget all");
        // What each design's research hangs from, so a learned design brings it into sight
        server.runCommand("execute as @a run voidheart research grant voidheart:contravoid_reactor");
        server.runCommand("execute as @a run voidheart research grant voidheart:void_marrow");
        int pool = server.computeOnServer(minecraft -> Blueprints.pool(Knowledge.tree(minecraft.registryAccess())).size());
        expect(pool >= BLIGHTS.size(), "Expected at least the blights' blueprints, got " + pool);
        // One contraption for each design, the way a player would collect them
        server.runOnServer(minecraft -> player(minecraft).setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(VoidHeartItems.BLESSED_CONTRAPTION, pool)));
        context.waitTicks(5);
        for (var blight : BLIGHTS)
            expect(server.computeOnServer(minecraft -> !discoverable(minecraft, blight)), "Expected " + blight + "'s research hidden before its blueprint is known");

        rightClick(context);
        context.waitFor(minecraft -> minecraft.gui.screen() instanceof BlueprintScreen, 40);
        int designs = context.computeOnClient(minecraft -> ((BlueprintScreen) minecraft.gui.screen()).designCount());
        expect(designs == Math.min(pool, Blueprints.OFFERED), "Expected the contraption to offer every unknown design up to three, got " + designs);
        context.takeScreenshot("void-blight-contraption-offer");

        var plate = context.computeOnClient(minecraft -> ((BlueprintScreen) minecraft.gui.screen()).plateCenter(0));
        var first = context.computeOnClient(minecraft -> ((BlueprintScreen) minecraft.gui.screen()).designAt(0));
        var previous = offerOf(context);
        click(context, plate);
        context.takeScreenshot("void-blight-contraption-picked");
        expect(server.computeOnServer(minecraft -> knowledge(minecraft).getBlueprints().isEmpty()), "Expected picking a plate alone to teach nothing");

        // Kept from the keyboard, as a player without a mouse would
        context.getInput().pressKey(InputConstants.KEY_RETURN);
        context.waitTicks(5);
        expectClient(context, "Expected keeping a design to close the screen", minecraft -> minecraft.gui.screen() == null);
        server.runOnServer(minecraft ->
        {
            expect(knowledge(minecraft).knowsBlueprint(first), "Expected the picked blueprint " + first + " learned");
            expect(player(minecraft).getMainHandItem().getCount() == pool - 1, "Expected one contraption spent, holds " + player(minecraft).getMainHandItem().getCount());
            expect(discoverable(minecraft, first), "Expected " + first + "'s research in sight once its blueprint is known");
        });

        // The next one only offers what is still unknown, until nothing is
        for (int left = pool - 1; left > 0; left--)
        {
            rightClick(context);
            context.waitFor(minecraft -> minecraft.gui.screen() instanceof BlueprintScreen, 40);
            var offer = offerOf(context);
            // What the stack drew before and is still unknown stays, in the same order; only once none is left does it draw afresh
            var kept = previous.subList(1, previous.size());
            if (!kept.isEmpty())
                expect(offer.equals(kept), "Expected the designs not picked kept, " + kept + ", got " + offer);
            else
                expect(offer.size() == Math.min(left, Blueprints.OFFERED), "Expected a fresh draw of " + Math.min(left, Blueprints.OFFERED) + " designs, got " + offer);
            for (var shown : offer)
                expect(server.computeOnServer(minecraft -> !knowledge(minecraft).knowsBlueprint(shown)), "Expected only unknown designs offered, got " + shown);
            previous = offer;
            context.getInput().pressKey(InputConstants.KEY_1);
            context.getInput().pressKey(InputConstants.KEY_RETURN);
            context.waitTicks(5);
        }
        expect(server.computeOnServer(minecraft -> knowledge(minecraft).getBlueprints().size() == pool), "Expected every blueprint learned");

        // With every design known, the last one unfolds nothing
        server.runOnServer(minecraft -> player(minecraft).setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(VoidHeartItems.BLESSED_CONTRAPTION)));
        context.waitTicks(3);
        rightClick(context);
        context.waitTicks(5);
        expectClient(context, "Expected no choice once every design is known", minecraft -> minecraft.gui.screen() == null);
        expect(!overlay(context).isEmpty(), "Expected the spent contraption to say why");
        expect(server.computeOnServer(minecraft -> player(minecraft).getMainHandItem().getCount() == 1), "Expected the unopened contraption kept");
    }

    /** The designs the open choice shows, in order; the first is the one the scenes pick. */
    private static List<Identifier> offerOf(ClientGameTestContext context)
    {
        return context.computeOnClient(minecraft ->
        {
            var screen = (BlueprintScreen) minecraft.gui.screen();
            return java.util.stream.IntStream.range(0, screen.designCount()).mapToObj(screen::designAt).toList();
        });
    }

    private static boolean discoverable(MinecraftServer minecraft, Identifier blueprint)
    {
        return Blueprints.researchOf(blueprint, Knowledge.tree(minecraft.registryAccess())).filter(entry -> knowledge(minecraft).isDiscoverable(entry.getValue())).isPresent();
    }

    private static void rapier(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft -> player(minecraft).setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(VoidHeartItems.VOID_BLIGHT_RAPIER)));
        context.waitTicks(5);
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            expect(Math.abs(player.entityInteractionRange() - 3.5) < 1E-3, "Expected the rapier to reach 3.5 blocks, reaches " + player.entityInteractionRange());
            expect(Math.abs(player.getAttributeValue(Attributes.ATTACK_SPEED) - 2) < 1E-3, "Expected 2 attacks a second, got " + player.getAttributeValue(Attributes.ATTACK_SPEED));
        });

        var target = server.computeOnServer(minecraft ->
        {
            var husk = spawn(minecraft, EntityTypes.HUSK, 0.5, FLAT_FEET, TARGET_Z);
            husk.setNoAi(true);
            husk.getAttribute(Attributes.MAX_HEALTH).setBaseValue(STURDY);
            husk.setHealth(STURDY);
            husk.setPersistenceRequired();
            return husk.getUUID();
        });
        aimFrom(context, server, 0.5, FLAT_FEET, 0.5, 0.5, FLAT_FEET + 1.2, TARGET_Z, 15);

        // A charged thrust punctures, the hasty one right after doesn't
        attack(context, CHARGED);
        attack(context, 2);
        context.waitTicks(2);
        expectOpen(server, target, 1, "after one charged and one hasty hit");

        for (int hit = 2; hit < Wound.HITS; hit++)
            attack(context, CHARGED);
        context.waitTicks(2);
        expectOpen(server, target, Wound.HITS - 1, "after six charged hits");
        int targetId = server.computeOnServer(minecraft -> husk(minecraft, target).getId());
        int synced = context.computeOnClient(minecraft -> minecraft.level.getEntity(targetId) instanceof LivingEntity living
                ? RapierStrikes.of(living).wounds().values().stream().mapToInt(wound -> wound.slits().size()).sum() : -1);
        expect(synced == Wound.HITS - 1, "Expected the client to be told of the punctures, sees " + synced);
        context.getInput().pressKey(InputConstants.KEY_F1);
        context.waitTicks(2);
        context.takeScreenshot("void-blight-rapier-punctured");
        // Seen from the side and from behind, to catch slits that sink into the body or show through it
        aimFrom(context, server, 2.5, FLAT_FEET, TARGET_Z, 0.5, FLAT_FEET + 1.2, TARGET_Z, 3);
        context.takeScreenshot("void-blight-rapier-punctured-side");
        aimFrom(context, server, 0.5, FLAT_FEET, TARGET_Z + 2.3, 0.5, FLAT_FEET + 1.2, TARGET_Z, 3);
        context.takeScreenshot("void-blight-rapier-punctured-behind");
        aimFrom(context, server, 0.5, FLAT_FEET, 0.5, 0.5, FLAT_FEET + 1.2, TARGET_Z, 3);
        context.getInput().pressKey(InputConstants.KEY_F1);
        context.waitTicks(2);

        // The seventh folds it away, held where it stood, and it takes the Unseaming as it snaps back
        attack(context, CHARGED);
        context.getInput().pressKey(InputConstants.KEY_F1);
        context.waitTicks(2);
        context.takeScreenshot("void-blight-rapier-fold-crack");
        float folded = server.computeOnServer(minecraft ->
        {
            var husk = husk(minecraft, target);
            expect(RapierStrikes.of(husk).fold().isPresent(), "Expected the seventh puncture to fold the target");
            return husk.getHealth();
        });
        context.waitTicks(3);
        context.takeScreenshot("void-blight-rapier-fold-collapse");
        // The snap, caught as soon as the client sees the fold end
        context.waitFor(minecraft -> minecraft.level.getEntity(targetId) instanceof LivingEntity living && RapierStrikes.of(living).fold().isEmpty(), 20);
        context.takeScreenshot("void-blight-rapier-fold-snap");
        context.waitTicks(3);
        context.takeScreenshot("void-blight-rapier-fold-after");
        context.getInput().pressKey(InputConstants.KEY_F1);
        float after = server.computeOnServer(minecraft -> husk(minecraft, target).getHealth());
        float expected = BlightBalance.unseamingDamage(STURDY);
        expect(folded - after >= expected - 0.5F, "Expected the Unseaming to take about " + expected + ", took " + (folded - after));
        server.runOnServer(minecraft -> expect(RapierStrikes.of(husk(minecraft, target)).fold().isEmpty(), "Expected the fold over"));

        // Then the same wielder can't puncture it for a while
        attack(context, CHARGED);
        context.waitTicks(2);
        expectOpen(server, target, 0, "right after an Unseaming");

        // The blade in its bearer's hand once the swing has come back up, at noon and at night, where only its edge should show
        context.waitTicks(20);
        context.takeScreenshot("void-blight-rapier-in-hand");
        server.runCommand("time set midnight");
        context.waitTicks(3);
        context.takeScreenshot("void-blight-rapier-in-hand-night");
        // Dropped on the ground beside its bearer
        server.runCommand("kill @e[type=husk]");
        server.runCommand("summon item 0.5 " + FLAT_FEET + " 1.6 {Item:{id:\"" + RAPIER + "\",count:1},PickupDelay:32767,Age:-32768}");
        aimFrom(context, server, 0.5, FLAT_FEET, 0.2, 0.5, FLAT_FEET, 1.6, 10);
        context.getInput().pressKey(InputConstants.KEY_F1);
        context.waitTicks(2);
        context.takeScreenshot("void-blight-rapier-dropped-night");
        context.getInput().pressKey(InputConstants.KEY_F1);
        server.runCommand("time set noon");

        // In the inventory it stays the flat sprite
        server.runOnServer(minecraft -> player(minecraft).getInventory().add(new ItemStack(VoidHeartItems.VOID_BLIGHT_RAPIER)));
        context.getInput().pressKey(InputConstants.KEY_E);
        context.waitTicks(5);
        context.takeScreenshot("void-blight-rapier-inventory");
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitTicks(3);
    }

    private static void scythe(ClientGameTestContext context, TestServerContext server)
    {
        server.runCommand("kill @e[type=!player]");
        server.runCommand("tp @a 0.5 " + FLAT_FEET + " 0.5 0 0");
        server.runOnServer(minecraft ->
        {
            player(minecraft).setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(VoidHeartItems.VOID_BLIGHT_SCYTHE));
            heal(minecraft);
        });
        var reaped = server.computeOnServer(minecraft -> standingHusk(minecraft, 0.5, TARGET_Z, 16));
        aimFrom(context, server, 0.5, FLAT_FEET, 0.5, 0.5, FLAT_FEET + 1.2, TARGET_Z, 15);

        // Two full swings fell it, and it rises for its slayer out of a stain on the ground
        attack(context, SCYTHE_CHARGED);
        attack(context, SCYTHE_CHARGED);
        context.waitTicks(2);
        server.runOnServer(minecraft ->
        {
            expect(!(minecraft.overworld().getEntity(reaped) instanceof LivingEntity living) || !living.isAlive(), "Expected two full swings to kill the husk");
            var raised = wraiths(minecraft);
            expect(raised.size() == 1, "Expected the husk to rise as one wraith, got " + raised.size());
            var wraith = raised.getFirst();
            expect(player(minecraft).getUUID().equals(wraith.owner()), "Expected the wraith to answer to its slayer");
            expect(wraith.source().filter(EntityTypes.HUSK::equals).isPresent(), "Expected the wraith to take the husk's shape, got " + wraith.source());
        });
        context.getInput().pressKey(InputConstants.KEY_F1);
        expectPhase(server, Harvest.Phase.STAIN);
        context.takeScreenshot("void-blight-scythe-stain");
        context.waitTicks(BlightBalance.WRAITH_STAIN_TICKS + BlightBalance.WRAITH_RISE_TICKS / 2 - 2);
        expectPhase(server, Harvest.Phase.RISING);
        context.takeScreenshot("void-blight-scythe-rising");
        context.waitTicks(BlightBalance.WRAITH_RISE_TICKS);
        expectPhase(server, Harvest.Phase.FIGHTING);
        context.takeScreenshot("void-blight-scythe-risen");
        // The client counts the wraith's age by the same clock as the server, so its rise and sink line up with the server's
        int serverAge = server.computeOnServer(minecraft -> wraiths(minecraft).getFirst().age());
        int clientAge = context.computeOnClient(minecraft -> minecraft.level.getEntitiesOfClass(HarvestWraith.class, minecraft.player.getBoundingBox().inflate(16)).getFirst().age());
        expect(Math.abs(serverAge - clientAge) <= 3, "Expected the client to count the wraith's age as the server does, " + clientAge + " against " + serverAge);
        aimFrom(context, server, 2.5, FLAT_FEET, TARGET_Z, 0.5, FLAT_FEET + 1.2, TARGET_Z, 3);
        context.takeScreenshot("void-blight-scythe-risen-side");

        // It glides through a wall at whatever struck its wielder, and its kill counts as the wielder's
        server.runCommand("fill -2 " + FLAT_FEET + " 6 3 " + (FLAT_FEET + 3) + " 6 stone");
        var foe = server.computeOnServer(minecraft ->
        {
            var husk = standingHusk(minecraft, 0.5, 8.5, 60);
            player(minecraft).setLastHurtByMob(husk(minecraft, husk));
            return husk;
        });
        waitFor(context, server, 200, "Expected the wraith to reach the husk behind the wall and strike it",
                minecraft -> husk(minecraft, foe).getHealth() < 60);
        aimFrom(context, server, 4.5, FLAT_FEET + 2, 8.5, 0.5, FLAT_FEET + 1, 8.5, 3);
        context.takeScreenshot("void-blight-scythe-fight");
        aimFrom(context, server, 4.5, FLAT_FEET, 8.5, 0.5, FLAT_FEET + 1, 8.5, 3);
        context.takeScreenshot("void-blight-scythe-fight-level");
        server.runOnServer(minecraft -> husk(minecraft, foe).setHealth(2));
        waitFor(context, server, 100, "Expected the wraith to finish the husk",
                minecraft -> !(minecraft.overworld().getEntity(foe) instanceof LivingEntity living) || !living.isAlive());
        context.waitTicks(3);
        int orbs = server.computeOnServer(minecraft -> minecraft.overworld().getEntities(EntityTypes.EXPERIENCE_ORB,
                new AABB(-3, FLAT_FEET - 1, 5, 4, FLAT_FEET + 4, 12), orb -> true).size());
        expect(orbs > 0, "Expected the wraith's kill to drop experience, as its wielder's kill");
        context.getInput().pressKey(InputConstants.KEY_F1);
        server.runCommand("fill -2 " + FLAT_FEET + " 6 3 " + (FLAT_FEET + 3) + " 6 air");

        // The blade in its bearer's hand, and the flat sprite in the inventory
        server.runCommand("tp @a 0.5 " + FLAT_FEET + " 0.5 0 0");
        context.waitTicks(20);
        context.takeScreenshot("void-blight-scythe-in-hand");
        context.getInput().pressKey(InputConstants.KEY_E);
        context.waitTicks(5);
        context.takeScreenshot("void-blight-scythe-inventory");
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitTicks(3);

        // Weak mobs never rise
        server.runCommand("tp @a 0.5 " + FLAT_FEET + " 0.5 0 0");
        var weak = server.computeOnServer(minecraft ->
        {
            var silverfish = spawn(minecraft, EntityTypes.SILVERFISH, 0.5, FLAT_FEET, TARGET_Z);
            silverfish.setNoAi(true);
            return silverfish.getUUID();
        });
        aimFrom(context, server, 0.5, FLAT_FEET, 0.5, 0.5, FLAT_FEET + 0.2, TARGET_Z, 15);
        attack(context, SCYTHE_CHARGED);
        context.waitTicks(3);
        server.runOnServer(minecraft ->
        {
            expect(!(minecraft.overworld().getEntity(weak) instanceof LivingEntity living) || !living.isAlive(), "Expected the silverfish dead");
            expect(wraiths(minecraft).size() == 1, "Expected a silverfish too weak to rise");
        });

        // A wielder keeps three: a fourth sends the oldest back
        server.runOnServer(minecraft ->
        {
            for (int i = 0; i < 3; i++)
            {
                var husk = spawn(minecraft, EntityTypes.HUSK, -2.5 + i * 2, FLAT_FEET, 4.5);
                HarvestWraith.raise(minecraft.overworld(), husk, player(minecraft));
                husk.discard();
            }
            long standing = wraiths(minecraft).stream().filter(wraith -> !wraith.isSinking()).count();
            expect(standing == BlightBalance.WRAITH_MAX, "Expected " + BlightBalance.WRAITH_MAX + " wraiths kept, got " + standing);
        });

        // Their time up, they sink back into the ground and are gone
        waitFor(context, server, BlightBalance.WRAITH_STAIN_TICKS + BlightBalance.WRAITH_RISE_TICKS + BlightBalance.WRAITH_LIFE_TICKS + BlightBalance.WRAITH_SINK_TICKS + 40,
                "Expected every wraith gone once its time is up", minecraft -> wraiths(minecraft).isEmpty());
        server.runCommand("kill @e[type=!player]");
    }

    private static void expectPhase(TestServerContext server, Harvest.Phase phase)
    {
        var phases = server.computeOnServer(minecraft -> wraiths(minecraft).stream().map(HarvestWraith::phase).toList());
        expect(phases.equals(List.of(phase)), "Expected one wraith " + phase + ", got " + phases);
    }

    private static void spear(ClientGameTestContext context, TestServerContext server)
    {
        server.runCommand("kill @e[type=!player]");
        server.runCommand("tp @a 0.5 " + FLAT_FEET + " 3.5 0 0");
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            player.getInventory().clearContent();
            player.getCooldowns().removeCooldown(player.getCooldowns().getCooldownGroup(new ItemStack(VoidHeartItems.VOID_BLIGHT_SPEAR)));
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(VoidHeartItems.VOID_BLIGHT_SPEAR));
            heal(minecraft);
        });
        // Two pigs it should draw in, one beside where it lands and one near the edge of its reach, and a sheep standing in for a boss, which the game
        // tests tag as one. None can walk, but each still moves as it is pushed, which a mob without its wits would not
        var near = server.computeOnServer(minecraft -> rootedMob(minecraft, EntityTypes.PIG, 2.5, SPEAR_Z));
        var far = server.computeOnServer(minecraft -> rootedMob(minecraft, EntityTypes.PIG, 5, SPEAR_Z + 1));
        var boss = server.computeOnServer(minecraft -> rootedMob(minecraft, EntityTypes.SHEEP, -2.5, SPEAR_Z));
        var nearFrom = server.computeOnServer(minecraft -> husk(minecraft, near).position());
        var farFrom = server.computeOnServer(minecraft -> husk(minecraft, far).position());
        var bossFrom = server.computeOnServer(minecraft -> husk(minecraft, boss).position());
        var playerFrom = server.computeOnServer(minecraft -> player(minecraft).position());
        aimFrom(context, server, 0.5, FLAT_FEET, 3.5, 0.5, FLAT_FEET, SPEAR_Z, 10);
        context.takeScreenshot("void-blight-spear-in-hand");

        // Held back, then let go
        context.getInput().holdMouse(InputConstants.MOUSE_BUTTON_RIGHT);
        context.waitTicks(BlightBalance.SPEAR_CHARGE_TICKS + 6);
        context.takeScreenshot("void-blight-spear-wind-up");
        context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_RIGHT);
        waitFor(context, server, 10, "Expected letting go to throw the spear", minecraft -> !spears(minecraft).isEmpty());
        // Caught in flight, side on
        server.runCommand("tick freeze");
        var flying = server.computeOnServer(minecraft -> spears(minecraft).getFirst().position());
        aimFrom(context, server, flying.x + 3, FLAT_FEET, flying.z, flying.x, flying.y, flying.z, 2);
        context.takeScreenshot("void-blight-spear-flight");
        aimFrom(context, server, 0.5, FLAT_FEET, 3.5, 0.5, FLAT_FEET, SPEAR_Z, 2);
        server.runCommand("tick unfreeze");
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            expect(player.getMainHandItem().isEmpty(), "Expected the spear out of the hand once thrown, holds " + player.getMainHandItem());
            expect(player.getCooldowns().isOnCooldown(new ItemStack(VoidHeartItems.VOID_BLIGHT_SPEAR)), "Expected a cooldown on the throw");
        });

        waitFor(context, server, 40, "Expected the spear to open its hole where it landed", minecraft -> spears(minecraft).getFirst().hasOpened());
        var center = server.computeOnServer(minecraft -> spears(minecraft).getFirst().holeCenter());
        // Its middle is kept from where the spear hangs, so the spear must not drift while the hole lasts
        var hangs = server.computeOnServer(minecraft -> spears(minecraft).getFirst().position());
        expect(Math.abs(center.z - SPEAR_Z) < 1.5 && center.y > FLAT_FEET + 0.5 && center.y < FLAT_FEET + 1.5,
                "Expected the hole to hang just above the floor where the spear was thrown, at " + center);
        context.getInput().pressKey(InputConstants.KEY_F1);
        context.waitTicks(1);
        context.takeScreenshot("void-blight-spear-opening");
        context.waitTicks(BlightBalance.HOLE_OPEN_TICKS + 6);
        context.takeScreenshot("void-blight-spear-hole");
        expect(server.computeOnServer(minecraft -> player(minecraft).position().distanceTo(playerFrom) < 0.2), "Expected the player never drawn in as it opened");

        // Frozen and looked at from a few places, for flicker and to see it is a hole from every side
        server.runCommand("tick freeze");
        for (int step = 0; step < 8; step++)
        {
            double angle = -0.5 + step * 0.14;
            aimFrom(context, server, center.x + Math.sin(angle) * 5, FLAT_FEET, center.z - Math.cos(angle) * 5, center.x, center.y, center.z, 2);
            context.takeScreenshot("void-blight-spear-hole-sweep-" + step);
        }
        aimFrom(context, server, center.x + 4, FLAT_FEET + 3, center.z + 4, center.x, center.y, center.z, 2);
        context.takeScreenshot("void-blight-spear-hole-behind");
        aimFrom(context, server, 0.5, FLAT_FEET, 3.5, center.x, center.y, center.z, 2);
        server.runCommand("tick unfreeze");

        // It draws the husks in and crushes what it holds, but the boss and the player stand where they were
        waitFor(context, server, BlightBalance.HOLE_TICKS, "Expected the hole to crush the husk beside it",
                minecraft -> husk(minecraft, near).getHealth() < ROOTED);
        context.takeScreenshot("void-blight-spear-crushing");
        server.runOnServer(minecraft ->
        {
            var spear = spears(minecraft).getFirst();
            expect(spear.isHoleOpen() && spear.position().distanceTo(hangs) < 1.0E-6 && spear.holeCenter().distanceTo(center) < 1.0E-6,
                    "Expected the spear and its hole to stay where they opened, at " + spear.position() + " and " + spear.holeCenter());
            double nearNow = husk(minecraft, near).position().distanceTo(center);
            double farNow = husk(minecraft, far).position().distanceTo(center);
            expect(nearNow < nearFrom.distanceTo(center) - 0.5, "Expected the near husk drawn in, " + nearFrom.distanceTo(center) + " then " + nearNow);
            expect(farNow < farFrom.distanceTo(center) - 0.5, "Expected the far husk drawn in, " + farFrom.distanceTo(center) + " then " + farNow);
            expect(husk(minecraft, boss).position().distanceTo(bossFrom) < 0.2, "Expected the boss to stand fast, moved to " + husk(minecraft, boss).position());
            expect(player(minecraft).position().distanceTo(playerFrom) < 0.2, "Expected the player never drawn in, moved to " + player(minecraft).position());
        });

        // Then it closes and flies back into the hand that threw it
        // Checked on every tick it waits, so the spear and its hole are seen to hang still until the hole closes
        waitFor(context, server, BlightBalance.HOLE_TICKS, "Expected the spear to fly back once its hole closed", minecraft ->
        {
            if (spears(minecraft).isEmpty())
                return false;
            var spear = spears(minecraft).getFirst();
            if (spear.isHoleOpen())
                expect(spear.position().distanceTo(hangs) < 1.0E-6 && spear.holeCenter().distanceTo(center) < 1.0E-6,
                        "Expected the spear and its hole to stay where they opened, at " + spear.position() + " and " + spear.holeCenter());
            return spear.isReturning();
        });
        context.waitTicks(2);
        context.takeScreenshot("void-blight-spear-returning");
        waitFor(context, server, BlightBalance.HOLE_TICKS + 80, "Expected the spear back with its thrower once the hole closed",
                minecraft -> spears(minecraft).isEmpty() && player(minecraft).getInventory().contains(stack -> stack.is(VoidHeartItems.VOID_BLIGHT_SPEAR)));
        expect(server.computeOnServer(minecraft -> player(minecraft).position().distanceTo(playerFrom) < 0.2), "Expected the player never drawn in, to the hole's end");
        context.getInput().pressKey(InputConstants.KEY_F1);
        server.runOnServer(minecraft -> expect(husk(minecraft, near).getHealth() <= ROOTED - 2 * BlightBalance.HOLE_CRUSH_DAMAGE,
                "Expected the held husk crushed more than once, at " + husk(minecraft, near).getHealth()));

        // In the inventory it is the flat sprite
        context.getInput().pressKey(InputConstants.KEY_E);
        context.waitTicks(5);
        context.takeScreenshot("void-blight-spear-inventory");
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitTicks(3);
        server.runCommand("kill @e[type=!player]");
    }

    /**
     * Far out in the world, where a float can no longer tell one block from the next, the hole must still open exactly where the spear struck, on a
     * block or in a mob, and keep its middle through a save mid-hole.
     */
    private static void spearFarOut(ClientGameTestContext context, TestServerContext server)
    {
        double far = 25_000_000.5;
        server.runCommand("kill @e[type=!player]");
        // Written out plainly: a double this large prints in exponent form, which a command can't read
        server.runCommand(String.format(Locale.ROOT, "tp @a %.1f %d %.1f 0 0", far, FLAT_FEET, far));
        expect(server.computeOnServer(minecraft -> Math.abs(player(minecraft).getX() - far) < 1), "Expected the player far out, at "
                + server.computeOnServer(minecraft -> player(minecraft).position()));
        // Loading that far is work off the game thread, so wait for it rather than a fixed time
        waitFor(context, server, 400, "Expected the far chunks loaded and ticking", minecraft ->
                minecraft.overworld().isPositionEntityTicking(BlockPos.containing(far + 3, FLAT_FEET, far + 3))
                && minecraft.overworld().isPositionEntityTicking(BlockPos.containing(far - 3, FLAT_FEET, far + 4)));

        // Dropped straight onto the floor, its hole hangs the standoff above the very point it struck
        var dropped = server.computeOnServer(minecraft -> throwSpear(minecraft, new Vec3(far + 3, FLAT_FEET + 3, far + 3), new Vec3(0, -1, 0)));
        waitFor(context, server, 40, "Expected the far spear to open on the floor", minecraft -> spearById(minecraft, dropped).hasOpened());
        server.runOnServer(minecraft ->
        {
            var spear = spearById(minecraft, dropped);
            var expected = new Vec3(far + 3, FLAT_FEET + BlightBalance.HOLE_STANDOFF, far + 3);
            expect(spear.holeCenter().distanceTo(expected) < 1.0E-3, "Expected the far hole at " + expected + ", got " + spear.holeCenter());

            // Saved and loaded while open, it keeps its middle and goes on
            var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, minecraft.registryAccess());
            spear.saveWithoutId(output);
            var copy = new ThrownBlightSpear(VoidHeartEntities.THROWN_BLIGHT_SPEAR, minecraft.overworld());
            copy.load(TagValueInput.create(ProblemReporter.DISCARDING, minecraft.registryAccess(), output.buildResult()));
            expect(copy.isHoleOpen() && copy.holeCenter().distanceTo(spear.holeCenter()) < 1.0E-6,
                    "Expected a saved hole to load where it was, " + spear.holeCenter() + " then " + copy.holeCenter());
        });
        server.runCommand("kill @e[type=voidheart:thrown_blight_spear]");

        // Thrown into a pig, its hole opens in the pig's middle
        var pig = server.computeOnServer(minecraft -> rootedMob(minecraft, EntityTypes.PIG, far - 3, far + 4));
        var pigMiddle = server.computeOnServer(minecraft -> husk(minecraft, pig).getBoundingBox().getCenter());
        var thrown = server.computeOnServer(minecraft -> throwSpear(minecraft, new Vec3(far - 3, pigMiddle.y, far + 1), new Vec3(0, 0, 1)));
        waitFor(context, server, 40, "Expected the far spear to strike the pig", minecraft -> spearById(minecraft, thrown).hasOpened());
        server.runOnServer(minecraft -> expect(spearById(minecraft, thrown).holeCenter().distanceTo(pigMiddle) < 1.0E-3,
                "Expected the hole in the pig's middle, " + pigMiddle + ", got " + spearById(minecraft, thrown).holeCenter()));
        server.runCommand("kill @e[type=!player]");
        server.runCommand("tp @a 0.5 " + FLAT_FEET + " 0.5 0 0");
        context.waitTicks(20);
    }

    /** A spear thrown by the player from a point, straight along a way, that gives nothing back when it comes home. */
    private static UUID throwSpear(MinecraftServer minecraft, Vec3 from, Vec3 way)
    {
        var level = minecraft.overworld();
        var spear = new ThrownBlightSpear(level, player(minecraft), new ItemStack(VoidHeartItems.VOID_BLIGHT_SPEAR));
        spear.setPos(from);
        spear.shoot(way.x, way.y, way.z, BlightBalance.SPEAR_THROW_SPEED, 0);
        spear.pickup = AbstractArrow.Pickup.DISALLOWED;
        level.addFreshEntity(spear);
        return spear.getUUID();
    }

    private static ThrownBlightSpear spearById(MinecraftServer minecraft, UUID id)
    {
        if (!(minecraft.overworld().getEntity(id) instanceof ThrownBlightSpear spear))
            throw new AssertionError("Expected the thrown spear still there");
        return spear;
    }

    private static void crossbow(ClientGameTestContext context, TestServerContext server)
    {
        server.runCommand("kill @e[type=!player]");
        server.runCommand("tp @a 0.5 " + FLAT_FEET + " 0.5 0 0");
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            player.getInventory().clearContent();
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(VoidHeartItems.VOID_BLIGHT_CROSSBOW));
            player.getInventory().add(new ItemStack(Items.ARROW, 16));
            heal(minecraft);
        });
        int charge = server.computeOnServer(minecraft -> CrossbowItem.getChargeDuration(player(minecraft).getMainHandItem(), player(minecraft)));
        expect(charge == BlightBalance.CROSSBOW_CHARGE_TICKS, "Expected the repeater to load in " + BlightBalance.CROSSBOW_CHARGE_TICKS + " ticks, takes " + charge);

        var first = server.computeOnServer(minecraft -> rootedMob(minecraft, EntityTypes.PIG, -2.5, 8));
        var second = server.computeOnServer(minecraft -> rootedMob(minecraft, EntityTypes.PIG, 0.5, 9));
        var third = server.computeOnServer(minecraft -> rootedMob(minecraft, EntityTypes.PIG, 3.5, 8));
        context.waitTicks(5);
        context.takeScreenshot("void-blight-crossbow-in-hand");

        // Loaded by holding, then fired at each pig in turn, each bolt stitching its pig to the one before
        loadAndFire(context, server, first, true);
        expect(server.computeOnServer(minecraft -> link(minecraft).members().equals(List.of(husk(minecraft, first).getId()))),
                "Expected the first pig struck to wait in the link");
        loadAndFire(context, server, second, false);
        server.runOnServer(minecraft ->
        {
            var link = link(minecraft);
            expect(link.holds(minecraft.overworld().getGameTime()) && link.members().equals(List.of(husk(minecraft, first).getId(), husk(minecraft, second).getId())),
                    "Expected the two pigs stitched together, got " + link);
        });
        loadAndFire(context, server, third, false);
        expect(server.computeOnServer(minecraft -> link(minecraft).members().size() == 3), "Expected all three pigs in the link");
        context.getInput().pressKey(InputConstants.KEY_F1);
        aimFrom(context, server, 0.5, FLAT_FEET + 2, 2, 0.5, FLAT_FEET + 0.5, 8.5, 5);
        context.takeScreenshot("void-blight-crossbow-threads");
        // Frozen and looked at from a few places, for flicker along the threads
        server.runCommand("tick freeze");
        for (int step = 0; step < 8; step++)
        {
            aimFrom(context, server, 0.5 + step * 0.15, FLAT_FEET + 2, 2 + step * 0.1, 0.5, FLAT_FEET + 0.5, 8.5, 2);
            context.takeScreenshot("void-blight-crossbow-threads-sweep-" + step);
        }
        // And from behind the pigs, since the threads turn to face the camera
        aimFrom(context, server, 0.5, FLAT_FEET + 2, 15, 0.5, FLAT_FEET + 0.5, 8.5, 2);
        context.takeScreenshot("void-blight-crossbow-threads-behind");
        aimFrom(context, server, 0.5, FLAT_FEET + 2, 2, 0.5, FLAT_FEET + 0.5, 8.5, 2);
        server.runCommand("tick unfreeze");

        // Wounding one hurts the others by half, and what they took is not passed on again. The shares of the last bolt are waited out first, since a
        // mob still smarting from a hit takes only what a harder one adds
        context.waitTicks(20);
        var before = server.computeOnServer(minecraft -> List.of(husk(minecraft, first).getHealth(), husk(minecraft, second).getHealth(), husk(minecraft, third).getHealth()));
        server.runOnServer(minecraft -> husk(minecraft, second).hurtServer(minecraft.overworld(), minecraft.overworld().damageSources().playerAttack(player(minecraft)), 10));
        // A fresh share runs as a glint along the threads
        boolean glinted = false;
        for (int tick = 0; tick < 4; tick++)
        {
            context.waitTick();
            glinted |= context.computeOnClient(minecraft -> EclipseThreadRender.glintsDrawn() > 0);
            context.takeScreenshot("void-blight-crossbow-shared-" + tick);
        }
        expect(glinted, "Expected a glint to run from the wounded pig");
        expect(context.computeOnClient(minecraft -> !minecraft.player.getAttachedOrElse(EclipseLinks.LINK, EclipseLink.NONE).shares().isEmpty()),
                "Expected the client to hear of the shared wound");
        server.runOnServer(minecraft ->
        {
            float share = EclipseLink.shareOf(10);
            expect(Math.abs(husk(minecraft, second).getHealth() - (before.get(1) - 10)) < 0.01, "Expected the wounded pig to take only its own wound, at " + husk(minecraft, second).getHealth());
            expect(Math.abs(husk(minecraft, first).getHealth() - (before.get(0) - share)) < 0.01, "Expected the first pig to share half, at " + husk(minecraft, first).getHealth());
            expect(Math.abs(husk(minecraft, third).getHealth() - (before.get(2) - share)) < 0.01, "Expected the third pig to share half, at " + husk(minecraft, third).getHealth());
        });

        // A killing blow is shared too, by the health it took
        context.waitTicks(20);
        var lethal = server.computeOnServer(minecraft ->
        {
            husk(minecraft, first).setHealth(3);
            return List.of(husk(minecraft, second).getHealth(), husk(minecraft, third).getHealth());
        });
        server.runOnServer(minecraft -> husk(minecraft, first).hurtServer(minecraft.overworld(), minecraft.overworld().damageSources().playerAttack(player(minecraft)), 10));
        context.waitTicks(2);
        server.runOnServer(minecraft ->
        {
            float share = EclipseLink.shareOf(3);
            expect(!(minecraft.overworld().getEntity(first) instanceof LivingEntity living) || !living.isAlive(), "Expected the first pig killed");
            expect(Math.abs(husk(minecraft, second).getHealth() - (lethal.get(0) - share)) < 0.01, "Expected the killing blow shared, at " + husk(minecraft, second).getHealth());
            expect(Math.abs(husk(minecraft, third).getHealth() - (lethal.get(1) - share)) < 0.01, "Expected the killing blow shared, at " + husk(minecraft, third).getHealth());
        });

        // Pulled too far apart, the link snaps
        server.runOnServer(minecraft -> husk(minecraft, third).teleportTo(3.5 + BlightBalance.LINK_BREAK + 4, FLAT_FEET, 8));
        waitFor(context, server, 10, "Expected the link to snap once a pig stood too far off", minecraft -> player(minecraft).getAttached(EclipseLinks.LINK) == null);
        context.waitTicks(2);
        context.takeScreenshot("void-blight-crossbow-snap");
        context.getInput().pressKey(InputConstants.KEY_F1);

        // A share the client first sees on a link holding one mob has nothing to run along, and never runs down a thread formed after it
        server.runOnServer(minecraft ->
        {
            husk(minecraft, third).teleportTo(3.5, FLAT_FEET, 8);
            long now = minecraft.overworld().getGameTime();
            int lone = husk(minecraft, second).getId();
            player(minecraft).setAttached(EclipseLinks.LINK, new EclipseLink(List.of(lone), now + BlightBalance.LINK_TICKS, List.of(new EclipseLink.Share(lone, now))));
        });
        context.waitTicks(2);
        server.runOnServer(minecraft -> EclipseLinks.struck(player(minecraft), husk(minecraft, third)));
        for (int tick = 0; tick < 8; tick++)
        {
            context.waitTick();
            expect(context.computeOnClient(minecraft -> EclipseThreadRender.glintsDrawn() == 0), "Expected no glint from a share seen on a lone mob");
        }
        server.runOnServer(minecraft -> player(minecraft).removeAttached(EclipseLinks.LINK));

        // Left alone, a link comes apart in its time
        server.runOnServer(minecraft ->
        {
            EclipseLinks.struck(player(minecraft), husk(minecraft, second));
            EclipseLinks.struck(player(minecraft), husk(minecraft, third));
        });
        context.waitTicks(BlightBalance.LINK_TICKS - 20);
        expect(server.computeOnServer(minecraft -> link(minecraft).holds(minecraft.overworld().getGameTime())), "Expected the link to hold until its time");
        waitFor(context, server, 40, "Expected the link to come apart in its time", minecraft -> player(minecraft).getAttached(EclipseLinks.LINK) == null);

        // In the inventory it is the flat sprite
        context.getInput().pressKey(InputConstants.KEY_E);
        context.waitTicks(5);
        context.takeScreenshot("void-blight-crossbow-inventory");
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitTicks(3);
        server.runCommand("kill @e[type=!player]");
    }

    /** Loads the repeater by holding right click, then fires it at a mob with a real click and waits for the bolt to strike. */
    private static void loadAndFire(ClientGameTestContext context, TestServerContext server, UUID target, boolean shot)
    {
        var at = server.computeOnServer(minecraft -> husk(minecraft, target).getBoundingBox().getCenter());
        float health = server.computeOnServer(minecraft -> husk(minecraft, target).getHealth());
        aimFrom(context, server, 0.5, FLAT_FEET, 0.5, at.x, at.y, at.z, 3);
        context.getInput().holdMouse(InputConstants.MOUSE_BUTTON_RIGHT);
        context.waitTicks(BlightBalance.CROSSBOW_CHARGE_TICKS + 4);
        context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_RIGHT);
        waitFor(context, server, 10, "Expected the repeater loaded", minecraft -> CrossbowItem.isCharged(player(minecraft).getMainHandItem()));
        if (shot)
            context.takeScreenshot("void-blight-crossbow-loaded");
        aimFrom(context, server, 0.5, FLAT_FEET, 0.5, at.x, at.y, at.z, 2);
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
        waitFor(context, server, 40, "Expected the bolt to strike its pig", minecraft -> husk(minecraft, target).getHealth() < health);
    }

    private static EclipseLink link(MinecraftServer minecraft)
    {
        return player(minecraft).getAttachedOrElse(EclipseLinks.LINK, EclipseLink.NONE);
    }

    private static UUID rootedMob(MinecraftServer minecraft, EntityType<? extends Mob> type, double x, double z)
    {
        var mob = spawn(minecraft, type, x, FLAT_FEET, z);
        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(ROOTED);
        mob.setHealth(ROOTED);
        mob.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
        mob.setPersistenceRequired();
        return mob.getUUID();
    }

    private static List<ThrownBlightSpear> spears(MinecraftServer minecraft)
    {
        return List.copyOf(minecraft.overworld().getEntities(VoidHeartEntities.THROWN_BLIGHT_SPEAR, spear -> spear.isAlive()));
    }

    private static List<HarvestWraith> wraiths(MinecraftServer minecraft)
    {
        return List.copyOf(minecraft.overworld().getEntities(VoidHeartEntities.HARVEST_WRAITH, wraith -> wraith.isAlive()));
    }

    private static UUID standingHusk(MinecraftServer minecraft, double x, double z, float health)
    {
        var husk = spawn(minecraft, EntityTypes.HUSK, x, FLAT_FEET, z);
        husk.setNoAi(true);
        husk.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
        husk.setHealth(health);
        husk.setPersistenceRequired();
        return husk.getUUID();
    }

    private static void attack(ClientGameTestContext context, int afterTicks)
    {
        context.waitTicks(afterTicks);
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
    }

    private static LivingEntity husk(MinecraftServer minecraft, UUID id)
    {
        var entity = minecraft.overworld().getEntity(id);
        if (!(entity instanceof LivingEntity living))
            throw new AssertionError("Expected the target alive");
        return living;
    }

    private static void expectOpen(TestServerContext server, UUID target, int expected, String when)
    {
        int open = server.computeOnServer(minecraft ->
        {
            var husk = husk(minecraft, target);
            long now = minecraft.overworld().getGameTime();
            return RapierStrikes.of(husk).wounds().values().stream().mapToInt(wound -> wound.open(now)).sum();
        });
        expect(open == expected, "Expected " + expected + " open punctures " + when + ", got " + open);
    }
}
