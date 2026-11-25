package net.voxelindustry.voidheart.common.setup;

import com.mojang.serialization.Codec;
import net.minecraft.world.entity.EntityType;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.voxelindustry.voidheart.common.content.crystal.CrystalHabit;
import net.voxelindustry.voidheart.common.content.crystal.ScouredSoak;
import net.minecraft.core.BlockPos;
import net.voxelindustry.voidheart.common.content.apparatus.GrudgeWard;
import net.voxelindustry.voidheart.common.content.crystal.CrystalRecordings;
import net.voxelindustry.voidheart.common.content.mob.DragoonAdaptation;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Registry;
import net.voxelindustry.voidheart.common.energy.gnawer.GnawerLink;
import net.voxelindustry.voidheart.common.content.sin.Sin;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.voxelindustry.voidheart.common.content.apparatus.ApparatusGrid;
import net.voxelindustry.voidheart.common.content.essence.EssenceStorage;
import net.voxelindustry.voidheart.common.content.essence.MultiEssenceStorage;
import net.voxelindustry.voidheart.common.content.oil.Brew;
import net.voxelindustry.voidheart.common.content.oil.WeaponOil;
import net.voxelindustry.voidheart.common.content.tormenter.TormenterSouls;

import java.util.List;
import java.util.UUID;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class VoidHeartDataComponents
{
    /**
     * First portal frame selected with a portal core, waiting for its pair.
     */
    public record PortalLinkStart(GlobalPos firstPos, Direction firstFacing, UUID owner, String ownerName)
    {
        public static final Codec<PortalLinkStart> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                GlobalPos.CODEC.fieldOf("first_pos").forGetter(PortalLinkStart::firstPos),
                Direction.CODEC.fieldOf("first_facing").forGetter(PortalLinkStart::firstFacing),
                UUIDUtil.CODEC.fieldOf("owner").forGetter(PortalLinkStart::owner),
                Codec.STRING.fieldOf("owner_name").forGetter(PortalLinkStart::ownerName)
        ).apply(instance, PortalLinkStart::new));
    }

    /**
     * Player a void heart was taken from.
     */
    public record HeartOwner(UUID player, String playerName)
    {
        public static final Codec<HeartOwner> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                UUIDUtil.CODEC.fieldOf("player").forGetter(HeartOwner::player),
                Codec.STRING.fieldOf("player_name").forGetter(HeartOwner::playerName)
        ).apply(instance, HeartOwner::new));
    }

    /**
     * Void door a key is bound to, inside the pocket dimension.
     */
    public record DoorLink(BlockPos doorPos, UUID doorId)
    {
        public static final Codec<DoorLink> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BlockPos.CODEC.fieldOf("door_pos").forGetter(DoorLink::doorPos),
                UUIDUtil.CODEC.fieldOf("door_id").forGetter(DoorLink::doorId)
        ).apply(instance, DoorLink::new));
    }

    /**
     * Void door of its owner's pocket a Rift Key opens onto, holding one of that owner's flexions.
     */
    public record RiftKeyLink(BlockPos doorPos, UUID doorId, UUID owner, String ownerName)
    {
        public static final Codec<RiftKeyLink> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BlockPos.CODEC.fieldOf("door_pos").forGetter(RiftKeyLink::doorPos),
                UUIDUtil.CODEC.fieldOf("door_id").forGetter(RiftKeyLink::doorId),
                UUIDUtil.CODEC.fieldOf("owner").forGetter(RiftKeyLink::owner),
                Codec.STRING.fieldOf("owner_name").forGetter(RiftKeyLink::ownerName)
        ).apply(instance, RiftKeyLink::new));
    }

    public static DataComponentType<PortalLinkStart> PORTAL_LINK;
    public static DataComponentType<HeartOwner>      HEART_OWNER;
    public static DataComponentType<DoorLink>        DOOR_LINK;
    public static DataComponentType<RiftKeyLink>     RIFT_KEY_LINK;
    public static DataComponentType<Integer>         CORRUPTION;
    public static DataComponentType<Integer>         EXPERIENCE;
    public static DataComponentType<EssenceStorage>  ESSENCE_STORAGE;
    public static DataComponentType<MultiEssenceStorage> MULTI_ESSENCE_STORAGE;
    public static DataComponentType<BlockPos>        BARRIER_FIRST_POINT;
    public static DataComponentType<TormenterSouls>  TORMENTER_SOULS;
    public static DataComponentType<WeaponOil>       WEAPON_OIL;
    public static DataComponentType<Brew>            BREW;
    /** Seconds of strained research a Meat Proxy can still bear. */
    public static DataComponentType<Double>          PROXY_ENDURANCE;
    public static DataComponentType<ApparatusGrid>   APPARATUS_GRID;
    /** The sin fused into a weapon. It holds no level: the wielder's proficiency decides. */
    public static DataComponentType<Sin>             SIN;
    /** Contravoid a Klein Star holds. */
    public static DataComponentType<Integer>         CONTRAVOID_CHARGE;
    /** The Gnawer a stake is marking a box for, and the first corner once one is chosen. */
    public static DataComponentType<GnawerLink>      GNAWER_LINK;
    /** The designs a blessed contraption drew, kept so that opening it again rerolls nothing. */
    public static DataComponentType<List<Identifier>> BLUEPRINT_DRAW;
    /** How a crystal grew. Absent on crystal as the world made it, and on shards grown without a habit. */
    public static DataComponentType<CrystalHabit>    CRYSTAL_HABIT;
    /** Taint a Scoured rubrum bundle has drawn off a node, kept while it is carried so it can't start over. */
    public static DataComponentType<Integer>         CRYSTAL_SOAK;
    /** The creature kind a Scoured memoriam bundle was imprinted with, which a Ley Ward it is set in keeps out. */
    public static DataComponentType<EntityType<?>>   CRYSTAL_IMPRINT;
    /** The kind of hit an apparatus's Grudge Ward holds against: whatever last broke its shield. */
    public static DataComponentType<DragoonAdaptation.Kind> APPARATUS_GRUDGE;
    /** The place a Deep memoriam recorded when it finished growing, which an Oculus Table can show. */
    public static DataComponentType<CrystalRecordings.Ref> CRYSTAL_RECORDING;

    public static void registerComponents()
    {
        PORTAL_LINK = register("portal_link", PortalLinkStart.CODEC);
        HEART_OWNER = register("heart_owner", HeartOwner.CODEC);
        DOOR_LINK = register("door_link", DoorLink.CODEC);
        RIFT_KEY_LINK = register("rift_key_link", RiftKeyLink.CODEC);
        CORRUPTION = register("corruption", Codec.INT);
        EXPERIENCE = register("experience", Codec.INT);
        ESSENCE_STORAGE = register("essence_storage", EssenceStorage.CODEC);
        MULTI_ESSENCE_STORAGE = register("multi_essence_storage", MultiEssenceStorage.CODEC);
        BARRIER_FIRST_POINT = register("barrier_first_point", BlockPos.CODEC);
        TORMENTER_SOULS = register("tormenter_souls", TormenterSouls.CODEC);
        WEAPON_OIL = register("weapon_oil", WeaponOil.CODEC);
        BREW = register("brew", Brew.CODEC);
        PROXY_ENDURANCE = register("proxy_endurance", Codec.DOUBLE);
        APPARATUS_GRID = register("apparatus_grid", ApparatusGrid.CODEC);
        SIN = register("sin", Sin.CODEC);
        CONTRAVOID_CHARGE = register("contravoid_charge", Codec.INT);
        GNAWER_LINK = register("gnawer_link", GnawerLink.CODEC);
        BLUEPRINT_DRAW = register("blueprint_draw", Identifier.CODEC.listOf());
        CRYSTAL_HABIT = register("crystal_habit", CrystalHabit.CODEC);
        CRYSTAL_SOAK = register("crystal_soak", Codec.intRange(0, ScouredSoak.MAX));
        CRYSTAL_IMPRINT = register("crystal_imprint", BuiltInRegistries.ENTITY_TYPE.byNameCodec());
        APPARATUS_GRUDGE = register("apparatus_grudge", GrudgeWard.KIND_CODEC);
        CRYSTAL_RECORDING = register("crystal_recording", CrystalRecordings.Ref.CODEC);
    }

    private static <T> DataComponentType<T> register(String name, Codec<T> codec)
    {
        return Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Identifier.fromNamespaceAndPath(MODID, name),
                DataComponentType.<T>builder()
                        .persistent(codec)
                        .networkSynchronized(ByteBufCodecs.fromCodecWithRegistries(codec))
                        .build());
    }
}
