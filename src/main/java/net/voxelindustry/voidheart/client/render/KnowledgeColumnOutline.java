package net.voxelindustry.voidheart.client.render;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.minecraft.client.renderer.state.level.BlockOutlineRenderState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.voxelindustry.voidheart.common.content.research.KnowledgeColumnBlock;
import net.voxelindustry.voidheart.common.content.research.KnowledgeColumnTile;
import org.jspecify.annotations.Nullable;

/**
 * Outlines only the knowledge column looked at, where it floats at this moment, instead of every column of its space.
 */
public final class KnowledgeColumnOutline
{
    private KnowledgeColumnOutline()
    {
    }

    public static void afterExtraction(LevelExtractionContext context, @Nullable HitResult hitResult)
    {
        var outline = context.levelState().blockOutlineRenderState;
        if (outline == null || !(hitResult instanceof BlockHitResult hit) || !hit.getBlockPos().equals(outline.pos()))
            return;

        var level = context.level();
        var pos = outline.pos();
        var state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof KnowledgeColumnBlock) || !(level.getBlockEntity(pos) instanceof KnowledgeColumnTile tile))
            return;

        int quarter = KnowledgeColumnBlock.quarterHit(state, pos, hit.getLocation(), hit.getDirection());
        float rise = KnowledgeColumnRender.rise(tile, quarter, context.deltaTracker().getGameTimeDeltaPartialTick(false));
        context.levelState().blockOutlineRenderState = new BlockOutlineRenderState(pos, outline.isTranslucent(), outline.highContrast(),
                KnowledgeColumnBlock.columnShape(quarter, rise));
    }
}
