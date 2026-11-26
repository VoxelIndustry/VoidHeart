package net.voxelindustry.voidheart.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.Font.DisplayMode;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.client.render.heart.FloorVeins;
import net.voxelindustry.voidheart.client.render.heart.HeartCage;
import net.voxelindustry.voidheart.client.render.heart.HeartForm;
import net.voxelindustry.voidheart.client.render.heart.HeartMotes;
import net.voxelindustry.voidheart.client.render.heart.HeartShape;
import net.voxelindustry.voidheart.client.render.heart.HeartTwin;
import net.voxelindustry.voidheart.client.render.heart.HeartVeins;
import net.voxelindustry.voidheart.client.util.GuiSprites;
import net.voxelindustry.voidheart.client.util.MathUtil;
import net.voxelindustry.voidheart.common.content.heart.HeartBeat;
import net.voxelindustry.voidheart.common.content.heart.VoidHeartTile;
import net.voxelindustry.voidheart.common.util.ProfileUtil;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.lang.Math.floor;
import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * The heart of a pocket, a mass of dark flesh whose form follows how far its owner has given themself to the void, with the owner's head, health and flexion
 * above it. The flesh is solid and world lit, the core showing through its cracks glows through {@code eyes}, both drawn on the same faces: one quad for each merged face of its pixels, a few hundred.
 * Rooted, its veins are flat as vines: painted into the flesh's faces, hanging as crossed strands a pixel wide and creeping over the floor around it a
 * tenth of a pixel up, as vanilla lays its vines and sculk veins. Each vein pixel is a solid violet quad with a glow quad over it, a few thousand at most.
 */
public class VoidHeartRender implements BlockEntityRenderer<VoidHeartTile, VoidHeartRender.VoidHeartRenderState>
{
    private static final Identifier PORTAL_ICONS = Identifier.fromNamespaceAndPath(MODID, "textures/hud/portal_icons.png");
    private static final int        FULL_BRIGHT  = 15728880;

    private static final RenderType FLESH_TYPE = RenderTypes.entitySolid(Identifier.fromNamespaceAndPath(MODID, "textures/block/void_heart_flesh.png"));
    private static final RenderType CORE_TYPE  = RenderTypes.eyes(Identifier.fromNamespaceAndPath(MODID, "textures/block/void_heart_flesh_glow.png"));
    /** Veins are solid violet lit by nothing but themselves, with a pale glow laid over the same faces where a pulse passes. */
    private static final RenderType VEIN_TYPE      = RenderTypes.entitySolid(Identifier.fromNamespaceAndPath(MODID, "textures/block/void_heart_vein.png"));
    private static final RenderType VEIN_GLOW_TYPE = RenderTypes.eyes(Identifier.fromNamespaceAndPath(MODID, "textures/block/void_heart_vein_glow.png"));
    /** With Marrow, bone takes world light like the flesh, the marrow shows pale through wider cracks, and its motes glow. */
    private static final RenderType BONE_TYPE      = RenderTypes.entitySolid(Identifier.fromNamespaceAndPath(MODID, "textures/block/void_heart_bone.png"));
    private static final RenderType MARROW_TYPE    = RenderTypes.eyes(Identifier.fromNamespaceAndPath(MODID, "textures/block/void_heart_marrow_glow.png"));
    private static final RenderType MOTE_TYPE      = RenderTypes.eyes(Identifier.fromNamespaceAndPath(MODID, "textures/block/void_heart_mote.png"));

    /** The heart at rest and swelled with a beat, built once in whole pixels of its block, so a beat steps out a pixel rather than stretching the texture. */
    private static final List<HeartShape.Face> REST    = HeartShape.faces(HeartShape.voxels(false));
    private static final List<HeartShape.Face> SWELLED = HeartShape.faces(HeartShape.voxels(true));
    private static final List<int[]>           MOTE_ORIGINS = HeartMotes.origins(HeartShape.voxels(false));
    private static final boolean[][][]         REST_VOXELS    = HeartShape.voxels(false);
    private static final boolean[][][]         SWELLED_VOXELS = HeartShape.voxels(true);

    /** A dormant heart turns slowly. A rooted one eases to rest square to the world, so its veins meet the floor's pixel for pixel. */
    private static final float TURN_DEGREES_PER_TICK = 0.25F;
    private static final float SETTLE_TICKS          = 100;
    /** Rooting, the veins creep over the heart and down to the floor, then once it has settled out over the floor. */
    private static final float HEART_VEIN_TICKS      = 80;
    private static final float FLOOR_VEIN_TICKS      = 120;
    /** Floor veins lie this far over the floor, as vanilla's sculk veins do. */
    private static final float FLOOR_LIFT            = 0.1F / 16;
    /** The heart shows swelled while its beat is past this. */
    private static final float BEAT_SWELLED          = 0.5F;
    /** With Marrow the ribs close over this long, once the veins have grown, and the cracks warm to the marrow's light as they do. */
    private static final float CAGE_TICKS            = 60;
    private static final float MARROW_REST           = 0.5F;
    private static final float MARROW_FLARE          = 0.5F;

    /** The core is dim between beats and flares with each. */
    private static final float CORE_REST             = 0.25F;
    private static final float CORE_FLARE            = 0.45F;
    /** Lit faintly by its own core, so its shape still reads in a dark pocket. */
    private static final int   FLESH_MIN_BLOCK_LIGHT = 6;

