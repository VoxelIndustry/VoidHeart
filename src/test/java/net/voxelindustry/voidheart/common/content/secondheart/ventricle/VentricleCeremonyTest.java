package net.voxelindustry.voidheart.common.content.secondheart.ventricle;

import com.mojang.serialization.JsonOps;
import net.voxelindustry.voidheart.common.content.secondheart.ventricle.VentricleCeremony.Conditions;
import net.voxelindustry.voidheart.common.content.secondheart.ventricle.VentricleCeremony.Event;
import net.voxelindustry.voidheart.common.content.secondheart.ventricle.VentricleCeremony.Ingredient;
import net.voxelindustry.voidheart.common.content.secondheart.ventricle.VentricleCeremony.Phase;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static net.voxelindustry.voidheart.common.content.secondheart.ventricle.VentricleBalance.*;
import static org.assertj.core.api.Assertions.assertThat;

class VentricleCeremonyTest
{
    private static final UUID CELEBRANT = UUID.randomUUID();
    private static final UUID OTHER     = UUID.randomUUID();

    private static final Conditions RUNNING   = new Conditions(true, true, true, true, false);
    private static final Conditions AWAY      = new Conditions(true, true, true, false, false);
    private static final Conditions DEAD      = new Conditions(true, true, true, false, true);
    private static final Conditions UNPOWERED = new Conditions(true, false, true, true, false);
    private static final Conditions BROKEN    = new Conditions(false, true, true, true, false);

    private static VentricleCeremony offeredAll()
    {
        var ceremony = new VentricleCeremony();
        for (var ingredient : Ingredient.values())
            ceremony.offer(CELEBRANT, ingredient, 64);
        ceremony.takeExperience(EXPERIENCE);
        return ceremony;
    }

    private static List<Event> run(VentricleCeremony ceremony, Conditions conditions, int ticks)
    {
        var events = new ArrayList<Event>();
        for (int i = 0; i < ticks; i++)
            events.addAll(ceremony.tick(conditions));
        return events;
    }

    @Test
    void offer_takesOnlyWhatIsNeededAndBindsTheCelebrant()
    {
        var ceremony = new VentricleCeremony();
        assertThat(ceremony.offer(CELEBRANT, Ingredient.MEMORIAM, 20)).isEqualTo(MEMORIAM_SHARDS);
        assertThat(ceremony.phase()).isEqualTo(Phase.OFFERING);
        assertThat(ceremony.celebrant()).isEqualTo(CELEBRANT);
        assertThat(ceremony.offer(CELEBRANT, Ingredient.MEMORIAM, 1)).isZero();
        assertThat(ceremony.offer(OTHER, Ingredient.WRAITH_INK, 4)).isZero();
    }

    @Test
    void takeExperience_onlyOnceEveryItemIsIn_thenKindles()
    {
        var ceremony = new VentricleCeremony();
        ceremony.offer(CELEBRANT, Ingredient.KERNACIAN_CORE, 1);
        assertThat(ceremony.takeExperience(500)).isZero();
        for (var ingredient : Ingredient.values())
            ceremony.offer(CELEBRANT, ingredient, 64);
        assertThat(ceremony.takeExperience(1000)).isEqualTo(1000);
        assertThat(ceremony.phase()).isEqualTo(Phase.OFFERING);
        assertThat(ceremony.takeExperience(1000)).isEqualTo(EXPERIENCE - 1000);
        assertThat(ceremony.phase()).isEqualTo(Phase.KINDLING);
    }

    @Test
    void withdraw_givesTheOfferingBackBeforeTheRiteOnly()
    {
        var ceremony = new VentricleCeremony();
        ceremony.offer(CELEBRANT, Ingredient.WRAITH_INK, 3);
        assertThat(ceremony.withdraw(OTHER)).isEmpty();
        assertThat(ceremony.withdraw(CELEBRANT)).containsEntry(Ingredient.WRAITH_INK, 3);
        assertThat(ceremony.phase()).isEqualTo(Phase.IDLE);

        assertThat(offeredAll().withdraw(CELEBRANT)).isEmpty();
    }

