package net.voxelindustry.voidheart.client.render.state;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

public class ShatterForgeRenderState extends FloatingItemRenderState
{
    public record BeamDraw(float fromX, float fromY, float fromZ, float toX, float toY, float toZ,
                           float red, float green, float blue, float radius)
    {
    }

    public record HintDraw(BlockPos offset, BlockState ghostState, boolean conflict, boolean shrink)
    {
    }

    public final List<BeamDraw> beams = new ArrayList<>();
    public final List<HintDraw> hints = new ArrayList<>();
}