    /** Veins show their violet at this shade, and their glow rises from faint as each beat's pulse runs out along them, away from the heart, in a few flat steps. */
    private static final float VEIN_SHADE            = 0.9F;
    private static final float VEIN_GLOW_REST        = 0.15F;
    private static final float VEIN_GLOW_FLARE       = 0.85F;
    private static final float VEIN_STEPS            = 4;
    private static final float PULSE_PIXELS_PER_TICK = 3;
    /** A pulse fades as it runs out, so the veins read as coming from the heart. */
    private static final float PULSE_FADE_PIXELS     = 60;
    private static final float PULSE_RISE_TICKS      = 1;
    private static final float PULSE_FALL_TICKS      = 2.5F;
    /** A form is kept for each owner's veins at each reach while growing, dropped all at once past this many. */
    private static final int   FORM_CACHE_SIZE       = 256;
    private static final int   FLOOR_CACHE_SIZE      = 64;
    /**
     * With the Second Heart a vein reaches out from the heart past its ribs, then the twin buds from its tip and swells down to the floor, beats loose
     * and quick on its own for a while, then in step with the first. Its growth goes in whole pixel steps.
     */
    private static final float TWIN_BRIDGE_TICKS = 20;
    private static final float TWIN_GROW_TICKS   = 80;
    private static final float TWIN_LOOSE_TICKS  = 100;
    private static final float TWIN_LOOSE_PERIOD = 19;
    private static final int   TWIN_LOOSE_SALT   = 0x5EC0;
    private static final int   TWIN_SIZE_STEPS   = 16;
    /** While the second life recharges the twin stops, darkens, empties of its light and shrinks a little, easing there and back. */
    private static final float TWIN_HOLLOW_TICKS = 20;
    private static final float TWIN_HOLLOW_SIZE  = 0.8F;
    private static final float TWIN_HOLLOW_SHADE = 0.35F;
    /** A newborn heart burns brighter than the first, so it reads as alive beside it rather than a lump of flesh. */
    private static final float TWIN_CORE_REST    = 0.5F;
    private static final float TWIN_CORE_FLARE   = 0.5F;
    /** The twin stands low, below the cracks of the flesh's texture, so its faces are laid this many pixels higher on it and drawn back down. */
    private static final int   TWIN_TEXTURE_LIFT = 4;
    /** Pixels a side: a mote's head and each pixel of its tail. */
    private static final float MOTE_SIZE             = 1 / 16F;

    // Owner overlay layout, the two flexion slot columns are as wide as the head and its bottom lines up with the hearts
    private static final int OWNER_HEAD_X    = -40;
    private static final int OWNER_HEAD_Y    = -4;
    private static final int OWNER_HEAD_SIZE = 18;
    private static final int OWNER_TEXT_X    = OWNER_HEAD_X + OWNER_HEAD_SIZE + 2;

    public static class VoidHeartRenderState extends BlockEntityRenderState
    {
        public float worldTime;
        public float turnDegrees;
        /** Ticks since the latest beat started, and how far the heart has swelled with it, 0 at rest and 1 at its fullest. */
        public float sinceBeat;
        public float beat;

        public int @Nullable [][][] veins;
        /** Vein pixels show up to this far along, all of them once grown. */
        public int     veinReach;
        public boolean veinsGrowing;

        public int @Nullable [][][] cage;
        /** Bone pixels show up to this far along the cage's growth, and the cracks show the marrow this much, 0 to 1. */
        public int     cageReach;
        public float   marrow;
        public List<HeartMotes.Speck> motes = List.of();

        /** The twin's faces in its own grid, drawn this many pixels aside, and the bridge's faces, all empty without one. */
        public List<HeartShape.Face> twin = List.of();
        public int     twinOffset;
        public float   twinBeat;
        /** 0 beating, 1 dark and hollow while the second life recharges. */
        public float   twinHollow;
        public List<BridgeFace> bridge = List.of();

        public FloorVeins.@Nullable Growth floorVeins;
        public int     floorReach;
        public boolean floorGrowing;

        public @Nullable String     ownerName;
        public @Nullable RenderType ownerSkin;

        public boolean ownerPresent;
        public float   health;
        public float   maxHealth;

        public boolean hasHeartData;
        public int     currentFlexion;
        public int     maxFlexion;
    }

    private final PlayerSkinRenderCache playerSkinRenderCache;

    private final Map<Integer, int[][][]> veinsBySeed = new HashMap<>();
    private final Map<FormKey, HeartForm> forms       = new HashMap<>();

    private final Map<FloorKey, FloorVeins.Growth> floors = new HashMap<>();

    private final Map<Integer, int[][][]>              cages     = new HashMap<>();
    private final Map<CageKey, List<HeartShape.Face>> cageFaces = new HashMap<>();

    private record CageKey(int[][][] cage, int reach)
    {
    }

    private record BridgeKey(int[][][] cage, int side)
    {
    }

    private record TwinKey(int side, int step, boolean swelled)
    {
    }

    private record Twin(boolean[][][] voxels, List<HeartShape.Face> faces)
    {
    }

    /** A face of a bridge pixel in the first heart's pixels, and how far along the bridge it lies, for its pulse. */
    public record BridgeFace(HeartShape.Face face, int along)
    {
    }

    private final Map<BridgeKey, List<int[]>> bridges = new HashMap<>();
    private final Map<TwinKey, Twin>          twins   = new HashMap<>();

    private record FormKey(int[][][] veins, boolean swelled, int reach)
    {
    }

    /** The floor map is kept as one array while it doesn't change, so it is compared by identity. */
    private record FloorKey(int[][][] veins, int quarterTurns, boolean[][] floor)
    {
    }

    public VoidHeartRender(BlockEntityRendererProvider.Context context)
    {
        playerSkinRenderCache = context.playerSkinRenderCache();
    }

    @Override
    public VoidHeartRenderState createRenderState()
    {
        return new VoidHeartRenderState();
    }

