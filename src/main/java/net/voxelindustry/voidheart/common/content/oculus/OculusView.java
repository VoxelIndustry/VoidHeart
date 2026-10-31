package net.voxelindustry.voidheart.common.content.oculus;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;

import java.util.Optional;
import java.util.UUID;

/**
 * What a table projects, shared by every block of the structure.
 *
 * @param zoom   step from the widest view, each one narrowing the world blocks shown per table block
 * @param follow player the projection is centered on, the table itself otherwise
 * @param asleep whether redstone power keeps the table from projecting
 * @param caving whether the hollows inside the land are shown instead of its surface, when no followed player decides
 */
public record OculusView(int zoom, Optional<UUID> follow, boolean leyLines, boolean asleep, boolean caving)
{
    /**
     * World blocks per table block at each zoom step, halving every two steps below 1:32 and gentler beyond it.
     */
    private static final int[] SCALES = { 48, 40, 32, 24, 16, 12, 8, 6, 4 };
    public static final int ZOOM_STEPS = SCALES.length;
    public static final int WIDEST_SCALE = SCALES[0];
    /**
     * A new table opens at 1:32, with room to zoom further out.
     */
    public static final int DEFAULT_ZOOM = 2;

    public static final OculusView DEFAULT = new OculusView(DEFAULT_ZOOM, Optional.empty(), false, false, false);

    public static final Codec<OculusView> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.intRange(0, ZOOM_STEPS - 1).optionalFieldOf("zoom", DEFAULT_ZOOM).forGetter(OculusView::zoom),
            UUIDUtil.CODEC.optionalFieldOf("follow").forGetter(OculusView::follow),
            Codec.BOOL.optionalFieldOf("ley_lines", false).forGetter(OculusView::leyLines),
            Codec.BOOL.optionalFieldOf("asleep", false).forGetter(OculusView::asleep),
            Codec.BOOL.optionalFieldOf("caving", false).forGetter(OculusView::caving)
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
        return new OculusView(Math.min(zoom + 1, ZOOM_STEPS - 1), follow, leyLines, asleep, caving);
    }

    public OculusView zoomOut()
    {
        return new OculusView(Math.max(zoom - 1, 0), follow, leyLines, asleep, caving);
    }

    public OculusView following(Optional<UUID> target)
    {
        return new OculusView(zoom, target, leyLines, asleep, caving);
    }

    public OculusView toggleLeyLines()
    {
        return new OculusView(zoom, follow, !leyLines, asleep, caving);
    }

    public OculusView toggleCaving()
    {
        return new OculusView(zoom, follow, leyLines, asleep, !caving);
    }

    public OculusView sleeping(boolean asleep)
    {
        return new OculusView(zoom, follow, leyLines, asleep, caving);
    }
}
