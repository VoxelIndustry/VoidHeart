package net.voxelindustry.voidheart.common.setup;

import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import net.voxelindustry.voidheart.common.tile.PortalWallTile;
import net.voxelindustry.voidheart.common.tile.VoidAltarTile;
import net.voxelindustry.voidheart.common.tile.VoidPillarTile;
import net.voxelindustry.voidheart.common.tile.VoidPortalTile;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class VoidHeartTiles
{
    public static BlockEntityType<VoidPortalTile> POCKET_PORTAL;
    public static BlockEntityType<PortalWallTile> PORTAL_WALL;
    public static BlockEntityType<VoidAltarTile>  VOID_ALTAR;
    public static BlockEntityType<VoidPillarTile> VOID_PILLAR;

    public static void registerTiles()
    {
        registerTile(POCKET_PORTAL =
                        BlockEntityType.Builder.create(VoidPortalTile::new, VoidHeartBlocks.POCKET_PORTAL).build(null),
                "pocket_portal");

        registerTile(PORTAL_WALL =
                        BlockEntityType.Builder.create(PortalWallTile::new, VoidHeartBlocks.PORTAL_WALL).build(null),
                "portal_wall");

        registerTile(VOID_ALTAR =
                        BlockEntityType.Builder.create(VoidAltarTile::new, VoidHeartBlocks.VOID_ALTAR).build(null),
                "void_altar");

        registerTile(VOID_PILLAR =
                        BlockEntityType.Builder.create(VoidPillarTile::new, VoidHeartBlocks.VOID_PILLAR).build(null),
                "void_pillar");
    }

    private static void registerTile(BlockEntityType<?> type, String name)
    {
        Registry.register(Registry.BLOCK_ENTITY_TYPE, new Identifier(MODID, name), type);
    }
}
