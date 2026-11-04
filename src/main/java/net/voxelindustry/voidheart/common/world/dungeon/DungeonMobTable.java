package net.voxelindustry.voidheart.common.world.dungeon;

import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;

import java.util.Arrays;
import java.util.List;

/**
 * How many mobs a dungeon holds and which ones. Difficulty only adds to their number and unlocks tougher types, as their stats never change.
 */
public final class DungeonMobTable
{
    private static final int[] BASE_MOBS = { 5, 15, 45 };
    private static final int[] BASE_NESTS = { 1, 3, 9 };

    // A scan height above zero is for a prepared structure the plan cannot see into: the floor must be looked for that far up
    public record Placement(DungeonMob mob, int x, int y, int z, Direction surface, int scanHeight)
    {
    }

    private DungeonMobTable()
    {
    }

    public static int budget(DungeonSize size, int difficulty)
    {
        int base = BASE_MOBS[size.ordinal()];
        return base + base * (difficulty - DungeonAddress.MIN_DIFFICULTY) / (DungeonAddress.MAX_DIFFICULTY - DungeonAddress.MIN_DIFFICULTY);
    }

    public static int nests(DungeonTheme theme, DungeonSize size, int difficulty)
    {
        if (!DungeonMob.Family.PEST.livesIn(theme))
            return 0;
        return BASE_NESTS[size.ordinal()] * (1 + (difficulty - DungeonAddress.MIN_DIFFICULTY) / 2);
    }

    public static List<DungeonMob> roster(DungeonTheme theme, int difficulty)
    {
        return Arrays.stream(DungeonMob.values())
                .filter(mob -> mob.family.livesIn(theme) && difficulty >= mob.minDifficulty)
                .toList();
    }

    public static DungeonMob pick(List<DungeonMob> roster, RandomSource random)
    {
        int roll = random.nextInt(roster.stream().mapToInt(mob -> mob.weight).sum());
        for (var mob : roster)
        {
            roll -= mob.weight;
            if (roll < 0)
                return mob;
        }
        return roster.getFirst();
    }
}
