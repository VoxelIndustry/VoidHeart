package net.voxelindustry.voidheart.common.setup;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.voxelindustry.voidheart.common.content.altar.VoidAltarTile;
import net.voxelindustry.voidheart.common.content.athanor.AspectIntakeTile;
import net.voxelindustry.voidheart.common.content.athanor.AthanorTile;
import net.voxelindustry.voidheart.common.content.athanor.CrucibleTile;
import net.voxelindustry.voidheart.common.content.armillary.ArmillaryRiftTile;
import net.voxelindustry.voidheart.common.content.armillary.ArmillaryTile;
import net.voxelindustry.voidheart.common.content.depths.SmokerVentTile;
import net.voxelindustry.voidheart.common.content.door.VoidDoorTile;
import net.voxelindustry.voidheart.common.content.essence.RavenousCollectorTile;
import net.voxelindustry.voidheart.common.content.essence.RavenousRelayTile;
import net.voxelindustry.voidheart.common.content.essence.RavenousStorageMatrixTile;
import net.voxelindustry.voidheart.common.content.essence.RavenousStorageTile;
import net.voxelindustry.voidheart.common.content.eyebottle.EyeBottleTile;
import net.voxelindustry.voidheart.common.content.heart.VoidHeartTile;
import net.voxelindustry.voidheart.common.content.inventorymover.InventoryInserterTile;
import net.voxelindustry.voidheart.common.content.oculus.OculusTableTile;
import net.voxelindustry.voidheart.common.content.beholder.BeholderTile;
import net.voxelindustry.voidheart.common.content.permeablebarrier.PermeableBarrierTile;
import net.voxelindustry.voidheart.common.content.pillar.VoidPillarTile;
import net.voxelindustry.voidheart.common.content.portalframe.PortalFrameCoreTile;
import net.voxelindustry.voidheart.common.content.portalframe.PortalFrameTile;
import net.voxelindustry.voidheart.common.content.portalinterior.PortalInteriorTile;
import net.voxelindustry.voidheart.common.content.research.CogitantTile;
import net.voxelindustry.voidheart.common.content.research.KnowledgeColumnTile;
import net.voxelindustry.voidheart.common.content.research.MeatProxyTile;
import net.voxelindustry.voidheart.common.content.research.VoidLoomTile;
import net.voxelindustry.voidheart.common.content.research.KnowledgeRepositoryTile;
import net.voxelindustry.voidheart.common.content.mob.BarnacleNestTile;
import net.voxelindustry.voidheart.common.content.repair.ExperienceSkullTile;
import net.voxelindustry.voidheart.common.content.repair.MendingAltarTile;
import net.voxelindustry.voidheart.common.content.shatterforge.ShatterForgeTile;
import net.voxelindustry.voidheart.common.content.tormenter.MalignantTormenterTile;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class VoidHeartTiles
{
    public static BlockEntityType<PortalInteriorTile> POCKET_PORTAL;
    public static BlockEntityType<PortalFrameTile> PORTAL_FRAME;
    public static BlockEntityType<PortalFrameCoreTile> PORTAL_FRAME_CORE;
    public static BlockEntityType<VoidAltarTile> VOID_ALTAR;
    public static BlockEntityType<VoidPillarTile> VOID_PILLAR;
    public static BlockEntityType<KnowledgeRepositoryTile> KNOWLEDGE_REPOSITORY;
    public static BlockEntityType<KnowledgeColumnTile>     KNOWLEDGE_COLUMN;
    public static BlockEntityType<CogitantTile>            COGITANT;
    public static BlockEntityType<VoidLoomTile>            VOID_LOOM;
    public static BlockEntityType<MeatProxyTile>           MEAT_PROXY;
    public static BlockEntityType<VoidHeartTile> VOID_HEART;
    public static BlockEntityType<ArmillaryTile> ARMILLARY;
    public static BlockEntityType<ArmillaryRiftTile> ARMILLARY_RIFT;
    public static BlockEntityType<VoidDoorTile> VOID_DOOR;

    public static BlockEntityType<PermeableBarrierTile> PERMEABLE_BARRIER;
    public static BlockEntityType<OculusTableTile>      OCULUS_TABLE;
    public static BlockEntityType<BeholderTile>         BEHOLDER;

    public static BlockEntityType<ExperienceSkullTile> EXPERIENCE_SKULL;
    public static BlockEntityType<BarnacleNestTile> BARNACLE_NEST;
    public static BlockEntityType<MendingAltarTile> MENDING_ALTAR;

    public static BlockEntityType<InventoryInserterTile> INVENTORY_INSERTER;

    public static BlockEntityType<EyeBottleTile> EYE_BOTTLE;
    public static BlockEntityType<ShatterForgeTile> SHATTER_FORGE;

    public static BlockEntityType<RavenousCollectorTile> RAVENOUS_COLLECTOR;
    public static BlockEntityType<RavenousStorageTile> RAVENOUS_STORAGE;
    public static BlockEntityType<RavenousStorageMatrixTile> RAVENOUS_STORAGE_MATRIX;
    public static BlockEntityType<RavenousRelayTile> RAVENOUS_RELAY;

    public static BlockEntityType<MalignantTormenterTile> MALIGNANT_TORMENTER;

    public static BlockEntityType<SmokerVentTile> SMOKER_VENT;

    public static BlockEntityType<AthanorTile> ATHANOR;
    public static BlockEntityType<CrucibleTile> CRUCIBLE;
    public static BlockEntityType<AspectIntakeTile> ASPECT_INTAKE;

    public static void registerTiles()
    {
        registerTile(ATHANOR = FabricBlockEntityTypeBuilder.create(AthanorTile::new, VoidHeartBlocks.ATHANOR).build(), "athanor");
        registerTile(CRUCIBLE = FabricBlockEntityTypeBuilder.create(CrucibleTile::new, VoidHeartBlocks.CRUCIBLE).build(), "crucible");
        registerTile(ASPECT_INTAKE = FabricBlockEntityTypeBuilder.create(AspectIntakeTile::new, VoidHeartBlocks.ASPECT_INTAKE).build(), "aspect_intake");

        registerTile(SMOKER_VENT = FabricBlockEntityTypeBuilder.create(SmokerVentTile::new, VoidHeartBlocks.SMOKER_VENT).build(), "smoker_vent");

        registerTile(POCKET_PORTAL =
                        FabricBlockEntityTypeBuilder.create(PortalInteriorTile::new, VoidHeartBlocks.PORTAL_INTERIOR).build(),
                "pocket_portal");

        registerTile(PORTAL_FRAME =
                        FabricBlockEntityTypeBuilder.create(PortalFrameTile::new, VoidHeartBlocks.PORTAL_FRAME).build(),
                "portal_frame");
        registerTile(PORTAL_FRAME_CORE =
                        FabricBlockEntityTypeBuilder.create(PortalFrameCoreTile::new, VoidHeartBlocks.PORTAL_FRAME_CORE).build(),
                "portal_frame_core");

        registerTile(ARMILLARY =
                        FabricBlockEntityTypeBuilder.create(ArmillaryTile::new, VoidHeartBlocks.ARMILLARY_CORE).build(),
                "armillary");
        registerTile(ARMILLARY_RIFT =
                        FabricBlockEntityTypeBuilder.create(ArmillaryRiftTile::new, VoidHeartBlocks.ARMILLARY_RIFT).build(),
                "armillary_rift");

        registerTile(VOID_ALTAR =
                        FabricBlockEntityTypeBuilder.create(VoidAltarTile::new, VoidHeartBlocks.VOID_ALTAR).build(),
                "void_altar");

        registerTile(VOID_PILLAR =
                        FabricBlockEntityTypeBuilder.create(VoidPillarTile::new, VoidHeartBlocks.VOID_PILLAR).build(),
                "void_pillar");

        registerTile(KNOWLEDGE_REPOSITORY =
                        FabricBlockEntityTypeBuilder.create(KnowledgeRepositoryTile::new, VoidHeartBlocks.KNOWLEDGE_REPOSITORY).build(),
                "knowledge_repository");
        registerTile(KNOWLEDGE_COLUMN =
                        FabricBlockEntityTypeBuilder.create(KnowledgeColumnTile::new, VoidHeartBlocks.KNOWLEDGE_COLUMN).build(),
                "knowledge_column");
        registerTile(COGITANT = FabricBlockEntityTypeBuilder.create(CogitantTile::new, VoidHeartBlocks.COGITANT).build(), "cogitant");
        registerTile(VOID_LOOM = FabricBlockEntityTypeBuilder.create(VoidLoomTile::new, VoidHeartBlocks.VOID_LOOM).build(), "void_loom");
        registerTile(MEAT_PROXY = FabricBlockEntityTypeBuilder.create(MeatProxyTile::new, VoidHeartBlocks.MEAT_PROXY).build(), "meat_proxy");

        registerTile(VOID_HEART =
                        FabricBlockEntityTypeBuilder.create(VoidHeartTile::new, VoidHeartBlocks.VOID_HEART).build(),
                "void_heart");

        registerTile(VOID_DOOR =
                        FabricBlockEntityTypeBuilder.create(VoidDoorTile::new, VoidHeartBlocks.VOID_DOOR).build(),
                "void_door");

        registerTile(PERMEABLE_BARRIER =
                        FabricBlockEntityTypeBuilder.create(PermeableBarrierTile::new, VoidHeartBlocks.PERMEABLE_BARRIER).build(),
                "permeable_barrier");

        registerTile(OCULUS_TABLE =
                        FabricBlockEntityTypeBuilder.create(OculusTableTile::new, VoidHeartBlocks.OCULUS_TABLE).build(),
                "oculus_table");

        registerTile(BEHOLDER =
                        FabricBlockEntityTypeBuilder.create(BeholderTile::new, VoidHeartBlocks.BEHOLDER).build(),
                "beholder");

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
        registerTile(MALIGNANT_TORMENTER = FabricBlockEntityTypeBuilder.create(MalignantTormenterTile::new, VoidHeartBlocks.MALIGNANT_TORMENTER).build(),
                "malignant_tormenter");
        registerTile(BARNACLE_NEST = FabricBlockEntityTypeBuilder.create(BarnacleNestTile::new, VoidHeartBlocks.BARNACLE_NEST).build(),
                "barnacle_nest");
    }

    private static void registerTile(BlockEntityType<?> type, String name)
    {
        Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Identifier.fromNamespaceAndPath(MODID, name), type);
    }
}
