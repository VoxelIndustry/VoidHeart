package net.voxelindustry.voidheart.compat.rei;

import net.minecraft.resources.Identifier;
import net.voxelindustry.voidheart.common.content.essence.EssenceRegistry;
import net.voxelindustry.voidheart.common.content.essence.EssenceType;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Essence costs in REI: displays carry them by id, and categories write each in a color readable on REI's panels.
 */
final class ReiAspects
{
    private ReiAspects()
    {
    }

    static int color(EssenceType type)
    {
        return switch (type.identifier().getPath())
        {
            case "stillness" -> 0xFF7FB8FF;
            case "fiery" -> 0xFFFF8A3D;
            case "suffering" -> 0xFFD8606A;
            case "contravoid" -> 0xFFB48CE6;
            default -> 0xFF999999;
        };
    }

    static Map<Identifier, Integer> ids(Map<EssenceType, Integer> aspects)
    {
        var ids = new LinkedHashMap<Identifier, Integer>();
        aspects.forEach((type, amount) -> ids.put(type.identifier(), amount));
        return ids;
    }

    static Map<EssenceType, Integer> types(Map<Identifier, Integer> ids)
    {
        var aspects = new LinkedHashMap<EssenceType, Integer>();
        ids.forEach((id, amount) ->
        {
            if (EssenceRegistry.ESSENCE_REGISTRY.containsKey(id))
                aspects.put(EssenceRegistry.ESSENCE_REGISTRY.getValue(id), amount);
        });
        return aspects;
    }
}
