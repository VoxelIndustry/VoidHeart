package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.voxelindustry.voidheart.common.curse.Corruption;
import net.voxelindustry.voidheart.common.energy.FoundationBalance;
import net.voxelindustry.voidheart.common.energy.LeyPower;
import net.voxelindustry.voidheart.common.energy.VoidEnergy;
import net.voxelindustry.voidheart.common.energy.creative.CreativeEnergyContent;
import net.voxelindustry.voidheart.common.ley.Ley;
import net.voxelindustry.voidheart.common.ley.LeyLattice;
import net.voxelindustry.voidheart.common.ley.TaintStage;

import static net.voxelindustry.voidheart.gametest.GameTestKit.*;

/**
 * The energy foundation in a real world: both creative test blocks placed by command, drawn from inside their range and not outside it, gone once broken
 * without dropping anything, and back after a reload. Corruption rises through its modifiers and node taint survives the reload too.
 */
public class EnergyFoundationClientTest implements FabricClientGameTest
{
    private static final BlockPos WELL  = new BlockPos(3, FLAT_FEET, 6);
    private static final BlockPos STONE = new BlockPos(-3, FLAT_FEET, 6);
    private static final String WARDED_TAG = "voidheart_energy_test_ward";

    private static boolean wardAdded;

    @Override
    public void runTest(ClientGameTestContext context)
    {
        TestWorldSave save;
        long node;
        try (var world = context.worldBuilder().create())
        {
            save = world.getWorldSave();
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            freezeTime(server, "noon");
            server.runCommand("gamerule spawn_mobs false");
            server.runCommand("gamemode creative @a");
            context.waitTicks(10);

            expect(BuiltInRegistries.BLOCK.getKey(CreativeEnergyContent.CREATIVE_VOID_WELL).getPath().equals("creative_void_well"), "The creative void well should be registered");

            server.runCommand("setblock " + WELL.getX() + " " + WELL.getY() + " " + WELL.getZ() + " voidheart:creative_void_well");
            server.runCommand("setblock " + STONE.getX() + " " + STONE.getY() + " " + STONE.getZ() + " voidheart:creative_ley_stone");
            context.waitTicks(5);

            checkWell(server, true, "A placed well");
            checkStone(server, true, "A placed stone");
            checkCorruption(server);
            node = taintNearestNode(server);

            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
            context.getInput().pressKey(InputConstants.KEY_F1);
            aimFrom(context, server, 0.5, FLAT_FEET, 0.5, 0.5, FLAT_FEET + 0.5, WELL.getZ() + 0.5, 5);
            context.takeScreenshot("energy-creative-blocks");
            context.getInput().pressKey(InputConstants.KEY_F1);

            checkBreaking(context, server);

            server.runCommand("setblock " + WELL.getX() + " " + WELL.getY() + " " + WELL.getZ() + " voidheart:creative_void_well");
            context.waitTicks(5);
            checkWell(server, true, "A replaced well");
        }

        try (var world = save.open())
        {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            context.waitTicks(10);

            checkWell(server, true, "A well after a reload");
            checkStone(server, true, "A stone after a reload");
            float taint = server.computeOnServer(minecraft -> Ley.taint(minecraft.overworld(), node));
            expect(taint == FoundationBalance.FESTERING_FROM, "Node taint should survive a reload, it is " + taint);
        }
    }

