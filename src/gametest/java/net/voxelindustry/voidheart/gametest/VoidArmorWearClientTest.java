package net.voxelindustry.voidheart.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.voxelindustry.voidheart.common.content.armor.ArmorBalance;
import net.voxelindustry.voidheart.common.content.armor.ArmorWear;
import net.voxelindustry.voidheart.common.content.armor.ParkedArmor;
import net.voxelindustry.voidheart.common.content.armor.VoidArmorBaseBlock;
import net.voxelindustry.voidheart.common.content.armor.VoidArmorBaseTile;
import net.voxelindustry.voidheart.common.content.armor.VoidArmorContent;
import net.voxelindustry.voidheart.common.content.armor.WornArmor;
import net.voxelindustry.voidheart.common.energy.storage.RubrumBalance;
import net.voxelindustry.voidheart.common.energy.storage.RubrumCellItem;
import net.voxelindustry.voidheart.common.energy.storage.RubrumContent;
import org.jspecify.annotations.Nullable;

import java.util.List;

import static net.voxelindustry.voidheart.gametest.GameTestKit.expect;
import static net.voxelindustry.voidheart.gametest.GameTestKit.player;

/**
 * A docked frame charges from a creative well's field, and far from any field from the rubrum cell in its base. A use on the base steps the player into it
 * after a moment in which they can't move, at the frame's size, and holding sneak lets them out: away from a base the frame stays parked where they stood
 * until a use steps them back in, and standing on an empty base it docks there. A ceiling two blocks up refuses stepping in, three is room enough. On a real death the frame goes back
 * to its base, emptied, and with that base gone it stands beside the respawned player.
 */
public class VoidArmorWearClientTest implements FabricClientGameTest
{
    private static final int      GROUND_Y  = -60;
    private static final BlockPos BASE      = new BlockPos(0, GROUND_Y, 2);
    private static final BlockPos WELL      = new BlockPos(4, GROUND_Y, 2);
    private static final BlockPos FAR_BASE  = new BlockPos(40, GROUND_Y, 2);

    @Override
    public void runTest(ClientGameTestContext context)
    {
        try (var world = context.worldBuilder().create())
        {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            server.runCommand("time set noon");
            server.runCommand("gamerule advance_time false");
            server.runCommand("gamerule spawn_mobs false");
            server.runCommand("gamerule immediate_respawn true");
            server.runCommand("gamemode survival @a");
            server.runCommand("clear @a");
            server.runCommand("setblock " + at(BASE) + " voidheart:void_armor_base");
            server.runCommand("setblock " + at(WELL) + " voidheart:creative_void_well");
            server.runCommand("setblock " + at(FAR_BASE) + " voidheart:void_armor_base");
            context.waitTicks(10);
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));

