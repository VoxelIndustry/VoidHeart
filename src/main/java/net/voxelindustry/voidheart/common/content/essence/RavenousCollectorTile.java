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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.voxelindustry.steamlayer.tile.TileBase;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

public class RavenousCollectorTile extends TileBase implements GeoBlockEntity
{
    public static final int CAPACITY = 16_000;
    public static final int HARVESTING_TIME_TICKS = 50;

    protected static final RawAnimation IDLE = RawAnimation.begin().thenLoop("collecting");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private EssenceStorage storage = new EssenceStorage(EssenceRegistry.EMPTY, CAPACITY);

    private BlockPos harvestingPos = BlockPos.ZERO;
    private long harvestingTickStart = 0;

    private long searchBackoff = 0L;

    public RavenousCollectorTile(BlockPos pos, BlockState state)
    {
        super(VoidHeartTiles.RAVENOUS_COLLECTOR, pos, state);
    }

    public static void tick(Level world, BlockPos pos, BlockState state, RavenousCollectorTile collector)
    {
        if (world.isClientSide())
            return;

        if (collector.storage.isUnattuned())
            return;

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

        if (collector.canEndHarvest())
            collector.endHarvest();
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

        if (storage.spaceLeft() < 1_000)
            return false;

        return level.getGameTime() - harvestingTickStart >= HARVESTING_TIME_TICKS && isBlockCollectable(harvestingPos);
    }

    private void endHarvest()
    {
        level.setBlockAndUpdate(harvestingPos, Blocks.AIR.defaultBlockState());
        storage.fill(storage.type(), 1_000);

        harvestingPos = BlockPos.ZERO;
        harvestingTickStart = 0;
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
        harvestingPos = BlockPos.of(nbt.getLongOr("harvestingPos", 0L));
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

    protected <E extends RavenousCollectorTile> PlayState deployAnimController(AnimationTest<E> state)
    {
        return state.setAndContinue(IDLE);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache()
    {
        return this.cache;
    }

    public EssenceStorage storage()
    {
        return storage;
    }
}
