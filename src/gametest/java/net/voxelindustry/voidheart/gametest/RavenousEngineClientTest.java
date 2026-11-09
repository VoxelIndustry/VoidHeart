package net.voxelindustry.voidheart.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/**
 * Stub for W5 Ravenous engine, which fills it with: a relay feeding the engine, the engine feeding the creative well's field, and a consumer. Run it alone with -PgameTest=RavenousEngine.
 */
public class RavenousEngineClientTest implements FabricClientGameTest
{
    @Override
    public void runTest(ClientGameTestContext context)
    {
        EnergyTestKit.expectRegistered("ravenous_engine");
    }
}
