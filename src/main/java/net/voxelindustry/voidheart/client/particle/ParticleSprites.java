package net.voxelindustry.voidheart.client.particle;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class ParticleSprites
{
    private static final ItemStackRenderState SCRATCH_RENDER_STATE = new ItemStackRenderState();

    /**
     * Sprite used by the breaking particles of an item.
     */
    public static TextureAtlasSprite itemSprite(ItemStack stack, ClientLevel level, RandomSource random)
    {
        Minecraft.getInstance().getItemModelResolver().updateForTopItem(SCRATCH_RENDER_STATE, stack, ItemDisplayContext.GROUND, level, null, 0);
        var material = SCRATCH_RENDER_STATE.pickParticleMaterial(random);
        return material != null ? material.sprite() : Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.ITEMS).missingSprite();
    }
}
