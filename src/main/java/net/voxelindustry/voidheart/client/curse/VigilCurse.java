package net.voxelindustry.voidheart.client.curse;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.data.AtlasIds;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.client.model.VigilFigureModel;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Seven hooded pilgrims hang in the air in an arc before the player, utterly still, watching, black blood running off them all the while and caves sounding
 * around the player. They raise their left hands at the player as one, speak in their chat alone, and the dark under their hoods lights up. Then they are
 * gone. They only ever exist in this class: no entity, nothing a crosshair can find, and the player walks right through them.
 */
public final class VigilCurse extends ClientCurse
{
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(MODID, "textures/entity/curse_vigil.png");
    private static final Identifier GLOW    = Identifier.fromNamespaceAndPath(MODID, "textures/entity/curse_vigil_glow.png");
    private static final Identifier MASK    = Identifier.fromNamespaceAndPath(MODID, "textures/entity/curse_vigil_mask.png");

    private static final RenderType BODY_TYPE = RenderTypes.entityCutoutDissolve(TEXTURE, MASK);
    private static final RenderType GLOW_TYPE = RenderTypes.eyes(GLOW);
    private static final RenderType SHADE_TYPE = RenderTypes.entityShadow(Identifier.fromNamespaceAndPath(MODID, "textures/entity/curse_vigil_shade.png"));

    private static final String MESSAGE_KEY   = "curse.voidheart.vigil.message";
    private static final int    MESSAGE_COLOR = 0x4FC86A;

    private static final int   COUNT         = 7;
    private static final int   LEADER_INDEX  = 3;
    private static final float LEADER_SCALE  = 1.3F;
    // Taller than anyone should be: 2.4 blocks from hem to the point of the hood
    private static final float FIGURE_SCALE  = 0.86F;
    private static final float FIGURE_WIDTH  = 0.7F;
    private static final float FIGURE_HEIGHT = 2.78F;
    private static final float MODEL_UNITS   = 16;

    // Placement
    // Close enough for all seven to fit on the screen at the default field of view, arms included
    private static final float ARC_STEP          = 14;
    private static final float RADIUS            = 7.5F;
    private static final float RADIUS_JITTER     = 0.4F;
    private static final float RADIUS_STEP       = 0.5F;
    private static final float RADIUS_MIN        = 2.2F;
    private static final float RADIUS_LEADER_MIN = 1.5F;
    private static final float SPACING           = 0.9F;
    private static final float HOVER_MIN         = 1;
    private static final float HOVER_MAX         = 1.3F;
    private static final float HOVER_CRAMPED     = 0.02F;
    // Up close they float lower, or their hoods would be above the player's view
    private static final float HOVER_CLOSE_MIN   = 0.25F;
    private static final float SCAN_FROM_ABOVE   = 2.5F;
    private static final int   SCAN_BLOCKS       = 8;
    private static final float DROP_LIMIT        = 3;
    private static final float SIGHT_AT_CHEST    = 0.62F;
    private static final float SIGHT_AT_HOOD     = 0.88F;
    private static final float CRAMPED_HEIGHT    = 1.2F;
    // The leader first, so that the middle of the arc is never lost to the others
    private static final int[] PLACING_ORDER     = {3, 2, 4, 1, 5, 0, 6};

    // Timeline, in ticks
    private static final int RUNE_FADE_TICKS = 7;
    private static final int BODY_FADE_START = 4;
    private static final int BODY_FADE_TICKS = 14;
    private static final int POINT_START     = 70;
    private static final int POINT_TICKS     = 20;
    private static final int POINT_STAGGER   = 2;
    private static final int MESSAGE_TICK    = 91;
    private static final int HOOD_START      = 100;
    private static final int HOOD_TICKS      = 25;
    private static final int HOOD_STAGGER    = 3;
    private static final int HOLD_SOUND_TICK = 104;
    private static final int VANISH_START    = 200;
    private static final int VANISH_TICKS    = 4;
    private static final int GLOW_OUT_TICKS  = 2;

    // Rune glow, its slow uneven breathing and the odd dip
    private static final float RUNE_LEVEL      = 0.82F;
    private static final float RUNE_PULSE      = 0.18F;
    private static final float RUNE_PULSE_SLOW = 0.07F;
    private static final float RUNE_PULSE_FAST = 0.171F;
    private static final float RUNE_DIP_SPEED  = 0.043F;
    private static final float RUNE_DIP        = 0.3F;

    private static final float BATON_LEVEL      = 0.85F;
    private static final float BATON_PULSE      = 0.15F;
    private static final float BATON_PULSE_RATE = 0.09F;

    private static final float HOOD_FLICKER      = 0.16F;
    private static final float HOOD_FLICKER_FAST = 0.83F;
    private static final float HOOD_FLICKER_SLOW = 0.29F;

    // Hover, turning and tracking. They are almost still
    private static final float BOB_SPEED       = 0.045F;
    private static final float BOB_HEIGHT      = 0.035F;
    private static final float BOB_RIPPLE      = 0.008F;
    private static final float BOB_RIPPLE_RATE = 0.117F;
    private static final float TURN_RATE       = 0.12F;
    private static final float TURN_MAX        = 4;
    private static final float HEAD_RATE       = 0.2F;
    private static final float HEAD_YAW_MAX    = 0.9F;
    private static final float HEAD_TILT_MAX   = 0.08F;
    private static final float ARM_SPLAY_MAX   = 0.05F;

