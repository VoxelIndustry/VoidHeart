package net.voxelindustry.voidheart.client.particle;

import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.texture.Sprite;
import net.minecraft.util.math.random.Random;

import static java.lang.Math.min;

public class SimpleSpriteProvider implements SpriteProvider
{
    private final Sprite[] sprites;

    private final float bias;

    public SimpleSpriteProvider(float bias, Sprite... sprites)
    {
        this.sprites = sprites;
        this.bias = bias;
    }

    public SimpleSpriteProvider(Sprite... sprites)
    {
        this(1, sprites);
    }


    @Override
    public Sprite getSprite(int age, int maxAge)
    {
        return this.sprites[min(this.sprites.length - 1, (int) (age * bias / (maxAge / this.sprites.length)))];
    }

    @Override
    public Sprite getSprite(Random random)
    {
        return this.sprites[random.nextInt(this.sprites.length)];
    }
}
