package net.voxelindustry.voidheart.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/**
 * Stub for W2 Rubrum storage, which fills it with: a construct formed, charged, broken and replaced, and the cell round trip. Run it alone with -PgameTest=RubrumStorage.
 */
public class RubrumStorageClientTest implements FabricClientGameTest
{
    @Override
    public void runTest(ClientGameTestContext context)
    {
        EnergyTestKit.expectRegistered("rubrum_construct_core", "rubrum_construct_frame", "rubrum_cell", "cell_socket");
    }
}
