package net.voxelindustry.voidheart.common.content.secondheart.ventricle;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static net.voxelindustry.voidheart.common.content.secondheart.ventricle.VentricleBalance.*;

/**
 * The Twin Ventricle's rite, without the world: what has been offered and by whom, the kindling, and the beating that calls the waves and ends on
 * the celebrant's heart stopping. It never fails. Losing power, fuel or a block of the chamber pauses it; the celebrant stepping off the cradle
 * pauses the beat; the celebrant dying or leaving starts the beat over, the offering kept.
 */
public final class VentricleCeremony
{
    public enum Phase
    {
        IDLE,
        OFFERING,
        KINDLING,
        BEATING
    }

    public enum Ingredient
    {
        KERNACIAN_CORE(KERNACIAN_CORES),
        DARK_AMALGAM(DARK_AMALGAMS),
        MEMORIAM(MEMORIAM_SHARDS),
        WRAITH_INK(WRAITH_INKS);

        public final int needed;

        Ingredient(int needed)
        {
            this.needed = needed;
        }
    }

    /**
     * What the world says this tick.
     *
     * @param complete      every block of the chamber is in place
     * @param powered       the pylons gave this tick's energy
     * @param fueled        both basins hold their essence
     * @param celebrantHere the celebrant stands in the cradle, alive
     * @param celebrantLost the celebrant died or left the game
     */
    public record Conditions(boolean complete, boolean powered, boolean fueled, boolean celebrantHere, boolean celebrantLost)
    {
    }

    public enum Event
    {
        KINDLED,
        BEAT,
        WAVE,
        RESTARTED,
        STOPPED
    }

    private Phase                   phase = Phase.IDLE;
    private @Nullable UUID          celebrant;
    private final Map<Ingredient, Integer> offered = new EnumMap<>(Ingredient.class);
    private int                     experience;
    private int                     kindling;
    private int                     beating;
    private int                     waves;
    private boolean                 restartPending;

    public Phase phase()
    {
        return phase;
    }

    public @Nullable UUID celebrant()
    {
        return celebrant;
    }

    public int offered(Ingredient ingredient)
    {
        return offered.getOrDefault(ingredient, 0);
    }

    public int stillNeeded(Ingredient ingredient)
    {
        return ingredient.needed - offered(ingredient);
    }

    public int experience()
    {
        return experience;
    }

    public int experienceNeeded()
    {
        return EXPERIENCE - experience;
    }

    public int kindling()
    {
        return kindling;
    }

    public int beating()
    {
        return beating;
    }

    public int waves()
    {
        return waves;
    }

    public boolean itemsComplete()
    {
        for (var ingredient : Ingredient.values())
            if (stillNeeded(ingredient) > 0)
                return false;
        return true;
    }

    /**
     * @return whether the player may offer here: anyone while it is empty, then only the one who began
     */
    public boolean acceptsFrom(UUID player)
    {
        return phase == Phase.IDLE || phase == Phase.OFFERING && player.equals(celebrant);
    }

    /**
     * @return how many of the stack the cradle takes
     */
    public int offer(UUID player, Ingredient ingredient, int count)
    {
        if (!acceptsFrom(player))
            return 0;
        int taken = Math.min(count, stillNeeded(ingredient));
        if (taken <= 0)
            return 0;
        celebrant = player;
        phase = Phase.OFFERING;
        offered.merge(ingredient, taken, Integer::sum);
        return taken;
    }

    /**
     * Gives the offering back, while it is still only an offering.
     *
     * @return what was offered, or empty if the rite has begun
     */
    public Map<Ingredient, Integer> withdraw(UUID player)
    {
        if (phase != Phase.OFFERING || !player.equals(celebrant))
            return Map.of();
        var back = new EnumMap<>(offered);
        clear();
        return back;
    }

    /**
     * @return experience taken, once every item is in
     */
    public int takeExperience(int available)
    {
        if (phase != Phase.OFFERING || !itemsComplete())
            return 0;
        int taken = Math.min(available, experienceNeeded());
        experience += taken;
        if (experience >= EXPERIENCE)
            phase = Phase.KINDLING;
        return taken;
    }

    /**
     * @return whether this tick would move the rite on, if powered and fueled, so energy and fuel are only drawn then
     */
    public boolean wantsPower(boolean complete, boolean celebrantHere)
    {
        if (!complete)
            return false;
        return phase == Phase.KINDLING || phase == Phase.BEATING && celebrantHere && !restartPending;
    }

