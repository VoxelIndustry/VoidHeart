package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.voxelindustry.voidheart.common.content.mob.WraithBeast;
import net.voxelindustry.voidheart.common.content.mob.WraithBeast.Phase;
import net.voxelindustry.voidheart.common.content.salt.SaltWard;
import net.voxelindustry.voidheart.common.setup.VoidHeartEntities;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;

import static net.voxelindustry.voidheart.gametest.GameTestKit.*;

/**
 * The Wraith Beast on open ground: out of phase it gallops through a wall and nothing hurts it, beside its prey it takes shape, rears and strikes every three
 * seconds at the quickest, solid and open to blows only around each strike. A raised shield blocks the blow but is knocked out of use, arrows pass through it
 * solid or not, and salt holds it material: it walks over a salt line, arrows find it there, and it fades only after striking out of the ward.
 */
public class WraithBeastClientTest implements FabricClientGameTest
{
    private static final int X0 = 340;
    private static final int FEET = FLAT_FEET;
    private static final int WALL_X = X0 + 5;
    private static final int TARGET_X = X0 + 10;
    private static final int SALT_X = X0 + 6;
    private static final int DROP_ROLLS = 100;

    @Override
    public void runTest(ClientGameTestContext context)
    {
        try (var world = context.worldBuilder().create())
        {
            var server = world.getServer();
            freezeTime(server, "noon");
            server.runCommand("gamerule spawn_mobs false");
            server.runCommand("gamemode creative @a");
            tp(server, X0 + 5.5, FEET, 10.5);
            world.getConnection().waitForChunksRender();
            context.waitTicks(20);
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
            context.getInput().pressKey(InputConstants.KEY_F1);

            crossesWallAndStrikes(context, server);
            closeUps(context, server);
            neverJumpsBedrock(context, server);
            arrowsPassThrough(context, server);
            heldBySalt(context, server);
            context.getInput().pressKey(InputConstants.KEY_F1);
            breaksShield(context, server);
            drops(server);

            clearScene(server);
            clearArea(server, X0 + 5);
            server.runCommand("gamemode creative @a");
        }
    }

    private static WraithBeast spawnBeast(MinecraftServer minecraft, double x)
    {
        var beast = spawn(minecraft, VoidHeartEntities.WRAITH_BEAST, x, FEET, 0.5);
        beast.snapTo(x, FEET, 0.5, -90, 0);
        return beast;
    }

