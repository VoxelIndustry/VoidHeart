package net.voxelindustry.voidheart.client.render.state;

import java.util.ArrayList;
import java.util.List;

public class ShatterForgeRenderState extends FloatingItemRenderState
{
    public record BeamDraw(float fromX, float fromY, float fromZ, float toX, float toY, float toZ,
                           float red, float green, float blue, float radius)
    {
    }

    public final List<BeamDraw> beams = new ArrayList<>();
}
