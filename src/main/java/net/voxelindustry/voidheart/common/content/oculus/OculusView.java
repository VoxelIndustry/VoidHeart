package net.voxelindustry.voidheart.common.content.oculus;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;

import java.util.Optional;
import java.util.UUID;

/**
 * What a table projects, shared by every block of the structure.
 *
 * @param zoom   step from the widest view, each one halving the world blocks shown per table block
 * @param follow player the projection is centered on, the table itself otherwise
 * @param asleep whether redstone power keeps the table from projecting
 */
public record OculusView(int zoom, Optional<UUID> follow, boolean leyLines, boolean asleep)
{
    /**
     * World blocks per table block at each zoom step, halving every two steps.
     */
    private static final int[] SCALES = { 32, 24, 16, 12, 8, 6, 4 };
    public static final int ZOOM_STEPS = SCALES.length;
    public static final int WIDEST_SCALE = SCALES[0];

    public static final OculusView DEFAULT = new OculusView(0, Optional.empty(), false, false);

    public static final Codec<OculusView> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.intRange(0, ZOOM_STEPS - 1).optionalFieldOf("zoom", 0).forGetter(OculusView::zoom),
            UUIDUtil.CODEC.optionalFieldOf("follow").forGetter(OculusView::follow),
            Codec.BOOL.optionalFieldOf("ley_lines", false).forGetter(OculusView::leyLines),
            Codec.BOOL.optionalFieldOf("asleep", false).forGetter(OculusView::asleep)
    ).apply(instance, OculusView::new));

    /**
     * World blocks represented along each axis of a single table block.
     */
    public int scale()
    {
        return SCALES[zoom];
    }

    public OculusView zoomIn()
    {
        return new OculusView(Math.min(zoom + 1, ZOOM_STEPS - 1), follow, leyLines, asleep);
    }

    public OculusView zoomOut()
    {
        return new OculusView(Math.max(zoom - 1, 0), follow, leyLines, asleep);
    }

    public OculusView following(Optional<UUID> target)
    {
        return new OculusView(zoom, target, leyLines, asleep);
    }

    public OculusView toggleLeyLines()
    {
        return new OculusView(zoom, follow, !leyLines, asleep);
    }

    public OculusView sleeping(boolean asleep)
    {
        return new OculusView(zoom, follow, leyLines, asleep);
    }
}
