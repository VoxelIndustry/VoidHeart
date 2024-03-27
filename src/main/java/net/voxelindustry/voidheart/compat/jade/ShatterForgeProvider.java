package net.voxelindustry.voidheart.compat.jade;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.common.content.shatterforge.ShatterForgeTile;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.IElementHelper;
import snownee.jade.impl.ui.ProgressArrowElement;

public class ShatterForgeProvider implements IServerDataProvider<BlockAccessor>, IBlockComponentProvider
{
    private static final Identifier SHATTER_FORGE = new Identifier(VoidHeart.MODID, "shatter_forge");

    @Override
    public void appendTooltip(
            ITooltip tooltip,
            BlockAccessor accessor,
            IPluginConfig config
    )
    {
        if (!accessor.getServerData().contains("progress"))
            return;

        var helper = IElementHelper.get();

        tooltip.append(helper.item(ItemStack.fromNbt(accessor.getServerData().getCompound("input"))));
        tooltip.append(new ProgressArrowElement(accessor.getServerData().getFloat("progress")));
        tooltip.append(helper.item(ItemStack.fromNbt(accessor.getServerData().getCompound("output"))));
    }

    @Override
    public Identifier getUid()
    {
        return SHATTER_FORGE;
    }

    @Override
    public void appendServerData(NbtCompound tag, BlockAccessor accessor)
    {
        var blockEntity = accessor.getBlockEntity();

        if (blockEntity instanceof ShatterForgeTile forge && forge.getRecipeState() != null)
        {
            tag.put("input", forge.getRecipeState().getInput(ItemStack.class, 0).writeNbt(new NbtCompound()));
            tag.putFloat("progress", forge.getRecipeState().completionDelta());
            tag.put("output", forge.getRecipeState().getOutput(ItemStack.class, 0).writeNbt(new NbtCompound()));
        }
    }
}
