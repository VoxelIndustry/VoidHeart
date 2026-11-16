package net.voxelindustry.voidheart.common.energy.gnawer;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.steamlayer.tile.TileBase;
import net.voxelindustry.voidheart.common.content.essence.EssenceRegistry;
import net.voxelindustry.voidheart.common.content.essence.IEssenceReceiver;
import net.voxelindustry.voidheart.common.energy.VoidEnergy;
import net.voxelindustry.voidheart.common.energy.machine.MachinePower;
import net.voxelindustry.voidheart.common.energy.machine.PowerMeter;
import net.voxelindustry.voidheart.common.world.daedalus.VoidDaedalus;
import net.voxelindustry.voidheart.common.world.dungeon.VoidDungeons;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Eats the box its two stakes mark, from the top layer down, paying void energy for each block by its hardness. Kept blocks go into a container beside
 * it, or pile on top of it once that is full. Erased blocks cost half and leave a trickle of Suffering for an essence storage beside it.
 * <p>
 * It breaks as a fake player named for whoever placed it, so claim protection applies, and never takes block entities, unbreakable blocks or its own
 * stakes. It refuses to run in the dungeons and the Daedalus, and pauses while any block it reaches next is unloaded.
 */
public class GnawerTile extends TileBase
{
    /**
     * Ticks between syncs of the block being eaten, for the tether.
     */
    private static final int TARGET_SYNC_TICKS = 4;
    /**
     * Every how many blocks eaten a crunch is heard.
     */
    private static final int SOUND_EVERY       = 4;

    @Nullable
    private GnawerBox box;
    private final List<BlockPos> stakes = new ArrayList<>();
    private long      cursor;
    private boolean   finished;
    private long      credit;
    private double    essence;
    private ItemStack pickaxe = ItemStack.EMPTY;
    @Nullable
    private UUID      owner;
    private String    ownerName = "";

    // Server only
    private final PowerMeter meter = new PowerMeter();
    private int eatenCount;
    private long lastTargetSync;

    // Synced to clients
    @Nullable
    private BlockPos target;
    private MachinePower power = MachinePower.IDLE;

    ////////////
    // CLIENT //
    ////////////

    private float spin;
    private float previousSpin;
    private float spinSpeed;

    public GnawerTile(BlockPos pos, BlockState state)
    {
        super(GnawerContent.GNAWER_TILE, pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, GnawerTile gnawer)
    {
        if (level instanceof ServerLevel serverLevel)
            gnawer.gnaw(serverLevel);
        else
            gnawer.animate();
    }

    public static boolean refusesToRun(Level level)
    {
        return level.dimension().equals(VoidDungeons.WORLD_KEY) || level.dimension().equals(VoidDaedalus.WORLD_KEY);
    }

    private void gnaw(ServerLevel level)
    {
        if (box == null || refusesToRun(level))
        {
            record(level, 0, 0, null);
            return;
        }

        var erase = getBlockState().getValue(GnawerBlock.ERASE);
        var budget = GnawerBalance.MAX_DRAW;
        long wanted = 0;
        long got = 0;
        BlockPos eatenLast = null;

        var eaten = 0;
        var scanned = 0;
        var volume = box.volume();
        while (eaten < GnawerBalance.BLOCKS_PER_TICK && scanned < GnawerBalance.SCAN_PER_TICK && cursor < volume)
        {
            var pos = box.at(cursor);
            if (!level.isLoaded(pos))
                break;

            var state = level.getBlockState(pos);
            if (!edible(level, pos, state))
            {
                cursor++;
                scanned++;
                continue;
            }

            var cost = GnawerBalance.cost(state.getDestroySpeed(level, pos), state.getBlock() instanceof LiquidBlock, erase);
            if (credit < cost)
            {
                var want = Math.min(budget, cost - credit);
                var drawn = want <= 0 ? 0 : VoidEnergy.draw(level, worldPosition, want);
                wanted += want;
                got += drawn;
                budget -= drawn;
                credit += drawn;
                if (credit < cost)
                    break;
            }

            cursor++;
            if (!eat(level, pos, state, erase))
            {
                scanned++;
                continue;
            }
            credit -= cost;
            eaten++;
            eatenLast = pos;
        }

        if (cursor >= volume)
            finish(level, true);
        else if (eaten > 0 || scanned > 0)
            setChanged();
        record(level, wanted, got, eatenLast);
    }

    private boolean edible(ServerLevel level, BlockPos pos, BlockState state)
    {
        if (state.isAir() || state.hasBlockEntity() || state.is(GnawerContent.GNAWING_STAKE) || pos.equals(worldPosition))
            return false;
        if (state.getBlock() instanceof LiquidBlock)
            return true;
        return state.getDestroySpeed(level, pos) >= 0;
    }

    /**
     * @return whether the block was eaten, false when protection refused it
     */
    private boolean eat(ServerLevel level, BlockPos pos, BlockState state, boolean erase)
    {
        var breaker = breaker(level);
        if (!PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level, breaker, pos, state, null))
            return false;

        if (!erase && !(state.getBlock() instanceof LiquidBlock))
        {
            var tool = tool();
            var harvested = !state.requiresCorrectToolForDrops() || tool.isCorrectToolForDrops(state);
            if (harvested)
            {
                for (var drop : Block.getDrops(state, level, pos, null, breaker, tool))
                    store(level, drop);
                state.spawnAfterBreak(level, pos, tool, false);
            }
        }
        else if (erase)
            feed(level, GnawerBalance.ERASE_ESSENCE);

        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);

