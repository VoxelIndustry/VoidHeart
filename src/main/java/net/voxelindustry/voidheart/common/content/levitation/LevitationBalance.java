package net.voxelindustry.voidheart.common.content.levitation;

/**
 * Numbers owned by the Levitation Belt. Values marked GUESS wait for play.
 */
public final class LevitationBalance
{
    // GUESS: added to the 0.42 jump strength, so a standing jump clears about 2.9 blocks instead of 1.25
    public static final double JUMP_BONUS          = 0.25;
    // GUESS: added to the 3 blocks a player falls without harm, so the belt's own jumps never hurt
    public static final double SAFE_FALL_BONUS     = 2;
    // GUESS: share of the fall damage left past the safe distance
    public static final double FALL_DAMAGE_KEPT    = 0.5;

    // GUESS: void energy a tick while flying in a field, a tenth of what a small pylon passes
    public static final long FLIGHT_COST          = 8;
    // A flying wearer keeps drawing from the same block while within this many blocks of it, so the grid isn't asked from a new block every tick.
    // Near a field's edge this lets flight reach this far past it
    public static final double ANCHOR_SLACK       = 2.5;
    // How often a wearer on the ground or jumping looks for a field to fly in
    public static final int  FIELD_CHECK_TICKS    = 10;

    // How long the lift takes to fade once the field is lost in flight, from a drift to a plain fall
    public static final int    FADE_TICKS          = 100;
    // Share of gravity felt the moment the lift starts to fade
    public static final double FADE_GRAVITY_START  = 0.1;
    // Fastest descent in blocks a tick the moment the lift starts to fade, and what a plain fall tops out at
    public static final double FADE_DRIFT          = 0.06;
    public static final double PLAIN_FALL_TOP      = 3.92;
    // Share of the fade over which falling doesn't count toward fall damage
    public static final double FADE_CUSHIONED      = 0.5;

    private LevitationBalance()
    {
    }
}
