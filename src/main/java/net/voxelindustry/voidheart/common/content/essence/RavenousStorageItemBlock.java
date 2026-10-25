package net.voxelindustry.voidheart.common.content.essence;

import com.geckolib.animatable.GeoItem;
import com.geckolib.animatable.client.GeoRenderProvider;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager.ControllerRegistrar;
import com.geckolib.util.GeckoLibUtil;
import net.minecraft.world.item.component.TooltipDisplay;
import java.util.function.Consumer;
import net.voxelindustry.voidheart.common.setup.VoidHeartDataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import net.voxelindustry.voidheart.client.render.RavenousCollectorItemRender;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class RavenousStorageItemBlock extends BlockItem implements GeoItem
{
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public RavenousStorageItemBlock(Block block, Item.Properties properties)
    {
        super(block, properties
                .stacksTo(1)
                .durability(RavenousCollectorTile.CAPACITY)
        );
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag)
    {

        var storage = stack.get(VoidHeartDataComponents.ESSENCE_STORAGE);
        if (storage != null)
        {
            tooltip.accept(Component.translatable(storage.type().translatableName()));
            tooltip.accept(Component.literal(storage.amount() + " / " + storage.maximumAmount()));
        }
        else
            tooltip.accept(Component.translatable(MODID + ".ravenous_collector.untyped"));
    }

    /**
     * No controllers, the item shows the still collector model.
     */
    @Override
    public void registerControllers(ControllerRegistrar controllers)
    {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache()
    {
        return cache;
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer)
    {
        consumer.accept(new RavenousCollectorItemRender());
    }
}
