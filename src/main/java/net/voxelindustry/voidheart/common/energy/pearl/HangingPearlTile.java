package net.voxelindustry.voidheart.common.energy.pearl;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.voxelindustry.steamlayer.tile.TileBase;
import net.voxelindustry.voidheart.common.content.essence.EssenceRegistry;
import net.voxelindustry.voidheart.common.content.essence.EssenceType;
import net.voxelindustry.voidheart.common.content.essence.IEssenceReceiver;
import net.voxelindustry.voidheart.common.energy.VoidEnergy;
import net.voxelindustry.voidheart.common.energy.machine.MachinePower;
import net.voxelindustry.voidheart.common.energy.machine.PowerMeter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * A pearl growing under a chain. Relays route Fiery into it like any receiver, and it draws void energy from the field around it; the lesser of the
 * two sets how fast it grows. Pearls growing in reach of each other form a choir and reinforce each other, up to six. A mature pearl stops drawing
 * and leaves its choir, but keeps shuddering.
 * <p>
 * Clients get its growth, whether it is being fed, why not, and the positions of its choir, which its look and its tremor are worked out from.
 */
public class HangingPearlTile extends TileBase implements IEssenceReceiver
{
    /**
     * Ticks between looking for the pearls in reach.
     */
    private static final int SCAN_PERIOD = 20;
    /**
     * Ticks a pearl still counts as fed after its last feeding, so a choir doesn't break up on a short gap.
     */
    private static final int FED_GRACE   = 40;
    /**
     * Growth is sent to clients each time it passes another step of this size, so a lone pearl sends it about every two and a half minutes.
     */
    private static final int SHOWN_STEPS = 100;

    /**
     * The client's sounds, installed by the client registrar so the server never loads them.
     */
    public static Consumer<HangingPearlTile> clientTick = pearl ->
    {
    };

    private final PearlGrowth growth = new PearlGrowth();
    private int    fiery;
    private double credit;

    // Server only
    private final PowerMeter meter = new PowerMeter();
    private int ticksSinceFed = FED_GRACE;

    // Synced to clients
    private List<BlockPos> choir = List.of();
    /** The lowest position among every pearl linked to this one through their neighbours, see {@link PearlChoir}. */
    private long           choirRoot;
    private boolean        fed;
    private boolean        fieryShort;
    private MachinePower   power = MachinePower.IDLE;
    private int            shownStep;

    // Client only
    private float sync;
    private float previousSync;

    public HangingPearlTile(BlockPos pos, BlockState state)
    {
        super(PearlContent.HANGING_PEARL_TILE, pos, state);
        choirRoot = pos.asLong();
    }

    public static void tick(Level level, BlockPos pos, BlockState state, HangingPearlTile pearl)
    {
        if (level instanceof ServerLevel serverLevel)
            pearl.serverTick(serverLevel);
        else
            pearl.animate();
    }

