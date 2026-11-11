package net.voxelindustry.voidheart.client.apparatus;

import net.minecraft.client.Minecraft;
import net.voxelindustry.voidheart.common.content.apparatus.ShieldPayloads;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The shield hits seen lately, by entity, kept until their runes have died away.
 */
public final class ShieldHits
{
    /** Hits past this many on one entity drop the oldest, since a swarm would only stack shells nobody can tell apart. */
    private static final int MAX_PER_ENTITY = 3;

    public record Hit(Vector3fc from, float strength, boolean broke, long tick)
    {
    }

    private static final Map<Integer, List<Hit>> HITS = new HashMap<>();

    private ShieldHits()
    {
    }

    public static void receive(ShieldPayloads.Hit payload)
    {
        var level = Minecraft.getInstance().level;
        if (level == null)
            return;
        var hits = HITS.computeIfAbsent(payload.entity(), id -> new ArrayList<>());
        hits.add(new Hit(payload.from(), payload.strength(), payload.broke(), level.getGameTime()));
        if (hits.size() > MAX_PER_ENTITY)
            hits.removeFirst();
    }

    public static Map<Integer, List<Hit>> all()
    {
        return HITS;
    }

    public static List<Hit> of(int entity)
    {
        return HITS.getOrDefault(entity, List.of());
    }

    public static void tick(Minecraft minecraft)
    {
        if (minecraft.level == null)
        {
            HITS.clear();
            return;
        }
        long now = minecraft.level.getGameTime();
        HITS.values().forEach(hits -> hits.removeIf(hit -> now - hit.tick() > ShieldRuneRender.LIFE_TICKS || now < hit.tick()));
        HITS.values().removeIf(List::isEmpty);
    }
}
