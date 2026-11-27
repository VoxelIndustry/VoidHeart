package net.voxelindustry.voidheart.common.content.mausoleum;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartSounds;

/**
 * Cracks a mausoleum's sarcophagus once its rider is dead, and lays its lid back for a review.
 */
public final class Sarcophagi
{
    private static final int DUST = 40;
    private static final int FLAMES = 24;

    private Sarcophagi()
    {
    }

    public static void open(ServerLevel level, int index)
    {
        var pos = Mausoleums.sarcophagus(index);
        if (!(level.getBlockEntity(pos) instanceof SarcophagusTile sarcophagus))
            return;
        if (!sarcophagus.isOpen())
        {
            sarcophagus.open(level.getRandom());
            level.playSound(null, pos, VoidHeartSounds.SARCOPHAGUS_CRACK, SoundSource.BLOCKS, 1.4F, 0.7F);
            double x = pos.getX() + 0.5, y = pos.getY() + 1, z = pos.getZ() + 0.5;
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, VoidHeartBlocks.VOIDSTONE_POLISHED.defaultBlockState()), x, y, z, DUST, 0.5, 0.2, 1.2, 0.1);
            level.sendParticles(VoidHeart.FUNERARY_FLAME_PARTICLE, x, y, z, FLAMES, 0.4, 0.1, 1.1, 0.02);
        }
        Mausoleums.state(level.getServer()).markOpened(index);
    }

    public static void close(ServerLevel level, int index)
    {
        if (level.getBlockEntity(Mausoleums.sarcophagus(index)) instanceof SarcophagusTile sarcophagus)
            sarcophagus.close();
    }
}
