package net.voxelindustry.voidheart.common.world.dungeon;

import net.minecraft.core.Vec3i;

// "from" is the corner of the part kept, for a file holding more than what is used
public record DungeonTemplate(String file, Vec3i from, Vec3i size)
{
    public static final DungeonTemplate TEMPLE = new DungeonTemplate("pocket_temple", Vec3i.ZERO, new Vec3i(17, 17, 17));
    public static final DungeonTemplate TOWER = new DungeonTemplate("pocket_tower", Vec3i.ZERO, new Vec3i(17, 17, 17));
    public static final DungeonTemplate KEEP = new DungeonTemplate("massive_pocket", new Vec3i(14, 6, 8), new Vec3i(37, 57, 37));
    public static final DungeonTemplate EMPTY_RITUAL = new DungeonTemplate("empty_ritual1", Vec3i.ZERO, new Vec3i(11, 7, 7));
    public static final DungeonTemplate EMPTY_RITUAL_ALT = new DungeonTemplate("empty_ritual2", Vec3i.ZERO, new Vec3i(11, 7, 7));
    public static final DungeonTemplate LAMP_RITUAL = new DungeonTemplate("void_lamp_ritual", Vec3i.ZERO, new Vec3i(11, 7, 7));
    public static final DungeonTemplate PEARL_RITUAL = new DungeonTemplate("void_pearl_ritual", Vec3i.ZERO, new Vec3i(7, 7, 11));
    public static final DungeonTemplate MONOLITH_CIRCLE = new DungeonTemplate("monolith_circle", Vec3i.ZERO, new Vec3i(9, 4, 9));

    public record Placement(DungeonTemplate template, int x, int y, int z)
    {
    }
}