    @Override
    public void extractRenderState(VoidHeartTile voidHeart, VoidHeartRenderState state, float partialTicks, Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress)
    {
        BlockEntityRenderer.super.extractRenderState(voidHeart, state, partialTicks, cameraPosition, breakProgress);

        float time = voidHeart.getLevel().getGameTime() + partialTicks;
        state.worldTime = time;
        int seed = voidHeart.beatSeed();
        boolean rooted = voidHeart.heartForm() >= 2;
        state.sinceBeat = HeartBeat.since(time, seed, rooted ? HeartBeat.ROOTED_PERIOD : HeartBeat.DORMANT_PERIOD);
        state.beat = HeartBeat.swell(state.sinceBeat, rooted);

        state.floorVeins = null;
        state.floorReach = 0;
        state.floorGrowing = false;
        state.cage = null;
        state.cageReach = 0;
        state.marrow = 0;
        state.motes = List.of();
        state.twin = List.of();
        state.bridge = List.of();
        state.twinBeat = 0;
        state.twinHollow = 0;
        if (rooted)
        {
            long rootedAt = voidHeart.grownAt(2);
            float since = time - rootedAt;
            int quarterTurns = settle(state, rootedAt, since);

            state.veins = veinsBySeed.computeIfAbsent(seed, owner -> HeartVeins.grow(HeartShape.voxels(false), owner));
            int length = HeartVeins.length(state.veins);
            float heartGrown = Math.clamp(since / HEART_VEIN_TICKS, 0, 1);
            state.veinsGrowing = heartGrown < 1;
            state.veinReach = state.veinsGrowing ? Math.round(length * heartGrown) : length;

            var floor = voidHeart.floor();
            if (floor != null && floor.length == FloorVeins.CELLS)
            {
                var growth = floorVeins(seed, quarterTurns, state.veins, floor);
                float floorGrown = Math.clamp((since - SETTLE_TICKS) / FLOOR_VEIN_TICKS, 0, 1);
                state.floorVeins = growth;
                state.floorGrowing = floorGrown < 1;
                state.floorReach = floorGrown <= 0 ? 0 : growth.first() + Math.round((growth.last() + 1 - growth.first()) * floorGrown);
            }

            if (voidHeart.heartForm() >= 3)
            {
                // Gained with Vein at once, the ribs wait for the veins to finish growing
                float sinceMarrow = time - Math.max(voidHeart.grownAt(3), rootedAt + (long) HEART_VEIN_TICKS);
                var veins = state.veins;
                state.cage = cages.computeIfAbsent(seed, owner -> HeartCage.grow(HeartShape.voxels(true), veins, owner));
                float closed = Math.clamp(sinceMarrow / CAGE_TICKS, 0, 1);
                state.cageReach = Math.round(HeartCage.length(state.cage) * closed);
                state.marrow = closed;
                if (closed >= 1)
                    state.motes = HeartMotes.at(time, seed, HeartBeat.ROOTED_PERIOD, MOTE_ORIGINS);

                if (voidHeart.heartForm() >= 4)
                {
                    long caged = Math.max(voidHeart.grownAt(3), rootedAt + (long) HEART_VEIN_TICKS) + (long) CAGE_TICKS;
                    twin(state, voidHeart, time, time - Math.max(voidHeart.grownAt(4), caged), seed, HeartTwin.side(floor, quarterTurns));
                }
            }
        }
        else
        {
            state.turnDegrees = TURN_DEGREES_PER_TICK * time;
            state.veins = null;
            state.veinsGrowing = false;
            state.veinReach = 0;
        }

        var playerProfile = voidHeart.getPlayerProfile();
        if (playerProfile == null)
        {
            state.ownerName = null;
            state.ownerSkin = null;
            return;
        }

        state.ownerName = ProfileUtil.getName(playerProfile);
        state.ownerSkin = playerSkinRenderCache.getOrDefault(playerProfile).renderType();

        var player = voidHeart.getPlayerID() == null ? null : voidHeart.getLevel().getPlayerByUUID(voidHeart.getPlayerID());
        state.ownerPresent = player != null;
        if (player != null)
        {
            state.health = player.getHealth();
            state.maxHealth = player.getMaxHealth();
        }

        var heartData = voidHeart.heartData();
        state.hasHeartData = heartData != null;
        if (heartData != null)
        {
            state.currentFlexion = heartData.currentFlexionCount();
            state.maxFlexion = heartData.maxFlexion(voidHeart.getOwnerAttunement());
        }
    }

