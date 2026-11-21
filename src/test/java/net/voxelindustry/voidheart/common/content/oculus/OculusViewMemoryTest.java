package net.voxelindustry.voidheart.common.content.oculus;

import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.crystal.BundleSize;
import net.voxelindustry.voidheart.common.content.crystal.CrystalRecordings;
import net.voxelindustry.voidheart.common.energy.grid.DeepReach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OculusViewMemoryTest
{
    private static final ResourceKey<net.minecraft.world.level.Level> DUNGEONS = ResourceKey.create(Registries.DIMENSION,
            Identifier.fromNamespaceAndPath("voidheart", "dungeons"));

    @Test
    void aMemorySetOnATableIsSavedWithItsView()
    {
        var memory = new OculusMemory(new CrystalRecordings.Ref(UUID.randomUUID(), DUNGEONS), BundleSize.MEDIUM);
        var view = OculusView.DEFAULT.zoomIn().remembering(Optional.of(memory));

        var saved = OculusView.CODEC.encodeStart(JsonOps.INSTANCE, view).getOrThrow();
        var loaded = OculusView.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow();
        assertThat(loaded).isEqualTo(view);
        assertThat(loaded.zoomOut().memory()).as("changing the view keeps the memory").contains(memory);
        assertThat(loaded.remembering(Optional.empty()).memory()).isEmpty();
    }

    @Test
    void aViewSavedBeforeMemoriesLoadsWithoutOne()
    {
        var old = new OculusView(3, Optional.empty(), true, false, false);
        var saved = OculusView.CODEC.encodeStart(JsonOps.INSTANCE, old).getOrThrow();
        assertThat(OculusView.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow().memory()).isEmpty();
    }

    @Test
    void aDeepReachCoversWhatIsLeftOfItsFieldPastTheFarPortal()
    {
        var reach = new DeepReach.Reach(DUNGEONS, BlockPos.ZERO, new BlockPos(4, 0, 0), Vec3.ZERO, DUNGEONS, new BlockPos(100, 64, 100), 6, 0);
        assertThat(reach.covers(new BlockPos(105, 64, 100))).isTrue();
        assertThat(reach.covers(new BlockPos(107, 64, 100))).isFalse();
    }
}
