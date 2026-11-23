package net.voxelindustry.voidheart.common.content.hoard;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import org.jetbrains.annotations.Nullable;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Where hoards can be reached from: nowhere in a dimension whose type is in {@link #FORBIDDEN}.
 */
public final class HoardReach
{
    public static final TagKey<DimensionType> FORBIDDEN = TagKey.create(Registries.DIMENSION_TYPE, Identifier.fromNamespaceAndPath(MODID, "hoard_forbidden"));

    private HoardReach()
    {
    }

    public static boolean forbidden(@Nullable Level level)
    {
        return level == null || level.dimensionTypeRegistration().is(FORBIDDEN);
    }
}
