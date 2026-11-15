package net.voxelindustry.voidheart.client.render.temple;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Watches whether the player stares at a temple's mosaic, on their own client alone: nobody else sees it wake, and it changes nothing in the world. A stare
 * counts while the crosshair rests on the mosaic from within {@value #RANGE} blocks with no screen open. The mosaic it last woke is kept until the player is
 * {@value #FORGET} blocks away from it, so its eyes follow them for as long as they stay.
 */
public final class MosaicStare
{
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Identifier MAP = Identifier.fromNamespaceAndPath(MODID, "mosaic/temple_mosaic_map.png");
    private static final Identifier FEATURES = Identifier.fromNamespaceAndPath(MODID, "mosaic/temple_mosaic.json");

    static final double RANGE = 16;
    static final double FORGET = 24;
    private static final int REFRESH_TICKS = 20;
    /** The share of the field of view lost at full reach. */
    static final float NARROWING = 0.1F;

    private static final MosaicWake wake = new MosaicWake();
    private static final MosaicMotion motion = new MosaicMotion();
    private static final MosaicSounds sounds = new MosaicSounds();
    private static @Nullable MosaicWall wall;
    private static @Nullable MosaicGaze gaze;
    private static @Nullable MosaicArt art;
    private static boolean artMissing;
    private static @Nullable ClientLevel level;
    private static boolean staring;

    private MosaicStare()
    {
    }

    public static void tick(Minecraft minecraft)
    {
        var player = minecraft.player;
        if (minecraft.level != level || player == null)
        {
            clear();
            level = minecraft.level;
        }
        if (level == null || player == null || minecraft.isPaused())
            return;

        staring = false;
        if (minecraft.gui.screen() == null)
        {
            var eye = player.getEyePosition();
            var hit = level.clip(new ClipContext(eye, eye.add(player.getViewVector(1).scale(RANGE)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
            if (hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult block)
            {
                var state = level.getBlockState(block.getBlockPos());
                if (state.is(VoidHeartBlocks.UNDERWAY_MOSAIC) || state.is(VoidHeartBlocks.UNDERWAY_MOSAIC_BED))
                {
                    if (wall == null || !wall.contains(block.getBlockPos(), block.getDirection()))
                    {
                        var found = MosaicWall.find(level, block.getBlockPos(), block.getDirection());
                        if (found != null && (wall == null || !wall.sameAs(found)))
                            switchTo(found);
                    }
                    staring = wall != null && wall.contains(block.getBlockPos(), block.getDirection());
                }
            }
        }

        if (wall == null)
            return;
        if (wall.middle().distanceTo(player.getEyePosition()) > FORGET)
        {
            forget();
            return;
        }
        if (level.getGameTime() % REFRESH_TICKS == 0 && !wall.refresh(level))
        {
            forget();
            return;
        }
        // A frozen world holds the mosaic still with it
        if (art() == null || !level.tickRateManager().runsNormally())
            return;
        wake.tick(staring);
        motion.tick(wake, art);
        sounds.tick(level, wall, wake);
        var at = wall.toPicture(player.getEyePosition());
        if (gaze != null)
            gaze.tick(wake, at[0], at[1], at[2]);
    }

    /**
     * How much the player's view narrows while the mosaic reaches for them, scaled by their setting for how strongly the view may change.
     *
     * @return what to multiply their field of view by
     */
    public static float narrowing(float effectScale)
    {
        return wall == null ? 1 : 1 - NARROWING * wake.reach(1) * effectScale;
    }

    private static void switchTo(MosaicWall found)
    {
        forget();
        wall = found;
        var loaded = art();
        if (loaded != null)
            gaze = new MosaicGaze(loaded, found.seed());
    }

    private static void forget()
    {
        wall = null;
        gaze = null;
        wake.forget();
        motion.forget();
        sounds.forget();
    }

    /** Reads the picture's map and features again after resources reload, and lets go of the mosaic it watched, whose eyes may no longer match. */
    public static void reloaded()
    {
        art = null;
        artMissing = false;
        forget();
    }

    /** Lets go of the world it watched, on leaving it. */
    public static void clear()
    {
        forget();
        level = null;
        staring = false;
    }

    /** The picture's map and features, read once. */
    public static @Nullable MosaicArt art()
    {
        if (art != null || artMissing)
            return art;
        var resources = Minecraft.getInstance().getResourceManager();
        try (var stream = resources.open(MAP); var image = NativeImage.read(stream); var reader = resources.openAsReader(FEATURES))
        {
            var json = String.join("\n", reader.lines().toList());
            art = MosaicArt.parse((x, y) -> image.getPixel(x, y) & 0xFFFFFF, json);
        } catch (IOException | RuntimeException e)
        {
            LOGGER.error("Could not read the temple mosaic's map, so it will never wake", e);
            artMissing = true;
        }
        return art;
    }

    public static @Nullable MosaicWall wall()
    {
        return wall;
    }

    public static @Nullable MosaicGaze gaze()
    {
        return gaze;
    }

    public static MosaicWake wake()
    {
        return wake;
    }

    public static MosaicMotion motion()
    {
        return motion;
    }

    public static MosaicSounds sounds()
    {
        return sounds;
    }

    /** Whether the player stared at the mosaic last tick, for game tests. */
    public static boolean staring()
    {
        return staring;
    }
}
