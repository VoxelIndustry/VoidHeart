package net.voxelindustry.voidheart.common.content.works;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

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

        public boolean isDone()
        {
            return tasks.stream().allMatch(task -> task.remaining() <= 0 && task.working() <= 0);
        }
    }

    /**
     * A craft an Officiant took.
     *
     * @param era {@link #ERA} for a claim made now, 0 for one saved before claims had an era
     */
    public record Claim(int job, String recipe, StationKind station, int era)
    {
        public static final Codec<Claim> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("job").forGetter(Claim::job),
                Codec.STRING.fieldOf("recipe").forGetter(Claim::recipe),
                Codec.STRING.xmap(StationKind::valueOf, StationKind::name).fieldOf("station").forGetter(Claim::station),
                Codec.INT.optionalFieldOf("era", 0).forGetter(Claim::era)
        ).apply(instance, Claim::new));

        public Claim(int job, String recipe, StationKind station)
        {
            this(job, recipe, station, ERA);
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
        var merged = new ArrayList<Task>();
        for (var task : tasks)
        {
            if (!merged.isEmpty() && merged.getLast().recipe().equals(task.recipe()))
                merged.set(merged.size() - 1, new Task(task.recipe(), task.station(), merged.getLast().remaining() + task.remaining(),
                        merged.getLast().working() + task.working()));
            else
                merged.add(task);
        }
        var id = nextId++;
        jobs.add(new Job(id, what, count, merged));
        return id;
    }

    /**
     * Claims one craft of the first task, oldest order first, that the station runs and that can start.
     *
     * @param canStart whether a task's recipe can start now, such as its ingredients being there
     */
    public @Nullable Claim claim(StationKind station, Predicate<Task> canStart)
    {
        for (var job : jobs)
        {
            for (int index = 0; index < job.tasks().size(); index++)
            {
                var task = job.tasks().get(index);
                if (task.station() != station || task.remaining() <= 0 || !canStart.test(task))
                    continue;
                job.tasks().set(index, task.plus(1));
                return new Claim(job.id(), task.recipe(), station);
            }
        }
        return null;
    }

    /**
     * Counts a claimed craft again, if its order is still there.
     */
    public void giveBack(Claim claim)
    {
        settle(claim, -1);
    }

    /**
     * Counts a claimed craft as done: its order goes once nothing of it is left or being worked.
     */
    public void finish(Claim claim)
    {
        settle(claim, 0);
    }

    /**
     * @param back how many of the claim's crafts to count again, -1 to give it back or 0 when it is done
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
                    job.tasks().set(index, new Task(task.recipe(), task.station(), task.remaining() - back, task.working() - 1));
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
