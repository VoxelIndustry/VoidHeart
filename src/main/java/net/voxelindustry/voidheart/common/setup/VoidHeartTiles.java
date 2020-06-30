package net.voxelindustry.voidheart.common.setup;

import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import net.voxelindustry.voidheart.common.tile.PortalWallTile;
import net.voxelindustry.voidheart.common.tile.VoidPortalTile;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class VoidHeartTiles
{
    public static BlockEntityType<VoidPortalTile> POCKET_PORTAL;
    public static BlockEntityType<PortalWallTile> PORTAL_WALL;

    public static void registerTiles()
    {
        registerTile(POCKET_PORTAL =
                        BlockEntityType.Builder.create(VoidPortalTile::new, VoidHeartBlocks.POCKET_PORTAL).build(null),
                new Identifier(MODID, "pocket_portal"));

        registerTile(PORTAL_WALL =
                        BlockEntityType.Builder.create(PortalWallTile::new, VoidHeartBlocks.PORTAL_WALL).build(null),
                new Identifier(MODID, "portal_wall"));
    }

    private static void registerTile(BlockEntityType<?> type, Identifier name)
    {
        Registry.register(Registry.BLOCK_ENTITY_TYPE, name, type);
    }
}
