package net.voxelindustry.voidheart.common.content.crystal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.voxelindustry.steamlayer.tile.TileBase;
import net.voxelindustry.voidheart.common.energy.LeyPower;
import net.voxelindustry.voidheart.common.energy.ley.LeySpurs;
import net.voxelindustry.voidheart.common.ley.flow.LeyFlowBalance;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Regrows the crystal fed into it on ley from a nearby standing stone. A clean line grows azurium Clear, a tainted one grows it plain. It asks for ley
 * only while something is growing, and pauses when none comes.
 */
public class CrystalCradleTile extends TileBase
{
    private static final float  EASE = 0.15F;
    private static final double JUMP = 0.5;

    private final CradleGrowth growth = new CradleGrowth();

    private @Nullable UUID owner;

    private float        supply;
    private CrystalHabit feed    = CrystalHabit.PLAIN;
    private CrystalHabit preview = CrystalHabit.PLAIN;

    private @Nullable BundleSize syncedSize;
    private           long       lastSync;

    private float  pool;
    private float  previousPool;
    private double shown;
    private double previousShown;

    public CrystalCradleTile(BlockPos pos, BlockState state)
    {
        super(CrystalContent.CRYSTAL_CRADLE_TILE, pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CrystalCradleTile cradle)
    {
        var server = (ServerLevel) level;
        boolean wasFed = cradle.supply > 0;
        if (cradle.growth.isGrowing())
        {
            cradle.supply = LeyPower.supply(server, pos, CradleBalance.LEY_NEED);
            cradle.feed = feedAt(server, pos);
            cradle.growth.grow(cradle.feed, cradle.supply / (double) CradleBalance.TICKS_PER_WORTH);
        }
        else
            cradle.supply = 0;

        boolean stepped = cradle.growth.grownSize() != cradle.syncedSize;
        if (stepped || wasFed != cradle.supply > 0 || (cradle.supply > 0 && level.getGameTime() - cradle.lastSync >= CradleBalance.SYNC_TICKS))
            cradle.publish();
    }

    /**
     * What the ley here would teach a crystal: Clear from a line still clean, nothing from one already tainted.
     */
    static CrystalHabit feedAt(ServerLevel level, BlockPos pos)
    {
        return LeySpurs.get(level).taintAt(pos) < LeyFlowBalance.TAINTED_FROM ? CrystalHabit.CLEAR : CrystalHabit.PLAIN;
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, CrystalCradleTile cradle)
    {
        cradle.previousPool = cradle.pool;
        cradle.pool += (cradle.supply - cradle.pool) * EASE;
        double target = cradle.growth.grown();
        // A harvest jumps rather than shrinking back through every size
        if (Math.abs(target - cradle.shown) > JUMP)
        {
            cradle.shown = target;
            cradle.previousShown = target;
            return;
        }
        cradle.previousShown = cradle.shown;
        cradle.shown += (target - cradle.shown) * EASE;
    }

    void publish()
    {
        if (!(level instanceof ServerLevel server))
            return;
        preview = growth.habit(CradleResearch.settles(server.getServer(), owner));
        syncedSize = growth.grownSize();
        lastSync = level.getGameTime();
        setChanged();
        sync();
    }

    public void claim(UUID player)
    {
        if (owner == null)
        {
            owner = player;
            setChanged();
        }
    }

    public CradleGrowth.Refusal seed(CrystalKind kind, int worth, CrystalHabit habit)
    {
        var refusal = growth.refusal(kind, worth, habit);
        if (refusal == CradleGrowth.Refusal.NONE)
        {
            growth.seed(kind, worth, habit);
            publish();
        }
        return refusal;
    }

    public int room()
    {
        return growth.room();
    }

    /**
     * Empties the cradle into items: the bundle grown so far with its habit, and the shards not yet grown with theirs.
     */
    public List<ItemStack> harvest()
    {
        if (!(level instanceof ServerLevel server))
            return List.of();
        var harvest = growth.harvest(CradleResearch.settles(server.getServer(), owner));
        publish();

        var items = new ArrayList<ItemStack>();
        if (harvest.kind() == null)
            return items;
        if (harvest.bundle() != null)
            items.add(CrystalBundles.bundleItem(harvest.kind(), harvest.bundle(), harvest.bundleHabit()));
        if (harvest.shards() > 0)
            items.add(CrystalBundles.shards(harvest.kind(), harvest.shards(), harvest.shardHabit()));
        return items;
    }

    /**
     * Finishes the growth at once, as the feed given paid for it. For review.
     */
    public void finish(CrystalHabit paidBy)
    {
        var target = growth.target();
        if (target != null)
            advance(paidBy, target.worth());
    }

    /**
     * Grows by that many shards' worth at once, as the feed given paid for it. For review and tests.
     */
    public void advance(CrystalHabit paidBy, double worth)
    {
        growth.grow(paidBy, worth);
        publish();
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state)
    {
        if (level != null && !level.isClientSide())
        {
            for (var item : harvest())
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, item);
        }
        super.preRemoveSideEffects(pos, state);
    }

    @Override
    protected void saveAdditional(ValueOutput output)
    {
        super.saveAdditional(output);
        output.store("growth", CradleGrowth.Saved.CODEC, growth.save());
        if (owner != null)
            output.store("owner", UUIDUtil.CODEC, owner);
        output.putFloat("supply", supply);
        output.store("feed", CrystalHabit.CODEC, feed);
        output.store("preview", CrystalHabit.CODEC, preview);
    }

    @Override
    protected void loadAdditional(ValueInput input)
    {
        super.loadAdditional(input);
        growth.load(input.read("growth", CradleGrowth.Saved.CODEC).orElse(new CradleGrowth().save()));
        owner = input.read("owner", UUIDUtil.CODEC).orElse(null);
        float saved = input.getFloatOr("supply", 0);
        supply = Float.isFinite(saved) ? Math.clamp(saved, 0, 1) : 0;
        feed = input.read("feed", CrystalHabit.CODEC).orElse(CrystalHabit.PLAIN);
        preview = input.read("preview", CrystalHabit.CODEC).orElse(CrystalHabit.PLAIN);
    }

    public CradleGrowth growth()
    {
        return growth;
    }

    public float supply()
    {
        return supply;
    }

    public CrystalHabit feed()
    {
        return feed;
    }

    /**
     * The habit the bundle would come out with now, as the server last worked it out.
     */
    public CrystalHabit preview()
    {
        return preview;
    }

    public @Nullable UUID owner()
    {
        return owner;
    }

    public float pool(float partialTicks)
    {
        return previousPool + (pool - previousPool) * partialTicks;
    }

    /**
     * How far it has grown, in shards' worth, eased for drawing.
     */
    public double shown(float partialTicks)
    {
        return previousShown + (shown - previousShown) * partialTicks;
    }
}
