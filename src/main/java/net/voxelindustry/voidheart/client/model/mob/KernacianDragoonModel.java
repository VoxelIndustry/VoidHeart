package net.voxelindustry.voidheart.client.model.mob;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.voxelindustry.voidheart.client.render.state.KernacianDragoonRenderState;
import net.voxelindustry.voidheart.common.content.mob.DragoonAdaptation;
import net.voxelindustry.voidheart.common.content.mob.DragoonAdaptation.Kind;
import net.voxelindustry.voidheart.common.content.mob.KernacianDragoon;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * A heavy plated figure under a faceless helm, cut by one narrow eye slit and swept back by crest blades. To fire, the faceplate splits down the middle and its
 * halves slide apart, then four shutters behind them fold open onto a furnace whose core swells until the beam leaves it. What hurts it shows on it: blades leave
 * it plated, blunt hits spiked, arrows shielded, fire finned, explosions bulked out and magic ringed with runes, each in three steps. On a wall it climbs hand over
 * hand, and strikes over the edge with its right arm where on the ground it would kick.
 */
public class KernacianDragoonModel extends EntityModel<KernacianDragoonRenderState>
{
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(MODID, "kernacian_dragoon"), "main");

    private static final float WALK_RATE = 0.45F;
    private static final float WALK_REACH = 0.4F;
    private static final float ARM_SWAY = 0.2F;
    private static final float SPLIT_SLIDE = 2.5F;
    private static final float SPLIT_SWING = 0.9F;
    private static final float SHUTTER_FOLD = 1.45F;
    private static final float CORE_SWELL = 0.7F;
    private static final float CORE_PULSE = 0.12F;
    private static final float CORE_PULSE_RATE = 1.7F;
    private static final float RECOIL_PITCH = -0.35F;
    private static final float CREST_SWEEP = 0.35F;
    private static final float BLADE_SWEEP = 0.3F;
    private static final float FIN_SWEEP = 0.4F;
    private static final float BRACE_PITCH = -0.35F;
    private static final float KICK_WINDUP_PITCH = 0.55F;
    private static final float KICK_PITCH = -1.35F;
    private static final float RING_SPIN = 0.02F;
    // Its hands swing between gripping the face and lifting off it, its shoulders hunching as they pull, and the diagonal foot pushes
    private static final float CLIMB_ARM_PITCH = -2.8F;
    private static final float CLIMB_ARM_SWING = 0.18F;
    private static final float CLIMB_ARM_SPREAD = 0.18F;
    private static final float CLIMB_SHRUG = 1.5F;
    private static final float CLIMB_LEG_PITCH = -0.25F;
    private static final float CLIMB_LEG_SWING = 0.25F;
    // Drawn this far back from the face it climbs, so its hands and feet meet the face rather than sink into it
    private static final float CLIMB_BACK = 2.5F;
    private static final float SWIPE_WINDUP_PITCH = -3.4F;
    private static final float SWIPE_PITCH = -1.95F;

    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;
    private final ModelPart leftHalf;
    private final ModelPart rightHalf;
    private final ModelPart topShutter;
    private final ModelPart bottomShutter;
    private final ModelPart leftShutter;
    private final ModelPart rightShutter;
    private final ModelPart core;
    private final ModelPart innerRing;
    private final ModelPart outerRing;
    private final Map<Kind, List<List<ModelPart>>> tiers = new EnumMap<>(Kind.class);

    public KernacianDragoonModel(ModelPart root)
    {
        super(root);
        body = root.getChild("body");
        head = root.getChild("head");
        rightArm = root.getChild("right_arm");
        leftArm = root.getChild("left_arm");
        rightLeg = root.getChild("right_leg");
        leftLeg = root.getChild("left_leg");
        leftHalf = head.getChild("left_half");
        rightHalf = head.getChild("right_half");
        topShutter = head.getChild("top_shutter");
        bottomShutter = head.getChild("bottom_shutter");
        leftShutter = head.getChild("left_shutter");
        rightShutter = head.getChild("right_shutter");
        core = head.getChild("core");
        innerRing = body.getChild("magic_1");
        outerRing = body.getChild("magic_3");

        var holders = List.of(body, head, rightArm, leftArm, rightLeg, leftLeg);
        for (var kind : Kind.values())
        {
            var byTier = new ArrayList<List<ModelPart>>();
            for (int tier = 1; tier <= DragoonAdaptation.TIERS.length; tier++)
            {
                var name = kind.name().toLowerCase() + "_" + tier;
                var parts = new ArrayList<ModelPart>();
                for (var holder : holders)
                {
                    if (holder.hasChild(name))
                        parts.add(holder.getChild(name));
                }
                byTier.add(parts);
            }
            tiers.put(kind, byTier);
        }
    }

    /**
     * Where the veins of the seventh kind take their texture: one even patch, lit in the glow texture, so a ridge of any length reads the same.
     */
    private static final int VEIN_U = 0;
    private static final int VEIN_V = 116;

    public static LayerDefinition createBodyLayer()
    {
        var mesh = new MeshDefinition();
        var root = mesh.getRoot();

        var body = root.addOrReplaceChild("body", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-7, -10, -4, 14, 12, 8)
                        .texOffs(40, 20).addBox(-6, -9, -5, 12, 8, 1)
                        .texOffs(44, 0).addBox(-6, 2, -3.5F, 12, 5, 7)
                        .texOffs(16, 34).addBox(-3, -9, 4, 6, 10, 2),
                PartPose.ZERO);
        addBodyAdaptations(body);

        // The shell behind, whose front is the furnace, and the faceplate halves that close over it
        var head = root.addOrReplaceChild("head", CubeListBuilder.create()
                        .texOffs(90, 34).addBox(-2, -1, -2, 4, 2, 4)
                        .texOffs(82, 0).addBox(-4, -10, 0, 8, 10, 4),
                PartPose.offset(0, -10, 0));
        head.addOrReplaceChild("left_half", CubeListBuilder.create().texOffs(0, 104).addBox(0, -5, -1, 4, 10, 2), PartPose.offset(-4, -5, -3));
        head.addOrReplaceChild("right_half", CubeListBuilder.create().texOffs(0, 104).mirror().addBox(-4, -5, -1, 4, 10, 2), PartPose.offset(4, -5, -3));
        head.addOrReplaceChild("top_shutter", CubeListBuilder.create().texOffs(12, 104).addBox(-3, 0, -0.5F, 6, 3, 1), PartPose.offset(0, -10, -1.5F));
        head.addOrReplaceChild("bottom_shutter", CubeListBuilder.create().texOffs(12, 108).addBox(-3, -3, -0.5F, 6, 3, 1), PartPose.offset(0, 0, -1.5F));
        head.addOrReplaceChild("left_shutter", CubeListBuilder.create().texOffs(26, 104).addBox(0, -2, -0.5F, 2, 4, 1), PartPose.offset(-3, -5, -1.5F));
        head.addOrReplaceChild("right_shutter", CubeListBuilder.create().texOffs(26, 104).mirror().addBox(-2, -2, -0.5F, 2, 4, 1), PartPose.offset(3, -5, -1.5F));
        head.addOrReplaceChild("core", CubeListBuilder.create().texOffs(32, 104).addBox(-1.5F, -1.5F, -0.5F, 3, 3, 1), PartPose.offset(0, -5, -0.5F));

        // Crest blades swept back off the crown and the cheeks
        head.addOrReplaceChild("crest", CubeListBuilder.create().texOffs(40, 104).addBox(-0.5F, -3, 0, 1, 3, 7),
                PartPose.offsetAndRotation(0, -10, -2.5F, CREST_SWEEP, 0, 0));
        head.addOrReplaceChild("left_blade", CubeListBuilder.create().texOffs(56, 104).addBox(-0.5F, -2, 0, 1, 2, 6),
                PartPose.offsetAndRotation(-3, -9.5F, -1, BLADE_SWEEP, -BLADE_SWEEP, 0));
        head.addOrReplaceChild("right_blade", CubeListBuilder.create().texOffs(56, 104).mirror().addBox(-0.5F, -2, 0, 1, 2, 6),
                PartPose.offsetAndRotation(3, -9.5F, -1, BLADE_SWEEP, BLADE_SWEEP, 0));
        head.addOrReplaceChild("left_fin", CubeListBuilder.create().texOffs(70, 104).addBox(-0.5F, -4, 0, 1, 4, 5),
                PartPose.offsetAndRotation(-4.5F, -2, -2, 0, -FIN_SWEEP, 0));
        head.addOrReplaceChild("right_fin", CubeListBuilder.create().texOffs(70, 104).mirror().addBox(-0.5F, -4, 0, 1, 4, 5),
                PartPose.offsetAndRotation(4.5F, -2, -2, 0, FIN_SWEEP, 0));

        // Veins from the core are raised ridges, half sunk so no face lies on the surface they run along
        head.addOrReplaceChild("kernacian_1", CubeListBuilder.create().texOffs(VEIN_U, VEIN_V).addBox(-0.5F, -1, -2.5F, 1, 2, 1), PartPose.ZERO);

        addArm(root, "right_arm", -9.5F, -1);
        addArm(root, "left_arm", 9.5F, 1);
        addLeg(root, "right_leg", -3.5F, false);
        addLeg(root, "left_leg", 3.5F, true);
        return LayerDefinition.create(mesh, 128, 128);
    }

    private static void addBodyAdaptations(PartDefinition body)
    {
        body.addOrReplaceChild("blade_3", CubeListBuilder.create()
                .texOffs(40, 48).addBox(-8, -9, -3, 1, 9, 6)
                .texOffs(40, 48).mirror().addBox(7, -9, -3, 1, 9, 6), PartPose.ZERO);
        body.addOrReplaceChild("blunt_3", CubeListBuilder.create()
                .texOffs(66, 48).addBox(-0.5F, -8, 6, 1, 1, 4)
                .texOffs(66, 48).addBox(-0.5F, -5, 6, 1, 1, 4)
                .texOffs(66, 48).addBox(-0.5F, -2, 6, 1, 1, 4), PartPose.ZERO);
        // Down the chest from the neck, then out along a cross ridge and down again, split so no two ridges overlap
        body.addOrReplaceChild("kernacian_1", CubeListBuilder.create()
                .texOffs(VEIN_U, VEIN_V).addBox(-0.5F, -9, -5.5F, 1, 6, 1)
                .texOffs(VEIN_U, VEIN_V).addBox(-4.5F, -4, -5.5F, 4, 1, 1)
                .texOffs(VEIN_U, VEIN_V).addBox(0.5F, -4, -5.5F, 4, 1, 1)
                .texOffs(VEIN_U, VEIN_V).addBox(-4.5F, -3, -5.5F, 1, 2, 1)
                .texOffs(VEIN_U, VEIN_V).addBox(3.5F, -3, -5.5F, 1, 2, 1), PartPose.ZERO);
        body.addOrReplaceChild("projectile_2", CubeListBuilder.create().texOffs(90, 48).addBox(-5, -8, -7.5F, 10, 7, 1), PartPose.ZERO);
        body.addOrReplaceChild("explosion_2", CubeListBuilder.create().texOffs(38, 63).addBox(-7.5F, -10.5F, -6, 15, 4, 2), PartPose.ZERO);

        // Heat fins along the back, two more at each step
        float[][] fins = { { -1.5F, -9 }, { 1.5F, -9 }, { -3.5F, -8 }, { 3.5F, -8 }, { -5.5F, -7 }, { 5.5F, -7 } };
        for (int tier = 1; tier <= 3; tier++)
        {
            var builder = CubeListBuilder.create();
            for (int i = (tier - 1) * 2; i < tier * 2; i++)
                builder.texOffs(112, 48).addBox(fins[i][0], fins[i][1], 6, 0, 6, 4);
            body.addOrReplaceChild("fire_" + tier, builder, PartPose.ZERO);
        }

        // Rings of runes behind the head, which turn slowly
        body.addOrReplaceChild("magic_1", CubeListBuilder.create()
                .texOffs(0, 87).addBox(-6, -6, -0.5F, 12, 1, 1)
                .texOffs(0, 87).addBox(-6, 5, -0.5F, 12, 1, 1)
                .texOffs(26, 87).addBox(-6, -5, -0.5F, 1, 12, 1)
                .texOffs(26, 87).addBox(5, -6, -0.5F, 1, 12, 1), PartPose.offset(0, -15.5F, 6));
        body.addOrReplaceChild("magic_3", CubeListBuilder.create()
                .texOffs(38, 87).addBox(-8, -8, -0.5F, 16, 1, 1)
                .texOffs(38, 87).addBox(-8, 7, -0.5F, 16, 1, 1)
                .texOffs(72, 87).addBox(-8, -7, -0.5F, 1, 16, 1)
                .texOffs(72, 87).addBox(7, -8, -0.5F, 1, 16, 1), PartPose.offset(0, -15.5F, 7.5F));
    }

    /**
     * @param outward -1 for the right arm, whose outer side is toward -x, and 1 for the left
     */
    private static void addArm(PartDefinition root, String name, float x, int outward)
    {
        var builder = CubeListBuilder.create();
        if (outward > 0)
            builder.mirror();
        builder.texOffs(92, 20).addBox(-3, -3, -3.5F, 6, 5, 7)
                .texOffs(0, 34).addBox(-2, -1, -2, 4, 9, 4)
                .texOffs(106, 0).addBox(-2.5F, 8, -2.5F, 5, 9, 5)
                .texOffs(106, 34).addBox(-2, 17, -2, 4, 3, 4);
        var arm = root.addOrReplaceChild(name, builder, PartPose.offset(x, -8, 0));

        float side = outward * 3;
        arm.addOrReplaceChild("blade_1", CubeListBuilder.create().texOffs(14, 48).addBox(side - 0.5F, 9, -2.5F, 1, 7, 5), PartPose.ZERO);
        arm.addOrReplaceChild("blunt_1", CubeListBuilder.create()
                .texOffs(54, 48).addBox(-2, -6, -0.5F, 1, 3, 1)
                .texOffs(54, 48).addBox(1, -6, -0.5F, 1, 3, 1), PartPose.ZERO);
        float studFrom = outward < 0 ? -5.5F : 2.5F;
        arm.addOrReplaceChild("blunt_2", CubeListBuilder.create()
                .texOffs(58, 48).addBox(studFrom, 11, -0.5F, 3, 1, 1)
                .texOffs(58, 48).addBox(studFrom, 14, -0.5F, 3, 1, 1), PartPose.ZERO);
        arm.addOrReplaceChild("explosion_3", CubeListBuilder.create().texOffs(72, 63).addBox(-4, -4.5F, -4.5F, 8, 3, 9), PartPose.ZERO);
        // Down the front of the upper arm, then along the inner side of the forearm, clear of the blade and the rune plate
        arm.addOrReplaceChild("kernacian_2", CubeListBuilder.create()
                .texOffs(VEIN_U, VEIN_V).addBox(-0.5F, 2, -2.5F, 1, 6, 1)
                .texOffs(VEIN_U, VEIN_V).addBox(-outward * 2.5F - 0.5F, 9, -0.5F, 1, 7, 1), PartPose.ZERO);
        arm.addOrReplaceChild("magic_2", CubeListBuilder.create().texOffs(30, 87).addBox(-2, 10, -2.6F, 4, 6, 0), PartPose.ZERO);
        // The shield grows on the left arm only, the one it would raise
        if (outward > 0)
        {
            arm.addOrReplaceChild("projectile_1", CubeListBuilder.create().texOffs(76, 48).addBox(3.5F, 8, -3, 1, 8, 6), PartPose.ZERO);
            arm.addOrReplaceChild("projectile_3", CubeListBuilder.create().texOffs(0, 63).addBox(4.6F, 5, -5, 1, 14, 10), PartPose.ZERO);
        }
    }

    private static void addLeg(PartDefinition root, String name, float x, boolean mirror)
    {
        var builder = CubeListBuilder.create();
        if (mirror)
            builder.mirror();
        builder.texOffs(0, 20).addBox(-2.5F, 0, -2.5F, 5, 9, 5)
                .texOffs(20, 20).addBox(-2.5F, 9, -2.5F, 5, 9, 5)
                .texOffs(66, 20).addBox(-3, 16, -4, 6, 2, 7);
        var leg = root.addOrReplaceChild(name, builder, PartPose.offset(x, 6, 0));
        leg.addOrReplaceChild("blade_2", CubeListBuilder.create().texOffs(26, 48).addBox(-3, 1, -3.5F, 6, 6, 1), PartPose.ZERO);
        // Down the outer side of the whole leg
        float outer = mirror ? 2 : -3;
        leg.addOrReplaceChild("kernacian_3", CubeListBuilder.create()
                .texOffs(VEIN_U, VEIN_V).addBox(outer, 1, -0.5F, 1, 8, 1)
                .texOffs(VEIN_U, VEIN_V).addBox(outer, 9, -0.5F, 1, 7, 1), PartPose.ZERO);
        leg.addOrReplaceChild("explosion_1", CubeListBuilder.create().texOffs(22, 63).addBox(-3, 8, -4, 6, 4, 2), PartPose.ZERO);
    }

    @Override
    public void setupAnim(KernacianDragoonRenderState state)
    {
        super.setupAnim(state);

        head.yRot = Mth.clamp(state.yRot, -40, 40) * Mth.DEG_TO_RAD;
        head.xRot = Mth.clamp(state.xRot, -25, 25) * Mth.DEG_TO_RAD + RECOIL_PITCH * state.recoil;

        // The faceplate halves slide apart and swing back, then the shutters behind them fold away from the furnace
        leftHalf.x -= SPLIT_SLIDE * state.split;
        rightHalf.x += SPLIT_SLIDE * state.split;
        leftHalf.yRot = SPLIT_SWING * state.split;
        rightHalf.yRot = -SPLIT_SWING * state.split;
        topShutter.xRot = SHUTTER_FOLD * state.shutters;
        bottomShutter.xRot = -SHUTTER_FOLD * state.shutters;
        leftShutter.yRot = -SHUTTER_FOLD * state.shutters;
        rightShutter.yRot = SHUTTER_FOLD * state.shutters;
        float swell = 1 + state.swell * (CORE_SWELL + Mth.sin(state.ageInTicks * CORE_PULSE_RATE) * CORE_PULSE);
        core.xScale = swell;
        core.yScale = swell;
        float open = state.shutters;

        float speed = state.walkAnimationSpeed;
        float stride = Mth.cos(state.walkAnimationPos * WALK_RATE) * speed;
        rightLeg.xRot = stride * WALK_REACH;
        leftLeg.xRot = -stride * WALK_REACH;
        rightArm.xRot = -stride * ARM_SWAY + BRACE_PITCH * open;
        leftArm.xRot = stride * ARM_SWAY + BRACE_PITCH * open;
        rightArm.zRot = 0.06F;
        leftArm.zRot = -0.06F;

        // On the ground it kicks, and on a wall it strikes with its right arm instead
        float strike = kickPose(state.kick);
        rightLeg.xRot = Mth.lerp(strike * (1 - state.climb), rightLeg.xRot, kickPitch(state.kick));
        if (state.climb > 0)
            climbPose(state, strike);

        innerRing.zRot = state.ageInTicks * RING_SPIN;
        outerRing.zRot = -state.ageInTicks * RING_SPIN * 0.7F;

        for (var kind : Kind.values())
        {
            float grown = state.grown[kind.ordinal()];
            var byTier = tiers.get(kind);
            for (int tier = 0; tier < byTier.size(); tier++)
            {
                // Grows in over the hits between its threshold and the next whole hit
                float scale = Mth.clamp(grown - (DragoonAdaptation.TIERS[tier] - 1), 0, 1);
                for (var part : byTier.get(tier))
                {
                    part.visible = scale > 0;
                    part.xScale = scale;
                    part.yScale = scale;
                    part.zScale = scale;
                }
            }
        }
    }

    /**
     * Hand over hand up the face in front, each hand gripping as the other lifts, with the foot opposite the gripping hand pushing.
     */
    private void climbPose(KernacianDragoonRenderState state, float strike)
    {
        float climb = state.climb;
        float reach = Mth.sin(state.climbCycle);
        rightArm.xRot = Mth.lerp(climb, rightArm.xRot, CLIMB_ARM_PITCH + CLIMB_ARM_SWING * reach);
        leftArm.xRot = Mth.lerp(climb, leftArm.xRot, CLIMB_ARM_PITCH - CLIMB_ARM_SWING * reach);
        rightArm.zRot = Mth.lerp(climb, rightArm.zRot, -CLIMB_ARM_SPREAD);
        leftArm.zRot = Mth.lerp(climb, leftArm.zRot, CLIMB_ARM_SPREAD);
        rightArm.y += CLIMB_SHRUG * reach * climb;
        leftArm.y -= CLIMB_SHRUG * reach * climb;
        rightLeg.xRot = Mth.lerp(climb, rightLeg.xRot, CLIMB_LEG_PITCH + CLIMB_LEG_SWING * reach);
        leftLeg.xRot = Mth.lerp(climb, leftLeg.xRot, CLIMB_LEG_PITCH - CLIMB_LEG_SWING * reach);
        rightArm.xRot = Mth.lerp(strike * climb, rightArm.xRot, swipePitch(state.kick));

        float back = CLIMB_BACK * climb;
        for (var part : List.of(body, head, rightArm, leftArm, rightLeg, leftLeg))
            part.z += back;
    }

    /**
     * The arm draws back overhead during the wind-up and smashes forward over the edge on the blow.
     */
    private static float swipePitch(float ticks)
    {
        int windup = KernacianDragoon.KICK_WINDUP_TICKS;
        if (ticks < windup - 2)
            return Mth.lerp(Mth.clamp(ticks / (windup - 2), 0, 1), CLIMB_ARM_PITCH, SWIPE_WINDUP_PITCH);
        if (ticks < windup)
            return Mth.lerp((ticks - (windup - 2)) / 2, SWIPE_WINDUP_PITCH, SWIPE_PITCH);
        return SWIPE_PITCH;
    }

    /**
     * @return how much of the kick pose to show, rising through the wind-up and fading after the blow
     */
    private static float kickPose(float ticks)
    {
        if (ticks <= 0)
            return 0;
        int windup = KernacianDragoon.KICK_WINDUP_TICKS;
        if (ticks <= windup)
            return 1;
        return 1 - Mth.clamp((ticks - windup) / KernacianDragoon.KICK_FOLLOW_TICKS, 0, 1);
    }

    /**
     * The leg draws back during the wind-up and snaps forward on the blow.
     */
    private static float kickPitch(float ticks)
    {
        int windup = KernacianDragoon.KICK_WINDUP_TICKS;
        if (ticks < windup - 2)
            return KICK_WINDUP_PITCH * Mth.clamp(ticks / (windup - 2), 0, 1);
        if (ticks < windup)
            return Mth.lerp((ticks - (windup - 2)) / 2, KICK_WINDUP_PITCH, KICK_PITCH);
        return KICK_PITCH;
    }

    /**
     * How far the helm has opened and what the core is doing, at a moment in the beam or while closing after it.
     *
     * @param beamTicks    the beam's progress, 0 when not beaming
     * @param closingTicks ticks since a beam ended, while the helm shuts again
     */
    public static void open(KernacianDragoonRenderState state, int beamTicks, float closingTicks, float partialTicks)
    {
        if (beamTicks > 0)
        {
            float ticks = beamTicks + partialTicks;
            state.split = Mth.clamp(ticks / SPLIT_TICKS, 0, 1);
            state.shutters = Mth.clamp((ticks - SHUTTERS_FROM) / SHUTTERS_TICKS, 0, 1);
            state.swell = Mth.clamp((ticks - SWELL_FROM) / (KernacianDragoon.BEAM_CHARGE_TICKS - SWELL_FROM), 0, 1);
            float fired = ticks - KernacianDragoon.BEAM_CHARGE_TICKS;
            state.recoil = fired > 0 && fired < RECOIL_TICKS ? 1 - fired / RECOIL_TICKS : 0;
            return;
        }
        float shut = 1 - Mth.clamp((closingTicks + partialTicks) / KernacianDragoon.CLOSE_TICKS, 0, 1);
        state.split = shut;
        state.shutters = shut;
        state.swell = 0;
        state.recoil = 0;
    }

    private static final float SPLIT_TICKS = 5;
    private static final float SHUTTERS_FROM = 4;
    private static final float SHUTTERS_TICKS = 7;
    private static final float SWELL_FROM = 10;
    private static final float RECOIL_TICKS = 6;
}
