package net.voxelindustry.voidheart.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/**
 * Stub for W9 Rift, which fills it with: a tear opened, a ward weakened inside its seep, and a wound opened and closed. Run it alone with -PgameTest=VoidTear.
 */
public class VoidTearClientTest implements FabricClientGameTest
{
    @Override
    public void runTest(ClientGameTestContext context)
    {
        EnergyTestKit.expectRegistered("void_tear", "void_wound");
    }
}
