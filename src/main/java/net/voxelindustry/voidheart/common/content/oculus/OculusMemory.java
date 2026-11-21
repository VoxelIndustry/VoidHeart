package net.voxelindustry.voidheart.common.content.oculus;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.ItemStack;
import net.voxelindustry.voidheart.common.content.crystal.BundleSize;
import net.voxelindustry.voidheart.common.content.crystal.CrystalBundles;
import net.voxelindustry.voidheart.common.content.crystal.CrystalHabit;
import net.voxelindustry.voidheart.common.content.crystal.CrystalKind;
import net.voxelindustry.voidheart.common.content.crystal.CrystalRecordings;
import net.voxelindustry.voidheart.common.setup.VoidHeartDataComponents;
import org.jspecify.annotations.Nullable;

/**
 * A Deep memoriam set on a table, whose recorded place the table shows instead of its own land. The bundle is kept as what it is, so it comes back out
 * the same.
 */
public record OculusMemory(CrystalRecordings.Ref recording, BundleSize size)
{
    private static final Codec<BundleSize> SIZE = Codec.STRING.comapFlatMap(name -> java.util.Arrays.stream(BundleSize.values())
                    .filter(size -> size.name().equalsIgnoreCase(name)).findFirst().map(com.mojang.serialization.DataResult::success)
                    .orElseGet(() -> com.mojang.serialization.DataResult.error(() -> "Unknown bundle size " + name)),
            size -> size.name().toLowerCase(java.util.Locale.ROOT));

    public static final Codec<OculusMemory> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            CrystalRecordings.Ref.CODEC.fieldOf("recording").forGetter(OculusMemory::recording),
            SIZE.fieldOf("size").forGetter(OculusMemory::size)
    ).apply(instance, OculusMemory::new));

    /**
     * The memory a bundle holds, or null for anything but a Deep memoriam that recorded somewhere.
     */
    public static @Nullable OculusMemory of(ItemStack stack)
    {
        var bundle = CrystalBundles.bundleOf(stack);
        var recording = stack.get(VoidHeartDataComponents.CRYSTAL_RECORDING);
        if (bundle == null || recording == null || CrystalBundles.kindOfBundle(bundle) != CrystalKind.MEMORIAM
            || CrystalBundles.habitOf(stack) != CrystalHabit.DEEP)
            return null;
        return new OculusMemory(recording, CrystalBundles.sizeOf(bundle));
    }

    public ItemStack toStack()
    {
        var stack = CrystalBundles.bundleItem(CrystalKind.MEMORIAM, size, CrystalHabit.DEEP);
        stack.set(VoidHeartDataComponents.CRYSTAL_RECORDING, recording);
        return stack;
    }
}