    // World light, though never so dark that the silhouette is lost
    private static final int BLOCK_LIGHT_FLOOR = 5;

    // Faint shade on the floor below, what shows that they float
    private static final float SHADE_RADIUS = 0.8F;
    private static final float SHADE_ALPHA  = 0.5F;
    private static final float SHADE_LIFT   = 0.02F;

    // Sounds
    private static final float DRONE_VOLUME  = 0.35F;
    private static final float DRONE_PITCH   = 0.5F;
    private static final float HOLD_VOLUME   = 0.55F;
    private static final float HOLD_PITCH    = 0.5F;
    private static final float VANISH_VOLUME = 0.8F;
    private static final float VANISH_PITCH  = 0.5F;

    // Thin smoke left at each spot, gone almost as soon as they are
    private static final int   BURST_SMOKE         = 6;
    private static final float BURST_SIZE          = 0.65F;
    private static final float BURST_RADIUS        = 0.3F;
    private static final float BURST_SPEED         = 0.05F;
    private static final int   BURST_LIFETIME_MIN  = 4;
    private static final int   BURST_LIFETIME_MORE = 4;

    // Minecraft's own cave noises from somewhere around the player. The offset only says which side they come from, as they don't fade with distance
    private static final int   CAVE_FIRST_TICK   = 6;
    private static final int   CAVE_GAP_MIN      = 48;
    private static final int   CAVE_GAP_EXTRA    = 24;
    private static final int   CAVE_LAST_MARGIN  = 30;
    private static final float CAVE_VOLUME       = 1;
    private static final float CAVE_PITCH        = 1;
    private static final float CAVE_OFFSET       = 8;

    // Black blood, from when the bodies have mostly formed until they vanish
    private static final int   BLOOD_START_TICK  = BODY_FADE_START + BODY_FADE_TICKS / 2;
    private static final float BLOOD_PER_TICK    = 0.6F;
    private static final float BLOOD_SHARE_HOOD  = 0.2F;
    private static final float BLOOD_SHARE_HAND  = 0.2F;
    // It keeps running off the same few strips of each robe, so that it reads as dripping rather than as a sprinkle
    private static final int   BLOOD_STRIPS      = 5;
    private static final float BLOOD_SIZE_MIN    = 0.1F;
    private static final float BLOOD_SIZE_MAX    = 0.16F;
    private static final float BLOOD_CLEARANCE   = 0.05F;
    private static final int   MAX_STAINS        = 150;
    private static final float STAIN_CHANCE      = 0.2F;
    private static final float STAIN_SIZE_MIN    = 0.1F;
    private static final float STAIN_SIZE_MAX    = 0.26F;
    // Longer than the curse, so that none has dried by the time they all go at once
    private static final int   STAIN_LIFETIME    = 400;
    private static final float STAIN_FLOOR_MATCH = 0.001F;

    private static final Identifier[] DROP_SPRITES = {
            Identifier.fromNamespaceAndPath(MODID, "curse_vigil_drop0"),
            Identifier.fromNamespaceAndPath(MODID, "curse_vigil_drop1"),
            Identifier.fromNamespaceAndPath(MODID, "curse_vigil_drop2")
    };
    private static final Identifier[] SPLASH_SPRITES = {
            Identifier.fromNamespaceAndPath(MODID, "curse_vigil_splash0"),
            Identifier.fromNamespaceAndPath(MODID, "curse_vigil_splash1")
    };
    private static final Identifier[] STAIN_SPRITES = {
            Identifier.fromNamespaceAndPath(MODID, "curse_vigil_stain0"),
            Identifier.fromNamespaceAndPath(MODID, "curse_vigil_stain1"),
            Identifier.fromNamespaceAndPath(MODID, "curse_vigil_stain2"),
            Identifier.fromNamespaceAndPath(MODID, "curse_vigil_stain3")
    };

    private final List<Figure>   figures = new ArrayList<>(COUNT);
    private final List<Particle> burst   = new ArrayList<>();
    private final List<SingleQuadParticle> blood  = new ArrayList<>();
    private final List<OmenStainParticle>  stains = new ArrayList<>();
    private final List<SoundInstance>      caves  = new ArrayList<>();
    private @Nullable VigilFigureModel model;
    private @Nullable SoundInstance    drone;
    private @Nullable SoundInstance    hold;

    private int drawnLastFrame;
    private int shadesLastFrame;
    private int nextCaveTick = CAVE_FIRST_TICK;

    public VigilCurse(long seed, int duration)
    {
        super(seed, duration);
    }

    /**
     * The figures, for tests. Empty until the curse has ticked once.
     */
    public List<Figure> figures()
    {
        return Collections.unmodifiableList(figures);
    }

    /**
     * How many figures were submitted last frame, for tests.
     */
    public int drawnLastFrame()
    {
        return drawnLastFrame;
    }

    /**
     * How many shades were submitted on the floor last frame, for tests.
     */
    public int shadesLastFrame()
    {
        return shadesLastFrame;
    }

