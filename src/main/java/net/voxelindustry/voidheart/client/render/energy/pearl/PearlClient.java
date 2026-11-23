package net.voxelindustry.voidheart.client.render.energy.pearl;

import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.special.SpecialModelRenderers;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.voxelindustry.voidheart.client.render.energy.superfluid.TesseractItemRender;
import net.voxelindustry.voidheart.common.energy.pearl.HangingPearlTile;
import net.voxelindustry.voidheart.common.energy.pearl.PearlContent;
import net.voxelindustry.voidheart.common.energy.pearl.PearlTremor;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Client startup registrar for the Collapsing Rubrum Pearls, called once by EnergyClientSetup. Besides the renderer it gives each burst of a
 * pearl's tremor a faint chime, now and then rather than every time.
 */
public final class PearlClient
{
    private static final float CHIME_CHANCE = 0.35F;
    private static final float CHIME_VOLUME = 0.12F;

    private PearlClient()
    {
    }

    public static void register()
    {
        BlockEntityRenderers.register(PearlContent.HANGING_PEARL_TILE, HangingPearlRender::new);
        SpecialModelRenderers.ID_MAPPER.put(Identifier.fromNamespaceAndPath(MODID, "rubrum_pearl"), PearlItemRender.Unbaked.MAP_CODEC);
        SpecialModelRenderers.ID_MAPPER.put(Identifier.fromNamespaceAndPath(MODID, "tesseract"), TesseractItemRender.Unbaked.MAP_CODEC);
        HangingPearlTile.clientTick = PearlClient::chime;
    }

    private static void chime(HangingPearlTile pearl)
    {
        var level = pearl.getLevel();
        if (level == null)
            return;
        var time = level.getGameTime();
        var now = PearlTremor.at(pearl.seed(), pearl.choirSeed(), pearl.sync(1), time).intensity() > 0;
        var before = PearlTremor.at(pearl.seed(), pearl.choirSeed(), pearl.sync(0), time - 1).intensity() > 0;
        if (!now || before || level.getRandom().nextFloat() >= CHIME_CHANCE)
            return;
        var pos = pearl.getBlockPos();
        level.playLocalSound(pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, CHIME_VOLUME, 0.45F + level.getRandom().nextFloat() * 0.2F, false);
    }
}
