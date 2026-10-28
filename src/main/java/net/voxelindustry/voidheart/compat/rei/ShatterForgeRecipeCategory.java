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
import net.voxelindustry.voidheart.common.util.ExperienceUtil;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class ShatterForgeRecipeCategory implements DisplayCategory<ShatterForgeRecipeDisplay>
{
    public static final CategoryIdentifier<ShatterForgeRecipeDisplay> IDENTIFIER = CategoryIdentifier.of(MODID, "shatter_forge_crafting");

    private static final DecimalFormat SECONDS_FORMAT = new DecimalFormat("#.##");

    private static final int EXPERIENCE_COLOR = 0xFF5BA51F;

    @Override
    public Identifier getIdentifier()
    {
        return IDENTIFIER.getIdentifier();
    }

    @Override
    public Renderer getIcon()
    {
        return EntryStacks.of(VoidHeartBlocks.SHATTER_FORGE);
    }

    @Override
    public Component getTitle()
    {
        return Component.translatable(IDENTIFIER.getIdentifier().toString().replace(":", "."));
    }

    @Override
    public List<Widget> setupDisplay(ShatterForgeRecipeDisplay display, Rectangle bounds)
    {
        List<Widget> widgets = new ArrayList<>();

        int centerX = bounds.getCenterX();
        int rowY = bounds.y + 8;

        widgets.add(Widgets.createSlot(new Point(centerX - 41, rowY)).entries(display.getInputEntries().isEmpty() ? List.of() : display.getInputEntries().get(0)).markInput());
        widgets.add(Widgets.createArrow(new Point(centerX - 12, rowY)).animationDurationTicks(display.getTime()));
        widgets.add(Widgets.createResultSlotBackground(new Point(centerX + 24, rowY)));
        widgets.add(Widgets.createSlot(new Point(centerX + 24, rowY)).entries(display.getOutputEntries().isEmpty() ? List.of() : display.getOutputEntries().get(0)).disableBackground().markOutput());

        widgets.add(Widgets.createLabel(new Point(centerX, rowY + 26),
                        Component.translatable(MODID + ".shatter_forge_crafting.monoliths", display.getMonolithCount()))
                .centered().noShadow().color(0xFF404040, 0xFFBBBBBB));
        widgets.add(Widgets.createLabel(new Point(centerX, rowY + 38),
                        Component.translatable(MODID + ".shatter_forge_crafting.time", SECONDS_FORMAT.format(display.getTime() / 20D)))
                .centered().noShadow().color(0xFF404040, 0xFFBBBBBB));

        int lineY = rowY + 50;
        for (var aspect : display.getAspects().entrySet())
        {
            widgets.add(Widgets.createLabel(new Point(centerX, lineY),
                            Component.translatable(MODID + ".rei.shatter_forge.aspect", Component.translatable(aspect.getKey().translatableName()), aspect.getValue()))
                    .centered().noShadow().color(aspectColor(aspect.getKey()), aspectColor(aspect.getKey())));
            lineY += 12;
        }
        if (display.getDraw() > 0)
        {
            widgets.add(Widgets.createLabel(new Point(centerX, lineY),
                            Component.translatable(MODID + ".rei.shatter_forge.draw", display.getDraw()))
                    .centered().noShadow().color(0xFF404040, 0xFFBBBBBB));
            lineY += 12;
        }
        if (display.getExperience() > 0)
        {
            widgets.add(Widgets.createLabel(new Point(centerX, lineY),
                            Component.translatable(MODID + ".rei.shatter_forge.experience", display.getExperience(), ExperienceUtil.getLevelsToGather(display.getExperience())))
                    .centered().noShadow().color(EXPERIENCE_COLOR, EXPERIENCE_COLOR));
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
        return 112;
    }

    @Override
    public CategoryIdentifier<? extends ShatterForgeRecipeDisplay> getCategoryIdentifier()
    {
        return IDENTIFIER;
    }
}
