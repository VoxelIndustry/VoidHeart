package net.voxelindustry.voidheart.common.world.pocket;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.Level;
import net.voxelindustry.voidheart.common.content.portalframe.PortalKey;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class VoidHeartData
{
    /** What the heart alone allows; each body rite after it adds one. */
    public static final int BASE_FLEXION = 3;

    public static final Codec<VoidHeartData> CODEC = RecordCodecBuilder
            .create(instance -> instance.group(
                    BlockPos.CODEC.fieldOf("pocketPos").forGetter(VoidHeartData::pocketPos),
                    Codec.INT.optionalFieldOf("bonusFlexion").forGetter(heartData -> Optional.of(heartData.bonusFlexion)),
                    // Saved before the rites set the flexion, read once and turned into a bonus over the base
                    Codec.INT.optionalFieldOf("maxFlexion").forGetter(heartData -> Optional.empty()),
                    Codec.INT.fieldOf("flexionCountWithoutPortals").orElse(0).forGetter(VoidHeartData::flexionCountWithoutPortals),
                    Codec.list(PortalKey.CODEC).fieldOf("portalsKeyList").orElse(Collections.emptyList()).forGetter(heartData -> List.copyOf(heartData.portalKeySet())),
                    Codec.INT.fieldOf("heartRise").orElse(0).forGetter(VoidHeartData::heartRise),
                    StringRepresentable.fromEnum(PocketVariant::values).optionalFieldOf("variant").forGetter(VoidHeartData::variant),
                    Codec.list(BlockPos.CODEC).fieldOf("riftDoors").orElse(Collections.emptyList()).forGetter(heartData -> List.copyOf(heartData.riftDoors))
            ).apply(instance, (pocketPos, bonusFlexion, legacyMaxFlexion, flexionCountWithoutPortals, portalKeys, heartRise, variant, riftDoors) ->
            {
                var heartData = new VoidHeartData(pocketPos);
                heartData.bonusFlexion = bonusFlexion.orElseGet(() -> legacyMaxFlexion.map(max -> max - BASE_FLEXION).orElse(0));
                heartData.flexionCountWithoutPortals = flexionCountWithoutPortals;
                heartData.portalKeyList.addAll(portalKeys);
                heartData.heartRise = heartRise;
                heartData.variant = variant.orElse(null);
                heartData.riftDoors.addAll(riftDoors);
                return heartData;
            }));

    private static final int POCKET_REACH = 9;
    private static final int CENTER_ABOVE_HEART = 6;

    private final BlockPos pocketPos;
    /** Given by command over what the rites allow, may be negative. */
    private int bonusFlexion;

    private int flexionCountWithoutPortals;
    private final Set<PortalKey> portalKeyList = new HashSet<>();

    /** Void doors of the pocket that Rift Keys open onto, each holding one flexion. */
    private final Set<BlockPos> riftDoors = new HashSet<>();

    private int heartRise;
    /** Null for pockets made before the variant was saved. */
    @Nullable
    private PocketVariant variant;

    private boolean isDirty;

    public VoidHeartData(BlockPos pocketPos)
    {
        this.pocketPos = pocketPos;
    }

    /** What the body rites allow: the heart gives the base and each rite after it one more. Owners of a pocket count as having given the heart. */
    public static int ritesFlexion(int attunement)
    {
        return BASE_FLEXION + Math.max(0, attunement - 1);
    }

    /** @param attunement the owner's attunement to the void */
    public boolean noFlexionLeft(int attunement)
    {
        return maxFlexion(attunement) <= currentFlexionCount();
    }

    public void addPortal(Level fromWorld, BlockPos corePos)
    {
        portalKeyList.add(new PortalKey(fromWorld.dimension(), corePos));
        markDirty();
    }

    public void removePortal(Level fromWorld, BlockPos corePos)
    {
        portalKeyList.remove(new PortalKey(fromWorld.dimension(), corePos));
        markDirty();
    }

    public BlockPos pocketPos()
    {
        return pocketPos;
    }

    /** @param attunement the owner's attunement to the void */
    public int maxFlexion(int attunement)
    {
        return Math.max(0, ritesFlexion(attunement) + bonusFlexion);
    }

    public int bonusFlexion()
    {
        return bonusFlexion;
    }

    public void bonusFlexion(int bonusFlexion)
    {
        this.bonusFlexion = bonusFlexion;
        markDirty();
    }

    /**
     * How many blocks the heart sits above where it would in a pocket without terrain, which lets a lake lie below it. The pocket keeps its size and moves down instead.
     */
    public int heartRise()
    {
        return heartRise;
    }

    public void heartRise(int heartRise)
    {
        this.heartRise = heartRise;
        markDirty();
    }

    public Optional<PocketVariant> variant()
    {
        return Optional.ofNullable(variant);
    }

    public void variant(PocketVariant variant)
    {
        this.variant = variant;
        markDirty();
    }

    public void removePortal(PortalKey key)
    {
        portalKeyList.remove(key);
        markDirty();
    }

    public boolean contains(BlockPos pos)
    {
        return Math.abs(pocketPos.getX() - pos.getX()) < POCKET_REACH
                && Math.abs(pocketPos.getY() + CENTER_ABOVE_HEART - heartRise - pos.getY()) < POCKET_REACH
                && Math.abs(pocketPos.getZ() - pos.getZ()) < POCKET_REACH;
    }

    public int currentFlexionCount()
    {
        return flexionCountWithoutPortals + portalsCount() / 2 + riftDoors.size();
    }

    public boolean holdsRiftDoor(BlockPos door)
    {
        return riftDoors.contains(door);
    }

    public void addRiftDoor(BlockPos door)
    {
        if (riftDoors.add(door.immutable()))
            markDirty();
    }

    public boolean removeRiftDoor(BlockPos door)
    {
        if (!riftDoors.remove(door))
            return false;
        markDirty();
        return true;
    }

    public Set<BlockPos> riftDoors()
    {
        return Collections.unmodifiableSet(riftDoors);
    }

    private void flexionCountWithoutPortals(int currentFlexionCount)
    {
        this.flexionCountWithoutPortals = currentFlexionCount;
    }

    public int flexionCountWithoutPortals()
    {
        return flexionCountWithoutPortals;
    }

    public int portalsCount()
    {
        return portalKeyList.size();
    }

    public Set<PortalKey> portalKeySet()
    {
        return portalKeyList;
    }

    private void markDirty()
    {
        this.isDirty = true;
    }

    public boolean isDirty()
    {
        return isDirty;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (VoidHeartData) obj;
        return Objects.equals(this.pocketPos, that.pocketPos) &&
                this.bonusFlexion == that.bonusFlexion;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(pocketPos, bonusFlexion);
    }

    @Override
    public String toString()
    {
        return "VoidHeartData[" +
                "pocketPos=" + pocketPos + ", " +
                "bonusFlexion=" + bonusFlexion + ']';
    }

    public Component debugPrint()
    {
        var root = Component.literal("§6PocketPos: §3" + pocketPos.toShortString() + "\n");
        root.append(Component.literal("§6Variant: §3" + variant().map(PocketVariant::getSerializedName).orElse("unknown") + "\n"));
        root.append(Component.literal("§6Bonus Flexion: §3" + bonusFlexion + "\n"));
        root.append(Component.literal("§6Flexion Count (without portals): §3" + flexionCountWithoutPortals + "\n"));
        root.append(Component.literal("§6Portals Count: §3" + portalsCount() + "\n"));
        root.append(Component.literal("§6Rift Doors: §3" + riftDoors.size() + "\n"));

        for (var key : portalKeyList)
            root.append(Component.literal(" -> §3"+key.toString() + "\n"));

        return root;
    }

    public static VoidHeartData create(BlockPos pocketPos)
    {
        return new VoidHeartData(pocketPos);
    }
}
