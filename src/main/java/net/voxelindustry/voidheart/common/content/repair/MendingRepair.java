package net.voxelindustry.voidheart.common.content.repair;

/**
 * How much the mending altar restores, on the same terms as an anvil and the Mending enchantment.
 */
public final class MendingRepair
{
    /**
     * As with Mending, a point of experience restores two of durability.
     */
    public static final int DURABILITY_PER_EXPERIENCE = 2;

    private MendingRepair()
    {
    }

    /**
     * As at an anvil, a repair material restores a quarter of the tool, at least one point.
     */
    public static int materialRepair(int maxDamage, int damage)
    {
        return Math.min(damage, Math.max(1, maxDamage / 4));
    }

    /**
     * @return the experience drawn in one gulp: no more than the gulp, the skull holds, or the tool still needs
     */
    public static int experienceToDrain(int damage, int available, int gulp)
    {
        var needed = (damage + DURABILITY_PER_EXPERIENCE - 1) / DURABILITY_PER_EXPERIENCE;
        return Math.max(0, Math.min(gulp, Math.min(available, needed)));
    }

    /**
     * @return the damage left once the given experience is spent on it
     */
    public static int damageAfterExperience(int damage, int experience)
    {
        return Math.max(0, damage - experience * DURABILITY_PER_EXPERIENCE);
    }
}
