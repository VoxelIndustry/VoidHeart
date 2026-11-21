package net.voxelindustry.voidheart.common.content.crystal;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartDataComponents;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;
import org.jspecify.annotations.Nullable;

/**
 * The crystals' shards and bundle blocks, and the habit they carry: a block state on a placed bundle, a component on an item.
 * <p>
 * A bundle item keeps {@link CrystalHabit#PLAIN} to say it grew in a cradle, so it breaks into exactly what grew it rather than what fortune makes of a
 * world bundle. A shard only keeps a real habit, so plain shards stack with those mined in the world.
 */
public final class CrystalBundles
{
    private CrystalBundles()
    {
    }

    public static Item shard(CrystalKind kind)
    {
        return switch (kind)
        {
            case AZURIUM -> VoidHeartItems.AZURIUM_SHARD;
            case RUBRUM -> VoidHeartItems.RUBRUM_SHARD;
            case MEMORIAM -> VoidHeartItems.MEMORIAM_SHARD;
        };
    }

    public static Block bundle(CrystalKind kind, BundleSize size)
    {
        return switch (kind)
        {
            case AZURIUM -> switch (size)
            {
                case SMALL -> VoidHeartBlocks.SMALL_AZURIUM_BUNDLE;
                case MEDIUM -> VoidHeartBlocks.MEDIUM_AZURIUM_BUNDLE;
                case LARGE -> VoidHeartBlocks.LARGE_AZURIUM_BUNDLE;
            };
            case RUBRUM -> switch (size)
            {
                case SMALL -> VoidHeartBlocks.SMALL_RUBRUM_BUNDLE;
                case MEDIUM -> VoidHeartBlocks.MEDIUM_RUBRUM_BUNDLE;
                case LARGE -> VoidHeartBlocks.LARGE_RUBRUM_BUNDLE;
            };
            case MEMORIAM -> switch (size)
            {
                case SMALL -> VoidHeartBlocks.SMALL_MEMORIAM_BUNDLE;
                case MEDIUM -> VoidHeartBlocks.MEDIUM_MEMORIAM_BUNDLE;
                case LARGE -> VoidHeartBlocks.LARGE_MEMORIAM_BUNDLE;
            };
        };
    }

    public static @Nullable CrystalKind kindOfShard(ItemStack stack)
    {
        for (var kind : CrystalKind.values())
        {
            if (stack.is(shard(kind)))
                return kind;
        }
        return null;
    }

    public static @Nullable CrystalKind kindOfBundle(Block block)
    {
        for (var kind : CrystalKind.values())
        {
            for (var size : BundleSize.values())
            {
                if (bundle(kind, size) == block)
                    return kind;
            }
        }
        return null;
    }

    public static @Nullable BundleSize sizeOf(Block block)
    {
        for (var kind : CrystalKind.values())
        {
            for (var size : BundleSize.values())
            {
                if (bundle(kind, size) == block)
                    return size;
            }
        }
        return null;
    }

    public static @Nullable Block bundleOf(ItemStack stack)
    {
        return stack.getItem() instanceof BlockItem item && kindOfBundle(item.getBlock()) != null ? item.getBlock() : null;
    }

    public static CrystalHabit habitOf(ItemStack stack)
    {
        return stack.getOrDefault(VoidHeartDataComponents.CRYSTAL_HABIT, CrystalHabit.NONE);
    }

    public static CrystalHabit habitOf(BlockState state)
    {
        return state.hasProperty(CrystalBundleBlock.HABIT) ? state.getValue(CrystalBundleBlock.HABIT) : CrystalHabit.NONE;
    }

    /**
     * Sets the habit the way the item keeps it: none at all on world crystal, and on a shard only a real habit.
     */
    public static ItemStack withHabit(ItemStack stack, CrystalHabit habit)
    {
        boolean keep = habit.isHabit() || (habit == CrystalHabit.PLAIN && bundleOf(stack) != null);
        if (keep)
            stack.set(VoidHeartDataComponents.CRYSTAL_HABIT, habit);
        else
            stack.remove(VoidHeartDataComponents.CRYSTAL_HABIT);
        return stack;
    }

    /**
     * The item a placed bundle is when taken whole, with its habit.
     */
    public static ItemStack itemOf(BlockState bundle)
    {
        return withHabit(new ItemStack(bundle.getBlock()), habitOf(bundle));
    }

    public static ItemStack bundleItem(CrystalKind kind, BundleSize size, CrystalHabit habit)
    {
        return withHabit(new ItemStack(bundle(kind, size)), habit);
    }

    public static ItemStack shards(CrystalKind kind, int count, CrystalHabit habit)
    {
        return withHabit(new ItemStack(shard(kind), count), habit);
    }
}
