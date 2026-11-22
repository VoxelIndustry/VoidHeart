package net.voxelindustry.voidheart.common.content.armor;

/**
 * The Void Armor's numbers, from docs/plans/void-armor.md. Amounts are void energy, rates per tick. All guesses, to be tuned from play.
 */
public final class ArmorBalance
{
    public static final long FRAME_CAPACITY   = 150_000;
    public static final long BATTERY_CAPACITY = 500_000;

    /** Guess: what a fight costs a tick on average with the bare frame, walking and sprinting included. */
    public static final long FIGHT_DRAIN             = 40;
    /** Guess: added by exoskeleton power over a fight, its hum and its powered jumps and hits. */
    public static final long EXOSKELETON_FIGHT_DRAIN = 14;
    public static final long NIGHT_VISION_DRAIN      = 2;
    /** Guess: the spectral lens drains 24 a tick while on, and is on about half a fight. */
    public static final long SPECTRAL_FIGHT_DRAIN    = 12;

    /** Guess: what the worn frame costs a tick standing still, before its modules. */
    public static final long   STANDING_DRAIN         = 12;
    public static final long   EXOSKELETON_DRAIN      = 6;
    public static final long   SPECTRAL_DRAIN         = 24;
    /** Guess: by the block, about 8 a tick walking and 20 sprinting at the frame's pace. */
    public static final double WALK_DRAIN_PER_BLOCK   = 32;
    public static final double SPRINT_DRAIN_PER_BLOCK = 60;
    /** Guess: what the wings cost a tick in the air, on top of standing; about a minute and a half of flight on a bare frame. */
    public static final long   LEVITATION_DRAIN       = 80;
    public static final long   POWERED_JUMP_DRAIN     = 300;
    public static final long   POWERED_HIT_DRAIN      = 200;
    /** Guess: what each shield point the frame refills in its socketed apparatus costs. */
    public static final double REGEN_DRAIN_PER_POINT = 40;
    /** How much faster the frame refills its socketed apparatus than the apparatus would alone. */
    public static final float  REGEN_BOOST           = 1.5F;
    /** Guess: 500 a heart of fall damage taken by the legs instead of the wearer. */
    public static final long   FALL_DRAIN_PER_POINT   = 250;

    /** Shares of the capacity below which the frame warns, then cuts its wings, spectral lens, night vision, exoskeleton power and apparatus regen in turn. */
    public static final double LOW               = 0.25;
    public static final double SHED_LEVITATION   = 0.20;
    public static final double SHED_SPECTRAL     = 0.15;
    public static final double SHED_NIGHT_VISION = 0.10;
    public static final double SHED_EXOSKELETON  = 0.05;
    public static final double SHED_REGEN        = 0.02;

    /** A limp frame's wearer moves at this share of their speed less, and can't jump or sprint. */
    public static final double LIMP_SLOW = 0.65;
    /** Added to a wearer's fall each tick in water, so the frame sinks and walks the bottom. */
    public static final double WATER_SINK = 0.03;
    /** Most ticks between telling clients a worn frame's charge, sooner when the frame cuts a module. */
    public static final int    CHARGE_SYNC = 20;

    public static final float FRAME_SPEED       = 0.15F;
    public static final float EXOSKELETON_SPEED = 0.20F;
    public static final float ENTITY_REACH      = 1.5F;
    public static final float BLOCK_REACH       = 1F;
    /** Blocks cleared by a jump with and without exoskeleton power. */
    public static final float FRAME_JUMP        = 1.25F;
    public static final float EXOSKELETON_JUMP  = 2F;
    /** Guess: about what each exoskeleton past the first adds to the jump, in blocks, for the frame's screen. */
    public static final float EXTRA_EXOSKELETON_JUMP = 0.85F;
    public static final float EXOSKELETON_HIT   = 3F;
    /** Guess: knockback added to a powered hit, half of a Knockback I. */
    public static final float EXOSKELETON_KNOCKBACK = 0.5F;

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
