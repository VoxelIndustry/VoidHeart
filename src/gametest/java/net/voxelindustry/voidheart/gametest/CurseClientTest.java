package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEventListener;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.voxelindustry.voidheart.client.curse.CurseDirector;
import net.voxelindustry.voidheart.common.curse.Corruption;
import net.voxelindustry.voidheart.common.curse.CurseKind;
import net.voxelindustry.voidheart.common.curse.CurseTimings;
import net.voxelindustry.voidheart.common.curse.Curses;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Corruption stays on a player through death, the debug commands strike and stop curses, and the sound curse plays out in order.
 */
public class CurseClientTest implements FabricClientGameTest
{
    private static final int GROUND = -61;

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
            server.runCommand("tp @a 0.5 " + (GROUND + 1) + " 0.5 0 0");
            context.waitTicks(20);

            testCorruption(context, server);
            testCommands(context, server);
            testEchoSequence(context, server);
            testStarsTakenOver(context, server);
        }
    }

    private void testCorruption(ClientGameTestContext context, TestServerContext server)
    {
        expectCorruption(server, 0);
        server.runOnServer(minecraft -> Corruption.set(player(minecraft), Corruption.MAX + 50));
        expectCorruption(server, Corruption.MAX);
        server.runCommand("voidheart corruption set 42 @a");
        server.runCommand("kill @a");
        context.waitTicks(5);
        server.runOnServer(minecraft -> player(minecraft).connection.handleClientCommand(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN)));
        context.waitTicks(10);
        expectCorruption(server, 42);
        server.runCommand("voidheart corruption set 0 @a");
        server.runCommand("tp @a 0.5 " + (GROUND + 1) + " 0.5 0 0");
        context.waitTicks(5);
    }

    private void testCommands(ClientGameTestContext context, TestServerContext server)
    {
        server.runCommand("voidheart curse trigger omen @a");
        context.waitTicks(3);
        expectRunning(server, null, "a sky curse refused by day");

        server.runCommand("voidheart curse force omen @a");
        context.waitTicks(3);
        expectRunning(server, CurseKind.OMEN, "a forced sky curse by day");
        if (context.computeOnClient(minecraft -> CurseDirector.active() == null))
            throw new AssertionError("Expected the client to run the forced curse");

        server.runCommand("voidheart curse stop @a");
        context.waitTicks(3);
        expectRunning(server, null, "a stopped curse");
        if (context.computeOnClient(minecraft -> CurseDirector.active() != null))
            throw new AssertionError("Expected the client to let go of the stopped curse");
    }

    /**
     * What makes the curse work is what is heard and in which order, so the sounds actually sent to the engine are checked, with their timing.
     */
    private void testEchoSequence(ClientGameTestContext context, TestServerContext server)
    {
        var heard = Collections.synchronizedList(new ArrayList<Heard>());
        SoundEventListener listener = (SoundInstance sound, WeighedSoundEvents events, float range) -> heard.add(new Heard(sound));
        context.runOnClient(minecraft -> minecraft.getSoundManager().addListener(listener));

        server.runCommand("voidheart curse trigger echo @a");
        context.waitTicks(CurseTimings.ECHO_TICKS + 5);

        context.runOnClient(minecraft -> minecraft.getSoundManager().removeListener(listener));
        expectRunning(server, null, "the sound curse over");
        if (context.computeOnClient(minecraft -> CurseDirector.active() != null))
            throw new AssertionError("Expected the sound curse to be over on the client");

        var growl = indexOf(heard, SoundEvents.CREEPER_HURT.location().toString());
        var hiss = indexOf(heard, SoundEvents.CREEPER_PRIMED.location().toString());
        var laugh = indexOf(heard, SoundEvents.WITCH_CELEBRATE.location().toString());
        if (growl < 0 || hiss < growl || laugh < hiss)
            throw new AssertionError("Expected a growl, a hiss then a laugh, got " + heard);

        var voice = heard.get(laugh);
        if (voice.pitch() > 0.5F || !voice.relative())
            throw new AssertionError("Expected the laugh pitched as low as it goes from inside the player's head, got " + voice);
        if (heard.stream().filter(sound -> sound.id().equals(voice.id())).count() != 2)
            throw new AssertionError("Expected a second voice thickening the laugh, got " + heard);
        if (heard.stream().anyMatch(sound -> sound.id().contains("explode")))
            throw new AssertionError("Expected no blast to ever come, got " + heard);
    }

    /**
     * A curse must take over the very stars already in the sky, so they stand at the same places in both captures.
     */
    private void testStarsTakenOver(ClientGameTestContext context, TestServerContext server)
    {
        server.runCommand("time set midnight");
        server.runCommand("tp @a 0.5 " + (GROUND + 1) + " 0.5 0 -55");
        context.getInput().pressKey(InputConstants.KEY_F1);
        context.waitTicks(10);
        context.takeScreenshot("curse-stars-vanilla");

        server.runCommand("voidheart curse trigger omen @a");
        context.waitTicks(3);
        expectRunning(server, CurseKind.OMEN, "a sky curse at night under the open sky");
        context.takeScreenshot("curse-stars-taken-over");

        server.runCommand("voidheart curse stop @a");
        context.getInput().pressKey(InputConstants.KEY_F1);
        context.waitTicks(3);
    }

    private static int indexOf(List<Heard> heard, String id)
    {
        synchronized (heard)
        {
            for (int i = 0; i < heard.size(); i++)
            {
                if (heard.get(i).id().equals(id))
                    return i;
            }
        }
        return -1;
    }

    private static void expectCorruption(TestServerContext server, int expected)
    {
        int actual = server.computeOnServer(minecraft -> Corruption.get(player(minecraft)));
        if (actual != expected)
            throw new AssertionError("Expected " + expected + " corruption, got " + actual);
    }

    private static void expectRunning(TestServerContext server, CurseKind expected, String what)
    {
        var actual = server.computeOnServer(minecraft -> Curses.running(player(minecraft)));
        if (actual != expected)
            throw new AssertionError("Expected " + what + ", the server runs " + actual);
    }

    private static ServerPlayer player(MinecraftServer minecraft)
    {
        return minecraft.getPlayerList().getPlayers().getFirst();
    }

    private record Heard(String id, float pitch, boolean relative)
    {
        Heard(SoundInstance sound)
        {
            this(sound.getIdentifier().toString(), sound.getPitch(), sound.isRelative());
        }
    }
}
