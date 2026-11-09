package net.voxelindustry.voidheart.gametest;

import net.minecraft.core.registries.BuiltInRegistries;

import static net.voxelindustry.voidheart.gametest.GameTestKit.expect;
import static net.voxelindustry.voidheart.gametest.GameTestKit.id;

/**
 * Helpers for the energy packages' game tests, kept apart from GameTestKit so the packages never edit a shared file.
 */
public final class EnergyTestKit
{
    private EnergyTestKit()
    {
    }

    /**
     * Each id names an item, which every block has too.
     */
    public static void expectRegistered(String... ids)
    {
        for (var path : ids)
            expect(BuiltInRegistries.ITEM.containsKey(id(path)), "voidheart:" + path + " should be registered");
    }
}
