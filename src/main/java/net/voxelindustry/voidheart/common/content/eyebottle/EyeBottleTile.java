package net.voxelindustry.voidheart.common.content.eyebottle;

import com.mojang.authlib.GameProfile;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.Level;
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
import software.bernie.geckolib.util.ClientUtils;
import software.bernie.geckolib.util.GeckoLibUtil;

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
    private GameProfile playerProfile;
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

        var gameProfileOpt = level.getServer().getProfileCache().get(playerID);

        if (gameProfileOpt.isEmpty())
            return;

        playerProfile = gameProfileOpt.get();
        sync();
    }

    @Override
    public void load(CompoundTag tag)
    {
        super.load(tag);

        if (tag.hasUUID("playerID"))
            playerID = tag.getUUID("playerID");

        if (isClient() && tag.contains("ownerProfile"))
        {
            playerProfile = NbtUtils.readGameProfile(tag.getCompound("ownerProfile"));

            if (playerProfile != null && playerProfile.isComplete())
                Minecraft.getInstance().getMinecraftSessionService().fillProfileProperties(playerProfile, true);
        }
    }

    @Override
    public void saveAdditional(CompoundTag tag)
    {
        if (playerID != null)
        {
            tag.putUUID("playerID", playerID);

            if (isServer() && playerProfile != null)
            {
                var profileTag = new CompoundTag();
                NbtUtils.writeGameProfile(profileTag, playerProfile);
                tag.put("ownerProfile", profileTag);
            }
        }

        super.saveAdditional(tag);
    }

    @Override
    public void registerControllers(ControllerRegistrar controllers)
    {
        var animationController = new AnimationController<>(this, this::deployAnimController);
        animationController.setSoundKeyframeHandler(event ->
        {
            var player = ClientUtils.getClientPlayer();

            if (player != null)
                player.playSound(SoundEvents.GLASS_PLACE, 0.1F, player.getRandom().nextFloat() * 0.1F + 0.9F);
        });
        controllers.add(animationController);
    }

    protected <E extends EyeBottleTile> PlayState deployAnimController(AnimationState<E> state)
    {
        return state.setAndContinue(IDLE);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache()
    {
        return this.cache;
    }
}
