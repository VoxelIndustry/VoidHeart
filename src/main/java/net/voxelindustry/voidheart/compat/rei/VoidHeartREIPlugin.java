package net.voxelindustry.voidheart.compat.rei;

import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.screen.OverlayDecider;
import me.shedaniel.rei.api.client.registry.screen.ScreenRegistry;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.InteractionResult;
import net.voxelindustry.voidheart.client.screen.ResearchScreen;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;

public class VoidHeartREIPlugin implements REIClientPlugin
{
    @Override
    public void registerCategories(CategoryRegistry registry)
    {
        registry.add(new AltarRecipeCategory());
        registry.add(new ShatterForgeRecipeCategory());

        registry.addWorkstations(AltarRecipeCategory.IDENTIFIER, EntryStacks.of(VoidHeartBlocks.VOID_ALTAR));
        registry.addWorkstations(ShatterForgeRecipeCategory.IDENTIFIER, EntryStacks.of(VoidHeartBlocks.SHATTER_FORGE));
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
