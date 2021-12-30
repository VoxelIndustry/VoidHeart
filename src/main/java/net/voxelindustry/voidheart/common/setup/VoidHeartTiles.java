package net.voxelindustry.voidheart.common.setup;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import net.voxelindustry.voidheart.common.content.altar.VoidAltarTile;
import net.voxelindustry.voidheart.common.content.door.VoidDoorTile;
import net.voxelindustry.voidheart.common.content.heart.VoidHeartTile;
import net.voxelindustry.voidheart.common.content.permeablebarrier.VoidBarrierEmitterTile;
import net.voxelindustry.voidheart.common.content.pillar.VoidPillarTile;
import net.voxelindustry.voidheart.common.content.portalframe.PortalFrameTile;
import net.voxelindustry.voidheart.common.content.portalinterior.PortalInteriorTile;
import net.voxelindustry.voidheart.common.content.repair.ExperienceSkullTile;
import net.voxelindustry.voidheart.common.content.repair.MendingAltarTile;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class VoidHeartTiles
{
    public static BlockEntityType<PortalInteriorTile> POCKET_PORTAL;
    public static BlockEntityType<PortalFrameTile>    PORTAL_WALL;
    public static BlockEntityType<VoidAltarTile>      VOID_ALTAR;
    public static BlockEntityType<VoidPillarTile>     VOID_PILLAR;
    public static BlockEntityType<VoidHeartTile>      VOID_HEART;
    public static BlockEntityType<VoidDoorTile>       VOID_DOOR;

    public static BlockEntityType<VoidBarrierEmitterTile> VOID_BARRIER_EMITTER;

    public static BlockEntityType<ExperienceSkullTile> EXPERIENCE_SKULL;
    public static BlockEntityType<MendingAltarTile> MENDING_ALTAR;

    public static void registerTiles()
    {
        registerTile(POCKET_PORTAL =
                        FabricBlockEntityTypeBuilder.create(PortalInteriorTile::new, VoidHeartBlocks.PORTAL_INTERIOR).build(null),
                "pocket_portal");

        registerTile(PORTAL_WALL =
                        FabricBlockEntityTypeBuilder.create(PortalFrameTile::new, VoidHeartBlocks.PORTAL_FRAME).build(null),
                "portal_wall");

        registerTile(VOID_ALTAR =
                        FabricBlockEntityTypeBuilder.create(VoidAltarTile::new, VoidHeartBlocks.VOID_ALTAR).build(null),
                "void_altar");

        registerTile(VOID_PILLAR =
                        FabricBlockEntityTypeBuilder.create(VoidPillarTile::new, VoidHeartBlocks.VOID_PILLAR).build(null),
                "void_pillar");

        registerTile(VOID_HEART =
                        FabricBlockEntityTypeBuilder.create(VoidHeartTile::new, VoidHeartBlocks.VOID_HEART).build(null),
                "void_heart");

        registerTile(VOID_DOOR =
                        FabricBlockEntityTypeBuilder.create(VoidDoorTile::new, VoidHeartBlocks.VOID_DOOR).build(null),
                "void_door");

        registerTile(VOID_BARRIER_EMITTER =
                        FabricBlockEntityTypeBuilder.create(VoidBarrierEmitterTile::new, VoidHeartBlocks.VOID_BARRIER_EMITTER).build(null),
                "void_barrier_emitter");

        registerTile(EXPERIENCE_SKULL =
                        FabricBlockEntityTypeBuilder.create(ExperienceSkullTile::new, VoidHeartBlocks.EXPERIENCE_SKULL).build(null),
                "experience_skull");
        registerTile(MENDING_ALTAR =
                        FabricBlockEntityTypeBuilder.create(MendingAltarTile::new, VoidHeartBlocks.MENDING_ALTAR).build(null),
                "mending_altar");
    }

    private static void registerTile(BlockEntityType<?> type, String name)
    {
        Registry.register(Registry.BLOCK_ENTITY_TYPE, new Identifier(MODID, name), type);
    }
}
