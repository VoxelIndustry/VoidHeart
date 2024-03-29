package net.voxelindustry.voidheart.common.content.ravenouscollector;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
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
    protected static final RawAnimation IDLE = RawAnimation.begin().thenLoop("collecting");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public RavenousCollectorTile(BlockPos pos, BlockState state)
    {
        super(VoidHeartTiles.RAVENOUS_COLLECTOR, pos, state);
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
}
