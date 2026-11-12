package net.voxelindustry.voidheart.common.content.underway;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.voxelindustry.voidheart.common.world.underway.UnderwaySection;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UnderwaySecurityCodecTest
{
    private static final String SAVED = """
            {"sections": [
              {"x": 3, "z": -2, "section": 1, "cell": true, "charge": 4200, "wake": 2, "alarm": 150, "destroyed": [5, 1]},
              {"x": 3, "z": -2, "section": 0, "cell": false, "charge": 0, "wake": 1, "alarm": 0, "destroyed": []},
              {"x": 0, "z": 0, "section": 9, "cell": true, "charge": 10, "wake": 0, "alarm": 0, "destroyed": []}
            ]}""";

    @Test
    void aSectionSavedMidAlarmComesBackAlarmedAndPowered()
    {
        var loaded = UnderwaySecurity.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(SAVED)).getOrThrow();
        var again = UnderwaySecurity.CODEC.parse(JsonOps.INSTANCE, UnderwaySecurity.CODEC.encodeStart(JsonOps.INSTANCE, loaded).getOrThrow()).getOrThrow();

        for (var security : new UnderwaySecurity[] { loaded, again })
        {
            var living = new UnderwaySecurity.Key(3, -2, UnderwaySection.LIVING);
            assertThat(security.hasCell(living)).isTrue();
            assertThat(security.charge(living)).isEqualTo(4200);
            assertThat(security.isAlarmed(living)).as("the alarm that sounded when it was saved, which is what holds its doors").isTrue();
            assertThat(security.wrecks(living)).containsExactly(1, 5);

            var entry = new UnderwaySecurity.Key(3, -2, UnderwaySection.ENTRY);
            assertThat(security.hasCell(entry)).isFalse();
            assertThat(security.isAlarmed(entry)).isFalse();
            assertThat(security.wrecks(entry)).isEmpty();
        }
        assertThat(UnderwaySecurity.CODEC.encodeStart(JsonOps.INSTANCE, again).getOrThrow().getAsJsonObject().getAsJsonArray("sections"))
                .as("a section that names no level of a fort is dropped").hasSize(2);
    }
}
