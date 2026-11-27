package net.voxelindustry.voidheart.common.content.mausoleum;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.voxelindustry.voidheart.common.world.mausoleum.MausoleumAddress;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MausoleumStateTest
{
    private static GlobalPos HOME;

    @BeforeAll
    static void bootstrap()
    {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        HOME = GlobalPos.of(ResourceKey.create(Registries.DIMENSION, Identifier.withDefaultNamespace("overworld")), BlockPos.ZERO);
    }

    @Test
    void aGateKeepsItsMausoleum()
    {
        var state = new MausoleumState();
        int first = state.indexFor(7, HOME, 0);
        int second = state.indexFor(9, HOME, 0);
        assertNotEquals(first, second);
        assertEquals(first, state.indexFor(7, HOME, 90));
        assertNotEquals(state.open(HOME, 0), state.open(HOME, 0), "Mausoleums opened by command share an index");
    }

    @Test
    void noIndexIsGivenPastTheLastAddress()
    {
        var state = new MausoleumState();
        for (int gate = 1; gate <= MausoleumAddress.MAX_INDEX + 1; gate++)
            assertEquals(gate - 1, state.indexFor(gate, HOME, 0));

        assertEquals(-1, state.indexFor(MausoleumAddress.MAX_INDEX + 5, HOME, 0), "A new gate was given an index with no address");
        assertEquals(-1, state.open(HOME, 0), "A command was given an index with no address");
        assertEquals(MausoleumAddress.MAX_INDEX + 1, state.size(), "A refused gate was still recorded");
        assertEquals(12, state.indexFor(13, HOME, 0), "A known gate lost its mausoleum once the row was full");
    }
}
