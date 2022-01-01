package net.voxelindustry.voidheart.compat.immportal;

import lombok.experimental.UtilityClass;
import net.fabricmc.loader.api.FabricLoader;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;

@UtilityClass
public class ImmersivePortalCompat
{
    public static final String IMMERSIVE_PORTALS = "imm_ptl_core";

    public boolean areWeRenderedByPortal()
    {
        if (!useImmersivePortal())
            return false;

        return PortalRendering.isRendering();
    }

    public static boolean useImmersivePortal()
    {
        return FabricLoader.getInstance().isModLoaded(IMMERSIVE_PORTALS);
    }
}
