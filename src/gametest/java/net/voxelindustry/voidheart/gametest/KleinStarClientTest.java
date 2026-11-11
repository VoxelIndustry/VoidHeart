package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.client.KleinDim;
import net.voxelindustry.voidheart.client.render.KleinStarRender;
import net.voxelindustry.voidheart.common.config.VoidHeartConfig;
import net.voxelindustry.voidheart.common.content.apparatus.ShieldingApparatus;
import net.voxelindustry.voidheart.common.content.essence.EssenceRegistry;
import net.voxelindustry.voidheart.common.content.essence.RavenousStorageTile;
import net.voxelindustry.voidheart.common.content.kleinstar.KleinStar;
import net.voxelindustry.voidheart.common.content.kleinstar.KleinStarBalance;
import net.voxelindustry.voidheart.common.content.kleinstar.KleinStarItem;
import net.voxelindustry.voidheart.common.content.kleinstar.RadiantEffect;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartEffects;
import net.voxelindustry.voidheart.common.setup.VoidHeartEntities;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;

import java.util.UUID;

import static net.voxelindustry.voidheart.gametest.GameTestKit.*;

/**
 * The Klein Star: called with the item for Contravoid, it grows for two seconds, flies to the middle of the enemies around the healthiest it sees and
 * bursts there, hurting and leaving Radiant all within its blast and none beyond, then keeps the ground lit, which burns what steps on it.
 */
public class KleinStarClientTest implements FabricClientGameTest
{
    private static final double PLAYER_X  = 0.5;
    private static final double PLAYER_Z  = 0.5;
    private static final double CROWD_Z   = 12.5;
    private static final double[] CROWD_X = {-1.5, 0.5, 2.5};
    // Too far from the crowd for a blast among them to reach
    private static final double LONER_X   = 15.5;
    private static final float  STURDY    = 300;
    // A husk the third star goes for, the wall raised in front of it once seen, and where it is moved to with the star on its way
    private static final double HIDDEN_Z  = 14.5;
    private static final int    WALL_Z    = 9;
    private static final double MOVED_X   = 4.5;

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
            server.runCommand("tp @a " + PLAYER_X + " " + FLAT_FEET + " " + PLAYER_Z + " 0 -5");
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            context.getInput().pressKey(InputConstants.KEY_F1);

            server.runOnServer(minecraft ->
            {
                var source = player(minecraft).damageSources().source(KleinStar.DAMAGE);
                expect(source.is(DamageTypeTags.BYPASSES_SHIELD), "Expected the blast to pass a raised shield");
                expect(source.is(ShieldingApparatus.BYPASSES), "Expected the blast to pass a Shielding Apparatus");
                var burn = player(minecraft).damageSources().source(RadiantEffect.DAMAGE);
                expect(burn.is(ShieldingApparatus.BYPASSES) && burn.is(DamageTypeTags.BYPASSES_ARMOR), "Expected Radiant to pass armor and a Shielding Apparatus");
            });

