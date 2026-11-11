package net.voxelindustry.voidheart.client.render.state;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.core.Direction;

import java.util.List;

public class WraithStainRenderState extends EntityRenderState
{
    public Direction face = Direction.UP;
    public int growTicks;
    public int lingerTicks;
    public float halfU;
    public float halfV;
    public boolean opens;
    // A mark of the local player's own open tunnel, whose hole looks into it
    public boolean window;
    // The parts of the mark on faces that can hold it
    public List<float[]> parts = List.of();
}
