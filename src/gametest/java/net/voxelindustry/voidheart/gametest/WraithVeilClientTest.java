package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.voxelindustry.voidheart.client.TunnelVision;
import net.voxelindustry.voidheart.client.render.WraithInk;
import net.voxelindustry.voidheart.common.content.mob.WraithStain;
import net.voxelindustry.voidheart.common.content.wraithveil.WallPassagePlan;
import net.voxelindustry.voidheart.common.content.wraithveil.WallPassagePlan.Plan;
import net.voxelindustry.voidheart.common.content.wraithveil.WallPassages;

import java.util.function.Predicate;

import static net.voxelindustry.voidheart.gametest.GameTestKit.*;

/**
 * A survival player opens a Wraith Veil tunnel through a six block wall, walks in, sees a zombie hidden in the stone beside it, and is pushed out
 * unhurt when it collapses. Then down through a floor and up through a ceiling, and turned away by fluid, bedrock and a wall too thick.
 */
public class WraithVeilClientTest implements FabricClientGameTest
{
    private static final int FEET = FLAT_FEET;

    @Override
    public void runTest(ClientGameTestContext context)
    {
        try (var world = context.worldBuilder().create())
        {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            freezeTime(server, "noon");
            clearArea(server, 0);
            clearScene(server);
            server.runCommand("gamemode survival @a");
            server.runCommand("clear @a");
            server.runCommand("item replace entity @a hotbar.0 with voidheart:wraith_veil");
            context.runOnClient(minecraft ->
            {
                minecraft.options.setCameraType(CameraType.FIRST_PERSON);
                minecraft.player.getInventory().setSelectedSlot(0);
            });

            // A wall six blocks thick with a zombie shut in a pocket beside the way through, and a marker on either side
            server.runCommand("fill 2 " + FEET + " -4 7 " + (FEET + 3) + " 4 stone");
            server.runCommand("fill 4 " + FEET + " 2 5 " + (FEET + 1) + " 3 air");
            server.runCommand("summon zombie 4.5 " + FEET + " 2.5 {NoAI:1b,PersistenceRequired:1b,Silent:1b}");
            server.runCommand("setblock -4 " + FEET + " 0 lapis_block");
            server.runCommand("setblock 13 " + FEET + " 0 gold_block");
            server.runCommand("setblock 13 " + (FEET + 1) + " 0 gold_block");
            aimCamera(server, 0.5, FEET, 0.5, 2, FEET + 1, 0.5);
            context.waitTicks(10);
            context.getInput().pressKey(InputConstants.KEY_F1);

            expectClient(context, "a crossable wall to show a preview", minecraft -> previewPlan(minecraft) != null);
            context.takeScreenshot("wraith-veil-preview");

            rightClick(context);
            waitFor(context, server, 20, "the tunnel to be marked", minecraft -> WallPassages.isPassing(player(minecraft)));
            // Back off to see the whole mark spread, its hole open, then the hole from up close
            aimCamera(server, -2.5, FEET, 0.5, 2, FEET + 1, 0.5);
            for (int at : new int[]{5, 9, 13})
            {
                int ticks = at;
                waitForClient(context, 40, "the mark to spread", minecraft -> WallPassages.ticksOf(minecraft.player) >= ticks);
                context.takeScreenshot("wraith-veil-mark-spread-" + at);
            }
            waitForClient(context, 40, "the hole to open", minecraft -> WallPassages.ticksOf(minecraft.player) >= WallPassagePlan.MARK_TICKS + 3);
            context.takeScreenshot("wraith-veil-mark-hole-opening");
            waitForClient(context, 40, "the tunnel to be boring in", minecraft -> WallPassages.ticksOf(minecraft.player) >= WallPassagePlan.MARK_TICKS + 8);
            context.takeScreenshot("wraith-veil-boring");
            waitForClient(context, 40, "the hole to be open", minecraft -> WallPassages.ticksOf(minecraft.player) >= WallPassagePlan.MARK_TICKS + 10);
            // The hole is a keyhole: the client has erased the stone behind it, which the server keeps
            var inWall = new BlockPos(4, FEET, 0);
            expectClient(context, "the stone inside the tunnel to be erased from outside", minecraft -> minecraft.level.getBlockState(inWall).isAir());
            server.runOnServer(minecraft -> expect(minecraft.overworld().getBlockState(inWall).is(Blocks.STONE), "Expected the server to keep the stone"));
            context.takeScreenshot("wraith-veil-mark-open");
            aimCamera(server, -1.5, FEET, 2.5, 2, FEET + 1, 0.5);
            context.waitTicks(2);
            context.takeScreenshot("wraith-veil-keyhole-angled");
            aimCamera(server, 0.5, FEET, 0.5, 2, FEET + 1, 0.5);
            context.waitTicks(2);
            context.takeScreenshot("wraith-veil-mark-open-close");

            context.getInput().holdKey(options -> options.keyUp);
            waitForClient(context, 80, "the player to walk into the wall", minecraft -> minecraft.player.getX() > 4.1);
            context.getInput().releaseKey(options -> options.keyUp);
            context.waitTicks(10);
            server.runOnServer(minecraft ->
            {
                var player = player(minecraft);
                expect(player.getX() > 3.5 && player.getX() < 6, "Expected the server to let the player walk into the tunnel, at " + player.position());
                expect(player.getHealth() >= player.getMaxHealth(), "Expected no suffocation in the tunnel, health " + player.getHealth());
            });
            expectClient(context, "the zombie in the stone to show through the tunnel wall",
                    minecraft -> minecraft.level.getEntitiesOfClass(Zombie.class, new AABB(3, FEET, 2, 6, FEET + 2, 4))
                            .stream().anyMatch(zombie -> TunnelVision.silhouette(zombie, 0) > 0));

            context.takeScreenshot("wraith-veil-inside-ahead");
            turn(context, 90, 0);
            context.takeScreenshot("wraith-veil-inside-behind");
            turn(context, 0, 0);
            context.takeScreenshot("wraith-veil-inside-zombie");
            turn(context, -90, 0);

            waitForClient(context, WallPassagePlan.OPEN_TICKS + 40, "the collapse to begin",
                    minecraft -> WallPassages.ticksOf(minecraft.player) > WallPassagePlan.MARK_TICKS + WallPassagePlan.OPEN_TICKS + WallPassagePlan.COLLAPSE_TICKS / 3);
            context.takeScreenshot("wraith-veil-collapsing");
            waitFor(context, server, 120, "the tunnel to close", minecraft -> !WallPassages.isPassing(player(minecraft)));
            context.waitTicks(5);
            server.runOnServer(minecraft ->
            {
                var player = player(minecraft);
                expect(Math.abs(player.getX() - 1.5) < 0.3, "Expected the collapse to push the player out of the nearer mouth at x 1.5, got " + player.position());
                expect(player.getHealth() >= player.getMaxHealth(), "Expected the collapse to leave the player unhurt, health " + player.getHealth());
                expect(player.level().noCollision(player, player.getBoundingBox()), "Expected the player clear of the wall");
                expect(player.getCooldowns().isOnCooldown(player.getMainHandItem()), "Expected the veil to rest after a tunnel");
            });
            aimCamera(server, -1.5, FEET, 0.5, 2, FEET + 1, 0.5);
            context.waitTicks(3);
            context.takeScreenshot("wraith-veil-closed");
            context.waitTicks(15);
            context.takeScreenshot("wraith-veil-closed-receding");
            server.runCommand("kill @e[type=zombie]");

            // Fluid inside the wall: no preview, and using it does nothing
            clearCooldown(server);
            server.runCommand("fill 4 " + FEET + " 0 4 " + (FEET + 1) + " 0 water");
            aimCamera(server, 0.5, FEET, 0.5, 2, FEET + 1, 0.5);
            // Long enough for the marks to have faded, so the screenshot only shows a preview
            context.waitTicks(50);
            expectRefused(context, server, "water inside the wall");
            context.takeScreenshot("wraith-veil-fluid-no-mark");

            server.runCommand("fill 4 " + FEET + " 0 4 " + (FEET + 1) + " 0 bedrock");
            context.waitTicks(3);
            expectRefused(context, server, "bedrock inside the wall");

            // Eleven blocks is one too many, ten is fine
            server.runCommand("fill 2 " + FEET + " 8 12 " + (FEET + 3) + " 12 stone");
            aimCamera(server, 0.5, FEET, 10.5, 2, FEET + 1, 10.5);
            context.waitTicks(10);
            expectRefused(context, server, "an eleven block wall");
            server.runCommand("fill 12 " + FEET + " 8 12 " + (FEET + 3) + " 12 air");
            context.waitTicks(3);
            expectClient(context, "a ten block wall to be crossable", minecraft -> previewPlan(minecraft) != null);

            // Ground a block higher at the far side of a five block wall: the tunnel stays two tall, takes the first block of it in, erased, and the
            // player jumps up out of it onto the rest
            server.runCommand("fill 7 " + FEET + " 8 12 " + (FEET + 3) + " 12 air");
            server.runCommand("fill 7 " + FEET + " 10 9 " + FEET + " 10 cobblestone");
            context.waitTicks(3);
            expectClient(context, "the tunnel to come out on the raised ground", minecraft ->
            {
                var plan = previewPlan(minecraft);
                return plan != null && Math.abs(plan.exitSpot().y - (FEET + 1)) < 1e-6 && plan.tubeMax().getY() - plan.tubeMin().getY() == 1
                       && plan.tubeMax().getX() == 7;
            });
            rightClick(context);
            waitForClient(context, 40, "the stepped tunnel to open",
                    minecraft -> WallPassages.ticksOf(minecraft.player) > WallPassagePlan.MARK_TICKS + WallPassagePlan.BORE_TICKS + 2);
            context.getInput().holdKey(options -> options.keyUp);
            waitForClient(context, 80, "the player to walk into the stepped tunnel", minecraft -> minecraft.player.getX() > 5);
            context.takeScreenshot("wraith-veil-step-up-inside");
            context.getInput().holdKey(options -> options.keyJump);
            waitForClient(context, 80, "the player to climb onto the step",
                    minecraft -> minecraft.player.getX() > 7.2 && minecraft.player.getY() > FEET + 0.99 && minecraft.player.onGround());
            context.getInput().releaseKey(options -> options.keyJump);
            context.getInput().releaseKey(options -> options.keyUp);
            context.waitTicks(5);
            server.runOnServer(minecraft ->
            {
                var player = player(minecraft);
                expect(player.getX() > 7 && player.getY() > FEET + 0.99, "Expected the player on the raised ground past the wall, got " + player.position());
                expect(player.getHealth() >= player.getMaxHealth(), "Expected no harm from the stepped tunnel, health " + player.getHealth());
            });
            waitFor(context, server, Plan.totalTicks(), "the stepped tunnel to close", minecraft -> !WallPassages.isPassing(player(minecraft)));
            waitForClient(context, 40, "the client to close the stepped tunnel", minecraft -> !WallPassages.isPassing(minecraft.player));
            clearCooldown(server);
            // Long enough for the old marks to have dried away, so the screenshot only shows a preview
            context.waitTicks(WraithStain.FADE_TICKS + 5);

            // A step in front of the wall, used at head height from below it: the tunnel starts on top of the step
            server.runCommand("setblock 1 " + FEET + " 10 cobblestone");
            aimCamera(server, 0.5, FEET, 10.5, 2, FEET + 1.5, 10.5);
            context.waitTicks(3);
            expectClient(context, "a step in front to be stood on", minecraft ->
            {
                var plan = previewPlan(minecraft);
                return plan != null && Math.abs(plan.entrySpot().y - (FEET + 1)) < 1e-6;
            });
            context.takeScreenshot("wraith-veil-step-preview");

            // Standing on a slab, the tunnel grows three tall so the head clears it
            server.runCommand("setblock 1 " + FEET + " 10 stone_slab");
            aimCamera(server, 1.3, FEET + 0.5, 10.5, 2, FEET + 1, 10.5);
            context.waitTicks(3);
            expectClient(context, "the slab underfoot to be taken into a two tall tunnel", minecraft ->
            {
                var plan = previewPlan(minecraft);
                return plan != null && plan.tubeMax().getY() - plan.tubeMin().getY() == 1 && plan.tubeMin().getX() == 1;
            });
            server.runCommand("setblock 1 " + FEET + " 10 air");

            // Glass inside the wall and a post two blocks tall at the far side: the tunnel goes on through both, erased from sight while open
            server.runCommand("fill 7 " + FEET + " 10 9 " + FEET + " 10 air");
            server.runCommand("setblock 4 " + (FEET + 1) + " 10 glass");
            server.runCommand("fill 7 " + FEET + " 10 7 " + (FEET + 1) + " 10 cobblestone");
            aimCamera(server, 0.5, FEET, 10.5, 2, FEET + 1, 10.5);
            context.waitTicks(3);
            expectClient(context, "the tunnel to go on through the post", minecraft ->
            {
                var plan = previewPlan(minecraft);
                return plan != null && plan.depth() == 6 && Math.abs(plan.exitSpot().x - 8.5) < 1e-6;
            });
            rightClick(context);
            waitForClient(context, 40, "the tunnel through the post to open",
                    minecraft -> WallPassages.ticksOf(minecraft.player) > WallPassagePlan.MARK_TICKS + WallPassagePlan.BORE_TICKS + 2);
            var post = new BlockPos(7, FEET, 10);
            var glass = new BlockPos(4, FEET + 1, 10);
            // Both are erased, but the post's faces out in the open are drawn again, so from outside it still shows
            expectClient(context, "the post and the glass to be erased, and the post's open sides drawn", minecraft ->
                    minecraft.level.getBlockState(post).isAir() && minecraft.level.getBlockState(glass).isAir()
                    && WallPassages.shownFromOutside(minecraft.player).stream().anyMatch(shown -> shown.pos().equals(post)
                                                                                                && shown.sides().contains(Direction.NORTH)
                                                                                                && shown.sides().contains(Direction.SOUTH)
                                                                                                && !shown.sides().contains(Direction.WEST)));
            server.runOnServer(minecraft -> expect(minecraft.overworld().getBlockState(post).is(Blocks.COBBLESTONE)
                                                   && minecraft.overworld().getBlockState(glass).is(Blocks.GLASS), "Expected the server to keep the post and the glass"));
            // From outside, beside the wall: the post is gone and no part of the tunnel shows, then from in front, only the mark
            aimCamera(server, 10.5, FEET, 14.5, 6.5, FEET + 1, 10.5);
            context.waitTicks(2);
            context.takeScreenshot("wraith-veil-erased-outside-side");
            aimCamera(server, 0.5, FEET, 10.5, 2, FEET + 1, 10.5);
            context.waitTicks(2);
            context.takeScreenshot("wraith-veil-erased-outside-front");
            context.getInput().holdKey(options -> options.keyUp);
            waitForClient(context, 80, "the player to walk into the erased tunnel", minecraft -> minecraft.player.getX() > 4.6);
            expectClient(context, "the client to have erased the post and the glass with the player inside", minecraft ->
                    minecraft.level.getBlockState(post).isAir() && minecraft.level.getBlockState(glass).isAir());
            context.takeScreenshot("wraith-veil-erased-inside");
            waitForClient(context, 80, "the player to come out past the post", minecraft -> minecraft.player.getX() > 8.2);
            context.getInput().releaseKey(options -> options.keyUp);
            context.waitTicks(5);
            expectClient(context, "the post to stay erased while the tunnel is open", minecraft ->
                    WallPassages.isPassing(minecraft.player) && minecraft.level.getBlockState(post).isAir());
            server.runOnServer(minecraft ->
            {
                var player = player(minecraft);
                expect(player.getX() > 8 && player.getHealth() >= player.getMaxHealth(), "Expected the player past the post unhurt, got " + player.position());
            });
            waitFor(context, server, Plan.totalTicks(), "the tunnel through the post to close", minecraft -> !WallPassages.isPassing(player(minecraft)));
            waitForClient(context, 40, "the client to close the tunnel through the post", minecraft -> !WallPassages.isPassing(minecraft.player));
            context.waitTicks(3);
            expectClient(context, "the client to have put the post and the glass back", minecraft ->
                    minecraft.level.getBlockState(post).is(Blocks.COBBLESTONE) && minecraft.level.getBlockState(glass).is(Blocks.GLASS));
            clearCooldown(server);

            // A wall only two blocks high: the ink stops at its top edge rather than hanging in the air above it
            server.runCommand("fill 2 " + FEET + " -11 4 " + (FEET + 1) + " -9 stone");
            aimCamera(server, 0.5, FEET, -9.5, 2, FEET + 1, -9.5);
            context.waitTicks(3);
            expectClient(context, "the low wall to be crossable", minecraft -> previewPlan(minecraft) != null);
            expectClient(context, "the low wall's mark to keep off the air above it", minecraft ->
            {
                var mark = previewPlan(minecraft).entry();
                var parts = WraithInk.holding(minecraft.level, mark.face(), mark.center(), (float) mark.halfU(), (float) mark.halfV());
                return !parts.isEmpty() && parts.stream().allMatch(part -> mark.center().y + part[3] <= FEET + 2 + 1e-4);
            });
            rightClick(context);
            waitForClient(context, 40, "the low wall's hole to open",
                    minecraft -> WallPassages.ticksOf(minecraft.player) > WallPassagePlan.MARK_TICKS + WallPassagePlan.BORE_TICKS + 2);
            aimCamera(server, -2.5, FEET + 1, -7.5, 2, FEET + 1, -9.5);
            context.waitTicks(2);
            context.takeScreenshot("wraith-veil-low-wall-mark");
            // Seen through the keyhole, the collapse pinches the tunnel shut from its middle out to the mouths
            aimCamera(server, -1.5, FEET, -9.5, 2, FEET + 1, -9.5);
            for (int percent : new int[]{40, 75})
            {
                int at = WallPassagePlan.MARK_TICKS + WallPassagePlan.OPEN_TICKS + WallPassagePlan.COLLAPSE_TICKS * percent / 100;
                waitForClient(context, Plan.totalTicks(), "the low wall's tunnel to pinch", minecraft -> WallPassages.ticksOf(minecraft.player) >= at);
                context.takeScreenshot("wraith-veil-low-wall-pinch-" + percent);
            }
            waitFor(context, server, Plan.totalTicks(), "the low wall's tunnel to close", minecraft -> !WallPassages.isPassing(player(minecraft)));
            waitForClient(context, 40, "the client to close the low wall's tunnel", minecraft -> !WallPassages.isPassing(minecraft.player));
            clearCooldown(server);

            // Erasing, seen from inside: through a wall one block thick, along a long tunnel, and into a hill whose grass is level with the top
            server.runCommand("fill 2 " + FEET + " 18 2 " + (FEET + 3) + " 22 stone");
            server.runCommand("fill 2 " + FEET + " 24 9 " + (FEET + 3) + " 28 stone");
            server.runCommand("setblock 13 " + FEET + " 26 gold_block");
            server.runCommand("fill 2 " + FEET + " 30 6 " + FEET + " 34 dirt");
            server.runCommand("fill 2 " + (FEET + 1) + " 30 6 " + (FEET + 1) + " 34 grass_block");
            walkAndLook(context, server, 20.5, 1.9, "thin");
            walkAndLook(context, server, 26.5, 3.5, "long");
            walkAndLook(context, server, 32.5, 2.3, "hill");
            server.runOnServer(minecraft -> expect(minecraft.overworld().getBlockState(new BlockPos(2, FEET + 1, 32)).is(Blocks.GRASS_BLOCK),
                    "Expected the server to keep the hill's grass"));

            // Down through a floor three blocks thick: the shaft is climbed down like a ladder
            server.runCommand("fill -9 " + (FEET + 4) + " -9 -5 " + (FEET + 6) + " -5 stone");
            server.runCommand("tp @a -6.5 " + (FEET + 7) + " -6.5 0 90");
            context.waitTicks(10);
            expectClient(context, "the floor to be crossable", minecraft -> previewPlan(minecraft) != null);
            rightClick(context);
            waitForClient(context, 120, "the player to sink into the floor", minecraft -> minecraft.player.getY() < FEET + 5.5);
            context.takeScreenshot("wraith-veil-floor-inside");
            waitForClient(context, 120, "the player to come out under the floor", minecraft -> minecraft.player.getY() < FEET + 2.5);
            context.waitTicks(20);
            server.runOnServer(minecraft ->
            {
                var player = player(minecraft);
                expect(player.getY() < FEET + 4, "Expected the player under the floor, got " + player.position());
                expect(player.getHealth() >= player.getMaxHealth(), "Expected no harm from the floor, health " + player.getHealth());
            });

            // The floor's tunnel stays open after the player has left it, until it collapses
            waitFor(context, server, Plan.totalTicks(), "the floor tunnel to close", minecraft -> !WallPassages.isPassing(player(minecraft)));
            waitForClient(context, 40, "the client to close the floor tunnel", minecraft -> !WallPassages.isPassing(minecraft.player));

            // Up through a ceiling two blocks thick, three blocks above: climbed with jump
            clearCooldown(server);
            server.runCommand("fill -16 " + (FEET + 3) + " 5 -12 " + (FEET + 4) + " 9 stone");
            server.runCommand("tp @a -13.5 " + FEET + " 7.5 0 -90");
            context.waitTicks(10);
            expectClient(context, "the ceiling to be crossable", minecraft -> previewPlan(minecraft) != null);
            rightClick(context);
            waitFor(context, server, 20, "the ceiling tunnel to be marked", minecraft -> WallPassages.isPassing(player(minecraft)));
            waitForClient(context, 40, "the ceiling tunnel to open", minecraft -> WallPassages.ticksOf(minecraft.player) > WallPassagePlan.MARK_TICKS + 2);
            context.getInput().holdKey(options -> options.keyJump);
            waitForClient(context, 120, "the player to climb above the ceiling", minecraft -> minecraft.player.getY() > FEET + 4.9);
            context.getInput().holdKey(options -> options.keyUp);
            context.waitTicks(10);
            context.getInput().releaseKey(options -> options.keyJump);
            context.getInput().releaseKey(options -> options.keyUp);
            context.waitTicks(20);
            server.runOnServer(minecraft ->
            {
                var player = player(minecraft);
                expect(player.getY() >= FEET + 5 - 0.01, "Expected the player on top of the ceiling, got " + player.position());
                expect(player.getHealth() >= player.getMaxHealth(), "Expected no harm from the ceiling, health " + player.getHealth());
            });

            // The wraith's own stain spreads the same ink, without a hole, then recedes
            server.runCommand("fill -21 " + FEET + " -2 -21 " + (FEET + 3) + " 2 stone");
            aimCamera(server, -17.5, FEET, 0.5, -20, FEET + 1.5, 0.5);
            context.waitTicks(3);
            server.runOnServer(minecraft -> WraithStain.on(minecraft.overworld(), new BlockPos(-21, FEET + 1, 0), Direction.EAST));
            context.waitTicks(WraithStain.GROW_TICKS / 2);
            context.takeScreenshot("wraith-stain-spreading");
            context.waitTicks(WraithStain.GROW_TICKS / 2 + 4);
            context.takeScreenshot("wraith-stain-full");
            context.waitTicks(WraithStain.LINGER_TICKS + WraithStain.FADE_TICKS / 2);
            context.takeScreenshot("wraith-stain-receding");

            context.getInput().pressKey(InputConstants.KEY_F1);
            server.runCommand("gamemode creative @a");
            server.runCommand("clear @a");
        }
    }

