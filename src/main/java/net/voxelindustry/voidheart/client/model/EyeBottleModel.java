package net.voxelindustry.voidheart.client.model;

import net.minecraft.util.Identifier;
import net.voxelindustry.voidheart.common.content.eyebottle.EyeBottleTile;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class EyeBottleModel extends DefaultedBlockGeoModel<EyeBottleTile>
{
    public EyeBottleModel()
    {
        super(new Identifier(MODID, "eye_bottle_animated"));
    }
}
