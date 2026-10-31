package net.voxelindustry.voidheart.common.config;

import dev.isxander.yacl3.config.v2.api.ConfigClassHandler;
import dev.isxander.yacl3.config.v2.api.SerialEntry;
import dev.isxander.yacl3.config.v2.api.autogen.AutoGen;
import dev.isxander.yacl3.config.v2.api.autogen.FloatSlider;
import dev.isxander.yacl3.config.v2.api.autogen.IntSlider;
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
    private static final String OCULUS_TABLE = "oculus_table";
    private static final String BEHOLDER = "beholder";

    @AutoGen(category = LEY_LINES)
    @TickBox
    @SerialEntry(value = "ley_line_travel_anchor_generate", comment = "Generate Old Anchors on well connected ley nodes")
    public boolean leyLineTravelAnchorGenerate = true;

    @AutoGen(category = LEY_LINES)
    @FloatSlider(min = 0, max = 1, step = 0.05F, format = "%.2f")
    @SerialEntry(value = "ley_line_travel_anchor_inactive_rate", comment = "Rate of Old Anchors found dormant, from 0 to 1")
    public float leyLineTravelAnchorInactiveRate = 0.5F;

    @AutoGen(category = OCULUS_TABLE)
    @IntSlider(min = 16, max = 512, step = 16)
    @SerialEntry(value = "oculus_table_follow_range", comment = "Horizontal distance from an Oculus Table within which players can be followed")
    public int oculusTableFollowRange = 128;

    @AutoGen(category = OCULUS_TABLE)
    @IntSlider(min = 0, max = 64, step = 4)
    @SerialEntry(value = "oculus_table_sight_range", comment = "Horizontal distance around an Oculus Table it sees by itself, at low resolution")
    public int oculusTableSightRange = 16;

    @AutoGen(category = OCULUS_TABLE)
    @IntSlider(min = 0, max = 30, step = 1)
    @SerialEntry(value = "oculus_table_mixed_seconds", comment = "Seconds an Oculus Table following a player shows both the surface and the hollows before settling on one, as they go under cover or come out")
    public int oculusTableMixedSeconds = 4;

    @AutoGen(category = BEHOLDER)
    @IntSlider(min = 16, max = 256, step = 8)
    @SerialEntry(value = "beholder_low_range", comment = "Horizontal distance within which a Beholder sees land and anonymous entities")
    public int beholderLowRange = 64;

    @AutoGen(category = BEHOLDER)
    @IntSlider(min = 8, max = 256, step = 8)
    @SerialEntry(value = "beholder_high_range", comment = "Horizontal distance within which a Beholder makes out player names and mob types")
    public int beholderHighRange = 32;

    public static void load()
    {
        HANDLER.load();
        get().leyLineTravelAnchorInactiveRate = Mth.clamp(get().leyLineTravelAnchorInactiveRate, 0, 1);
        get().oculusTableFollowRange = Mth.clamp(get().oculusTableFollowRange, 16, 512);
        get().oculusTableSightRange = Mth.clamp(get().oculusTableSightRange, 0, 64);
        get().oculusTableMixedSeconds = Mth.clamp(get().oculusTableMixedSeconds, 0, 30);
        get().beholderLowRange = Mth.clamp(get().beholderLowRange, 16, 256);
        get().beholderHighRange = Mth.clamp(get().beholderHighRange, 8, get().beholderLowRange);
    }

    public static VoidHeartConfig get()
    {
        return HANDLER.instance();
    }
}
