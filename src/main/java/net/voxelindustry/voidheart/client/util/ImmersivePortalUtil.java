package net.voxelindustry.voidheart.client.util;

import lombok.experimental.UtilityClass;
import net.voxelindustry.voidheart.VoidHeart;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;

@UtilityClass
public class ImmersivePortalUtil
{
    public boolean areWeRenderedByPortal()
    {
        if (!VoidHeart.useImmersivePortal())
            return false;

        return PortalRendering.isRendering();
    }
}