    private void serverTick(ServerLevel level)
    {
        if (growth.isMature())
        {
            if (fed || !choir.isEmpty() || power != MachinePower.IDLE || fieryShort)
            {
                fed = false;
                fieryShort = false;
                choir = List.of();
                choirRoot = worldPosition.asLong();
                meter.record(0, 0);
                power = MachinePower.IDLE;
                setChanged();
                sync();
            }
            return;
        }

        var changed = false;
        if ((level.getGameTime() + worldPosition.asLong()) % SCAN_PERIOD == 0)
        {
            var found = scanChoir(level);
            var root = found.isEmpty() ? worldPosition.asLong() : PearlChoir.root(worldPosition.asLong(), at -> choirOf(level, at, found));
            if (!found.equals(choir) || root != choirRoot)
            {
                choir = found;
                choirRoot = root;
                changed = true;
            }
        }

        var fieryShare = PearlGrowth.fieryShare(credit + fiery);
        var wanted = PearlGrowth.voidWanted(fieryShare);
        var got = wanted > 0 ? VoidEnergy.draw(level, worldPosition, wanted) : 0;
        var feed = PearlGrowth.feed(credit, fiery, got);
        if (feed.fiery() > 0 || feed.credit() != credit)
        {
            fiery -= (int) feed.fiery();
            credit = feed.credit();
            setChanged();
        }

        var matured = growth.tick(PearlGrowth.boost(1 + choir.size()), feed.supply());
        ticksSinceFed = feed.supply() > 0 ? 0 : Math.min(FED_GRACE, ticksSinceFed + 1);

        var meterChanged = meter.record(wanted, got);
        var nowFed = ticksSinceFed < FED_GRACE;
        var nowShort = fieryShare < 1;
        if (meterChanged || meter.state() != power || nowFed != fed || nowShort != fieryShort)
        {
            power = meter.state();
            fed = nowFed;
            fieryShort = nowShort;
            changed = true;
        }

        var step = (int) (growth.share() * SHOWN_STEPS);
        if (step != shownStep)
        {
            shownStep = step;
            changed = true;
        }
        if (feed.supply() > 0)
            setChanged();

        updateStage();
        if (matured)
            level.playSound(null, worldPosition, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.BLOCKS, 0.8F, 0.5F);

        if (changed || matured)
            sync();
    }

    /**
     * The other pearls being fed in reach, nearest chunks only, in a stable order so an unchanged choir is never sent again.
     */
    private List<BlockPos> scanChoir(ServerLevel level)
    {
        var reach = PearlBalance.REACH;
        var reachSqr = reach * reach;
        var span = (int) Math.ceil(reach);
        var found = new ArrayList<BlockPos>();

        for (var chunkX = SectionPos.blockToSectionCoord(worldPosition.getX() - span); chunkX <= SectionPos.blockToSectionCoord(worldPosition.getX() + span); chunkX++)
        {
            for (var chunkZ = SectionPos.blockToSectionCoord(worldPosition.getZ() - span); chunkZ <= SectionPos.blockToSectionCoord(worldPosition.getZ() + span); chunkZ++)
            {
                if (!level.hasChunk(chunkX, chunkZ))
                    continue;
                for (var entity : level.getChunk(chunkX, chunkZ).getBlockEntities().values())
                {
                    if (entity == this || !(entity instanceof HangingPearlTile other) || !other.isFed() || other.growth.isMature())
                        continue;
                    if (other.worldPosition.distSqr(worldPosition) <= reachSqr)
                        found.add(other.worldPosition.immutable());
                }
            }
        }
        found.sort(Comparator.comparingLong(BlockPos::asLong));
        return List.copyOf(found);
    }

