package net.voxelindustry.voidheart.client.render.state;

import net.minecraft.core.BlockPos;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public class ShatterForgeRenderState extends FloatingItemRenderState
{
    /**
     * @param headOffset distance between the front of a forming beam and {@code from}, the beam is drawn up to it
     * @param tailOffset distance between the tail of a dissolving beam and {@code from}, the beam is drawn from it
     * @param colors     one color per veil, inner layer first then outer layer
     */
    public record VeilDraw(Vec3 from, Vec3 to, float length, float headOffset, float tailOffset, int[] colors, int coreColor, float radius,
                           boolean taperFrom, boolean taperTo)
    {
    }

    /**
     * @param growth share of the strand already stretched from its anchor toward the beam
     */
    public record StrandDraw(Vec3 anchor, Vec3 target, int color, float growth, float seed)
    {
    }

    public enum HintKind
    {
        MISSING,
        CONFLICT,
        OPTIONAL
    }

    /**
     * @param label text floating above the hint, null for none
     */
    public record HintDraw(BlockPos offset, @Nullable BlockState ghostState, HintKind kind, boolean shrink, @Nullable FormattedCharSequence label, int labelWidth)
    {
    }

    public final List<VeilDraw>   veils   = new ArrayList<>();
    public final List<StrandDraw> strands = new ArrayList<>();
    public final List<HintDraw>   hints   = new ArrayList<>();
}
