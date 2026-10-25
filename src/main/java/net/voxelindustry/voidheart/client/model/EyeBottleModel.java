package net.voxelindustry.voidheart.client.model;

import net.voxelindustry.voidheart.common.content.eyebottle.EyeBottleTile;
import com.geckolib.model.DefaultedBlockGeoModel;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

import net.minecraft.resources.Identifier;

public class EyeBottleModel extends DefaultedBlockGeoModel<EyeBottleTile>
{
    public EyeBottleModel()
    {
        super(Identifier.fromNamespaceAndPath(MODID, "eye_bottle_animated"));
    }
}
