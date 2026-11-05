package net.voxelindustry.voidheart.common.content.armillary;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import lombok.Getter;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.voxelindustry.voidheart.common.world.dungeon.DungeonAddress;
import net.voxelindustry.voidheart.common.world.dungeon.DungeonSize;
import net.voxelindustry.voidheart.common.world.dungeon.DungeonTheme;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * What one armillary has found so far and which bubble it is reaching for. Counts its own ticks, so it stands still while its chunk is unloaded.
 */
public final class ArmillaryChart
{
    public enum Phase implements StringRepresentable
    {
        SCANNING,
        SEARCHING,
        REACHED,
        CONNECTED;

        @Override
        public String getSerializedName()
        {
            return name().toLowerCase();
        }
    }

    public enum Event
    {
        NONE,
        FADED,
        FOUND,
        REACHED
    }

    public record Rules(int firstScanTicks, int scanTicks, int maxBubbles, int fadeTicks, int minSearchTicks, int maxSearchTicks)
    {
    }

    public static final Codec<ArmillaryChart> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("age").forGetter(chart -> chart.age),
            Codec.LONG.fieldOf("next_scan_at").forGetter(chart -> chart.nextScanAt),
            Codec.INT.fieldOf("next_id").forGetter(chart -> chart.nextId),
            VoidBubble.CODEC.listOf().fieldOf("bubbles").forGetter(chart -> chart.bubbles),
            Codec.INT.fieldOf("target").forGetter(chart -> chart.targetId),
            StringRepresentable.fromEnum(Phase::values).fieldOf("phase").forGetter(chart -> chart.phase),
            Codec.INT.fieldOf("search_ticks").forGetter(chart -> chart.searchTicks),
            Codec.INT.fieldOf("search_duration").forGetter(chart -> chart.searchDuration)
    ).apply(instance, ArmillaryChart::new));

    /** How long a bubble stays on the chart once its connection gave way, empty and falling apart. */
    public static final int RUIN_TICKS = 20 * 90;

    private static final int     NONE                = -1;
    private static final int     PLACEMENT_TRIES     = 12;
    private static final float   EDGE_MARGIN         = 0.08F;
    private static final float   MIN_SPACING         = 0.09F;
    private static final int[]   DIFFICULTY_WEIGHTS  = { 35, 28, 20, 12, 5 };
    private static final int[]   SIZE_WEIGHTS        = { 45, 35, 20 };

    private long age;
    private long nextScanAt = NONE;
    private int  nextId;

    private final List<VoidBubble> bubbles;

    @Getter
    private int   targetId = NONE;
    @Getter
    private Phase phase    = Phase.SCANNING;
    @Getter
    private int   searchTicks;
    @Getter
    private int   searchDuration;

    public ArmillaryChart()
    {
        bubbles = new ArrayList<>();
    }

    private ArmillaryChart(long age, long nextScanAt, int nextId, List<VoidBubble> bubbles, int targetId, Phase phase, int searchTicks, int searchDuration)
    {
        this.age = age;
        this.nextScanAt = nextScanAt;
        this.nextId = nextId;
        this.bubbles = new ArrayList<>(bubbles);
        this.targetId = targetId;
        this.phase = phase;
        this.searchTicks = searchTicks;
        this.searchDuration = searchDuration;

        if (target() == null || searchDuration <= 0)
            clearTarget();
    }

    public List<VoidBubble> bubbles()
    {
        return Collections.unmodifiableList(bubbles);
    }

    public long age()
    {
        return age;
    }

    public @Nullable VoidBubble target()
    {
        return bubble(targetId);
    }

    public @Nullable VoidBubble bubble(int id)
    {
        for (var bubble : bubbles)
        {
            if (bubble.id() == id)
                return bubble;
        }
        return null;
    }

    public Event tick(Rules rules, RandomSource random)
    {
        age++;
        if (nextScanAt == NONE)
            nextScanAt = age - 1 + rules.firstScanTicks();

        var event = bubbles.removeIf(bubble -> bubble.fadeAt() <= age) ? Event.FADED : Event.NONE;

        if (age >= nextScanAt)
        {
            nextScanAt = age + rules.scanTicks();
            if (liveBubbles() < rules.maxBubbles())
            {
                find(rules, random);
                event = Event.FOUND;
            }
        }

        if (phase == Phase.SEARCHING && ++searchTicks >= searchDuration)
        {
            phase = Phase.REACHED;
            event = Event.REACHED;
        }
        return event;
    }

    public VoidBubble find(Rules rules, RandomSource random)
    {
        int difficulty = DungeonAddress.MIN_DIFFICULTY + weighted(DIFFICULTY_WEIGHTS, random);
        var size = DungeonSize.values()[weighted(SIZE_WEIGHTS, random)];
        var themes = Arrays.stream(DungeonTheme.values()).filter(candidate -> candidate.comesIn(size)).toList();
        var theme = themes.get(random.nextInt(themes.size()));
        long seed = random.nextLong();

        var bubble = new VoidBubble(nextId++, difficulty, size, theme, freeX(difficulty, seed, size, random), seed, age, age + rules.fadeTicks(), false);
        bubbles.add(bubble);
        return bubble;
    }

    private float freeX(int difficulty, long seed, DungeonSize size, RandomSource random)
    {
        float y = VoidBubble.y(difficulty, seed);
        float radius = VoidBubble.radius(size);
        float x = 0.5F;
        for (int attempt = 0; attempt < PLACEMENT_TRIES; attempt++)
        {
            x = EDGE_MARGIN + random.nextFloat() * (1 - 2 * EDGE_MARGIN);
            if (isFree(x, y, radius))
                break;
        }
        return x;
    }

    private boolean isFree(float x, float y, float radius)
    {
        for (var other : bubbles)
        {
            float reach = radius + other.radius() + MIN_SPACING;
            float dx = (x - other.x()) * ArmillaryRoots.ASPECT;
            float dy = y - other.y();
            if (dx * dx + dy * dy < reach * reach)
                return false;
        }
        return true;
    }

    private static int weighted(int[] weights, RandomSource random)
    {
        int total = 0;
        for (int weight : weights)
            total += weight;

        int roll = random.nextInt(total);
        for (int index = 0; index < weights.length; index++)
        {
            roll -= weights[index];
            if (roll < 0)
                return index;
        }
        return weights.length - 1;
    }

    /**
     * Reaching for another bubble throws away the path to the last one, which starts fading again.
     */
    public boolean target(int id, Rules rules)
    {
        var bubble = bubble(id);
        if (bubble == null || bubble.broken() || phase == Phase.CONNECTED || id == targetId)
            return false;

        releaseTarget(rules);
        replace(bubble.withFadeAt(VoidBubble.NEVER));
        targetId = id;
        phase = Phase.SEARCHING;
        searchTicks = 0;
        searchDuration = searchDuration(bubble.difficulty(), rules);
        return true;
    }

    static int searchDuration(int difficulty, Rules rules)
    {
        float depth = (difficulty - DungeonAddress.MIN_DIFFICULTY) / (float) (DungeonAddress.MAX_DIFFICULTY - DungeonAddress.MIN_DIFFICULTY);
        return Math.round(rules.minSearchTicks() + (rules.maxSearchTicks() - rules.minSearchTicks()) * depth);
    }

    public boolean abandon(Rules rules)
    {
        if (phase == Phase.CONNECTED || target() == null)
            return false;

        releaseTarget(rules);
        clearTarget();
        return true;
    }

    /**
     * A search takes its full time unless a command moves it along.
     */
    public boolean advanceSearch(float progress)
    {
        if (phase != Phase.SEARCHING)
            return false;

        searchTicks = Math.round(searchDuration * progress);
        if (searchTicks >= searchDuration)
        {
            searchTicks = searchDuration;
            phase = Phase.REACHED;
        }
        return true;
    }

    public boolean connect()
    {
        if (phase != Phase.REACHED)
            return false;

        phase = Phase.CONNECTED;
        return true;
    }

    /**
     * The bubble is spent once its connection is gone, and only lingers as a broken shell.
     */
    public void connectionClosed()
    {
        var spent = target();
        if (spent != null)
            replace(spent.broke(age + RUIN_TICKS));
        clearTarget();
    }

    public int liveBubbles()
    {
        return (int) bubbles.stream().filter(bubble -> !bubble.broken()).count();
    }

    private void releaseTarget(Rules rules)
    {
        var target = target();
        if (target != null)
            replace(target.withFadeAt(age + rules.fadeTicks()));
    }

    private void clearTarget()
    {
        targetId = NONE;
        phase = Phase.SCANNING;
        searchTicks = 0;
        searchDuration = 0;
    }

    private void replace(VoidBubble bubble)
    {
        bubbles.replaceAll(existing -> existing.id() == bubble.id() ? bubble : existing);
    }
}
