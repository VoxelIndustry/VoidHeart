package net.voxelindustry.voidheart.client.render.state;

import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

public class VoidAltarRenderState extends FloatingItemRenderState
{
    public final List<BlockPos>        missingPillarOffsets = new ArrayList<>();
    public final List<BlockStateModelPart> pillarParts      = new ArrayList<>();

    public boolean renderVoidCube;
    public int     warmProgress;
    public int     coolProgress;
    public double  cameraDistanceSqr;

    public final ItemStackRenderState       output    = new ItemStackRenderState();
    public       int                        outputCount;
    public final List<ItemStackRenderState> toConsume = new ArrayList<>();
}
