package net.voxelindustry.voidheart.common.content.essence;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
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

    private BlockPos harvestingPos = BlockPos.ORIGIN;
    private long harvestingTickStart = 0;

    private long searchBackoff = 0L;

    public RavenousCollectorTile(BlockPos pos, BlockState state)
    {
        super(VoidHeartTiles.RAVENOUS_COLLECTOR, pos, state);
    }

    public static void tick(World world, BlockPos pos, BlockState state, RavenousCollectorTile collector)
    {
        if (world.isClient)
            return;

        if (collector.storage.isUnattuned())
            return;

        if (collector.harvestingPos == BlockPos.ORIGIN)
        {
            if (world.getTime() - collector.searchBackoff < 40)
                return;

            var found = collector.findRandomCollectable();
            if (found == BlockPos.ORIGIN)
            {
                collector.searchBackoff = world.getTime();
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
        this.harvestingTickStart = world.getTime();
        sync();
    }

    private boolean canEndHarvest()
    {
        if (harvestingPos == BlockPos.ORIGIN || harvestingTickStart == 0)
            return false;

        if (storage.spaceLeft() < 1_000)
            return false;

        return world.getTime() - harvestingTickStart >= HARVESTING_TIME_TICKS && isBlockCollectable(harvestingPos);
    }

    private void endHarvest()
    {
        world.setBlockState(harvestingPos, Blocks.AIR.getDefaultState());
        storage.fill(storage.type(), 1_000);

        harvestingPos = BlockPos.ORIGIN;
        harvestingTickStart = 0;
    }

    private BlockPos findRandomCollectable()
    {
        var iterablePos = BlockPos.iterateRandomly(world.random, 7 * 7 * 7, pos, 3);

        for (var candidate : iterablePos)
        {
            if (isBlockCollectable(candidate))
                return candidate.toImmutable();
        }

        return BlockPos.ORIGIN;
    }

    private boolean isBlockCollectable(BlockPos pos)
    {
        return world.getBlockState(pos).isIn(storage.type().consumables());
    }

    @Override
    public void readNbt(NbtCompound nbt)
    {
        super.readNbt(nbt);

        harvestingTickStart = nbt.getLong("harvestingTickStart");
        harvestingPos = BlockPos.fromLong(nbt.getLong("harvestingPos"));
        storage = EssenceStorage.CODEC.decode(NbtOps.INSTANCE, nbt.getCompound("storage")).result().get().getFirst();
    }

    @Override
    protected void writeNbt(NbtCompound nbt)
    {
        super.writeNbt(nbt);

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
