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
import net.voxelindustry.voidheart.common.content.essence.EssenceRegistry;
import net.voxelindustry.voidheart.common.content.essence.EssenceType;
import net.voxelindustry.voidheart.common.content.essence.IEssenceReceiver;
import net.voxelindustry.voidheart.common.content.research.Knowledge;
import net.voxelindustry.voidheart.common.content.research.VoidGrafts;
import net.voxelindustry.voidheart.common.energy.LeyPower;
import net.voxelindustry.voidheart.common.energy.VoidEnergy;
import net.voxelindustry.voidheart.common.setup.VoidHeartDataComponents;
import net.voxelindustry.voidheart.common.energy.VoidSeep;
import net.voxelindustry.voidheart.common.energy.ley.LeySpurs;
import net.voxelindustry.voidheart.common.energy.superfluid.SuperfluidBalance;
import net.voxelindustry.voidheart.common.energy.superfluid.SuperfluidContent;
import net.voxelindustry.voidheart.common.energy.superfluid.SuperfluidPearls;
import net.voxelindustry.voidheart.common.energy.superfluid.SuperfluidPour;
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
 * only while something is growing, and pauses when none comes. Essence a relay feeds it teaches the crystal its own habit instead, and is only taken
 * while that habit can settle.
 * <p>
 * Azurium in a cradle near a mature pearl hung on its chain is pulled out of it instead, once its owner knows how, and winds about the pearl as
 * superfluid. A pouring cradle grows nothing, and gives back only the shards not yet started once emptied.
 */
public class CrystalCradleTile extends TileBase implements IEssenceReceiver
{
    private static final float  EASE = 0.15F;
    private static final double JUMP = 0.5;

    private final CradleGrowth  growth  = new CradleGrowth();
    private final CradleEssence essence = new CradleEssence(CradleBalance.ESSENCE_TANK);

    // What a finished Deep memoriam recorded, handed to the bundle when it is taken out
    private CrystalRecordings.@Nullable Ref recording;

    private @Nullable UUID owner;

    /**
     * What drives the cradle now, for its look and Jade.
     */
    public enum Source implements StringRepresentable
    {
        IDLE, LEY, FED, SEEP, SCOURING, BREACHED, VOID, POURING;

        public static final Codec<Source> CODEC = StringRepresentable.fromEnum(Source::values);

        @Override
        public String getSerializedName()
        {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private float        supply;
    private float        leyShare;
    private float        voidShare;
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

    /** The pearl this cradle pours into, and the units of its next shard already poured. Both synced, for the stream and the shrinking crystal. */
    private @Nullable BlockPos pourTarget;
    private           int      pourCredit;

    // Client only: how far the stream has formed, and where it went while it fades
    private           float    pourShown;
    private           float    previousPourShown;
    private @Nullable BlockPos shownPourTarget;

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
        var wasFeed = cradle.feed;
        var wasStone = cradle.scourStone;
        long now = level.getGameTime();
        if (Math.floorMod(now + pos.hashCode(), CradleBalance.SEEP_TICKS) == 0)
            cradle.seep = VoidSeep.strengthAt(server, pos);

        boolean wasGrowing = cradle.growth.isGrowing();
        var wasTarget = cradle.pourTarget;
        if (cradle.pour(server, pos, now))
        {
            cradle.supply = 0;
            cradle.source = Source.POURING;
            cradle.scourStone = null;
            cradle.flushTaint(server);
        }
        else if (wasGrowing)
        {
            cradle.feed(server, pos, now);
            if (!cradle.growth.isGrowing())
                cradle.onFinished(server);
        }
        else
        {
            cradle.supply = 0;
            cradle.source = Source.IDLE;
            cradle.scourStone = null;
            cradle.flushTaint(server);
        }

        boolean stepped = cradle.growth.grownSize() != cradle.syncedSize;
        boolean changed = wasSource != cradle.source || wasFeed != cradle.feed || !java.util.Objects.equals(wasStone, cradle.scourStone)
                || !java.util.Objects.equals(wasTarget, cradle.pourTarget);
        boolean active = cradle.supply > 0 || cradle.source == Source.POURING;
        if (stepped || changed || (active && now - cradle.lastSync >= CradleBalance.SYNC_TICKS))
            cradle.publish();
    }