    @Override
    public void submit(VoidHeartRenderState state, PoseStack matrices, SubmitNodeCollector collector, CameraRenderState camera)
    {
        matrices.pushPose();

        matrices.translate(0.5, 0, 0.5);
        matrices.rotate(MathUtil.quatFromAngleDegrees(state.turnDegrees, MathUtil.NEGATIVE_Y));
        matrices.translate(-0.5, 0, -0.5);

        boolean swelled = state.beat > BEAT_SWELLED;
        var form = state.veins == null ? null : form(state.veins, swelled, state.veinReach);
        var faces = form != null ? form.flesh() : swelled ? SWELLED : REST;
        int fleshLight = LightCoordsUtil.withBlock(state.lightCoords, Math.max(LightCoordsUtil.block(state.lightCoords), FLESH_MIN_BLOCK_LIGHT));
        collector.submitCustomGeometry(matrices, FLESH_TYPE, (pose, buffer) -> faces(pose, buffer, fleshLight, 0xFFFFFFFF, faces));

        int core = Math.round(255 * (CORE_REST + CORE_FLARE * state.beat));
        int coreColor = 0xFF000000 | core << 16 | core << 8 | core;
        if (state.marrow < 1)
            collector.submitCustomGeometry(matrices, CORE_TYPE, (pose, buffer) -> faces(pose, buffer, FULL_BRIGHT, coreColor, faces));
        if (state.marrow > 0)
        {
            // The marrow's cracks show over the plain ones as the ribs close, then alone
            int marrow = Math.round(255 * state.marrow);
            int marrowShade = Math.round(255 * (MARROW_REST + MARROW_FLARE * state.beat));
            int marrowColor = marrow << 24 | marrowShade << 16 | marrowShade << 8 | marrowShade;
            collector.submitCustomGeometry(matrices, MARROW_TYPE, (pose, buffer) -> faces(pose, buffer, FULL_BRIGHT, marrowColor, faces));
        }

        var cage = state.cage;
        if (cage != null && state.cageReach > 0)
        {
            var bone = cageFaces(cage, state.cageReach);
            collector.submitCustomGeometry(matrices, BONE_TYPE, (pose, buffer) -> faces(pose, buffer, state.lightCoords, 0xFFFFFFFF, bone));
        }

        int veinShade = Math.round(255 * VEIN_SHADE);
        int veinColor = 0xFF000000 | veinShade << 16 | veinShade << 8 | veinShade;
        if (!state.twin.isEmpty())
            twin(state, matrices, collector, fleshLight);
        if (!state.bridge.isEmpty())
        {
            var bridge = state.bridge;
            float quiet = 1 - state.twinHollow;
            collector.submitCustomGeometry(matrices, VEIN_TYPE, (pose, buffer) ->
            {
                for (var face : bridge)
                    face(pose, buffer, FULL_BRIGHT, veinColor, face.face());
            });
            collector.submitCustomGeometry(matrices, VEIN_GLOW_TYPE, (pose, buffer) ->
            {
                for (var face : bridge)
                {
                    int glow = glow(face.along(), Integer.MAX_VALUE, false, state.sinceBeat);
                    int alpha = Math.round((glow >>> 24) * quiet);
                    face(pose, buffer, FULL_BRIGHT, alpha << 24 | 0xFFFFFF, face.face());
                }
            });
        }
        if (form != null && (!form.paint().isEmpty() || !form.strands().isEmpty()))
        {
            collector.submitCustomGeometry(matrices, VEIN_TYPE, (pose, buffer) ->
            {
                for (var paint : form.paint())
                    face(pose, buffer, FULL_BRIGHT, veinColor, paint.face());
                for (var strand : form.strands())
                    strand(pose, buffer, veinColor, strand);
            });
            collector.submitCustomGeometry(matrices, VEIN_GLOW_TYPE, (pose, buffer) ->
            {
                for (var paint : form.paint())
                    face(pose, buffer, FULL_BRIGHT, glow(paint.along(), state.veinReach, state.veinsGrowing, state.sinceBeat), paint.face());
                for (var strand : form.strands())
                    strand(pose, buffer, glow(strand.along(), state.veinReach, state.veinsGrowing, state.sinceBeat), strand);
            });
        }

        matrices.popPose();

        // The floor is the world's, so its veins lie square to the block and don't turn with the heart
        var floorVeins = state.floorVeins;
        if (floorVeins != null && state.floorReach > floorVeins.first())
        {
            var shown = new ArrayList<FloorVeins.Pixel>();
            for (var pixel : floorVeins.pixels())
            {
                if (pixel.along() >= state.floorReach)
                    break;
                shown.add(pixel);
            }
            collector.submitCustomGeometry(matrices, VEIN_TYPE, (pose, buffer) ->
            {
                for (var pixel : shown)
                    floorPixel(pose, buffer, veinColor, pixel);
            });
            collector.submitCustomGeometry(matrices, VEIN_GLOW_TYPE, (pose, buffer) ->
            {
                for (var pixel : shown)
                    floorPixel(pose, buffer, glow(pixel.along(), state.floorReach, state.floorGrowing, state.sinceBeat), pixel);
            });
        }

        if (!state.motes.isEmpty())
            motes(state, matrices, collector, camera);

        if (state.ownerName == null)
            return;

        matrices.pushPose();
        matrices.translate(0.5, 1.3, 0.5);
        matrices.scale(1.25F / 64F, 1.25F / 64F, 1.25F / 64F);

        // Since 26.1 the camera orientation faces the other way, only the Y axis needs flipping for text and sprites
        matrices.rotate(camera.orientation);
        matrices.rotate(MathUtil.quatFromAngleDegrees(180, MathUtil.POSITIVE_X));

        // The head sits in the corner, name and hearts start on its right and the flexion slots stack below it
        var name = Component.literal(state.ownerName).getVisualOrderText();
        collector.submitText(matrices, OWNER_TEXT_X + 1, -1, name, false, DisplayMode.NORMAL, FULL_BRIGHT, 0xFF3C3C3C, 0, 0);
        collector.submitText(matrices, OWNER_TEXT_X, -2, name, false, DisplayMode.SEE_THROUGH, FULL_BRIGHT, 0xFFFFFFFF, 0, 0);

        if (state.ownerSkin != null)
            collector.submitCustomGeometry(matrices, state.ownerSkin, (pose, buffer) -> renderHeadSkin(pose, buffer, OWNER_HEAD_X, OWNER_HEAD_Y, 0, OWNER_HEAD_SIZE, OWNER_HEAD_SIZE));

        if (state.ownerPresent)
            collector.submitCustomGeometry(matrices, GuiSprites.renderType(), (pose, buffer) -> renderHearts(state.health, state.maxHealth, pose, buffer, OWNER_TEXT_X, 8, 0, 6, 6));

        if (state.hasHeartData)
            collector.submitCustomGeometry(matrices, RenderTypes.entityTranslucent(PORTAL_ICONS), (pose, buffer) -> renderHeartData(state.currentFlexion, state.maxFlexion, pose, buffer, OWNER_HEAD_X, OWNER_HEAD_Y + OWNER_HEAD_SIZE + 2, 0));

        matrices.popPose();
    }

