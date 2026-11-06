package net.voxelindustry.voidheart.common.content.research;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

/**
 * A brain kept thinking for a repository, which gathers a little more knowledge each second for every one of them it holds.
 */
public class CogitantTile extends RepositoryBoundTile
{
    /** Where the brain sits in its cage, and so where its thoughts leave from. */
    public static final double BRAIN_HEIGHT = 7 / 16D;

    private static final float MOTE_CHANCE = 0.04F;

    public CogitantTile(BlockPos pos, BlockState state)
    {
        super(VoidHeartTiles.COGITANT, pos, state);
    }

    public static void clientTick(Level world, BlockPos pos, BlockState state, CogitantTile cogitant)
    {
        if (!cogitant.isActive() || cogitant.getLink().isEmpty() || world.getRandom().nextFloat() > MOTE_CHANCE)
            return;

        if (world.getBlockEntity(cogitant.getLink().get().repository()) instanceof KnowledgeRepositoryTile repository)
            repository.drawMote(new Vec3(pos.getX() + 0.5, pos.getY() + BRAIN_HEIGHT, pos.getZ() + 0.5));
    }
}