    /**
     * Pours a tick of azurium into the pearl it found, and says whether any went. The pearl is looked for every {@link SuperfluidBalance#SCAN_PERIOD}
     * ticks and dropped as soon as it has no room left, so a cradle near several moves on to the next once the first is wound full.
     */
    private boolean pour(ServerLevel server, BlockPos pos, long now)
    {
        if (Math.floorMod(now + pos.hashCode(), SuperfluidBalance.SCAN_PERIOD) == 0)
            pourTarget = pours(server) ? SuperfluidPearls.nearestWithRoom(server, pos) : null;
        if (pourTarget == null)
            return false;
        return pourUnits(server, SuperfluidBalance.POUR_PER_TICK) > 0;
    }

    /**
     * Pours that many units into the pearl it pours into, at once, and says how many went.
     */
    private int pourUnits(ServerLevel server, int units)
    {
        var pearl = pourTarget == null ? null : SuperfluidPearls.at(server, pourTarget);
        var step = pearl == null ? null : SuperfluidPour.pour(pourCredit, growth.worth(), pearl.superfluidRoom(), units);
        if (step == null || step.poured() <= 0)
        {
            pourTarget = null;
            return 0;
        }
        pearl.receiveSuperfluid(step.poured());
        pourCredit = step.credit();
        setChanged();
        if (step.drained() > 0)
        {
            growth.drain(step.drained());
            publish();
        }
        return step.poured();
    }

    /**
     * Pours that many shards' worth into the nearest pearl with room at once, for review, and says how many units went.
     */
    public int pourNow(int shards)
    {
        if (!(level instanceof ServerLevel server) || !pours(server))
            return 0;
        pourTarget = SuperfluidPearls.nearestWithRoom(server, worldPosition);
        var poured = pourUnits(server, shards * SuperfluidBalance.SHARD_UNITS);
        publish();
        return poured;
    }

    /**
     * Whether what it holds can pour at all: azurium of any habit but Fouled, since foulness only ever leaves a crystal by being scoured, in a cradle
     * whose owner knows superfluid azurium.
     */
    private boolean pours(ServerLevel server)
    {
        if (growth.kind() != CrystalKind.AZURIUM || growth.worth() <= 0 || owner == null || growth.seedHabit() == CrystalHabit.FOULED)
            return false;
        var settles = CradleResearch.settles(server.getServer(), owner);
        return growth.habit(settles) != CrystalHabit.FOULED && Knowledge.hasCompleted(server.getServer(), owner, SuperfluidContent.RESEARCH);
    }

    /**
     * One tick of growth. Seep feeds a crystal Fouled before any ley does. Fouled crystal on ley is scoured into the line instead, where its owner knows how
     * and a real line feeds the stone, and stalls while that line is breached; anywhere else it just regrows Fouled.
     */
    private void feed(ServerLevel server, BlockPos pos, long now)
    {
        leyShare = 0;
        voidShare = 0;
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
                leyShare = supply;
                voidShare = drawVoid(server, pos);
                supply = Math.min(1, leyShare + voidShare);
                source = supply <= 0 ? Source.IDLE : voidShare > leyShare ? Source.VOID : Source.LEY;
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
        leyShare = LeyPower.supply(server, pos, CradleBalance.LEY_NEED);
        voidShare = drawVoid(server, pos);
        supply = Math.min(1, leyShare + voidShare);
        source = supply <= 0 ? Source.IDLE : voidShare > leyShare ? Source.VOID : Source.LEY;
        // Essence teaches the crystal over whatever powers it, but only grows it as far as that power does
        var fed = supply > 0 ? essence.pay(CradleBalance.ESSENCE_PER_TICK * supply, habit -> takesEssence(server, habit)) : null;
        if (fed != null)
        {
            source = Source.FED;
            feed = fed;
            setChanged();
            growth.grow(feed, supply / (double) CradleBalance.TICKS_PER_WORTH);
            return;
        }
        var leyFeed = feedAt(server, pos);
        var voidFeed = voidFeedAt(server);
        feed = voidShare > leyShare ? voidFeed : leyFeed;
        growth.grow(leyFeed, leyShare / (double) CradleBalance.TICKS_PER_WORTH);
        growth.grow(voidFeed, Math.min(voidShare, 1 - leyShare) / (double) CradleBalance.TICKS_PER_WORTH);
    }

