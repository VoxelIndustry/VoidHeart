package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.voxelindustry.voidheart.common.content.mob.DragoonAdaptation;
import net.voxelindustry.voidheart.common.content.mob.DragoonAdaptation.Kind;
import net.voxelindustry.voidheart.common.content.mob.KernacianDragoon;
import net.voxelindustry.voidheart.common.content.oil.OilCoating;
import net.voxelindustry.voidheart.common.content.oil.OilType;
import net.voxelindustry.voidheart.common.setup.VoidHeartEntities;

/**
 * Kernacian Oil counts as a seventh kind the Dragoon adapts to on its own, growing veins from its core as it does, and strikes the other constructs harder.
 */
public class KernacianOilClientTest implements FabricClientGameTest
{
    private static final int    GROUND_Y = -60;
    private static final double DRAGOON_Z = 4.5;

    @Override
    public void runTest(ClientGameTestContext context)
    {
        try (var world = context.worldBuilder().create())
        {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            server.runCommand("time set noon");
            server.runCommand("gamerule advance_time false");
            server.runCommand("gamemode creative @a");
            context.waitTicks(10);
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));

            // Each kernacian hit counts as the seventh kind only, and the Dragoon takes less of it each time
            server.runOnServer(minecraft ->
            {
                var level = minecraft.overworld();
                var player = player(minecraft);
                player.teleportTo(0.5, GROUND_Y, 0.5);
                var sword = new ItemStack(Items.IRON_SWORD);
                OilCoating.coat(sword, OilType.KERNACIAN);
                player.getInventory().setItem(0, sword);
                player.getInventory().setSelectedSlot(0);

                var dragoon = spawn(level, VoidHeartEntities.KERNACIAN_DRAGOON, 0.5, DRAGOON_Z);
                float lost = 0;
                for (int hit = 0; hit < DragoonAdaptation.TIERS[2]; hit++)
                {
                    float before = dragoon.getHealth();
                    attack(player, dragoon);
                    float now = before - dragoon.getHealth();
                    if (hit > 0)
                        expect(now < lost, "Expected each kernacian hit to land softer, " + now + " after " + lost);
                    lost = now;
                }
                int packed = dragoon.adaptation();
                expect(DragoonAdaptation.count(packed, Kind.KERNACIAN) == DragoonAdaptation.TIERS[2], "Expected " + DragoonAdaptation.TIERS[2] + " kernacian hits counted, got "
                        + DragoonAdaptation.count(packed, Kind.KERNACIAN));
                expect(DragoonAdaptation.count(packed, Kind.BLADE) == 0, "Expected none counted as a blade");
                dragoon.setHealth(dragoon.getMaxHealth());
            });

            // The other constructs take more from it than from the same sword plain
            server.runOnServer(minecraft ->
            {
                var level = minecraft.overworld();
                var player = player(minecraft);
                for (var type : new EntityType[]{ VoidHeartEntities.LEGIONARY, VoidHeartEntities.CENTURION, VoidHeartEntities.MALIGNANT_BOULDER })
                {
                    var sword = player.getMainHandItem();
                    sword.remove(net.voxelindustry.voidheart.common.setup.VoidHeartDataComponents.WEAPON_OIL);
                    float plain = lossFrom(player, level, type);
                    OilCoating.coat(sword, OilType.KERNACIAN);
                    float coated = lossFrom(player, level, type);
                    System.out.println("[Kernacian] " + type.toShortString() + " lost " + plain + " plain and " + coated + " coated");
                    // Armor takes a little more off a harder blow, so the bonus shows a bit under its full share
                    expect(plain > 0 && coated > plain * (1 + OilCoating.CONSTRUCT_BONUS * 0.6F) && coated <= plain * (1 + OilCoating.CONSTRUCT_BONUS) + 0.05F,
                            "Expected a " + type.toShortString() + " to take about " + Math.round(OilCoating.CONSTRUCT_BONUS * 100) + "% more, " + coated + " against " + plain);
                }
            });

            // The veins, grown through all three steps, from the front and the side
            context.getInput().pressKey(InputConstants.KEY_F1);
            aimFrom(context, server, 0.5, GROUND_Y + 0.6, DRAGOON_Z - 2.6, 0.5, GROUND_Y + 1.5, DRAGOON_Z, 20);
            context.takeScreenshot("kernacian-veins-front");
            aimFrom(context, server, 2.6, GROUND_Y + 0.6, DRAGOON_Z - 1.6, 0.5, GROUND_Y + 1.5, DRAGOON_Z, 10);
            context.takeScreenshot("kernacian-veins-side");
            server.runOnServer(minecraft ->
            {
                var dragoon = minecraft.overworld().getEntities(VoidHeartEntities.KERNACIAN_DRAGOON, entity -> true).getFirst();
                dragoon.setAdaptation(0);
                for (int i = 0; i < DragoonAdaptation.TIERS[0]; i++)
                    dragoon.setAdaptation(DragoonAdaptation.adapt(dragoon.adaptation(), Kind.KERNACIAN));
            });
            aimFrom(context, server, 0.5, GROUND_Y + 0.6, DRAGOON_Z - 2.6, 0.5, GROUND_Y + 1.5, DRAGOON_Z, 20);
            context.takeScreenshot("kernacian-veins-first-step");
            context.getInput().pressKey(InputConstants.KEY_F1);
            server.runCommand("kill @e[type=voidheart:kernacian_dragoon]");
        }
    }

    private static float lossFrom(ServerPlayer player, ServerLevel level, EntityType<? extends Mob> type)
    {
        var mob = spawn(level, type, 3.5, 0.5);
        float before = mob.getHealth();
        attack(player, mob);
        float lost = before - mob.getHealth();
        mob.discard();
        return lost;
    }

    private static <T extends Mob> T spawn(ServerLevel level, EntityType<T> type, double x, double z)
    {
        var mob = type.create(level, EntitySpawnReason.COMMAND);
        mob.setNoAi(true);
        mob.snapTo(x, GROUND_Y, z, 180, 0);
        // Without its AI nothing turns the body, so it would stand facing the way it was made
        mob.setYBodyRot(180);
        mob.setYHeadRot(180);
        level.addFreshEntity(mob);
        return mob;
    }

    private static void attack(ServerPlayer player, LivingEntity target)
    {
        target.setInvulnerableTime(0);
        try
        {
            var field = LivingEntity.class.getDeclaredField("damageCooldownTime");
            field.setAccessible(true);
            field.setInt(target, 0);
        }
        catch (ReflectiveOperationException e)
        {
            throw new AssertionError(e);
        }
        player.resetAttackStrengthTicker();
        player.attack(target);
    }

    private static void aimFrom(ClientGameTestContext context, TestServerContext server, double feetX, double feetY, double feetZ, double x, double y, double z, int settleTicks)
    {
        double dx = x - feetX;
        double dy = y - (feetY + 1.62);
        double dz = z - feetZ;
        double yaw = Math.toDegrees(Math.atan2(-dx, dz));
        double pitch = -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
        server.runCommand("tp @a " + feetX + " " + feetY + " " + feetZ + " " + yaw + " " + pitch);
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
        });
        context.waitTicks(settleTicks);
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
