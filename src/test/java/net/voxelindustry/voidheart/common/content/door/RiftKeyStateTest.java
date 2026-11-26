package net.voxelindustry.voidheart.common.content.door;

import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.voxelindustry.voidheart.common.world.pocket.VoidHeartData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RiftKeyStateTest
{
    private static final BlockPos DOOR = new BlockPos(10, 70, 10);
    private static final BlockPos OTHER_DOOR = new BlockPos(12, 70, 10);

    @BeforeAll
    static void bootstrap()
    {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static GlobalPos entry(int x)
    {
        return GlobalPos.of(ResourceKey.create(Registries.DIMENSION, Identifier.withDefaultNamespace("overworld")), new BlockPos(x, 64, 0));
    }

    @Test
    void riftDoorHoldsOneFlexionUntilRemoved()
    {
        // Two flexions: the base with one taken back by command
        var heart = new VoidHeartData(BlockPos.ZERO);
        heart.bonusFlexion(2 - VoidHeartData.BASE_FLEXION);
        heart.addRiftDoor(DOOR);
        heart.addRiftDoor(DOOR);
        assertThat(heart.currentFlexionCount()).isEqualTo(1);
        assertThat(heart.noFlexionLeft(1)).isFalse();

        heart.addRiftDoor(OTHER_DOOR);
        assertThat(heart.noFlexionLeft(1)).isTrue();

        assertThat(heart.removeRiftDoor(DOOR)).isTrue();
        assertThat(heart.removeRiftDoor(DOOR)).isFalse();
        assertThat(heart.currentFlexionCount()).isEqualTo(1);
    }

    @Test
    void riftDoorsSurviveSaving()
    {
        var heart = new VoidHeartData(BlockPos.ZERO);
        heart.addRiftDoor(DOOR);

        var json = VoidHeartData.CODEC.encodeStart(JsonOps.INSTANCE, heart).getOrThrow();
        var loaded = VoidHeartData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertThat(loaded.holdsRiftDoor(DOOR)).isTrue();
        assertThat(loaded.currentFlexionCount()).isEqualTo(1);

        json.getAsJsonObject().remove("riftDoors");
        assertThat(VoidHeartData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow().riftDoors()).isEmpty();
    }

    @Test
    void doorKeepsOneEntryAtATime()
    {
        var pairs = new RiftPairs();
        assertThat(pairs.open(DOOR, entry(0))).isEmpty();
        assertThat(pairs.open(DOOR, entry(5))).contains(entry(0));

        assertThat(pairs.doorOf(entry(0))).isEmpty();
        assertThat(pairs.doorOf(entry(5))).contains(DOOR);
        assertThat(pairs.entryOf(DOOR)).contains(entry(5));

        assertThat(pairs.close(DOOR)).contains(entry(5));
        assertThat(pairs.isEmpty()).isTrue();
    }

    @Test
    void entryBelongsToOneDoor()
    {
        var pairs = new RiftPairs();
        pairs.open(DOOR, entry(0));
        pairs.open(OTHER_DOOR, entry(0));

        assertThat(pairs.entryOf(DOOR)).isEmpty();
        assertThat(pairs.doorOf(entry(0))).contains(OTHER_DOOR);
    }

    @Test
    void pairsSurviveSaving()
    {
        var pairs = new RiftPairs();
        pairs.open(DOOR, entry(3));

        var json = RiftPairs.CODEC.encodeStart(JsonOps.INSTANCE, pairs).getOrThrow();
        var loaded = RiftPairs.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertThat(loaded.entryOf(DOOR)).contains(entry(3));
    }
}
