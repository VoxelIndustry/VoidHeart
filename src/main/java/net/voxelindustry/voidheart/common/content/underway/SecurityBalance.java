package net.voxelindustry.voidheart.common.content.underway;

import net.voxelindustry.voidheart.common.energy.storage.RubrumBalance;

/** The numbers of the Underway's security, all guesses to tune in play. Damage is balanced for netherite with Protection IV, like the Daedalus above. */
public final class SecurityBalance
{
    private static final int SECOND = 20;

    /** A full rubrum cell holds a section live for about ten minutes. */
    public static final long DRAIN_PER_TICK = RubrumBalance.CELL_CAPACITY / (10 * 60 * SECOND);
    /** How long the klaxon keeps sounding after the last sighting. */
    public static final int ALARM_TICKS = 20 * SECOND;

    public static final float EYE_HEALTH = 12;
    public static final double EYE_RANGE = 12;
    /** Half the width of an eye's cone, in degrees. */
    public static final float EYE_CONE = 22;
    /** How far an eye swings to each side of the way it faces, in degrees. */
    public static final float EYE_SWEEP = 55;
    public static final int EYE_SWEEP_TICKS = 6 * SECOND;
    /** How long a player may stand in an eye's cone before it raises the alarm. */
    public static final int EYE_NOTICE_TICKS = 30;

    public static final float CUPOLA_HEALTH = 50;
    public static final float EMBRASURE_HEALTH = 40;
    public static final double GUN_RANGE = 20;
    public static final int GUN_DEPLOY_TICKS = 12;
    /** How long a gun shows its aiming line before it fires. */
    public static final int GUN_AIM_TICKS = 24;
    /** The last of the aim, when the line no longer follows its target. */
    public static final int GUN_LOCK_TICKS = 6;
    public static final int GUN_BURST_SHOTS = 4;
    public static final int GUN_SHOT_EVERY = 3;
    public static final int GUN_COOL_TICKS = 2 * SECOND;
    /** A gun with nothing in sight for this long folds away. */
    public static final int GUN_STAND_DOWN_TICKS = 5 * SECOND;
    /** A bullet ignores armour but not Protection, so a burst that lands whole takes about a third of a netherite player's health. */
    public static final float GUN_SHOT_DAMAGE = 5;
    /** How far from a locked line a body is still struck. */
    public static final double GUN_SHOT_WIDTH = 0.45;
    /** Half the arc an embrasure's gun can swing through, in degrees. */
    public static final float EMBRASURE_ARC = 32;

    public static final float AUTOMATON_HEALTH = 70;
    public static final float AUTOMATON_ARMOUR = 14;
    public static final double AUTOMATON_SPEED = 0.27;
    public static final double AUTOMATON_SIGHT = 28;
    /** A thrust of its lance, as raw damage against armour: about 5 health through Protection IV netherite. */
    public static final float AUTOMATON_LANCE_DAMAGE = 32;
    public static final float AUTOMATON_SHOT_DAMAGE = 7;
    public static final double AUTOMATON_SHOT_RANGE = 22;
    /** Closer than this it stops shooting and charges. */
    public static final double AUTOMATON_CHARGE_RANGE = 5;
    public static final int AUTOMATON_AIM_TICKS = 18;
    public static final int AUTOMATON_LOCK_TICKS = 5;
    public static final int AUTOMATON_RELOAD_TICKS = 50;
    /** How far from its post an Automaton follows before it turns back. */
    public static final double AUTOMATON_LEASH = 72;

    /** The alarm's two notes follow each other this many ticks apart. */
    public static final int KLAXON_EVERY = 8;

    public static final float TRIPWIRE_HEALTH = 6;
    public static final double TRIPWIRE_REACH = 8;
    /** How long a broken beam stays quiet before it can be broken again. */
    public static final int TRIPWIRE_RESET_TICKS = 3 * SECOND;
    /** How far from a tripwire the pit it releases may lie, and how far the doors it shuts. */
    public static final double TRIPWIRE_PIT_REACH = 4;
    public static final int TRIPWIRE_DOOR_REACH = 24;

