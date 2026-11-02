package net.voxelindustry.voidheart.common.content.essence;

import com.geckolib.util.GeckoLibUtil;
import com.geckolib.animation.object.PlayState;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.state.AnimationTest;
import com.geckolib.animation.AnimationController;
import com.geckolib.animatable.manager.AnimatableManager.ControllerRegistrar;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.GeoBlockEntity;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.voxelindustry.steamlayer.tile.TileBase;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

public class RavenousCollectorTile extends TileBase implements GeoBlockEntity
{
    public static final int CAPACITY = 16_000;
    public static final int HARVESTING_TIME_TICKS = 50;
    public static final int HARVEST_AMOUNT = 1_000;

    protected static final RawAnimation COLLECTING = RawAnimation.begin().thenPlay("collecting");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private EssenceStorage storage = new EssenceStorage(EssenceRegistry.EMPTY, CAPACITY);

    private BlockPos harvestingPos = BlockPos.ZERO;
    private long harvestingTickStart = 0;

    private long searchBackoff = 0L;

    /**
     * Game time until which a Shatter Forge needs more essence than this collector holds, relays then show it being
     * fed from storages.
     */
    private long fedUntil;

    ////////////
    // CLIENT //
    ////////////

    private static final int HARVEST_STRAND_TICKS = 20;

    private BlockPos displayedHarvestPos;
    private int harvestStrandTicks;
    private int previousHarvestStrandTicks;

    public RavenousCollectorTile(BlockPos pos, BlockState state)
    {
        super(VoidHeartTiles.RAVENOUS_COLLECTOR, pos, state);
    }

    public static void tick(Level world, BlockPos pos, BlockState state, RavenousCollectorTile collector)
    {
        if (world.isClientSide())
        {
            collector.tickHarvestStrands();
            return;
        }

        if (collector.storage.isUnattuned())
            return;

        // Too full to take in another block: stop harvesting until essence is drawn
        if (collector.storage.spaceLeft() < HARVEST_AMOUNT)
        {
            if (collector.harvestingPos != BlockPos.ZERO)
                collector.abandonHarvest();
            return;
        }

        if (collector.harvestingPos == BlockPos.ZERO)
        {
            if (world.getGameTime() - collector.searchBackoff < 40)
                return;

            var found = collector.findRandomCollectable();
            if (found == BlockPos.ZERO)
            {
                collector.searchBackoff = world.getGameTime();
                return;
            }

            collector.startHarvesting(found);
            return;
        }

        if (!collector.isBlockCollectable(collector.harvestingPos))
            collector.abandonHarvest();
        else if (collector.canEndHarvest())
            collector.endHarvest();
    }

    /**
     * Harvest strands grow from the harvested block toward the collector, then retract into it once the harvest ends.
     */
    private void tickHarvestStrands()
    {
        previousHarvestStrandTicks = harvestStrandTicks;
        if (harvestingPos != BlockPos.ZERO)
        {
            if (!harvestingPos.equals(displayedHarvestPos))
            {
                displayedHarvestPos = harvestingPos;
                harvestStrandTicks = previousHarvestStrandTicks = 0;
            }
            harvestStrandTicks = Math.min(HARVEST_STRAND_TICKS, harvestStrandTicks + 1);
        }
        else
            harvestStrandTicks = Math.max(0, harvestStrandTicks - 1);
    }

    /**
     * @return position of the block harvest strands reach into, null when none are shown
     */
    public BlockPos getDisplayedHarvestPos()
    {
        return harvestStrandTicks > 0 || previousHarvestStrandTicks > 0 ? displayedHarvestPos : null;
    }

    /**
     * @return 0 when retracted, 1 when the strands reach the collector
     */
    public float getHarvestStrandGrowth(float partialTicks)
    {
        return Mth.lerp(partialTicks, previousHarvestStrandTicks, harvestStrandTicks) / HARVEST_STRAND_TICKS;
    }

