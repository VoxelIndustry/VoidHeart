package net.voxelindustry.voidheart.compat.jade;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.voxelindustry.voidheart.common.content.tormenter.MalignantTormenterTile;
import net.voxelindustry.voidheart.common.content.tormenter.TormenterSouls;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class MalignantTormenterProvider implements IServerDataProvider<BlockAccessor>
{
    private static final Identifier MALIGNANT_TORMENTER = Identifier.fromNamespaceAndPath(MODID, "malignant_tormenter");

    @Override
    public Identifier getUid()
    {
        return MALIGNANT_TORMENTER;
    }

    @Override
    public void appendServerData(CompoundTag tag, BlockAccessor accessor)
    {
        if (accessor.getBlockEntity() instanceof MalignantTormenterTile tormenter)
        {
            var souls = tormenter.souls();
            tag.putInt("tormenterSouls", souls == null ? 0 : souls.souls());
            if (souls != null)
                tag.putString("tormenterMob", souls.type().getDescriptionId());
        }
    }

    public static class Client implements IBlockComponentProvider
    {
        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config)
        {
            var data = accessor.getServerData();
            if (!data.contains("tormenterSouls"))
                return;

            var souls = data.getIntOr("tormenterSouls", 0);
            if (souls == 0)
            {
                tooltip.add(Component.translatable(MODID + ".jade.tormenter.empty"));
                return;
            }

            var mob = Component.translatable(data.getStringOr("tormenterMob", ""));
            tooltip.add(souls >= TormenterSouls.REQUIRED
                    ? Component.translatable(MODID + ".jade.tormenter.sated", mob)
                    : Component.translatable(MODID + ".jade.tormenter.hungering", mob, souls, TormenterSouls.REQUIRED));
        }

        @Override
        public Identifier getUid()
        {
            return MALIGNANT_TORMENTER;
        }
    }
}
