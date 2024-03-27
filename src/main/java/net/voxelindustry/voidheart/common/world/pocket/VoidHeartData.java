package net.voxelindustry.voidheart.common.world.pocket;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import lombok.extern.log4j.Log4j2;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.voxelindustry.voidheart.common.content.portalframe.PortalKey;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Log4j2
public final class VoidHeartData
{
    public static final Codec<VoidHeartData> CODEC = RecordCodecBuilder
            .create(instance -> instance.group(
                    BlockPos.CODEC.fieldOf("pocketPos").forGetter(VoidHeartData::pocketPos),
                    Codec.INT.fieldOf("maxFlexion").forGetter(VoidHeartData::maxFlexion),
                    Codec.INT.fieldOf("flexionCountWithoutPortals").orElse(0).forGetter(VoidHeartData::flexionCountWithoutPortals),
                    Codec.list(PortalKey.CODEC).fieldOf("portalsKeyList").orElse(Collections.emptyList()).forGetter(heartData -> List.copyOf(heartData.portalKeySet()))
            ).apply(instance, (pocketPos, maxFlexion, flexionCountWithoutPortals, portalKeys) ->
            {
                var heartData = new VoidHeartData(pocketPos, maxFlexion);
                heartData.flexionCountWithoutPortals = flexionCountWithoutPortals;
                heartData.portalKeyList.addAll(portalKeys);
                return heartData;
            }));

    private final BlockPos pocketPos;
    private final int maxFlexion;

    private int flexionCountWithoutPortals;
    private final Set<PortalKey> portalKeyList = new HashSet<>();

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

    public void addPortal(World fromWorld, BlockPos corePos)
    {
        portalKeyList.add(new PortalKey(fromWorld.getRegistryKey(), corePos));
        markDirty();
    }

    public void removePortal(World fromWorld, BlockPos corePos)
    {
        portalKeyList.remove(new PortalKey(fromWorld.getRegistryKey(), corePos));
        markDirty();
    }

    public static VoidHeartData fromNbt(NbtCompound compound)
    {
        return CODEC.decode(NbtOps.INSTANCE, compound)
                .resultOrPartial(log::error)
                .orElseThrow().getFirst();
    }

    public NbtElement toNbt()
    {
        return CODEC.encodeStart(NbtOps.INSTANCE, this).result().orElseThrow();
    }

    public BlockPos pocketPos()
    {
        return pocketPos;
    }

    public int maxFlexion()
    {
        return maxFlexion;
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

    public Text debugPrint()
    {
        var root = Text.literal("§6PocketPos: §3" + pocketPos.toShortString() + "\n");
        root.append(Text.literal("§6Max Flexion: §3" + maxFlexion + "\n"));
        root.append(Text.literal("§6Flexion Count (without portals): §3" + flexionCountWithoutPortals + "\n"));
        root.append(Text.literal("§6Portals Count: §3" + portalsCount() + "\n"));

        for (var key : portalKeyList)
            root.append(Text.literal(" -> §3"+key.toString() + "\n"));

        return root;
    }

    public static VoidHeartData create(BlockPos pocketPos)
    {
        return new VoidHeartData(pocketPos, 3);
    }
}