    private void startHarvesting(BlockPos pos)
    {
        this.harvestingPos = pos;
        this.harvestingTickStart = level.getGameTime();
        sync();
    }

    private boolean canEndHarvest()
    {
        if (harvestingPos == BlockPos.ZERO || harvestingTickStart == 0)
            return false;

        return level.getGameTime() - harvestingTickStart >= HARVESTING_TIME_TICKS && isBlockCollectable(harvestingPos);
    }

    private void endHarvest()
    {
        level.setBlockAndUpdate(harvestingPos, Blocks.AIR.defaultBlockState());
        storage.fill(storage.type(), HARVEST_AMOUNT);

        harvestingPos = BlockPos.ZERO;
        harvestingTickStart = 0;
        setChanged();
        sync();
    }

    /**
     * The harvested block was removed or replaced before the harvest ended.
     */
    private void abandonHarvest()
    {
        harvestingPos = BlockPos.ZERO;
        harvestingTickStart = 0;
        setChanged();
        sync();
    }

    private BlockPos findRandomCollectable()
    {
        var iterablePos = BlockPos.randomInCube(level.getRandom(), 7 * 7 * 7, worldPosition, 3);

        for (var candidate : iterablePos)
        {
            if (isBlockCollectable(candidate))
                return candidate.immutable();
        }

        return BlockPos.ZERO;
    }

    private boolean isBlockCollectable(BlockPos pos)
    {
        return level.getBlockState(pos).is(storage.type().consumables());
    }

    @Override
    protected void loadAdditional(ValueInput nbt)
    {
        super.loadAdditional(nbt);

        harvestingTickStart = nbt.getLongOr("harvestingTickStart", 0L);
        // Keep the BlockPos.ZERO instance, "not harvesting" is checked by identity
        var savedHarvestingPos = nbt.getLongOr("harvestingPos", 0L);
        harvestingPos = savedHarvestingPos == 0L ? BlockPos.ZERO : BlockPos.of(savedHarvestingPos);
        storage = nbt.read("storage", EssenceStorage.CODEC).orElse(storage);
    }

    @Override
    protected void saveAdditional(ValueOutput nbt)
    {
        super.saveAdditional(nbt);

        nbt.putLong("harvestingTickStart", harvestingTickStart);
        nbt.putLong("harvestingPos", harvestingPos.asLong());
        nbt.store("storage", EssenceStorage.CODEC, storage);
    }

    @Override
    public void registerControllers(ControllerRegistrar controllers)
    {
        var animationController = new AnimationController<RavenousCollectorTile>("main", this::deployAnimController);
        controllers.add(animationController);
    }

    /**
     * Cycles while harvesting. Each cycle plays to its end so the corner cubes settle back in place once harvesting stops.
     */
    protected <E extends RavenousCollectorTile> PlayState deployAnimController(AnimationTest<E> state)
    {
        var controller = state.controller();
        if (state.isCurrentAnimation(COLLECTING) && !controller.hasAnimationFinished())
            return PlayState.CONTINUE;
        if (!state.animatable().isHarvesting())
            return PlayState.STOP;

        controller.reset();
        return state.setAndContinue(COLLECTING);
    }

    public void requestFeed(long until)
    {
        fedUntil = until;
    }

    public boolean isFed()
    {
        return level != null && level.getGameTime() < fedUntil;
    }

    public boolean isHarvesting()
    {
        return harvestingPos != BlockPos.ZERO;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache()
    {
        return this.cache;
    }

    public static EssenceStorage copyOf(EssenceStorage source)
    {
        var copy = new EssenceStorage(EssenceRegistry.EMPTY, CAPACITY);
        copy.attune(source.type());
        copy.fill(source.type(), source.amount());
        return copy;
    }

    public void restoreStorage(EssenceStorage source)
    {
        storage = copyOf(source);
        setChanged();
        sync();
    }

    public EssenceStorage storage()
    {
        return storage;
    }
}
