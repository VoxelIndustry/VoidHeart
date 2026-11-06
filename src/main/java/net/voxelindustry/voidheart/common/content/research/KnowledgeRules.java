package net.voxelindustry.voidheart.common.content.research;

/**
 * @param loomCut      share of the growth of insight prices a Void Loom takes away
 * @param cogitants    how many Cogitants around a repository count at most
 * @param proxyStrain  strain each Meat Proxy bears
 * @param proxies      how many Meat Proxies around a repository count at most
 * @param proxyInsightsPerHour insights each counted Meat Proxy makes in an hour
 */
public record KnowledgeRules(double baseRate, double baseCap, double insightBaseCost, double insightGrowth, double loomCut, int cogitants, int proxyStrain,
                             int proxies, double proxyInsightsPerHour)
{
    public KnowledgeRules(double baseRate, double baseCap, double insightBaseCost, double insightGrowth)
    {
        this(baseRate, baseCap, insightBaseCost, insightGrowth, 0.25, 12, 10, 3, 2);
    }

    public double insightGrowth(boolean loom)
    {
        return loom ? 1 + (insightGrowth - 1) * (1 - loomCut) : insightGrowth;
    }

    public double insightCost(int bought)
    {
        return insightCost(bought, false);
    }

    public double insightCost(int bought, boolean loom)
    {
        return insightBaseCost * Math.pow(insightGrowth(loom), bought);
    }
}