        // Crumbs in the block's own colours, and a little dark ink with every crunch heard
        var center = Vec3.atCenterOf(pos);
        if (!(state.getBlock() instanceof LiquidBlock))
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), center.x, center.y, center.z, 6, 0.25, 0.25, 0.25, 0.02);
        if (++eatenCount % SOUND_EVERY == 0)
        {
            level.sendParticles(ParticleTypes.SQUID_INK, center.x, center.y, center.z, 1, 0.15, 0.15, 0.15, 0.01);
            level.playSound(null, pos, state.getSoundType().getBreakSound(), SoundSource.BLOCKS, 0.5F, 0.7F + level.getRandom().nextFloat() * 0.2F);
        }
        return true;
    }

    private FakePlayer breaker(ServerLevel level)
    {
        if (owner == null)
            return FakePlayer.get(level);
        return FakePlayer.get(level, new GameProfile(owner, ownerName.isEmpty() ? "Gnawer" : ownerName));
    }

    /**
     * The lent pickaxe, whose tier and enchantments decide the drops without wearing it, or a plain diamond one. What its tier can't harvest is eaten for nothing.
     */
    private ItemStack tool()
    {
        return pickaxe.isEmpty() ? new ItemStack(Items.DIAMOND_PICKAXE) : pickaxe;
    }

    /**
     * Into the first container beside the Gnawer that takes it, the rest piled on top of it.
     */
    private void store(ServerLevel level, ItemStack stack)
    {
        if (stack.isEmpty())
            return;

        var left = stack.getCount();
        for (var direction : Direction.values())
        {
            if (left <= 0)
                break;
            var storage = ItemStorage.SIDED.find(level, worldPosition.relative(direction), direction.getOpposite());
            if (storage == null || !storage.supportsInsertion())
                continue;
            try (var transaction = Transaction.openOuter())
            {
                left -= (int) storage.insert(ItemVariant.of(stack), left, transaction);
                transaction.commit();
            }
        }
        if (left > 0)
            Block.popResource(level, worldPosition.above(), stack.copyWithCount(left));
    }

    /**
     * Suffering for an essence storage beside the Gnawer, kept in fractions until a whole point is ready. Lost when none has room.
     */
    private void feed(ServerLevel level, double amount)
    {
        essence += amount;
        var whole = (int) essence;
        if (whole <= 0)
            return;
        essence -= whole;
        for (var direction : Direction.values())
        {
            if (level.getBlockEntity(worldPosition.relative(direction)) instanceof IEssenceReceiver receiver
                    && receiver.receive(EssenceRegistry.SUFFERING, whole) > 0)
                return;
        }
    }

    private void record(ServerLevel level, long wanted, long got, @Nullable BlockPos eaten)
    {
        var changed = meter.record(wanted, got);
        var newPower = meter.state();
        var newTarget = eaten != null ? eaten : (box == null || newPower == MachinePower.UNPOWERED ? null : target);
        var targetDue = level.getGameTime() - lastTargetSync >= TARGET_SYNC_TICKS;

        if (changed || newPower != power || (newTarget == null) != (target == null) || (targetDue && newTarget != null && !newTarget.equals(target)))
        {
            power = newPower;
            target = newTarget == null ? null : newTarget.immutable();
            lastTargetSync = level.getGameTime();
            setChanged();
            sync();
        }
    }

    private void finish(ServerLevel level, boolean done)
    {
        for (var stake : stakes)
        {
            if (level.isLoaded(stake) && level.getBlockState(stake).is(GnawerContent.GNAWING_STAKE))
            {
                level.setBlock(stake, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                store(level, new ItemStack(GnawerContent.GNAWING_STAKE));
            }
        }
        stakes.clear();
        box = null;
        cursor = 0;
        finished = done;
        target = null;
        if (done)
            level.playSound(null, worldPosition, SoundEvents.GENERIC_EAT.value(), SoundSource.BLOCKS, 0.8F, 0.6F);
        setChanged();
        sync();
    }

    /**
     * Gives the Gnawer a box, unless it's still eating one. The box is eaten as the player who marked it, so protection is asked about them.
     */
    public boolean link(BlockPos first, BlockPos second, Player marker)
    {
        if (box != null)
            return false;
        owner = marker.getUUID();
        ownerName = marker.getGameProfile().name();
        box = GnawerBox.of(first, second);
        stakes.clear();
        stakes.add(first.immutable());
        stakes.add(second.immutable());
        cursor = 0;
        finished = false;
        setChanged();
        sync();
        return true;
    }

    /**
     * Drops the box unfinished, and gives its stakes back.
     */
    public void clear(ServerLevel level)
    {
        if (box != null)
            finish(level, false);
    }

    public void setOwner(UUID owner, String name)
    {
        this.owner = owner;
        this.ownerName = name;
        setChanged();
    }

    /**
     * Lends a pickaxe, and gives back the one it held.
     */
    public ItemStack swapPickaxe(ItemStack offered)
    {
        var held = pickaxe;
        pickaxe = offered;
        setChanged();
        return held;
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state)
    {
        super.preRemoveSideEffects(pos, state);
        if (level != null && !pickaxe.isEmpty())
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, pickaxe);
    }

    private void animate()
    {
        var eating = target != null && power != MachinePower.UNPOWERED;
        spinSpeed += ((eating ? 1 : 0) - spinSpeed) * 0.1F;
        previousSpin = spin;
        spin += spinSpeed * 0.15F;
    }

    public float spin(float partialTicks)
    {
        return previousSpin + (spin - previousSpin) * partialTicks;
    }

    @Nullable
    public GnawerBox box()
    {
        return box;
    }

    @Nullable
    public BlockPos target()
    {
        return target;
    }

    public MachinePower power()
    {
        return power;
    }

    public boolean isFinished()
    {
        return finished;
    }

    public ItemStack pickaxe()
    {
        return pickaxe;
    }

    /**
     * How much of the box is behind it, from 0 to 1.
     */
    public float progress()
    {
        return box == null ? (finished ? 1 : 0) : (float) cursor / box.volume();
    }

    @Override
    protected void loadAdditional(ValueInput nbt)
    {
        super.loadAdditional(nbt);

        box = nbt.read("box", GnawerBox.CODEC).orElse(null);
        stakes.clear();
        nbt.read("stakes", BlockPos.CODEC.listOf()).ifPresent(stakes::addAll);
        cursor = Math.max(0, nbt.getLongOr("cursor", 0));
        if (box != null && cursor > box.volume())
            cursor = box.volume();
        finished = nbt.getBooleanOr("finished", false);
        credit = Math.max(0, nbt.getLongOr("credit", 0));
        essence = Math.clamp(nbt.getDoubleOr("essence", 0), 0, 1);
        pickaxe = nbt.read("pickaxe", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        owner = nbt.read("owner", UUIDUtil.CODEC).orElse(null);
        ownerName = nbt.getStringOr("ownerName", "");

        target = nbt.read("target", BlockPos.CODEC).orElse(null);
        power = MachinePower.byOrdinal(nbt.getIntOr("power", 0));
        meter.restore(power);
    }

    @Override
    protected void saveAdditional(ValueOutput nbt)
    {
        super.saveAdditional(nbt);

        if (box != null)
            nbt.store("box", GnawerBox.CODEC, box);
        nbt.store("stakes", BlockPos.CODEC.listOf(), List.copyOf(stakes));
        nbt.putLong("cursor", cursor);
        nbt.putBoolean("finished", finished);
        nbt.putLong("credit", credit);
        nbt.putDouble("essence", essence);
        if (!pickaxe.isEmpty())
            nbt.store("pickaxe", ItemStack.OPTIONAL_CODEC, pickaxe);
        if (owner != null)
            nbt.store("owner", UUIDUtil.CODEC, owner);
        nbt.putString("ownerName", ownerName);

        if (target != null)
            nbt.store("target", BlockPos.CODEC, target);
        nbt.putInt("power", power.ordinal());
    }
}
