package net.voxelindustry.voidheart.common.config;

import dev.isxander.yacl3.config.v2.api.ConfigClassHandler;
import dev.isxander.yacl3.config.v2.api.SerialEntry;
import dev.isxander.yacl3.config.v2.api.autogen.AutoGen;
import dev.isxander.yacl3.config.v2.api.autogen.FloatSlider;
import dev.isxander.yacl3.config.v2.api.autogen.TickBox;
import dev.isxander.yacl3.config.v2.api.serializer.GsonConfigSerializerBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class VoidHeartConfig
{
    public static final ConfigClassHandler<VoidHeartConfig> HANDLER = ConfigClassHandler.createBuilder(VoidHeartConfig.class)
            .id(Identifier.fromNamespaceAndPath(MODID, "config"))
            .serializer(config -> GsonConfigSerializerBuilder.create(config)
                    .setPath(FabricLoader.getInstance().getConfigDir().resolve(MODID + ".json5"))
                    .setJson5(true)
                    .build())
            .build();

    private static final String LEY_LINES = "ley_lines";

    @AutoGen(category = LEY_LINES)
    @TickBox
    @SerialEntry(value = "ley_line_travel_anchor_generate", comment = "Generate Old Anchors on well connected ley nodes")
    public boolean leyLineTravelAnchorGenerate = true;

    @AutoGen(category = LEY_LINES)
    @FloatSlider(min = 0, max = 1, step = 0.05F, format = "%.2f")
    @SerialEntry(value = "ley_line_travel_anchor_inactive_rate", comment = "Rate of Old Anchors found dormant, from 0 to 1")
    public float leyLineTravelAnchorInactiveRate = 0.5F;

    public static void load()
    {
        HANDLER.load();
        get().leyLineTravelAnchorInactiveRate = Mth.clamp(get().leyLineTravelAnchorInactiveRate, 0, 1);
    }

    public static VoidHeartConfig get()
    {
        return HANDLER.instance();
    }
}
