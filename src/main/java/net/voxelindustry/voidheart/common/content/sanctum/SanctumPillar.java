package net.voxelindustry.voidheart.common.content.sanctum;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;

/**
 * The pillar that rises where a sanctum's guardian sat once it is dead: a plinth, a drum of column, and the cradle on top holding the
 * contraption the guardian kept. It rises a stone at a time while the guardian comes apart.
 */
public final class SanctumPillar
{
    public static final int STONES = 3;

    private SanctumPillar()
    {
    }

    private static BlockState stone(int stone)
    {
        return switch (stone)
        {
            case 0 -> VoidHeartBlocks.VOIDSTONE_POLISHED.defaultBlockState();
            case 1 -> VoidHeartBlocks.VOIDSTONE_PILLAR.defaultBlockState();
            default -> VoidHeartBlocks.BLESSED_CRADLE.defaultBlockState();
        };
    }

    /** Raises one stone of a sanctum's pillar; the last one blesses the cradle and marks the pillar risen. */
    public static void raise(ServerLevel level, int index, int stone)
    {
        var sanctum = Sanctums.state(level.getServer()).get(index);
        if (sanctum == null || sanctum.raised() || stone < 0 || stone >= STONES)
            return;

        var pos = Sanctums.altar(index).above(stone);
        var state = stone(stone);
        level.setBlock(pos, state, Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.DEEPSLATE_BRICKS_PLACE, SoundSource.BLOCKS, 1.4F, 0.5F + stone * 0.1F);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 30, 0.4, 0.4, 0.4, 0.1);
        if (stone == STONES - 1)
        {
            if (level.getBlockEntity(pos) instanceof BlessedCradleTile cradle)
                cradle.bless(level.getRandom());
            level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.8F, 0.7F);
            Sanctums.state(level.getServer()).markRaised(index);
        }
    }

    /** Finishes a pillar whose rising was cut short, as when the world closed while its guardian came apart. */
    public static void raiseAll(ServerLevel level, int index)
    {
        for (int stone = 0; stone < STONES; stone++)
            raise(level, index, stone);
    }
}