    /**
     * How much of the smoke left by the vanishing is still around, for tests.
     */
    public long burstAlive()
    {
        return burst.stream().filter(Particle::isAlive).count();
    }

    /**
     * Where each drop still hanging or falling is, for tests.
     */
    public List<Vec3> dropPositions()
    {
        return blood.stream().filter(Particle::isAlive).filter(VigilDropParticle.class::isInstance).map(drop -> ((VigilDropParticle) drop).position()).toList();
    }

    /**
     * How many stains lie on the floor, for tests.
     */
    public long stainsAlive()
    {
        return stains.stream().filter(Particle::isAlive).count();
    }

    @Override
    protected void tick(Minecraft minecraft, LocalPlayer player)
    {
        if (model == null)
        {
            model = new VigilFigureModel(minecraft.getEntityModels().bakeLayer(VigilFigureModel.LAYER));
            place(player);
            drone = playInsideHead(minecraft, SoundEvents.BEACON_AMBIENT, DRONE_VOLUME, DRONE_PITCH, false);
        }

        var level = minecraft.level;
        var eye = player.getEyePosition();
        for (var figure : figures)
            figure.tick(level, eye);

        blood.removeIf(particle -> !particle.isAlive());
        if (age >= BLOOD_START_TICK && age < VANISH_START)
        {
            for (var figure : figures)
                figure.bleed(minecraft, level);
        }
        if (age == VANISH_START + VANISH_TICKS)
            clearBlood();

        if (age >= nextCaveTick && age < VANISH_START - CAVE_LAST_MARGIN)
        {
            float around = random.nextFloat() * Mth.TWO_PI;
            caves.add(playAround(minecraft, SoundEvents.AMBIENT_CAVE.value(), CAVE_VOLUME, CAVE_PITCH, false, Mth.cos(around) * CAVE_OFFSET, Mth.sin(around) * CAVE_OFFSET));
            nextCaveTick = age + CAVE_GAP_MIN + random.nextInt(CAVE_GAP_EXTRA);
        }

        if (age == MESSAGE_TICK)
            minecraft.gui.hud.getChat().addClientSystemMessage(Component.translatable(MESSAGE_KEY).withColor(MESSAGE_COLOR));
        if (age == HOLD_SOUND_TICK)
            hold = playInsideHead(minecraft, SoundEvents.BEACON_AMBIENT, HOLD_VOLUME, HOLD_PITCH, true);
        if (age == VANISH_START)
        {
            stopSounds();
            playInsideHead(minecraft, SoundEvents.BEACON_DEACTIVATE, VANISH_VOLUME, VANISH_PITCH, false);
            for (var figure : figures)
                figure.burst(minecraft);
        }
    }

    @Override
    protected void onEnd()
    {
        stopSounds();
        burst.clear();
        clearBlood();
    }

    private void clearBlood()
    {
        blood.forEach(SingleQuadParticle::remove);
        stains.forEach(SingleQuadParticle::remove);
        blood.clear();
        stains.clear();
    }

    private void stopSounds()
    {
        var sounds = Minecraft.getInstance().getSoundManager();
        if (drone != null)
            sounds.stop(drone);
        if (hold != null)
            sounds.stop(hold);
        caves.forEach(sounds::stop);
        drone = null;
        hold = null;
        caves.clear();
    }

    private SoundInstance playInsideHead(Minecraft minecraft, SoundEvent sound, float volume, float pitch, boolean looping)
    {
        return playAround(minecraft, sound, volume, pitch, looping, 0, 0);
    }

    private SoundInstance playAround(Minecraft minecraft, SoundEvent sound, float volume, float pitch, boolean looping, float x, float z)
    {
        var instance = new SimpleSoundInstance(sound.location(), SoundSource.HOSTILE, volume, pitch, random,
                looping, 0, SoundInstance.Attenuation.NONE, x, 0, z, true);
        minecraft.getSoundManager().play(instance);
        return instance;
    }

    private void place(LocalPlayer player)
    {
        var level = player.level();
        var eye = player.getEyePosition();
        float lookYaw = player.getYRot();

        var runes = new ArrayList<Integer>(VigilFigureModel.RUNES);
        for (int i = 0; i < VigilFigureModel.RUNES; i++)
            runes.add(i);
        for (int i = runes.size() - 1; i > 0; i--)
            Collections.swap(runes, i, random.nextInt(i + 1));

        var spots = new Spot[COUNT];
        for (int i : PLACING_ORDER)
        {
            float scale = scaleOf(i);
            float radius = i == LEADER_INDEX ? RADIUS : RADIUS + (random.nextFloat() - 0.5F) * 2 * RADIUS_JITTER;
            float hover = Mth.lerp(random.nextFloat(), HOVER_MIN, HOVER_MAX);
            float angle = (lookYaw + (i - LEADER_INDEX) * ARC_STEP) * Mth.DEG_TO_RAD;
            spots[i] = findOnRay(level, player, eye, spots, i, -Mth.sin(angle), Mth.cos(angle), radius, hover, FIGURE_HEIGHT * scale);
            if (spots[i] == null)
                spots[i] = findOnRay(level, player, eye, spots, i, -Mth.sin(angle), Mth.cos(angle), radius, hover, CRAMPED_HEIGHT);
        }
        for (int i : PLACING_ORDER)
        {
            if (spots[i] == null)
                spots[i] = fallInLine(level, player, eye, spots, i);
        }

        for (int i = 0; i < COUNT; i++)
            figures.add(new Figure(spots[i], scaleOf(i), i == LEADER_INDEX, runes.get(i), random.nextFloat() * Mth.TWO_PI, random.nextFloat() * Mth.TWO_PI,
                    (random.nextFloat() - 0.5F) * 2 * HEAD_TILT_MAX, (random.nextFloat() - 0.5F) * 2 * ARM_SPLAY_MAX,
                    random.nextInt(POINT_STAGGER + 1), random.nextInt(HOOD_STAGGER + 1)));
    }