    /** The twin and its bridge as far as they have grown, how it beats, and how hollow it is. */
    private void twin(VoidHeartRenderState state, VoidHeartTile voidHeart, float time, float since, int seed, int side)
    {
        var cage = state.cage;
        var veins = state.veins;
        var path = bridges.computeIfAbsent(new BridgeKey(cage, side), key -> HeartTwin.bridge(REST_VOXELS, SWELLED_VOXELS, veins, key.cage(), key.side()));
        int reach = Math.round(path.size() * Math.clamp(since / TWIN_BRIDGE_TICKS, 0, 1));

        boolean beats = voidHeart.isTwinBeats();
        float sinceChange = time - voidHeart.getTwinChangedAt();
        float eased = Math.clamp(sinceChange / TWIN_HOLLOW_TICKS, 0, 1);
        state.twinHollow = beats ? 1 - eased : eased;

        float grown = Math.clamp((since - TWIN_BRIDGE_TICKS) / TWIN_GROW_TICKS, 0, 1);
        grown = grown * grown * (3 - 2 * grown);
        float size = grown * (1 - (1 - TWIN_HOLLOW_SIZE) * state.twinHollow);
        int step = Math.round(size * TWIN_SIZE_STEPS);

        if (!beats)
            state.twinBeat = 0;
        else if (since < TWIN_BRIDGE_TICKS + TWIN_GROW_TICKS + TWIN_LOOSE_TICKS)
            state.twinBeat = HeartBeat.swell(HeartBeat.since(time, seed ^ TWIN_LOOSE_SALT, TWIN_LOOSE_PERIOD), false);
        else
            state.twinBeat = state.beat;
        state.twinBeat *= 1 - state.twinHollow;

        var twin = step > 0 ? twin(side, step, state.twinBeat > BEAT_SWELLED) : null;
        state.twin = twin == null ? List.of() : twin.faces();
        state.twinOffset = HeartTwin.offset(side);
        state.bridge = bridgeFaces(path, reach, state.beat > BEAT_SWELLED ? SWELLED_VOXELS : REST_VOXELS, twin == null ? null : twin.voxels(), state.twinOffset);
    }

    private Twin twin(int side, int step, boolean swelled)
    {
        return twins.computeIfAbsent(new TwinKey(side, step, swelled), key ->
        {
            var voxels = HeartTwin.voxels(key.step() / (float) TWIN_SIZE_STEPS, key.swelled(), key.side());
            var faces = new ArrayList<HeartShape.Face>();
            for (var face : HeartShape.faces(voxels))
            {
                int lift = TWIN_TEXTURE_LIFT;
                faces.add(switch (face.axis())
                {
                    case 0 -> new HeartShape.Face(0, face.positive(), face.plane(), face.a0() + lift, face.b0(), face.a1() + lift, face.b1());
                    case 1 -> new HeartShape.Face(1, face.positive(), face.plane() + lift, face.a0(), face.b0(), face.a1(), face.b1());
                    default -> new HeartShape.Face(2, face.positive(), face.plane(), face.a0(), face.b0() + lift, face.a1(), face.b1() + lift);
                });
            }
            return new Twin(voxels, List.copyOf(faces));
        });
    }

    /** The faces of the bridge's pixels grown so far, leaving out pixels either heart swells into and faces laid against flesh or the next pixel. */
    private static List<BridgeFace> bridgeFaces(List<int[]> path, int reach, boolean[][][] heart, boolean @Nullable [][][] twin, int twinOffset)
    {
        var shown = new ArrayList<int[]>();
        for (int i = 0; i < reach; i++)
        {
            var pixel = path.get(i);
            if (!solid(heart, twin, twinOffset, pixel[0], pixel[1], pixel[2]))
                shown.add(new int[] { pixel[0], pixel[1], pixel[2], i });
        }
        var faces = new ArrayList<BridgeFace>();
        for (var pixel : shown)
        {
            int x = pixel[0], y = pixel[1], z = pixel[2];
            for (int axis = 0; axis < 3; axis++)
            {
                for (int sign = -1; sign <= 1; sign += 2)
                {
                    int nx = x + (axis == 0 ? sign : 0), ny = y + (axis == 1 ? sign : 0), nz = z + (axis == 2 ? sign : 0);
                    if (solid(heart, twin, twinOffset, nx, ny, nz) || shownAt(shown, nx, ny, nz))
                        continue;
                    boolean positive = sign > 0;
                    int plane = (axis == 0 ? x : axis == 1 ? y : z) + (positive ? 1 : 0);
                    var face = switch (axis)
                    {
                        case 0 -> new HeartShape.Face(0, positive, plane, y, z, y + 1, z + 1);
                        case 1 -> new HeartShape.Face(1, positive, plane, x, z, x + 1, z + 1);
                        default -> new HeartShape.Face(2, positive, plane, x, y, x + 1, y + 1);
                    };
                    faces.add(new BridgeFace(face, pixel[3]));
                }
            }
        }
        return faces;
    }

    private static boolean shownAt(List<int[]> shown, int x, int y, int z)
    {
        for (var pixel : shown)
        {
            if (pixel[0] == x && pixel[1] == y && pixel[2] == z)
                return true;
        }
        return false;
    }