    /**
     * Void energy for what the ley leaves short, once the owner knows Deep Growth, as a share of a full supply.
     */
    private float drawVoid(ServerLevel server, BlockPos pos)
    {
        float short_ = 1 - leyShare;
        if (short_ <= 0 || !CradleResearch.settles(server.getServer(), owner).test(CrystalHabit.DEEP))
            return 0;
        long want = Math.round(CradleBalance.VOID_NEED * short_);
        long got = want > 0 ? VoidEnergy.draw(server, pos, want) : 0;
        return Math.clamp(got / (float) CradleBalance.VOID_NEED, 0, short_);
    }

    /**
     * What void energy teaches a crystal here: Deep in the void's own places, the pockets, the dungeons and the Daedalus, and nothing elsewhere.
     */
    static CrystalHabit voidFeedAt(ServerLevel level)
    {
        return VoidGrafts.isVoid(level.dimension()) ? CrystalHabit.DEEP : CrystalHabit.PLAIN;
    }

    /**
     * Once fully grown, a Deep memoriam records the place it grew in, which an Oculus Table can then show.
     */
    private void onFinished(ServerLevel server)
    {
        if (growth.kind() != CrystalKind.MEMORIAM || growth.habit(CradleResearch.settles(server.getServer(), owner)) != CrystalHabit.DEEP)
            return;
        recording = CrystalRecordings.record(server, worldPosition);
        setChanged();
    }

    /**
     * Whether essence teaching this habit is any use here: something is growing that takes it, its owner knows it, and no seep or foulness decides the
     * habit first. Relays send nothing it would only waste.
     */
    private boolean takesEssence(ServerLevel server, CrystalHabit habit)
    {
        var kind = growth.kind();
        return kind != null && growth.isGrowing() && growth.seedHabit() != CrystalHabit.FOULED && seep < CradleBalance.FOUL_SEEP
               && habit.takenBy(kind) && CradleResearch.settles(server.getServer(), owner).test(habit);
    }

    /**
     * The habit an essence teaches, or null for one that teaches none.
     */
    public static @Nullable CrystalHabit habitOf(EssenceType type)
    {
        if (type == EssenceRegistry.FIERY)
            return CrystalHabit.SEARING;
        if (type == EssenceRegistry.STILLNESS)
            return CrystalHabit.STILL;
        if (type == EssenceRegistry.SUFFERING)
            return CrystalHabit.WRUNG;
        return null;
    }

    /**
     * The essence that teaches a habit, or null.
     */
    public static @Nullable EssenceType essenceOf(CrystalHabit habit)
    {
        return switch (habit)
        {
            case SEARING -> EssenceRegistry.FIERY;
            case STILL -> EssenceRegistry.STILLNESS;
            case WRUNG -> EssenceRegistry.SUFFERING;
            default -> null;
        };
    }

    @Override
    public int spaceFor(EssenceType type)
    {
        var habit = habitOf(type);
        if (habit == null || !(level instanceof ServerLevel server) || !takesEssence(server, habit))
            return 0;
        return essence.room(habit);
    }