    private static float scaleOf(int index)
    {
        return index == LEADER_INDEX ? FIGURE_SCALE * LEADER_SCALE : FIGURE_SCALE;
    }

    /**
     * The farthest spot along a ray from the player where the figure stands in the open, in sight of the player and clear of the others.
     */
    private static @Nullable Spot findOnRay(Level level, LocalPlayer player, Vec3 eye, Spot[] spots, int index, float dirX, float dirZ, float radius, float hover, float neededHeight)
    {
        float scale = scaleOf(index);
        float nearest = index == LEADER_INDEX ? RADIUS_LEADER_MIN : RADIUS_MIN;
        var cursor = new BlockPos.MutableBlockPos();
        for (float distance = radius; distance >= nearest; distance -= RADIUS_STEP)
        {
            double x = player.getX() + dirX * distance;
            double z = player.getZ() + dirZ * distance;
            if (!hasRoom(spots, index, x, z))
                continue;
            float closeness = Mth.clamp(distance / (RADIUS - RADIUS_JITTER), HOVER_CLOSE_MIN, 1);
            var spot = standAt(level, player, eye, cursor, x, z, hover * closeness, scale, neededHeight);
            if (spot != null)
                return spot;
        }
        return null;
    }

    private static boolean hasRoom(Spot[] spots, int index, double x, double z)
    {
        for (int other = 0; other < spots.length; other++)
        {
            if (other != index && spots[other] != null
                    && Math.hypot(spots[other].x() - x, spots[other].z() - z) < FIGURE_WIDTH * SPACING * (scaleOf(index) + scaleOf(other)) / 2)
                return false;
        }
        return true;
    }

    private static @Nullable Spot standAt(Level level, LocalPlayer player, Vec3 eye, BlockPos.MutableBlockPos cursor, double x, double z, float hover, float scale, float neededHeight)
    {
        double floor = floorBelow(level, cursor, x, player.getY() + SCAN_FROM_ABOVE, z);
        // Over a drop they simply hang level with the player
        if (player.getY() - floor > DROP_LIMIT)
            floor = Double.NaN;
        double base = Double.isNaN(floor) ? player.getY() : floor;
        float height = FIGURE_HEIGHT * scale;

        for (float tryHover : new float[] {hover, HOVER_CRAMPED})
        {
            double feet = base + tryHover;
            if (isOpen(level, x, feet, z, FIGURE_WIDTH * scale, neededHeight)
                    && inSight(level, player, eye, x, feet + height * SIGHT_AT_CHEST, z)
                    && (neededHeight < height || inSight(level, player, eye, x, feet + height * SIGHT_AT_HOOD, z)))
                return new Spot(x, feet, z, floor);
        }
        return null;
    }

    /**
     * Down through whatever is at the top of the scan, into the first open space, and on to what closes it from below.
     */
    private static double floorBelow(Level level, BlockPos.MutableBlockPos cursor, double x, double fromY, double z)
    {
        cursor.set(Mth.floor(x), Mth.floor(fromY), Mth.floor(z));
        boolean open = false;
        for (int i = 0; i < SCAN_BLOCKS; i++)
        {
            var shape = level.getBlockState(cursor).getCollisionShape(level, cursor);
            if (shape.isEmpty())
                open = true;
            else if (open)
                return cursor.getY() + shape.max(Direction.Axis.Y);
            cursor.move(Direction.DOWN);
        }
        return Double.NaN;
    }

    private static boolean isOpen(Level level, double x, double feet, double z, float width, float height)
    {
        return level.noBlockCollision(null, new AABB(x - width / 2, feet, z - width / 2, x + width / 2, feet + height, z + width / 2));
    }

