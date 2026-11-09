package net.voxelindustry.voidheart.common.energy;

/**
 * Anything that stores void energy, such as a rubrum construct or a machine's buffer. The network finds holders inside its fields.
 */
public interface IVoidHolder
{
    long stored();

    long capacity();

    /**
     * The most a single insert or extract moves.
     */
    long maxRate();

    /**
     * Returns how much was, or with simulate would be, accepted.
     */
    long insert(long amount, boolean simulate);

    /**
     * Returns how much was, or with simulate would be, taken out.
     */
    long extract(long amount, boolean simulate);
}