    @Override
    public int receive(EssenceType type, int amount)
    {
        if (spaceFor(type) <= 0)
            return 0;
        int taken = essence.fill(habitOf(type), amount);
        if (taken > 0)
            setChanged();
        return taken;
    }

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
        cradle.previousPourShown = cradle.pourShown;
        if (cradle.pourTarget != null)
        {
            if (!cradle.pourTarget.equals(cradle.shownPourTarget))
            {
                cradle.shownPourTarget = cradle.pourTarget;
                cradle.pourShown = 0;
                cradle.previousPourShown = 0;
            }
            cradle.pourShown = Math.min(1, cradle.pourShown + 1F / POUR_FORM_TICKS);
        }
        else
            cradle.pourShown = Math.max(0, cradle.pourShown - 1F / POUR_FORM_TICKS);
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
            var target = growth.target();
            growth.seed(kind, worth, habit);
            // A larger bundle to grow: what it records is the place as it is when it finishes again
            if (growth.target() != target)
                dropRecording();
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
        // The shard already pouring is partly in the pearl, so it never comes back
        if (pourCredit > 0)
            growth.drain(growth.worth() - SuperfluidPour.kept(growth.worth(), pourCredit));
        pourCredit = 0;
        pourTarget = null;
        var harvest = growth.harvest(CradleResearch.settles(server.getServer(), owner));
        var recorded = recording;
        recording = null;
        publish();

        var items = new ArrayList<ItemStack>();
        if (harvest.kind() == null)
            return items;
        if (harvest.bundle() != null)
        {
            var bundle = CrystalBundles.bundleItem(harvest.kind(), harvest.bundle(), harvest.bundleHabit());
            if (recorded != null && harvest.bundleHabit() == CrystalHabit.DEEP)
                bundle.set(VoidHeartDataComponents.CRYSTAL_RECORDING, recorded);
            items.add(bundle);
        }
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
        boolean wasGrowing = growth.isGrowing();
        growth.grow(paidBy, worth);
        if (wasGrowing && !growth.isGrowing() && level instanceof ServerLevel server)
            onFinished(server);
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
        output.store("essence", CradleEssence.Saved.CODEC, essence.save());
        if (recording != null)
            output.store("recording", CrystalRecordings.Ref.CODEC, recording);
        if (pourTarget != null)
            output.store("pourTarget", BlockPos.CODEC, pourTarget);
        output.putInt("pourCredit", pourCredit);
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
        essence.load(input.read("essence", CradleEssence.Saved.CODEC).orElse(new CradleEssence.Saved(java.util.List.of(), java.util.List.of())));
        recording = input.read("recording", CrystalRecordings.Ref.CODEC).orElse(null);
        pourTarget = input.read("pourTarget", BlockPos.CODEC).orElse(null);
        pourCredit = growth.worth() > 0 ? Math.clamp(input.getIntOr("pourCredit", 0), 0, SuperfluidBalance.SHARD_UNITS - 1) : 0;
    }

    public Source source()
    {
        return source;
    }

    /** Ticks the stream takes to reach the pearl, and to fade once the pour stops. */
    public static final int POUR_FORM_TICKS = 20;

    public @Nullable BlockPos pourTarget()
    {
        return pourTarget;
    }

    public int pourCredit()
    {
        return pourCredit;
    }

    /**
     * Shards' worth still in the cradle, counting what is left of the one pouring.
     */
    public double held()
    {
        return SuperfluidPour.held(growth.worth(), pourCredit);
    }

    /**
     * How far the stream to its pearl has formed, from 0 to 1, and the pearl it goes to, kept while it fades.
     */
    public float pourShown(float partialTicks)
    {
        return previousPourShown + (pourShown - previousPourShown) * partialTicks;
    }

    public @Nullable BlockPos shownPourTarget()
    {
        return shownPourTarget;
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

    public CradleEssence essence()
    {
        return essence;
    }

    public CrystalRecordings.@Nullable Ref recording()
    {
        return recording;
    }

    private void dropRecording()
    {
        if (recording != null && level instanceof ServerLevel server)
            CrystalRecordings.forget(server.getServer(), recording);
        recording = null;
    }

    public float voidShare()
    {
        return voidShare;
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
