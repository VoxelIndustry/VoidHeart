package net.voxelindustry.voidheart.compat.rei;

import me.shedaniel.math.Point;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.gui.Renderer;
import me.shedaniel.rei.api.client.gui.widgets.Widget;
import me.shedaniel.rei.api.client.gui.widgets.Widgets;
import me.shedaniel.rei.api.client.registry.display.DisplayCategory;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.voxelindustry.voidheart.common.content.essence.EssenceType;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * The base in the middle with its reagents around it, as the crucibles stand around the core, and the product on the right.
 */
public class AthanorRecipeCategory implements DisplayCategory<AthanorRecipeDisplay>
{
    public static final CategoryIdentifier<AthanorRecipeDisplay> IDENTIFIER = CategoryIdentifier.of(MODID, "athanor_brewing");

    private static final DecimalFormat SECONDS_FORMAT = new DecimalFormat("#.##");

    /**
     * Offsets of the reagent slots from the base slot: above, right, below, left.
     */
    private static final int[][] REAGENT_OFFSETS = {{0, -20}, {20, 0}, {0, 20}, {-20, 0}};

    @Override
    public Identifier getIdentifier()
    {
        return IDENTIFIER.getIdentifier();
    }

    @Override
    public Renderer getIcon()
    {
        return EntryStacks.of(VoidHeartBlocks.ATHANOR);
    }

    @Override
    public Component getTitle()
    {
        return Component.translatable(MODID + ".athanor_brewing");
    }

    @Override
    public List<Widget> setupDisplay(AthanorRecipeDisplay display, Rectangle bounds)
    {
        List<Widget> widgets = new ArrayList<>();

        var inputs = display.getInputEntries();
        int baseX = bounds.x + 26;
        int baseY = bounds.y + 24;

        if (!inputs.isEmpty())
            widgets.add(Widgets.createSlot(new Point(baseX, baseY)).entries(inputs.get(0)).markInput());
        for (int i = 1; i < inputs.size() && i <= REAGENT_OFFSETS.length; i++)
        {
            var offset = REAGENT_OFFSETS[i - 1];
            widgets.add(Widgets.createSlot(new Point(baseX + offset[0], baseY + offset[1])).entries(inputs.get(i)).markInput());
        }

        // Clear of the right reagent slot, which ends 38 past the base.
        int arrowX = baseX + 44;
        int resultX = arrowX + 32;
        widgets.add(Widgets.createArrow(new Point(arrowX, baseY)).animationDurationTicks(display.getTime()));
        widgets.add(Widgets.createResultSlotBackground(new Point(resultX, baseY)));
        widgets.add(Widgets.createSlot(new Point(resultX, baseY)).entries(display.getOutputEntries().isEmpty() ? List.of() : display.getOutputEntries().get(0))
                .disableBackground().markOutput());

        widgets.add(Widgets.createLabel(new Point(arrowX + 12, baseY - 12),
                        Component.translatable(MODID + ".athanor_brewing.time", SECONDS_FORMAT.format(display.getTime() / 20D)))
                .centered().noShadow().color(0xFF404040, 0xFFBBBBBB));

        // Essence costs sit under the result, below its background.
        int textX = resultX + 8;
        int lineY = baseY + 26;
        for (var aspect : display.getAspects().entrySet())
        {
            widgets.add(Widgets.createLabel(new Point(textX, lineY),
                            Component.translatable(MODID + ".rei.shatter_forge.aspect", Component.translatable(aspect.getKey().translatableName()), aspect.getValue()))
                    .centered().noShadow().color(aspectColor(aspect.getKey()), aspectColor(aspect.getKey())));
            lineY += 10;
        }
        return widgets;
    }

    private static int aspectColor(EssenceType type)
    {
        return switch (type.identifier().getPath())
        {
            case "stillness" -> 0xFF7FB8FF;
            case "fiery" -> 0xFFFF8A3D;
            default -> 0xFF999999;
        };
    }

    @Override
    public int getDisplayHeight()
    {
        return 72;
    }

    @Override
    public CategoryIdentifier<? extends AthanorRecipeDisplay> getCategoryIdentifier()
    {
        return IDENTIFIER;
    }
}
