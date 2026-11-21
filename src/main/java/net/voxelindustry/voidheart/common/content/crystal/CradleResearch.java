package net.voxelindustry.voidheart.common.content.crystal;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.voxelindustry.voidheart.common.content.research.Knowledge;
import org.jspecify.annotations.Nullable;

import java.util.UUID;
import java.util.function.Predicate;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * The research that lets each habit settle in a cradle. Habits whose research doesn't exist yet never settle.
 */
public final class CradleResearch
{
    public static final Identifier CRYSTAL_CRADLE = Identifier.fromNamespaceAndPath(MODID, "crystal_cradle");

    private CradleResearch()
    {
    }

    public static @Nullable Identifier researchFor(CrystalHabit habit)
    {
        return switch (habit)
        {
            case CLEAR -> CRYSTAL_CRADLE;
            case DEEP -> Identifier.fromNamespaceAndPath(MODID, "deep_growth");
            case FOULED -> Identifier.fromNamespaceAndPath(MODID, "seep_growth");
            case SCOURED -> Identifier.fromNamespaceAndPath(MODID, "scouring");
            case SEARING, STILL -> Identifier.fromNamespaceAndPath(MODID, "fed_growth");
            case WRUNG -> Identifier.fromNamespaceAndPath(MODID, "wrung_growth");
            case NONE, PLAIN -> null;
        };
    }

    /**
     * Which habits settle in a cradle that player owns. A cradle nobody owns settles none.
     */
    public static Predicate<CrystalHabit> settles(MinecraftServer server, @Nullable UUID owner)
    {
        return habit ->
        {
            var research = researchFor(habit);
            return owner != null && research != null && Knowledge.hasCompleted(server, owner, research);
        };
    }
}
