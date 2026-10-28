package net.voxelindustry.voidheart.common.util;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ExperienceUtil
{
    public float nextLevelProgress(int experienceTotal)
    {
        int level = 0;
        int nextLevelExp = 7;

        while (experienceTotal >= nextLevelExp)
        {
            experienceTotal -= nextLevelExp;
            level++;

            nextLevelExp = getNextLevelExp(level);
        }

        return experienceTotal / (float) nextLevelExp;
    }

    public int getNextLevelExp(int level)
    {
        if (level < 15)
            return 7 + level * 2;
        else if (level < 30)
            return 37 + (level - 15) * 5;
        else
            return 112 + (level - 30) * 9;
    }

    public int getExperienceLevel(int experienceTotal)
    {
        int level = 0;
        int nextLevelExp = 7;

        while (experienceTotal >= nextLevelExp)
        {
            experienceTotal -= nextLevelExp;
            level++;

            nextLevelExp = getNextLevelExp(level);
        }

        return level;
    }

    /**
     * @return levels a player starting from level 0 spends to gather the given points, a started level counting as one
     */
    public int getLevelsToGather(int experienceTotal)
    {
        var level = getExperienceLevel(experienceTotal);
        return nextLevelProgress(experienceTotal) > 0 ? level + 1 : level;
    }

    public int getTotalExperienceForLevel(int level)
    {
        int total = 0;
        for (int i = 0; i < level; i++)
        {
            total += getNextLevelExp(i + 1);
        }

        return total;
    }
}
