package net.voxelindustry.voidheart.common.content.eyebottle;

import com.mojang.authlib.GameProfile;
import lombok.Getter;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
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
    public void setWorld(World world)
    {
        super.setWorld(world);

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

        var gameProfileOpt = world.getServer().getUserCache().getByUuid(playerID);

        if (gameProfileOpt.isEmpty())
            return;

        playerProfile = gameProfileOpt.get();
        sync();
    }

    @Override
    public void readNbt(NbtCompound tag)
    {
        super.readNbt(tag);

        if (tag.containsUuid("playerID"))
            playerID = tag.getUuid("playerID");

        if (isClient() && tag.contains("ownerProfile"))
        {
            playerProfile = NbtHelper.toGameProfile(tag.getCompound("ownerProfile"));

            if (playerProfile != null && playerProfile.isComplete())
                MinecraftClient.getInstance().getSessionService().fillProfileProperties(playerProfile, true);
        }
    }

    @Override
    public void writeNbt(NbtCompound tag)
    {
        if (playerID != null)
        {
            tag.putUuid("playerID", playerID);

            if (isServer() && playerProfile != null)
            {
                var profileTag = new NbtCompound();
                NbtHelper.writeGameProfile(profileTag, playerProfile);
                tag.put("ownerProfile", profileTag);
            }
        }

        super.writeNbt(tag);
    }

    @Override
    public void registerControllers(ControllerRegistrar controllers)
    {
        controllers.add(new AnimationController<>(this, this::deployAnimController));
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
