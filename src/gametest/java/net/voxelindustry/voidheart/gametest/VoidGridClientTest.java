package net.voxelindustry.voidheart.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/**
 * Stub for W1 Void grid, which fills it with: a chain of three pylons feeding a consumer, and a capped hub. Run it alone with -PgameTest=VoidGrid.
 */
public class VoidGridClientTest implements FabricClientGameTest
{
    @Override
    public void runTest(ClientGameTestContext context)
    {
        EnergyTestKit.expectRegistered("azurium_pylon");
    }
}
