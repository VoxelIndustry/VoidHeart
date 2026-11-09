package net.voxelindustry.voidheart.compat.rei;

import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.client.registry.entry.CollapsibleEntryRegistry;
import me.shedaniel.rei.api.client.registry.screen.OverlayDecider;
import me.shedaniel.rei.api.client.registry.screen.ScreenRegistry;
import me.shedaniel.rei.api.common.util.EntryStacks;
import me.shedaniel.rei.plugin.client.BuiltinClientPlugin;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import net.voxelindustry.voidheart.client.InsightMemory;
import net.voxelindustry.voidheart.client.screen.ResearchScreen;
import net.voxelindustry.voidheart.common.content.oil.Brew;
import net.voxelindustry.voidheart.common.content.oil.Brews;
import net.voxelindustry.voidheart.common.content.oil.SinVessel;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;
import net.voxelindustry.voidheart.common.world.daedalus.DaedalusProp;

import java.util.Arrays;
import java.util.List;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class VoidHeartREIPlugin implements REIClientPlugin
{
    @Override
    public void registerCategories(CategoryRegistry registry)
    {
        registry.add(new AltarRecipeCategory());
        registry.add(new ShatterForgeRecipeCategory());
        registry.add(new AthanorRecipeCategory());

        registry.addWorkstations(AltarRecipeCategory.IDENTIFIER, EntryStacks.of(VoidHeartBlocks.VOID_ALTAR));
        registry.addWorkstations(ShatterForgeRecipeCategory.IDENTIFIER, EntryStacks.of(VoidHeartBlocks.SHATTER_FORGE));
        registry.addWorkstations(AthanorRecipeCategory.IDENTIFIER, EntryStacks.of(VoidHeartBlocks.ATHANOR));
        EnergyRei.registerCategories(registry);
    }

    /**
     * Shards and flasks change a brew rather than make one, so they get a page each instead of a recipe.
     */
    @Override
    public void registerDisplays(DisplayRegistry registry)
    {
        var plugin = BuiltinClientPlugin.getInstance();
        var title = Component.translatable("voidheart.rei.refinement");
        int rubrumPercent = Math.round((Brew.PROLONGED_SCALE - 1) * 100);
        int memorySeconds = (int) (InsightMemory.MEMORY_MILLIS / 1000);
        plugin.registerInformation(EntryStacks.of(VoidHeartItems.AZURIUM_SHARD), title, lines -> refinement(lines, Component.translatable("voidheart.rei.refinement.azurium")));
        plugin.registerInformation(EntryStacks.of(VoidHeartItems.RUBRUM_SHARD), title, lines -> refinement(lines,
                Component.translatable("voidheart.rei.refinement.rubrum", rubrumPercent)));
        plugin.registerInformation(EntryStacks.of(VoidHeartItems.MEMORIAM_SHARD), title, lines -> refinement(lines,
                Component.translatable("voidheart.rei.refinement.memoriam", memorySeconds)));
        for (var vessel : SinVessel.values())
        {
            String holds = vessel.holdsElixirs() && vessel.holdsOils() ? "both" : vessel.holdsOils() ? "oils" : "drinks";
            plugin.registerInformation(EntryStacks.of(Brews.flask(vessel)), title, lines -> refinement(lines,
                    Component.translatable("voidheart.rei.refinement.flask"),
                    Component.translatable(vessel.traitKey()),
                    Component.translatable("voidheart.rei.refinement.holds_" + holds)));
        }
        EnergyRei.registerDisplays(registry);
    }

    private static List<Component> refinement(List<Component> lines, Component... text)
    {
        lines.addAll(List.of(text));
        lines.add(Component.translatable("voidheart.rei.refinement.research"));
        return lines;
    }

    @Override
    public void registerCollapsibleEntries(CollapsibleEntryRegistry registry)
    {
        registry.group(Identifier.fromNamespaceAndPath(MODID, "daedalus_props"), Component.translatable("voidheart.rei.group.daedalus_props"),
                Arrays.stream(DaedalusProp.values()).map(prop -> EntryStacks.of(VoidHeartBlocks.daedalusProp(prop))).toList());
    }

    @Override
    public void registerScreens(ScreenRegistry registry)
    {
        registry.registerDecider(new OverlayDecider()
        {
            @Override
            public <R extends Screen> boolean isHandingScreen(Class<R> screen)
            {
                return ResearchScreen.class.isAssignableFrom(screen);
            }

            @Override
            public <R extends Screen> InteractionResult shouldScreenBeOverlaid(R screen)
            {
                return InteractionResult.FAIL;
            }

            @Override
            public double getPriority()
            {
                return 1000;
            }
        });
    }
}
