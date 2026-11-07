package net.voxelindustry.voidheart.common.content.research;

import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

import java.util.UUID;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Bears strain for its owner while a research needs it. Its wear is applied to the owner's knowledge, where research runs even with this unloaded,
 * and told back here whenever the repository looks around. It gives out once nothing is left.
 */
public class MeatProxyTile extends RepositoryBoundTile
{
    /** Below this share left it looks spent, then nearly gone. */
    public static final float WORN   = 0.6F;
    public static final float FAILING = 0.25F;

    private static final double SAME = 1E-3;

    /** Changes when it is placed again, the seconds left go with the item. */
    @Getter
    private UUID    id = UUID.randomUUID();
    @Getter
    private double  left = Knowledge.proxySeconds();
    /** Whether it wore down since the repository last looked, which shows. */
    @Getter
    private boolean bearing;

    public MeatProxyTile(BlockPos pos, BlockState state)
    {
        super(VoidHeartTiles.MEAT_PROXY, pos, state);
    }

    /** From 1 when fresh to 0 when it gives out. */
    public float share()
    {
        return (float) Math.clamp(left / Knowledge.proxySeconds(), 0, 1);
    }

    public void restore(double left)
    {
        this.left = Math.clamp(left, 0, Knowledge.proxySeconds());
        setChanged();
    }

    /** What the owner's knowledge says is left of it. */
    public void wearTo(ServerLevel level, double now)
    {
        boolean wore = now < left - SAME;
        if (!wore && !bearing)
            return;

        left = Math.max(0, Math.min(left, now));
        bearing = wore;
        setChanged();
        sync();

        if (left <= 0)
            giveOut(level);
    }

    private void giveOut(ServerLevel level)
    {
        var pos = getBlockPos();
        getLink().map(link -> level.getServer().getPlayerList().getPlayer(link.owner()))
                .ifPresent(owner -> owner.sendOverlayMessage(Component.translatable(MODID + ".meat_proxy.gave_out")));
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.NETHER_WART_BLOCK.defaultBlockState()), pos.getX() + 0.5, pos.getY() + 0.5,
                pos.getZ() + 0.5, 40, 0.3, 0.3, 0.3, 0.1);
        level.playSound(null, pos, SoundEvents.SLIME_DEATH, SoundSource.BLOCKS, 1, 0.6F);
        level.removeBlock(pos, false);
    }

    @Override
    protected void loadAdditional(ValueInput tag)
    {
        super.loadAdditional(tag);

        id = tag.read("proxy_id", UUIDUtil.CODEC).orElse(id);
        left = tag.getDoubleOr("left", left);
        bearing = tag.getBooleanOr("bearing", false);
    }

    @Override
    protected void saveAdditional(ValueOutput tag)
    {
        tag.store("proxy_id", UUIDUtil.CODEC, id);
        tag.putDouble("left", left);
        tag.putBoolean("bearing", bearing);

        super.saveAdditional(tag);
    }
}
