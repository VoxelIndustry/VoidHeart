package net.voxelindustry.voidheart.compat.jade;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.voxelindustry.voidheart.common.content.beam.BeamFocusBlock;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class BeamFocusProvider implements IBlockComponentProvider
{
    private static final Identifier BEAM_FOCUS = Identifier.fromNamespaceAndPath(MODID, "beam_focus");

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config)
    {
        var name = BeamFocusBlock.getOutputFace(accessor.getBlockState()).getName();
        var direction = Character.toUpperCase(name.charAt(0)) + name.substring(1);

        tooltip.add(Component.translatable("voidheart.jade.beam_focus.emits", direction));
    }

    @Override
    public Identifier getUid()
    {
        return BEAM_FOCUS;
    }
}
