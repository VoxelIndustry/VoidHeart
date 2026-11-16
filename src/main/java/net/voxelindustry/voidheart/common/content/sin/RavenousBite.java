package net.voxelindustry.voidheart.common.content.sin;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.setup.VoidHeartTags;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A pickaxe or shovel infused with Ravenous Gold keeps eating, one ring per tick, nearest first. Breaking an ore takes the vein of the same ore around it.
 * Breaking anything else takes a square on the face hit, going straight in, and never lower than the wielder's feet or the broken block, so walls and
 * floors come out flat. The bitten blocks cost the wielder's hunger and none of the tool's durability, and their drops gather where the first block broke.
 * Sneaking breaks a single block. A starved wielder's tool stops biting until they eat.
 * <p>
 * The client plans with the same rules to outline what a swing would take.
 */
public final class RavenousBite
{
    private static final Map<UUID, Bite> BITES = new HashMap<>();
    /**
     * The face each player last started breaking, read before the block is gone.
     */
    private static final Map<UUID, Hit>  HITS  = new HashMap<>();

    /**
     * Set while the bite breaks a block, so that break doesn't bite in turn.
     */
    private static boolean biting;

    private RavenousBite()
    {
    }

    static void register()
    {
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) ->
        {
            if (!biting && player instanceof ServerPlayer && bites(player.getMainHandItem()))
                HITS.put(player.getUUID(), new Hit(pos.immutable(), faceHit(player, pos)));
            return true;
        });
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) ->
        {
            if (!biting && level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer)
                start(serverLevel, serverPlayer, pos, state);
        });
        ServerTickEvents.END_SERVER_TICK.register(RavenousBite::tick);
    }

    /**
     * Whether a stack is a tool that bites: a diggable tool carrying Gluttony.
     */
    public static boolean bites(ItemStack stack)
    {
        return stack.is(VoidHeartTags.SIN_DIGGABLE) && SinProficiencies.sinOf(stack) == Sin.GLUTTONY;
    }

    private static void start(ServerLevel level, ServerPlayer player, BlockPos origin, BlockState broken)
    {
        var hit = HITS.remove(player.getUUID());
        var tool = player.getMainHandItem();
        if (!canBite(player, tool, broken))
            return;

        var face = hit != null && hit.pos().equals(origin) ? hit.face() : faceHit(player, origin);
        var rings = plan(level, player, tool, origin, broken, face);
        if (rings.isEmpty())
            return;
        var unfinished = BITES.put(player.getUUID(), new Bite(level, origin.immutable(), edibility(level, tool, broken), tool, rings));
        if (unfinished != null)
            DiggingProficiency.gain(player, unfinished.learned);
    }

    /**
     * Whether breaking this block with this tool would bite at all.
     */
    public static boolean canBite(Player player, ItemStack tool, BlockState broken)
    {
        return bites(tool) && !player.isShiftKeyDown() && !player.isCreative() && !player.isSpectator() && !isStarved(player) && effective(tool, broken);
    }

    /**
     * What a bite would take beside the broken block, in the rings it eats them in.
     *
     * @param face the face of the broken block the player hit
     */
    public static List<List<BlockPos>> plan(Level level, Player player, ItemStack tool, BlockPos origin, BlockState broken, Direction face)
    {
        var stage = DiggingProficiency.stage(player);
        var edible = edibility(level, tool, broken);
        if (isOre(broken))
            return BitePlan.vein(origin, edible, DiggingProficiency.veinSize(stage), SinBalance.DIGGING_REACH);

        // A swing at a wall never digs lower than the wielder's feet or the broken block, whichever is lower
        var lowestY = face.getAxis().isHorizontal() ? Math.min(Mth.floor(player.getY()), origin.getY()) : level.getMinY();
        return BitePlan.square(origin, face, DiggingProficiency.squareRadius(stage), DiggingProficiency.squareDepth(stage), lowestY, edible);
    }

    public static boolean isOre(BlockState state)
    {
        return state.is(ConventionalBlockTags.ORES);
    }

    /**
     * Along a vein, the same ore. In a square, anything the tool is made for.
     */
    private static Predicate<BlockPos> edibility(Level level, ItemStack tool, BlockState broken)
    {
        var vein = isOre(broken);
        return pos ->
        {
            var state = level.getBlockState(pos);
            return !state.isAir() && !state.hasBlockEntity() && state.getDestroySpeed(level, pos) >= 0 && effective(tool, state)
                    && (!vein || sameKind(broken, state));
        };
    }

    /**
     * The face of a block the player is looking at, or, when the look misses it, the side facing the player most.
     */
    public static Direction faceHit(Player player, BlockPos pos)
    {
        if (player.pick(player.blockInteractionRange(), 1, false) instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
                && hit.getBlockPos().equals(pos))
            return hit.getDirection();
        return Direction.getApproximateNearest(player.getLookAngle()).getOpposite();
    }

    private record Hit(BlockPos pos, Direction face)
    {
    }

    private static void tick(MinecraftServer server)
    {
        if (BITES.isEmpty())
            return;

        var iterator = BITES.entrySet().iterator();
        while (iterator.hasNext())
        {
            var entry = iterator.next();
            var player = server.getPlayerList().getPlayer(entry.getKey());
            var bite = entry.getValue();
            if (player == null || !bite.step(player))
            {
                if (player != null)
                    DiggingProficiency.gain(player, bite.learned);
                iterator.remove();
            }
        }
    }

    /**
     * Blocks of one kind: the same block, or two blocks of {@code voidheart:bite_together}, such as stone, deepslate and tuff.
     */
    public static boolean sameKind(BlockState broken, BlockState other)
    {
        return other.is(broken.getBlock()) || broken.is(VoidHeartTags.BITE_TOGETHER) && other.is(VoidHeartTags.BITE_TOGETHER);
    }

    /**
     * Only what the tool mines at its own speed and gets drops from, so a pickaxe never bites dirt or a stone pickaxe diamond ore.
     */
    private static boolean effective(ItemStack tool, BlockState state)
    {
        return tool.getDestroySpeed(state) > 1 && (!state.requiresCorrectToolForDrops() || tool.isCorrectToolForDrops(state));
    }

    private static boolean isStarved(Player player)
    {
        return player.getFoodData().getFoodLevel() <= 0;
    }

    private static final class Bite
    {
        private final ServerLevel           level;
        private final BlockPos              origin;
        private final Predicate<BlockPos>   edible;
        private final ItemStack             tool;
        private final List<List<BlockPos>>  rings;
        private int                         ring;
        private float                       learned;

        private Bite(ServerLevel level, BlockPos origin, Predicate<BlockPos> edible, ItemStack tool, List<List<BlockPos>> rings)
        {
            this.level = level;
            this.origin = origin;
            this.edible = edible;
            this.tool = tool;
            this.rings = rings;
        }

        /**
         * Eats the next ring.
         *
         * @return whether more rings are left to eat
         */
        private boolean step(ServerPlayer player)
        {
            if (ring >= rings.size() || !player.isAlive() || player.level() != level || player.getMainHandItem() != tool || !bites(tool))
                return false;

            var drops = new AABB(origin);
            for (var pos : rings.get(ring))
            {
                if (isStarved(player) || player.getMainHandItem() != tool)
                    return false;

                var state = level.getBlockState(pos);
                if (!edible.test(pos))
                    continue;

                var hardness = state.getDestroySpeed(level, pos);
                if (!eat(player, pos))
                    continue;

                crumble(pos, state);
                player.causeFoodExhaustion(hardness * SinBalance.DIGGING_EXHAUSTION_PER_HARDNESS);
                learned += hardness;
                drops = drops.minmax(new AABB(pos));
            }
            gather(drops);
            ring++;
            return ring < rings.size();
        }

        /**
         * Breaks the block as the player would, protection and all, then gives back the durability the break cost.
         */
        private boolean eat(ServerPlayer player, BlockPos pos)
        {
            var damageable = tool.isDamageableItem();
            var damage = tool.getDamageValue();
            // A tool on its last uses could still break mid-bite, before the damage is given back
            if (damageable && tool.getMaxDamage() - damage <= 2)
                return false;

            boolean broke;
            biting = true;
            try
            {
                broke = player.gameMode.destroyBlock(pos);
            } finally
            {
                biting = false;
            }
            if (damageable && !tool.isEmpty() && tool.getDamageValue() != damage)
                tool.setDamageValue(damage);
            return broke;
        }

        private void crumble(BlockPos pos, BlockState state)
        {
            var center = Vec3.atCenterOf(pos);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), center.x, center.y, center.z, 6, 0.25, 0.25, 0.25, 0.02);
            level.sendParticles(ParticleTypes.SQUID_INK, center.x, center.y, center.z, 1, 0.15, 0.15, 0.15, 0.01);
        }

        /**
         * Items and experience born this tick inside the ring are brought to the block the player broke, so they're never left down a tunnel.
         */
        private void gather(AABB area)
        {
            var target = Vec3.atCenterOf(origin);
            var box = area.inflate(0.5);
            for (var item : level.getEntitiesOfClass(ItemEntity.class, box, entity -> entity.tickCount == 0))
            {
                item.setPos(target.x, target.y, target.z);
                item.setDeltaMovement(Vec3.ZERO);
            }
            for (var orb : level.getEntitiesOfClass(ExperienceOrb.class, box, entity -> entity.tickCount == 0))
                orb.setPos(target.x, target.y, target.z);
        }
    }
}
