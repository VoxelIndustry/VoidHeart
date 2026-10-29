package net.voxelindustry.voidheart.compat.modmenu;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.voxelindustry.voidheart.common.config.VoidHeartConfig;

public class VoidHeartModMenuPlugin implements ModMenuApi
{
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory()
    {
        return parent -> VoidHeartConfig.HANDLER.generateGui().generateScreen(parent);
    }
}
