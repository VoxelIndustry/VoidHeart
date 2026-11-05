package net.voxelindustry.voidheart.common.content.mob;

/**
 * When a Fallen Bishop runs to heal, and when it stops. The cap on retreats is what keeps a fight with one from going on forever.
 */
public final class BishopRetreat
{
    public static final float WITHDRAW_BELOW = 0.3F;
    public static final float HEAL_UP_TO = 0.7F;
    public static final int MAX_WITHDRAWALS = 2;
    public static final float HEAL_PER_TICK = 4F / 20;

    private BishopRetreat()
    {
    }

    public static boolean shouldWithdraw(float health, float maxHealth, int withdrawals)
    {
        return withdrawals < MAX_WITHDRAWALS && health / maxHealth < WITHDRAW_BELOW;
    }

    public static boolean isHealed(float health, float maxHealth)
    {
        return health / maxHealth >= HEAL_UP_TO;
    }
}
