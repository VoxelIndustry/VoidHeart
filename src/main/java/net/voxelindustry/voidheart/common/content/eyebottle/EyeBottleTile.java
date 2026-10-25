package net.voxelindustry.voidheart.common.content.eyebottle;

import com.geckolib.util.GeckoLibUtil;
import com.geckolib.util.ClientUtil;
import com.geckolib.animation.object.PlayState;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.state.AnimationTest;
import com.geckolib.animation.AnimationController;
import com.geckolib.animatable.manager.AnimatableManager.ControllerRegistrar;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.GeoBlockEntity;
import net.voxelindustry.voidheart.common.util.ProfileUtil;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.voxelindustry.steamlayer.tile.TileBase;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class EyeBottleTile extends TileBase implements GeoBlockEntity
{
    protected static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    @Getter
    private UUID playerID;
    @Getter
    private ResolvableProfile playerProfile;
    private final Map<UUID, Long> lastPlayerHitCache = new HashMap<>();

    public EyeBottleTile(BlockPos pos, BlockState state)
    {
        super(VoidHeartTiles.EYE_BOTTLE, pos, state);
    }

    @Override
    public void setLevel(Level world)
    {
        super.setLevel(world);

        refreshPlayerProfile();
    }

    public void setPlayerID(UUID playerID)
    {
        this.playerID = playerID;

        refreshPlayerProfile();
    }

    private void refreshPlayerProfile()
    {
        if (!isServer() || playerID == null)
            return;

        ProfileUtil.resolve(level.getServer(), playerID, profile ->
        {
            playerProfile = profile;
            sync();
        });
    }

    @Override
    protected void loadAdditional(ValueInput tag)
    {
        super.loadAdditional(tag);

        if (tag.read("playerID", UUIDUtil.CODEC).isPresent())
            playerID = tag.read("playerID", UUIDUtil.CODEC).orElseThrow();

        if (isClient())
            playerProfile = tag.read("ownerProfile", ResolvableProfile.CODEC).orElse(null);
    }

    @Override
    protected void saveAdditional(ValueOutput tag)
    {
        if (playerID != null)
        {
            tag.store("playerID", UUIDUtil.CODEC, playerID);

            if (isServer() && playerProfile != null)
                tag.store("ownerProfile", ResolvableProfile.CODEC, playerProfile);
        }

        super.saveAdditional(tag);
    }

    @Override
    public void registerControllers(ControllerRegistrar controllers)
    {
        var animationController = new AnimationController<EyeBottleTile>("main", this::deployAnimController);
        animationController.setSoundKeyframeHandler(event ->
        {
            var player = ClientUtil.getClientPlayer();

            if (player != null)
                player.playSound(SoundEvents.GLASS_PLACE, 0.1F, player.getRandom().nextFloat() * 0.1F + 0.9F);
        });
        controllers.add(animationController);
    }

    protected <E extends EyeBottleTile> PlayState deployAnimController(AnimationTest<E> state)
    {
        return state.setAndContinue(IDLE);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache()
    {
        return this.cache;
    }
}
