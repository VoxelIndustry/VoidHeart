package net.voxelindustry.voidheart.common.content.shroud;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.voxelindustry.steamlayer.tile.TileBase;
import net.voxelindustry.voidheart.common.content.mausoleum.Mausoleums;
import net.voxelindustry.voidheart.common.setup.VoidHeartSounds;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;
import net.voxelindustry.voidheart.common.world.daedalus.ShroudBlocks;
import net.voxelindustry.voidheart.common.world.daedalus.ShroudGate;

/**
 * Holds the ink offered to a shroud gate, and times its seal: lifting while an ink lies here, settling once it is taken. A client times a change from
 * when it sees it, since its clock can trail the server's by a few ticks.
 */
public class ShroudAltarTile extends TileBase
{
    private static final float OFFER_VOLUME = 0.8F;
    private static final float OFFER_PITCH = 0.6F;
    private static final float LIFT_VOLUME = 1.0F;
    private static final float LIFT_PITCH = 0.5F;
    private static final float SETTLE_VOLUME = 0.8F;
    private static final float SETTLE_PITCH = 0.45F;
    private static final int PASS_CHECK_TICKS = 2;

    private ItemStack ink = ItemStack.EMPTY;
    /** The game time the gate last opened or closed. */
    private long changedAt;
    /** How far the seal had lifted at that time. */
    private float progressAtChange;
    /** Whether a state was loaded already, so that a change seen later is timed from when it was seen. */
    private boolean loaded;

    public ShroudAltarTile(BlockPos pos, BlockState state)
    {
        super(VoidHeartTiles.SHROUD_ALTAR, pos, state);
    }

    public boolean holdsInk()
    {
        return !ink.isEmpty();
    }

    public ItemStack ink()
    {
        return ink;
    }

    /** Whether it holds its gate open for good, with no ink, as the way back out of a mausoleum does. */
    public boolean isKept()
    {
        return getBlockState().getOptionalValue(ShroudAltarBlock.KEPT).orElse(false);
    }

    /** The gate stays open as long as an ink lies on its altar. */
    public boolean isOpen()
    {
        return holdsInk() || isKept();
    }

    public ShroudGate gate()
    {
        var facing = ShroudBlocks.compass(getBlockState().getValue(ShroudAltarBlock.FACING));
        return ShroudGate.fromAltar(worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), facing);
    }

    /** @return how far the seal has lifted at a game time, 0 sealed to 1 open */
    public float progress(double gameTime)
    {
        if (isKept())
            return 1;
        // Game times outgrow a float's precision in old worlds, so the difference is taken first
        return ShroudVeil.progress(isOpen(), progressAtChange, (float) (gameTime - changedAt));
    }

    public void offer(ItemStack stack)
    {
        if (holdsInk() || level == null)
            return;
        change();
        ink = stack;
        level.playSound(null, worldPosition, VoidHeartSounds.WRAITH_WHISPER, SoundSource.BLOCKS, OFFER_VOLUME, OFFER_PITCH);
        var gate = gate();
        level.playSound(null, new BlockPos(gate.x(), gate.y() + ShroudGate.SEAL_BOTTOM + ShroudGate.SEAL_HEIGHT / 2, gate.z()), VoidHeartSounds.WRAITH_EMERGE, SoundSource.BLOCKS,
                LIFT_VOLUME, LIFT_PITCH);
        setChanged();
        sync();
    }

    public ItemStack withdraw()
    {
        if (!holdsInk() || level == null)
            return ItemStack.EMPTY;
        change();
        var taken = ink;
        ink = ItemStack.EMPTY;
        var gate = gate();
        level.playSound(null, new BlockPos(gate.x(), gate.y() + ShroudGate.SEAL_BOTTOM + ShroudGate.SEAL_HEIGHT / 2, gate.z()), VoidHeartSounds.WRAITH_WHISPER, SoundSource.BLOCKS,
                SETTLE_VOLUME, SETTLE_PITCH);
        setChanged();
        sync();
        return taken;
    }

    private void change()
    {
        long now = level.getGameTime();
        progressAtChange = progress(now);
        changedAt = now;
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state)
    {
        if (level != null && holdsInk())
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, ink);
        super.preRemoveSideEffects(pos, state);
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, ShroudAltarTile altar)
    {
        ShroudVeilEmitter.tick(level, altar);
    }

    /** Takes whoever presses into the void of a gate standing wide open through it. */
    public static void serverTick(Level level, BlockPos pos, BlockState state, ShroudAltarTile altar)
    {
        if (level.getGameTime() % PASS_CHECK_TICKS != 0 || !altar.isOpen() || altar.progress(level.getGameTime()) < 1)
            return;
        var gate = altar.gate();
        for (var player : level.getEntitiesOfClass(ServerPlayer.class, Mausoleums.mouth(gate), player -> !player.isSpectator()))
            Mausoleums.pass(player, gate, altar.isKept());
    }

    @Override
    protected void loadAdditional(ValueInput input)
    {
        super.loadAdditional(input);
        boolean wasOpen = isOpen();
        boolean timedHere = loaded && level != null && level.isClientSide();
        float shown = timedHere ? progress(level.getGameTime()) : 0;

        ink = input.read("ink", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        changedAt = input.getLongOr("changedAt", 0);
        progressAtChange = Math.clamp(input.getFloatOr("progressAtChange", isOpen() ? 1 : 0), 0, 1);
        if (timedHere && wasOpen != isOpen())
        {
            progressAtChange = shown;
            changedAt = level.getGameTime();
        }
        loaded = true;
    }

    @Override
    protected void saveAdditional(ValueOutput output)
    {
        output.store("ink", ItemStack.OPTIONAL_CODEC, ink);
        output.putLong("changedAt", changedAt);
        output.putFloat("progressAtChange", progressAtChange);
        super.saveAdditional(output);
    }
}
