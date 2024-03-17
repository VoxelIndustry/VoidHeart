package net.voxelindustry.voidheart.common.content.heart;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Identifier;
import net.voxelindustry.steamlayer.network.tilesync.TileSyncElement;
import net.voxelindustry.voidheart.common.world.pocket.VoidHeartData;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public record VoidHeartDataSyncElement(VoidHeartData heartData) implements TileSyncElement<VoidHeartDataSyncElement>
{
    public static final Identifier IDENTIFIER = new Identifier(MODID, "altar_crafting_sync");

    public static final Codec<VoidHeartDataSyncElement> CODEC = RecordCodecBuilder.create((instance) ->
            instance.group(VoidHeartData.CODEC.fieldOf("data").forGetter(VoidHeartDataSyncElement::heartData))
                    .apply(instance, VoidHeartDataSyncElement::new));

    @Override
    public Identifier getIdentifier()
    {
        return IDENTIFIER;
    }

    @Override
    public Codec<VoidHeartDataSyncElement> getCodec()
    {
        return CODEC;
    }
}