    /**
     * The pearls a pearl counts in reach, as packed positions: this one's just found, or another loaded one's from its last scan.
     */
    private List<Long> choirOf(ServerLevel level, long packed, List<BlockPos> ownFound)
    {
        if (packed == worldPosition.asLong())
            return ownFound.stream().map(BlockPos::asLong).toList();
        var pos = BlockPos.of(packed);
        if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof HangingPearlTile other))
            return List.of();
        return other.choir.stream().map(BlockPos::asLong).toList();
    }

    private void animate()
    {
        previousSync = sync;
        if (!choir.isEmpty())
            sync = Math.min(1, sync + 1F / PearlTremor.SYNC_TICKS);
        else
            sync = Math.max(0, sync - 4F / PearlTremor.SYNC_TICKS);
        clientTick.accept(this);
    }

    //
    // Essence
    //

    @Override
    public int spaceFor(EssenceType type)
    {
        return type == EssenceRegistry.FIERY && !growth.isMature() ? PearlBalance.FIERY_TANK - fiery : 0;
    }

    @Override
    public int receive(EssenceType type, int amount)
    {
        var taken = Math.min(Math.max(0, amount), spaceFor(type));
        if (taken > 0)
        {
            fiery += taken;
            setChanged();
        }
        return taken;
    }

    /**
     * None of it can be drawn back out.
     */
    @Override
    public int amountOf(EssenceType type)
    {
        return 0;
    }

    @Override
    public int extract(EssenceType type, int amount)
    {
        return 0;
    }

    //
    // Drops and review
    //

    /**
     * What it leaves when taken down: the pearl once mature, otherwise its seed carrying the growth it had.
     */
    public ItemStack dropped()
    {
        if (growth.isMature())
            return new ItemStack(PearlContent.COLLAPSING_RUBRUM_PEARL);
        var seed = new ItemStack(PearlContent.KINDLED_FRACTAL_SEED);
        if (growth.progress() > 0)
            seed.set(PearlContent.PEARL_GROWTH, growth.progress());
        return seed;
    }

    public void restoreGrowth(double progress)
    {
        growth.setProgress(progress);
        shownStep = (int) (growth.share() * SHOWN_STEPS);
        updateStage();
        setChanged();
        sync();
    }

    /**
     * Sets how far it has grown, for review.
     */
    public void setShare(double share)
    {
        restoreGrowth(share * growth.duration());
    }

    /**
     * Fills its Fiery, for review.
     */
    public void fillFiery()
    {
        fiery = PearlBalance.FIERY_TANK;
        setChanged();
    }

    private void updateStage()
    {
        if (level == null || level.isClientSide())
            return;
        var stage = growth.stage().ordinal();
        if (getBlockState().getValue(HangingPearlBlock.STAGE) != stage)
            level.setBlock(worldPosition, getBlockState().setValue(HangingPearlBlock.STAGE, stage), Block.UPDATE_ALL);
    }

    //
    // Getters
    //

    public PearlGrowth growth()
    {
        return growth;
    }

    public List<BlockPos> choir()
    {
        return choir;
    }

    public boolean isFed()
    {
        return fed;
    }

    public boolean isFieryShort()
    {
        return fieryShort;
    }

    public MachinePower power()
    {
        return power;
    }

    public int fiery()
    {
        return fiery;
    }

    public float sync(float partialTicks)
    {
        return previousSync + (sync - previousSync) * partialTicks;
    }

    /**
     * The choir's shared seed, from its root, so every pearl linked into one choir works out the same, even those out of each other's reach.
     */
    public long choirSeed()
    {
        return PearlTremor.seedOf(choir.isEmpty() ? worldPosition.asLong() : choirRoot);
    }

    public long seed()
    {
        return PearlTremor.seedOf(worldPosition.asLong());
    }

    //
    // Saving
    //

    @Override
    protected void loadAdditional(ValueInput nbt)
    {
        super.loadAdditional(nbt);

        growth.setProgress(nbt.getDoubleOr("growth", 0));
        fiery = Math.clamp(nbt.getIntOr("fiery", 0), 0, PearlBalance.FIERY_TANK);
        credit = Math.clamp(nbt.getDoubleOr("credit", 0), 0, 1);
        choir = nbt.read("choir", BlockPos.CODEC.listOf()).map(List::copyOf).orElse(List.of());
        choirRoot = nbt.getLongOr("choirRoot", worldPosition.asLong());
        fed = nbt.getBooleanOr("fed", false);
        fieryShort = nbt.getBooleanOr("fieryShort", false);
        power = MachinePower.byOrdinal(nbt.getIntOr("power", 0));
        meter.restore(power);
        shownStep = (int) (growth.share() * SHOWN_STEPS);
    }

    @Override
    protected void saveAdditional(ValueOutput nbt)
    {
        super.saveAdditional(nbt);

        nbt.putDouble("growth", growth.progress());
        nbt.putInt("fiery", fiery);
        nbt.putDouble("credit", credit);
        nbt.store("choir", BlockPos.CODEC.listOf(), choir);
        nbt.putLong("choirRoot", choirRoot);
        nbt.putBoolean("fed", fed);
        nbt.putBoolean("fieryShort", fieryShort);
        nbt.putInt("power", power.ordinal());
    }
}