    public List<Event> tick(Conditions conditions)
    {
        var events = new ArrayList<Event>(2);
        switch (phase)
        {
            case KINDLING ->
            {
                if (conditions.complete() && conditions.powered() && conditions.fueled() && ++kindling >= KINDLING_TICKS)
                {
                    phase = Phase.BEATING;
                    events.add(Event.KINDLED);
                }
            }
            case BEATING ->
            {
                if (conditions.celebrantLost())
                {
                    // The beat starts over once they are back in the cradle
                    if (beating > 0 || waves > 0)
                        events.add(Event.RESTARTED);
                    beating = 0;
                    waves = 0;
                    restartPending = true;
                    break;
                }
                if (!conditions.celebrantHere())
                    break;
                restartPending = false;
                if (!conditions.complete() || !conditions.powered() || !conditions.fueled())
                    break;
                beating++;
                if (beating % BEAT_TICKS == 0)
                    events.add(Event.BEAT);
                if (waves < VentricleWaves.due(beating / BEAT_TICKS))
                {
                    waves++;
                    events.add(Event.WAVE);
                }
                if (beating >= BEATING_TICKS)
                {
                    events.add(Event.STOPPED);
                    clear();
                }
            }
            default ->
            {
            }
        }
        return events;
    }

    private void clear()
    {
        phase = Phase.IDLE;
        celebrant = null;
        offered.clear();
        experience = 0;
        kindling = 0;
        beating = 0;
        waves = 0;
        restartPending = false;
    }

    /** For commands and tests: jumps the rite ahead, keeping its offering. */
    public void skipTo(Phase phase, int ticksIn)
    {
        if (phase == Phase.IDLE || phase == Phase.OFFERING)
            return;
        for (var ingredient : Ingredient.values())
            offered.put(ingredient, ingredient.needed);
        experience = EXPERIENCE;
        this.phase = phase;
        kindling = phase == Phase.KINDLING ? ticksIn : KINDLING_TICKS;
        beating = phase == Phase.BEATING ? ticksIn : 0;
        waves = phase == Phase.BEATING ? VentricleWaves.due(beating / BEAT_TICKS) : 0;
    }

    public void setCelebrant(UUID celebrant)
    {
        this.celebrant = celebrant;
    }

    private record Saved(Phase phase, Optional<UUID> celebrant, Map<Ingredient, Integer> offered, int experience, int kindling, int beating,
                         int waves)
    {
    }

    /** The offering once took a blessed contraption where it now takes a Dark Amalgam, so cradles saved then read it as one. */
    private static Ingredient ingredientNamed(String name)
    {
        return "CONTRAPTION".equals(name) ? Ingredient.DARK_AMALGAM : Ingredient.valueOf(name);
    }

    private static final Codec<Phase>      PHASE_CODEC      = Codec.STRING.xmap(Phase::valueOf, Phase::name);
    private static final Codec<Ingredient> INGREDIENT_CODEC = Codec.STRING.xmap(VentricleCeremony::ingredientNamed, Ingredient::name);

    public static final Codec<VentricleCeremony> CODEC = RecordCodecBuilder.<Saved>create(instance -> instance.group(
            PHASE_CODEC.optionalFieldOf("phase", Phase.IDLE).forGetter(Saved::phase),
            UUIDUtil.CODEC.optionalFieldOf("celebrant").forGetter(Saved::celebrant),
            Codec.unboundedMap(INGREDIENT_CODEC, Codec.INT).optionalFieldOf("offered", Map.of()).forGetter(Saved::offered),
            Codec.INT.optionalFieldOf("experience", 0).forGetter(Saved::experience),
            Codec.INT.optionalFieldOf("kindling", 0).forGetter(Saved::kindling),
            Codec.INT.optionalFieldOf("beating", 0).forGetter(Saved::beating),
            Codec.INT.optionalFieldOf("waves", 0).forGetter(Saved::waves)
    ).apply(instance, Saved::new)).xmap(VentricleCeremony::fromSaved, VentricleCeremony::toSaved);

    /**
     * Saved data may be stale or edited, so anything that can't be a real rite falls back to the nearest one that can.
     */
    private static VentricleCeremony fromSaved(Saved saved)
    {
        var ceremony = new VentricleCeremony();
        if (saved.phase() == Phase.IDLE || saved.celebrant().isEmpty())
            return ceremony;
        ceremony.celebrant = saved.celebrant().get();
        for (var entry : saved.offered().entrySet())
            ceremony.offered.put(entry.getKey(), Math.clamp(entry.getValue(), 0, entry.getKey().needed));
        ceremony.experience = Math.clamp(saved.experience(), 0, EXPERIENCE);
        boolean paid = ceremony.itemsComplete() && ceremony.experience >= EXPERIENCE;
        ceremony.phase = paid ? saved.phase() : Phase.OFFERING;
        if (ceremony.phase == Phase.OFFERING && ceremony.offered.isEmpty())
            return new VentricleCeremony();
        if (ceremony.phase != Phase.OFFERING)
        {
            ceremony.kindling = Math.clamp(saved.kindling(), 0, KINDLING_TICKS);
            if (ceremony.phase == Phase.BEATING)
            {
                ceremony.beating = Math.clamp(saved.beating(), 0, BEATING_TICKS - 1);
                ceremony.waves = Math.clamp(saved.waves(), 0, VentricleWaves.due(ceremony.beating / BEAT_TICKS));
            }
        }
        return ceremony;
    }

    private Saved toSaved()
    {
        return new Saved(phase, Optional.ofNullable(celebrant), new EnumMap<>(offered), experience, kindling, beating, waves);
    }
}
