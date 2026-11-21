package net.voxelindustry.voidheart.common.content.crystal;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * What a habit changes, worded to be read at a glance: on an item, where it acts and the change, and on the block it changes, the change alone. A habit
 * whose effects aren't built yet has none listed.
 */
public final class HabitEffects
{
    /**
     * @param where  the key of where it acts, such as pylon
     * @param change the key of the change, with a {@code .block} variant for the block it changes
     */
    public record Effect(String where, String change)
    {
        public Component onItem()
        {
            return Component.translatable(MODID + ".habit.where." + where).withStyle(ChatFormatting.GRAY)
                    .append(" ")
                    .append(Component.translatable(change).withStyle(ChatFormatting.WHITE));
        }

        public Component onBlock()
        {
            return Component.translatable(change + ".block").withStyle(ChatFormatting.WHITE);
        }
    }

    private HabitEffects()
    {
    }

    public static List<Effect> of(CrystalHabit habit, CrystalKind kind)
    {
        return switch (kind)
        {
            case AZURIUM -> switch (habit)
            {
                case CLEAR -> List.of(new Effect("pylon", MODID + ".habit.clear.arcs"), new Effect("pylon", MODID + ".habit.clear.dark"));
                case FOULED -> List.of(new Effect("spike", MODID + ".habit.fouled.greedy"));
                case SCOURED -> List.of(new Effect("pylon", MODID + ".habit.scoured.holds"));
                default -> List.of();
            };
            case RUBRUM -> switch (habit)
            {
                case FOULED -> List.of(new Effect("construct", MODID + ".habit.fouled.holds"), new Effect("construct", MODID + ".habit.fouled.leaks"));
                case SCOURED -> List.of(new Effect("node", MODID + ".habit.scoured.draws"));
                default -> List.of();
            };
            case MEMORIAM -> List.of();
        };
    }

    public static MutableComponent name(CrystalHabit habit)
    {
        return Component.translatable(habit.translationKey()).withStyle(habit.color());
    }
}
