package net.voxelindustry.voidheart.client.particle;

import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * Look of an experience orb on a particle: one of the smaller orbs of the vanilla orb sheet, shimmering green to yellow.
 */
final class ExperienceOrbLook
{
    static final int FULL_BRIGHT = 15728880;

    // The orb sheet holds 4 by 4 orbs, from the smallest to the largest
    private static final int SHEET_SIZE = 4;
    private static final int MAX_ICON   = 6;

    private final int   icon;
    private final float colorOffset;

    ExperienceOrbLook(RandomSource random)
    {
        icon = random.nextInt(MAX_ICON + 1);
        colorOffset = random.nextFloat() * Mth.TWO_PI;
    }

    float quadSize()
    {
        return 0.07F + 0.015F * icon;
    }

    /**
     * Same shimmer as experience orbs.
     */
    void applyColor(SingleQuadParticle particle, int age)
    {
        var phase = (age + colorOffset) / 2F;
        particle.setColor((Mth.sin(phase) + 1) * 0.5F, 1F, (Mth.sin(phase + 4.1887903F) + 1) * 0.1F);
    }

    float u0(TextureAtlasSprite sprite)
    {
        return Mth.lerp((icon % SHEET_SIZE) / (float) SHEET_SIZE, sprite.getU0(), sprite.getU1());
    }

    float u1(TextureAtlasSprite sprite)
    {
        return Mth.lerp((icon % SHEET_SIZE + 1) / (float) SHEET_SIZE, sprite.getU0(), sprite.getU1());
    }

    float v0(TextureAtlasSprite sprite)
    {
        return Mth.lerp((icon / SHEET_SIZE) / (float) SHEET_SIZE, sprite.getV0(), sprite.getV1());
    }

    float v1(TextureAtlasSprite sprite)
    {
        return Mth.lerp((icon / SHEET_SIZE + 1) / (float) SHEET_SIZE, sprite.getV0(), sprite.getV1());
    }
}
