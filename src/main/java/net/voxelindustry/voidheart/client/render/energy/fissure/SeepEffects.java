package net.voxelindustry.voidheart.client.render.energy.fissure;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.client.render.VoidHeartRenderTypes;
import net.voxelindustry.voidheart.common.energy.fissure.FissureScars;
import net.voxelindustry.voidheart.common.energy.fissure.SeepMath;
import net.voxelindustry.voidheart.common.energy.fissure.VoidFissureBlock;
import net.voxelindustry.voidheart.common.energy.fissure.VoidFissureTile;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static net.voxelindustry.voidheart.client.render.energy.fissure.FissureLook.*;

/**
 * What a seep draws across the land, always shown since it is a hazard: low wisps of seep dark creeping outward over the ground and fading toward its
 * edge, dark vapor rising from the crack, an occasional black mote, the faint scars closed cracks leave, and the seep's edge as a dashed fissure violet
 * ring on demand. Density and reach follow the seep's radius, a wound leaks more heavily, and everything thins out while the crack closes.
 * <p>
 * All of it is blended, without depth writes, in {@link FissureRenderTypes#DARK}, except the ring in {@code MARKER}. Per seep and frame, at width 5: up to
 * about 160 wisps and 30 puffs of 8 quads each and a few dozen motes, simulated per client tick; a ring adds about 600 quads while shown.
 */
final class SeepEffects
{
    private static final double DRAW_DISTANCE = 112;

    // Wisps lying over the ground, a few per block of radius, living 5 to 9 seconds
    private static final float WISP_BASE        = 12;
    private static final float WISP_PER_BLOCK   = 4.5F;
    private static final float WOUND_HEAVIER    = 1.6F;
    private static final int   WISP_LIFE_MIN    = 100;
    private static final int   WISP_LIFE_MAX    = 180;
    private static final float WISP_SPEED_MIN   = 0.012F;
    private static final float WISP_SPEED_MAX   = 0.035F;
    private static final float WISP_LIFT_MIN    = 0.05F;
    private static final float WISP_LIFT_MAX    = 0.45F;
    private static final float WISP_LENGTH_MIN  = 1.2F;
    private static final float WISP_LENGTH_MAX  = 2.4F;
    private static final float WISP_WIDTH       = 0.95F;
    // Wisps lean off the line they creep along, so together they read as patches of fog rather than spokes
    private static final float WISP_LEAN        = 0.7F;
    private static final float WISP_ALPHA       = 0.32F;
    private static final float WISP_EDGE_POWER  = 0.8F;
    private static final int   WISP_FADE_IN     = 25;
    private static final int   WISP_FADE_OUT    = 40;

    // Vapor rising slowly from the crack
    private static final float VAPOR_BASE       = 0.03F;
    private static final float VAPOR_PER_BLOCK  = 0.03F;
    private static final int   VAPOR_LIFE_MIN   = 60;
    private static final int   VAPOR_LIFE_MAX   = 110;
    private static final float VAPOR_RISE       = 0.016F;
    private static final float VAPOR_SIZE_START = 0.22F;
    private static final float VAPOR_SIZE_END   = 0.85F;
    private static final float VAPOR_ALPHA      = 0.3F;

    // Black motes rising here and there in the seep
    private static final float MOTE_BASE        = 0.02F;
    private static final float MOTE_PER_BLOCK   = 0.002F;
    private static final int   MOTE_LIFE_MIN    = 40;
    private static final int   MOTE_LIFE_MAX    = 80;
    private static final float MOTE_RISE        = 0.03F;
    private static final float MOTE_HALF        = 0.035F;
    private static final float MOTE_ALPHA       = 0.85F;

    private static final float SCAR_HALF        = 0.04F;
    private static final float SCAR_ALPHA       = 0.5F;
    private static final float SCAR_Y           = 0.02F;

    private static final class Wisp
    {
        double x, y, z;
        float  dirX, dirZ, lookX, lookZ, speed, length, width, lift;
        long   born;
        int    life;
        int    columnX, columnZ;
    }

    private static final class Puff
    {
        double x, y, z;
        long   born;
        int    life;
    }

    private static final class Emitter
    {
        final BlockPos pos;
        VoidFissureTile tile;
        long  seen;
        float radius;
        float thinning;
        boolean wound;
        float wispCredit, vaporCredit, moteCredit;
        final List<Wisp> wisps  = new ArrayList<>();
        final List<Puff> vapor  = new ArrayList<>();
        final List<Puff> motes  = new ArrayList<>();

