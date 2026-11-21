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
import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;
import net.voxelindustry.steamlayer.tile.TileBase;
import net.voxelindustry.voidheart.common.energy.LeyPower;
import net.voxelindustry.voidheart.common.energy.VoidSeep;
import net.voxelindustry.voidheart.common.energy.ley.LeySpurs;
import net.voxelindustry.voidheart.common.ley.Ley;
import net.voxelindustry.voidheart.common.ley.TaintStage;
import net.voxelindustry.voidheart.common.ley.flow.LeyFlowBalance;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

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

    /**
     * What drives the cradle now, for its look and Jade.
     */
    public enum Source implements StringRepresentable
    {
        IDLE, LEY, SEEP, SCOURING, BREACHED;

        public static final Codec<Source> CODEC = StringRepresentable.fromEnum(Source::values);

        @Override
        public String getSerializedName()
        {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private float        supply;
    private CrystalHabit feed    = CrystalHabit.PLAIN;
    private CrystalHabit preview = CrystalHabit.PLAIN;
    private Source       source  = Source.IDLE;
    private float        seep;

    private @Nullable BlockPos scourStone;
    private @Nullable Long     scourNode;
    private           boolean  breached;
    private           double   pendingTaint;
    private           double   taintPushed;

    private @Nullable BundleSize syncedSize;
    private           long       lastSync;

    private float  pool;
    private float  previousPool;
    private double shown;
    private double previousShown;

    /**
     * Cradles loaded on this client, for the ink thread a scouring one sends down its spur.
     */
    public static final Set<CrystalCradleTile> CLIENT_LOADED = Collections.newSetFromMap(new WeakHashMap<>());

    public CrystalCradleTile(BlockPos pos, BlockState state)
    {
        super(CrystalContent.CRYSTAL_CRADLE_TILE, pos, state);
    }

    @Override
    public void setLevel(Level level)
    {
        super.setLevel(level);
        if (level.isClientSide())
            CLIENT_LOADED.add(this);
    }

    @Override
    public void clearRemoved()
    {
        super.clearRemoved();
        if (level != null && level.isClientSide())
            CLIENT_LOADED.add(this);
    }

    @Override
    public void setRemoved()
    {
        super.setRemoved();
        if (level != null && level.isClientSide())
            CLIENT_LOADED.remove(this);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CrystalCradleTile cradle)
    {
        var server = (ServerLevel) level;
        var wasSource = cradle.source;
        var wasStone = cradle.scourStone;
        long now = level.getGameTime();
        if (Math.floorMod(now + pos.hashCode(), CradleBalance.SEEP_TICKS) == 0)
            cradle.seep = VoidSeep.strengthAt(server, pos);

        if (cradle.growth.isGrowing())
            cradle.feed(server, pos, now);
        else
        {
            cradle.supply = 0;
            cradle.source = Source.IDLE;
            cradle.scourStone = null;
            cradle.flushTaint(server);
        }

        boolean stepped = cradle.growth.grownSize() != cradle.syncedSize;
        boolean changed = wasSource != cradle.source || !java.util.Objects.equals(wasStone, cradle.scourStone);
        if (stepped || changed || (cradle.supply > 0 && now - cradle.lastSync >= CradleBalance.SYNC_TICKS))
            cradle.publish();
    }

    /**
     * One tick of growth. Seep feeds a crystal Fouled before any ley does. Fouled crystal on ley is scoured into the line instead, where its owner knows how
     * and a real line feeds the stone, and stalls while that line is breached; anywhere else it just regrows Fouled.
     */
    private void feed(ServerLevel server, BlockPos pos, long now)
    {
        boolean inSeep = seep >= CradleBalance.FOUL_SEEP;
        if (growth.scours() && !inSeep)
        {
            supply = LeyPower.supply(server, pos, CradleBalance.LEY_NEED);
            var spurs = LeySpurs.get(server);
            var node = supply > 0 ? spurs.upstreamNodeFor(pos) : null;
            boolean knows = CradleResearch.settles(server.getServer(), owner).test(CrystalHabit.SCOURED);
            if (node == null || !knows)
            {
                scourStone = null;
                flushTaint(server);
                source = supply > 0 ? Source.LEY : Source.IDLE;
                feed = CrystalHabit.FOULED;
                growth.grow(feed, supply / (double) CradleBalance.TICKS_PER_WORTH);
                return;
            }
            if (scourNode != null && scourNode.longValue() != node)
                flushTaint(server);
            scourNode = node;
            scourStone = spurs.stoneFor(pos);
            if (Math.floorMod(now + pos.hashCode(), CradleBalance.SCOUR_TICKS) == 0)
            {
                flushTaint(server);
                breached = Ley.stage(server, node).isAtLeast(TaintStage.BREACHED);
            }
            feed = CrystalHabit.SCOURED;
            if (breached)
            {
                source = Source.BREACHED;
                return;
            }
            source = Source.SCOURING;
            double amount = supply / (double) CradleBalance.TICKS_PER_WORTH;
            double before = growth.grown();
            growth.grow(feed, amount);
            pendingTaint += (growth.grown() - before) * CradleBalance.TAINT_PER_WORTH;
            return;
        }

        scourStone = null;
        flushTaint(server);
        if (inSeep)
        {
            supply = 1;
            source = Source.SEEP;
            feed = CrystalHabit.FOULED;
            growth.grow(feed, CradleBalance.SEEP_GROWTH / CradleBalance.TICKS_PER_WORTH);
            return;
        }
        supply = LeyPower.supply(server, pos, CradleBalance.LEY_NEED);
        source = supply > 0 ? Source.LEY : Source.IDLE;
        feed = feedAt(server, pos);
        growth.grow(feed, supply / (double) CradleBalance.TICKS_PER_WORTH);
    }

    /**
     * Hands the foulness scoured since the last time to the node it belongs to.
     */
    private void flushTaint(ServerLevel server)
    {
        if (scourNode != null && pendingTaint > 0)
        {
            Ley.addTaint(server, scourNode, (float) pendingTaint);
            taintPushed += pendingTaint;
            setChanged();
        }
        pendingTaint = 0;
        if (scourStone == null)
            scourNode = null;
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
        flushTaint(server);
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
        double before = growth.grown();
        growth.grow(paidBy, worth);
        // Scouring at once still costs the line what scouring slowly would
        if (paidBy == CrystalHabit.SCOURED && scourNode != null && level instanceof ServerLevel server)
        {
            pendingTaint += (growth.grown() - before) * CradleBalance.TAINT_PER_WORTH;
            flushTaint(server);
        }
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
        output.store("source", Source.CODEC, source);
        if (scourStone != null)
            output.store("scourStone", BlockPos.CODEC, scourStone);
        if (scourNode != null)
            output.putLong("scourNode", scourNode);
        output.putDouble("pendingTaint", pendingTaint);
        output.putDouble("taintPushed", taintPushed);
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
        source = input.read("source", Source.CODEC).orElse(Source.IDLE);
        scourStone = input.read("scourStone", BlockPos.CODEC).orElse(null);
        scourNode = input.getLong("scourNode").orElse(null);
        double pending = input.getDoubleOr("pendingTaint", 0);
        pendingTaint = Double.isFinite(pending) ? Math.max(0, pending) : 0;
        double pushed = input.getDoubleOr("taintPushed", 0);
        taintPushed = Double.isFinite(pushed) ? Math.max(0, pushed) : 0;
    }

    public Source source()
    {
        return source;
    }

    /**
     * The stone a scouring cradle sends its foulness down, for the ink thread, or null when it isn't scouring.
     */
    public @Nullable BlockPos scourStone()
    {
        return scourStone;
    }

    public @Nullable Long scourNode()
    {
        return scourNode;
    }

    /**
     * Taint this cradle has scoured into lines so far.
     */
    public double taintPushed()
    {
        return taintPushed;
    }

    public float seep()
    {
        return seep;
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
