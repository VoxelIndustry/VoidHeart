package net.voxelindustry.voidheart.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/**
 * Stub for W11 Deep siphon, which fills it with: a siphon in the Daedalus powering a consumer next to the armillary. Run it alone with -PgameTest=DeepSiphon.
 */
public class DeepSiphonClientTest implements FabricClientGameTest
{
    @Override
    public void runTest(ClientGameTestContext context)
    {
        EnergyTestKit.expectRegistered("deep_siphon");
    }
}