        Emitter(BlockPos pos)
        {
            this.pos = pos;
        }
    }

    private static final Map<BlockPos, Emitter> EMITTERS = new HashMap<>();

    private SeepEffects()
    {
    }

    static void clear()
    {
        EMITTERS.clear();
        FissureScars.clear();
        SeepRings.clear();
    }

    static void tick(Minecraft minecraft)
    {
        var level = minecraft.level;
        if (level == null || minecraft.player == null)
        {
            clear();
            return;
        }
        long time = level.getGameTime();
        var random = level.getRandom();
        var viewer = minecraft.player.position();

        for (var tile : VoidFissureTile.CLIENT_LOADED)
        {
            if (tile.getLevel() != level || tile.isRemoved())
                continue;
            var emitter = EMITTERS.computeIfAbsent(tile.getBlockPos(), Emitter::new);
            emitter.tile = tile;
            emitter.seen = time;
        }

        var iterator = EMITTERS.values().iterator();
        while (iterator.hasNext())
        {
            var emitter = iterator.next();
            if (emitter.seen != time)
                emitter.tile = null;

            emitter.wisps.removeIf(wisp -> time - wisp.born >= wisp.life);
            emitter.vapor.removeIf(puff -> time - puff.born >= puff.life);
            emitter.motes.removeIf(mote -> time - mote.born >= mote.life);
            if (emitter.tile == null)
            {
                emitter.thinning = Math.max(0, emitter.thinning - 0.05F);
                if (emitter.wisps.isEmpty() && emitter.vapor.isEmpty() && emitter.motes.isEmpty())
                    iterator.remove();
                continue;
            }

            var tile = emitter.tile;
            emitter.radius = tile.seepRadius(time);
            emitter.wound = tile.isWound();
            emitter.thinning = 1 - SeepMath.closing(tile.closingSince(), time);
            if (Vec3.atCenterOf(emitter.pos).distanceToSqr(viewer) > DRAW_DISTANCE * DRAW_DISTANCE)
                continue;

            for (var wisp : emitter.wisps)
                creep(level, wisp);
            spawn(level, emitter, random, time);
        }

        FissureScars.prune(time);
        SeepRings.tick(minecraft, time);
    }

