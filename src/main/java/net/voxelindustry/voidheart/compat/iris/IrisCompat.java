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

    /**
     * Whether a shader pack draws the world now, which may not run the mod's own entity pipelines.
     */
    public static boolean shaderPackInUse()
    {
        return useIris() && ShaderPack.inUse();
    }

    /** Kept apart so Iris's classes load only when Iris is there. */
    private static final class ShaderPack
    {
        static boolean inUse()
        {
            return net.irisshaders.iris.api.v0.IrisApi.getInstance().isShaderPackInUse();
        }
    }
}