    private static void checkWell(TestServerContext server, boolean present, String what)
    {
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            var inside = WELL.offset(0, 4, FoundationBalance.CREATIVE_WELL_RADIUS - 5);
            var outside = WELL.east(FoundationBalance.CREATIVE_WELL_RADIUS + 1);

            long available = VoidEnergy.available(level, inside);
            long drawn = VoidEnergy.draw(level, inside, 5_000);
            long offered = VoidEnergy.offer(level, inside, 7_000);
            if (present)
            {
                expect(available == Long.MAX_VALUE, what + " should make energy endless inside its field, got " + available);
                expect(drawn == 5_000, what + " should give all that is drawn, gave " + drawn);
                expect(offered == 7_000, what + " should take any offer, took " + offered);
            }
            else
            {
                expect(available == 0 && drawn == 0 && offered == 0, what + " should give and take nothing, gave " + drawn + " and took " + offered);
            }

            expect(VoidEnergy.draw(level, outside, 5_000) == 0, what + " should give nothing outside its field");
            expect(VoidEnergy.offer(level, outside, 5_000) == 0, what + " should take nothing outside its field");
        });
    }

    private static void checkStone(TestServerContext server, boolean present, String what)
    {
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            float near = LeyPower.supply(level, STONE.offset(0, 2, FoundationBalance.CREATIVE_LEY_STONE_RADIUS - 3), 2.5);
            float far = LeyPower.supply(level, STONE.west(FoundationBalance.CREATIVE_LEY_STONE_RADIUS + 1), 2.5);
            expect(near == (present ? 1 : 0), what + " should supply " + (present ? "all" : "none") + " of the ley asked near it, supplied " + near);
            expect(far == 0, what + " should supply nothing away from it, supplied " + far);
        });
    }

    private static void checkBreaking(ClientGameTestContext context, TestServerContext server)
    {
        server.runCommand("setblock " + WELL.getX() + " " + WELL.getY() + " " + WELL.getZ() + " air destroy");
        server.runCommand("setblock " + STONE.getX() + " " + STONE.getY() + " " + STONE.getZ() + " air destroy");
        context.waitTicks(5);

        checkWell(server, false, "A broken well");
        checkStone(server, false, "A broken stone");
        int drops = server.computeOnServer(minecraft -> minecraft.overworld().getEntitiesOfClass(ItemEntity.class, new AABB(WELL).inflate(10)).size());
        expect(drops == 0, "The creative blocks should drop nothing, dropped " + drops);

        server.runCommand("setblock " + STONE.getX() + " " + STONE.getY() + " " + STONE.getZ() + " voidheart:creative_ley_stone");
        context.waitTicks(5);
        checkStone(server, true, "A replaced stone");
    }

    /**
     * Raise is the only way gameplay adds corruption: modifiers scale it, and the fractions they leave add up over many small rises.
     */
    private static void checkCorruption(TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            if (!wardAdded)
            {
                Corruption.addModifier(player -> player.entityTags().contains(WARDED_TAG) ? 0.5F : 1);
                wardAdded = true;
            }

            var player = player(minecraft);
            Corruption.set(player, 0);
            Corruption.raise(player, 10);
            expect(Corruption.get(player) == 10, "An unwarded raise of 10 should add 10, corruption is " + Corruption.get(player));

            player.addTag(WARDED_TAG);
            for (int i = 0; i < 4; i++)
                Corruption.raise(player, 1);
            player.removeTag(WARDED_TAG);
            expect(Corruption.get(player) == 12, "Four warded raises of 1 should add 2, corruption is " + Corruption.get(player));

            Corruption.raise(player, -5);
            expect(Corruption.get(player) == 12, "A negative raise should change nothing, corruption is " + Corruption.get(player));
            Corruption.set(player, 0);
        });
    }

    private static long taintNearestNode(TestServerContext server)
    {
        return server.computeOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            var node = LeyLattice.get(level).orElseThrow().nearestNode(0, 0).orElseThrow();
            expect(Ley.stage(level, node.id()) == TaintStage.CLEAN, "A fresh node should be clean");

            Ley.addTaint(level, node.id(), FoundationBalance.FESTERING_FROM);
            expect(Ley.taint(level, node.id()) == FoundationBalance.FESTERING_FROM, "Taint should be stored on the node");
            expect(Ley.stage(level, node.id()) == TaintStage.FESTERING, "The node should be festering, it is " + Ley.stage(level, node.id()));
            checkFlow(minecraft, node.x(), node.z(), node.power());
            return node.id();
        });
    }

    /**
     * Before the real flow lands, a node gives its raw power and claims never compete.
     */
    private static void checkFlow(MinecraftServer minecraft, int x, int z, float power)
    {
        double flow = Ley.lineFlowAt(minecraft.overworld(), new BlockPos(x, FLAT_FEET, z));
        expect(flow == power, "The default flow on a node should be its raw power " + power + ", got " + flow);
        double claimed = Ley.claim(minecraft.overworld(), new BlockPos(x, FLAT_FEET, z), power / 2);
        expect(claimed == power / 2, "A claim below the node's flow should be granted in full, got " + claimed);
    }
}