            // Empty, it does not open
            server.runOnServer(minecraft -> player(minecraft).setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(VoidHeartItems.KLEIN_STAR)));
            context.waitTicks(5);
            rightClick(context);
            expect(server.computeOnServer(minecraft -> stars(minecraft) == 0), "Expected an empty Klein Star to call nothing");
            expect(!overlay(context).isEmpty(), "Expected an empty Klein Star to say what it lacks");

            // Filled from a Ravenous Storage holding Contravoid
            var storage = new BlockPos(-3, FLAT_FEET, 1);
            server.runOnServer(minecraft ->
            {
                var level = minecraft.overworld();
                level.setBlockAndUpdate(storage, VoidHeartBlocks.RAVENOUS_STORAGE.defaultBlockState());
                var tile = (RavenousStorageTile) level.getBlockEntity(storage);
                expect(tile.receive(EssenceRegistry.CONTRAVOID, 1200) == 1200, "Expected the storage to take Contravoid");
                useOn(player(minecraft), storage);
                expect(KleinStarItem.charge(held(minecraft)) == 1200, "Expected the Klein Star to drink the storage's Contravoid, holds " + KleinStarItem.charge(held(minecraft)));
                expect(tile.amountOf(EssenceRegistry.CONTRAVOID) == 0, "Expected the storage drained");
                level.removeBlock(storage, false);
            });

            var crowd = new UUID[CROWD_X.length];
            for (int i = 0; i < CROWD_X.length; i++)
            {
                int index = i;
                // The first is the healthiest, so the star starts on it
                crowd[i] = server.computeOnServer(minecraft -> sturdyHusk(minecraft, CROWD_X[index], CROWD_Z, index == 0 ? STURDY + 20 : STURDY));
            }
            var loner = server.computeOnServer(minecraft -> sturdyHusk(minecraft, LONER_X, CROWD_Z, STURDY - 50));
            context.waitTicks(10);

            // The staff as it is carried, from the front, from behind and from its bearer's own eyes with the hand showing
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
            context.waitTicks(3);
            context.takeScreenshot("klein-star-staff-idle-front");
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            context.waitTicks(3);
            context.takeScreenshot("klein-star-staff-idle-back");
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
            context.getInput().pressKey(InputConstants.KEY_F1);
            context.waitTicks(3);
            context.takeScreenshot("klein-star-staff-idle-first-person");
            context.getInput().pressKey(InputConstants.KEY_F1);
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            context.waitTicks(3);

            // Holding the staff, its bearer is shown where a star would burst: among the crowd they face
            context.waitTicks(3);
            var shown = context.computeOnClient(minecraft -> KleinStarRender.idleAim());
            expect(shown != null && Math.abs(shown.x - 0.46) < 0.6 && Math.abs(shown.z - CROWD_Z) < 0.6, "Expected the crowd shown as where a star would burst, shown " + shown);
            context.takeScreenshot("klein-star-preview-idle");

            int cost = VoidHeartConfig.get().kleinStarContravoidCost;
            rightClick(context);
            waitFor(context, server, 10, "Expected a Klein Star to appear", minecraft -> stars(minecraft) == 1);
            server.runOnServer(minecraft ->
            {
                expect(KleinStarItem.charge(held(minecraft)) == 1200 - cost, "Expected one star's Contravoid burned, holds " + KleinStarItem.charge(held(minecraft)));
                expect(player(minecraft).getCooldowns().isOnCooldown(held(minecraft)), "Expected the Klein Star to be on cooldown");
            });
            expectClient(context, "Expected the client to draw the star", minecraft -> KleinStarRender.activeStars() == 1);
            context.takeScreenshot("klein-star-appear");

            // Its growth, a frame every few ticks
            for (int frame = 0; frame < 2; frame++)
            {
                context.waitTicks(5);
                context.takeScreenshot("klein-star-grow-" + frame);
            }
            expectClient(context, "Expected no idle preview while a star grows", minecraft -> KleinStarRender.idleAim() == null);

            // Its bearer steers it by looking: at the lone husk, weaker than any of the crowd, the star turns to it, and back with their gaze
            server.runOnServer(minecraft ->
            {
                var aim = star(minecraft).aim();
                expect(Math.abs(aim.x - 0.46) < 0.6 && Math.abs(aim.z - CROWD_Z) < 0.6, "Expected the star to aim among the crowd its caster faces, aims at " + aim);
            });
            server.runCommand("tp @a " + aimedPose(PLAYER_X, FLAT_FEET, PLAYER_Z, LONER_X, FLAT_FEET + 1, CROWD_Z));
            context.waitTicks(2);
            server.runOnServer(minecraft ->
            {
                var aim = star(minecraft).aim();
                expect(Math.abs(aim.x - LONER_X) < 0.5 && Math.abs(aim.z - CROWD_Z) < 0.5, "Expected the star steered to the lone husk its caster looks at, aims at " + aim);
            });
            context.takeScreenshot("klein-star-preview-steered");
            server.runCommand("tp @a " + PLAYER_X + " " + FLAT_FEET + " " + PLAYER_Z + " 0 -5");
            context.waitTicks(2);

            // It grows on the end of the staff, and goes where the staff is carried
            server.runCommand("tp @a " + (PLAYER_X - 3) + " " + FLAT_FEET + " " + PLAYER_Z + " 0 -5");
            context.waitTicks(2);
            server.runOnServer(minecraft ->
            {
                var caster = player(minecraft);
                var tip = KleinStar.tip(minecraft.overworld(), caster, true, 1);
                var at = star(minecraft).center();
                expect(at.distanceTo(tip) < 0.05, "Expected the star on the end of the staff at " + tip + ", is at " + at);
                expect(at.x < PLAYER_X - 2 && at.distanceTo(caster.getEyePosition()) < 1.5, "Expected the star carried west with its caster, is at " + at);
            });
            server.runCommand("tp @a " + PLAYER_X + " " + FLAT_FEET + " " + PLAYER_Z + " 0 -5");
            context.waitTicks(2);
            expect(context.computeOnClient(minecraft -> KleinDim.lastDim()) > 0.3F, "Expected the scene dimmed around a grown star, dimmed " + context.computeOnClient(minecraft -> KleinDim.lastDim()));

            // Held still while the caster turns a little at a time: glow that overlaps itself must not change as the camera moves
            server.runCommand("tick freeze");
            for (int step = 0; step < 6; step++)
            {
                server.runCommand("tp @a " + PLAYER_X + " " + FLAT_FEET + " " + PLAYER_Z + " " + step * 2 + " -5");
                context.waitTicks(2);
                context.takeScreenshot("klein-star-sweep-" + step);
            }
            server.runCommand("tp @a " + PLAYER_X + " " + FLAT_FEET + " " + PLAYER_Z + " 0 -5");
            // The staff and the star on its end, from the front and from the caster's own eyes with the hand showing
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
            context.waitTicks(2);
            context.takeScreenshot("klein-star-staff-front");
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
            context.getInput().pressKey(InputConstants.KEY_F1);
            context.waitTicks(2);
            context.takeScreenshot("klein-star-staff-first-person");
            context.getInput().pressKey(InputConstants.KEY_F1);
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            context.waitTicks(3);
            server.runCommand("tick unfreeze");

            expect(context.computeOnClient(minecraft -> KleinStarRender.drawnCrumbs()) > 20,
                    "Expected the growing star to tear crumbs off the ground, tore " + context.computeOnClient(minecraft -> KleinStarRender.drawnCrumbs()));

            // Its flight, a frame a tick, and how far it strays from the straight way there
            waitFor(context, server, KleinStarBalance.GROW_TICKS + 10, "Expected the star to leave", minecraft -> stars(minecraft) == 0 || star(minecraft).launched());
            var left = server.computeOnServer(minecraft -> stars(minecraft) == 0 ? null : star(minecraft).center());
            expect(left != null, "Expected to see the star in flight");
            // Held still a few ticks out, seen from the side, for the streak it lays along its curve
            context.waitTicks(3);
            server.runCommand("tick freeze");
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
            aimFrom(context, server, PLAYER_X + 9, FLAT_FEET + 1, PLAYER_Z + 4, PLAYER_X, FLAT_FEET + 4, PLAYER_Z + 5, 2);
            context.takeScreenshot("klein-star-flight-held");
            server.runCommand("tp @a " + PLAYER_X + " " + FLAT_FEET + " " + PLAYER_Z + " 0 -5");
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            context.waitTicks(3);
            server.runCommand("tick unfreeze");
            var bound = new Vec3(0.5, FLAT_FEET + 1, CROWD_Z);
            double strayed = 0;
            for (int frame = 0; frame < KleinStarBalance.MAX_FLIGHT_TICKS && server.computeOnServer(minecraft -> stars(minecraft) == 1); frame++)
            {
                var at = server.computeOnServer(minecraft -> stars(minecraft) == 0 ? null : star(minecraft).center());
                if (at != null)
                {
                    var span = bound.subtract(left);
                    var onLine = left.add(span.scale(Math.clamp(at.subtract(left).dot(span) / span.lengthSqr(), 0, 1)));
                    strayed = Math.max(strayed, at.distanceTo(onLine));
                }
                if (frame < 6)
                    context.takeScreenshot("klein-star-flight-" + frame);
                context.waitTick();
            }
            expect(strayed > 1.5, "Expected the star to curve on its way, strayed " + strayed);

            waitFor(context, server, KleinStarBalance.MAX_FLIGHT_TICKS + 10, "Expected the star to burst", minecraft -> stars(minecraft) == 0);
            context.waitTick();
            context.takeScreenshot("klein-star-burst-0");
            context.waitTicks(2);
            context.takeScreenshot("klein-star-burst-2");
            context.waitTicks(3);
            context.takeScreenshot("klein-star-burst-5");

            var burstAt = context.computeOnClient(minecraft -> KleinStarRender.lastBurstCenter());
            expect(burstAt != null, "Expected the client to be told of the burst");
            // The healthiest pulls a little harder than the two others
            expect(Math.abs(burstAt.x - 0.46) < 0.3 && Math.abs(burstAt.z - CROWD_Z) < 0.3, "Expected the burst in the middle of the crowd, at " + burstAt);
            expectClient(context, "Expected the burst to light the ground", minecraft -> KleinStarRender.litFaces() > 50);

            server.runOnServer(minecraft ->
            {
                for (var id : crowd)
                {
                    var husk = (Husk) minecraft.overworld().getEntity(id);
                    float lost = husk.getMaxHealth() - husk.getHealth();
                    expect(lost > 25 && lost <= 42, "Expected the blast to hurt each of the crowd for most of its damage, lost " + lost);
                    expect(husk.hasEffect(VoidHeartEffects.RADIANT), "Expected the crowd left Radiant");
                }
                var far = (Husk) minecraft.overworld().getEntity(loner);
                expect(far.getHealth() == far.getMaxHealth() && !far.hasEffect(VoidHeartEffects.RADIANT), "Expected the blast not to reach a husk 13 blocks from it");
                expect(player(minecraft).getHealth() == player(minecraft).getMaxHealth(), "Expected the caster out of its own blast at 12 blocks");
            });

            // Radiant keeps burning
            float afterBlast = server.computeOnServer(minecraft -> health(minecraft, crowd[1]));
            context.waitTicks(45);
            expect(server.computeOnServer(minecraft -> health(minecraft, crowd[1])) < afterBlast, "Expected Radiant to keep burning the crowd");
            context.takeScreenshot("klein-star-cover");

            // The lit ground burns what steps onto it, and only that
            server.runOnServer(minecraft -> minecraft.overworld().getEntity(loner).snapTo(4.5, FLAT_FEET, CROWD_Z + 2, 90, 0));
            waitFor(context, server, KleinStarBalance.COVER_INTERVAL + 10, "Expected the lit ground to leave a newcomer Radiant",
                    minecraft -> ((LivingEntity) minecraft.overworld().getEntity(loner)).hasEffect(VoidHeartEffects.RADIANT));

            // The cover seen close and low, where it would fight the ground it lies on
            server.runCommand("gamemode spectator @a");
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
            server.runCommand("tick freeze");
            for (int step = 0; step < 6; step++)
            {
                aimFrom(context, server, 0.5 + step * 0.06, FLAT_FEET + 0.2, CROWD_Z - 9 + step * 0.08, 0.5, FLAT_FEET, CROWD_Z, 2);
                context.takeScreenshot("klein-star-cover-sweep-" + step);
            }
            aimFrom(context, server, 0.5, FLAT_FEET + 9, CROWD_Z - 6, 0.5, FLAT_FEET, CROWD_Z, 2);
            context.takeScreenshot("klein-star-cover-above");
            server.runCommand("tick unfreeze");

            // Drawing in toward the middle as it ends
            context.waitTicks(KleinStarBalance.COVER_TICKS - 45 - 60);
            context.takeScreenshot("klein-star-cover-fading");
            context.waitTicks(60);
            expectClient(context, "Expected the cover gone once its time is up", minecraft -> KleinStarRender.lastBurstCenter() == null);
            server.runOnServer(minecraft ->
            {
                var stepped = (LivingEntity) minecraft.overworld().getEntity(loner);
                stepped.removeEffect(VoidHeartEffects.RADIANT);
            });
            context.waitTicks(KleinStarBalance.COVER_INTERVAL + 5);
            expect(server.computeOnServer(minecraft -> !((LivingEntity) minecraft.overworld().getEntity(loner)).hasEffect(VoidHeartEffects.RADIANT)),
                    "Expected the ground to stop burning once the cover is gone");

            // At night, where the star is the only light
            server.runCommand("time set midnight");
            server.runCommand("gamemode survival @a");
            server.runCommand("tp @a " + PLAYER_X + " " + FLAT_FEET + " " + PLAYER_Z + " 0 -5");
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            context.waitTicks(10);
            rightClick(context);
            waitFor(context, server, 10, "Expected a second Klein Star once the cooldown is over", minecraft -> stars(minecraft) == 1);
            context.waitTicks(30);
            context.takeScreenshot("klein-star-night-grown");
            waitFor(context, server, KleinStarBalance.GROW_TICKS + KleinStarBalance.MAX_FLIGHT_TICKS, "Expected the second star to burst", minecraft -> stars(minecraft) == 0);
            context.waitTicks(12);
            context.takeScreenshot("klein-star-night-cover");

            // What it saw while it grew it still goes for behind a wall raised since, around the wall, and follows it when it moves
            server.runCommand("time set noon");
            clearScene(server);
            waitFor(context, server, 260, "Expected the cooldown to end", minecraft -> !player(minecraft).getCooldowns().isOnCooldown(held(minecraft)));
            server.runOnServer(minecraft -> KleinStarItem.setCharge(held(minecraft), KleinStarBalance.CAPACITY));
            var hidden = server.computeOnServer(minecraft -> sturdyHusk(minecraft, 0.5, HIDDEN_Z, STURDY));
            server.runCommand("tp @a " + PLAYER_X + " " + FLAT_FEET + " " + PLAYER_Z + " 0 -5");
            context.waitTicks(10);
            rightClick(context);
            waitFor(context, server, 10, "Expected a third Klein Star", minecraft -> stars(minecraft) == 1);
            context.waitTicks(8);
            server.runCommand("fill -6 " + FLAT_FEET + " " + WALL_Z + " 7 " + (FLAT_FEET + 4) + " " + WALL_Z + " stone");
            context.waitTicks(6);
            server.runOnServer(minecraft ->
            {
                var aim = star(minecraft).aim();
                expect(Math.abs(aim.x - 0.5) < 0.5 && Math.abs(aim.z - HIDDEN_Z) < 0.5, "Expected the star to still aim at the husk behind the new wall, aims at " + aim);
            });
            context.takeScreenshot("klein-star-preview-wall");

            waitFor(context, server, KleinStarBalance.GROW_TICKS + 10, "Expected the star to leave for the hidden husk", minecraft -> stars(minecraft) == 0 || star(minecraft).launched());
            // On its way, the husk is moved aside behind the wall
            context.waitTicks(2);
            server.runOnServer(minecraft -> minecraft.overworld().getEntity(hidden).snapTo(MOVED_X, FLAT_FEET, HIDDEN_Z, 90, 0));
            context.waitTicks(2);
            server.runCommand("tick freeze");
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
            aimFrom(context, server, PLAYER_X + 12, FLAT_FEET + 5, WALL_Z - 6, 0.5, FLAT_FEET + 3, WALL_Z, 2);
            context.takeScreenshot("klein-star-around-wall");
            server.runCommand("tp @a " + PLAYER_X + " " + FLAT_FEET + " " + PLAYER_Z + " 0 -5");
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            context.waitTicks(3);
            server.runCommand("tick unfreeze");

            waitFor(context, server, KleinStarBalance.MAX_FLIGHT_TICKS + 10, "Expected the third star to burst", minecraft -> stars(minecraft) == 0);
            context.waitTicks(2);
            var behindWall = context.computeOnClient(minecraft -> KleinStarRender.lastBurstCenter());
            expect(behindWall != null && behindWall.z > WALL_Z + 1, "Expected the star to burst past the wall, not against it, burst at " + behindWall);
            expect(Math.abs(behindWall.x - MOVED_X) < 1.5 && Math.abs(behindWall.z - HIDDEN_Z) < 1.5, "Expected the star to follow the husk to where it moved, burst at " + behindWall);
            server.runOnServer(minecraft ->
            {
                var husk = (LivingEntity) minecraft.overworld().getEntity(hidden);
                expect(husk.getHealth() < husk.getMaxHealth() - 25, "Expected the husk behind the wall hurt, has " + husk.getHealth());
            });
            context.takeScreenshot("klein-star-past-wall");
            server.runCommand("fill -6 " + FLAT_FEET + " " + WALL_Z + " 7 " + (FLAT_FEET + 4) + " " + WALL_Z + " air");

            clearScene(server);
            server.runCommand("gamemode creative @a");
            context.getInput().pressKey(InputConstants.KEY_F1);
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));

            // The item in hand and on the hotbar, with what Contravoid it has left
            server.runCommand("time set noon");
            context.waitTicks(10);
            context.takeScreenshot("klein-star-item-held");
        }
    }

    private static ItemStack held(MinecraftServer minecraft)
    {
        return player(minecraft).getItemInHand(InteractionHand.MAIN_HAND);
    }

    private static int stars(MinecraftServer minecraft)
    {
        return minecraft.overworld().getEntities(VoidHeartEntities.KLEIN_STAR, star -> true).size();
    }

    private static KleinStar star(MinecraftServer minecraft)
    {
        return minecraft.overworld().getEntities(VoidHeartEntities.KLEIN_STAR, star -> true).getFirst();
    }

    private static float health(MinecraftServer minecraft, UUID id)
    {
        return ((LivingEntity) minecraft.overworld().getEntity(id)).getHealth();
    }

    /**
     * A husk that stands where it is put and outlives a blast.
     */
    private static UUID sturdyHusk(MinecraftServer minecraft, double x, double z, float health)
    {
        var husk = spawn(minecraft, EntityTypes.HUSK, x, FLAT_FEET, z);
        husk.setNoAi(true);
        husk.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
        husk.setHealth(health);
        husk.setPersistenceRequired();
        return husk.getUUID();
    }
}
