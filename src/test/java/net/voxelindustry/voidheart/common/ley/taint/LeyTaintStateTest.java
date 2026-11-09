package net.voxelindustry.voidheart.common.ley.taint;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.SavedDataStorage;
import net.voxelindustry.voidheart.common.energy.FoundationBalance;
import net.voxelindustry.voidheart.common.ley.Ley;
import net.voxelindustry.voidheart.common.ley.TaintStage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LeyTaintStateTest
{
    private static final long NODE = 0x1234_5678_9ABCL;
    private static final long OTHER = -77L;

    @BeforeAll
    static void bootstrap()
    {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void cleanNodesHoldNoTaint()
    {
        var state = new LeyTaintState();

        assertThat(state.taint(NODE)).isZero();
        assertThat(state.tainted()).isEmpty();
        assertThat(state.isDirty()).isFalse();
    }

    @Test
    void taintAddsUpAndIsKeptInRange()
    {
        var state = new LeyTaintState();

        state.add(NODE, 30);
        state.add(NODE, 30);
        assertThat(state.taint(NODE)).isEqualTo(60);

        state.add(NODE, 1_000);
        assertThat(state.taint(NODE)).isEqualTo(FoundationBalance.MAX_TAINT);

        state.add(NODE, -1_000);
        assertThat(state.taint(NODE)).isZero();
        assertThat(state.tainted()).doesNotContain(NODE);
    }

    @Test
    void healingAnUntaintedNodeChangesNothing()
    {
        var state = new LeyTaintState();
        state.add(NODE, -5);
        state.add(NODE, Float.NaN);

        assertThat(state.taint(NODE)).isZero();
        assertThat(state.isDirty()).isFalse();
    }

    @Test
    void changesMarkTheStateDirty()
    {
        var state = new LeyTaintState();
        state.set(NODE, 10);

        assertThat(state.isDirty()).isTrue();
    }

    @Test
    void codecKeepsEveryNode()
    {
        var state = new LeyTaintState();
        state.set(NODE, 12.5F);
        state.set(OTHER, 99);

        var json = LeyTaintState.CODEC.encodeStart(JsonOps.INSTANCE, state).getOrThrow();
        var loaded = LeyTaintState.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();

        assertThat(loaded.taint(NODE)).isEqualTo(12.5F);
        assertThat(loaded.taint(OTHER)).isEqualTo(99);
        assertThat(loaded.tainted()).containsExactlyInAnyOrder(NODE, OTHER);
    }

    @Test
    void staleSavedValuesAreClamped()
    {
        var json = JsonParser.parseString("{\"nodes\":[{\"node\":1,\"taint\":500},{\"node\":2,\"taint\":-3},{\"node\":3,\"taint\":40}]}");
        var loaded = LeyTaintState.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();

        assertThat(loaded.taint(1)).isEqualTo(FoundationBalance.MAX_TAINT);
        assertThat(loaded.taint(2)).isZero();
        assertThat(loaded.taint(3)).isEqualTo(40);
        assertThat(loaded.tainted()).containsExactlyInAnyOrder(1L, 3L);
    }

    @Test
    void defaultFlowStoresTaintInTheOverworld()
    {
        var state = new LeyTaintState();
        var storage = mock(SavedDataStorage.class);
        when(storage.computeIfAbsent(LeyTaintState.TYPE)).thenReturn(state);
        var overworld = mock(ServerLevel.class);
        when(overworld.getDataStorage()).thenReturn(storage);
        var server = mock(MinecraftServer.class);
        when(server.overworld()).thenReturn(overworld);
        // Asked from another level, the taint still lands in the overworld
        var level = mock(ServerLevel.class);
        when(level.getServer()).thenReturn(server);

        Ley.addTaint(level, NODE, FoundationBalance.FESTERING_FROM);

        assertThat(state.taint(NODE)).isEqualTo(FoundationBalance.FESTERING_FROM);
        assertThat(Ley.taint(level, NODE)).isEqualTo(FoundationBalance.FESTERING_FROM);
        assertThat(Ley.stage(level, NODE)).isEqualTo(TaintStage.FESTERING);
        assertThat(Ley.stage(level, OTHER)).isEqualTo(TaintStage.CLEAN);
    }
}
