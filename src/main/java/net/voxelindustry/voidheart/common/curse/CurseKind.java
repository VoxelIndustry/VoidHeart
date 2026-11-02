package net.voxelindustry.voidheart.common.curse;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.dimension.DimensionType;

import java.util.function.IntFunction;

/**
 * What the server needs to know about each curse. What the afflicted player perceives lives on their client,
 * see {@code client.curse.CurseDirector}, and nobody else ever notices it.
 */
public enum CurseKind
{
    ECHO("echo", 20, false),
    OMEN("omen", 40, true),
    VIGIL("vigil", 60, false),
    RAPTURE("rapture", 80, true);

    private static final CurseKind[] VALUES = values();

    public static final StreamCodec<ByteBuf, CurseKind> STREAM_CODEC = ByteBufCodecs.idMapper((IntFunction<CurseKind>) id -> VALUES[id], Enum::ordinal);

    private final String  id;
    private final int     threshold;
    private final boolean needsNightSky;

    CurseKind(String id, int threshold, boolean needsNightSky)
    {
        this.id = id;
        this.threshold = threshold;
        this.needsNightSky = needsNightSky;
    }

    public String id()
    {
        return id;
    }

    /**
     * The corruption at which this curse starts striking.
     */
    public int threshold()
    {
        return threshold;
    }

    public boolean canStrike(ServerPlayer player)
    {
        return !needsNightSky || underNightSky(player);
    }

    private static boolean underNightSky(ServerPlayer player)
    {
        var level = player.level();
        return level.dimensionType().skybox() == DimensionType.Skybox.OVERWORLD
                && level.isDarkOutside()
                && level.canSeeSky(player.blockPosition());
    }
}
