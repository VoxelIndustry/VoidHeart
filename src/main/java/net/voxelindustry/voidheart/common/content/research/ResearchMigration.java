package net.voxelindustry.voidheart.common.content.research;

import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Research put in front of research that worlds already had. Whoever owns one of the research that now hangs from a newer one is granted the newer one
 * too, so what they built keeps working: the Armillary, Athanor and portal cores draw void energy, and the Beholder draws ley.
 */
public final class ResearchMigration
{
    public static final Identifier VOID_ENERGY = id("void_energy");
    public static final Identifier LEY_SENSE   = id("ley_sense");

    /** Each research added in front of others, with the research that came before it and now hang from it. */
    public static final Map<Identifier, List<Identifier>> ADDED_PARENTS = Map.of(
            VOID_ENERGY, List.of(id("armillary"), id("tria_prima"), id("portal_flexion")),
            LEY_SENSE, List.of(id("beholder")));

    private ResearchMigration()
    {
    }

    /**
     * @param tree what the research registry holds, since a data pack may have taken a parent out
     * @return what was granted, sorted
     */
    public static List<Identifier> grantAddedParents(PlayerKnowledge knowledge, Set<Identifier> tree)
    {
        return grantAddedParents(knowledge, ADDED_PARENTS, tree);
    }

    static List<Identifier> grantAddedParents(PlayerKnowledge knowledge, Map<Identifier, List<Identifier>> added, Set<Identifier> tree)
    {
        var granted = new ArrayList<Identifier>();
        added.forEach((parent, children) ->
        {
            if (tree.contains(parent) && !knowledge.getCompleted().contains(parent) && children.stream().anyMatch(knowledge.getCompleted()::contains))
            {
                knowledge.grant(parent);
                granted.add(parent);
            }
        });
        granted.sort(null);
        return granted;
    }

    private static Identifier id(String path)
    {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }
}
