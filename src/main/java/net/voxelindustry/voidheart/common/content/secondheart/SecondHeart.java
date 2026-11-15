package net.voxelindustry.voidheart.common.content.secondheart;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.sin.SinStagger;
import net.voxelindustry.voidheart.common.setup.VoidHeartSounds;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * The second heart: lethal damage dissolves its player instead of killing them, then rebuilds them where they fell at full health, knocking
 * back everything around. The life then comes back on the player's own online ticks.
 */
public final class SecondHeart
{
    public static final AttachmentType<HeartState> HEART = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(MODID, "second_heart"),
            builder -> builder.persistent(HeartState.CODEC).copyOnDeath());

    /** Only whether the heart is there and ready reaches the client, and only when it changes. */
    public static final AttachmentType<HeartShown> SHOWN = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(MODID, "second_heart_shown"),
            builder -> builder.syncWith(HeartShown.STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));

    public static final AttachmentType<HeartSequence> SEQUENCE = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(MODID, "second_heart_sequence"),
            builder -> builder.syncWith(HeartSequence.STREAM_CODEC, AttachmentSyncPredicate.all()));

    private static final double TARGET_CLEAR_RANGE = 48;
    private static final double HOLD_SLACK         = 0.05;
    private static final float  READY_VOLUME       = 0.45F;

    private static final Map<UUID, Vec3> LAST_SAFE = new HashMap<>();

    private SecondHeart()
    {
    }

    public static void register()
    {
        ServerLivingEntityEvents.ALLOW_DEATH.register(SecondHeart::allowDeath);
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !(entity instanceof Player player) || !unhurtable(player, source));
        ServerTickEvents.END_SERVER_TICK.register(SecondHeart::tickServer);
        ServerPlayerEvents.LEAVE.register(SecondHeart::onLeave);

        UseItemCallback.EVENT.register((player, level, hand) -> held(player) ? InteractionResult.FAIL : InteractionResult.PASS);
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> held(player) ? InteractionResult.FAIL : InteractionResult.PASS);
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> held(player) ? InteractionResult.FAIL : InteractionResult.PASS);
        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> held(player) ? InteractionResult.FAIL : InteractionResult.PASS);
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> held(player) ? InteractionResult.FAIL : InteractionResult.PASS);
    }

    public static void clear()
    {
        LAST_SAFE.clear();
    }

    public static HeartState state(Player player)
    {
        return player.getAttachedOrElse(HEART, HeartState.NONE);
    }

    public static @Nullable HeartSequence sequence(Player player)
    {
        return player.getAttached(SEQUENCE);
    }

    public static float elapsed(Player player, float partialTick)
    {
        var sequence = sequence(player);
        return sequence == null ? -1 : sequence.elapsed(player.level().getGameTime(), partialTick);
    }

    /**
     * @return whether the player is dissolved or reforming, so can't act, be targeted or be pushed
     */
    public static boolean held(Player player)
    {
        var sequence = sequence(player);
        if (sequence == null)
            return false;
        var phase = HeartRules.phase((int) (player.level().getGameTime() - sequence.start()));
        return phase == HeartRules.Phase.DISSOLVE || phase == HeartRules.Phase.REFORM;
    }

    private static boolean unhurtable(Player player, DamageSource source)
    {
        if (source.is(DamageTypes.GENERIC_KILL))
            return false;
        var sequence = sequence(player);
        return sequence != null && player.level().getGameTime() - sequence.start() < HeartRules.SEQUENCE_TICKS;
    }

    private static boolean allowDeath(LivingEntity entity, DamageSource source, float amount)
    {
        if (!(entity instanceof ServerPlayer player))
            return true;
        var state = state(player);
        var verdict = HeartRules.onLethal(state.owned(), state.recharge(), sequence(player) != null, totemWouldSave(player, source),
                source.is(DamageTypes.GENERIC_KILL));
        switch (verdict)
        {
            case HEART ->
            {
                fire(player, wound(player, source));
                return false;
            }
            case HOLD ->
            {
                player.setHealth(1);
                return false;
            }
            default ->
            {
                return true;
            }
        }
    }

    private static boolean totemWouldSave(Player player, DamageSource source)
    {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY))
            return false;
        for (var hand : InteractionHand.values())
            if (player.getItemInHand(hand).has(DataComponents.DEATH_PROTECTION))
                return true;
        return false;
    }

    /**
     * Where the body starts to come apart: toward what struck it, at the feet for a fall, else at the chest.
     */
    private static Vec3 wound(Player player, DamageSource source)
    {
        double height = player.getBbHeight();
        var attacker = source.getDirectEntity() != null ? source.getDirectEntity() : source.getEntity();
        var from = attacker != null ? attacker.getBoundingBox().getCenter() : source.getSourcePosition();
        if (from != null)
        {
            var chest = player.position().add(0, height * 0.6, 0);
            var toward = new Vec3(from.x - chest.x, 0, from.z - chest.z);
            if (toward.lengthSqr() > 1.0E-4)
                toward = toward.normalize().scale(player.getBbWidth() * 0.5);
            double y = Math.clamp(from.y - player.getY(), 0.1, height - 0.1);
            return new Vec3(toward.x, y, toward.z);
        }
        if (source.is(DamageTypeTags.IS_FALL))
            return new Vec3(0, 0.05, 0);
        return chest(player);
    }

    private static Vec3 chest(Player player)
    {
        return new Vec3(0, player.getBbHeight() * 0.6, 0);
    }

    /**
     * Fires the heart as the rite does: its first use, from the chest, whatever the charge.
     */
    public static void fireRite(ServerPlayer player)
    {
        player.setAttached(HEART, new HeartState(true, 0));
        fire(player, chest(player));
    }

    private static void fire(ServerPlayer player, Vec3 wound)
    {
        player.setHealth(1);
        player.setAttached(HEART, new HeartState(true, HeartRules.RECHARGE_TICKS));
        player.setAttached(SEQUENCE, new HeartSequence(player.level().getGameTime(), player.position(), wound));
        player.stopUsingItem();
        player.stopRiding();
        player.clearFire();
        player.setDeltaMovement(Vec3.ZERO);
        player.connection.send(new ClientboundSetEntityMotionPacket(player));
        forgetAsTarget(player);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), VoidHeartSounds.SECOND_HEART_DISSOLVE, SoundSource.PLAYERS, 1, 1);
    }

    private static void forgetAsTarget(ServerPlayer player)
    {
        var box = player.getBoundingBox().inflate(TARGET_CLEAR_RANGE);
        for (var mob : player.level().getEntitiesOfClass(Mob.class, box, mob -> mob.getTarget() == player))
        {
            mob.setTarget(null);
            if (mob.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET))
                mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        }
    }

    public static void tickServer(MinecraftServer server)
    {
        for (var player : server.getPlayerList().getPlayers())
        {
            var sequence = sequence(player);
            if (sequence != null)
                tickSequence(player, sequence);
            else
                rememberSafe(player);

            var state = state(player);
            if (sequence == null && state.owned() && state.recharge() > 0)
            {
                int before = state.recharge();
                player.setAttached(HEART, new HeartState(true, HeartRules.recharge(before)));
                if (HeartRules.comesBack(before))
                    playTo(player, VoidHeartSounds.SECOND_HEART_READY, READY_VOLUME);
            }

            var shown = state(player).shown();
            if (player.getAttachedOrElse(SHOWN, HeartShown.NONE) != shown)
                player.setAttached(SHOWN, shown);
        }
    }

    private static void tickSequence(ServerPlayer player, HeartSequence sequence)
    {
        int elapsed = (int) (player.level().getGameTime() - sequence.start());
        var phase = HeartRules.phase(elapsed);
        if (phase == HeartRules.Phase.DONE)
        {
            player.removeAttached(SEQUENCE);
            return;
        }
        if (phase == HeartRules.Phase.GRACE)
        {
            if (elapsed == HeartRules.BURST_TICK)
                burst(player);
            return;
        }

        if (elapsed == HeartRules.DISSOLVE_TICKS)
            sequence = beginReform(player, sequence);
        hold(player, sequence.anchor());
        if (elapsed % 10 == 0)
            forgetAsTarget(player);
    }

    private static void hold(ServerPlayer player, Vec3 anchor)
    {
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0;
        if (player.position().distanceToSqr(anchor) > HOLD_SLACK * HOLD_SLACK)
            player.teleportTo(anchor.x, anchor.y, anchor.z);
    }

    private static HeartSequence beginReform(ServerPlayer player, HeartSequence sequence)
    {
        var level = player.level();
        var anchor = sequence.anchor();
        var spot = SafeSpot.choose(anchor, hazards(player, anchor), LAST_SAFE.get(player.getUUID()), fallback(level, anchor));
        if (!spot.equals(anchor))
        {
            sequence = sequence.movedTo(spot);
            player.setAttached(SEQUENCE, sequence);
            player.teleportTo(spot.x, spot.y, spot.z);
        }
        player.setHealth(player.getMaxHealth());
        player.clearFire();
        player.setAirSupply(player.getMaxAirSupply());
        level.playSound(null, spot.x, spot.y, spot.z, VoidHeartSounds.SECOND_HEART_REFORM, SoundSource.PLAYERS, 1, 1);
        return sequence;
    }

    private static void burst(ServerPlayer player)
    {
        var level = player.level();
        var center = player.position();
        var box = player.getBoundingBox().inflate(HeartRules.BURST_RADIUS);
        var source = level.damageSources().playerAttack(player);
        for (var entity : level.getEntitiesOfClass(LivingEntity.class, box, entity -> entity != player && entity.isAlive() && !entity.isSpectator()))
        {
            double dx = entity.getX() - center.x, dz = entity.getZ() - center.z;
            double strength = HeartRules.burstStrength(Math.sqrt(dx * dx + dz * dz));
            if (strength <= 0)
                continue;
            if (dx * dx + dz * dz < 1.0E-4)
                dx = 0.01;
            entity.knockback(strength, -dx, -dz, source, 0);
            if (entity instanceof ServerPlayer other)
                other.connection.send(new ClientboundSetEntityMotionPacket(other));
            else
                SinStagger.stagger(entity, HeartRules.BURST_STAGGER);
        }
        level.playSound(null, center.x, center.y, center.z, VoidHeartSounds.SECOND_HEART_BURST, SoundSource.PLAYERS, 1.5F, 1);
    }

    private static void rememberSafe(ServerPlayer player)
    {
        if (player.isSpectator() || player.tickCount % 5 != 0)
            return;
        var at = player.position();
        if (SafeSpot.remember(player.onGround(), player.isInLiquid(), hazards(player, at)))
            LAST_SAFE.put(player.getUUID(), at);
    }

    static SafeSpot.Hazards hazards(Player player, Vec3 at)
    {
        var level = player.level();
        var box = player.getDimensions(player.getPose()).makeBoundingBox(at).deflate(1.0E-3);
        boolean belowWorld = at.y < level.getMinY();
        boolean inLava = BlockPos.betweenClosedStream(box).anyMatch(pos -> level.getFluidState(pos).is(FluidTags.LAVA));
        boolean inWall = !level.noCollision(player, box);
        return new SafeSpot.Hazards(belowWorld, inLava, inWall, !belowWorld && overVoid(level, at));
    }

    private static boolean overVoid(Level level, Vec3 at)
    {
        var pos = BlockPos.containing(at.x, at.y - 0.01, at.z).mutable();
        for (int i = 0; i < SafeSpot.GROUND_SEARCH && pos.getY() >= level.getMinY(); i++, pos.move(0, -1, 0))
        {
            var state = level.getBlockState(pos);
            if (!state.getCollisionShape(level, pos).isEmpty() || !state.getFluidState().isEmpty())
                return false;
        }
        return true;
    }

    private static Vec3 fallback(ServerLevel level, Vec3 at)
    {
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(at.x), (int) Math.floor(at.z));
        if (top > level.getMinY())
            return new Vec3(Math.floor(at.x) + 0.5, top, Math.floor(at.z) + 0.5);
        return Vec3.atBottomCenterOf(level.getRespawnData().pos());
    }

    private static void playTo(ServerPlayer player, SoundEvent sound, float volume)
    {
        player.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), SoundSource.PLAYERS,
                player.getX(), player.getY(), player.getZ(), volume, 1, player.getRandom().nextLong()));
    }

    /**
     * A player leaving mid-reform is rebuilt at once, so they never log back in dissolved.
     */
    private static void onLeave(ServerPlayer player)
    {
        LAST_SAFE.remove(player.getUUID());
        var sequence = sequence(player);
        if (sequence == null)
            return;
        if (player.level().getGameTime() - sequence.start() < HeartRules.DISSOLVE_TICKS)
            sequence = beginReform(player, sequence);
        player.setHealth(player.getMaxHealth());
        player.removeAttached(SEQUENCE);
    }

    /** For commands and tests. */
    public static void grant(ServerPlayer player, boolean ready)
    {
        player.setAttached(HEART, new HeartState(true, ready ? 0 : HeartRules.RECHARGE_TICKS));
    }

    public static void setRecharge(ServerPlayer player, int ticks)
    {
        player.setAttached(HEART, new HeartState(state(player).owned(), Math.max(0, ticks)));
    }

    public static void revoke(ServerPlayer player)
    {
        player.removeAttached(HEART);
    }
}
