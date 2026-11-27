package net.voxelindustry.voidheart.compat.jade;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.voxelindustry.voidheart.common.content.shroud.ShroudAltarTile;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * What lies on a shroud altar, read from the client's copy, which is synced for drawing anyway. An empty altar says nothing: its outline is the hint.
 */
public class ShroudAltarProvider implements IBlockComponentProvider
{
    private static final Identifier SHROUD_ALTAR = Identifier.fromNamespaceAndPath(MODID, "shroud_altar");

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config)
    {
        if (accessor.getBlockEntity() instanceof ShroudAltarTile altar && altar.holdsInk())
            tooltip.add(Component.translatable("voidheart.jade.shroud_altar.offered", altar.ink().getHoverName()));
    }

    @Override
    public Identifier getUid()
    {
        return SHROUD_ALTAR;
    }
}
