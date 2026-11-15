package net.voxelindustry.voidheart.client.render.temple;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.voxelindustry.voidheart.client.render.WraithInk;
import net.voxelindustry.voidheart.common.content.sanctum.VeilGate;
import net.voxelindustry.voidheart.common.content.wraithveil.WraithVeilItem;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.world.underway.MosaicDoor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * While a Wraith Veil is in hand, the door in any temple mosaic nearby shows faintly as the veil's own aiming mark would, breathing, so its
 * bearer sees where the veil could open a way. Nothing shows once the door stands open.
 */
public final class MosaicDoorHint
{
    private static final int    SCAN_EVERY   = 10;
    private static final int    REACH        = 14;
    private static final int    REACH_UP     = 7;
    private static final double SHOWN_WITHIN = 24;
    private static final double GAP          = 0.015;
    private static final float  SPREAD       = 0.8F;
    private static final float  PULSE_RANGE  = 0.06F;
    private static final float  PULSE_RATE   = 0.09F;
    private static final float  GLOW         = 0.45F;

    private static List<MosaicDoor> doors = List.of();
    private static long scannedAt = -SCAN_EVERY;

    private MosaicDoorHint()
    {
    }

    public static void register()
    {
        LevelRenderEvents.COLLECT_SUBMITS.register(MosaicDoorHint::collectSubmits);
    }

    /** @return how many doors the hint last found near its player, which tests read */
    public static int found()
    {
        return doors.size();
    }

    private static boolean holdsVeil(Player player)
    {
        for (var hand : InteractionHand.values())
        {
            var stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof WraithVeilItem && !player.getCooldowns().isOnCooldown(stack))
                return true;
        }
        return false;
    }

    private static void collectSubmits(LevelRenderContext context)
    {
        var minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        var level = minecraft.level;
        if (player == null || level == null || !holdsVeil(player))
            return;

        long now = level.getGameTime();
        if (now - scannedAt >= SCAN_EVERY || now < scannedAt)
        {
            scannedAt = now;
            doors = scan(level, player.blockPosition());
        }
        if (doors.isEmpty())
            return;

        var levelState = context.levelState();
        var camera = levelState.cameraRenderState.pos;
        float time = levelState.gameTime + levelState.worldPartialTicks;
        float halfU = MosaicDoor.COLUMNS / 2F, halfV = MosaicDoor.ROWS / 2F;
        var poseStack = context.poseStack();
        for (var door : doors)
        {
            var center = door.center();
            if (center.distanceToSqr(player.position()) > SHOWN_WITHIN * SHOWN_WITHIN || isOpen(level, door))
                continue;
            var face = door.facing();
            var at = center.add(face.getUnitVec3().scale(GAP)).subtract(camera);
            float spread = SPREAD + PULSE_RANGE * Mth.sin(time * PULSE_RATE);
            poseStack.pushPose();
            poseStack.translate((float) at.x, (float) at.y, (float) at.z);
            WraithInk.submit(poseStack, context.submitNodeCollector(), face, halfU, halfV, WraithInk.variant(face, center, halfU, halfV),
                    WraithInk.color(spread, 0, GLOW, WraithInk.Mode.PREVIEW), LightCoordsUtil.FULL_BRIGHT, WraithInk.holding(level, face, center, halfU, halfV));
            poseStack.popPose();
        }
    }

    private static boolean isOpen(Level level, MosaicDoor door)
    {
        return !level.getEntitiesOfClass(VeilGate.class, door.mouth(0.1).inflate(0.2), gate -> !gate.stays()).isEmpty();
    }

    /** Finds every temple mosaic around a place by its tiles, and where its door is. */
    private static List<MosaicDoor> scan(Level level, BlockPos around)
    {
        var found = new ArrayList<MosaicDoor>();
        Set<Long> keys = new HashSet<>();
        var pos = new BlockPos.MutableBlockPos();
        for (int dy = -REACH_UP; dy <= REACH_UP; dy++)
        {
            for (int dz = -REACH; dz <= REACH; dz++)
            {
                for (int dx = -REACH; dx <= REACH; dx++)
                {
                    pos.setWithOffset(around, dx, dy, dz);
                    if (!level.getBlockState(pos).is(VoidHeartBlocks.UNDERWAY_MOSAIC))
                        continue;
                    var face = openSide(level, pos);
                    if (face == null)
                        continue;
                    var origin = MosaicDoor.mosaicOrigin(level, pos, face);
                    if (origin != null && keys.add(origin.asLong()))
                        found.add(new MosaicDoor(origin, face));
                }
            }
        }
        return found;
    }

    /** @return the side of a tile the temple is on: the one not against stone */
    private static Direction openSide(Level level, BlockPos tile)
    {
        for (var side : Direction.Plane.HORIZONTAL)
        {
            var next = tile.relative(side);
            if (!level.getBlockState(next).isCollisionShapeFullBlock(level, next))
                return side;
        }
        return null;
    }
}
