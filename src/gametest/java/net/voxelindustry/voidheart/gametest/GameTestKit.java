package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Hud;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.research.Knowledge;
import net.voxelindustry.voidheart.common.content.research.PlayerKnowledge;
import net.voxelindustry.voidheart.common.curse.CurseKind;
import net.voxelindustry.voidheart.common.curse.Curses;
import net.voxelindustry.voidheart.common.world.daedalus.DaedalusTheme;
import net.voxelindustry.voidheart.common.world.daedalus.DistrictPos;

import java.util.function.Predicate;
import java.util.function.Supplier;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Helpers shared by the client game tests, imported statically. Every test runs with a single player.
 */
public final class GameTestKit
{
    /**
     * Grass level of the default superflat world the tests create.
     */
    public static final int FLAT_GROUND = -61;
    public static final int FLAT_FEET   = FLAT_GROUND + 1;

    private static final double EYE_HEIGHT = 1.62;

    private GameTestKit()
    {
    }

    public static Identifier id(String path)
    {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }

    public static ServerPlayer player(MinecraftServer minecraft)
    {
        return minecraft.getPlayerList().getPlayers().getFirst();
    }

    public static void expect(boolean condition, String message)
    {
        if (!condition)
            throw new AssertionError(message);
    }

    public static void expectClient(ClientGameTestContext context, String message, Predicate<Minecraft> condition)
    {
        if (!context.computeOnClient(condition::test))
            throw new AssertionError(message);
    }

    public static void waitFor(ClientGameTestContext context, TestServerContext server, int maxTicks, String message, Predicate<MinecraftServer> condition)
    {
        waitFor(context, server, maxTicks, () -> message, condition);
    }

    public static void waitFor(ClientGameTestContext context, TestServerContext server, int maxTicks, Supplier<String> message, Predicate<MinecraftServer> condition)
    {
        for (int tick = 0; tick < maxTicks; tick++)
        {
            if (server.computeOnServer(condition::test))
                return;
            context.waitTick();
        }
        throw new AssertionError(message.get() + " within " + maxTicks + " ticks");
    }

    /**
     * Stops the day cycle at the given time, such as {@code noon} or {@code midnight}.
     */
    public static void freezeTime(TestServerContext server, String time)
    {
        server.runCommand("time set " + time);
        server.runCommand("gamerule advance_time false");
    }

    // Moving and aiming the player

    public static void tp(TestServerContext server, double x, double y, double z)
    {
        server.runCommand("tp @a " + x + " " + y + " " + z);
    }

    /**
     * The arguments of a {@code tp} that puts the player's feet at one point and their eyes on another.
     */
    public static String aimedPose(double feetX, double feetY, double feetZ, double x, double y, double z)
    {
        double dx = x - feetX;
        double dy = y - (feetY + EYE_HEIGHT);
        double dz = z - feetZ;
        double yaw = Math.toDegrees(Math.atan2(-dx, dz));
        double pitch = -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
        return feetX + " " + feetY + " " + feetZ + " " + yaw + " " + pitch;
    }

    public static void aimCamera(TestServerContext server, double feetX, double feetY, double feetZ, double x, double y, double z)
    {
        server.runCommand("tp @a " + aimedPose(feetX, feetY, feetZ, x, y, z));
    }

    public static void aimFrom(ClientGameTestContext context, TestServerContext server, double feetX, double feetY, double feetZ, double x, double y, double z)
    {
        aimFrom(context, server, feetX, feetY, feetZ, x, y, z, 3);
    }

