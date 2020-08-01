package net.voxelindustry.voidheart.client.model.monolith;

import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class VoidMonolithSpriteManager
{
    private static final SpriteIdentifier[] SPRITE_IDENTIFIERS =
            {
                    getSpriteIdentifier("monolith_side"),
                    getSpriteIdentifier("monolith_side_lower"),
                    getSpriteIdentifier("monolith_side_middle"),
                    getSpriteIdentifier("monolith_side_upper")
            };

    private static final Map<Direction, Sprite> frameSpriteCache = new HashMap<>();

    private static Function<SpriteIdentifier, Sprite> spriteGetter;

    public static void updateSpriteGetter(Function<SpriteIdentifier, Sprite> newGetter)
    {
        spriteGetter = newGetter;
    }

    public static SpriteIdentifier[] getSpriteIdentifiers()
    {
        return SPRITE_IDENTIFIERS;
    }

    public static Sprite getFrameSprite(Direction dir)
    {
        return frameSpriteCache.computeIfAbsent(dir, direction ->
        {
            switch (direction)
            {
                case DOWN:
                    return spriteGetter.apply(SPRITE_IDENTIFIERS[3]);
                case UP:
                    return spriteGetter.apply(SPRITE_IDENTIFIERS[1]);
                case NORTH:
                    return spriteGetter.apply(SPRITE_IDENTIFIERS[2]);
                default:
                    return spriteGetter.apply(SPRITE_IDENTIFIERS[0]);
            }
        });
    }

    private static SpriteIdentifier getSpriteIdentifier(String from)
    {
        return new SpriteIdentifier(SpriteAtlasTexture.BLOCK_ATLAS_TEX, new Identifier(MODID, "block/monolith/" + from));
    }
}