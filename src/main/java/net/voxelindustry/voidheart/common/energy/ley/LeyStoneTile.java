package net.voxelindustry.voidheart.common.energy.ley;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.voxelindustry.steamlayer.tile.TileBase;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * The lower half of a standing stone. The server joins it to its level's spurs, and shows on it what the spur carries: where its seam comes from, the
 * line's flow and taint. That is synced so the client can draw the channel and the seam. Nothing here is the source of truth, so a stale save is harmless.
 */
public class LeyStoneTile extends TileBase
{
    /**
     * Stones loaded on the client, for drawing seams across the land between them.
     */
    public static final Set<LeyStoneTile> CLIENT_LOADED = Collections.newSetFromMap(new WeakHashMap<>());

    private static final float FLOW_STEP  = 0.05F;
    private static final float TAINT_STEP = 0.5F;
    private static final float EASE       = 0.12F;

    private boolean connected;
    private boolean root;
    private double  upstreamX;
    private double  upstreamZ;
    private int     upstreamY;
    private float   flow;
    private float   taint;
    private float   pathStart;
    private float   pathEnd;
    private float   spurLength;

    private float glow;
    private float previousGlow;

    public LeyStoneTile(BlockPos pos, BlockState state)
    {
        super(LeyStoneContent.LEY_STONE_TILE, pos, state);
    }

    @Override
    public void setLevel(Level level)
    {
        super.setLevel(level);
        join();
    }

    @Override
    public void clearRemoved()
    {
        super.clearRemoved();
        join();
    }

    private void join()
    {
        if (getLevel() instanceof ServerLevel level)
            LeySpurs.get(level).stoneLoaded(getBlockPos());
        else if (getLevel() != null)
            CLIENT_LOADED.add(this);
    }

    @Override
    public void setRemoved()
    {
        super.setRemoved();
        if (getLevel() != null && getLevel().isClientSide())
            CLIENT_LOADED.remove(this);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state)
    {
        super.preRemoveSideEffects(pos, state);
        if (getLevel() instanceof ServerLevel level)
            LeySpurs.get(level).stoneRemoved(pos);
    }

    void showSpur(SpurPlan.Link link, SpurTap tap, float flow, float taint, float spurLength)
    {
        double x, z;
        int y;
        if (link.isRoot())
        {
            x = tap.x();
            z = tap.z();
            y = link.stone().getY();
        }
        else
        {
            x = link.parent().getX() + 0.5;
            z = link.parent().getZ() + 0.5;
            y = link.parent().getY();
        }

        boolean changed = !connected || root != link.isRoot() || x != upstreamX || z != upstreamZ || y != upstreamY
                || Math.abs(flow - this.flow) > FLOW_STEP || Math.abs(taint - this.taint) > TAINT_STEP
                || pathStart != (float) link.pathStart() || pathEnd != (float) link.pathEnd() || this.spurLength != spurLength;
        if (!changed)
            return;

        connected = true;
        root = link.isRoot();
        upstreamX = x;
        upstreamZ = z;
        upstreamY = y;
        this.flow = flow;
        this.taint = taint;
        pathStart = (float) link.pathStart();
        pathEnd = (float) link.pathEnd();
        this.spurLength = spurLength;
        setChanged();
        sync();
    }

    void showDark()
    {
        if (!connected)
            return;
        connected = false;
        root = false;
        flow = 0;
        taint = 0;
        setChanged();
        sync();
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, LeyStoneTile stone)
    {
        stone.previousGlow = stone.glow;
        float target = stone.connected ? stone.flow : 0;
        stone.glow += (target - stone.glow) * EASE;
    }

    @Override
    protected void saveAdditional(ValueOutput output)
    {
        super.saveAdditional(output);
        output.putBoolean("connected", connected);
        output.putBoolean("root", root);
        output.putDouble("upstreamX", upstreamX);
        output.putDouble("upstreamZ", upstreamZ);
        output.putInt("upstreamY", upstreamY);
        output.putFloat("flow", flow);
        output.putFloat("taint", taint);
        output.putFloat("pathStart", pathStart);
        output.putFloat("pathEnd", pathEnd);
        output.putFloat("spurLength", spurLength);
    }

    @Override
    protected void loadAdditional(ValueInput input)
    {
        super.loadAdditional(input);
        connected = input.getBooleanOr("connected", false);
        root = input.getBooleanOr("root", false);
        upstreamX = input.getDoubleOr("upstreamX", 0);
        upstreamZ = input.getDoubleOr("upstreamZ", 0);
        upstreamY = input.getIntOr("upstreamY", 0);
        flow = sane(input.getFloatOr("flow", 0));
        taint = sane(input.getFloatOr("taint", 0));
        pathStart = sane(input.getFloatOr("pathStart", 0));
        pathEnd = sane(input.getFloatOr("pathEnd", 0));
        spurLength = sane(input.getFloatOr("spurLength", 0));
    }

    private static float sane(float value)
    {
        return Float.isFinite(value) ? Math.max(0, value) : 0;
    }

    public Direction facing()
    {
        return getBlockState().getValue(LeyStoneBlock.FACING);
    }

    public boolean isConnected()
    {
        return connected;
    }

    public boolean isRoot()
    {
        return root;
    }

    public double upstreamX()
    {
        return upstreamX;
    }

    public int upstreamY()
    {
        return upstreamY;
    }

    public double upstreamZ()
    {
        return upstreamZ;
    }

    public float flow()
    {
        return flow;
    }

    public float taint()
    {
        return taint;
    }

    public float pathStart()
    {
        return pathStart;
    }

    public float pathEnd()
    {
        return pathEnd;
    }

    public float spurLength()
    {
        return spurLength;
    }

    /**
     * The flow the channel shows, eased so the moon tides swell slowly.
     */
    public float glow(float partialTicks)
    {
        return previousGlow + (glow - previousGlow) * partialTicks;
    }
}
