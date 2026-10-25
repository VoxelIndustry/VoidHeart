package net.voxelindustry.voidheart.client.particle;

import static java.lang.Math.min;

import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.RandomSource;

public class SimpleSpriteProvider implements SpriteSet
{
    private final TextureAtlasSprite[] sprites;

    private final float bias;

    public SimpleSpriteProvider(float bias, TextureAtlasSprite... sprites)
    {
        this.sprites = sprites;
        this.bias = bias;
    }

    public SimpleSpriteProvider(TextureAtlasSprite... sprites)
    {
        this(1, sprites);
    }


    @Override
    public TextureAtlasSprite get(int age, int maxAge)
    {
        return this.sprites[min(this.sprites.length - 1, (int) (age * bias / (maxAge / this.sprites.length)))];
    }

    @Override
    public TextureAtlasSprite get(RandomSource random)
    {
        return this.sprites[random.nextInt(this.sprites.length)];
    }
}
