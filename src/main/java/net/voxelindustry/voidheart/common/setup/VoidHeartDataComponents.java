package net.voxelindustry.voidheart.common.setup;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Registry;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.voxelindustry.voidheart.common.content.essence.EssenceStorage;

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

    public static DataComponentType<PortalLinkStart> PORTAL_LINK;
    public static DataComponentType<HeartOwner>      HEART_OWNER;
    public static DataComponentType<DoorLink>        DOOR_LINK;
    public static DataComponentType<Integer>         CORRUPTION;
    public static DataComponentType<Integer>         EXPERIENCE;
    public static DataComponentType<EssenceStorage>  ESSENCE_STORAGE;
    public static DataComponentType<BlockPos>        BARRIER_FIRST_POINT;

    public static void registerComponents()
    {
        PORTAL_LINK = register("portal_link", PortalLinkStart.CODEC);
        HEART_OWNER = register("heart_owner", HeartOwner.CODEC);
        DOOR_LINK = register("door_link", DoorLink.CODEC);
        CORRUPTION = register("corruption", Codec.INT);
        EXPERIENCE = register("experience", Codec.INT);
        ESSENCE_STORAGE = register("essence_storage", EssenceStorage.CODEC);
        BARRIER_FIRST_POINT = register("barrier_first_point", BlockPos.CODEC);
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
