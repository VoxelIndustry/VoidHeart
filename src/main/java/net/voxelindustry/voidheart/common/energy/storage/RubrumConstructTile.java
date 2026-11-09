package net.voxelindustry.voidheart.common.energy.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.steamlayer.tile.TileBase;
import net.voxelindustry.voidheart.common.energy.IVoidHolder;
import net.voxelindustry.voidheart.common.energy.creative.CreativeSources;
import net.voxelindustry.voidheart.common.energy.storage.ConstructLayout.FramePart;
import net.voxelindustry.voidheart.common.energy.storage.ConstructLayout.Size;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The heart of a rubrum construct. It forms when a whole frame stands around it, holds the bundles set in its sockets,
 * and stores void energy for the network, which finds it as an {@link IVoidHolder}. A creative void well covering it
 * fills it, standing in for a source.
 */
public class RubrumConstructTile extends TileBase implements IVoidHolder
{
    public static final  int  PREVIEW_TICKS = 600;
    private static final int  CHECK_PERIOD  = 20;
    private static final int  SYNC_PERIOD   = 10;
    /** Share of the flow kept each tick when smoothing it, so a change shows over about a second. */
    private static final float FLOW_SMOOTHING = 0.9F;

    /** Why a frame that stands complete still does not form. */
    public enum Problem
    {
        NONE,
        TOO_MANY_BUNDLES
    }

    private final ConstructCharge charge   = new ConstructCharge(() -> level == null ? 0 : level.getGameTime());
    private final SocketSet       sockets  = new SocketSet();
    private final RubrumBudding   budding  = new RubrumBudding();

    private @Nullable Size      layout;
    private           boolean   formed;
    private           Problem   problem = Problem.NONE;
    private @Nullable BlockPos  budPos;
    private           Direction budFace = Direction.NORTH;
    private @Nullable Size      previewSize;
    private           long      previewUntil;

    /** Net flow over the rate, from -1 discharging fully to 1 charging fully. Synced for the looks. */
    private float   flow;
    private float   syncedFlow;
    private long    syncedStored;
    private boolean checkRequested = true;

    // Client only: what the renderer eases toward
    private float displayFill;
    private float previousDisplayFill;
    private final long[] arcStarts = new long[ConstructLayout.MAX_SOCKETS];
    private final long[] arcNext   = new long[ConstructLayout.MAX_SOCKETS];
    private final int[]  arcSeeds  = new int[ConstructLayout.MAX_SOCKETS];

    public RubrumConstructTile(BlockPos pos, BlockState state)
    {
        super(RubrumContent.RUBRUM_CONSTRUCT, pos, state);
        java.util.Arrays.fill(arcStarts, Long.MIN_VALUE / 2);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, RubrumConstructTile tile)
    {
        if (level.isClientSide())
            tile.clientTick();
        else
            tile.serverTick((ServerLevel) level);
    }

    private void serverTick(ServerLevel level)
    {
        var time = level.getGameTime();
        if (checkRequested || (time + worldPosition.asLong()) % CHECK_PERIOD == 0)
        {
            checkRequested = false;
            checkStructure();
        }

        if (formed)
        {
            if (CreativeSources.wells(level).covers(worldPosition))
                charge.insert(charge.maxRate(), false);

            if (time % CHECK_PERIOD == 0)
                tickBudding(level, CHECK_PERIOD);
        }

        var rate = charge.maxRate();
        var target = rate > 0 ? Mth.clamp(charge.lastNetFlow() / (float) rate, -1F, 1F) : 0F;
        flow = flow * FLOW_SMOOTHING + target * (1 - FLOW_SMOOTHING);
        if (Math.abs(flow) < 0.01F && target == 0)
            flow = 0;

        // Marked for saving with each sync rather than every tick, which would also wake comparators every tick
        if (time % SYNC_PERIOD == 0 && (charge.stored() != syncedStored || Math.abs(flow - syncedFlow) > 0.05F
                || Math.signum(flow) != Math.signum(syncedFlow)))
        {
            syncedStored = charge.stored();
            syncedFlow = flow;
            setChanged();
            sync();
        }
    }

    //
    // Structure
    //

    public void requestCheck()
    {
        checkRequested = true;
    }

    private boolean isComplete(Size size)
    {
        for (var offset : ConstructLayout.frame(size))
        {
            if (!level.getBlockState(worldPosition.offset(offset)).is(RubrumContent.RUBRUM_CONSTRUCT_FRAME))
                return false;
        }
        return true;
    }

