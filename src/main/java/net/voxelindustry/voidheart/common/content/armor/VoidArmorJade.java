package net.voxelindustry.voidheart.common.content.armor;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.config.IPluginConfig;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Jade for the Void Armor base: whether a frame stands on it and how charged it is. The base syncs its frame, so the client reads it directly.
 */
public final class VoidArmorJade
{
    private static final Identifier UID = Identifier.fromNamespaceAndPath(MODID, "void_armor_base");

    private VoidArmorJade()
    {
    }

    public static void registerClient(IWailaClientRegistration registration)
    {
        registration.registerBlockComponent(new Client(), VoidArmorBaseBlock.class);
    }

    public static class Client implements IBlockComponentProvider
    {
        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config)
        {
            if (!(accessor.getBlockEntity() instanceof VoidArmorBaseTile base))
                return;
            if (!base.hasFrame())
            {
                tooltip.add(Component.translatable(MODID + ".jade.void_armor_base.empty"));
                return;
            }
            tooltip.add(Component.translatable(MODID + ".jade.void_armor_base.docked", Math.round(VoidArmorItem.fill(base.frame()) * 100)));
        }

        @Override
        public Identifier getUid()
        {
            return UID;
        }
    }
}