    private static void spawn(Level level, Emitter emitter, RandomSource random, long time)
    {
        float radius = emitter.radius;
        float heavier = emitter.wound ? WOUND_HEAVIER : 1;
        double cx = emitter.pos.getX() + 0.5, cz = emitter.pos.getZ() + 0.5;

        if (radius > 0.5F)
        {
            float wanted = (WISP_BASE + WISP_PER_BLOCK * radius) * heavier;
            emitter.wispCredit += wanted / ((WISP_LIFE_MIN + WISP_LIFE_MAX) / 2F) * emitter.thinning;
            while (emitter.wispCredit >= 1)
            {
                emitter.wispCredit--;
                // Thicker near the crack, so the seep reads as leaking out of it
                double angle = random.nextDouble() * Math.PI * 2;
                double distance = 0.3 + (radius - 0.3) * Math.pow(random.nextDouble(), 1.4);
                var wisp = new Wisp();
                wisp.dirX = (float) Math.cos(angle);
                wisp.dirZ = (float) Math.sin(angle);
                double lean = angle + (random.nextDouble() * 2 - 1) * WISP_LEAN;
                wisp.lookX = (float) Math.cos(lean);
                wisp.lookZ = (float) Math.sin(lean);
                wisp.x = cx + wisp.dirX * distance;
                wisp.z = cz + wisp.dirZ * distance;
                double ground = surface(level, wisp.x, wisp.z, emitter.pos.getY());
                if (Double.isNaN(ground))
                    continue;
                wisp.lift = WISP_LIFT_MIN + random.nextFloat() * (WISP_LIFT_MAX - WISP_LIFT_MIN);
                wisp.y = ground + wisp.lift;
                wisp.columnX = Mth.floor(wisp.x);
                wisp.columnZ = Mth.floor(wisp.z);
                wisp.speed = WISP_SPEED_MIN + random.nextFloat() * (WISP_SPEED_MAX - WISP_SPEED_MIN);
                wisp.length = WISP_LENGTH_MIN + random.nextFloat() * (WISP_LENGTH_MAX - WISP_LENGTH_MIN);
                wisp.width = WISP_WIDTH * (0.7F + 0.6F * random.nextFloat());
                wisp.born = time;
                wisp.life = WISP_LIFE_MIN + random.nextInt(WISP_LIFE_MAX - WISP_LIFE_MIN + 1);
                emitter.wisps.add(wisp);
            }

            emitter.moteCredit += (MOTE_BASE + MOTE_PER_BLOCK * radius) * heavier * emitter.thinning;
            while (emitter.moteCredit >= 1)
            {
                emitter.moteCredit--;
                double angle = random.nextDouble() * Math.PI * 2;
                double distance = radius * Math.sqrt(random.nextDouble());
                var mote = new Puff();
                mote.x = cx + Math.cos(angle) * distance;
                mote.z = cz + Math.sin(angle) * distance;
                double ground = surface(level, mote.x, mote.z, emitter.pos.getY());
                if (Double.isNaN(ground))
                    continue;
                mote.y = ground + 0.1;
                mote.born = time;
                mote.life = MOTE_LIFE_MIN + random.nextInt(MOTE_LIFE_MAX - MOTE_LIFE_MIN + 1);
                emitter.motes.add(mote);
            }
        }

        var tile = emitter.tile;
        float crack = emitter.wound ? CrackShape.woundLength(tile.isConfined()) : CrackShape.fissureLength(tile.width());
        emitter.vaporCredit += (VAPOR_BASE + VAPOR_PER_BLOCK * crack) * emitter.thinning;
        boolean axisX = tile.getBlockState().hasProperty(VoidFissureBlock.AXIS) && tile.getBlockState().getValue(VoidFissureBlock.AXIS) == Direction.Axis.X;
        while (emitter.vaporCredit >= 1)
        {
            emitter.vaporCredit--;
            float along = (random.nextFloat() - 0.5F) * crack * 0.8F;
            var puff = new Puff();
            puff.x = cx + (axisX ? along : 0) + (random.nextFloat() - 0.5F) * 0.15F;
            puff.z = cz + (axisX ? 0 : along) + (random.nextFloat() - 0.5F) * 0.15F;
            puff.y = emitter.pos.getY() + 0.1;
            puff.born = time;
            puff.life = VAPOR_LIFE_MIN + random.nextInt(VAPOR_LIFE_MAX - VAPOR_LIFE_MIN + 1);
            emitter.vapor.add(puff);
        }
    }

    /**
     * Moves a wisp outward and keeps it on the ground as it crosses into a new column.
     */
    private static void creep(Level level, Wisp wisp)
    {
        wisp.x += wisp.dirX * wisp.speed;
        wisp.z += wisp.dirZ * wisp.speed;
        int columnX = Mth.floor(wisp.x), columnZ = Mth.floor(wisp.z);
        if (columnX == wisp.columnX && columnZ == wisp.columnZ)
            return;
        double ground = surface(level, wisp.x, wisp.z, Mth.floor(wisp.y - wisp.lift));
        if (!Double.isNaN(ground))
            wisp.y = ground + wisp.lift;
        wisp.columnX = columnX;
        wisp.columnZ = columnZ;
    }

    static void collectSubmits(LevelRenderContext context)
    {
        if (Minecraft.getInstance().level == null)
            return;
        var levelState = context.levelState();
        var camera = levelState.cameraRenderState.pos;
        float partialTicks = levelState.worldPartialTicks;
        float time = levelState.gameTime + partialTicks;

        if (!EMITTERS.isEmpty() || !FissureScars.scars().isEmpty())
            context.submitNodeCollector().submitCustomGeometry(context.poseStack(), FissureRenderTypes.DARK, (pose, buffer) ->
            {
                scars(pose, buffer, camera, time);
                EMITTERS.values().forEach(emitter -> draw(pose, buffer, emitter, camera, time, partialTicks));
            });
        if (SeepRings.any())
            context.submitNodeCollector().submitCustomGeometry(context.poseStack(), VoidHeartRenderTypes.MARKER, (pose, buffer) -> SeepRings.draw(pose, buffer, camera, time));
    }