            charge(context, server);
            stepInAndOut(context, server);
            dockOnStepOut(context, server);
            refuseLowCeiling(context, server);
            deathSendsHome(context, server);
            homelessParksBesideRespawn(context, server);
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
        }
    }

    private static void charge(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            base(minecraft, BASE).dock(new ItemStack(VoidArmorContent.VOID_ARMOR));
            var far = base(minecraft, FAR_BASE);
            far.dock(new ItemStack(VoidArmorContent.VOID_ARMOR));
            far.cell().setItem(0, RubrumCellItem.full());
        });
        context.waitTicks(40);
        long fromField = server.computeOnServer(minecraft -> base(minecraft, BASE).charge());
        expect(fromField > 0 && fromField <= 41 * ArmorBalance.BASE_INTAKE, "Expected the base in the well's field to charge its frame, got " + fromField);
        long fromCell = server.computeOnServer(minecraft -> base(minecraft, FAR_BASE).charge());
        long cellLeft = server.computeOnServer(minecraft -> RubrumCellItem.charge(base(minecraft, FAR_BASE).cell().getItem(0)));
        expect(fromCell > 0 && fromCell + cellLeft == RubrumBalance.CELL_CAPACITY, "Expected the far base to charge its frame from its cell, got " + fromCell
                + " in the frame and " + cellLeft + " in the cell");
        server.runOnServer(minecraft -> base(minecraft, BASE).frame().set(RubrumContent.VOID_CHARGE, ArmorBalance.FRAME_CAPACITY));
    }

    private static void stepInAndOut(ClientGameTestContext context, TestServerContext server)
    {
        lookAtBase(server, 0.5, 0.5, BASE);
        context.waitTicks(5);
        context.getInput().pressKey(options -> options.keyUse);
        context.waitTicks(5);
        var phase = phase(server);
        expect(phase == WornArmor.Phase.STEPPING_IN, "Expected a use on the docked base to start stepping in, got " + phase);
        boolean taken = server.computeOnServer(minecraft -> !base(minecraft, BASE).hasFrame()
                && !minecraft.overworld().getBlockState(BASE).getValue(VoidArmorBaseBlock.DOCKED));
        expect(taken, "Expected the frame gone from its base once stepped into");
        double speed = server.computeOnServer(minecraft -> player(minecraft).getAttributeValue(Attributes.MOVEMENT_SPEED));
        expect(speed == 0, "Expected the player held still while stepping in, speed " + speed);

        context.waitTicks(ArmorBalance.STEP_IN_TICKS);
        expect(phase(server) == WornArmor.Phase.WORN, "Expected the frame worn after stepping in, got " + phase(server));
        double height = server.computeOnServer(minecraft -> player(minecraft).getBbHeight());
        double scale = server.computeOnServer(minecraft -> player(minecraft).getAttributeValue(Attributes.SCALE));
        expect(Math.abs(height - ArmorBalance.WEARER_HEIGHT) < 0.01 && scale == ArmorBalance.SCALE, "Expected the wearer 1.5 times as big, height " + height);
        double reach = server.computeOnServer(minecraft -> player(minecraft).entityInteractionRange());
        expect(reach == 3 + ArmorBalance.ENTITY_REACH, "Expected the wearer's reach lengthened, got " + reach);

        server.runCommand("tp @a 0.5 " + GROUND_Y + " -4.5 180 10");
        context.waitTicks(5);
        context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
        context.waitTicks(3);
        context.takeScreenshot("void-armor-worn");

        // A short press does nothing, a held one steps out
        context.getInput().holdKey(options -> options.keyShift);
        context.waitTicks(5);
        context.getInput().releaseKey(options -> options.keyShift);
        context.waitTicks(3);
        expect(phase(server) == WornArmor.Phase.WORN, "Expected a short sneak to keep the frame on");
        holdSneakOut(context);
        expect(phase(server) == null, "Expected holding sneak to step out, got " + phase(server));
        var parked = parked(server);
        expect(parked.size() == 1, "Expected the frame parked where the wearer stepped out, found " + parked.size());
        double after = server.computeOnServer(minecraft -> player(minecraft).getAttributeValue(Attributes.SCALE));
        expect(after == 1, "Expected the player back to their own size, scale " + after);
        context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
        server.runCommand("tp @a 0.5 " + GROUND_Y + " -9.5 0 -5");
        context.waitTicks(5);
        context.takeScreenshot("void-armor-parked");

        // A use on the parked frame steps back in
        lookAt(server, 0.5, -7.5, 0.5, GROUND_Y + 1.3, -4.5);
        context.waitTicks(5);
        context.getInput().pressKey(options -> options.keyUse);
        context.waitTicks(5);
        expect(phase(server) == WornArmor.Phase.STEPPING_IN && parked(server).isEmpty(), "Expected a use on the parked frame to step back in");
        context.waitTicks(ArmorBalance.STEP_IN_TICKS);
    }

    private static void dockOnStepOut(ClientGameTestContext context, TestServerContext server)
    {
        var stand = VoidArmorBaseBlock.standAt(BASE);
        server.runCommand("tp @a " + stand.x + " " + stand.y + " " + stand.z + " 180 0");
        context.waitTicks(5);
        holdSneakOut(context);
        boolean docked = server.computeOnServer(minecraft -> base(minecraft, BASE).hasFrame()
                && minecraft.overworld().getBlockState(BASE).getValue(VoidArmorBaseBlock.DOCKED));
        expect(docked && phase(server) == null && parked(server).isEmpty(), "Expected stepping out on the empty base to dock the frame there");
    }

    /**
     * Two blocks of room over the base is too low for a wearer 2.7 tall; three, as in the void's corridors, is enough.
     */
    private static void refuseLowCeiling(ClientGameTestContext context, TestServerContext server)
    {
        server.runCommand("setblock " + at(BASE.above(2)) + " minecraft:stone");
        lookAtBase(server, 0.5, 0.5, BASE);
        context.waitTicks(5);
        context.getInput().pressKey(options -> options.keyUse);
        context.waitTicks(5);
        boolean kept = server.computeOnServer(minecraft -> base(minecraft, BASE).hasFrame());
        expect(kept && phase(server) == null, "Expected a ceiling two blocks over the base to refuse stepping in");
        server.runCommand("setblock " + at(BASE.above(2)) + " minecraft:air");
        server.runCommand("setblock " + at(BASE.above(3)) + " minecraft:stone");
        context.waitTicks(3);
    }

    private static void deathSendsHome(ClientGameTestContext context, TestServerContext server)
    {
        lookAtBase(server, 0.5, 0.5, BASE);
        context.waitTicks(5);
        context.getInput().pressKey(options -> options.keyUse);
        context.waitTicks(ArmorBalance.STEP_IN_TICKS + 5);
        expect(phase(server) == WornArmor.Phase.WORN, "Expected to step in under a ceiling three blocks over the base, and wear the frame before dying");
        server.runCommand("setblock " + at(BASE.above(3)) + " minecraft:air");
        server.runCommand("tp @a 0.5 " + GROUND_Y + " -10.5");
        context.waitTicks(3);
        server.runCommand("kill @a");
        context.waitTicks(3);
        long charge = server.computeOnServer(minecraft -> base(minecraft, BASE).charge());
        boolean home = server.computeOnServer(minecraft -> base(minecraft, BASE).hasFrame());
        expect(home && charge <= 4 * ArmorBalance.BASE_INTAKE, "Expected the frame back on its base, emptied, after the wearer died; charge " + charge);
        context.waitTicks(20);
        expect(phase(server) == null, "Expected the respawned player out of the frame, got " + phase(server));
    }

    private static void homelessParksBesideRespawn(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            var frame = base(minecraft, FAR_BASE).undock();
            ArmorWear.stepIn(player, frame, player.position());
        });
        context.waitTicks(ArmorBalance.STEP_IN_TICKS + 5);
        server.runCommand("setblock " + at(FAR_BASE) + " minecraft:air");
        server.runCommand("kill @e[type=minecraft:item]");
        server.runCommand("kill @a");
        context.waitTicks(30);
        var parked = parked(server);
        double distance = server.computeOnServer(minecraft ->
        {
            var player = player(minecraft);
            var all = minecraft.overworld().getEntitiesOfClass(ParkedArmor.class, new AABB(player.blockPosition()).inflate(8));
            return all.isEmpty() ? -1 : all.getFirst().distanceTo(player);
        });
        expect(phase(server) == null && distance >= 0 && distance < 3,
                "Expected a frame with no base left to stand beside its respawned wearer, distance " + distance + ", parked " + parked.size());
        context.takeScreenshot("void-armor-parked-at-respawn");
    }

    private static void holdSneakOut(ClientGameTestContext context)
    {
        context.getInput().holdKey(options -> options.keyShift);
        context.waitTicks(ArmorBalance.STEP_OUT_HOLD_TICKS + 2);
        context.getInput().releaseKey(options -> options.keyShift);
        context.waitTicks(ArmorBalance.STEP_OUT_TICKS + 3);
    }

    private static void lookAtBase(TestServerContext server, double feetX, double feetZ, BlockPos base)
    {
        lookAt(server, feetX, feetZ, base.getX() + 0.5, base.getY() + 0.15, base.getZ() + 0.5);
    }

    private static void lookAt(TestServerContext server, double feetX, double feetZ, double x, double y, double z)
    {
        double dx = x - feetX;
        double dy = y - (GROUND_Y + 1.62);
        double dz = z - feetZ;
        double yaw = Math.toDegrees(Math.atan2(-dx, dz));
        double pitch = -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
        server.runCommand("tp @a " + feetX + " " + GROUND_Y + " " + feetZ + " " + yaw + " " + pitch);
    }

    private static WornArmor.@Nullable Phase phase(TestServerContext server)
    {
        return server.computeOnServer(minecraft ->
        {
            var worn = player(minecraft).getAttached(ArmorWear.WORN);
            return worn == null ? null : worn.phase();
        });
    }

    private static List<ParkedArmor> parked(TestServerContext server)
    {
        return server.computeOnServer(minecraft -> minecraft.overworld().getEntitiesOfClass(ParkedArmor.class, new AABB(BlockPos.ZERO).inflate(64, 128, 64)));
    }

    private static VoidArmorBaseTile base(MinecraftServer minecraft, BlockPos pos)
    {
        if (minecraft.overworld().getBlockEntity(pos) instanceof VoidArmorBaseTile base)
            return base;
        throw new AssertionError("No Void Armor Base at " + pos.toShortString());
    }

    private static String at(BlockPos pos)
    {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }
}
