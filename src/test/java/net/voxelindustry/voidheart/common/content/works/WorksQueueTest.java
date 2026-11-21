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

        assertThat(loaded.order("b", 1, List.of(task("b", StationKind.TABLE, 1)))).isEqualTo(2);
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
