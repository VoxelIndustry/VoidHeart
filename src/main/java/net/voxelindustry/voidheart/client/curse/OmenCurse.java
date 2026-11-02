package net.voxelindustry.voidheart.client.curse;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.client.curse.sky.CurseSkyUniform;
import net.voxelindustry.voidheart.client.curse.sky.SkyFrame;
import net.voxelindustry.voidheart.client.curse.sky.StarField;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * The night sky falls sick and bleeds. The stars the player was looking at go red one by one and start to tremble, slow waves of red spread
 * between them and light them as they pass, then it rains blood for a few seconds, spattering the ground. In the end each star goes back to what it was.
 */
public final class OmenCurse extends ClientCurse
{
    private static final RenderPipeline STARS_PIPELINE = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(MODID, "pipeline/curse_omen"))
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(CurseSkyUniform.BINDINGS)
            .withVertexShader(Identifier.fromNamespaceAndPath(MODID, "core/curse_omen"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(MODID, "core/curse_omen"))
            .withVertexBinding(0, StarField.VERTEX_FORMAT)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withColorTargetState(new ColorTargetState(BlendFunction.OVERLAY))
            .build();

    private static final RenderPipeline RIPPLE_PIPELINE = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(MODID, "pipeline/curse_omen_ripple"))
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(CurseSkyUniform.BINDINGS)
            .withVertexShader(Identifier.fromNamespaceAndPath(MODID, "core/curse_omen_ripple"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(MODID, "core/curse_omen_ripple"))
            .withVertexBinding(0, DefaultVertexFormat.POSITION)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withColorTargetState(new ColorTargetState(BlendFunction.OVERLAY))
            .withCull(false)
            .build();

    // In ticks from the start. The very first frames still show the vanilla sky
    private static final int TURN_START   = 2;
    private static final int TURN_END     = 40;
    private static final int WOBBLE_START = 20;
    private static final int WOBBLE_END   = 55;
    private static final int RIPPLE_START = 40;
    private static final int RIPPLE_FULL  = 75;
    public static final  int RAIN_START   = 100;
    public static final  int RAIN_END     = 240;
    private static final int RAIN_RAMP    = 20;

    // In ticks before the end, leaving the last frames equal to the vanilla sky
    private static final int RIPPLE_FADE_FROM = 58;
    private static final int RIPPLE_FADE_TO   = 8;
    private static final int RETURN_FROM      = 44;
    private static final int RETURN_TO        = 4;

    private static final float SECONDS_PER_TICK = 1 / 20F;

    // The waves step back as the rain takes over
    private static final float RIPPLE_UNDER_RAIN = 0.15F;
    // The first wave starts about where the player is looking, however low that is
    private static final float FIRST_WAVE_LOWEST  = 0.42F;
    private static final float FIRST_WAVE_HIGHEST = 0.97F;
    private static final float FIRST_WAVE_OFF_AIM = 0.35F;

    private static final float PEAK_DROPS_PER_TICK = 34;
    private static final float DECREASED_SHARE     = 0.5F;
    private static final float MINIMAL_SHARE       = 0.2F;
    private static final int   MAX_LIVE            = 1400;
    // Slowly swells and thins out the rain, by this share at most
    private static final float GUST_DEPTH          = 0.25F;
    private static final float GUST_RATE           = 0.13F;

    private static final float SPAWN_RADIUS       = 16;
    private static final float SPAWN_NEAREST      = 2;
    // Above 0.5, drops crowd toward the camera rather than spreading evenly over the disc
    private static final float SPAWN_SPREAD       = 0.7F;
    private static final float SPAWN_HEIGHT_MIN   = 8;
    private static final float SPAWN_HEIGHT_EXTRA = 12;
    // With the open sky this far above the camera, the player is underground and nothing would be seen
    private static final int   SURFACE_MAX_ABOVE  = 20;

    private static final float FALL_SPEED_MIN      = 0.6F;
    private static final float FALL_SPEED_MAX      = 0.76F;
    private static final float SLANT_MIN           = 0.07F;
    private static final float SLANT_MAX           = 0.13F;
    private static final float SLANT_JITTER        = 0.02F;
    private static final float DROP_SIZE_MIN       = 0.26F;
    private static final float DROP_SIZE_MAX       = 0.42F;
    // Above 1, large drops are rare
    private static final float DROP_SIZE_SKEW      = 1.8F;

    public static final  int   MAX_STAINS        = 150;
    // Landing near the camera, where it shows, a drop is likelier to leave a mark
    private static final float STAIN_CHANCE_NEAR = 0.14F;
    private static final float STAIN_CHANCE_FAR  = 0.018F;
    private static final float STAIN_NEAR        = 6;
    private static final float STAIN_FAR         = 16;
    private static final float STAIN_SIZE_MIN    = 0.14F;
    private static final float STAIN_SIZE_MAX    = 0.4F;
    private static final float STAIN_SIZE_SKEW   = 1.5F;
    private static final int   STAIN_LIFE_MIN    = 60;
    private static final int   STAIN_LIFE_EXTRA  = 40;
    // All must have dried this many ticks before the end, and a stain this short isn't worth starting
    private static final int   STAIN_END_MARGIN  = 4;
    private static final int   STAIN_LIFE_LEAST  = 16;

    private static final float PATTER_VOLUME = 0.28F;
    private static final float PATTER_PITCH  = 0.58F;
    // Per tick, toward how much of the disc around the player lies under the open sky
    private static final float PATTER_FOLLOW = 0.1F;

    private static final Identifier[] DROP_SPRITES = {
            Identifier.fromNamespaceAndPath(MODID, "curse_omen_drop0"),
            Identifier.fromNamespaceAndPath(MODID, "curse_omen_drop1"),
            Identifier.fromNamespaceAndPath(MODID, "curse_omen_drop2")
    };
    private static final Identifier[] SPLASH_SPRITES = {
            Identifier.fromNamespaceAndPath(MODID, "curse_omen_splash0"),
            Identifier.fromNamespaceAndPath(MODID, "curse_omen_splash1")
    };
    private static final Identifier[] STAIN_SPRITES = {
            Identifier.fromNamespaceAndPath(MODID, "curse_omen_stain0"),
            Identifier.fromNamespaceAndPath(MODID, "curse_omen_stain1"),
            Identifier.fromNamespaceAndPath(MODID, "curse_omen_stain2"),
            Identifier.fromNamespaceAndPath(MODID, "curse_omen_stain3")
    };

    private final float skySeed;
    private final float slantX;
    private final float slantZ;
    private final float gustPhase;

    private final List<SingleQuadParticle> live   = new ArrayList<>();
    private final List<OmenStainParticle>  stains = new ArrayList<>();

    private @Nullable OmenPatterSound patter;
    private @Nullable GpuBufferSlice  uniform;

    private float ripple;
    private float firstWaveAzimuth;
    private float firstWaveHeight = FIRST_WAVE_HIGHEST;
    private float dropsDue;
    private float openSky = 1;

    public OmenCurse(long seed, int duration)
    {
        super(seed, duration);
        skySeed = random.nextFloat();
        float windAngle = random.nextFloat() * Mth.TWO_PI;
        float slant = Mth.lerp(random.nextFloat(), SLANT_MIN, SLANT_MAX);
        slantX = Mth.cos(windAngle) * slant;
        slantZ = Mth.sin(windAngle) * slant;
        gustPhase = random.nextFloat() * Mth.TWO_PI;
    }

    @Override
    public boolean hidesStars()
    {
        return true;
    }

    private static float ramp(float time, float from, float to)
    {
        return Mth.clamp((time - from) / (to - from), 0, 1);
    }

    private static float rainLevel(float time)
    {
        return Math.min(ramp(time, RAIN_START, RAIN_START + RAIN_RAMP), 1 - ramp(time, RAIN_END - RAIN_RAMP, RAIN_END));
    }

    @Override
    protected void tick(Minecraft minecraft, LocalPlayer player)
    {
        if (age == 0)
            aimFirstWave(player);

        live.removeIf(particle -> !particle.isAlive());
        stains.removeIf(stain -> !stain.isAlive());

        float rain = rainLevel(age);
        if (rain > 0)
            spawnDrops(minecraft, rain);
        tickPatter(minecraft, rain);
    }

    private void aimFirstWave(LocalPlayer player)
    {
        var look = player.getLookAngle();
        firstWaveAzimuth = (float) Mth.atan2(look.z, look.x) + (random.nextFloat() - 0.5F) * 2 * FIRST_WAVE_OFF_AIM;
        firstWaveHeight = Mth.clamp((float) look.y + (random.nextFloat() - 0.5F) * FIRST_WAVE_OFF_AIM, FIRST_WAVE_LOWEST, FIRST_WAVE_HIGHEST);
    }

    private void spawnDrops(Minecraft minecraft, float rain)
    {
        float share = switch (minecraft.options.particles().get())
        {
            case ALL -> 1;
            case DECREASED -> DECREASED_SHARE;
            case MINIMAL -> MINIMAL_SHARE;
        };
        float gust = 1 + GUST_DEPTH * Mth.sin(age * GUST_RATE + gustPhase);
        dropsDue += PEAK_DROPS_PER_TICK * rain * gust * share;

        var level = minecraft.level;
        var camera = minecraft.gameRenderer.mainCamera().position();
        var atlas = minecraft.getAtlasManager().getAtlasOrThrow(AtlasIds.PARTICLES);

        int tried = 0;
        int open = 0;
        while (dropsDue >= 1 && live.size() < MAX_LIVE)
        {
            dropsDue--;
            tried++;
            if (spawnDrop(minecraft, level, camera, atlas))
                open++;
        }
        dropsDue %= 1;
        if (tried > 0)
            openSky += (open / (float) tried - openSky) * PATTER_FOLLOW;
    }

    private boolean spawnDrop(Minecraft minecraft, ClientLevel level, Vec3 camera, TextureAtlas atlas)
    {
        float angle = random.nextFloat() * Mth.TWO_PI;
        float distance = Mth.lerp((float) Math.pow(random.nextFloat(), SPAWN_SPREAD), SPAWN_NEAREST, SPAWN_RADIUS);
        double x = camera.x + Mth.cos(angle) * distance;
        double z = camera.z + Mth.sin(angle) * distance;

        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
        if (surface > camera.y + SURFACE_MAX_ABOVE)
            return false;

        double y = Math.max(surface, camera.y) + SPAWN_HEIGHT_MIN + random.nextFloat() * SPAWN_HEIGHT_EXTRA;
        double xd = slantX + (random.nextFloat() - 0.5F) * SLANT_JITTER;
        double zd = slantZ + (random.nextFloat() - 0.5F) * SLANT_JITTER;
        double yd = -Mth.lerp(random.nextFloat(), FALL_SPEED_MIN, FALL_SPEED_MAX);
        float size = Mth.lerp((float) Math.pow(random.nextFloat(), DROP_SIZE_SKEW), DROP_SIZE_MIN, DROP_SIZE_MAX);

        TextureAtlasSprite sprite = atlas.getSprite(DROP_SPRITES[random.nextInt(DROP_SPRITES.length)]);
        var drop = new OmenDropParticle(level, x, y, z, xd, yd, zd, size, sprite, this);
        minecraft.particleEngine.add(drop);
        live.add(drop);
        return true;
    }

    void splash(ClientLevel level, double x, double y, double z)
    {
        var minecraft = Minecraft.getInstance();
        var sprite = minecraft.getAtlasManager().getAtlasOrThrow(AtlasIds.PARTICLES).getSprite(SPLASH_SPRITES[random.nextInt(SPLASH_SPRITES.length)]);
        var splash = new OmenSplashParticle(level, x, y, z, sprite);
        minecraft.particleEngine.add(splash);
        live.add(splash);
    }

    void land(ClientLevel level, double x, double surface, double z)
    {
        splash(level, x, surface, z);

        var minecraft = Minecraft.getInstance();
        int lifetime = Math.min(STAIN_LIFE_MIN + random.nextInt(STAIN_LIFE_EXTRA), duration - age - STAIN_END_MARGIN);
        if (stains.size() >= MAX_STAINS || lifetime < STAIN_LIFE_LEAST)
            return;

        var camera = minecraft.gameRenderer.mainCamera().position();
        float distance = (float) Math.hypot(x - camera.x, z - camera.z);
        if (random.nextFloat() >= Mth.clampedMap(distance, STAIN_NEAR, STAIN_FAR, STAIN_CHANCE_NEAR, STAIN_CHANCE_FAR))
            return;

        int blockX = Mth.floor(x);
        int blockZ = Mth.floor(z);
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, blockX, blockZ);
        if (!level.getFluidState(new BlockPos(blockX, top - 1, blockZ)).isEmpty())
            return;

        float size = Mth.lerp((float) Math.pow(random.nextFloat(), STAIN_SIZE_SKEW), STAIN_SIZE_MIN, STAIN_SIZE_MAX);
        // Pulled back from an edge past which the ground is at another height, so that it never hangs in the air or runs into a wall
        double stainX = x;
        if (x - size < blockX && level.getHeight(Heightmap.Types.MOTION_BLOCKING, blockX - 1, blockZ) != top)
            stainX = blockX + size;
        else if (x + size > blockX + 1 && level.getHeight(Heightmap.Types.MOTION_BLOCKING, blockX + 1, blockZ) != top)
            stainX = blockX + 1 - size;
        double stainZ = z;
        if (z - size < blockZ && level.getHeight(Heightmap.Types.MOTION_BLOCKING, blockX, blockZ - 1) != top)
            stainZ = blockZ + size;
        else if (z + size > blockZ + 1 && level.getHeight(Heightmap.Types.MOTION_BLOCKING, blockX, blockZ + 1) != top)
            stainZ = blockZ + 1 - size;

        var sprite = minecraft.getAtlasManager().getAtlasOrThrow(AtlasIds.PARTICLES).getSprite(STAIN_SPRITES[random.nextInt(STAIN_SPRITES.length)]);
        var stain = new OmenStainParticle(level, stainX, surface, stainZ, size, random.nextInt(4), lifetime, sprite);
        minecraft.particleEngine.add(stain);
        stains.add(stain);
    }

    private void tickPatter(Minecraft minecraft, float rain)
    {
        if (rain <= 0)
        {
            stopPatter(minecraft);
            return;
        }

        if (patter == null)
        {
            patter = new OmenPatterSound(random, PATTER_PITCH);
            minecraft.getSoundManager().play(patter);
        }
        patter.setLoudness(PATTER_VOLUME * rain * openSky);
    }

    private void stopPatter(Minecraft minecraft)
    {
        if (patter == null)
            return;
        patter.end();
        minecraft.getSoundManager().stop(patter);
        patter = null;
    }

    @Override
    protected void onEnd()
    {
        stopPatter(Minecraft.getInstance());
        live.forEach(SingleQuadParticle::remove);
        live.clear();
        stains.forEach(SingleQuadParticle::remove);
        stains.clear();
    }

    /**
     * Where the drops in the air are now, for tests.
     */
    public List<Vec3> dropPositions()
    {
        return live.stream().filter(OmenDropParticle.class::isInstance).map(particle -> ((OmenDropParticle) particle).position()).toList();
    }

    /**
     * For tests.
     */
    public int liveParticles()
    {
        return live.size();
    }

    /**
     * For tests.
     */
    public int liveStains()
    {
        return stains.size();
    }

    /**
     * For tests.
     */
    public @Nullable SoundInstance patter()
    {
        return patter;
    }

    @Override
    public void prepareSky(SkyRenderState state, SkyFrame frame)
    {
        StarField.prepare();
        OmenDome.prepare();

        float time = age + frame.partialTicks();
        float turn = ramp(time, TURN_START, TURN_END) + ramp(time, duration - RETURN_FROM, duration - RETURN_TO);
        float wobble = Mth.smoothstep(ramp(time, WOBBLE_START, WOBBLE_END));
        ripple = Mth.smoothstep(ramp(time, RIPPLE_START, RIPPLE_FULL))
                * (1 - Mth.smoothstep(ramp(time, duration - RIPPLE_FADE_FROM, duration - RIPPLE_FADE_TO)))
                * (1 - RIPPLE_UNDER_RAIN * rainLevel(time));

        var stateA = new Vector4f(turn, wobble, ripple, state.moonAngle);
        var stateB = new Vector4f(skySeed, Math.max(time - RIPPLE_START, 0) * SECONDS_PER_TICK, firstWaveAzimuth, firstWaveHeight);
        uniform = CurseSkyUniform.write(frame, time * SECONDS_PER_TICK, stateA, stateB);
    }

    @Override
    public void drawSky(RenderPass renderPass, SkyFrame frame)
    {
        if (uniform == null)
            return;
        if (ripple > 0)
            OmenDome.draw(renderPass, RIPPLE_PIPELINE, uniform);
        StarField.draw(renderPass, STARS_PIPELINE, uniform);
    }
}
