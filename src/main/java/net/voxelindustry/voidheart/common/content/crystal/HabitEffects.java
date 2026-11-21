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
                case DEEP -> List.of(new Effect("pylon", MODID + ".habit.deep.reach"));
                case FOULED -> List.of(new Effect("spike", MODID + ".habit.fouled.greedy"));
                case SCOURED -> List.of(new Effect("pylon", MODID + ".habit.scoured.holds"));
                case SEARING -> List.of(new Effect("construct", MODID + ".habit.searing.gives"), new Effect("construct", MODID + ".habit.searing.shut"));
                case STILL -> List.of(new Effect("construct", MODID + ".habit.still.takes"), new Effect("construct", MODID + ".habit.still.shut"));
                default -> List.of();
            };
            case RUBRUM -> switch (habit)
            {
                case FOULED -> List.of(new Effect("construct", MODID + ".habit.fouled.holds"), new Effect("construct", MODID + ".habit.fouled.leaks"));
                case SCOURED -> List.of(new Effect("node", MODID + ".habit.scoured.draws"));
                case SEARING -> List.of(new Effect("cell", MODID + ".habit.searing.burst"));
                case STILL -> List.of(new Effect("cell", MODID + ".habit.still.reserve"), new Effect("cell", MODID + ".habit.still.keeps"));
                case WRUNG -> List.of(new Effect("cell", MODID + ".habit.wrung.drinks"));
                default -> List.of();
            };
            case MEMORIAM -> switch (habit)
            {
                case WRUNG -> List.of(new Effect("altar", MODID + ".habit.wrung.grudge"));
                case DEEP -> List.of(new Effect("table", MODID + ".habit.deep.records"));
                default -> List.of();
            };
        };
    }

    public static MutableComponent name(CrystalHabit habit)
    {
        return Component.translatable(habit.translationKey()).withStyle(habit.color());
    }
}
