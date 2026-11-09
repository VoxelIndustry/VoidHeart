package net.voxelindustry.voidheart.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/**
 * Stub for W4 Standing stones and wards, which fills it with: a spur from a real line to a ward, where Corruption.raise comes out slower inside the ward. Run it alone with -PgameTest=LeyStone.
 */
public class LeyStoneClientTest implements FabricClientGameTest
{
    @Override
    public void runTest(ClientGameTestContext context)
    {
        EnergyTestKit.expectRegistered("ley_stone", "ley_ward", "ley_grower");
    }
}
