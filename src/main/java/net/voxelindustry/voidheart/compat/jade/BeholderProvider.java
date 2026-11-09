package net.voxelindustry.voidheart.compat.jade;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.voxelindustry.voidheart.common.config.VoidHeartConfig;
import net.voxelindustry.voidheart.common.content.beholder.BeholderTile;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * How far a Beholder sees on the ley that reaches it, or that it is blind.
 */
public class BeholderProvider implements IServerDataProvider<BlockAccessor>
{
    private static final Identifier BEHOLDER = Identifier.fromNamespaceAndPath(MODID, "beholder");

    @Override
    public Identifier getUid()
    {
        return BEHOLDER;
    }

    @Override
    public void appendServerData(CompoundTag tag, BlockAccessor accessor)
    {
        if (!(accessor.getBlockEntity() instanceof BeholderTile beholder))
            return;

        tag.putInt("supply", Math.round(beholder.getSupply() * 100));
        tag.putInt("range", Math.round(VoidHeartConfig.get().beholderLowRange * beholder.sight()));
    }

    public static class Client implements IBlockComponentProvider
    {
        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config)
        {
            var data = accessor.getServerData();
            if (!data.contains("range"))
                return;

            int range = data.getIntOr("range", 0);
            if (range <= 0)
                tooltip.add(Component.translatable(MODID + ".jade.beholder.blind").withStyle(ChatFormatting.RED));
            else
                tooltip.add(Component.translatable(MODID + ".jade.beholder.sight", range, data.getIntOr("supply", 0))
                        .withStyle(data.getIntOr("supply", 0) >= 100 ? ChatFormatting.GRAY : ChatFormatting.YELLOW));
        }

        @Override
        public Identifier getUid()
        {
            return BEHOLDER;
        }
    }
}
