package net.voxelindustry.voidheart.compat.iris;

import lombok.experimental.UtilityClass;
import net.fabricmc.loader.api.FabricLoader;

@UtilityClass
public class IrisCompat
{
    public static final String IRIS = "iris";

    public static boolean useIris()
    {
        return FabricLoader.getInstance().isModLoaded(IRIS);
    }
}
