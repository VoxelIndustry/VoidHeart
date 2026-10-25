package net.voxelindustry.voidheart.common.content.essence;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.voxelindustry.steamlayer.tile.TileBase;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager.ControllerRegistrar;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

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
        if (world.isClientSide)
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
        var iterablePos = BlockPos.randomInCube(level.random, 7 * 7 * 7, worldPosition, 3);

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
    public void load(CompoundTag nbt)
    {
        super.load(nbt);

        harvestingTickStart = nbt.getLong("harvestingTickStart");
        harvestingPos = BlockPos.of(nbt.getLong("harvestingPos"));
        storage = EssenceStorage.CODEC.decode(NbtOps.INSTANCE, nbt.getCompound("storage")).result().get().getFirst();
    }

    @Override
    protected void saveAdditional(CompoundTag nbt)
    {
        super.saveAdditional(nbt);

        nbt.putLong("harvestingTickStart", harvestingTickStart);
        nbt.putLong("harvestingPos", harvestingPos.asLong());
        nbt.put("storage", EssenceStorage.CODEC.encodeStart(NbtOps.INSTANCE, storage).result().get());
    }

    @Override
    public void registerControllers(ControllerRegistrar controllers)
    {
        var animationController = new AnimationController<>(this, this::deployAnimController);
        controllers.add(animationController);
    }

    protected <E extends RavenousCollectorTile> PlayState deployAnimController(AnimationState<E> state)
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
