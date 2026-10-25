package net.voxelindustry.voidheart.client.model;

import net.voxelindustry.voidheart.common.content.eyebottle.EyeBottleTile;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

import net.minecraft.resources.ResourceLocation;

public class EyeBottleModel extends DefaultedBlockGeoModel<EyeBottleTile>
{
    public EyeBottleModel()
    {
        super(new ResourceLocation(MODID, "eye_bottle_animated"));
    }
}