    @Test
    void kindling_onlyRunsWhilePoweredAndWhole()
    {
        var ceremony = offeredAll();
        run(ceremony, UNPOWERED, 100);
        run(ceremony, BROKEN, 100);
        assertThat(ceremony.kindling()).isZero();
        assertThat(run(ceremony, RUNNING, KINDLING_TICKS)).containsExactly(Event.KINDLED);
        assertThat(ceremony.phase()).isEqualTo(Phase.BEATING);
    }

    @Test
    void beating_givenFullRun_thenBeatsCallsEveryWaveAndStops()
    {
        var ceremony = offeredAll();
        run(ceremony, RUNNING, KINDLING_TICKS);
        var events = run(ceremony, RUNNING, BEATING_TICKS);
        assertThat(events.stream().filter(e -> e == Event.BEAT)).hasSize(BEATING_TICKS / BEAT_TICKS);
        assertThat(events.stream().filter(e -> e == Event.WAVE)).hasSize(WAVES);
        assertThat(events.getLast()).isEqualTo(Event.STOPPED);
        assertThat(ceremony.phase()).isEqualTo(Phase.IDLE);
        assertThat(ceremony.celebrant()).isNull();
    }

    @Test
    void beating_givenCelebrantSteppedOff_thenPausesWithoutLoss()
    {
        var ceremony = offeredAll();
        run(ceremony, RUNNING, KINDLING_TICKS + 500);
        int beating = ceremony.beating();
        run(ceremony, AWAY, 1000);
        assertThat(ceremony.beating()).isEqualTo(beating);
        run(ceremony, RUNNING, 10);
        assertThat(ceremony.beating()).isEqualTo(beating + 10);
    }

    @Test
    void beating_givenCelebrantDied_thenStartsOverKeepingTheOffering()
    {
        var ceremony = offeredAll();
        run(ceremony, RUNNING, KINDLING_TICKS + 900);
        assertThat(ceremony.waves()).isPositive();
        assertThat(run(ceremony, DEAD, 5)).containsExactly(Event.RESTARTED);
        assertThat(ceremony.beating()).isZero();
        assertThat(ceremony.waves()).isZero();
        assertThat(ceremony.phase()).isEqualTo(Phase.BEATING);
        assertThat(ceremony.celebrant()).isEqualTo(CELEBRANT);
        run(ceremony, RUNNING, 10);
        assertThat(ceremony.beating()).isEqualTo(10);
    }

    @Test
    void wantsPower_onlyWhenItWouldMoveOn()
    {
        var ceremony = offeredAll();
        assertThat(ceremony.wantsPower(true, false)).isTrue();
        assertThat(ceremony.wantsPower(false, true)).isFalse();
        run(ceremony, RUNNING, KINDLING_TICKS);
        assertThat(ceremony.wantsPower(true, false)).isFalse();
        assertThat(ceremony.wantsPower(true, true)).isTrue();
        assertThat(new VentricleCeremony().wantsPower(true, true)).isFalse();
    }

    @Test
    void codec_roundTripsARiteUnderWay()
    {
        var ceremony = offeredAll();
        run(ceremony, RUNNING, KINDLING_TICKS + 700);
        var json = VentricleCeremony.CODEC.encodeStart(JsonOps.INSTANCE, ceremony).getOrThrow();
        var read = VentricleCeremony.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertThat(read.phase()).isEqualTo(Phase.BEATING);
        assertThat(read.celebrant()).isEqualTo(CELEBRANT);
        assertThat(read.beating()).isEqualTo(ceremony.beating());
        assertThat(read.waves()).isEqualTo(ceremony.waves());
    }

    @Test
    void codec_givenUnpaidRiteSavedAsBeating_thenOnlyAnOffering()
    {
        var ceremony = new VentricleCeremony();
        ceremony.offer(CELEBRANT, Ingredient.MEMORIAM, 3);
        var json = VentricleCeremony.CODEC.encodeStart(JsonOps.INSTANCE, ceremony).getOrThrow().getAsJsonObject();
        json.addProperty("phase", "BEATING");
        json.addProperty("beating", 99999);
        var read = VentricleCeremony.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertThat(read.phase()).isEqualTo(Phase.OFFERING);
        assertThat(read.beating()).isZero();
        assertThat(read.offered(Ingredient.MEMORIAM)).isEqualTo(3);
    }
}