    private static boolean inSight(Level level, LocalPlayer player, Vec3 eye, double x, double y, double z)
    {
        var hit = level.clip(new ClipContext(eye, new Vec3(x, y, z), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.MISS;
    }

    /**
     * A figure whose whole ray is walled off falls in line along the rays of the others, farthest first, the way they would stand in a corridor.
     * With no room left at all they would rather share a spot than stand in a wall.
     */
    private static Spot fallInLine(Level level, LocalPlayer player, Vec3 eye, Spot[] spots, int index)
    {
        var cursor = new BlockPos.MutableBlockPos();
        for (float distance = RADIUS; distance >= RADIUS_LEADER_MIN; distance -= RADIUS_STEP)
        {
            for (int other : PLACING_ORDER)
            {
                if (spots[other] == null)
                    continue;

                double dirX = spots[other].x() - player.getX();
                double dirZ = spots[other].z() - player.getZ();
                double length = Math.hypot(dirX, dirZ);
                if (length < 1.0E-3)
                    continue;

                double x = player.getX() + dirX / length * distance;
                double z = player.getZ() + dirZ / length * distance;
                if (!hasRoom(spots, index, x, z))
                    continue;
                var spot = standAt(level, player, eye, cursor, x, z, HOVER_CRAMPED, scaleOf(index), CRAMPED_HEIGHT);
                if (spot != null)
                    return spot;
            }
        }

        for (int other : PLACING_ORDER)
        {
            if (spots[other] != null)
                return spots[other];
        }
        // Walled in on every side. The only certainly open place left is the one the player stands in
        return new Spot(player.getX(), player.getY() + HOVER_CRAMPED, player.getZ(), Double.NaN);
    }

    @Override
    public void collectSubmits(LevelRenderContext context)
    {
        drawnLastFrame = 0;
        shadesLastFrame = 0;
        if (model == null)
            return;

        var camera = context.levelState().cameraRenderState.pos;
        var poseStack = context.poseStack();
        var collector = context.submitNodeCollector();
        float partialTicks = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float time = age + partialTicks;

        for (var figure : figures)
        {
            if (figure.submit(model, poseStack, collector, camera, time, partialTicks))
                drawnLastFrame++;
        }
    }

    private void drip(Minecraft minecraft, ClientLevel level, Vec3 from)
    {
        // A hand can reach into a wall the figure itself stays out of
        if (!isOpen(level, from.x, from.y - BLOOD_CLEARANCE, from.z, BLOOD_CLEARANCE, BLOOD_CLEARANCE))
            return;

        var sprite = minecraft.getAtlasManager().getAtlasOrThrow(AtlasIds.PARTICLES).getSprite(DROP_SPRITES[random.nextInt(DROP_SPRITES.length)]);
        double surface = landingBelow(level, new BlockPos.MutableBlockPos(), from.x, from.y, from.z);
        var drop = new VigilDropParticle(level, from.x, from.y, from.z, surface, Mth.lerp(random.nextFloat(), BLOOD_SIZE_MIN, BLOOD_SIZE_MAX), sprite, this);
        minecraft.particleEngine.add(drop);
        blood.add(drop);
    }

    /**
     * The top of whatever a drop falling from there lands on, NaN when that is too far down to be worth waiting for.
     */
    private static double landingBelow(Level level, BlockPos.MutableBlockPos cursor, double x, double fromY, double z)
    {
        cursor.set(Mth.floor(x), Mth.floor(fromY), Mth.floor(z));
        double insideX = x - cursor.getX();
        double insideZ = z - cursor.getZ();
        for (int i = 0; i < SCAN_BLOCKS; i++)
        {
            var shape = level.getBlockState(cursor).getCollisionShape(level, cursor);
            double blockTop = shape.isEmpty() ? 0 : shape.max(Direction.Axis.Y, insideX, insideZ);
            double top = Math.max(blockTop, level.getFluidState(cursor).getHeight(level, cursor));
            if (top > 0 && cursor.getY() + top <= fromY)
                return cursor.getY() + top;
            cursor.move(Direction.DOWN);
        }
        return Double.NaN;
    }

    /**
     * A small splash, and now and then a stain that stays until they vanish.
     */
    void land(ClientLevel level, double x, double surface, double z)
    {
        var minecraft = Minecraft.getInstance();
        var atlas = minecraft.getAtlasManager().getAtlasOrThrow(AtlasIds.PARTICLES);
        var splash = new OmenSplashParticle(level, x, surface, z, atlas.getSprite(SPLASH_SPRITES[random.nextInt(SPLASH_SPRITES.length)]));
        minecraft.particleEngine.add(splash);
        blood.add(splash);

        float size = Mth.lerp(random.nextFloat(), STAIN_SIZE_MIN, STAIN_SIZE_MAX);
        if (stains.size() >= MAX_STAINS || random.nextFloat() >= STAIN_CHANCE || !liesFlat(level, x, surface, z, size))
            return;

        var stain = new OmenStainParticle(level, x, surface, z, size, random.nextInt(4), STAIN_LIFETIME, atlas.getSprite(STAIN_SPRITES[random.nextInt(STAIN_SPRITES.length)]));
        minecraft.particleEngine.add(stain);
        stains.add(stain);
    }

    /**
     * Dry ground at the same height under all of it, so that a stain never hangs over an edge, runs into a wall or lies on water.
     */
    private static boolean liesFlat(Level level, double x, double surface, double z, float size)
    {
        var cursor = new BlockPos.MutableBlockPos();
        cursor.set(Mth.floor(x), Mth.floor(surface - STAIN_FLOOR_MATCH), Mth.floor(z));
        if (!level.getFluidState(cursor).isEmpty())
            return false;

        for (int sideX = -1; sideX <= 1; sideX += 2)
        {
            for (int sideZ = -1; sideZ <= 1; sideZ += 2)
            {
                double landing = landingBelow(level, cursor, x + sideX * size, surface + 2 * STAIN_FLOOR_MATCH, z + sideZ * size);
                if (Double.isNaN(landing) || Math.abs(landing - surface) > STAIN_FLOOR_MATCH)
                    return false;
            }
        }
        return true;
    }

    private static float vanishing(float time, float over)
    {
        return 1 - Mth.clamp((time - VANISH_START) / over, 0, 1);
    }

    private static float smooth(float t)
    {
        float clamped = Mth.clamp(t, 0, 1);
        return clamped * clamped * (3 - 2 * clamped);
    }

    /**
     * Where a figure hangs, and the floor found below it, NaN over a drop.
     */
    private record Spot(double x, double y, double z, double floor)
    {
    }

    /**
     * How the pointing finger leans off the exact line to the player's eye.
     *
     * @param degrees       how far off
     * @param inward        how much more it heads for the middle of the body than the line does, in the unit direction
     * @param acrossTheLine how far it turns past the line, positive when that heads for the player rather than away
     */
    public record FingerLean(float degrees, float inward, float acrossTheLine)
    {
    }

    /**
     * One figure. Its spot never changes once chosen, but it keeps turning to face the player.
     */
    public final class Figure
    {
        private final double x;
        private final double y;
        private final double z;
        private final double floor;
        private final float  scale;
        private final boolean leader;
        private final float  phase;
        private final float  phase2;
        private final int    pointDelay;
        private final int    hoodDelay;

        private final VigilFigureModel.Pose  pose = new VigilFigureModel.Pose();
        private final VigilFigureModel.State bodyState;
        private final VigilFigureModel.State runeState;
        private final VigilFigureModel.State hoodState;
        private final VigilFigureModel.State batonState;
        private final int[] bloodStrips = new int[BLOOD_STRIPS];

        private float   dripsDue;
        private boolean settled;
        private float   yaw;
        private float   yawO;
        private float   headYaw;
        private float   headYawO;
        private float   headPitch;
        private float   headPitchO;
        private float   targetX;
        private float   targetXO;
        private float   targetY;
        private float   targetYO;
        private float   targetZ;
        private float   targetZO;
        private int     light;

        private Figure(Spot spot, float scale, boolean leader, int rune, float phase, float phase2, float tilt, float splay, int pointDelay, int hoodDelay)
        {
            x = spot.x();
            y = spot.y();
            z = spot.z();
            floor = spot.floor();
            this.scale = scale;
            this.leader = leader;
            this.phase = phase;
            this.phase2 = phase2;
            this.pointDelay = pointDelay;
            this.hoodDelay = hoodDelay;
            pose.leader = leader;
            pose.rune = rune;
            pose.phase = phase;
            pose.headTilt = tilt;
            pose.splay = splay;
            bodyState = new VigilFigureModel.State(pose, VigilFigureModel.Pass.BODY);
            runeState = new VigilFigureModel.State(pose, VigilFigureModel.Pass.RUNE);
            hoodState = new VigilFigureModel.State(pose, VigilFigureModel.Pass.HOOD);
            batonState = new VigilFigureModel.State(pose, VigilFigureModel.Pass.BATON);
            for (int i = 0; i < BLOOD_STRIPS; i++)
                bloodStrips[i] = random.nextInt(VigilFigureModel.stripCount());
        }

        public double x()
        {
            return x;
        }

        public double y()
        {
            return y;
        }

        public double z()
        {
            return z;
        }

        /**
         * The floor found below it, NaN when it hangs over a drop.
         */
        public double floor()
        {
            return floor;
        }

        public float scale()
        {
            return scale;
        }

        public boolean leader()
        {
            return leader;
        }

        public int rune()
        {
            return pose.rune;
        }

        /**
         * Where its body faces, as the yaw of an entity.
         */
        public float yaw()
        {
            return yaw;
        }

        /**
         * Of the pose last drawn.
         */
        public FingerLean fingerLean()
        {
            var base = new Vector3f();
            var direction = new Vector3f();
            model.fingerLine(bodyState, base, direction);
            var toEye = new Vector3f(pose.targetX, pose.targetY, pose.targetZ).sub(base).normalize();
            float across = (direction.x - toEye.x) * toEye.x + (direction.y - toEye.y) * toEye.y;
            return new FingerLean((float) Math.toDegrees(direction.angle(toEye)), toEye.x - direction.x, across);
        }

        /**
         * Where the pointing finger of the pose last drawn starts and ends in the world, first the start then the end.
         */
        public Vec3[] fingerEnds()
        {
            var base = new Vector3f();
            var direction = new Vector3f();
            model.fingerLine(bodyState, base, direction);
            var tip = new Vector3f(direction).mul(VigilFigureModel.FINGER_LENGTH).add(base);
            return new Vec3[] {toWorld(base), toWorld(tip)};
        }

        /**
         * The inverse of what {@link #tick} does to the position of the player's eye.
         */
        private Vec3 toWorld(Vector3f inModel)
        {
            float radians = yaw * Mth.DEG_TO_RAD;
            float cos = Mth.cos(radians);
            float sin = Mth.sin(radians);
            float blocks = scale / MODEL_UNITS;
            return new Vec3(
                    x + (inModel.x * cos + inModel.z * sin) * blocks,
                    y + (VigilFigureModel.HEM_Y - inModel.y) * blocks,
                    z + (inModel.x * sin - inModel.z * cos) * blocks);
        }

        /**
         * The space its body takes.
         */
        public AABB bounds()
        {
            float width = FIGURE_WIDTH * scale;
            return new AABB(x - width / 2, y, z - width / 2, x + width / 2, y + FIGURE_HEIGHT * scale, z + width / 2);
        }

        private void tick(ClientLevel level, Vec3 eye)
        {
            yawO = yaw;
            headYawO = headYaw;
            headPitchO = headPitch;
            targetXO = targetX;
            targetYO = targetY;
            targetZO = targetZ;

            double dx = eye.x - x;
            double dy = eye.y - y;
            double dz = eye.z - z;

            float toPlayer = (float) Math.toDegrees(Math.atan2(-dx, dz));
            if (settled)
                yaw += Mth.clamp(Mth.wrapDegrees(toPlayer - yaw) * TURN_RATE, -TURN_MAX, TURN_MAX);
            else
                yaw = toPlayer;

            // From the world into the frame of the model, the same as the pose the figure is submitted with
            float radians = yaw * Mth.DEG_TO_RAD;
            float cos = Mth.cos(radians);
            float sin = Mth.sin(radians);
            float units = MODEL_UNITS / scale;
            targetX = (float) (dx * cos + dz * sin) * units;
            targetY = VigilFigureModel.HEM_Y - (float) dy * units;
            targetZ = (float) (dx * sin - dz * cos) * units;

            float neckToTargetY = targetY - VigilFigureModel.NECK_Y;
            float flat = Mth.sqrt(targetX * targetX + targetZ * targetZ);
            float wantedYaw = Mth.clamp((float) Mth.atan2(-targetX, -targetZ), -HEAD_YAW_MAX, HEAD_YAW_MAX);
            float wantedPitch = (float) Mth.atan2(neckToTargetY, flat);
            if (settled)
            {
                headYaw += (wantedYaw - headYaw) * HEAD_RATE;
                headPitch += (wantedPitch - headPitch) * HEAD_RATE;
            }
            else
            {
                headYaw = wantedYaw;
                headPitch = wantedPitch;
                yawO = yaw;
                headYawO = headYaw;
                headPitchO = headPitch;
                targetXO = targetX;
                targetYO = targetY;
                targetZO = targetZ;
                settled = true;
            }

            int here = LightCoordsUtil.getLightCoords(level, BlockPos.containing(x, y + FIGURE_HEIGHT * scale / 2, z));
            light = LightCoordsUtil.max(here, LightCoordsUtil.pack(BLOCK_LIGHT_FLOOR, 0));
        }

        private boolean submit(VigilFigureModel model, PoseStack poseStack, SubmitNodeCollector collector, Vec3 camera, float time, float partialTicks)
        {
            float vanish = vanishing(time, VANISH_TICKS);
            if (vanish <= 0 || !settled)
                return false;

            float glowOut = vanishing(time, GLOW_OUT_TICKS);
            float bodyAlpha = smooth((time - BODY_FADE_START) / BODY_FADE_TICKS) * vanish;
            float runeAlpha = smooth(time / RUNE_FADE_TICKS) * runeLevel(time) * glowOut;
            float hoodAlpha = hoodLevel(time) * glowOut;

            pose.time = time;
            pose.headYaw = Mth.lerp(partialTicks, headYawO, headYaw);
            pose.headPitch = Mth.lerp(partialTicks, headPitchO, headPitch);
            pose.targetX = Mth.lerp(partialTicks, targetXO, targetX);
            pose.targetY = Mth.lerp(partialTicks, targetYO, targetY);
            pose.targetZ = Mth.lerp(partialTicks, targetZO, targetZ);
            pose.raise = smooth((time - POINT_START - pointDelay) / POINT_TICKS);

            float bob = Mth.sin(time * BOB_SPEED + phase) * BOB_HEIGHT + Mth.sin(time * BOB_RIPPLE_RATE + phase2) * BOB_RIPPLE;

            poseStack.pushPose();
            // Subtracted in double, so it stays steady far from the world origin
            poseStack.translate((float) (x - camera.x), (float) (y + bob - camera.y), (float) (z - camera.z));
            poseStack.rotateDegrees(Axis.YP, 180 - Mth.rotLerp(partialTicks, yawO, yaw));
            poseStack.scale(scale, scale, scale);
            poseStack.scale(-1, -1, 1);
            poseStack.translate(0, -VigilFigureModel.HEM_Y / MODEL_UNITS, 0);

            if (bodyAlpha > 0)
                collector.submitModel(model, bodyState, poseStack, BODY_TYPE, light, OverlayTexture.NO_OVERLAY, ARGB.white(bodyAlpha), null, 0);

            var glowing = collector.order(1);
            if (runeAlpha > 0)
                glowing.submitModel(model, runeState, poseStack, GLOW_TYPE, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ARGB.white(runeAlpha), null, 0);
            if (hoodAlpha > 0)
                glowing.submitModel(model, hoodState, poseStack, GLOW_TYPE, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ARGB.white(hoodAlpha), null, 0);
            if (leader && bodyAlpha > 0)
            {
                float batonAlpha = bodyAlpha * glowOut * (BATON_LEVEL + BATON_PULSE * Mth.sin(time * BATON_PULSE_RATE + phase));
                glowing.submitModel(model, batonState, poseStack, GLOW_TYPE, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ARGB.white(batonAlpha), null, 0);
            }

            poseStack.popPose();

            if (bodyAlpha > 0 && !Double.isNaN(floor))
            {
                submitShade(poseStack, collector, camera, bodyAlpha);
                shadesLastFrame++;
            }
            return true;
        }

        /**
         * One soft dark blob on the floor. The shadow render type keeps it off the surface it lies on, and it's drawn once, from above.
         */
        private void submitShade(PoseStack poseStack, SubmitNodeCollector collector, Vec3 camera, float fade)
        {
            float radius = SHADE_RADIUS * scale;
            int color = ARGB.white(SHADE_ALPHA * fade);

            poseStack.pushPose();
            poseStack.translate((float) (x - camera.x), (float) (floor + SHADE_LIFT - camera.y), (float) (z - camera.z));
            collector.submitCustomGeometry(poseStack, SHADE_TYPE, (entry, buffer) ->
            {
                shadeVertex(entry, buffer, color, -radius, -radius, 0, 0);
                shadeVertex(entry, buffer, color, -radius, radius, 0, 1);
                shadeVertex(entry, buffer, color, radius, radius, 1, 1);
                shadeVertex(entry, buffer, color, radius, -radius, 1, 0);
            });
            poseStack.popPose();
        }

        private static void shadeVertex(PoseStack.Pose entry, VertexConsumer buffer, int color, float x, float z, float u, float v)
        {
            buffer.addVertex(entry, x, 0, z)
                    .setColor(color)
                    .setUv(u, v)
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(LightCoordsUtil.FULL_BRIGHT)
                    .setNormal(entry, 0, 1, 0);
        }

        private float runeLevel(float time)
        {
            float breath = Mth.sin(time * RUNE_PULSE_SLOW + phase) * 0.6F + Mth.sin(time * RUNE_PULSE_FAST + phase2) * 0.4F;
            float dip = Mth.sin(time * RUNE_DIP_SPEED + phase * 2.3F);
            dip = dip > 0 ? dip * dip * dip * dip * dip * dip : 0;
            return Mth.clamp(RUNE_LEVEL + RUNE_PULSE * breath - RUNE_DIP * dip, 0, 1);
        }

        private float hoodLevel(float time)
        {
            float ramp = smooth((time - HOOD_START - hoodDelay) / HOOD_TICKS);
            if (ramp <= 0)
                return 0;
            float flicker = Mth.sin(time * HOOD_FLICKER_FAST + phase) * Mth.sin(time * HOOD_FLICKER_SLOW + phase2);
            return Mth.clamp(ramp * (1 - HOOD_FLICKER / 2 + flicker * HOOD_FLICKER), 0, 1);
        }

        /**
         * Off the same few strips of its robe, from under its hood and from the finger of its left hand.
         */
        private void bleed(Minecraft minecraft, ClientLevel level)
        {
            dripsDue += BLOOD_PER_TICK * scale / FIGURE_SCALE;
            while (dripsDue >= 1)
            {
                dripsDue--;
                float source = random.nextFloat();
                Vec3 from;
                if (source < BLOOD_SHARE_HOOD)
                {
                    var fold = VigilFigureModel.hoodFold(headPitch, new Vector3f());
                    fold.x += (random.nextFloat() - 0.5F) * 2 * VigilFigureModel.FOLD_HALF_WIDTH;
                    from = toWorld(fold);
                }
                else if (source < BLOOD_SHARE_HOOD + BLOOD_SHARE_HAND)
                    from = fingerEnds()[1];
                else
                    from = toWorld(VigilFigureModel.stripEnd(bloodStrips[random.nextInt(BLOOD_STRIPS)], new Vector3f()));
                drip(minecraft, level, from);
            }
        }

        private void burst(Minecraft minecraft)
        {
            float height = FIGURE_HEIGHT * scale;
            for (int i = 0; i < BURST_SMOKE; i++)
            {
                double px = x + (random.nextDouble() - 0.5) * 2 * BURST_RADIUS;
                double py = y + random.nextDouble() * height;
                double pz = z + (random.nextDouble() - 0.5) * 2 * BURST_RADIUS;
                double vx = (px - x) * BURST_SPEED / BURST_RADIUS;
                double vy = (random.nextDouble() - 0.3) * BURST_SPEED;
                double vz = (pz - z) * BURST_SPEED / BURST_RADIUS;

                var particle = minecraft.particleEngine.createParticle(ParticleTypes.SMOKE, px, py, pz, vx, vy, vz);
                if (particle != null)
                {
                    particle.scale(BURST_SIZE);
                    particle.setLifetime(BURST_LIFETIME_MIN + random.nextInt(BURST_LIFETIME_MORE));
                    burst.add(particle);
                }
            }
        }
    }
}