    private static void draw(PoseStack.Pose pose, VertexConsumer buffer, Emitter emitter, Vec3 camera, float time, float partialTicks)
    {
        if (Vec3.atCenterOf(emitter.pos).distanceToSqr(camera) > DRAW_DISTANCE * DRAW_DISTANCE)
            return;
        double cx = emitter.pos.getX() + 0.5, cz = emitter.pos.getZ() + 0.5;
        float radius = Math.max(0.5F, emitter.radius);
        float thinning = emitter.thinning;

        for (var wisp : emitter.wisps)
        {
            float age = time - wisp.born;
            double x = wisp.x + wisp.dirX * wisp.speed * partialTicks, z = wisp.z + wisp.dirZ * wisp.speed * partialTicks;
            double distance = Math.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz));
            float edge = (float) Math.pow(Mth.clamp(1 - distance / radius, 0, 1), WISP_EDGE_POWER);
            float alpha = WISP_ALPHA * envelope(age, wisp.life, WISP_FADE_IN, WISP_FADE_OUT) * edge * thinning;
            flatBlob(buffer, pose, relative(x, wisp.y, z, camera), wisp.lookX, wisp.lookZ, wisp.length / 2, wisp.width / 2, SEEP_DARK, alpha);
        }

        for (var puff : emitter.vapor)
        {
            float age = time - puff.born;
            float progress = Mth.clamp(age / puff.life, 0, 1);
            float size = Mth.lerp(progress, VAPOR_SIZE_START, VAPOR_SIZE_END);
            float alpha = VAPOR_ALPHA * envelope(age, puff.life, 15, puff.life * 0.6F) * thinning;
            softDot(buffer, pose, relative(puff.x, puff.y + age * VAPOR_RISE + size * 0.5, puff.z, camera), size, SEEP_DARK, alpha);
        }

        for (var mote : emitter.motes)
        {
            float age = time - mote.born;
            float alpha = MOTE_ALPHA * envelope(age, mote.life, 8, 20) * thinning;
            if (alpha > 0.01F)
                mote(buffer, pose, relative(mote.x, mote.y + age * MOTE_RISE, mote.z, camera), MOTE_HALF, color(MOTE_BLACK, alpha));
        }
    }

    private static float envelope(float age, float life, float fadeIn, float fadeOut)
    {
        return Mth.clamp(Math.min(age / fadeIn, (life - age) / fadeOut), 0, 1);
    }

    /**
     * A faint seam where a crack has closed, fading over about a minute.
     */
    private static void scars(PoseStack.Pose pose, VertexConsumer buffer, Vec3 camera, float time)
    {
        var shape = new CrackShape();
        for (var scar : FissureScars.scars())
        {
            float fade = 1 - Mth.clamp((time - scar.since()) / FissureScars.FADE_TICKS, 0, 1);
            if (fade <= 0 || Vec3.atCenterOf(scar.pos()).distanceToSqr(camera) > DRAW_DISTANCE * DRAW_DISTANCE)
                continue;
            float length = scar.wound() ? CrackShape.woundLength(scar.confined()) : CrackShape.fissureLength(Math.round(scar.width()));
            shape.build(scar.seed(), length, 0, scar.wound(), (joint, edge) -> 0, 0);
            int on = color(BRUISE, SCAR_ALPHA * fade);
            int off = color(BRUISE, 0);
            boolean axisX = scar.axis() == Direction.Axis.X;
            double ox = scar.pos().getX() + 0.5, oy = scar.pos().getY() + SCAR_Y, oz = scar.pos().getZ() + 0.5;
            for (int i = 0; i < CrackShape.JOINTS - 1; i++)
            {
                for (int side = -1; side <= 1; side += 2)
                {
                    seamVertex(buffer, pose, camera, axisX, ox, oy, oz, shape.along[i], shape.center[i], on);
                    seamVertex(buffer, pose, camera, axisX, ox, oy, oz, shape.along[i + 1], shape.center[i + 1], on);
                    seamVertex(buffer, pose, camera, axisX, ox, oy, oz, shape.along[i + 1], shape.center[i + 1] + side * SCAR_HALF, off);
                    seamVertex(buffer, pose, camera, axisX, ox, oy, oz, shape.along[i], shape.center[i] + side * SCAR_HALF, off);
                }
            }
        }
    }

    private static void seamVertex(VertexConsumer buffer, PoseStack.Pose pose, Vec3 camera, boolean axisX, double ox, double oy, double oz,
                                   float along, float across, int color)
    {
        var at = axisX ? relative(ox + along, oy, oz + across, camera) : relative(ox + across, oy, oz + along, camera);
        vertex(buffer, pose, at, color);
    }
}
