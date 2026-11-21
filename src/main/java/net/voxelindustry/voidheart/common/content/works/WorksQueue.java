package net.voxelindustry.voidheart.common.content.works;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * One owner's orders, each a list of crafts in the order the plan made them. An Officiant claims one craft at a time from the first task it can serve
 * whose ingredients are there, so a craft waits on the crafts making its ingredients without the queue tracking them. A claimed craft is the
 * Officiant's to finish; one it gives back, broken before laying anything, is counted again.
 */
public final class WorksQueue
{
    public static final Codec<WorksQueue> CODEC = Job.CODEC.listOf().xmap(WorksQueue::new, queue -> queue.jobs);

    /**
     * @param remaining crafts not yet claimed
     */
    public record Task(String recipe, StationKind station, long remaining)
    {
        static final Codec<Task> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("recipe").forGetter(Task::recipe),
                Codec.STRING.xmap(StationKind::valueOf, StationKind::name).fieldOf("station").forGetter(Task::station),
                Codec.LONG.fieldOf("remaining").forGetter(Task::remaining)
        ).apply(instance, Task::new));
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
            return tasks.stream().allMatch(task -> task.remaining() <= 0);
        }
    }

    /**
     * A craft an Officiant took.
     */
    public record Claim(int job, String recipe, StationKind station)
    {
        public static final Codec<Claim> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("job").forGetter(Claim::job),
                Codec.STRING.fieldOf("recipe").forGetter(Claim::recipe),
                Codec.STRING.xmap(StationKind::valueOf, StationKind::name).fieldOf("station").forGetter(Claim::station)
        ).apply(instance, Claim::new));
    }

    private final List<Job> jobs = new ArrayList<>();

    public WorksQueue()
    {
    }

    private WorksQueue(List<Job> jobs)
    {
        jobs.forEach(job -> this.jobs.add(new Job(job.id(), job.what(), job.count(), job.tasks())));
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
                merged.set(merged.size() - 1, new Task(task.recipe(), task.station(), merged.getLast().remaining() + task.remaining()));
            else
                merged.add(task);
        }
        var id = jobs.stream().mapToInt(Job::id).max().orElse(0) + 1;
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
                job.tasks().set(index, new Task(task.recipe(), task.station(), task.remaining() - 1));
                var claim = new Claim(job.id(), task.recipe(), station);
                jobs.removeIf(Job::isDone);
                return claim;
            }
        }
        return null;
    }

    /**
     * Counts a claimed craft again, if its order is still there.
     */
    public void giveBack(Claim claim)
    {
        for (var job : jobs)
        {
            if (job.id() != claim.job())
                continue;
            for (int index = 0; index < job.tasks().size(); index++)
            {
                var task = job.tasks().get(index);
                if (task.recipe().equals(claim.recipe()))
                {
                    job.tasks().set(index, new Task(task.recipe(), task.station(), task.remaining() + 1));
                    return;
                }
            }
        }
    }

    /**
     * @return whether an order with that id was there
     */
    public boolean cancel(int job)
    {
        return jobs.removeIf(existing -> existing.id() == job);
    }
}
