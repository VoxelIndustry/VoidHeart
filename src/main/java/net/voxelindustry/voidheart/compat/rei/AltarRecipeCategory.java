package net.voxelindustry.voidheart.compat.rei;

import me.shedaniel.math.Point;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.EntryStack;
import me.shedaniel.rei.api.RecipeCategory;
import me.shedaniel.rei.api.widgets.Widgets;
import me.shedaniel.rei.gui.entries.RecipeEntry;
import me.shedaniel.rei.gui.entries.SimpleRecipeEntry;
import me.shedaniel.rei.gui.widget.Widget;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.util.Identifier;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static java.util.Collections.singletonList;
import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class AltarRecipeCategory implements RecipeCategory<AltarRecipeDisplay>
{
    public static final Identifier IDENTIFIER = new Identifier(MODID, "altar_crafting");

    @Override
    public Identifier getIdentifier()
    {
        return IDENTIFIER;
    }

    @Override
    public EntryStack getLogo()
    {
        return EntryStack.create(VoidHeartBlocks.VOID_ALTAR);
    }

    @Override
    public String getCategoryName()
    {
        return I18n.translate(IDENTIFIER.toString().replace(":", "."));
    }

    @Override
    public RecipeEntry getSimpleRenderer(AltarRecipeDisplay recipe)
    {
        return SimpleRecipeEntry.create(singletonList(recipe.getInputEntries().get(0)), recipe.getOutputEntries());
    }

    @Override
    public List<Widget> setupDisplay(AltarRecipeDisplay display, Rectangle bounds)
    {
        List<Widget> widgets = new ArrayList<>();

        int startX = 35;
        int startY = 16;
        widgets.add(Widgets.createSlot(new Point(bounds.x + startX, bounds.y + startY)).entries(getInput(display, 0)).markInput());
        widgets.add(Widgets.createSlot(new Point(bounds.x + startX - 18, bounds.y + startY + 36)).entries(getInput(display, 1)).markInput());
        widgets.add(Widgets.createSlot(new Point(bounds.x + startX + 18, bounds.y + startY + 36)).entries(getInput(display, 2)).markInput());

        widgets.add(Widgets.createSlot(new Point(bounds.x + startX + 36, bounds.y + startY + 18)).entries(getInput(display, 3)).markInput());
        widgets.add(Widgets.createSlot(new Point(bounds.x + startX + 36, bounds.y + startY - 18)).entries(getInput(display, 4)).markInput());

        widgets.add(Widgets.createSlot(new Point(bounds.x + startX - 18, bounds.y + startY - 36)).entries(getInput(display, 5)).markInput());
        widgets.add(Widgets.createSlot(new Point(bounds.x + startX + 18, bounds.y + startY - 36)).entries(getInput(display, 6)).markInput());

        widgets.add(Widgets.createSlot(new Point(bounds.x + startX - 36, bounds.y + startY + 18)).entries(getInput(display, 7)).markInput());
        widgets.add(Widgets.createSlot(new Point(bounds.x + startX - 36, bounds.y + startY - 18)).entries(getInput(display, 8)).markInput());

        widgets.add(Widgets.createSlot(new Point(bounds.x + startX + 36 + 36, bounds.y + startY)).entries(getOutput(display, 0)).markOutput());

        return widgets;
    }

    @Override
    public int getDisplayHeight()
    {
        return 90;
    }

    public List<EntryStack> getInput(AltarRecipeDisplay recipeDisplay, int index)
    {
        List<List<EntryStack>> inputs = recipeDisplay.getInputEntries();
        return inputs.size() > index ? inputs.get(index) : Collections.emptyList();
    }

    public List<EntryStack> getOutput(AltarRecipeDisplay recipeDisplay, int index)
    {
        List<EntryStack> outputs = recipeDisplay.getOutputEntries();
        return outputs.size() > index ? Collections.singletonList(outputs.get(index)) : Collections.emptyList();
    }
}
