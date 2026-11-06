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
    private static final String SHOULDER_EYE = "shoulder_eye";
    private static final String RESEARCH = "research";
    private static final String ARMILLARY = "armillary";

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

    @AutoGen(category = SHOULDER_EYE)
    @FloatSlider(min = 0, max = 20, step = 0.5F, format = "%.1f")
    @SerialEntry(value = "shoulder_eye_beam_damage", comment = "Damage dealt by each beam of a Shoulder Eye")
    public float shoulderEyeBeamDamage = 4;

    @AutoGen(category = SHOULDER_EYE)
    @IntSlider(min = 4, max = 32, step = 1)
    @SerialEntry(value = "shoulder_eye_range", comment = "Distance from its owner within which a Shoulder Eye fires at enemies")
    public int shoulderEyeRange = 16;

    @AutoGen(category = SHOULDER_EYE)
    @IntSlider(min = 5, max = 600, step = 5)
    @SerialEntry(value = "shoulder_eye_regen_seconds", comment = "Seconds a slain Shoulder Eye takes to grow back before returning to its owner")
    public int shoulderEyeRegenSeconds = 60;

    @AutoGen(category = RESEARCH)
    @TickBox
    @SerialEntry(value = "research_gating", comment = "Refuse the recipes named by research until that research is done")
    public boolean researchGating = true;

    @AutoGen(category = RESEARCH)
    @FloatSlider(min = 0.01F, max = 5, step = 0.01F, format = "%.2f")
    @SerialEntry(value = "knowledge_base_rate", comment = "Knowledge per second a repository gathers, before research")
    public float knowledgeBaseRate = 0.1F;

    @AutoGen(category = RESEARCH)
    @IntSlider(min = 10, max = 1000, step = 10)
    @SerialEntry(value = "knowledge_base_cap", comment = "Knowledge a player can hold, before research")
    public int knowledgeBaseCap = 30;

    @AutoGen(category = RESEARCH)
    @IntSlider(min = 2, max = 16, step = 1)
    @SerialEntry(value = "knowledge_column_radius", comment = "How many blocks from a repository, in every direction, its Knowledge Columns count")
    public int knowledgeColumnRadius = 8;

    @AutoGen(category = RESEARCH)
    @FloatSlider(min = 1, max = 100, step = 1, format = "%.0f")
    @SerialEntry(value = "insight_base_cost", comment = "Knowledge the first insight costs")
    public float insightBaseCost = 10;

    @AutoGen(category = RESEARCH)
    @FloatSlider(min = 1, max = 2, step = 0.01F, format = "%.2f")
    @SerialEntry(value = "insight_cost_growth", comment = "How much pricier each insight is than the one before")
    public float insightCostGrowth = 1.2F;

    @AutoGen(category = RESEARCH)
    @FloatSlider(min = 0, max = 1, step = 0.01F, format = "%.2f")
    @SerialEntry(value = "knowledge_first_tablet_chance", comment = "Chance for a broken monolith block to drop a Knowledge Tablet, until the player has held one")
    public float knowledgeFirstTabletChance = 0.3F;

    @AutoGen(category = RESEARCH)
    @FloatSlider(min = 0, max = 1, step = 0.01F, format = "%.2f")
    @SerialEntry(value = "knowledge_tablet_chance", comment = "Chance for a broken monolith block to drop a Knowledge Tablet, once the player has held one")
    public float knowledgeTabletChance = 0.05F;

    @AutoGen(category = ARMILLARY)
    @IntSlider(min = 1, max = 60, step = 1)
    @SerialEntry(value = "armillary_scan_minutes", comment = "Minutes an armillary takes to find each new bubble, while its chunk is loaded")
    public int armillaryScanMinutes = 5;

    @AutoGen(category = ARMILLARY)
    @IntSlider(min = 1, max = 10, step = 1)
    @SerialEntry(value = "armillary_max_bubbles", comment = "How many bubbles an armillary can hold on its chart at once")
    public int armillaryMaxBubbles = 5;

    @AutoGen(category = ARMILLARY)
    @IntSlider(min = 1, max = 240, step = 1)
    @SerialEntry(value = "armillary_bubble_fade_minutes", comment = "Minutes a bubble nobody targets stays on the chart")
    public int armillaryBubbleFadeMinutes = 30;

    @AutoGen(category = ARMILLARY)
    @IntSlider(min = 1, max = 60, step = 1)
    @SerialEntry(value = "armillary_search_min_minutes", comment = "Minutes the search for a path takes at the shallowest depth")
    public int armillarySearchMinMinutes = 2;

    @AutoGen(category = ARMILLARY)
    @IntSlider(min = 1, max = 60, step = 1)
    @SerialEntry(value = "armillary_search_max_minutes", comment = "Minutes the search for a path takes at the deepest depth")
    public int armillarySearchMaxMinutes = 8;

    @AutoGen(category = ARMILLARY)
    @IntSlider(min = 1, max = 240, step = 1)
    @SerialEntry(value = "armillary_connection_min_minutes", comment = "Shortest time a connection to a dungeon can hold, in minutes")
    public int armillaryConnectionMinMinutes = 30;

    @AutoGen(category = ARMILLARY)
    @IntSlider(min = 1, max = 240, step = 1)
    @SerialEntry(value = "armillary_connection_max_minutes", comment = "Longest time a connection to a dungeon can hold, in minutes")
    public int armillaryConnectionMaxMinutes = 40;

    @AutoGen(category = ARMILLARY)
    @IntSlider(min = 1, max = 60, step = 1)
    @SerialEntry(value = "armillary_daedalus_search_minutes", comment = "Minutes the search for a path down to the Daedalus takes")
    public int armillaryDaedalusSearchMinutes = 10;

    @AutoGen(category = ARMILLARY)
    @IntSlider(min = 0, max = 64, step = 1)
    @SerialEntry(value = "armillary_daedalus_pearls", comment = "Void pearls it takes to open the way into the Daedalus")
    public int armillaryDaedalusPearls = 8;

    @AutoGen(category = ARMILLARY)
    @TickBox
    @SerialEntry(value = "armillary_camera_shake", comment = "Shake the camera while a connection collapses")
    public boolean armillaryCameraShake = true;

    public static void load()
    {
        HANDLER.load();
        get().knowledgeBaseRate = Mth.clamp(get().knowledgeBaseRate, 0.01F, 5);
        get().knowledgeBaseCap = Mth.clamp(get().knowledgeBaseCap, 10, 1000);
        get().knowledgeColumnRadius = Mth.clamp(get().knowledgeColumnRadius, 2, 16);
        get().insightBaseCost = Mth.clamp(get().insightBaseCost, 1, 100);
        get().insightCostGrowth = Mth.clamp(get().insightCostGrowth, 1, 2);
        get().knowledgeFirstTabletChance = Mth.clamp(get().knowledgeFirstTabletChance, 0, 1);
        get().knowledgeTabletChance = Mth.clamp(get().knowledgeTabletChance, 0, 1);
        get().armillaryScanMinutes = Mth.clamp(get().armillaryScanMinutes, 1, 60);
        get().armillaryMaxBubbles = Mth.clamp(get().armillaryMaxBubbles, 1, 10);
        get().armillaryBubbleFadeMinutes = Mth.clamp(get().armillaryBubbleFadeMinutes, 1, 240);
        get().armillarySearchMinMinutes = Mth.clamp(get().armillarySearchMinMinutes, 1, 60);
        get().armillarySearchMaxMinutes = Mth.clamp(get().armillarySearchMaxMinutes, 1, 60);
        get().armillaryConnectionMinMinutes = Mth.clamp(get().armillaryConnectionMinMinutes, 1, 240);
        get().armillaryConnectionMaxMinutes = Mth.clamp(get().armillaryConnectionMaxMinutes, 1, 240);
        get().armillaryDaedalusSearchMinutes = Mth.clamp(get().armillaryDaedalusSearchMinutes, 1, 60);
        get().armillaryDaedalusPearls = Mth.clamp(get().armillaryDaedalusPearls, 0, 64);
        get().leyLineTravelAnchorInactiveRate = Mth.clamp(get().leyLineTravelAnchorInactiveRate, 0, 1);
        get().oculusTableFollowRange = Mth.clamp(get().oculusTableFollowRange, 16, 512);
        get().oculusTableSightRange = Mth.clamp(get().oculusTableSightRange, 0, 64);
        get().oculusTableMixedSeconds = Mth.clamp(get().oculusTableMixedSeconds, 0, 30);
        get().beholderLowRange = Mth.clamp(get().beholderLowRange, 16, 256);
        get().beholderHighRange = Mth.clamp(get().beholderHighRange, 8, get().beholderLowRange);
        get().shoulderEyeBeamDamage = Mth.clamp(get().shoulderEyeBeamDamage, 0, 20);
        get().shoulderEyeRange = Mth.clamp(get().shoulderEyeRange, 4, 32);
        get().shoulderEyeRegenSeconds = Mth.clamp(get().shoulderEyeRegenSeconds, 5, 600);
    }

    public static VoidHeartConfig get()
    {
        return HANDLER.instance();
    }
}
