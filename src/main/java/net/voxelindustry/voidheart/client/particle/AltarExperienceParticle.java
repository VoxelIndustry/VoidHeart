package net.voxelindustry.voidheart.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.joml.Vector3fc;

/**
 * Experience orb drawn from an experience skull into a Void Altar.
 */
public class AltarExperienceParticle extends AltarStreamParticle
{
    private final ExperienceOrbLook look;

    public AltarExperienceParticle(ClientLevel world, double x, double y, double z, double destinationX, double destinationY, double destinationZ,
                                   Vector3fc firstPointBezier, Vector3fc secondPointBezier, TextureAtlasSprite sprite)
    {
        super(world, x, y, z, destinationX, destinationY, destinationZ, firstPointBezier, secondPointBezier, sprite);

        look = new ExperienceOrbLook(random);
        quadSize = look.quadSize();
        look.applyColor(this, age);
    }

    @Override
    public void tick()
    {
        super.tick();
        if (isAlive())
            look.applyColor(this, age);
    }

    @Override
    protected float getU0()
    {
        return look.u0(sprite);
    }

    @Override
    protected float getU1()
    {
        return look.u1(sprite);
    }

    @Override
    protected float getV0()
    {
        return look.v0(sprite);
    }

    @Override
    protected float getV1()
    {
        return look.v1(sprite);
    }

    @Override
    protected int getLightCoords(float partialTicks)
    {
        return ExperienceOrbLook.FULL_BRIGHT;
    }

    @Override
    protected Layer getLayer()
    {
        return Layer.TRANSLUCENT;
    }
}