    public static final float SEARCHLIGHT_HEALTH = 20;
    public static final double SEARCHLIGHT_RANGE = 26;
    public static final float SEARCHLIGHT_CONE = 9;
    public static final float SEARCHLIGHT_SWEEP = 50;
    public static final int SEARCHLIGHT_SWEEP_TICKS = 9 * SECOND;
    public static final int SEARCHLIGHT_NOTICE_TICKS = 8;
    /** How long a section knows where whoever a searchlight caught is. */
    public static final int SPOTTED_TICKS = 6 * SECOND;

    public static final float WELL_HEALTH = 60;
    public static final double WELL_RANGE = 26;
    /** Closer than this a mortar cannot drop a shell: the place to stand to wreck it. */
    public static final double WELL_MIN_RANGE = 4.5;
    public static final int WELL_RISE_TICKS = 16;
    public static final int WELL_SALVO_SHELLS = 3;
    public static final int WELL_SHELL_EVERY = 30;
    /** How long a shell flies, which is how long its circle shows on the ground. */
    public static final int WELL_FLIGHT_TICKS = 26;
    public static final int WELL_COOL_TICKS = 5 * SECOND;
    public static final int WELL_STAND_DOWN_TICKS = 6 * SECOND;
    public static final double WELL_BLAST_RADIUS = 2.6;
    /** A shell at the middle of its circle, as raw damage against armour: about 6 health through Protection IV netherite. */
    public static final float WELL_SHELL_DAMAGE = 38;

    public static final float MANTLET_HEALTH = 90;
    public static final float MANTLET_ARMOUR = 14;
    public static final double MANTLET_SPEED = 0.2;
    /** A blow of its shield, as raw damage against armour, which also throws its target back. */
    public static final float MANTLET_BASH_DAMAGE = 24;
    public static final double MANTLET_BASH_KNOCKBACK = 2.5;
    /** How far to each side of where its shield faces a blow glances off, in degrees. */
    public static final float MANTLET_SHIELD_ARC = 80;
    /** How fast it brings its shield around, in degrees a tick: a sprinting player circles faster up close. */
    public static final float MANTLET_TURN_PER_TICK = 4;
    public static final float MANTLET_SHOT_DAMAGE = 5;
    public static final double MANTLET_SHOT_RANGE = 16;
    public static final double MANTLET_BASH_RANGE = 3.5;
    public static final int MANTLET_AIM_TICKS = 22;
    public static final int MANTLET_LOCK_TICKS = 6;
    public static final int MANTLET_BURST_SHOTS = 3;
    public static final int MANTLET_SHOT_EVERY = 4;
    /** How long its slit stays open after its burst. */
    public static final int MANTLET_SLIT_TICKS = 14;
    public static final int MANTLET_RELOAD_TICKS = 4 * SECOND;

    public static final float CART_HEALTH = 80;
    public static final float CART_ARMOUR = 16;
    public static final double CART_SIGHT = 30;
    /** How far to each side of its track someone still stands in its way. */
    public static final double CART_TRACK_WIDTH = 1.1;
    public static final int CART_WIND_UP_TICKS = 24;
    public static final double CART_TOP_SPEED = 0.95;
    public static final double CART_SPEED_UP = 0.07;
    /** How far past whoever it charges it keeps going. */
    public static final double CART_OVERSHOOT = 5;
    public static final int CART_BRAKE_TICKS = 3 * SECOND;
    /** Being run over, as raw damage against armour: about 7 health through Protection IV netherite. */
    public static final float CART_RAM_DAMAGE = 44;
    /** How far to each side of its track's line a blow glances off its ram plates, in degrees. */
    public static final float CART_PLATE_ARC = 40;

    public static final float SAPPER_HEALTH = 10;
    public static final double SAPPER_SPEED = 0.36;
    public static final double SAPPER_SIGHT = 26;
    public static final double SAPPER_ARM_RANGE = 2.6;
    public static final int SAPPER_FUSE_TICKS = 30;
    public static final double SAPPER_BLAST_RADIUS = 3.5;
    public static final float SAPPER_DAMAGE = 34;

    public static final float FITTER_HEALTH = 30;
    public static final float FITTER_ARMOUR = 8;
    public static final double FITTER_SPEED = 0.3;
    public static final int FITTER_REPAIR_TICKS = 5 * SECOND;
    /** How near a wreck a fitter must stand to work on it, level with the floor. */
    public static final double FITTER_REACH = 2.5;

    private SecurityBalance()
    {
    }
}
