package net.voxelindustry.voidheart.common.content.tormenter;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.cubemob.MagmaCube;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.steamlayer.tile.TileBase;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;
import org.jetbrains.annotations.Nullable;

public class MalignantTormenterTile extends TileBase
{
    public static final int LOOT_INTERVAL_TICKS = 40;

    // The droplet fills everything above, so loot comes out around the tip and is thrown clear of the belly
    private static final double LOOT_HEIGHT       = 0.3;
    private static final double LOOT_FROM_MIDDLE  = 0.95;
    private static final double LOOT_THROW        = 0.14;
    private static final double LOOT_LIFT         = 0.12;
    private static final float  SOUND_CHANCE      = 0.5F;
    private static final float  DEATH_SOUND_SHARE = 0.25F;
    private static final float  SOUND_VOLUME      = 0.6F;
    private static final float  SOUND_PITCH_LEAST = 0.7F;
    private static final float  SOUND_PITCH_RANGE = 0.4F;

    @Nullable
    private EntityType<?> soulType;
    private int           soulCount;

    /**
     * Never added to the world: it only stands in for the mob when asking for its loot and voice.
     */
    @Nullable
    private Mob     victim;
    private boolean victimUnavailable;

    public MalignantTormenterTile(BlockPos pos, BlockState state)
    {
        super(VoidHeartTiles.MALIGNANT_TORMENTER, pos, state);
    }

    void tickServer(ServerLevel level)
    {
        if (!isSated())
            return;

        // Spread across tormenters so that a farm does not drop and scream all on one tick
        if (Math.floorMod(level.getGameTime() + worldPosition.asLong(), LOOT_INTERVAL_TICKS) != 0)
            return;

        var mob = victim(level);
        if (mob == null)
            return;

        dropLoot(level, mob);
        scream(level, mob);
    }

    @Nullable
    private Mob victim(ServerLevel level)
    {
        if (victim == null && !victimUnavailable && soulType != null)
        {
            if (soulType.create(level, EntitySpawnReason.TRIGGERED) instanceof Mob mob)
            {
                mob.setPos(Vec3.atCenterOf(worldPosition));
                // The loot table of a magma cube only pays out on the larger ones
                if (mob instanceof MagmaCube cube)
                    cube.setSize(2, false);
                victim = mob;
            }
            else
                victimUnavailable = true;
        }
        return victim;
    }

    /**
     * Rolled as a player's kill: some mobs, like the blaze, hold nothing else in their loot table.
     */
    private void dropLoot(ServerLevel level, Mob mob)
    {
        var table = mob.getLootTable();
        if (table.isEmpty())
            return;

        var killer = FakePlayer.get(level);
        var params = new LootParams.Builder(level)
                .withParameter(LootContextParams.THIS_ENTITY, mob)
                .withParameter(LootContextParams.ORIGIN, mob.position())
                .withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().playerAttack(killer))
                .withParameter(LootContextParams.ATTACKING_ENTITY, killer)
                .withParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, killer)
                .withParameter(LootContextParams.LAST_DAMAGE_PLAYER, killer)
                .create(LootContextParamSets.ENTITY);

        level.getServer().reloadableRegistries().getLootTable(table.get())
                .getRandomItems(params, stack -> giveLoot(level, stack));
    }

    /**
     * A container underneath takes the loot first, what it has no room for falls out into the world.
     */
    private void giveLoot(ServerLevel level, ItemStack stack)
    {
        var left = stack;
        var container = HopperBlockEntity.getContainerAt(level, worldPosition.below());
        if (container != null)
            left = HopperBlockEntity.addItem(null, container, stack, Direction.UP);
        if (!left.isEmpty())
            spit(level, left);
    }

    private void spit(ServerLevel level, ItemStack stack)
    {
        var angle = level.getRandom().nextDouble() * Math.TAU;
        var outX = Math.cos(angle);
        var outZ = Math.sin(angle);
        var item = new ItemEntity(level, worldPosition.getX() + 0.5 + outX * LOOT_FROM_MIDDLE, worldPosition.getY() + LOOT_HEIGHT, worldPosition.getZ() + 0.5 + outZ * LOOT_FROM_MIDDLE, stack,
                outX * LOOT_THROW, LOOT_LIFT, outZ * LOOT_THROW);
        item.setDefaultPickUpDelay();
        level.addFreshEntity(item);
    }

    private void scream(ServerLevel level, Mob mob)
    {
        var random = level.getRandom();
        if (random.nextFloat() >= SOUND_CHANCE)
            return;

        var pitch = SOUND_PITCH_LEAST + random.nextFloat() * SOUND_PITCH_RANGE;
        var sound = soundOf(level, mob, random.nextFloat() < DEATH_SOUND_SHARE);
        if (sound != null)
            level.playSound(null, worldPosition, sound, SoundSource.HOSTILE, SOUND_VOLUME, pitch);
    }

    /**
     * Not every mob has an idle sound, creepers and slimes among them, while all of them can be hurt.
     */
    @Nullable
    private static SoundEvent soundOf(ServerLevel level, Mob mob, boolean dying)
    {
        var hurt = mob.getHurtSound(level.damageSources().generic());
        if (dying)
        {
            var death = mob.getDeathSound();
            return death != null ? death : hurt;
        }

        var ambient = mob.getAmbientSound();
        return ambient != null ? ambient : hurt;
    }

    void restoreSouls(TormenterSouls souls)
    {
        setSouls(souls.type(), souls.souls());
        setChanged();
        if (isServer())
            sync();
    }

    private void setSouls(@Nullable EntityType<?> type, int count)
    {
        var usable = type != null && count > 0;
        soulType = usable ? type : null;
        soulCount = usable ? Math.min(count, TormenterSouls.REQUIRED) : 0;
        victim = null;
        victimUnavailable = false;
    }

    @Nullable
    public TormenterSouls souls()
    {
        return soulType == null ? null : new TormenterSouls(soulType, soulCount);
    }

    public boolean isSated()
    {
        return soulCount >= TormenterSouls.REQUIRED;
    }

    @Override
    protected void loadAdditional(ValueInput nbt)
    {
        super.loadAdditional(nbt);

        // A mob from a mod since removed leaves an empty tormenter, rather than one with souls of nothing
        setSouls(nbt.read("soulType", BuiltInRegistries.ENTITY_TYPE.byNameCodec()).orElse(null), nbt.getIntOr("soulCount", 0));
    }

    @Override
    protected void saveAdditional(ValueOutput nbt)
    {
        super.saveAdditional(nbt);

        if (soulType != null)
        {
            nbt.store("soulType", BuiltInRegistries.ENTITY_TYPE.byNameCodec(), soulType);
            nbt.putInt("soulCount", soulCount);
        }
    }
}