    /**
     * Aims the player and keeps them flying there, since landing ends creative flight and they would fall away from the framing.
     */
    public static void aimFrom(ClientGameTestContext context, TestServerContext server, double feetX, double feetY, double feetZ, double x, double y, double z, int settleTicks)
    {
        aimCamera(server, feetX, feetY, feetZ, x, y, z);
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
        });
        context.waitTicks(settleTicks);
    }

    public static void heal(MinecraftServer minecraft)
    {
        var player = player(minecraft);
        player.setHealth(player.getMaxHealth());
    }

    // Entities and the world

    public static void clearScene(TestServerContext server)
    {
        server.runCommand("kill @e[type=!player]");
    }

    /**
     * Clears a 41 block wide slice of the superflat world around x, leaving only the grass.
     */
    public static void clearArea(TestServerContext server, double x)
    {
        int from = (int) x - 20;
        int to = (int) x + 20;
        server.runCommand("fill " + from + " " + FLAT_FEET + " -20 " + to + " " + (FLAT_FEET + 8) + " 20 air");
        server.runCommand("fill " + from + " " + FLAT_GROUND + " -20 " + to + " " + FLAT_GROUND + " grass_block");
    }

    public static <T extends Mob> T spawn(MinecraftServer minecraft, EntityType<T> type, double x, double y, double z)
    {
        var level = minecraft.overworld();
        var mob = type.create(level, EntitySpawnReason.COMMAND);
        mob.snapTo(x, y, z, 90, 0);
        level.addFreshEntity(mob);
        return mob;
    }

    public static <T extends Entity> T first(MinecraftServer minecraft, EntityType<T> type)
    {
        var found = minecraft.overworld().getEntities(type, Entity::isAlive);
        if (found.isEmpty())
            throw new AssertionError("Expected a living " + type);
        return found.getFirst();
    }

    public static void useOn(ServerPlayer player, BlockPos pos)
    {
        var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        player.gameMode.useItemOn(player, player.level(), player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
    }

    /**
     * Sets a private int field, for skipping a mob's cooldowns or timers.
     */
    public static void setIntField(Object target, Class<?> owner, String name, int value)
    {
        try
        {
            var field = owner.getDeclaredField(name);
            field.setAccessible(true);
            field.setInt(target, value);
        }
        catch (ReflectiveOperationException e)
        {
            throw new AssertionError(e);
        }
    }

    // Input and screens

    public static void resize(ClientGameTestContext context, int width, int height, int scale)
    {
        context.getInput().resizeWindow(width, height);
        context.runOnClient(minecraft ->
        {
            minecraft.options.guiScale().set(scale);
            minecraft.resizeGui();
        });
        context.waitTicks(4);
    }

    public static void hover(ClientGameTestContext context, int[] guiPosition)
    {
        double scale = context.computeOnClient(minecraft -> minecraft.getWindow().getGuiScale());
        context.getInput().setCursorPos(guiPosition[0] * scale, guiPosition[1] * scale);
        context.waitTicks(3);
    }

    public static void click(ClientGameTestContext context, int[] guiPosition)
    {
        click(context, guiPosition[0], guiPosition[1], 2);
    }

    /**
     * Left clicks at a position in GUI units, then waits for the screen to react.
     */
    public static void click(ClientGameTestContext context, float x, float y, int afterTicks)
    {
        double scale = context.computeOnClient(minecraft -> minecraft.getWindow().getGuiScale());
        context.getInput().setCursorPos(x * scale, y * scale);
        context.waitTicks(2);
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        context.waitTicks(afterTicks);
    }

    public static void rightClick(ClientGameTestContext context)
    {
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
        context.waitTicks(5);
    }

    /**
     * The action bar message showing, or an empty string.
     */
    public static String overlay(ClientGameTestContext context)
    {
        return context.computeOnClient(minecraft ->
        {
            try
            {
                var field = Hud.class.getDeclaredField("overlayMessageString");
                field.setAccessible(true);
                var message = (Component) field.get(minecraft.gui.hud);
                return message == null ? "" : message.getString();
            }
            catch (ReflectiveOperationException e)
            {
                throw new AssertionError(e);
            }
        });
    }

    // Mod state

    public static PlayerKnowledge knowledge(MinecraftServer minecraft)
    {
        return Knowledge.of(minecraft, player(minecraft).getUUID());
    }

    public static void expectRunning(TestServerContext server, CurseKind expected, String what)
    {
        var actual = server.computeOnServer(minecraft -> Curses.running(player(minecraft)));
        if (actual != expected)
            throw new AssertionError("Expected " + what + ", the server runs " + actual);
    }

    /**
     * The district of a theme closest to the middle of the Daedalus.
     */
    public static DistrictPos closestDistrict(DaedalusTheme theme)
    {
        for (int radius = 0; radius < 64; radius++)
        {
            for (int z = -radius; z <= radius; z++)
            {
                for (int x = -radius; x <= radius; x++)
                {
                    if (Math.max(Math.abs(x), Math.abs(z)) == radius && DaedalusTheme.at(new DistrictPos(x, z)) == theme)
                        return new DistrictPos(x, z);
                }
            }
        }
        throw new AssertionError("No district of " + theme);
    }
}
