package net.voxelindustry.voidheart.common.setup;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.voxelindustry.voidheart.common.content.altar.VoidAltarTile;
import net.voxelindustry.voidheart.common.content.door.VoidDoorTile;
import net.voxelindustry.voidheart.common.content.essence.RavenousCollectorTile;
import net.voxelindustry.voidheart.common.content.essence.RavenousRelayTile;
import net.voxelindustry.voidheart.common.content.essence.RavenousStorageMatrixTile;
import net.voxelindustry.voidheart.common.content.essence.RavenousStorageTile;
import net.voxelindustry.voidheart.common.content.eyebottle.EyeBottleTile;
import net.voxelindustry.voidheart.common.content.heart.VoidHeartTile;
import net.voxelindustry.voidheart.common.content.inventorymover.InventoryInserterTile;
import net.voxelindustry.voidheart.common.content.permeablebarrier.VoidBarrierEmitterTile;
import net.voxelindustry.voidheart.common.content.pillar.VoidPillarTile;
import net.voxelindustry.voidheart.common.content.portalframe.PortalFrameCoreTile;
import net.voxelindustry.voidheart.common.content.portalframe.PortalFrameTile;
import net.voxelindustry.voidheart.common.content.portalinterior.PortalInteriorTile;
import net.voxelindustry.voidheart.common.content.repair.ExperienceSkullTile;
import net.voxelindustry.voidheart.common.content.repair.MendingAltarTile;
import net.voxelindustry.voidheart.common.content.shatterforge.ShatterForgeTile;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class VoidHeartTiles
{
    public static BlockEntityType<PortalInteriorTile> POCKET_PORTAL;
    public static BlockEntityType<PortalFrameTile> PORTAL_FRAME;
    public static BlockEntityType<PortalFrameCoreTile> PORTAL_FRAME_CORE;
    public static BlockEntityType<VoidAltarTile> VOID_ALTAR;
    public static BlockEntityType<VoidPillarTile> VOID_PILLAR;
    public static BlockEntityType<VoidHeartTile> VOID_HEART;
    public static BlockEntityType<VoidDoorTile> VOID_DOOR;

    public static BlockEntityType<VoidBarrierEmitterTile> VOID_BARRIER_EMITTER;

    public static BlockEntityType<ExperienceSkullTile> EXPERIENCE_SKULL;
    public static BlockEntityType<MendingAltarTile> MENDING_ALTAR;

    public static BlockEntityType<InventoryInserterTile> INVENTORY_INSERTER;

    public static BlockEntityType<EyeBottleTile> EYE_BOTTLE;
    public static BlockEntityType<ShatterForgeTile> SHATTER_FORGE;

    public static BlockEntityType<RavenousCollectorTile> RAVENOUS_COLLECTOR;
    public static BlockEntityType<RavenousStorageTile> RAVENOUS_STORAGE;
    public static BlockEntityType<RavenousStorageMatrixTile> RAVENOUS_STORAGE_MATRIX;
    public static BlockEntityType<RavenousRelayTile> RAVENOUS_RELAY;

    public static void registerTiles()
    {
        registerTile(POCKET_PORTAL =
                        FabricBlockEntityTypeBuilder.create(PortalInteriorTile::new, VoidHeartBlocks.PORTAL_INTERIOR).build(),
                "pocket_portal");

        registerTile(PORTAL_FRAME =
                        FabricBlockEntityTypeBuilder.create(PortalFrameTile::new, VoidHeartBlocks.PORTAL_FRAME).build(),
                "portal_frame");
        registerTile(PORTAL_FRAME_CORE =
                        FabricBlockEntityTypeBuilder.create(PortalFrameCoreTile::new, VoidHeartBlocks.PORTAL_FRAME_CORE).build(),
                "portal_frame_core");

        registerTile(VOID_ALTAR =
                        FabricBlockEntityTypeBuilder.create(VoidAltarTile::new, VoidHeartBlocks.VOID_ALTAR).build(),
                "void_altar");

        registerTile(VOID_PILLAR =
                        FabricBlockEntityTypeBuilder.create(VoidPillarTile::new, VoidHeartBlocks.VOID_PILLAR).build(),
                "void_pillar");

        registerTile(VOID_HEART =
                        FabricBlockEntityTypeBuilder.create(VoidHeartTile::new, VoidHeartBlocks.VOID_HEART).build(),
                "void_heart");

        registerTile(VOID_DOOR =
                        FabricBlockEntityTypeBuilder.create(VoidDoorTile::new, VoidHeartBlocks.VOID_DOOR).build(),
                "void_door");

        registerTile(VOID_BARRIER_EMITTER =
                        FabricBlockEntityTypeBuilder.create(VoidBarrierEmitterTile::new, VoidHeartBlocks.VOID_BARRIER_EMITTER).build(),
                "void_barrier_emitter");

        registerTile(EXPERIENCE_SKULL =
                        FabricBlockEntityTypeBuilder.create(ExperienceSkullTile::new, VoidHeartBlocks.EXPERIENCE_SKULL).build(),
                "experience_skull");
        registerTile(MENDING_ALTAR =
                        FabricBlockEntityTypeBuilder.create(MendingAltarTile::new, VoidHeartBlocks.MENDING_ALTAR).build(),
                "mending_altar");

        registerTile(INVENTORY_INSERTER =
                        FabricBlockEntityTypeBuilder.create(InventoryInserterTile::new, VoidHeartBlocks.INVENTORY_INSERTER).build(),
                "inventory_inserter");

        registerTile(EYE_BOTTLE =
                        FabricBlockEntityTypeBuilder.create(EyeBottleTile::new, VoidHeartBlocks.EYE_BOTTLE).build(),
                "eye_bottle");

        registerTile(SHATTER_FORGE = FabricBlockEntityTypeBuilder.create(ShatterForgeTile::new, VoidHeartBlocks.SHATTER_FORGE).build(),
                "shatter_forge");

        registerTile(RAVENOUS_COLLECTOR = FabricBlockEntityTypeBuilder.create(RavenousCollectorTile::new, VoidHeartBlocks.RAVENOUS_COLLECTOR).build(),
                "ravenous_collector");
        registerTile(RAVENOUS_STORAGE = FabricBlockEntityTypeBuilder.create(RavenousStorageTile::new, VoidHeartBlocks.RAVENOUS_STORAGE).build(),
                "ravenous_storage");
        registerTile(RAVENOUS_STORAGE_MATRIX = FabricBlockEntityTypeBuilder.create(RavenousStorageMatrixTile::new, VoidHeartBlocks.RAVENOUS_STORAGE_MATRIX).build(),
                "ravenous_storage_matrix");
        registerTile(RAVENOUS_RELAY = FabricBlockEntityTypeBuilder.create(RavenousRelayTile::new, VoidHeartBlocks.RAVENOUS_RELAY).build(),
                "ravenous_relay");
    }

    private static void registerTile(BlockEntityType<?> type, String name)
    {
        Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, new ResourceLocation(MODID, name), type);
    }
}
