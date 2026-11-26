package net.voxelindustry.voidheart.common.content.heart;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.voxelindustry.steamlayer.network.tilesync.TileSyncElement;
import net.voxelindustry.voidheart.common.world.pocket.VoidHeartData;

import java.util.List;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * @param attunement how far the owner has given themself to the void, which sets the flexion and the heart's form
 * @param twinBeats  whether the owner's second life is ready, as last seen while they were here
 * @param grownAt    when each form past the dormant heart first showed, in game time
 */
public record VoidHeartDataSyncElement(VoidHeartData heartData, int attunement, boolean twinBeats, List<Long> grownAt) implements TileSyncElement<VoidHeartDataSyncElement>
{
    public static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath(MODID, "void_heart_data_sync");

    public static final Codec<VoidHeartDataSyncElement> CODEC = RecordCodecBuilder.create((instance) ->
            instance.group(VoidHeartData.CODEC.fieldOf("data").forGetter(VoidHeartDataSyncElement::heartData),
                            Codec.INT.optionalFieldOf("attunement", 0).forGetter(VoidHeartDataSyncElement::attunement),
                            Codec.BOOL.optionalFieldOf("twinBeats", true).forGetter(VoidHeartDataSyncElement::twinBeats),
                            Codec.LONG.listOf().optionalFieldOf("grownAt", List.of()).forGetter(VoidHeartDataSyncElement::grownAt))
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
