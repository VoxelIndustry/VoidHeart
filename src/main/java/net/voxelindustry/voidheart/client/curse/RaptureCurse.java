package net.voxelindustry.voidheart.client.curse;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.FilterMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.voxelindustry.voidheart.client.curse.sky.CurseSkyUniform;
import net.voxelindustry.voidheart.client.curse.sky.SkyFrame;
import net.voxelindustry.voidheart.client.curse.sky.StarField;
import net.voxelindustry.voidheart.common.curse.RaptureLift;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * The sky notices the player. Every star opens into an eye, all of them come to stare straight at the player, the ground starts coming apart upward,
 * and a mouth opens at the zenith to take them. Only the lift is real, see {@link RaptureLift}.
 */
public final class RaptureCurse extends ClientCurse
{
    private static final RenderPipeline EYES_PIPELINE = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(MODID, "pipeline/curse_rapture_field"))
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(CurseSkyUniform.BINDINGS)
            .withVertexShader(Identifier.fromNamespaceAndPath(MODID, "core/curse_rapture_field"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(MODID, "core/curse_rapture_field"))
            .withVertexBinding(0, StarField.VERTEX_FORMAT)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            // With alpha zero this adds up as the vanilla stars do, with alpha one it is solid, so one star can turn into the other
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA))
            .withCull(false)
            .build();

    // The mouth is expensive per pixel but drawn on a coarse grid, so each cell is worked out once a frame into a small texture, however large it is on screen
    private static final RenderPipeline MOUTH_BAKE_PIPELINE = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(MODID, "pipeline/curse_rapture_zenith_bake"))
            .withBindGroupLayout(CurseSkyUniform.BINDINGS)
            .withVertexShader(Identifier.fromNamespaceAndPath(MODID, "core/curse_rapture_zenith_bake"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(MODID, "core/curse_rapture_zenith_bake"))
            .withVertexBinding(0, DefaultVertexFormat.POSITION)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withColorTargetState(ColorTargetState.DEFAULT)
            .withCull(false)
            .build();

    private static final RenderPipeline MOUTH_PIPELINE = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(MODID, "pipeline/curse_rapture_zenith"))
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(CurseSkyUniform.BINDINGS)
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
            .withVertexShader(Identifier.fromNamespaceAndPath(MODID, "core/curse_rapture_zenith"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(MODID, "core/curse_rapture_zenith"))
            .withVertexBinding(0, DefaultVertexFormat.POSITION)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withCull(false)
            .build();

    // The same as BAKED_SIZE in the zenith shaders, one texel to a cell of the mouth's grid: enough for the whole quad at 72 cells per half width of 0.7
    private static final int MOUTH_BAKED_SIZE = 206;

    // Share of the opening spent as a thin seam before the jaws part
    private static final float MOUTH_SEAM_SHARE   = 0.26F;
    // How open the mouth is once split, then how much wider it keeps getting until the lift ends
    private static final float MOUTH_SPLIT_OPEN   = 0.84F;
    private static final float MOUTH_WIDENING     = 0.0009F;
    private static final float MOUTH_WIDEST       = 1.06F;
    // A share of how open it is, and how many radians a tick
    private static final float MOUTH_BREATH       = 0.035F;
    private static final float MOUTH_BREATH_RATE  = 0.075F;
    private static final float MOUTH_SHUDDER      = 0.012F;
    private static final float MOUTH_SHUDDER_RATE = 0.31F;

    private static final float RUMBLE_VOLUME         = 1;
    // The sound engine doesn't go lower
    private static final float RUMBLE_PITCH          = 0.5F;
    private static final float RUMBLE_SWELL_TICKS    = 40;
    private static final float DRONE_VOLUME          = 0.8F;
    private static final int   THUNDER_INTERVAL_LOW  = 50;
    private static final int   THUNDER_INTERVAL_HIGH = 90;
    private static final float THUNDER_VOLUME        = 0.9F;

    private static final float SPAWN_RADIUS       = 14;
    private static final float SPAWN_LEAST_RADIUS = 2.5F;
    // Under 1 gathers them toward the player, 0.5 would spread them evenly over the disc
    private static final float SPAWN_SPREAD       = 0.72F;
    private static final float SPAWNS_LEAST       = 1;
    private static final float SPAWNS_MOST        = 13;
    private static final int   SPAWN_RAMP_TICKS   = 50;
    private static final int   LIVE_MOST          = 900;
    private static final float WALL_SHARE         = 0.45F;
    // On grass, the rest being the dirt underneath, as vanilla break particles have it
    private static final float GROUND_TOP_SHARE   = 0.75F;
    private static final float FACE_PUSH          = 0.03F;
    private static final int   GROUND_SCAN_UP     = 3;
    private static final int   GROUND_SCAN_DOWN   = 24;
    private static final int   WALL_HEIGHT_BELOW  = 2;
    private static final int   WALL_HEIGHT_ABOVE  = 9;
    private static final int   WALL_RAY_CROSSINGS = 26;

    // Clouds would veil the sky, so they part as the eyes open and gather again as all of it lets go
    private static final int CLOUDS_PART_TICKS   = 45;
    private static final int CLOUDS_GATHER_TICKS = RaptureLift.RELEASE_TICKS;

    private static @Nullable GpuBuffer     mouthQuad;
    private static @Nullable TextureTarget mouthTexture;

    private final List<RaptureRiseParticle> risers = new ArrayList<>();
    private final List<RumbleSound>         rumble = new ArrayList<>();
    private final BlockPos.MutableBlockPos  cursor = new BlockPos.MutableBlockPos();
    private final int                       endTick;

    private boolean facingKnown;
    private float   facingCos = 1;
    private float   facingSin;
    private double  groundY;
    private int     nextThunder = RaptureLift.RUMBLE_TICK;
    private int     peakRisers;
    private int     risersFromGround;
    private int     risersFromWalls;

    private @Nullable GpuBufferSlice uniform;

    public RaptureCurse(long seed, int duration)
    {
        super(seed, duration);
        this.endTick = RaptureLift.endTick(seed);
    }

    /**
     * The most block chips alive at once so far, for tests.
     */
    public int peakRisers()
    {
        return peakRisers;
    }

    public int liveRisers()
    {
        return risers.size();
    }

    public int risersFromGround()
    {
        return risersFromGround;
    }

    public int risersFromWalls()
    {
        return risersFromWalls;
    }

    /**
     * The sounds of the rumble playing now, for tests.
     */
    public List<SoundInstance> rumble()
    {
        return List.copyOf(rumble);
    }

    public int height()
    {
        return RaptureLift.height(seed);
    }

    @Override
    protected void tick(Minecraft minecraft, LocalPlayer player)
    {
        if (!facingKnown)
        {
            float yaw = player.getYRot() * Mth.DEG_TO_RAD;
            facingCos = Mth.cos(yaw);
            facingSin = Mth.sin(yaw);
            facingKnown = true;
        }
        if (player.onGround() || age < RaptureLift.LIFT_TICK)
            groundY = player.getY();

        risers.removeIf(riser -> !riser.isAlive());

        if (age == endTick)
            stopRumble(minecraft);
        if (age >= RaptureLift.RUMBLE_TICK && age < endTick)
        {
            tickRumble(minecraft);
            spawnRisers(minecraft, player);
        }
    }

    @Override
    protected void onEnd()
    {
        stopRumble(Minecraft.getInstance());
        risers.forEach(RaptureRiseParticle::release);
        risers.clear();
    }

    private void tickRumble(Minecraft minecraft)
    {
        if (age == RaptureLift.RUMBLE_TICK)
        {
            startLoop(minecraft, SoundEvents.AMBIENT_BASALT_DELTAS_LOOP.value(), RUMBLE_VOLUME);
            startLoop(minecraft, SoundEvents.BEACON_AMBIENT, DRONE_VOLUME);
        }
        if (age >= nextThunder)
        {
            var thunder = new RumbleSound(SoundEvents.LIGHTNING_BOLT_THUNDER, random, THUNDER_VOLUME, false);
            minecraft.getSoundManager().play(thunder);
            rumble.removeIf(sound -> !sound.isLooping() && !minecraft.getSoundManager().isActive(sound));
            rumble.add(thunder);
            nextThunder = age + Mth.nextInt(random, THUNDER_INTERVAL_LOW, THUNDER_INTERVAL_HIGH);
        }
    }

    private void startLoop(Minecraft minecraft, SoundEvent sound, float volume)
    {
        var loop = new RumbleSound(sound, random, volume, true);
        minecraft.getSoundManager().play(loop);
        rumble.add(loop);
    }

    private void stopRumble(Minecraft minecraft)
    {
        rumble.forEach(sound ->
        {
            sound.cut();
            minecraft.getSoundManager().stop(sound);
        });
        rumble.clear();
    }

    private void spawnRisers(Minecraft minecraft, LocalPlayer player)
    {
        peakRisers = Math.max(peakRisers, risers.size());
        if (risers.size() >= LIVE_MOST)
            return;

        var level = minecraft.level;
        float ramp = Mth.clamp((age - RaptureLift.RUMBLE_TICK) / (float) SPAWN_RAMP_TICKS, 0, 1);
        float wanted = Mth.lerp(ramp, SPAWNS_LEAST, SPAWNS_MOST);
        int spawns = Mth.floor(wanted) + (random.nextFloat() < Mth.frac(wanted) ? 1 : 0);

        for (int i = 0; i < spawns; i++)
        {
            var riser = random.nextFloat() < WALL_SHARE ? fromWall(level, player) : null;
            if (riser != null)
                risersFromWalls++;
            else
            {
                riser = fromGround(level, player);
                if (riser != null)
                    risersFromGround++;
            }
            if (riser != null)
            {
                risers.add(riser);
                minecraft.particleEngine.add(riser);
            }
        }
    }

    /**
     * A top face under a random point around the player, however high they have been lifted. Stops at the first block found, usually within a few steps.
     */
    private @Nullable RaptureRiseParticle fromGround(ClientLevel level, LocalPlayer player)
    {
        float angle = random.nextFloat() * Mth.TWO_PI;
        float radius = Mth.lerp((float) Math.pow(random.nextFloat(), SPAWN_SPREAD), SPAWN_LEAST_RADIUS, SPAWN_RADIUS);
        double x = player.getX() + Mth.cos(angle) * radius;
        double z = player.getZ() + Mth.sin(angle) * radius;

        int top = Mth.floor(player.getY()) + GROUND_SCAN_UP;
        boolean openAbove = false;
        cursor.set(Mth.floor(x), top, Mth.floor(z));
        for (int y = top; y > top - GROUND_SCAN_UP - GROUND_SCAN_DOWN; y--)
        {
            cursor.setY(y);
            var state = level.getBlockState(cursor);
            if (!isSurface(state))
            {
                openAbove = true;
                continue;
            }
            if (openAbove)
                return RaptureRiseParticle.onFace(level, x, y + 1, z, Direction.UP, FACE_PUSH, state, cursor.immutable(), random.nextFloat(), random.nextFloat() < GROUND_TOP_SHARE);
        }
        return null;
    }

    /**
     * A side face met by a horizontal ray from the player at a random height, walking the block grid so the face it entered through is known.
     */
    private @Nullable RaptureRiseParticle fromWall(ClientLevel level, LocalPlayer player)
    {
        float angle = random.nextFloat() * Mth.TWO_PI;
        float dirX = Mth.cos(angle);
        float dirZ = Mth.sin(angle);
        double startX = player.getX();
        double startZ = player.getZ();
        int y = Mth.floor(groundY) + Mth.nextInt(random, -WALL_HEIGHT_BELOW, WALL_HEIGHT_ABOVE);

        int cellX = Mth.floor(startX);
        int cellZ = Mth.floor(startZ);
        int stepX = dirX > 0 ? 1 : -1;
        int stepZ = dirZ > 0 ? 1 : -1;
        double perX = 1 / Math.max(Math.abs(dirX), 1.0E-4F);
        double perZ = 1 / Math.max(Math.abs(dirZ), 1.0E-4F);
        double nextX = (dirX > 0 ? cellX + 1 - startX : startX - cellX) * perX;
        double nextZ = (dirZ > 0 ? cellZ + 1 - startZ : startZ - cellZ) * perZ;

        cursor.set(cellX, y, cellZ);
        if (isSurface(level.getBlockState(cursor)))
            return null;

        for (int crossing = 0; crossing < WALL_RAY_CROSSINGS; crossing++)
        {
            boolean alongX = nextX < nextZ;
            double travelled = alongX ? nextX : nextZ;
            if (travelled > SPAWN_RADIUS)
                return null;

            if (alongX)
            {
                cellX += stepX;
                nextX += perX;
            }
            else
            {
                cellZ += stepZ;
                nextZ += perZ;
            }

            cursor.set(cellX, y, cellZ);
            var state = level.getBlockState(cursor);
            if (!isSurface(state))
                continue;

            double hitY = y + random.nextFloat();
            double hitX = startX + dirX * travelled;
            double hitZ = startZ + dirZ * travelled;
            var face = alongX ? (stepX > 0 ? Direction.WEST : Direction.EAST) : (stepZ > 0 ? Direction.NORTH : Direction.SOUTH);
            return RaptureRiseParticle.onFace(level, hitX, hitY, hitZ, face, FACE_PUSH, state, cursor.immutable(), random.nextFloat(), false);
        }
        return null;
    }

    private static boolean isSurface(BlockState state)
    {
        return state.isSolidRender() && state.shouldSpawnTerrainParticles();
    }

    @Override
    public boolean hidesStars()
    {
        return true;
    }

    @Override
    public void prepareSky(SkyRenderState state, SkyFrame frame)
    {
        StarField.prepare();
        prepareMouthQuad();

        float now = age + frame.partialTicks();
        var stateA = new Vector4f(
                now / RaptureLift.EYES_OPEN_TICKS,
                Mth.clamp((now - RaptureLift.STARE_START_TICK) / (RaptureLift.STARE_END_TICK - RaptureLift.STARE_START_TICK), 0, 1),
                Mth.clamp((now - endTick) / RaptureLift.EYES_CLOSE_TICKS, 0, 1),
                mouthOpening(now));
        var stateB = new Vector4f(
                facingCos,
                facingSin,
                Mth.clamp((now - RaptureLift.MOUTH_TICK) / RaptureLift.MOUTH_OPEN_TICKS, 0, 1),
                Mth.clamp((now - endTick) / RaptureLift.MOUTH_SNAP_TICKS, 0, 1));
        uniform = CurseSkyUniform.write(frame, now / 20F, stateA, stateB);

        if (mouthShows())
            bakeMouth();
    }

    private boolean mouthShows()
    {
        return age >= RaptureLift.MOUTH_TICK && age <= endTick + RaptureLift.MOUTH_SNAP_TICKS;
    }

    private void bakeMouth()
    {
        if (mouthTexture == null)
            mouthTexture = new TextureTarget("Curse zenith", MOUTH_BAKED_SIZE, MOUTH_BAKED_SIZE, GpuFormat.RGBA8_UNORM, null);

        var indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        try (var renderPass = RenderSystem.getDevice().createCommandEncoder()
                .createRenderPass(() -> "Curse zenith", mouthTexture.getColorTextureView(), Optional.of(new Vector4f(0, 0, 0, 0))))
        {
            renderPass.setPipeline(RenderSystem.getCompiledPipeline(MOUTH_BAKE_PIPELINE));
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform(CurseSkyUniform.NAME, uniform);
            renderPass.setVertexBuffer(0, mouthQuad.slice());
            renderPass.setIndexBuffer(indices.getBuffer(6), indices.type());
            renderPass.drawIndexed(6, 1, 0, 0, 0);
        }
    }

    /**
     * A thin seam runs across first and only then parts, keeps slowly widening and breathing, then snaps shut with its speed still growing.
     */
    private float mouthOpening(float now)
    {
        float splitting = Mth.clamp((now - RaptureLift.MOUTH_TICK) / RaptureLift.MOUTH_OPEN_TICKS, 0, 1);
        if (splitting <= 0)
            return 0;

        float parting = Mth.clamp((splitting - MOUTH_SEAM_SHARE) / (1 - MOUTH_SEAM_SHARE), 0, 1);
        float eased = 1 - (1 - parting) * (1 - parting);
        float opened = eased * MOUTH_SPLIT_OPEN;
        float past = Math.max(0, now - RaptureLift.MOUTH_TICK - RaptureLift.MOUTH_OPEN_TICKS);
        opened = Math.min(MOUTH_WIDEST, opened + past * MOUTH_WIDENING);
        opened *= 1 + splitting * (MOUTH_BREATH * Mth.sin(now * MOUTH_BREATH_RATE) + MOUTH_SHUDDER * Mth.sin(now * MOUTH_SHUDDER_RATE));

        float snapping = Mth.clamp((now - endTick) / RaptureLift.MOUTH_SNAP_TICKS, 0, 1);
        return opened * (1 - snapping * snapping);
    }

    /**
     * The color to draw the clouds with, which fade out over its alpha while this curse runs.
     */
    public static int partClouds(int color)
    {
        if (!(CurseDirector.active() instanceof RaptureCurse curse))
            return color;

        float now = curse.age + Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float parting = Mth.clamp(now / CLOUDS_PART_TICKS, 0, 1);
        float gathering = Mth.clamp((now - curse.endTick) / CLOUDS_GATHER_TICKS, 0, 1);
        return ARGB.multiplyAlpha(color, Math.max(1 - parting, gathering));
    }

    private static void prepareMouthQuad()
    {
        if (mouthQuad != null)
            return;

        var data = MemoryUtil.memAlloc(4 * 3 * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        try
        {
            data.putFloat(-1).putFloat(0).putFloat(-1);
            data.putFloat(1).putFloat(0).putFloat(-1);
            data.putFloat(1).putFloat(0).putFloat(1);
            data.putFloat(-1).putFloat(0).putFloat(1);
            data.flip();
            mouthQuad = RenderSystem.getDevice().createBuffer(() -> "Curse zenith quad", GpuBuffer.USAGE_VERTEX, data);
        } finally
        {
            MemoryUtil.memFree(data);
        }
    }

    @Override
    public void drawSky(RenderPass renderPass, SkyFrame frame)
    {
        if (uniform == null)
            return;

        StarField.draw(renderPass, EYES_PIPELINE, uniform);

        if (mouthQuad == null || mouthTexture == null || !mouthShows())
            return;

        var indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        renderPass.setPipeline(RenderSystem.getCompiledPipeline(MOUTH_PIPELINE));
        RenderSystem.bindDefaultUniforms(renderPass);
        renderPass.setUniform(CurseSkyUniform.NAME, uniform);
        renderPass.setUniform("Sampler0", mouthTexture.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
        renderPass.setVertexBuffer(0, mouthQuad.slice());
        renderPass.setIndexBuffer(indices.getBuffer(6), indices.type());
        renderPass.drawIndexed(6, 1, 0, 0, 0);
    }

    /**
     * Comes from inside the player's head, the looping layers swell in, and all of it can be cut at once.
     */
    private static final class RumbleSound extends AbstractTickableSoundInstance
    {
        private static final float QUIETEST = 0.01F;

        private final float fullVolume;
        private int ticks;

        private RumbleSound(SoundEvent event, RandomSource random, float volume, boolean loop)
        {
            super(event, SoundSource.HOSTILE, random);
            this.fullVolume = volume;
            this.looping = loop;
            this.delay = 0;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.pitch = RUMBLE_PITCH;
            this.volume = loop ? QUIETEST : volume;
        }

        @Override
        public void tick()
        {
            ticks++;
            if (looping)
                volume = Math.max(QUIETEST, fullVolume * Mth.clamp(ticks / RUMBLE_SWELL_TICKS, 0, 1));
        }

        private void cut()
        {
            stop();
        }
    }
}
