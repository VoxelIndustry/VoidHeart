package net.voxelindustry.voidheart.compat.jade;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.common.content.altar.VoidAltarTile;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * The essence a rite running on the altar drinks, read from what the altar syncs to clients.
 */
public class AltarProvider implements IBlockComponentProvider
{
    private static final Identifier ALTAR = Identifier.fromNamespaceAndPath(VoidHeart.MODID, "altar");

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config)
    {
        if (!(accessor.getBlockEntity() instanceof VoidAltarTile altar) || !altar.isCrafting())
            return;

        altar.getDraught().forEach((aspect, needed) ->
        {
            int drunk = Math.min(needed, altar.getDrunk().getOrDefault(aspect, 0));
            tooltip.add(Component.translatable(aspect.translatableName())
                    .append(": " + drunk + "/" + needed)
                    .withStyle(drunk >= needed ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
        });
    }

    @Override
    public Identifier getUid()
    {
        return ALTAR;
    }
}
