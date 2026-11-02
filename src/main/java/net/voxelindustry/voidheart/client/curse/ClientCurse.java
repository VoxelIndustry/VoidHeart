package net.voxelindustry.voidheart.client.curse;

import com.mojang.renderpearl.api.commands.RenderPass;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.util.RandomSource;
import net.voxelindustry.voidheart.client.curse.sky.SkyFrame;

/**
 * What a curse makes the afflicted player perceive, for as long as it runs. Only their client knows about it.
 * Time stands still while the game is paused or frozen, so the state of a curse only changes in {@link #tick}.
 */
public abstract class ClientCurse
{
    protected final long         seed;
    protected final RandomSource random;
    protected final int          duration;

    protected int age;

    protected ClientCurse(long seed, int duration)
    {
        this.seed = seed;
        this.random = RandomSource.create(seed);
        this.duration = duration;
    }

    final void advance(Minecraft minecraft, LocalPlayer player)
    {
        tick(minecraft, player);
        age++;
    }

    final boolean isFinished()
    {
        return age >= duration;
    }

    /**
     * Age in ticks, for tests.
     */
    public final int age()
    {
        return age;
    }

    protected void tick(Minecraft minecraft, LocalPlayer player)
    {
    }

    /**
     * Called when the curse is cut short or the player leaves, to let go of anything outliving a tick.
     */
    protected void onEnd()
    {
    }

    /**
     * Draws into the world, as {@code LevelRenderEvents.COLLECT_SUBMITS} would.
     */
    public void collectSubmits(LevelRenderContext context)
    {
    }

    /**
     * Whether the vanilla stars are hidden, because this curse draws them itself.
     */
    public boolean hidesStars()
    {
        return false;
    }

    /**
     * Writes this frame's uniforms and uploads meshes. No render pass is open here, unlike in {@link #drawSky}.
     */
    public void prepareSky(SkyRenderState state, SkyFrame frame)
    {
    }

    /**
     * Draws into the open sky pass, in front of the sky, sun and moon and behind everything else in the world.
     */
    public void drawSky(RenderPass renderPass, SkyFrame frame)
    {
    }
}
