package net.voxelindustry.voidheart.common.content.works;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WorksQueueTest
{
    private static WorksQueue.Task task(String recipe, StationKind station, long crafts)
    {
        return new WorksQueue.Task(recipe, station, crafts);
    }

    @Test
    void order_givenTheSameRecipeTwiceInARow_thenMergesThem()
    {
        var queue = new WorksQueue();

        queue.order("pearls", 2, List.of(task("shard", StationKind.TABLE, 2), task("shard", StationKind.TABLE, 1), task("pearl", StationKind.ALTAR, 2)));

        assertThat(queue.jobs().getFirst().tasks()).containsExactly(task("shard", StationKind.TABLE, 3), task("pearl", StationKind.ALTAR, 2));
    }

    @Test
    void claim_givenAStationOfAnotherKind_thenSkipsTheTask()
    {
        var queue = new WorksQueue();
        queue.order("pearls", 1, List.of(task("shard", StationKind.TABLE, 1), task("pearl", StationKind.ALTAR, 1)));

        var claim = queue.claim(StationKind.ALTAR, any -> true);

        assertThat(claim).isEqualTo(new WorksQueue.Claim(1, "pearl", StationKind.ALTAR));
    }

    @Test
    void claim_givenATaskThatCannotStart_thenTakesTheNextOne()
    {
        var queue = new WorksQueue();
        queue.order("first", 1, List.of(task("waiting", StationKind.ALTAR, 1)));
        queue.order("second", 1, List.of(task("ready", StationKind.ALTAR, 1)));

        var claim = queue.claim(StationKind.ALTAR, candidate -> candidate.recipe().equals("ready"));

        assertThat(claim.recipe()).isEqualTo("ready");
        assertThat(queue.jobs().get(0).tasks().getFirst().remaining()).isEqualTo(1);
        assertThat(queue.jobs().get(1).tasks().getFirst().remaining()).isZero();
    }

    @Test
    void claim_givenTheLastCraftOfAnOrder_thenTheOrderStaysWhileItIsWorked()
    {
        var queue = new WorksQueue();
        queue.order("pearl", 1, List.of(task("pearl", StationKind.ALTAR, 1)));

        queue.claim(StationKind.ALTAR, any -> true);

        assertThat(queue.jobs()).singleElement().extracting(job -> job.tasks().getFirst().working()).isEqualTo(1L);
        assertThat(queue.claim(StationKind.ALTAR, any -> true)).isNull();
    }

    @Test
    void finish_givenTheLastCraftOfAnOrder_thenTheOrderIsGone()
    {
        var queue = new WorksQueue();
        queue.order("pearl", 1, List.of(task("pearl", StationKind.ALTAR, 1)));
        var claim = queue.claim(StationKind.ALTAR, any -> true);

        queue.finish(claim);

        assertThat(queue.jobs()).isEmpty();
    }

    @Test
    void finish_givenCraftsStillToClaim_thenTheOrderStays()
    {
        var queue = new WorksQueue();
        queue.order("pearls", 2, List.of(task("pearl", StationKind.ALTAR, 2)));
        var claim = queue.claim(StationKind.ALTAR, any -> true);

        queue.finish(claim);

        assertThat(queue.jobs()).singleElement().extracting(job -> job.tasks().getFirst()).isEqualTo(task("pearl", StationKind.ALTAR, 1));
    }

    @Test
    void giveBack_givenTheOrderStillThere_thenTheCraftCountsAgain()
    {
        var queue = new WorksQueue();
        queue.order("pearls", 2, List.of(task("pearl", StationKind.ALTAR, 2)));
        var claim = queue.claim(StationKind.ALTAR, any -> true);

        queue.giveBack(claim);

        assertThat(queue.jobs().getFirst().tasks().getFirst()).isEqualTo(task("pearl", StationKind.ALTAR, 2));
    }

    @Test
    void order_givenTheLastOrderCancelled_thenNeverGivesItsIdAgain()
    {
        var queue = new WorksQueue();
        var first = queue.order("pearl", 1, List.of(task("pearl", StationKind.ALTAR, 1)));
        var claim = queue.claim(StationKind.ALTAR, any -> true);
        queue.cancel(first);

        var second = queue.order("pearl", 1, List.of(task("pearl", StationKind.ALTAR, 1)));
        queue.finish(claim);

        assertThat(second).isNotEqualTo(first);
        assertThat(queue.jobs()).singleElement().extracting(WorksQueue.Job::id).isEqualTo(second);
    }

    @Test
    void codec_givenAQueueSavedAsAListOfJobs_thenLoadsAndKeepsCountingUp()
    {
        var queue = new WorksQueue();
        queue.order("a", 1, List.of(task("a", StationKind.TABLE, 1)));
        var old = WorksQueue.Job.CODEC.listOf().encodeStart(com.mojang.serialization.JsonOps.INSTANCE, queue.jobs()).getOrThrow();

        var loaded = WorksQueue.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, old).getOrThrow();

        assertThat(loaded.order("b", 1, List.of(task("b", StationKind.TABLE, 1)))).isGreaterThan(1);
    }

    private static WorksQueue.Claim oldClaim(int job, String recipe, StationKind station)
    {
        // Claims saved before they had an era carry none
        var saved = WorksQueue.Claim.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, new WorksQueue.Claim(job, recipe, station)).getOrThrow()
                .getAsJsonObject();
        saved.remove("era");
        return WorksQueue.Claim.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, saved).getOrThrow();
    }

    @Test
    void codec_givenAnOldClaimOnAnOrderLoadedWithIt_thenItStillSettlesIt()
    {
        var queue = new WorksQueue();
        queue.order("pearl", 1, List.of(task("pearl", StationKind.ALTAR, 1)));
        queue.claim(StationKind.ALTAR, any -> true);
        var old = WorksQueue.Job.CODEC.listOf().encodeStart(com.mojang.serialization.JsonOps.INSTANCE, queue.jobs()).getOrThrow();
        var loaded = WorksQueue.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, old).getOrThrow();

        loaded.finish(oldClaim(1, "pearl", StationKind.ALTAR));

        assertThat(loaded.jobs()).isEmpty();
    }

    @Test
    void codec_givenAnOldClaimWithTheIdOfANewOrder_thenItNeverSettlesIt()
    {
        // An empty old queue next to a claim saved for a cancelled order 1001, the id the old gap would have given the next order
        var old = WorksQueue.Job.CODEC.listOf().encodeStart(com.mojang.serialization.JsonOps.INSTANCE, List.of()).getOrThrow();
        var loaded = WorksQueue.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, old).getOrThrow();
        var fresh = 0;
        while (fresh < 1001)
        {
            fresh = loaded.order("pearl", 1, List.of(task("pearl", StationKind.ALTAR, 1)));
            if (fresh < 1001)
                loaded.cancel(fresh);
        }
        loaded.claim(StationKind.ALTAR, any -> true);

        loaded.giveBack(oldClaim(1001, "pearl", StationKind.ALTAR));
        loaded.finish(oldClaim(1001, "pearl", StationKind.ALTAR));

        assertThat(roundTrip(loaded).jobs()).singleElement().satisfies(job ->
        {
            assertThat(job.id()).isEqualTo(1001);
            assertThat(job.tasks().getFirst().remaining()).isZero();
            assertThat(job.tasks().getFirst().working()).isEqualTo(1L);
        });
    }

    @Test
    void codec_givenAQueueSavedBeforeEras_thenOnlyItsOrdersTakeOldClaimsOnceLoadedAgain()
    {
        var queue = new WorksQueue();
        queue.order("a", 1, List.of(task("pearl", StationKind.ALTAR, 1)));
        queue.claim(StationKind.ALTAR, any -> true);
        var saved = WorksQueue.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, queue).getOrThrow().getAsJsonObject();
        saved.remove("legacy_below");
        var loaded = WorksQueue.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, saved).getOrThrow();
        var later = loaded.order("b", 1, List.of(task("pearl", StationKind.ALTAR, 1)));
        loaded.claim(StationKind.ALTAR, task -> true);

        var again = roundTrip(loaded);
        again.finish(oldClaim(later, "pearl", StationKind.ALTAR));
        again.finish(oldClaim(1, "pearl", StationKind.ALTAR));

        assertThat(again.jobs()).singleElement().extracting(WorksQueue.Job::id).isEqualTo(later);
    }

    @Test
    void codec_givenAnEmptyOldQueueAndAClaimOnACancelledOrder_thenTheClaimNeverSettlesANewOrder()
    {
        // Before the counter was saved: order 1 claimed, then cancelled, leaving an empty list and the Officiant's claim
        var queue = new WorksQueue();
        var cancelled = queue.order("pearl", 1, List.of(task("pearl", StationKind.ALTAR, 1)));
        var stale = queue.claim(StationKind.ALTAR, any -> true);
        queue.cancel(cancelled);
        var old = WorksQueue.Job.CODEC.listOf().encodeStart(com.mojang.serialization.JsonOps.INSTANCE, queue.jobs()).getOrThrow();

        var loaded = WorksQueue.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, old).getOrThrow();
        loaded.order("pearl", 1, List.of(task("pearl", StationKind.ALTAR, 1)));
        loaded.claim(StationKind.ALTAR, any -> true);
        loaded.finish(oldClaim(stale.job(), stale.recipe(), stale.station()));

        assertThat(loaded.jobs()).singleElement().extracting(job -> job.tasks().getFirst().working()).isEqualTo(1L);
    }

    private static WorksQueue roundTrip(WorksQueue queue)
    {
        var saved = WorksQueue.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, queue).getOrThrow();
        return WorksQueue.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, saved).getOrThrow();
    }

    @Test
    void codec_givenTheLastOrderCancelled_thenTheNextIdStillComesAfterItOnceLoaded()
    {
        var queue = new WorksQueue();
        queue.order("a", 1, List.of(task("a", StationKind.TABLE, 1)));
        var cancelled = queue.order("b", 1, List.of(task("b", StationKind.TABLE, 1)));
        queue.cancel(cancelled);

        var next = roundTrip(queue).order("c", 1, List.of(task("c", StationKind.TABLE, 1)));

        assertThat(next).isGreaterThan(cancelled);
    }

    @Test
    void codec_givenEveryOrderCancelled_thenTheIdsDoNotStartOverOnceLoaded()
    {
        var queue = new WorksQueue();
        var first = queue.order("a", 1, List.of(task("a", StationKind.TABLE, 1)));
        var second = queue.order("b", 1, List.of(task("b", StationKind.TABLE, 1)));
        queue.cancel(first);
        queue.cancel(second);

        var loaded = roundTrip(queue);

        assertThat(loaded.jobs()).isEmpty();
        assertThat(loaded.order("c", 1, List.of(task("c", StationKind.TABLE, 1)))).isGreaterThan(second);
    }

    @Test
    void cancel_givenAnOrderId_thenRemovesOnlyThatOrder()
    {
        var queue = new WorksQueue();
        var first = queue.order("a", 1, List.of(task("a", StationKind.TABLE, 1)));
        queue.order("b", 1, List.of(task("b", StationKind.TABLE, 1)));

        assertThat(queue.cancel(first)).isTrue();
        assertThat(queue.jobs()).extracting(WorksQueue.Job::what).containsExactly("b");
    }
}
