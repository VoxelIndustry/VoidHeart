package net.voxelindustry.voidheart.data.provider;

import net.voxelindustry.voidheart.common.content.essence.EssenceRegistry;
import net.voxelindustry.voidheart.common.content.essence.EssenceType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The essence each late altar rite drinks, by recipe name. Every rite a tier-3 research unlocks drinks one aspect by its family: Fiery for war and
 * furnaces, Stillness for wards and sight, Suffering for flesh, Contravoid for what the reactor's research brings.
 */
final class AltarDraughts
{
    private final Map<String, Map<EssenceType, Integer>> draughts = new HashMap<>();
    private final Set<String> used = new HashSet<>();

    AltarDraughts()
    {
        fiery(2_000, "athanor", "void_armor_base", "ravenous_digging",
                "sin_infusion_pride", "sin_infusion_wrath", "sin_infusion_gluttony", "sin_infusion_sloth", "sin_infusion_greed");
        fiery(6_000, "void_armor");
        fiery(1_500, "armor_battery_cell");
        fiery(3_000, "armor_exoskeleton_power");

        stillness(2_000, "sin_cleansing", "greater_shielding_apparatus", "armor_spectral_lens", "officiant", "memoriam_lens");
        stillness(6_000, "supreme_shielding_apparatus");
        stillness(1_000, "apparatus_ember_ward", "apparatus_arrow_ward", "apparatus_blast_ward", "apparatus_arcane_ward", "apparatus_grudge_ward",
                "armor_night_vision");
        stillness(1_500, "apparatus_pulse_core", "apparatus_blink_core", "apparatus_stasis_core", "apparatus_mending_core", "hoard_coffer", "fold", "aspect_fold");
        stillness(3_000, "shoulder_eye", "azurium_spike", "fouled_azurium_spike", "armor_levitation",
                "wrought_anchor");
        stillness(4_000, "deep_siphon");

        suffering(4_000, "void_marrow", "heart_cradle");
        suffering(3_000, "meat_proxy", "fissure_seed");

        contravoid(4_000, "klein_star");
        contravoid(3_000, "void_blight_crossbow", "void_blight_rapier", "void_blight_scythe", "void_blight_spear");
        contravoid(2_000, "contravoid_reactor_core", "tesseract");
        contravoid(3_000, "large_rubrum_cell");
    }

    private void fiery(int amount, String... recipes)
    {
        put(EssenceRegistry.FIERY, amount, recipes);
    }

    private void stillness(int amount, String... recipes)
    {
        put(EssenceRegistry.STILLNESS, amount, recipes);
    }

    private void suffering(int amount, String... recipes)
    {
        put(EssenceRegistry.SUFFERING, amount, recipes);
    }

    private void contravoid(int amount, String... recipes)
    {
        put(EssenceRegistry.CONTRAVOID, amount, recipes);
    }

    private void put(EssenceType aspect, int amount, String... recipes)
    {
        for (var recipe : recipes)
        {
            if (draughts.put(recipe, Map.of(aspect, amount)) != null)
                throw new IllegalStateException("Altar recipe " + recipe + " is given two draughts");
        }
    }

    /**
     * @return what the named altar recipe drinks, empty for an earlier rite
     */
    Map<EssenceType, Integer> of(String recipe)
    {
        var draught = draughts.get(recipe);
        if (draught == null)
            return Map.of();
        used.add(recipe);
        return draught;
    }

    /**
     * Fails datagen when a draught names a recipe that was never made, such as one renamed since.
     */
    void checkAllUsed()
    {
        var unused = new HashSet<>(draughts.keySet());
        unused.removeAll(used);
        if (!unused.isEmpty())
            throw new IllegalStateException("Altar draughts name recipes that do not exist: " + unused);
    }
}