    private static Husk spawnTarget(MinecraftServer minecraft, double x)
    {
        var level = minecraft.overworld();
        var husk = EntityTypes.HUSK.create(level, EntitySpawnReason.COMMAND);
        husk.snapTo(x, FEET, 0.5, 90, 0);
        husk.setNoAi(true);
        husk.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 100_000, 2));
        level.addFreshEntity(husk);
        return husk;
    }

    private static WraithBeast beast(MinecraftServer minecraft)
    {
        return first(minecraft, VoidHeartEntities.WRAITH_BEAST);
    }

    private static String describe(WraithBeast beast)
    {
        return beast.phase() + " at " + beast.position() + ", solid " + !beast.noPhysics + ", health " + beast.getHealth();
    }

    private static void crossesWallAndStrikes(ClientGameTestContext context, TestServerContext server)
    {
        server.runCommand("fill " + WALL_X + " " + FEET + " -3 " + WALL_X + " " + (FEET + 3) + " 3 stone");
        aimFrom(context, server, X0 + 5.5, FEET + 0.5, 10.5, X0 + 5.5, FEET + 1, 0.5);
        server.runOnServer(minecraft -> spawnBeast(minecraft, X0 + 0.5).setTarget(spawnTarget(minecraft, TARGET_X + 0.5)));

        // Through the wall, untouchable all the way
        boolean[] seenInWall = new boolean[1];
        waitFor(context, server, 120, () -> "The beast should gallop through the wall", minecraft ->
        {
            var beast = beast(minecraft);
            var level = minecraft.overworld();
            if (!level.noCollision(beast, beast.getBoundingBox()))
            {
                expect(beast.phase() == Phase.PHANTOM, "Only out of phase may it be inside a wall, " + describe(beast));
                if (!seenInWall[0])
                {
                    float before = beast.getHealth();
                    expect(!beast.hurtServer(level, beast.damageSources().playerAttack(player(minecraft)), 10), "Out of phase it should take no hit");
                    expect(!beast.isPickable() && !beast.canBeHitByProjectile(), "Out of phase nothing should touch it");
                    expect(beast.getHealth() == before, "Out of phase it should lose no health");
                }
                seenInWall[0] = true;
            }
            return seenInWall[0] && beast.getX() > WALL_X + 1.5;
        });
        context.takeScreenshot("wraith-beast-past-wall");

        // Takes shape beside the husk and rears
        waitFor(context, server, 80, "The beast should take shape beside its prey", minecraft -> beast(minecraft).phase() == Phase.WINDUP);
        int windupStart = server.computeOnServer(minecraft -> minecraft.overworld().getGameTime()).intValue();
        server.runOnServer(minecraft ->
        {
            var beast = beast(minecraft);
            var level = minecraft.overworld();
            expect(!beast.noPhysics && beast.isPickable(), "Taking shape it should turn solid, " + describe(beast));
            expect(level.noCollision(beast, beast.getBoundingBox()), "It should take shape in open air, " + describe(beast));
            expect(beast.getX() > WALL_X + 1, "It should take shape on the husk's side of the wall, " + describe(beast));
        });
        context.waitTicks(10);
        context.takeScreenshot("wraith-beast-rear");

        // The blow
        float[] huskBefore = new float[1];
        server.runOnServer(minecraft -> huskBefore[0] = first(minecraft, EntityTypes.HUSK).getHealth());
        waitFor(context, server, 30, "The beast should strike", minecraft -> beast(minecraft).phase() == Phase.RECOVER);
        int firstStrike = server.computeOnServer(minecraft -> minecraft.overworld().getGameTime()).intValue();
        expect(firstStrike - windupStart >= WraithBeast.WINDUP_TICKS - 1, "It should rear about a second before striking, struck after " + (firstStrike - windupStart));
        context.waitTicks(2);
        context.takeScreenshot("wraith-beast-strike");
        server.runOnServer(minecraft ->
        {
            var husk = first(minecraft, EntityTypes.HUSK);
            expect(husk.getHealth() < huskBefore[0], "The blow should land, husk health " + huskBefore[0] + " to " + husk.getHealth());
        });

        // Fades back out, then strikes again no sooner than the interval
        waitFor(context, server, WraithBeast.RECOVER_TICKS + 10, "The beast should fade back out", minecraft -> beast(minecraft).phase() == Phase.PHANTOM);
        int faded = server.computeOnServer(minecraft -> minecraft.overworld().getGameTime()).intValue();
        expect(faded - firstStrike >= WraithBeast.RECOVER_TICKS - 1, "It should stay solid a while after striking, faded after " + (faded - firstStrike));
        server.runOnServer(minecraft -> expect(beast(minecraft).noPhysics && !beast(minecraft).isPickable(), "Faded it should be out of phase again"));
        context.waitTicks(4);
        context.takeScreenshot("wraith-beast-phantom");
        // Solid again for the next blow, and open to one, after the shots so its red flash spoils none
        waitFor(context, server, 120, "The beast should take shape again", minecraft -> beast(minecraft).phase() == Phase.WINDUP);
        server.runOnServer(minecraft ->
        {
            var beast = beast(minecraft);
            float before = beast.getHealth();
            expect(beast.hurtServer(minecraft.overworld(), beast.damageSources().playerAttack(player(minecraft)), 4), "Solid it should take a hit, " + describe(beast));
            expect(beast.getHealth() < before, "Solid it should lose health");
        });
        waitFor(context, server, 40, "The beast should strike again", minecraft -> beast(minecraft).phase() == Phase.RECOVER);
        int secondStrike = server.computeOnServer(minecraft -> minecraft.overworld().getGameTime()).intValue();
        System.out.println("[Beast] strikes " + (secondStrike - firstStrike) + " ticks apart");
        expect(secondStrike - firstStrike >= WraithBeast.STRIKE_INTERVAL - 1, "Strikes should come no closer than the interval, came " + (secondStrike - firstStrike));
        expect(secondStrike - firstStrike <= WraithBeast.STRIKE_INTERVAL + 20, "Beside its prey it should strike again soon, came " + (secondStrike - firstStrike));

        clearScene(server);
        server.runCommand("fill " + WALL_X + " " + FEET + " -3 " + WALL_X + " " + (FEET + 3) + " 3 air");
    }

    /**
     * Its prey just past a bedrock wall, near enough that open ground on the far side is within its strike: it must not take shape over there.
     */
    private static void neverJumpsBedrock(ClientGameTestContext context, TestServerContext server)
    {
        server.runCommand("fill " + WALL_X + " " + FEET + " -3 " + WALL_X + " " + (FEET + 3) + " 3 bedrock");
        server.runOnServer(minecraft -> spawnBeast(minecraft, WALL_X - 0.6).setTarget(spawnTarget(minecraft, WALL_X + 1.8)));
        for (int tick = 0; tick < 80; tick++)
        {
            context.waitTick();
            server.runOnServer(minecraft ->
            {
                var beast = beast(minecraft);
                expect(beast.getX() < WALL_X, "The beast should never get past bedrock, " + describe(beast));
                expect(beast.phase() == Phase.PHANTOM, "With no open ground on its side it should not take shape, " + describe(beast));
            });
        }
        clearScene(server);
        server.runCommand("fill " + WALL_X + " " + FEET + " -3 " + WALL_X + " " + (FEET + 3) + " 3 air");
    }

    /**
     * Near enough to judge the look: galloping out of phase, then frozen mid-rear and just after the blow.
     */
    private static void closeUps(ClientGameTestContext context, TestServerContext server)
    {
        aimFrom(context, server, X0 + 5.5, FEET + 0.3, 5, X0 + 5, FEET + 1, 0.5);
        server.runOnServer(minecraft -> spawnBeast(minecraft, X0 - 1.5).setTarget(spawnTarget(minecraft, X0 + 7.5)));
        waitFor(context, server, 80, "The beast should gallop into view", minecraft -> beast(minecraft).getX() > X0 + 3);
        server.runCommand("tick freeze");
        context.waitTicks(2);
        context.takeScreenshot("wraith-beast-close-gallop");
        server.runCommand("tick unfreeze");
        waitFor(context, server, 80, "The beast should take shape", minecraft -> beast(minecraft).phase() == Phase.WINDUP);
        context.waitTicks(9);
        server.runCommand("tick freeze");
        context.waitTicks(2);
        context.takeScreenshot("wraith-beast-close-rear");
        server.runCommand("tick unfreeze");
        waitFor(context, server, 30, "The beast should strike", minecraft -> beast(minecraft).phase() == Phase.RECOVER);
        server.runCommand("tick freeze");
        context.waitTicks(2);
        context.takeScreenshot("wraith-beast-close-slam");
        server.runCommand("tick unfreeze");
        clearScene(server);
    }

    /**
     * Fired at it while solid, just after a blow, an arrow flies on through and the beast slips aside for clients to see.
     */
    private static void arrowsPassThrough(ClientGameTestContext context, TestServerContext server)
    {
        aimFrom(context, server, X0 + 5.5, FEET + 0.5, 10.5, X0 + 5.5, FEET + 1, 0.5);
        server.runOnServer(minecraft -> spawnBeast(minecraft, X0 + 2.5).setTarget(spawnTarget(minecraft, X0 + 5.5)));
        waitFor(context, server, 120, "The beast should strike the husk", minecraft -> beast(minecraft).phase() == Phase.RECOVER);

        int[] ids = new int[1];
        float[] before = new float[1];
        server.runOnServer(minecraft ->
        {
            var beast = beast(minecraft);
            ids[0] = beast.getId();
            before[0] = beast.getHealth();
            var level = minecraft.overworld();
            var arrow = new Arrow(level, beast.getX(), beast.getY() + 1, beast.getZ() - 6, new ItemStack(Items.ARROW), null);
            arrow.shoot(0, 0, 1, 2.5F, 0);
            level.addFreshEntity(arrow);
        });
        boolean dodged = false;
        for (int tick = 0; tick < 20 && !dodged; tick++)
        {
            context.waitTick();
            dodged = context.computeOnClient(client -> client.level.getEntity(ids[0]) instanceof WraithBeast beast && beast.dodgedTick > 0);
        }
        expect(dodged, "Clients should see the beast slip aside from the arrow");
        context.waitTicks(3);
        context.takeScreenshot("wraith-beast-dodge");
        context.waitTicks(10);
        server.runOnServer(minecraft ->
        {
            var beast = beast(minecraft);
            expect(beast.getHealth() == before[0], "An arrow should never hurt it, health " + before[0] + " to " + beast.getHealth());
            var arrows = minecraft.overworld().getEntitiesOfClass(Arrow.class, new AABB(X0 - 20, FEET - 2, -20, X0 + 30, FEET + 10, 30));
            expect(!arrows.isEmpty() && arrows.getFirst().getZ() > beast.getZ() + 1, "The arrow should fly on past it, " + arrows.stream().map(Arrow::position).toList());
        });
        clearScene(server);
    }

    private static void heldBySalt(ClientGameTestContext context, TestServerContext server)
    {
        // Lets the husks killed before finish falling out of the shot
        context.waitTicks(25);
        server.runCommand("fill " + SALT_X + " " + FEET + " -6 " + SALT_X + " " + FEET + " 6 voidheart:salt_ward");
        aimFrom(context, server, SALT_X + 0.5, FEET + 0.5, 6.5, SALT_X + 0.5, FEET + 1, 0.5);
        server.runOnServer(minecraft -> spawnBeast(minecraft, X0 + 0.5).setTarget(spawnTarget(minecraft, SALT_X + 6.5)));

        boolean held = false;
        float[] beforeArrow = new float[1];
        for (int tick = 0; tick < 200 && !held; tick++)
        {
            context.waitTick();
            held = server.computeOnServer(minecraft ->
            {
                var beast = beast(minecraft);
                if (SaltWard.isWardedBelow(minecraft.overworld(), beast.blockPosition()) && minecraft.overworld().noCollision(beast, beast.getBoundingBox()))
                {
                    // Give it the tick it takes to notice
                    return beast.phase() == Phase.HELD;
                }
                return false;
            });
        }
        expect(held, "In the ward the salt should hold it material");
        server.runOnServer(minecraft ->
        {
            var beast = beast(minecraft);
            expect(beast.isSolid() && !beast.noPhysics && beast.isPickable(), "Held by salt it should be solid, " + describe(beast));
            expect(beast.canBeHitByProjectile(), "Held by salt arrows should find it");
            beforeArrow[0] = beast.getHealth();
            var level = minecraft.overworld();
            var arrow = new Arrow(level, beast.getX(), beast.getY() + 1, beast.getZ() - 5, new ItemStack(Items.ARROW), null);
            arrow.shoot(0, 0, 1, 2.5F, 0);
            level.addFreshEntity(arrow);
        });
        waitFor(context, server, 20, () -> "An arrow should hurt it while salt holds it", minecraft -> beast(minecraft).getHealth() < beforeArrow[0]);
        // Past the red of the arrow's hit, and still held
        context.waitTicks(11);
        server.runOnServer(minecraft -> expect(beast(minecraft).isSolid(), "It should stay held until it strikes, " + describe(beast(minecraft))));
        context.takeScreenshot("wraith-beast-held-by-salt");

        // It walks on to the husk, strikes, and fades once out of the ward
        waitFor(context, server, 200, () -> "Held by salt it should still walk up and strike", minecraft -> beast(minecraft).phase() == Phase.RECOVER);
        waitFor(context, server, WraithBeast.RECOVER_TICKS + 10, () -> "Out of the ward it should fade after striking",
                minecraft -> beast(minecraft).phase() == Phase.PHANTOM);
        server.runOnServer(minecraft ->
        {
            var beast = beast(minecraft);
            expect(!SaltWard.isWardedBelow(minecraft.overworld(), beast.blockPosition()), "It should have struck out of the ward, " + describe(beast));
        });
        clearScene(server);
        server.runCommand("fill " + SALT_X + " " + FEET + " -6 " + SALT_X + " " + FEET + " 6 air");
    }

    /**
     * A survival player it finds on its own, with a raised shield: the shield takes the blow and is knocked out of use.
     */
    private static void breaksShield(ClientGameTestContext context, TestServerContext server)
    {
        server.runCommand("gamemode survival @a");
        aimCamera(server, X0 + 0.5, FEET, 0.5, X0 + 8, FEET + 1, 0.5);
        server.runOnServer(minecraft ->
        {
            heal(minecraft);
            var player = player(minecraft);
            player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
            spawnBeast(minecraft, X0 + 8.5);
        });
        context.waitTicks(3);
        context.getInput().holdKey(options -> options.keyUse);
        context.waitTicks(8);
        server.runOnServer(minecraft -> expect(player(minecraft).isBlocking(), "The player should be blocking"));

        // It comes from ahead and takes shape on the nearest side, so the shield faces it
        waitFor(context, server, 160, "The beast should find the player and strike", minecraft -> beast(minecraft).phase() == Phase.RECOVER);
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            expect(player.getHealth() == player.getMaxHealth(), "A raised shield should take the blow, health " + player.getHealth());
            expect(player.getCooldowns().isOnCooldown(player.getItemBySlot(EquipmentSlot.OFFHAND)), "The blow should knock the shield out of use");
        });
        context.getInput().releaseKey(options -> options.keyUse);
        context.waitTicks(2);
        server.runOnServer(minecraft -> player(minecraft).setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY));

        // Without the shield the next blow lands
        waitFor(context, server, WraithBeast.STRIKE_INTERVAL + 60, "The beast should strike again", minecraft ->
                player(minecraft).getHealth() < player(minecraft).getMaxHealth());
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            float lost = player.getMaxHealth() - player.getHealth();
            System.out.println("[Beast] an unshielded blow took " + lost);
            expect(lost >= 8, "An unshielded blow should hurt badly, took " + lost);
            heal(minecraft);
        });
        server.runCommand("gamemode creative @a");
        clearScene(server);
    }

    private static void drops(TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            var area = new AABB(X0 - 6, FEET - 1, -6, X0 + 2, FEET + 4, 6);
            int fewest = Integer.MAX_VALUE;
            int most = 0;
            int inks = 0;
            for (int roll = 0; roll < DROP_ROLLS; roll++)
            {
                var beast = spawnBeast(minecraft, X0 - 1.5);
                beast.setNoAi(true);
                beast.kill(level);
                int shards = 0;
                for (var drop : level.getEntitiesOfClass(ItemEntity.class, area))
                {
                    if (drop.getItem().is(VoidHeartItems.ENDER_SHARD))
                        shards += drop.getItem().getCount();
                    if (drop.getItem().is(VoidHeartItems.WRAITH_INK))
                        inks += drop.getItem().getCount();
                    drop.discard();
                }
                fewest = Math.min(fewest, shards);
                most = Math.max(most, shards);
            }
            System.out.println("[Beast] drops over " + DROP_ROLLS + ": shards " + fewest + "-" + most + ", inks " + inks);
            expect(fewest >= 2 && most <= 4 && most > fewest, "It should drop 2-4 ender shards, dropped " + fewest + "-" + most);
            expect(inks > DROP_ROLLS / 4 && inks < DROP_ROLLS * 9 / 10, "It should often drop wraith ink, dropped " + inks + " in " + DROP_ROLLS);
        });
        clearScene(server);
    }
}