    private static boolean solid(boolean[][][] heart, boolean @Nullable [][][] twin, int twinOffset, int x, int y, int z)
    {
        if (y < 0 || y >= HeartShape.SIZE || z < 0 || z >= HeartShape.SIZE)
            return false;
        if (x >= 0 && x < HeartShape.SIZE && heart[x][y][z])
            return true;
        int tx = x - twinOffset;
        return twin != null && tx >= 0 && tx < HeartShape.SIZE && twin[tx][y][z];
    }

    /** The twin's flesh, darkened as it hollows, and its core, which beats with it and goes out while it is hollow. */
    private static void twin(VoidHeartRenderState state, PoseStack matrices, SubmitNodeCollector collector, int fleshLight)
    {
        matrices.pushPose();
        matrices.translate(state.twinOffset / 16F, -TWIN_TEXTURE_LIFT / 16F, 0);
        var faces = state.twin;
        int shade = Math.round(255 * (1 - (1 - TWIN_HOLLOW_SHADE) * state.twinHollow));
        int fleshColor = 0xFF000000 | shade << 16 | shade << 8 | shade;
        collector.submitCustomGeometry(matrices, FLESH_TYPE, (pose, buffer) -> faces(pose, buffer, fleshLight, fleshColor, faces));
        if (state.twinHollow < 1)
        {
            int core = Math.round(255 * (TWIN_CORE_REST + TWIN_CORE_FLARE * state.twinBeat) * (1 - state.twinHollow));
            int coreColor = 0xFF000000 | core << 16 | core << 8 | core;
            collector.submitCustomGeometry(matrices, CORE_TYPE, (pose, buffer) -> faces(pose, buffer, FULL_BRIGHT, coreColor, faces));
        }
        matrices.popPose();
    }

    private HeartForm form(int[][][] veins, boolean swelled, int reach)
    {
        if (forms.size() > FORM_CACHE_SIZE)
            forms.clear();
        return forms.computeIfAbsent(new FormKey(veins, swelled, reach), key -> HeartForm.build(key.swelled(), key.veins(), key.reach()));
    }

    private List<HeartShape.Face> cageFaces(int[][][] cage, int reach)
    {
        if (cageFaces.size() > FORM_CACHE_SIZE)
            cageFaces.clear();
        return cageFaces.computeIfAbsent(new CageKey(cage, reach), key -> HeartShape.faces(HeartCage.shown(key.cage(), key.reach())));
    }

    /**
     * Each mote pixel as a square facing the camera, turned with the heart around its block's middle since motes leave from its pixels. Wound
     * counterclockwise on screen, as {@code eyes} culls.
     */
    private static void motes(VoidHeartRenderState state, PoseStack matrices, SubmitNodeCollector collector, CameraRenderState camera)
    {
        float turn = (float) Math.toRadians(state.turnDegrees);
        float cos = (float) Math.cos(turn), sin = (float) Math.sin(turn);
        var right = camera.orientation.transform(new Vector3f(1, 0, 0)).mul(MOTE_SIZE / 2);
        var up = camera.orientation.transform(new Vector3f(0, 1, 0)).mul(MOTE_SIZE / 2);
        var motes = state.motes;
        collector.submitCustomGeometry(matrices, MOTE_TYPE, (pose, buffer) ->
        {
            for (var speck : motes)
            {
                float cx = speck.x() - 0.5F, cz = speck.z() - 0.5F;
                float x = 0.5F + cx * cos - cz * sin, y = speck.y(), z = 0.5F + cx * sin + cz * cos;
                int color = Math.round(255 * speck.alpha()) << 24 | 0xFFFFFF;
                vertex(pose, buffer, FULL_BRIGHT, color, x - right.x + up.x, y - right.y + up.y, z - right.z + up.z, 0, 0, 0, 0, 1);
                vertex(pose, buffer, FULL_BRIGHT, color, x - right.x - up.x, y - right.y - up.y, z - right.z - up.z, 0, 1, 0, 0, 1);
                vertex(pose, buffer, FULL_BRIGHT, color, x + right.x - up.x, y + right.y - up.y, z + right.z - up.z, 1, 1, 0, 0, 1);
                vertex(pose, buffer, FULL_BRIGHT, color, x + right.x + up.x, y + right.y + up.y, z + right.z + up.z, 1, 0, 0, 0, 1);
            }
        });
    }

    @Override
    public boolean shouldRenderOffScreen()
    {
        // Its veins reach blocks out over the floor
        return true;
    }

    /**
     * Turns the heart on from where it was when it rooted and eases it to rest at the next quarter turn far enough on to slow into, starting at its own
     * turning speed so it never jerks. Returns the quarter turns it rests at.
     */
    private static int settle(VoidHeartRenderState state, long rootedAt, float since)
    {
        double start = TURN_DEGREES_PER_TICK * (double) rootedAt % 360;
        double target = Math.ceil((start + TURN_DEGREES_PER_TICK * SETTLE_TICKS / 2) / 90) * 90;
        double u = Math.clamp(since / SETTLE_TICKS, 0, 1);
        double eased = (target - start) * (3 * u * u - 2 * u * u * u) + TURN_DEGREES_PER_TICK * SETTLE_TICKS * (u - 2 * u * u + u * u * u);
        state.turnDegrees = (float) (start + eased);
        return (int) Math.round(target / 90) & 3;
    }

