package net.voxelindustry.voidheart.client.render.state;

import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;

import java.util.ArrayList;
import java.util.List;

public class PermeableBarrierRenderState extends BlockEntityRenderState
{
    public enum Mode
    {
        HIDDEN,
        SIGIL,
        SOLID,
        GHOST,
        FAINT_GHOST
    }

    public final List<BlockStateModelPart> appearanceParts = new ArrayList<>();
    public final boolean[]                 culledFaces     = new boolean[6];

    public Mode  mode = Mode.HIDDEN;
    public float worldTime;
    public float waveIntensity;
}
