package net.voxelindustry.voidheart.common.content.armor;

/**
 * The Void Armor's numbers, from docs/plans/void-armor.md. Amounts are void energy, rates per tick. All guesses, to be tuned from play.
 */
public final class ArmorBalance
{
    public static final long FRAME_CAPACITY   = 150_000;
    public static final long BATTERY_CAPACITY = 250_000;

    /** Guess: what a fight costs a tick on average with the bare frame, walking and sprinting included. */
    public static final long FIGHT_DRAIN             = 40;
    /** Guess: added by exoskeleton power over a fight, its hum and its powered jumps and hits. */
    public static final long EXOSKELETON_FIGHT_DRAIN = 14;
    public static final long NIGHT_VISION_DRAIN      = 2;
    /** Guess: the spectral lens drains 24 a tick while on, and is on about half a fight. */
    public static final long SPECTRAL_FIGHT_DRAIN    = 12;

    public static final float FRAME_SPEED       = 0.15F;
    public static final float EXOSKELETON_SPEED = 0.20F;
    public static final float ENTITY_REACH      = 1.5F;
    public static final float BLOCK_REACH       = 1F;
    /** Blocks cleared by a jump with and without exoskeleton power. */
    public static final float FRAME_JUMP        = 1.25F;
    public static final float EXOSKELETON_JUMP  = 2F;
    public static final float EXOSKELETON_HIT   = 3F;

    /** Jump strength added by exoskeleton power, about two blocks of jump over vanilla's 0.42. */
    public static final double EXOSKELETON_JUMP_STRENGTH = 0.12;

    public static final float  SCALE             = 1.5F;
    public static final double WEARER_WIDTH      = 0.6 * SCALE;
    public static final double WEARER_HEIGHT     = 1.8 * SCALE;
    /** Added to vanilla's 0.6, so the frame walks up full blocks. */
    public static final double STEP_HEIGHT_BONUS = 0.4;
    public static final double SAFE_FALL_BONUS   = 3;

    public static final int STEP_IN_TICKS       = 30;
    public static final int STEP_OUT_TICKS      = 20;
    public static final int STEP_OUT_HOLD_TICKS = 15;
    /** How far from a respawned player a frame with no base to go home to stands. */
    public static final double PARK_BESIDE      = 1.5;

    /** Guess: most a base puts into its frame a tick, from the pylon field first, then its own cell. */
    public static final long BASE_INTAKE   = 160;
    public static final int  BASE_SYNC     = 10;

    public static final int RACKS        = 4;
    public static final int SOCKET_SLOT  = RACKS;
    public static final int FRAME_SLOTS  = RACKS + 1;

    private ArmorBalance()
    {
    }
}
