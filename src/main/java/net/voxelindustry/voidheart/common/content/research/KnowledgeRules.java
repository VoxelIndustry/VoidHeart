package net.voxelindustry.voidheart.common.content.research;

public record KnowledgeRules(double baseRate, double baseCap, double insightBaseCost, double insightGrowth)
{
    public double insightCost(int bought)
    {
        return insightBaseCost * Math.pow(insightGrowth, bought);
    }
}