    private FloorVeins.Growth floorVeins(int seed, int quarterTurns, int[][][] veins, boolean[][] floor)
    {
        if (floors.size() > FLOOR_CACHE_SIZE)
            floors.clear();
        return floors.computeIfAbsent(new FloorKey(veins, quarterTurns, floor), key ->
        {
            var feet = new ArrayList<int[]>();
            for (var foot : HeartVeins.feet(key.veins()))
            {
                var turned = FloorVeins.turn(foot[0], foot[1], key.quarterTurns());
                feet.add(new int[] { turned[0], turned[1], foot[2] });
            }
            return FloorVeins.grow(key.floor(), feet, seed);
        });
    }

    /**
     * A vein pixel's glow: faint, brighter where the lub and dub run out past it and less so the further out it lies, in a few flat steps, and full at
     * the growing tip of each vein while they grow. Carried in alpha, since the glow is laid over the vein's own violet.
     */
    private static int glow(int along, int reach, boolean growing, float sinceBeat)
    {
        float glow;
        if (growing && along >= reach - 1)
            glow = 1;
        else
        {
            float since = sinceBeat - along / PULSE_PIXELS_PER_TICK;
            float pulse = Math.max(flash(since), HeartBeat.DUB_STRENGTH * flash(since - HeartBeat.DUB_DELAY)) * (float) Math.exp(-along / PULSE_FADE_PIXELS);
            glow = VEIN_GLOW_REST + VEIN_GLOW_FLARE * Math.round(pulse * VEIN_STEPS) / VEIN_STEPS;
        }
        return Math.round(255 * glow) << 24 | 0xFFFFFF;
    }

    /** A hanging pixel of vein as two planes crossed through its middle, each drawn both ways, so it is a line a pixel wide from any side. */
    private static void strand(PoseStack.Pose pose, VertexConsumer buffer, int color, HeartForm.Strand strand)
    {
        int x = strand.x(), y = strand.y(), z = strand.z();
        float x0 = x / 16F, x1 = (x + 1) / 16F, xm = (x + 0.5F) / 16F;
        float z0 = z / 16F, z1 = (z + 1) / 16F, zm = (z + 0.5F) / 16F;
        float y0 = y / 16F, y1 = (y + 1) / 16F;
        float v0 = (15 - y) / 16F, v1 = (16 - y) / 16F;
        float uz0 = z / 32F, uz1 = (z + 1) / 32F, ux0 = x / 32F, ux1 = (x + 1) / 32F;
        quad(pose, buffer, FULL_BRIGHT, color, xm, y1, z1, xm, y0, z1, xm, y0, z0, xm, y1, z0, uz1, v0, uz0, v1, 1, 0, 0);
        quad(pose, buffer, FULL_BRIGHT, color, xm, y1, z0, xm, y0, z0, xm, y0, z1, xm, y1, z1, uz0, v0, uz1, v1, -1, 0, 0);
        quad(pose, buffer, FULL_BRIGHT, color, x0, y1, zm, x0, y0, zm, x1, y0, zm, x1, y1, zm, ux0, v0, ux1, v1, 0, 0, 1);
        quad(pose, buffer, FULL_BRIGHT, color, x1, y1, zm, x1, y0, zm, x0, y0, zm, x0, y1, zm, ux1, v0, ux0, v1, 0, 0, -1);
    }

    /** A pixel of vein over the floor, placed from the corner of the square of floor around the heart. */
    private static void floorPixel(PoseStack.Pose pose, VertexConsumer buffer, int color, FloorVeins.Pixel pixel)
    {
        int px = pixel.x() - FloorVeins.RADIUS * 16, pz = pixel.z() - FloorVeins.RADIUS * 16;
        float x0 = px / 16F, x1 = (px + 1) / 16F, z0 = pz / 16F, z1 = (pz + 1) / 16F;
        float u0 = (16 + Math.floorMod(px, 16)) / 32F, u1 = u0 + 1 / 32F;
        float v0 = Math.floorMod(pz, 16) / 16F, v1 = v0 + 1 / 16F;
        quad(pose, buffer, FULL_BRIGHT, color, x0, FLOOR_LIFT, z0, x0, FLOOR_LIFT, z1, x1, FLOOR_LIFT, z1, x1, FLOOR_LIFT, z0, u0, v0, u1, v1, 0, 1, 0);
    }

    private static float flash(float since)
    {
        if (since < 0)
            return 0;
        if (since < PULSE_RISE_TICKS)
            return since / PULSE_RISE_TICKS;
        return (float) Math.exp(-(since - PULSE_RISE_TICKS) / PULSE_FALL_TICKS);
    }

    /**
     * Each face in pixels with its texture laid as in the world, sides from the left half of the sheet and tops and bottoms from the right, so the flesh
     * shows at the block's own 16 pixels and every texel lands whole. Corners run counterclockwise from outside, since the flesh culls.
     */
    private static void faces(PoseStack.Pose pose, VertexConsumer buffer, int light, int color, List<HeartShape.Face> faces)
    {
        for (var face : faces)
            face(pose, buffer, light, color, face);
    }

