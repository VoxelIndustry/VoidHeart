package net.voxelindustry.voidheart.common.world.pocket;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.voxelindustry.voidheart.common.content.portalframe.PortalKey;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class VoidHeartData
{
    public static final Codec<VoidHeartData> CODEC = RecordCodecBuilder
            .create(instance -> instance.group(
                    BlockPos.CODEC.fieldOf("pocketPos").forGetter(VoidHeartData::pocketPos),
                    Codec.INT.fieldOf("maxFlexion").forGetter(VoidHeartData::maxFlexion),
                    Codec.INT.fieldOf("flexionCountWithoutPortals").orElse(0).forGetter(VoidHeartData::flexionCountWithoutPortals),
                    Codec.list(PortalKey.CODEC).fieldOf("portalsKeyList").orElse(Collections.emptyList()).forGetter(heartData -> List.copyOf(heartData.portalKeySet())),
                    Codec.INT.fieldOf("heartRise").orElse(0).forGetter(VoidHeartData::heartRise)
            ).apply(instance, (pocketPos, maxFlexion, flexionCountWithoutPortals, portalKeys, heartRise) ->
            {
                var heartData = new VoidHeartData(pocketPos, maxFlexion);
                heartData.flexionCountWithoutPortals = flexionCountWithoutPortals;
                heartData.portalKeyList.addAll(portalKeys);
                heartData.heartRise = heartRise;
                return heartData;
            }));

    private static final int POCKET_REACH = 9;
    private static final int CENTER_ABOVE_HEART = 6;

    private final BlockPos pocketPos;
    private final int maxFlexion;

    private int flexionCountWithoutPortals;
    private final Set<PortalKey> portalKeyList = new HashSet<>();

    private int heartRise;

    private boolean isDirty;

    public VoidHeartData(BlockPos pocketPos, int maxFlexion)
    {
        this.pocketPos = pocketPos;
        this.maxFlexion = maxFlexion;
    }

    public boolean noFlexionLeft()
    {
        return maxFlexion <= flexionCountWithoutPortals + portalsCount() / 2;
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

    public int maxFlexion()
    {
        return maxFlexion;
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

    public boolean contains(BlockPos pos)
    {
        return Math.abs(pocketPos.getX() - pos.getX()) < POCKET_REACH
                && Math.abs(pocketPos.getY() + CENTER_ABOVE_HEART - heartRise - pos.getY()) < POCKET_REACH
                && Math.abs(pocketPos.getZ() - pos.getZ()) < POCKET_REACH;
    }

    public int currentFlexionCount()
    {
        return flexionCountWithoutPortals + portalsCount() / 2;
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
                this.maxFlexion == that.maxFlexion;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(pocketPos, maxFlexion);
    }

    @Override
    public String toString()
    {
        return "VoidHeartData[" +
                "pocketPos=" + pocketPos + ", " +
                "maxFlexion=" + maxFlexion + ']';
    }

    public Component debugPrint()
    {
        var root = Component.literal("§6PocketPos: §3" + pocketPos.toShortString() + "\n");
        root.append(Component.literal("§6Max Flexion: §3" + maxFlexion + "\n"));
        root.append(Component.literal("§6Flexion Count (without portals): §3" + flexionCountWithoutPortals + "\n"));
        root.append(Component.literal("§6Portals Count: §3" + portalsCount() + "\n"));

        for (var key : portalKeyList)
            root.append(Component.literal(" -> §3"+key.toString() + "\n"));

        return root;
    }

    public static VoidHeartData create(BlockPos pocketPos)
    {
        return new VoidHeartData(pocketPos, 3);
    }
}
