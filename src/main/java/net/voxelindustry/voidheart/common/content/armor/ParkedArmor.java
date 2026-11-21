package net.voxelindustry.voidheart.common.content.armor;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * A frame standing where its wearer stepped out of it, holding its charge with nothing drawn. Nothing harms it and mobs ignore it; its wearer steps back
 * in with a use. Only a command removes it, and then it drops rather than vanish. If it ever falls out of the world, it goes home to its base.
 */
public class ParkedArmor extends Entity
{
    private static final EntityDataAccessor<ItemStack> FRAME = SynchedEntityData.defineId(ParkedArmor.class, EntityDataSerializers.ITEM_STACK);

    private static final double GRAVITY = 0.08;
    private static final double DRAG    = 0.98;

    public ParkedArmor(EntityType<? extends ParkedArmor> type, Level level)
    {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder)
    {
        builder.define(FRAME, ItemStack.EMPTY);
    }

    public ItemStack frame()
    {
        return entityData.get(FRAME);
    }

    public void setFrame(ItemStack frame)
    {
        entityData.set(FRAME, frame.copyWithCount(1));
    }

    @Override
    public void tick()
    {
        super.tick();
        if (!onGround())
            setDeltaMovement(getDeltaMovement().add(0, -GRAVITY, 0));
        move(MoverType.SELF, getDeltaMovement());
        setDeltaMovement(getDeltaMovement().multiply(onGround() ? 0 : DRAG, DRAG, onGround() ? 0 : DRAG));
        if (!level().isClientSide() && frame().isEmpty())
            discard();
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location)
    {
        if (!(player instanceof ServerPlayer serverPlayer))
            return InteractionResult.SUCCESS;
        var frame = frame();
        if (frame.isEmpty())
            return InteractionResult.PASS;
        var feet = position();
        var refusal = ArmorWear.refusal(serverPlayer, frame, feet);
        if (refusal != null)
        {
            serverPlayer.sendOverlayMessage(refusal);
            return InteractionResult.FAIL;
        }
        setFrame(ItemStack.EMPTY);
        discard();
        ArmorWear.stepIn(serverPlayer, frame, feet);
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount)
    {
        if (!source.is(DamageTypes.GENERIC_KILL) || isRemoved())
            return false;
        if (!frame().isEmpty())
            spawnAtLocation(level, frame());
        setFrame(ItemStack.EMPTY);
        discard();
        return true;
    }

    /**
     * A kill by command leaves the frame as an item, as one by damage does, rather than losing it.
     */
    @Override
    public void kill(ServerLevel level)
    {
        if (!frame().isEmpty())
            spawnAtLocation(level, frame());
        setFrame(ItemStack.EMPTY);
        super.kill(level);
    }

    /**
     * Fallen out of the world, it goes home to its base, or with none to go to, stands near the overworld's spawn where there is room to step in. With
     * no such room it stands on the spawn itself rather than being lost.
     */
    @Override
    protected void onBelowWorld()
    {
        if (level() instanceof ServerLevel level && !frame().isEmpty() && !ArmorWear.sendHome(level.getServer(), frame()))
        {
            var overworld = level.getServer().overworld();
            var spawn = Vec3.atBottomCenterOf(overworld.getRespawnData().pos());
            var spot = ArmorWear.roomNear(overworld, spawn, getYRot());
            ArmorWear.park(overworld, spot != null ? spot : spawn, getYRot(), frame());
        }
        setFrame(ItemStack.EMPTY);
        discard();
    }

    @Override
    public boolean isPickable()
    {
        return !isRemoved();
    }

    @Override
    public boolean isPushable()
    {
        return false;
    }

    @Override
    public boolean isAttackable()
    {
        return false;
    }

    @Override
    public boolean fireImmune()
    {
        return true;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input)
    {
        setFrame(input.read("frame", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output)
    {
        output.store("frame", ItemStack.OPTIONAL_CODEC, frame());
    }
}
