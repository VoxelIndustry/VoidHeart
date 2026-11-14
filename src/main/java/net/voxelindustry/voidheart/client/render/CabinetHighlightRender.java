package net.voxelindustry.voidheart.client.render;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.voxelindustry.voidheart.common.energy.storage.RubrumCellItem;
import net.voxelindustry.voidheart.common.energy.storage.RubrumContent;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.world.underway.UnderwayProp;

import java.util.ArrayList;
import java.util.List;

/**
 * While a rubrum cell is in hand, outlines the dark breaker cabinets around, through walls, so a visitor carrying power finds where it goes.
 * <p>
 * A pulsing additive outline that writes no depth, a little outside the cabinet's own shape so it never shares a plane with it. Cabinets are found by a scan every
 * half second that skips every chunk section whose palette holds no cabinet, so the frame only draws the dozen ribbons of the few found.
 */
public final class CabinetHighlightRender
{
    private static final int RANGE = 32;
    private static final int RESCAN_TICKS = 10;
    private static final int COLOR = RubrumCellItem.BAR_COLOR;
    private static final int LOW_ALPHA = 90;
    private static final int HIGH_ALPHA = 220;
    private static final float PULSE_RATE = 0.15F;
    private static final float HALF_WIDTH = 0.022F;
    private static final double INFLATE = 0.05;

    private static final List<AABB> found = new ArrayList<>();
    private static ClientLevel scannedLevel;
    private static long scannedAt;

    private CabinetHighlightRender()
    {
    }

    /** Lets go of the world it last scanned, on leaving it. */
    public static void clear()
    {
        scannedLevel = null;
        found.clear();
    }

    public static void collectSubmits(LevelRenderContext context)
    {
        var minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        var level = minecraft.level;
        if (player == null || level == null || !holdsCell(player))
            return;
        if (level != scannedLevel || Math.abs(level.getGameTime() - scannedAt) >= RESCAN_TICKS)
            scan(level, player.blockPosition());
        if (found.isEmpty())
            return;

        var levelState = context.levelState();
        var camera = levelState.cameraRenderState.pos;
        float time = (levelState.gameTime % 24000L) + levelState.worldPartialTicks;
        int color = ARGB.color(Math.round(Mth.lerp(0.5F + 0.5F * Mth.sin(time * PULSE_RATE), LOW_ALPHA, HIGH_ALPHA)), COLOR);
        var boxes = List.copyOf(found);
        context.submitNodeCollector().submitCustomGeometry(context.poseStack(), VoidHeartRenderTypes.GLOW_THROUGH, (pose, buffer) ->
        {
            for (var box : boxes)
                RelayLinkPreviewRender.outline(pose, buffer, box, camera, color, HALF_WIDTH);
        });
    }

    private static boolean holdsCell(Player player)
    {
        return player.getItemInHand(InteractionHand.MAIN_HAND).is(RubrumContent.RUBRUM_CELL) || player.getItemInHand(InteractionHand.OFF_HAND).is(RubrumContent.RUBRUM_CELL);
    }

    private static void scan(ClientLevel level, BlockPos origin)
    {
        scannedLevel = level;
        scannedAt = level.getGameTime();
        found.clear();
        Block cabinet = VoidHeartBlocks.underwayProp(UnderwayProp.BREAKER_CABINET);
        for (int chunkX = (origin.getX() - RANGE) >> 4; chunkX <= (origin.getX() + RANGE) >> 4; chunkX++)
        {
            for (int chunkZ = (origin.getZ() - RANGE) >> 4; chunkZ <= (origin.getZ() + RANGE) >> 4; chunkZ++)
            {
                var chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null)
                    continue;
                var sections = chunk.getSections();
                for (int index = 0; index < sections.length; index++)
                {
                    var section = sections[index];
                    int baseY = level.getSectionYFromSectionIndex(index) << 4;
                    if (section.hasOnlyAir() || Math.abs(baseY + 8 - origin.getY()) > RANGE + 8 || !section.maybeHas(state -> state.is(cabinet)))
                        continue;
                    for (int y = 0; y < 16; y++)
                    {
                        for (int z = 0; z < 16; z++)
                        {
                            for (int x = 0; x < 16; x++)
                            {
                                var state = section.getBlockState(x, y, z);
                                if (!state.is(cabinet))
                                    continue;
                                var pos = new BlockPos((chunkX << 4) + x, baseY + y, (chunkZ << 4) + z);
                                if (pos.distSqr(origin) > RANGE * RANGE)
                                    continue;
                                var shape = state.getShape(level, pos);
                                found.add((shape.isEmpty() ? new AABB(pos) : shape.bounds().move(pos)).inflate(INFLATE));
                            }
                        }
                    }
                }
            }
        }
    }
}
