package net.voxelindustry.voidheart.common.content.blight;

import net.minecraft.resources.Identifier;
import net.voxelindustry.voidheart.common.content.research.Research;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

/**
 * The designs a blessed contraption can teach. A blueprint is only an id: whatever research names it in its {@code blueprint} field stays out of sight
 * until it is learned, so the pool is whatever the research tree asks for.
 */
public final class Blueprints
{
    /** How many designs one contraption shows. */
    public static final int OFFERED = 3;

    private Blueprints()
    {
    }

    /** Every blueprint some research waits on, in a fixed order. */
    public static List<Identifier> pool(Map<Identifier, Research> tree)
    {
        return tree.values().stream()
                .flatMap(research -> research.blueprint().stream())
                .distinct()
                .sorted()
                .toList();
    }

    public static List<Identifier> unknown(List<Identifier> pool, Collection<Identifier> known)
    {
        return pool.stream().filter(blueprint -> !known.contains(blueprint)).toList();
    }

    /** Up to {@link #OFFERED} designs the player does not know, at random. */
    public static List<Identifier> draw(List<Identifier> pool, Collection<Identifier> known, Random random)
    {
        var left = new ArrayList<>(unknown(pool, known));
        Collections.shuffle(left, random);
        return List.copyOf(left.subList(0, Math.min(OFFERED, left.size())));
    }

    /**
     * What a contraption shows now. The designs it already drew stay, so opening it again rerolls nothing, but those learned since or gone from the tree are
     * dropped. It draws afresh only once none of them is left.
     */
    public static List<Identifier> offer(List<Identifier> drawn, List<Identifier> pool, Collection<Identifier> known, Random random)
    {
        var kept = drawn.stream().filter(pool::contains).filter(blueprint -> !known.contains(blueprint)).toList();
        return kept.isEmpty() ? draw(pool, known, random) : kept;
    }

    /** The research a blueprint brings into sight. */
    public static Optional<Map.Entry<Identifier, Research>> researchOf(Identifier blueprint, Map<Identifier, Research> tree)
    {
        return tree.entrySet().stream()
                .filter(entry -> entry.getValue().blueprint().filter(blueprint::equals).isPresent())
                .min(Map.Entry.comparingByKey());
    }

    public static String nameKey(Identifier blueprint)
    {
        return "blueprint." + blueprint.getNamespace() + "." + blueprint.getPath();
    }

    public static String loreKey(Identifier blueprint)
    {
        return nameKey(blueprint) + ".lore";
    }
}
