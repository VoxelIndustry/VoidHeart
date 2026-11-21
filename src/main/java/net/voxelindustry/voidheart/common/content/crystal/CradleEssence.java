package net.voxelindustry.voidheart.common.content.crystal;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

/**
 * The essence a cradle has been fed through relays, one small tank for each habit an essence teaches: Fiery grows Searing, Stillness Still and Suffering
 * Wrung. Pure.
 * <p>
 * Growth asks a little essence each tick. Tanks hold whole points, so a point taken out pays for several ticks through a credit, as the Hanging Pearl's
 * Fiery does. Each tick is paid by the essence held most, so two fed at once take turns and neither settles.
 */
public final class CradleEssence
{
    public static final List<CrystalHabit> FED = List.of(CrystalHabit.SEARING, CrystalHabit.STILL, CrystalHabit.WRUNG);

    private static final double EPSILON = 1E-9;

    public record Saved(List<Integer> tanks, List<Double> credits)
    {
        public static final Codec<Saved> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.listOf().optionalFieldOf("tanks", List.of()).forGetter(Saved::tanks),
                Codec.DOUBLE.listOf().optionalFieldOf("credits", List.of()).forGetter(Saved::credits)
        ).apply(instance, Saved::new));
    }

    private final int      capacity;
    private final int[]    tanks   = new int[FED.size()];
    private final double[] credits = new double[FED.size()];

    public CradleEssence(int capacity)
    {
        this.capacity = capacity;
    }

    /**
     * Whether some essence teaches this habit.
     */
    public static boolean isFed(CrystalHabit habit)
    {
        return FED.contains(habit);
    }

    public int amount(CrystalHabit habit)
    {
        int index = FED.indexOf(habit);
        return index < 0 ? 0 : tanks[index];
    }

    public int room(CrystalHabit habit)
    {
        int index = FED.indexOf(habit);
        return index < 0 ? 0 : capacity - tanks[index];
    }

    /**
     * @return how much went in
     */
    public int fill(CrystalHabit habit, int amount)
    {
        int index = FED.indexOf(habit);
        if (index < 0 || amount <= 0)
            return 0;
        int taken = Math.min(amount, capacity - tanks[index]);
        tanks[index] += taken;
        return taken;
    }

    /**
     * Pays for one tick of growth needing this much essence, from the accepted essence held most.
     *
     * @return the habit that essence teaches, or null when none could pay the whole tick
     */
    public @Nullable CrystalHabit pay(double need, Predicate<CrystalHabit> accepted)
    {
        if (need <= 0 || !Double.isFinite(need))
            return null;
        int best = -1;
        double most = 0;
        for (int i = 0; i < FED.size(); i++)
        {
            double held = tanks[i] + credits[i];
            if (held + EPSILON >= need && held > most && accepted.test(FED.get(i)))
            {
                best = i;
                most = held;
            }
        }
        if (best < 0)
            return null;
        if (credits[best] + EPSILON < need)
        {
            int taken = Math.min(tanks[best], (int) Math.ceil(need - credits[best] - EPSILON));
            tanks[best] -= taken;
            credits[best] += taken;
        }
        credits[best] = Math.max(0, credits[best] - need);
        return FED.get(best);
    }

    public boolean isEmpty()
    {
        for (int i = 0; i < FED.size(); i++)
        {
            if (tanks[i] > 0 || credits[i] > EPSILON)
                return false;
        }
        return true;
    }

    public Saved save()
    {
        return new Saved(java.util.Arrays.stream(tanks).boxed().toList(), java.util.Arrays.stream(credits).boxed().toList());
    }

    /**
     * Loads saved tanks, mending what can't be: more than a tank holds, negative or unknown amounts.
     */
    public void load(Saved saved)
    {
        for (int i = 0; i < FED.size(); i++)
        {
            int tank = i < saved.tanks().size() ? saved.tanks().get(i) : 0;
            double credit = i < saved.credits().size() ? saved.credits().get(i) : 0;
            tanks[i] = Math.clamp(tank, 0, capacity);
            credits[i] = Double.isFinite(credit) ? Math.clamp(credit, 0, 1) : 0;
        }
    }
}
