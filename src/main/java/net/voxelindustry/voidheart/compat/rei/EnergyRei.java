package net.voxelindustry.voidheart.compat.rei;

import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.common.util.EntryStacks;
import me.shedaniel.rei.plugin.client.BuiltinClientPlugin;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.voxelindustry.voidheart.common.energy.FoundationBalance;
import net.voxelindustry.voidheart.common.energy.creative.CreativeEnergyContent;
import net.voxelindustry.voidheart.common.energy.engine.RavenousEngineRei;
import net.voxelindustry.voidheart.common.energy.grid.VoidGridRei;
import net.voxelindustry.voidheart.common.energy.ley.LeyStoneRei;
import net.voxelindustry.voidheart.common.energy.reactor.ContravoidReactorRei;
import net.voxelindustry.voidheart.common.energy.siphon.DeepSiphonRei;
import net.voxelindustry.voidheart.common.energy.spike.LeySpikeRei;
import net.voxelindustry.voidheart.common.energy.storage.RubrumRei;
import net.voxelindustry.voidheart.common.energy.fissure.VoidFissureRei;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * VoidHeartREIPlugin's one call into the energy system, handing off to each package.
 */
public final class EnergyRei
{
    private EnergyRei()
    {
    }

    public static void registerCategories(CategoryRegistry registry)
    {
        VoidGridRei.registerCategories(registry);
        RubrumRei.registerCategories(registry);
        LeyStoneRei.registerCategories(registry);
        RavenousEngineRei.registerCategories(registry);
        LeySpikeRei.registerCategories(registry);
        VoidFissureRei.registerCategories(registry);
        ContravoidReactorRei.registerCategories(registry);
        DeepSiphonRei.registerCategories(registry);
    }

    public static void registerDisplays(DisplayRegistry registry)
    {
        VoidGridRei.registerDisplays(registry);
        RubrumRei.registerDisplays(registry);
        LeyStoneRei.registerDisplays(registry);
        RavenousEngineRei.registerDisplays(registry);
        LeySpikeRei.registerDisplays(registry);
        VoidFissureRei.registerDisplays(registry);
        ContravoidReactorRei.registerDisplays(registry);
        DeepSiphonRei.registerDisplays(registry);

        page(CreativeEnergyContent.CREATIVE_VOID_WELL, null, line("creative_void_well", FoundationBalance.CREATIVE_WELL_RADIUS));
        page(CreativeEnergyContent.CREATIVE_LEY_STONE, null, line("creative_ley_stone", FoundationBalance.CREATIVE_LEY_STONE_RADIUS));
    }

    /**
     * An information page titled with the item's name, ending with the research that teaches it, if any.
     */
    public static void page(ItemLike item, @Nullable String research, Component... lines)
    {
        var title = new ItemStack(item).getHoverName();
        BuiltinClientPlugin.getInstance().registerInformation(EntryStacks.of(item), title, list ->
        {
            list.addAll(List.of(lines));
            if (research != null)
                list.add(Component.translatable(MODID + ".research.locked", Component.translatable("research." + MODID + "." + research)));
            return list;
        });
    }

    /**
     * One paragraph of a page, from {@code voidheart.rei.<key>}.
     */
    public static Component line(String key, Object... args)
    {
        return Component.translatable(MODID + ".rei." + key, args);
    }

    /**
     * A large amount with its thousands grouped, as the research pages write them.
     */
    public static String amount(long value)
    {
        return String.format(Locale.ROOT, "%,d", value);
    }
}