    private void checkStructure()
    {
        Size found = null;
        problem = Problem.NONE;
        for (var size : new Size[]{Size.LARGE, Size.SMALL})
        {
            if (isComplete(size))
            {
                found = size;
                break;
            }
        }
        if (found != null && !sockets.fits(found))
        {
            problem = Problem.TOO_MANY_BUNDLES;
            found = null;
        }

        var current = formed ? layout : null;
        if (found == current)
        {
            if (formed)
                setFrameParts(layout, false);
            return;
        }

        if (formed)
            unform();
        if (found != null)
            form(found);
    }

    private void form(Size size)
    {
        if (layout != size)
            sockets.arrangeFor(size);
        layout = size;
        formed = true;
        previewSize = null;
        updateSockets();
        charge.setActive(true);
        setFrameParts(size, false);
        level.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1F, 0.8F);
        setChanged();
        sync();
    }

    private void unform()
    {
        formed = false;
        charge.setActive(false);
        flow = 0;
        if (layout != null)
            setFrameParts(layout, true);
        setChanged();
        sync();
    }

    /**
     * Shows each frame block's part while formed, or sets them loose again. Only blocks whose state changes are touched.
     */
    private void setFrameParts(Size size, boolean loose)
    {
        for (var offset : ConstructLayout.frame(size))
        {
            var pos = worldPosition.offset(offset);
            var state = level.getBlockState(pos);
            if (!state.is(RubrumContent.RUBRUM_CONSTRUCT_FRAME))
                continue;
            var part = loose ? FramePart.LOOSE : ConstructLayout.partAt(size, offset);
            if (state.getValue(ConstructFrameBlock.PART) != part)
                level.setBlock(pos, state.setValue(ConstructFrameBlock.PART, part), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state)
    {
        if (formed && layout != null && level != null && !level.isClientSide())
            setFrameParts(layout, true);
        super.preRemoveSideEffects(pos, state);
    }

    private void updateSockets()
    {
        charge.setSockets(sockets.capacity(), sockets.rate());
    }

    public BlockPos socketPos(int socket)
    {
        return worldPosition.offset(ConstructLayout.sockets(layout).get(socket));
    }

    //
    // Sockets
    //

    /**
     * Sets a bundle in the free socket nearest to where the construct was clicked. Returns false when none is free.
     */
    public boolean insertBundle(SocketBundle bundle, Vec3 hit)
    {
        if (!formed || layout == null)
            return false;
        var socket = SocketSet.nearest(layout, hit.x - worldPosition.getX() - 0.5, hit.y - worldPosition.getY() - 0.5, hit.z - worldPosition.getZ() - 0.5,
                index -> sockets.get(index) == null && level.getBlockState(socketPos(index)).isAir());
        if (socket < 0)
            return false;

        sockets.set(socket, bundle);
        updateSockets();
        var cell = socketPos(socket);
        level.playSound(null, cell, SoundEvents.AMETHYST_CLUSTER_PLACE, SoundSource.BLOCKS, 1F, 1F);
        setChanged();
        sync();
        return true;
    }

    public sealed interface Removal
    {
        record Taken(SocketBundle bundle) implements Removal
        {
        }

        record Held() implements Removal
        {
        }

        record Empty() implements Removal
        {
        }
    }

    /**
     * Takes out the bundle nearest to where the construct was clicked. Rubrum stays while the rest could not hold the charge.
     */
    public Removal removeBundle(Vec3 hit)
    {
        if (layout == null)
            return new Removal.Empty();
        var socket = SocketSet.nearest(layout, hit.x - worldPosition.getX() - 0.5, hit.y - worldPosition.getY() - 0.5, hit.z - worldPosition.getZ() - 0.5,
                index -> sockets.get(index) != null);
        if (socket < 0)
            return new Removal.Empty();

        var bundle = sockets.get(socket);
        if (bundle.isRubrum() && !charge.canLoseCapacity(bundle.capacity()))
            return new Removal.Held();

        sockets.set(socket, null);
        updateSockets();
        level.playSound(null, socketPos(socket), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.BLOCKS, 1F, 1F);
        setChanged();
        sync();
        return new Removal.Taken(bundle);
    }

    //
    // Budding
    //

    private void tickBudding(ServerLevel level, int elapsed)
    {
        var before = budding.stage();
        budding.tick(charge.fill(), elapsed);
        var stage = budding.stage();

        if (stage != RubrumBudding.Stage.NONE && (budPos == null || !isBudSpot(budPos, budFace)))
            pickBudSpot(level);

        if (budPos != null && budding.canGrow(charge.stored()))
        {
            var state = VoidHeartBlocks.SMALL_RUBRUM_BUNDLE.defaultBlockState().setValue(AmethystClusterBlock.FACING, budFace);
            level.setBlock(budPos, state, Block.UPDATE_ALL);
            level.playSound(null, budPos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.2F, 1F);
            charge.spend(budding.cost());
            budding.grown();
            budPos = null;
            setChanged();
            sync();
            return;
        }

        if (stage != before)
        {
            setChanged();
            sync();
        }
    }

    private boolean isBudSpot(BlockPos spot, Direction face)
    {
        if (layout == null || ConstructLayout.isInside(layout, spot.subtract(worldPosition)))
            return false;
        return level.isEmptyBlock(spot) && level.getBlockState(spot.relative(face.getOpposite())).is(RubrumContent.RUBRUM_CONSTRUCT_FRAME)
                && VoidHeartBlocks.SMALL_RUBRUM_BUNDLE.defaultBlockState().setValue(AmethystClusterBlock.FACING, face).canSurvive(level, spot);
    }

    private record BudSpot(BlockPos pos, Direction face)
    {
    }

    /**
     * A free outer face of a frame block, sides before top. The bottom is never used.
     */
    private void pickBudSpot(ServerLevel level)
    {
        budPos = null;
        var sides = new ArrayList<BudSpot>();
        var tops = new ArrayList<BudSpot>();
        for (var offset : ConstructLayout.frame(layout))
        {
            var frame = worldPosition.offset(offset);
            for (var face : Direction.values())
            {
                if (face == Direction.DOWN)
                    continue;
                var spot = frame.relative(face);
                if (!isBudSpot(spot, face))
                    continue;
                (face == Direction.UP ? tops : sides).add(new BudSpot(spot, face));
            }
        }
        var pool = sides.isEmpty() ? tops : sides;
        if (pool.isEmpty())
            return;
        var choice = pool.get(level.getRandom().nextInt(pool.size()));
        budPos = choice.pos();
        budFace = choice.face();
        setChanged();
        sync();
    }

    //
    // Client
    //

    private void clientTick()
    {
        previousDisplayFill = displayFill;
        var fill = charge.fill();
        displayFill += (fill - displayFill) * 0.15F;
        if (Math.abs(fill - displayFill) < 0.001F)
            displayFill = fill;

        if (!formed || layout == null)
            return;

        var time = level.getGameTime();
        var random = level.getRandom();
        var strength = Math.abs(flow);
        for (var socket = 0; socket < ConstructLayout.socketCount(layout); socket++)
        {
            var bundle = sockets.get(socket);
            if (bundle == null || bundle.isRubrum() || strength < 0.02F)
                continue;
            if (time >= arcNext[socket])
            {
                arcStarts[socket] = time;
                arcSeeds[socket] = random.nextInt();
                arcNext[socket] = time + Math.round(Mth.lerp(strength, 30, 10)) + random.nextInt(6);
            }
        }

        if (budPos != null && budding.stage() != RubrumBudding.Stage.NONE && budding.stage() != RubrumBudding.Stage.READY
                && (time + worldPosition.asLong()) % 10 == 0)
        {
            var from = Vec3.atCenterOf(worldPosition);
            var to = Vec3.atCenterOf(budPos).subtract(new Vec3(budFace.getUnitVec3i()).scale(0.4));
            level.addParticle(RubrumContent.RUBRUM_MOTE, from.x, from.y, from.z, to.x, to.y, to.z);
        }
    }

    //
    // Holder
    //

    @Override
    public long stored()
    {
        return charge.stored();
    }

    @Override
    public long capacity()
    {
        return charge.capacity();
    }

    @Override
    public long maxRate()
    {
        return charge.maxRate();
    }

    @Override
    public long insert(long amount, boolean simulate)
    {
        return charge.insert(amount, simulate);
    }

    @Override
    public long extract(long amount, boolean simulate)
    {
        return charge.extract(amount, simulate);
    }

    //
    // Accessors
    //

    public ConstructCharge charge()
    {
        return charge;
    }

    public SocketSet sockets()
    {
        return sockets;
    }

    public RubrumBudding budding()
    {
        return budding;
    }

    public boolean isFormed()
    {
        return formed;
    }

    public @Nullable Size layout()
    {
        return layout;
    }

    public Problem problem()
    {
        return problem;
    }

    public float flow()
    {
        return flow;
    }

    public @Nullable BlockPos budPos()
    {
        return budPos;
    }

    public Direction budFace()
    {
        return budFace;
    }

    public float displayFill(float partialTicks)
    {
        return Mth.lerp(partialTicks, previousDisplayFill, displayFill);
    }

    public long arcStart(int socket)
    {
        return arcStarts[socket];
    }

    public int arcSeed(int socket)
    {
        return arcSeeds[socket];
    }

    public @Nullable Size previewSize()
    {
        return previewSize != null && level != null && level.getGameTime() < previewUntil ? previewSize : null;
    }

    /**
     * Each use on a loose core shows the next frame size, then none.
     */
    public void stepPreview(net.minecraft.world.entity.player.Player player)
    {
        var current = previewSize();
        previewSize = current == null ? Size.SMALL : current == Size.SMALL ? Size.LARGE : null;
        previewUntil = level.getGameTime() + PREVIEW_TICKS;
        player.sendOverlayMessage(previewSize == null
                ? Component.translatable("voidheart.rubrum_construct.preview.off")
                : Component.translatable("voidheart.rubrum_construct.preview", previewSize.edge()));
        setChanged();
        sync();
    }

    /**
     * Restores what a broken core carried. The capacity comes from the bundles, so they go in first.
     */
    public void restore(ConstructContents contents)
    {
        sockets.load(contents.sockets());
        updateSockets();
        charge.setStored(contents.stored());
        syncedStored = charge.stored();
        setChanged();
        sync();
    }

    public @Nullable ConstructContents contents()
    {
        if (charge.stored() <= 0 && sockets.isEmpty())
            return null;
        return new ConstructContents(charge.stored(), sockets.entries());
    }

    /**
     * Sets the charge directly, for review commands and tests.
     */
    public void setStored(long stored)
    {
        charge.setStored(stored);
        setChanged();
        syncedStored = charge.stored();
        sync();
    }

    public void setBudProgress(long progress)
    {
        budding.setProgress(progress);
        setChanged();
        sync();
    }

    public List<Component> status()
    {
        var lines = new ArrayList<Component>();
        lines.add(Component.translatable("voidheart.rubrum_construct.status", String.format("%,d", charge.stored()), String.format("%,d", charge.capacity()),
                charge.maxRate(), sockets.count(true), sockets.count(false)));
        return lines;
    }

    //
    // Saving
    //

    @Override
    protected void loadAdditional(ValueInput tag)
    {
        super.loadAdditional(tag);
        sockets.load(tag.read("sockets", SocketSet.Entry.CODEC.listOf()).orElse(List.of()));
        layout = tag.read("layout", Size.CODEC).orElse(null);
        formed = layout != null && tag.getBooleanOr("formed", false);
        updateSockets();
        charge.setStored(tag.getLongOr("stored", 0L));
        charge.setActive(formed);
        budding.setProgress(tag.getLongOr("budProgress", 0L));
        budPos = tag.read("budPos", BlockPos.CODEC).orElse(null);
        budFace = Direction.from3DDataValue(tag.getByteOr("budFace", (byte) Direction.NORTH.get3DDataValue()));
        previewSize = tag.read("previewSize", Size.CODEC).orElse(null);
        previewUntil = tag.getLongOr("previewUntil", 0L);
        flow = tag.getFloatOr("flow", 0F);
        syncedStored = charge.stored();
    }

    @Override
    protected void saveAdditional(ValueOutput tag)
    {
        tag.store("sockets", SocketSet.Entry.CODEC.listOf(), sockets.entries());
        if (layout != null)
            tag.store("layout", Size.CODEC, layout);
        tag.putBoolean("formed", formed);
        tag.putLong("stored", charge.stored());
        tag.putLong("budProgress", budding.progress());
        if (budPos != null)
            tag.store("budPos", BlockPos.CODEC, budPos);
        tag.putByte("budFace", (byte) budFace.get3DDataValue());
        if (previewSize != null)
            tag.store("previewSize", Size.CODEC, previewSize);
        tag.putLong("previewUntil", previewUntil);
        tag.putFloat("flow", flow);
        super.saveAdditional(tag);
    }
}
