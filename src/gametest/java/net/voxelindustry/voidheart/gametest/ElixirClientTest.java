package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.Items;
import net.voxelindustry.voidheart.common.content.depths.SmokerCycle;
import net.voxelindustry.voidheart.common.content.mob.MalignantBoulder;
import net.voxelindustry.voidheart.common.content.oil.Elixirs;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartEffects;
import net.voxelindustry.voidheart.common.setup.VoidHeartEntities;

/**
 * Each elixir is drunk with the use key and leaves its bottle: Ember Draught halves the Dragoon's void fire, Stillwater cools a smoker's jet to a
 * quarter, and Bulwark's absorption takes a whole Boulder slam.
 */
public class ElixirClientTest implements FabricClientGameTest
{
    private static final int GROUND_Y = -60;
    private static final BlockPos VENT = new BlockPos(0, GROUND_Y - 1, 6);
    /**
     * Ticks of holding use, longer than the 32 a drink takes.
     */
    private static final int DRINK_TICKS = 40;
    private static final float JET_HIT = 4;

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
            context.waitTicks(10);
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));

            drink(context, server, "ember_draught");
            server.runOnServer(minecraft ->
            {
                var player = player(minecraft);
                expect(player.hasEffect(VoidHeartEffects.EMBER_WARD), "Expected Ember Draught to give its ward");
                expect(player.getInventory().contains(stack -> stack.is(Items.GLASS_BOTTLE)), "Expected the bottle left after drinking");
                expect(Elixirs.againstVoidFire(player, 10) == 10 * Elixirs.EMBER_WARD_TAKEN, "Expected the ward to cut the Dragoon's void fire");
            });

            drink(context, server, "bulwark_elixir");
            server.runOnServer(minecraft ->
            {
                var player = player(minecraft);
                float absorption = player.getAbsorptionAmount();
                expect(absorption == 4 * (Elixirs.BULWARK_AMPLIFIER + 1), "Expected Bulwark to give 24 absorption, got " + absorption);

                var level = minecraft.overworld();
                var boulder = VoidHeartEntities.MALIGNANT_BOULDER.create(level, EntitySpawnReason.COMMAND);
                boulder.setNoAi(true);
                boulder.snapTo(4.5, GROUND_Y, 0.5, 0, 0);
                level.addFreshEntity(boulder);
                player.hurtServer(level, player.damageSources().source(MalignantBoulder.DAMAGE, boulder), MalignantBoulder.SLAM_DAMAGE);
                expect(player.getHealth() == player.getMaxHealth(), "Expected the absorption to take the whole slam, health is " + player.getHealth());
                boulder.discard();
            });

            context.getInput().pressKey(options -> options.keyInventory);
            context.waitTicks(10);
            context.takeScreenshot("elixir-effects-inventory");
            context.getInput().pressKey(InputConstants.KEY_ESCAPE);
            context.waitTicks(5);

            drink(context, server, "stillwater");
            server.runOnServer(minecraft ->
            {
                expect(player(minecraft).hasEffect(VoidHeartEffects.STILLWATER), "Expected Stillwater to give its effect");
                minecraft.overworld().setBlockAndUpdate(VENT, VoidHeartBlocks.SMOKER_VENT.defaultBlockState());
            });
            context.takeScreenshot("elixir-effects-hud");

            long ventSeed = SmokerCycle.seed(VENT);
            waitForPhase(context, server, ventSeed, SmokerCycle.Phase.ERUPTING);
            server.runOnServer(minecraft ->
            {
                var player = player(minecraft);
                player.setHealth(player.getMaxHealth());
                player.setAbsorptionAmount(0);
                player.teleportTo(VENT.getX() + 0.5, VENT.getY() + 1, VENT.getZ() + 0.5);
            });
            context.waitTicks(25);
            float lost = server.computeOnServer(minecraft -> player(minecraft).getMaxHealth() - player(minecraft).getHealth());
            expect(lost > 0, "Expected the jet to still burn a little");
            expect(lost <= 3 * JET_HIT * Elixirs.STILLWATER_TAKEN + 0.01F, "Expected Stillwater to cool each jet hit to a quarter, lost " + lost + " in 25 ticks");
            System.out.println("[Elixir] lost in a jet with Stillwater: " + lost + " health in 25 ticks");
        }
    }

    private static void drink(ClientGameTestContext context, TestServerContext server, String elixir)
    {
        server.runCommand("item replace entity @a weapon.mainhand with voidheart:" + elixir);
        context.waitTicks(5);
        context.getInput().holdKeyFor(options -> options.keyUse, DRINK_TICKS);
        context.waitTicks(5);
    }

    private static void waitForPhase(ClientGameTestContext context, TestServerContext server, long ventSeed, SmokerCycle.Phase phase)
    {
        for (int tick = 0; tick < 600; tick++)
        {
            long time = server.computeOnServer(minecraft -> minecraft.overworld().getGameTime());
            if (SmokerCycle.phase(ventSeed, time) == phase)
                return;
            context.waitTicks(1);
        }
        throw new AssertionError("The smoker never reached " + phase);
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