    private static WallPassagePlan.Plan previewPlan(Minecraft minecraft)
    {
        if (!(minecraft.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK)
            return null;
        var player = minecraft.player;
        if (player.getCooldowns().isOnCooldown(player.getMainHandItem()))
            return null;
        return WallPassagePlan.plan(WallPassages.cells(player.level(), player), hit.getBlockPos(), hit.getDirection(), player.position());
    }

    private static void clearCooldown(TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            var cooldowns = player(minecraft).getCooldowns();
            cooldowns.removeCooldown(cooldowns.getCooldownGroup(player(minecraft).getMainHandItem()));
        });
    }

    private static void expectRefused(ClientGameTestContext context, TestServerContext server, String what)
    {
        expectClient(context, "no preview with " + what, minecraft -> previewPlan(minecraft) == null);
        var before = server.computeOnServer(minecraft -> player(minecraft).position());
        rightClick(context);
        context.waitTicks(5);
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            expect(!WallPassages.isPassing(player), "Expected no tunnel with " + what);
            expect(player.position().distanceTo(before) < 0.05, "Expected the player to stay put with " + what);
        });
    }

    /**
     * Opens a tunnel east from x 0.5 at this z, looks into it from outside, walks in until the feet pass stopX, looks ahead and back, then waits
     * for it to close.
     */
    private static void walkAndLook(ClientGameTestContext context, TestServerContext server, double z, double stopX, String name)
    {
        aimCamera(server, 0.5, FEET, z, 2, FEET + 1, z);
        context.waitTicks(3);
        expectClient(context, "the " + name + " wall to be crossable", minecraft -> previewPlan(minecraft) != null);
        rightClick(context);
        waitForClient(context, 40, "the " + name + " tunnel to open",
                minecraft -> WallPassages.ticksOf(minecraft.player) > WallPassagePlan.MARK_TICKS + WallPassagePlan.BORE_TICKS + 2);
        context.takeScreenshot("wraith-veil-" + name + "-outside");
        // Every block of the tunnel is erased; those whose faces show from outside are drawn again on those faces only
        var mouthTop = new BlockPos(2, FEET + 1, Mth.floor(z));
        expectClient(context, "the " + name + " tunnel's mouth to be erased", minecraft -> minecraft.level.getBlockState(mouthTop).isAir());
        // Stepping through the mark, the moment erasing flips from outside to inside
        double[] steps = {1.5, 1.75, 1.95, 2.05, 2.3};
        for (int i = 0; i < steps.length; i++)
        {
            aimCamera(server, steps[i], FEET, z, 12, FEET + 1.6, z);
            context.waitTicks(3);
            context.takeScreenshot("wraith-veil-" + name + "-crossing-" + i);
        }
        aimCamera(server, 0.5, FEET, z, 2, FEET + 1, z);
        context.waitTicks(2);
        context.getInput().holdKey(options -> options.keyUp);
        waitForClient(context, 80, "the player to walk into the " + name + " tunnel", minecraft -> minecraft.player.getX() > stopX);
        context.getInput().releaseKey(options -> options.keyUp);
        context.waitTicks(3);
        context.takeScreenshot("wraith-veil-" + name + "-ahead");
        turn(context, 90, 0);
        context.takeScreenshot("wraith-veil-" + name + "-behind");
        turn(context, -90, 0);
        waitFor(context, server, Plan.totalTicks() + 40, "the " + name + " tunnel to close", minecraft -> !WallPassages.isPassing(player(minecraft)));
        waitForClient(context, 40, "the client to close the " + name + " tunnel", minecraft -> !WallPassages.isPassing(minecraft.player));
        clearCooldown(server);
    }

    private static void turn(ClientGameTestContext context, float yaw, float pitch)
    {
        context.runOnClient(minecraft ->
        {
            minecraft.player.setYRot(yaw);
            minecraft.player.setXRot(pitch);
            minecraft.player.yRotO = yaw;
            minecraft.player.xRotO = pitch;
        });
        context.waitTicks(1);
    }

    private static void waitForClient(ClientGameTestContext context, int maxTicks, String what, Predicate<Minecraft> condition)
    {
        for (int tick = 0; tick < maxTicks; tick++)
        {
            if (context.computeOnClient(condition::test))
                return;
            context.waitTicks(1);
        }
        throw new AssertionError("Expected " + what + " within " + maxTicks + " ticks");
    }
}
