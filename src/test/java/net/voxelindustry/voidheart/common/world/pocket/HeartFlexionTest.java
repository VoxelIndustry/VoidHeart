package net.voxelindustry.voidheart.common.world.pocket;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HeartFlexionTest
{
    @Test
    void eachRiteAfterTheHeart_addsOneFlexion()
    {
        var data = VoidHeartData.create(new BlockPos(0, 64, 0));

        assertThat(data.maxFlexion(1)).isEqualTo(3);
        assertThat(data.maxFlexion(2)).isEqualTo(4);
        assertThat(data.maxFlexion(3)).isEqualTo(5);
        assertThat(data.maxFlexion(4)).isEqualTo(6);
    }

    @Test
    void ownerNotYetAttuned_stillHasTheHeartsFlexion()
    {
        assertThat(VoidHeartData.create(BlockPos.ZERO).maxFlexion(0)).isEqualTo(3);
    }

    @Test
    void savedBeforeTheRites_keepsWhatTheCommandGaveAsABonus()
    {
        var plain = parse("{\"pocketPos\":[0,64,0],\"maxFlexion\":3}");
        var raised = parse("{\"pocketPos\":[0,64,0],\"maxFlexion\":8}");
        var lowered = parse("{\"pocketPos\":[0,64,0],\"maxFlexion\":1}");

        assertThat(plain.bonusFlexion()).isZero();
        assertThat(raised.maxFlexion(1)).isEqualTo(8);
        assertThat(raised.maxFlexion(3)).isEqualTo(10);
        assertThat(lowered.maxFlexion(1)).isEqualTo(1);
    }

    @Test
    void bonusBelowZero_neverLeavesANegativeMax()
    {
        var data = VoidHeartData.create(BlockPos.ZERO);
        data.bonusFlexion(-10);

        assertThat(data.maxFlexion(4)).isZero();
    }

    @Test
    void savesTheBonus_notAMax()
    {
        var data = VoidHeartData.create(BlockPos.ZERO);
        data.bonusFlexion(2);

        var saved = VoidHeartData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow().getAsJsonObject();

        assertThat(saved.has("maxFlexion")).isFalse();
        assertThat(saved.get("bonusFlexion").getAsInt()).isEqualTo(2);
        assertThat(VoidHeartData.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow().maxFlexion(2)).isEqualTo(6);
    }

    @Test
    void usedFlexion_runsOutAtTheRitesMax()
    {
        var data = parse("{\"pocketPos\":[0,64,0],\"bonusFlexion\":0,\"flexionCountWithoutPortals\":4}");

        assertThat(data.noFlexionLeft(1)).isTrue();
        assertThat(data.noFlexionLeft(2)).isTrue();
        assertThat(data.noFlexionLeft(3)).isFalse();
    }

    private static VoidHeartData parse(String json)
    {
        return VoidHeartData.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
    }
}
