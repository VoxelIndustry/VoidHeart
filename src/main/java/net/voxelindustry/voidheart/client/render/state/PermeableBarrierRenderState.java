package net.voxelindustry.voidheart.client.render.state;

import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

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
    public final int[]                     faceLight       = new int[6];
    public final float[]                   faceShade       = new float[6];

    public final TextureAtlasSprite[]      faceSprites     = new TextureAtlasSprite[6];
    /**
     * Points the mosaic spreads from, relative to this block: its own and those of the barriers around it.
     */
    public final List<float[]>             seedPoints      = new ArrayList<>();

    public Mode  mode = Mode.HIDDEN;
    public long  seed;
    /**
     * Progress of the mosaic animation since the barrier last turned solid or open for the viewer, -1 when idle.
     */
    public float transition = -1;
    public float worldTime;
    public float waveIntensity;
}
