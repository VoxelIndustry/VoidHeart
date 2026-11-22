package net.voxelindustry.voidheart.common.content.works;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plans on the mod's real recipes: a Void Pearl comes down to ender pearls and obsidian through the crafting table, and the Void Armor frame merges
 * every pearl and ingot it needs into one node each.
 */
class WorksRealTreeTest
{
    private static JsonWorks works;

    @BeforeAll
    static void read() throws IOException
    {
        works = new JsonWorks();
    }

    @Test
    void voidPearl_comesFromEnderPearlsAndObsidianThroughTheTable()
    {
        var plan = WorksPlan.plan(works::recipesFor, Map.of("minecraft:ender_pearl", 4L, "minecraft:obsidian", 8L), "voidheart:void_pearl", 2);

        assertThat(plan.isComplete()).as("lacking %s", plan.lacking()).isTrue();
        assertThat(plan.nodes().get("voidheart:obsidian_shard").recipe().station()).isEqualTo(StationKind.TABLE);
        assertThat(plan.nodes().get("voidheart:void_pearl").recipe().station()).isEqualTo(StationKind.ALTAR);
        assertThat(plan.nodes().get("minecraft:obsidian").needed()).isEqualTo(4);
    }

    @Test
    void voidArmor_drinksItsFieryAndListsWhatIsLacking()
    {
        var plan = WorksPlan.plan(works::recipesFor, Map.of(), "voidheart:void_armor", 1);

        assertThat(plan.essence()).containsEntry("voidheart:fiery", 6000L);
        assertThat(plan.lacking()).containsKeys("voidheart:kernacian_core", "minecraft:netherite_ingot", "voidheart:rubrum_shard");
        assertThat(plan.nodes().get("voidheart:seething_steel_ingot").recipe().station()).isEqualTo(StationKind.FORGE);
    }
}
