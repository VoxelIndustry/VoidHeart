package net.voxelindustry.voidheart.common.world.underway;

/** What a prop hung on a wall or from a ceiling may hang on. */
public final class UnderwayPropMount
{
    private UnderwayPropMount()
    {
    }

    /** @return whether the block there is solid enough to hang a prop on: not open, not a lamp and not another prop */
    public static boolean holds(UnderwayCanvas canvas, int x, int y, int z)
    {
        var material = canvas.get(x, y, z);
        return !material.isOpen() && material != UnderwayMaterial.LAMP && material != UnderwayMaterial.EMERGENCY_LAMP;
    }
}
