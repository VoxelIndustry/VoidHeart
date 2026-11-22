package net.voxelindustry.voidheart.common.content.works;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.ToLongFunction;

/**
 * One owner's orders, each a list of crafts in the order the plan made them. An Officiant claims one craft at a time from the first task it can serve
 * whose ingredients are there, so a craft waits on the crafts making its ingredients without the queue tracking them. A claimed craft is the
 * Officiant's to finish; one it gives back, broken before laying anything, is counted again. An order stays until its last claimed craft is finished,
 * so its progress shows to the end.
 */
public final class WorksQueue
{
    /**
     * The era of claims made since claims began saving one. Claims saved before it read as era 0, and only settle orders loaded with them.
     */
    static final int ERA = 1;

    private record Saved(List<Job> jobs, int nextId, Optional<Integer> legacyBelow)
    {
        static final Codec<Saved> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Job.CODEC.listOf().fieldOf("jobs").forGetter(Saved::jobs),
                Codec.INT.optionalFieldOf("next_id", 1).forGetter(Saved::nextId),
                Codec.INT.optionalFieldOf("legacy_below").forGetter(Saved::legacyBelow)
        ).apply(instance, Saved::new));
    }

    /**
     * Saved with the next order's id, so an id is never given twice; queues saved as a bare list of jobs still load. Every order in a queue saved before
     * claims had an era is one an old claim may settle, and none made after.
     */
    public static final Codec<WorksQueue> CODEC = Codec.withAlternative(
            Saved.CODEC.xmap(saved -> new WorksQueue(saved.jobs(), saved.nextId(), saved.legacyBelow().orElse(saved.nextId())),
                    queue -> new Saved(queue.jobs, queue.nextId, Optional.of(queue.legacyBelow))),
            Job.CODEC.listOf().xmap(jobs ->
            {
                var next = jobs.stream().mapToInt(Job::id).max().orElse(0) + 1;
                return new WorksQueue(jobs, next, next);
            }, queue -> queue.jobs));

    /**
     * @param remaining crafts not yet claimed
     * @param working   crafts claimed and not yet finished
     */
    public record Task(String recipe, StationKind station, long remaining, long working)
    {
        static final Codec<Task> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("recipe").forGetter(Task::recipe),
                Codec.STRING.xmap(StationKind::valueOf, StationKind::name).fieldOf("station").forGetter(Task::station),
                Codec.LONG.fieldOf("remaining").forGetter(Task::remaining),
                Codec.LONG.optionalFieldOf("working", 0L).forGetter(Task::working)
        ).apply(instance, Task::new));

        public Task(String recipe, StationKind station, long remaining)
        {
            this(recipe, station, remaining, 0);
        }

        private Task plus(long claimed)
        {
            return new Task(recipe, station, remaining - claimed, Math.max(0, working + claimed));
        }
    }

    /**
     * @param what what was ordered, as the player reads it
     */
    public record Job(int id, String what, long count, List<Task> tasks)
    {
        static final Codec<Job> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("id").forGetter(Job::id),
                Codec.STRING.fieldOf("what").forGetter(Job::what),
                Codec.LONG.fieldOf("count").forGetter(Job::count),
                Task.CODEC.listOf().fieldOf("tasks").forGetter(Job::tasks)
        ).apply(instance, Job::new));

        public Job
        {
            tasks = new ArrayList<>(tasks);
        }

        /**
         * The ordered item is crafted last, so once its crafts are all done nothing else of the order is needed, even crafts planned for ingredients
         * the hoard was given since.
         */
        public boolean isDone()
        {
            if (tasks.isEmpty())
                return true;
            var last = tasks.getLast();
            return last.remaining() <= 0 && last.working() <= 0;
        }
    }

    /**
     * Crafts an Officiant took: one, or a batch of one recipe that a furnace works through at once.
     *
     * @param era   {@link #ERA} for a claim made now, 0 for one saved before claims had an era
     * @param count how many crafts of the recipe it took, 1 for a claim saved before batches
     */
    public record Claim(int job, String recipe, StationKind station, int era, int count)
    {
        public static final Codec<Claim> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("job").forGetter(Claim::job),
                Codec.STRING.fieldOf("recipe").forGetter(Claim::recipe),
                Codec.STRING.xmap(StationKind::valueOf, StationKind::name).fieldOf("station").forGetter(Claim::station),
                Codec.INT.optionalFieldOf("era", 0).forGetter(Claim::era),
                Codec.INT.optionalFieldOf("count", 1).forGetter(Claim::count)
        ).apply(instance, Claim::new));

        public Claim
        {
            count = Math.max(1, count);
        }

        public Claim(int job, String recipe, StationKind station, int era)
        {
            this(job, recipe, station, era, 1);
        }

        public Claim(int job, String recipe, StationKind station)
        {
            this(job, recipe, station, ERA, 1);
        }
    }

    private final List<Job> jobs = new ArrayList<>();
    /** The id the next order gets, past every id given so far, so a claim on a cancelled order never settles a later one. */
    private int nextId = 1;
    /** Orders below this id were loaded from a save older than claim eras, so a claim saved with them may settle them; no later order. */
    private int legacyBelow = 1;

    public WorksQueue()
    {
    }

    private WorksQueue(List<Job> jobs, int nextId, int legacyBelow)
    {
        jobs.forEach(job -> this.jobs.add(new Job(job.id(), job.what(), job.count(), job.tasks())));
        this.nextId = Math.max(nextId, jobs.stream().mapToInt(Job::id).max().orElse(0) + 1);
        this.legacyBelow = Math.clamp(legacyBelow, 1, this.nextId);
    }

    public List<Job> jobs()
    {
        return List.copyOf(jobs);
    }

    /**
     * Adds an order, crafts of one recipe next to each other merged into one task.
     *
     * @return the new job's id
     */
    public int order(String what, long count, List<Task> tasks)
    {
        var id = nextId++;
        jobs.add(new Job(id, what, count, merged(tasks)));
        return id;
    }

    /**
     * @return the tasks with crafts of one recipe next to each other merged into one
     */
    static List<Task> merged(List<Task> tasks)
    {
        var merged = new ArrayList<Task>();
        for (var task : tasks)
        {
            if (!merged.isEmpty() && merged.getLast().recipe().equals(task.recipe()))
                merged.set(merged.size() - 1, new Task(task.recipe(), task.station(), merged.getLast().remaining() + task.remaining(),
                        merged.getLast().working() + task.working()));
            else
                merged.add(task);
        }
        return merged;
    }

    /**
     * Puts crafts worked out again in place of what is left of an order, so long as none of it is being worked.
     *
     * @return whether the order was there and idle, and so changed
     */
    public boolean replan(int job, List<Task> tasks)
    {
        for (int index = 0; index < jobs.size(); index++)
        {
            var existing = jobs.get(index);
            if (existing.id() != job)
                continue;
            if (existing.tasks().stream().anyMatch(task -> task.working() > 0))
                return false;
            jobs.set(index, new Job(existing.id(), existing.what(), existing.count(), merged(tasks)));
            jobs.removeIf(Job::isDone);
            return true;
        }
        return false;
    }

    /**
     * Claims one craft of the first task, oldest order first, that the station runs and that can start.
     *
     * @param canStart whether a task's recipe can start now, such as its ingredients being there
     */
    public @Nullable Claim claim(StationKind station, Predicate<Task> canStart)
    {
        return claimBatch(station, task -> canStart.test(task) ? 1 : 0);
    }

    /**
     * Claims as many crafts as can start together of the first task, oldest order first, that the station runs and that can start at all, never more
     * than the task has left.
     *
     * @param canStart how many crafts of a task's recipe can start now, 0 when none can
     */
    public @Nullable Claim claimBatch(StationKind station, ToLongFunction<Task> canStart)
    {
        for (var job : jobs)
        {
            for (int index = 0; index < job.tasks().size(); index++)
            {
                var task = job.tasks().get(index);
                if (task.station() != station || task.remaining() <= 0)
                    continue;
                var count = (int) Math.min(Math.min(task.remaining(), canStart.applyAsLong(task)), Integer.MAX_VALUE);
                if (count <= 0)
                    continue;
                job.tasks().set(index, task.plus(count));
                return new Claim(job.id(), task.recipe(), station, ERA, count);
            }
        }
        return null;
    }

    /**
     * Counts claimed crafts again, if their order is still there.
     */
    public void giveBack(Claim claim)
    {
        settle(claim, claim.count());
    }

    /**
     * Counts claimed crafts as done: their order goes once nothing of it is left or being worked.
     */
    public void finish(Claim claim)
    {
        settle(claim, 0);
    }

    /**
     * Counts some of a batch done and the rest again, such as a furnace broken halfway through.
     */
    public void finish(Claim claim, int done)
    {
        settle(claim, Math.clamp(claim.count() - done, 0, claim.count()));
    }

    /**
     * @param back how many of the claim's crafts to count again, none when it is done
     */
    private void settle(Claim claim, int back)
    {
        for (var job : jobs)
        {
            // A claim saved before eras only knew the orders loaded with it, so it can't settle a later order given a cancelled order's id
            if (job.id() != claim.job() || claim.era() < ERA && job.id() >= legacyBelow)
                continue;
            for (int index = 0; index < job.tasks().size(); index++)
            {
                var task = job.tasks().get(index);
                if (task.recipe().equals(claim.recipe()) && task.working() > 0)
                {
                    var settled = Math.min(claim.count(), task.working());
                    job.tasks().set(index, new Task(task.recipe(), task.station(), task.remaining() + Math.min(back, settled), task.working() - settled));
                    break;
                }
            }
        }
        jobs.removeIf(Job::isDone);
    }

    /**
     * @return whether an order with that id was there
     */
    public boolean cancel(int job)
    {
        return jobs.removeIf(existing -> existing.id() == job);
    }
}
