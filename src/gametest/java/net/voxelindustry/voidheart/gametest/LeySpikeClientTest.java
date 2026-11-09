package net.voxelindustry.voidheart.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/**
 * Stub for W8 Spike, which fills it with: a spike on a real node, with taint rising and a collector filling. Run it alone with -PgameTest=LeySpike.
 */
public class LeySpikeClientTest implements FabricClientGameTest
{
    @Override
    public void runTest(ClientGameTestContext context)
    {
        EnergyTestKit.expectRegistered("azurium_spike");
    }
}
