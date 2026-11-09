package net.voxelindustry.voidheart.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/**
 * Stub for W10 Contravoid reactor, which fills it with: lit, one input starved and recovered, starved again into a failure, and a safe shutdown. Run it alone with -PgameTest=ContravoidReactor.
 */
public class ContravoidReactorClientTest implements FabricClientGameTest
{
    @Override
    public void runTest(ClientGameTestContext context)
    {
        EnergyTestKit.expectRegistered("contravoid_reactor_core", "reactor_containment", "reactor_intake");
    }
}
