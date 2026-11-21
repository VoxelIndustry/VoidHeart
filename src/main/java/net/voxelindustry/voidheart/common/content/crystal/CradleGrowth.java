package net.voxelindustry.voidheart.common.content.crystal;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * What a cradle holds and how far it has grown. Pure.
 * <p>
 * Shards fed in are its worth. It grows up to the largest bundle that worth makes, and growth never makes more crystal than went in. Each bit of growth is
 * credited to the feed that paid for it, and the feed that paid for two thirds of it sets the habit. Otherwise the shards' own habit stays, or the bundle
 * grows plain. A cradle takes one crystal and one habit at a time, so what comes back out is never a guess.
 */
public final class CradleGrowth
{
    public static final double SETTLING_SHARE = 2 / 3D;

    public static final int MAX_WORTH = BundleSize.LARGE.worth();

    private static final double EPSILON = 1E-6;

    public record Saved(@Nullable CrystalKind kind, int worth, CrystalHabit seed, double grown, Map<CrystalHabit, Double> fed)
    {
        public static final Codec<Saved> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                CrystalKind.CODEC.optionalFieldOf("kind").forGetter(saved -> java.util.Optional.ofNullable(saved.kind())),
                Codec.INT.optionalFieldOf("worth", 0).forGetter(Saved::worth),
                CrystalHabit.CODEC.optionalFieldOf("seed", CrystalHabit.PLAIN).forGetter(Saved::seed),
                Codec.DOUBLE.optionalFieldOf("grown", 0D).forGetter(Saved::grown),
                Codec.unboundedMap(CrystalHabit.CODEC, Codec.DOUBLE).optionalFieldOf("fed", Map.of()).forGetter(Saved::fed)
        ).apply(instance, (kind, worth, seed, grown, fed) -> new Saved(kind.orElse(null), worth, seed, grown, fed)));
    }

    /**
     * What comes out of an emptied cradle: the bundle grown so far, if any, and the shards not yet grown, which keep the habit they went in with.
     */
    public record Harvest(@Nullable CrystalKind kind, @Nullable BundleSize bundle, CrystalHabit bundleHabit, int shards, CrystalHabit shardHabit)
    {
        public static final Harvest EMPTY = new Harvest(null, null, CrystalHabit.PLAIN, 0, CrystalHabit.PLAIN);

        public boolean isEmpty()
        {
            return bundle == null && shards == 0;
        }
    }

    public enum Refusal
    {
        NONE,
        OTHER_CRYSTAL,
        OTHER_HABIT,
        FULL
    }

    private @Nullable CrystalKind kind;
    private           int         worth;
    private           CrystalHabit seed  = CrystalHabit.PLAIN;
    private           double      grown;

    private final EnumMap<CrystalHabit, Double> fed = new EnumMap<>(CrystalHabit.class);

    /**
     * Why these shards can't go in, or {@link Refusal#NONE} if they can. Worth past a large bundle is refused whole.
     */
    public Refusal refusal(CrystalKind kind, int worth, CrystalHabit habit)
    {
        if (this.worth > 0 && this.kind != kind)
            return Refusal.OTHER_CRYSTAL;
        if (this.worth > 0 && seed != asSeed(habit))
            return Refusal.OTHER_HABIT;
        if (this.worth + worth > MAX_WORTH)
            return Refusal.FULL;
        return Refusal.NONE;
    }

    /**
     * How many more shards fit.
     */
    public int room()
    {
        return MAX_WORTH - worth;
    }

    /**
     * Adds shards, or a bundle by its worth. Growth already made is kept, and the new worth has to grow like the rest.
     *
     * @return whether they went in
     */
    public boolean seed(CrystalKind kind, int worth, CrystalHabit habit)
    {
        if (worth <= 0 || refusal(kind, worth, habit) != Refusal.NONE)
            return false;
        if (this.worth == 0)
        {
            this.kind = kind;
            seed = asSeed(habit);
        }
        this.worth += worth;
        return true;
    }

    private static CrystalHabit asSeed(CrystalHabit habit)
    {
        return habit.isHabit() ? habit : CrystalHabit.PLAIN;
    }

    /**
     * The bundle its worth makes, which it grows toward, or null while it holds nothing.
     */
    public @Nullable BundleSize target()
    {
        return BundleSize.largestWithin(worth);
    }

    public boolean isGrowing()
    {
        var target = target();
        return target != null && grown < target.worth() - EPSILON;
    }

    /**
     * Grows by amount, counted in shards' worth, paid for by that feed: {@link CrystalHabit#PLAIN} or the habit the feed gives.
     */
    public void grow(CrystalHabit feed, double amount)
    {
        var target = target();
        if (target == null || amount <= 0 || !Double.isFinite(amount))
            return;
        double step = Math.min(amount, target.worth() - grown);
        if (step <= 0)
            return;
        grown += step;
        fed.merge(asSeed(feed), step, Double::sum);
    }

    /**
     * The largest bundle fully grown so far, or null.
     */
    public @Nullable BundleSize grownSize()
    {
        return BundleSize.largestWithin((int) Math.floor(grown + EPSILON));
    }

    /**
     * The habit the bundle would have if it came out now. A habit settles only where the crystal takes it and settles allows it, which is the owner's research.
     */
    public CrystalHabit habit(Predicate<CrystalHabit> settles)
    {
        if (kind == null)
            return CrystalHabit.PLAIN;
        double total = fed.values().stream().mapToDouble(Double::doubleValue).sum();
        // Foulness only leaves a crystal by being scoured into a line, so no other feed and no lack of research washes it out
        if (seed == CrystalHabit.FOULED)
        {
            double scoured = fed.getOrDefault(CrystalHabit.SCOURED, 0D);
            boolean wasScoured = total > EPSILON && scoured >= total * SETTLING_SHARE - EPSILON;
            return wasScoured && CrystalHabit.SCOURED.takenBy(kind) && settles.test(CrystalHabit.SCOURED) ? CrystalHabit.SCOURED : CrystalHabit.FOULED;
        }
        if (total > EPSILON)
        {
            for (var entry : fed.entrySet())
            {
                var feed = entry.getKey();
                if (feed.isHabit() && entry.getValue() >= total * SETTLING_SHARE - EPSILON && feed.takenBy(kind) && settles.test(feed))
                    return feed;
            }
        }
        if (seed.isHabit() && seed.takenBy(kind) && settles.test(seed))
            return seed;
        return CrystalHabit.PLAIN;
    }

    /**
     * Empties the cradle. Growth toward the next size is lost, but never the shards it would have used.
     */
    public Harvest harvest(Predicate<CrystalHabit> settles)
    {
        if (kind == null || worth == 0)
        {
            clear();
            return Harvest.EMPTY;
        }
        var bundle = grownSize();
        int left = worth - (bundle == null ? 0 : bundle.worth());
        var harvest = new Harvest(kind, bundle, habit(settles), left, seed);
        clear();
        return harvest;
    }

    private void clear()
    {
        kind = null;
        worth = 0;
        seed = CrystalHabit.PLAIN;
        grown = 0;
        fed.clear();
    }

    public @Nullable CrystalKind kind()
    {
        return kind;
    }

    public int worth()
    {
        return worth;
    }

    public double grown()
    {
        return grown;
    }

    public CrystalHabit seedHabit()
    {
        return seed;
    }

    /**
     * Fouled crystal is scoured on ley rather than grown: it pushes its foulness into the line that powers it.
     */
    public boolean scours()
    {
        return seed == CrystalHabit.FOULED && worth > 0;
    }

    public Saved save()
    {
        return new Saved(kind, worth, seed, grown, Map.copyOf(fed));
    }

    /**
     * Loads saved growth, mending what can't be: worth past a large bundle, growth past the worth, unknown or negative feeds.
     */
    public void load(Saved saved)
    {
        clear();
        if (saved.kind() == null || saved.worth() <= 0)
            return;
        kind = saved.kind();
        worth = Math.min(saved.worth(), MAX_WORTH);
        seed = asSeed(saved.seed());
        var target = target();
        grown = target == null || !Double.isFinite(saved.grown()) ? 0 : Math.clamp(saved.grown(), 0, target.worth());
        saved.fed().forEach((feed, amount) ->
        {
            if (feed != CrystalHabit.NONE && Double.isFinite(amount) && amount > 0)
                fed.merge(feed, amount, Double::sum);
        });
    }
}
