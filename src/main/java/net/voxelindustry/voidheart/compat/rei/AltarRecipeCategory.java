package net.voxelindustry.voidheart.compat.rei;

import me.shedaniel.math.Point;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.gui.Renderer;
import me.shedaniel.rei.api.client.gui.widgets.Widget;
import me.shedaniel.rei.api.client.gui.widgets.Widgets;
import me.shedaniel.rei.api.client.registry.display.DisplayCategory;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.entry.EntryStack;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class AltarRecipeCategory implements DisplayCategory<AltarRecipeDisplay>
{
    public static final CategoryIdentifier<AltarRecipeDisplay> IDENTIFIER = CategoryIdentifier.of(MODID, "altar_crafting");

    @Override
    public Identifier getIdentifier()
    {
        return IDENTIFIER.getIdentifier();
    }

    @Override
    public Renderer getIcon()
    {
        return EntryStacks.of(VoidHeartBlocks.VOID_ALTAR);
    }

    @Override
    public Text getTitle()
    {
        return Text.translatable(IDENTIFIER.getIdentifier().toString().replace(":", "."));
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

    @Override
    public CategoryIdentifier<? extends AltarRecipeDisplay> getCategoryIdentifier()
    {
        return IDENTIFIER;
    }

    public List<EntryStack<ItemStack>> getInput(AltarRecipeDisplay recipeDisplay, int index)
    {
        var inputs = recipeDisplay.getInputEntries();
        return inputs.size() > index ? inputs.get(index).castAsList() : Collections.emptyList();
    }

    public List<EntryStack<ItemStack>> getOutput(AltarRecipeDisplay recipeDisplay, int index)
    {
        var outputs = recipeDisplay.getOutputEntries();
        return outputs.size() > index ? outputs.get(index).castAsList() : Collections.emptyList();
    }
}