    private static void face(PoseStack.Pose pose, VertexConsumer buffer, int light, int color, HeartShape.Face face)
    {
        float plane = face.plane() / 16F;
        float a0 = face.a0() / 16F, a1 = face.a1() / 16F, b0 = face.b0() / 16F, b1 = face.b1() / 16F;
        // Faces past the block along X, as the bridge's, take their texels from where they lie in their own block
        int ua0 = face.a0() - 16 * Math.floorDiv(face.a0(), 16), ua1 = ua0 + face.a1() - face.a0();
        switch (face.axis())
        {
            // Facing X: a is Y, b is Z
            case 0 ->
            {
                float v0 = (16 - face.a1()) / 16F, v1 = (16 - face.a0()) / 16F;
                if (face.positive())
                    quad(pose, buffer, light, color, plane, a1, b1, plane, a0, b1, plane, a0, b0, plane, a1, b0, face.b1() / 32F, v0, face.b0() / 32F, v1, 1, 0, 0);
                else
                    quad(pose, buffer, light, color, plane, a1, b0, plane, a0, b0, plane, a0, b1, plane, a1, b1, face.b0() / 32F, v0, face.b1() / 32F, v1, -1, 0, 0);
            }
            // Facing Y: a is X, b is Z
            case 1 ->
            {
                float u0 = (16 + ua0) / 32F, u1 = (16 + ua1) / 32F;
                if (face.positive())
                    quad(pose, buffer, light, color, a0, plane, b0, a0, plane, b1, a1, plane, b1, a1, plane, b0, u0, face.b0() / 16F, u1, face.b1() / 16F, 0, 1, 0);
                else
                    quad(pose, buffer, light, color, a0, plane, b1, a0, plane, b0, a1, plane, b0, a1, plane, b1, u0, face.b1() / 16F, u1, face.b0() / 16F, 0, -1, 0);
            }
            // Facing Z: a is X, b is Y
            default ->
            {
                float v0 = (16 - face.b1()) / 16F, v1 = (16 - face.b0()) / 16F;
                if (face.positive())
                    quad(pose, buffer, light, color, a0, b1, plane, a0, b0, plane, a1, b0, plane, a1, b1, plane, ua0 / 32F, v0, ua1 / 32F, v1, 0, 0, 1);
                else
                    quad(pose, buffer, light, color, a1, b1, plane, a1, b0, plane, a0, b0, plane, a0, b1, plane, ua1 / 32F, v0, ua0 / 32F, v1, 0, 0, -1);
            }
        }
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer buffer, int light, int color,
                             float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz,
                             float u0, float v0, float u1, float v1, int nx, int ny, int nz)
    {
        vertex(pose, buffer, light, color, ax, ay, az, u0, v0, nx, ny, nz);
        vertex(pose, buffer, light, color, bx, by, bz, u0, v1, nx, ny, nz);
        vertex(pose, buffer, light, color, cx, cy, cz, u1, v1, nx, ny, nz);
        vertex(pose, buffer, light, color, dx, dy, dz, u1, v0, nx, ny, nz);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, int light, int color, float x, float y, float z, float u, float v, int nx, int ny, int nz)
    {
        buffer.addVertex(pose, x, y, z)
                .setColor(color)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }

    private void renderHeartData(int currentFlexion, int maxFlexion, PoseStack.Pose pose, VertexConsumer buffer, float posX, float posY, float posZ)
    {
        for (int i = 0; i < currentFlexion; i++)
            renderSprite(pose, buffer, posX + 10 * (i % 2), posY + 10 * (i / 2), posZ, 8, 8, 0.25F, 4 / 48F, 0.75F, 12 / 48F);

        for (int i = currentFlexion; i < maxFlexion; i++)
            renderSprite(pose, buffer, posX + 10 * (i % 2), posY + 10 * (i / 2), posZ, 8, 8, 0.25F, 20 / 48F, 0.75F, 28 / 48F);
    }

    private void renderHearts(float health, float maxHealth, PoseStack.Pose pose, VertexConsumer buffer, float posX, float posY, float posZ, float width, float height)
    {
        TextureAtlasSprite container = GuiSprites.get(GuiSprites.HEART_CONTAINER);
        for (int i = 0; i < maxHealth; i += 2)
            renderSprite(pose, buffer, posX + width / 2 * i, posY, posZ, width, height, container);

        TextureAtlasSprite full = GuiSprites.get(GuiSprites.HEART_FULL);
        for (int i = 0; i < health - 1; i += 2)
            renderSprite(pose, buffer, posX + width / 2 * i, posY, posZ, width, height, full);

        if (health % 2 != 0)
            renderSprite(pose, buffer, posX + width * (float) floor(health / 2F), posY, posZ, width, height, GuiSprites.get(GuiSprites.HEART_HALF));
    }

    private void renderSprite(PoseStack.Pose pose, VertexConsumer buffer, float posX, float posY, float posZ, float width, float height, TextureAtlasSprite sprite)
    {
        renderSprite(pose, buffer, posX, posY, posZ, width, height, sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV1());
    }

    private void renderSprite(PoseStack.Pose pose,
                              VertexConsumer buffer,
                              float posX,
                              float posY,
                              float posZ,
                              float width,
                              float height,
                              float minU,
                              float minV,
                              float maxU,
                              float maxV)
    {
        buffer.addVertex(pose, posX, posY, posZ)
                .setColor(1, 1, 1, 0.95F)
                .setUv(minU, minV)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(FULL_BRIGHT)
                .setNormal(-1, 0, 0);
        buffer.addVertex(pose, posX, posY + height, posZ)
                .setColor(1, 1, 1, 0.95F)
                .setUv(minU, maxV)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(FULL_BRIGHT)
                .setNormal(-1, 0, 0);
        buffer.addVertex(pose, posX + width, posY + height, posZ)
                .setColor(1, 1, 1, 0.95F)
                .setUv(maxU, maxV)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(FULL_BRIGHT)
                .setNormal(-1, 0, 0);
        buffer.addVertex(pose, posX + width, posY, posZ)
                .setColor(1, 1, 1, 0.95F)
                .setUv(maxU, minV)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(FULL_BRIGHT)
                .setNormal(-1, 0, 0);
    }

    private void renderHeadSkin(PoseStack.Pose pose, VertexConsumer buffer, float posX, float posY, float posZ, float width, float height)
    {
        renderSprite(pose, buffer, posX, posY, posZ, width, height, 0.125F, 0.125F, 0.25F, 0.25F);
    }
}
