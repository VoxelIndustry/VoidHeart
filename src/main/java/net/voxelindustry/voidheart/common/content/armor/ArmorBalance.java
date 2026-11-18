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

    public static final int RACKS        = 4;
    public static final int SOCKET_SLOT  = RACKS;
    public static final int FRAME_SLOTS  = RACKS + 1;

    private ArmorBalance()
    {
    }
}
