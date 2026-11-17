package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.voxelindustry.voidheart.client.screen.BlueprintScreen;
import net.voxelindustry.voidheart.common.content.blight.BlightBalance;
import net.voxelindustry.voidheart.common.content.blight.RapierStrikes;
import net.voxelindustry.voidheart.common.content.blight.Wound;
import net.voxelindustry.voidheart.common.content.research.Knowledge;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;

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
    private static final int    CHARGED   = 13;

    @Override
    public void runTest(ClientGameTestContext context)
    {
        try (var world = context.worldBuilder().create())
        {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            freezeTime(server, "noon");
            server.runCommand("gamemode survival @a");
            server.runCommand("difficulty normal");
            clearArea(server, 0);
            server.runCommand("tp @a 0.5 " + FLAT_FEET + " 0.5 0 0");
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));

            contraption(context, server);
            rapier(context, server);
        }
    }

    private static void contraption(ClientGameTestContext context, TestServerContext server)
    {
        server.runCommand("execute as @a run voidheart blueprint forget all");
        server.runCommand("execute as @a run voidheart research grant voidheart:contravoid_reactor");
        server.runOnServer(minecraft -> player(minecraft).setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(VoidHeartItems.BLESSED_CONTRAPTION, 2)));
        context.waitTicks(5);
        expect(server.computeOnServer(minecraft -> !rapierDiscoverable(minecraft)), "Expected the rapier's research hidden before its blueprint is known");

        rightClick(context);
        context.waitFor(minecraft -> minecraft.gui.screen() instanceof BlueprintScreen, 40);
        int designs = context.computeOnClient(minecraft -> ((BlueprintScreen) minecraft.gui.screen()).designCount());
        expect(designs == 1, "Expected the contraption to offer the one design there is, got " + designs);
        context.takeScreenshot("void-blight-contraption-offer");

        var plate = context.computeOnClient(minecraft -> ((BlueprintScreen) minecraft.gui.screen()).plateCenter(0));
        click(context, plate);
        context.takeScreenshot("void-blight-contraption-picked");
        expect(server.computeOnServer(minecraft -> knowledge(minecraft).getBlueprints().isEmpty()), "Expected picking a plate alone to teach nothing");

        var keep = context.computeOnClient(minecraft -> ((BlueprintScreen) minecraft.gui.screen()).keepCenter());
        click(context, keep);
        context.waitTicks(5);
        expectClient(context, "Expected keeping a design to close the screen", minecraft -> minecraft.gui.screen() == null);
        server.runOnServer(minecraft ->
        {
            expect(knowledge(minecraft).knowsBlueprint(id("void_blight_rapier")), "Expected the rapier's blueprint learned");
            expect(player(minecraft).getMainHandItem().getCount() == 1, "Expected one contraption spent, holds " + player(minecraft).getMainHandItem().getCount());
            expect(rapierDiscoverable(minecraft), "Expected the rapier's research in sight once its blueprint is known");
        });

        // With every design known, the second one unfolds nothing
        rightClick(context);
        context.waitTicks(5);
        expectClient(context, "Expected no choice once every design is known", minecraft -> minecraft.gui.screen() == null);
        expect(!overlay(context).isEmpty(), "Expected the spent contraption to say why");
        expect(server.computeOnServer(minecraft -> player(minecraft).getMainHandItem().getCount() == 1), "Expected the unopened contraption kept");
    }

    private static boolean rapierDiscoverable(MinecraftServer minecraft)
    {
        var research = Knowledge.tree(minecraft.registryAccess()).get(id("void_blight_rapier"));
        return research != null && knowledge(minecraft).isDiscoverable(research);
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
