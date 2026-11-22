package net.voxelindustry.voidheart.compat.jade;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.voxelindustry.voidheart.common.ley.anchor.WroughtAnchorTile;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * The name a wrought anchor goes by on the travel map, which the block already syncs.
 */
public class WroughtAnchorProvider implements IBlockComponentProvider
{
    private static final Identifier WROUGHT_ANCHOR = Identifier.fromNamespaceAndPath(MODID, "wrought_anchor");

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config)
    {
        if (accessor.getBlockEntity() instanceof WroughtAnchorTile anchor && !anchor.getName().isEmpty())
            tooltip.add(Component.literal(anchor.getName()).withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    @Override
    public Identifier getUid()
    {
        return WROUGHT_ANCHOR;
    }
}
