package net.voxelindustry.voidheart.client.model.armor;

import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.voxelindustry.voidheart.client.armor.VoidArmorLook;
import net.voxelindustry.voidheart.client.model.armor.ArmorSkin.Material;

import java.util.List;

import static net.voxelindustry.voidheart.VoidHeart.MODID;
import static net.voxelindustry.voidheart.client.model.armor.ArmorSkin.Material.*;

/**
 * The Void Armor as a war-frame people built in the Dragoon's image: dark void-metal plates laced together in stepped rows, the samurai way, under a kabuto
 * with a crescent crest and a menpō beneath the Dragoon's eye slit. It wraps a humanoid pose, so it walks, swings and aims as its wearer does.
 * <p>
 * Open, its chest plates stand raised like a fan, its skirt and thigh plates lifted, its shoulder guards out and its mask up. Closing, the skirt and thighs
 * fold down first, then the chest plates drop row by row from the top, the shoulder guards fall and the mask closes last. Its skirt and shoulder guards swing
 * behind the body as it walks. Limp, its head bows.
 * <p>
 * Drawn in entity cutout, which culls nothing, so no two faces share a plane: overlapping plates step by a tenth of a pixel or more on every axis. The glow
 * model is the same boxes again in {@code eyes}, showing only what is lit. Per frame: about 75 boxes, twice, for each frame in view.
 */
public class VoidArmorModel extends HumanoidModel<AvatarRenderState>
{
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(MODID, "void_armor"), "main");

    // How far each piece stands open, in radians
    private static final float OPEN_ROW    = 1.15F;
    private static final float OPEN_SKIRT  = 1.0F;
    private static final float OPEN_THIGH  = 0.9F;
    private static final float OPEN_GUARD  = 0.55F;
    private static final float OPEN_MASK   = 1.7F;
    // Closing in stages, as shares of the whole close
    private static final float SKIRT_DONE  = 0.33F;
    private static final float ROWS_DONE   = 0.73F;
    private static final float GUARDS_DONE = 0.93F;
    private static final float GUARD_TILT  = 0.12F;
    private static final float SKIRT_SWING = 0.75F;
    private static final float SIDE_SWAY   = 0.12F;
    private static final float LIMP_BOW    = 0.55F;
    /** About a pixel of rattle at a plate's lower edge. */
    private static final float HIT_JOLT    = 0.08F;
    /** The rows sit a degree or so apart, so the stack doesn't look machined. */
    private static final float[] ROW_SKEW  = { 0.02F, -0.015F, 0.025F, -0.01F };
    private static final int     ROWS      = 4;

    private final boolean glow;
    private final ModelPart mask;
    private final ModelPart nightLens;
    private final ModelPart spectralLens;
    private final ModelPart apparatusLens;
    private final List<ModelPart> rows;
    private final ModelPart skirtFront;
    private final ModelPart skirtBack;
    private final ModelPart skirtRight;
    private final ModelPart skirtLeft;
    private final ModelPart rightGuard;
    private final ModelPart leftGuard;
    private final ModelPart rightThigh;
    private final ModelPart leftThigh;
    private final List<ModelPart> pistons;
    private final List<ModelPart> tubes;

    /**
     * @param glow whether this is the glow drawn over the frame, which shows only the lenses that are on and the tubes still charged
     */
    public VoidArmorModel(ModelPart root, boolean glow)
    {
        super(root);
        this.glow = glow;
        mask = head.getChild("mask");
        nightLens = head.getChild("night_lens");
        spectralLens = head.getChild("spectral_lens");
        rows = List.of(body.getChild("row_0"), body.getChild("row_1"), body.getChild("row_2"), body.getChild("row_3"));
        apparatusLens = rows.get(1).getChild("apparatus_lens");
        skirtFront = body.getChild("skirt_front");
        skirtBack = body.getChild("skirt_back");
        skirtRight = body.getChild("skirt_right");
        skirtLeft = body.getChild("skirt_left");
        rightGuard = rightArm.getChild("guard");
        leftGuard = leftArm.getChild("guard");
        rightThigh = rightLeg.getChild("thigh");
        leftThigh = leftLeg.getChild("thigh");
        pistons = List.of(rightArm.getChild("piston"), leftArm.getChild("piston"), rightLeg.getChild("piston"), leftLeg.getChild("piston"));
        tubes = List.of(body.getChild("tube_0"), body.getChild("tube_1"), body.getChild("tube_2"));
    }

    public static LayerDefinition createLayer()
    {
        return LayerDefinition.create(build().mesh(), ArmorSkin.WIDTH, ArmorSkin.HEIGHT);
    }

    public record Built(MeshDefinition mesh, ArmorSkin skin)
    {
    }

    /**
     * In model pixels, y down and -z the front. The wearer's head spans x ±4, y -8 to 0 and z ±4, its body x ±4, y 0 to 12 and z ±2, and its arms and legs
     * 4 pixels square, so every plate stands clear of them.
     */
    public static Built build()
    {
        return build(false);
    }

    /**
     * The open frame as a layer, its plates where they stand open, so the geometry test checks the pose a parked or docked frame shows.
     */
    public static LayerDefinition createOpenLayer()
    {
        return LayerDefinition.create(build(true).mesh(), ArmorSkin.WIDTH, ArmorSkin.HEIGHT);
    }

    /**
     * @param open whether the plates stand open in the mesh itself, for the geometry test; drawn frames are posed in {@link #setupAnim}
     */
    public static Built build(boolean open)
    {
        var mesh = new MeshDefinition();
        var root = mesh.getRoot();
        var skin = new ArmorSkin();

        var head = root.addOrReplaceChild("head", helm(skin), PartPose.ZERO);
        head.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
        helmParts(head, skin, open);

        var body = root.addOrReplaceChild("body", torso(skin), PartPose.ZERO);
        torsoParts(body, skin, open);

        var rightArm = root.addOrReplaceChild("right_arm", arm(skin, "right_arm", 1), PartPose.offset(-5, 2, 0));
        armParts(rightArm, skin, "right_arm", 1, open);
        var leftArm = root.addOrReplaceChild("left_arm", arm(skin, "left_arm", -1), PartPose.offset(5, 2, 0));
        armParts(leftArm, skin, "left_arm", -1, open);

        var rightLeg = root.addOrReplaceChild("right_leg", leg(skin, "right_leg", 1), PartPose.offset(-1.9F, 12, 0));
        legParts(rightLeg, skin, "right_leg", 1, open);
        var leftLeg = root.addOrReplaceChild("left_leg", leg(skin, "left_leg", -1), PartPose.offset(1.9F, 12, 0));
        legParts(leftLeg, skin, "left_leg", -1, open);
        return new Built(mesh, skin);
    }

    // The helm

    private static CubeListBuilder helm(ArmorSkin skin)
    {
        var helm = CubeListBuilder.create();
        // The kabuto's low bowl and its brim
        skin.box(helm, "head", PLATE, -5, -10.5F, -5, 10, 4, 10);
        skin.box(helm, "head", GOLD, -5.5F, -7, -5.5F, 11, 1, 11);
        // Three stepped plates of neck guard, sweeping down and back, each a back plate and two side plates
        for (int i = 0; i < 3; i++)
        {
            float half = 5.6F + i * 0.5F;
            float top = -5.9F + i * 1.7F;
            float back = 4.7F + i * 0.7F;
            var material = i % 2 == 0 ? PLATE : LACQUER;
            skin.box(helm, "head", material, -half, top, back, 2 * half, 2, 1);
            skin.box(helm, "head", material, -half - 0.1F, top + 0.1F, -2.6F + i * 0.2F, 1, 2, back + 1.1F + 2.6F - i * 0.2F);
            skin.box(helm, "head", material, half - 0.9F, top + 0.1F, -2.6F + i * 0.2F, 1, 2, back + 1.1F + 2.6F - i * 0.2F);
        }
        // The plate over the eyes, cut by the Dragoon's slit
        skin.box(helm, "head", SLIT, -4.5F, -6.6F, -5.6F, 9, 2.7F, 1);
        return helm;
    }

    private static void helmParts(PartDefinition head, ArmorSkin skin, boolean open)
    {
        // The turned-back wings at its sides
        for (int side = -1; side <= 1; side += 2)
        {
            var wing = CubeListBuilder.create();
            skin.box(wing, "head", LACQUER, side > 0 ? -1.4F : 0, -1.6F, 0, 1.4F, 3.4F, 3.2F);
            head.addOrReplaceChild(side > 0 ? "right_wing" : "left_wing", wing, PartPose.offsetAndRotation(side * -5.6F, -6.6F, -5.2F, 0, side * 0.55F, 0));
        }

        // The menpō hangs from its top edge, so it lifts up and away to open
        var mask = CubeListBuilder.create();
        skin.box(mask, "head", MASK, -4, 0, -0.2F, 8, 3.7F, 1.1F);
        skin.box(mask, "head", PLATE, -4.7F, 2.6F, 0.3F, 9.4F, 1.8F, 3.4F);
        skin.box(mask, "head", PLATE, -0.8F, -0.1F, -0.9F, 1.6F, 1.7F, 0.8F);
        head.addOrReplaceChild("mask", mask, PartPose.offsetAndRotation(0, -4, -5.6F, open ? -OPEN_MASK : 0, 0, 0));

        // A flat crescent at the brow: a bar and two horns rising from it
        var crest = head.addOrReplaceChild("crest", skin.box(CubeListBuilder.create(), "head", GOLD, -1.3F, -1.4F, -0.4F, 2.6F, 1.4F, 0.4F),
                PartPose.offset(0, -10.4F, -5.3F));
        for (int side = -1; side <= 1; side += 2)
        {
            var horn = skin.box(CubeListBuilder.create(), "head", GOLD, side > 0 ? -4.2F : 0, -0.45F, -0.35F, 4.2F, 0.9F, 0.3F);
            crest.addOrReplaceChild(side > 0 ? "right_horn" : "left_horn", horn, PartPose.offsetAndRotation(side * -1.1F, -0.8F, 0, 0, 0, side * 0.55F));
        }

        // Night vision is a lens beside the slit, the spectral lens a paler second eye on the other side
        head.addOrReplaceChild("night_lens", skin.box(CubeListBuilder.create(), "head", LENS_NIGHT, -4.2F, -6.1F, -6.0F, 1.5F, 1.1F, 0.5F), PartPose.ZERO);
        head.addOrReplaceChild("spectral_lens", skin.box(CubeListBuilder.create(), "head", LENS_SPECTRAL, 2.7F, -6.1F, -6.0F, 1.5F, 1.1F, 0.5F), PartPose.ZERO);
    }

    // The torso

    private static CubeListBuilder torso(ArmorSkin skin)
    {
        var torso = CubeListBuilder.create();
        // What the plates are laced over, and the gorget at the neck
        skin.box(torso, "body", CORE, -4.6F, 0.1F, -2.8F, 9.2F, 11.3F, 5.6F);
        skin.box(torso, "body", PLATE, -4.3F, 0.05F, -3.2F, 8.6F, 1.4F, 6.4F);
        // The back: a plate, the Dragoon's vents between the shoulder blades and the rack the battery tubes stand in
        skin.box(torso, "body", LACQUER, -4.9F, 0.1F, 2.5F, 9.8F, 11.1F, 1);
        skin.box(torso, "body", VENT, -3, 1, 3.3F, 6, 2.6F, 0.8F);
        skin.box(torso, "body", GOLD, -3.6F, 4.4F, 3.3F, 7.2F, 5.6F, 0.6F);
        return torso;
    }

    private static void torsoParts(PartDefinition body, ArmorSkin skin, boolean open)
    {
        // The dō: rows of plates down the chest, each hung from its top edge so they rise like a fan to open
        for (int i = 0; i < ROWS; i++)
        {
            float half = 5.2F - i * 0.2F;
            var row = CubeListBuilder.create();
            skin.box(row, "body", i % 2 == 0 ? PLATE : LACQUER, -half, 0, -1.3F + i * 0.1F, 2 * half, 3, 1.4F);
            // The second row holds a gold-rimmed socket, where the socketed apparatus shows as a small lens set into it
            if (i == 1)
                skin.box(row, "body", GOLD, -1.5F, 0.5F, -1.5F, 3, 2.2F, 0.5F);
            var part = body.addOrReplaceChild("row_" + i, row, PartPose.offsetAndRotation(0, 0.2F + i * 2.7F, -2.8F, open ? -OPEN_ROW + ROW_SKEW[i] : 0, 0, 0));
            if (i == 1)
                part.addOrReplaceChild("apparatus_lens", skin.box(CubeListBuilder.create(), "body", LENS_APPARATUS, -0.9F, 0.9F, -1.65F, 1.8F, 1.4F, 0.6F),
                        PartPose.ZERO);
        }

        for (int i = 0; i < VoidArmorLook.MOST_TUBES; i++)
            body.addOrReplaceChild("tube_" + i, skin.box(CubeListBuilder.create(), "body", TUBE, -2.6F + i * 2, 4.9F, 3.6F, 1.2F, 4.6F, 1.4F), PartPose.ZERO);

        // The kusazuri: four hanging panels of two stepped plates around the hips
        var front = CubeListBuilder.create();
        skin.box(front, "body", PLATE, -4.4F, 0, -1, 8.8F, 3.2F, 1);
        skin.box(front, "body", LACQUER, -4.6F, 2.8F, -1.25F, 9.2F, 3.2F, 1);
        body.addOrReplaceChild("skirt_front", front, PartPose.offsetAndRotation(0, 11, -3, open ? -OPEN_SKIRT : 0, 0, 0));
        var back = CubeListBuilder.create();
        skin.box(back, "body", PLATE, -4.4F, 0, 0, 8.8F, 3.2F, 1);
        skin.box(back, "body", LACQUER, -4.6F, 2.8F, 0.25F, 9.2F, 3.2F, 1);
        body.addOrReplaceChild("skirt_back", back, PartPose.offsetAndRotation(0, 11, 3, open ? OPEN_SKIRT : 0, 0, 0));
        for (int side = -1; side <= 1; side += 2)
        {
            var panel = CubeListBuilder.create();
            skin.box(panel, "body", PLATE, side > 0 ? -1 : 0, 0, -2.6F, 1, 3.2F, 5.2F);
            skin.box(panel, "body", LACQUER, side > 0 ? -1.25F : 0.25F, 2.8F, -2.8F, 1, 3.2F, 5.6F);
            body.addOrReplaceChild(side > 0 ? "skirt_right" : "skirt_left", panel, PartPose.offsetAndRotation(side * -4.8F, 11, 0, 0, 0, open ? side * OPEN_SKIRT : 0));
        }
    }

    // The arms

    /**
     * @param side 1 for the right arm, which reaches out along -x, -1 for the left
     */
    private static CubeListBuilder arm(ArmorSkin skin, String part, int side)
    {
        var arm = CubeListBuilder.create();
        box(skin, arm, part, side, CORE, -3.6F, -1.6F, -2.6F, 4.8F, 5, 5.2F);
        box(skin, arm, part, side, PLATE, -3.8F, 3.6F, -2.9F, 5.2F, 4.6F, 5.8F);
        // A heavy gauntlet around the hand that holds the weapon
        box(skin, arm, part, side, LACQUER, -4, 7.9F, -3, 5.6F, 3, 6);
        return arm;
    }

    private static void armParts(PartDefinition arm, ArmorSkin skin, String part, int side, boolean open)
    {
        // The ō-sode: a broad guard of a cap and three stepped plates hanging out from the shoulder
        var guard = CubeListBuilder.create();
        box(skin, guard, part, side, PLATE, -4, -0.4F, -3.4F, 5.4F, 1, 6.8F);
        for (int i = 0; i < 3; i++)
            box(skin, guard, part, side, i % 2 == 0 ? LACQUER : PLATE, -4.2F - i * 0.35F, 0.4F + i * 2.5F, -3.6F - i * 0.1F, 1, 2.9F, 7.2F + i * 0.2F);
        arm.addOrReplaceChild("guard", guard, PartPose.offsetAndRotation(side * -1, -2.6F, 0, 0, 0, side * (GUARD_TILT + (open ? OPEN_GUARD : 0))));
        arm.addOrReplaceChild("piston", box(skin, CubeListBuilder.create(), part, side, PISTON, -4.5F, 4.2F, -0.8F, 0.8F, 3.8F, 1.6F), PartPose.ZERO);
    }

    // The legs

    private static CubeListBuilder leg(ArmorSkin skin, String part, int side)
    {
        var leg = CubeListBuilder.create();
        box(skin, leg, part, side, CORE, -2.5F, -0.2F, -2.5F, 4.3F, 6, 5);
        box(skin, leg, part, side, GOLD, -2.4F, 5.4F, -3.3F, 4.15F, 2.2F, 1.4F);
        box(skin, leg, part, side, CORE, -2.3F, 6, -2.3F, 4.1F, 5.8F, 4.6F);
        box(skin, leg, part, side, PLATE, -2.6F, 6.6F, -3, 4.45F, 4.6F, 1);
        box(skin, leg, part, side, LACQUER, -2.7F, 10.4F, -3.6F, 4.4F, 1.7F, 6);
        return leg;
    }

    private static void legParts(PartDefinition leg, ArmorSkin skin, String part, int side, boolean open)
    {
        // The haidate: two stepped thigh plates hung from the hip, so they lift with the skirt
        var thigh = CubeListBuilder.create();
        box(skin, thigh, part, side, PLATE, -2.8F, 0, -0.8F, 4.6F, 2.8F, 0.8F);
        box(skin, thigh, part, side, LACQUER, -3, 2.5F, -1, 4.75F, 2.8F, 0.8F);
        leg.addOrReplaceChild("thigh", thigh, PartPose.offsetAndRotation(0, 0.2F, -2.6F, open ? -OPEN_THIGH : 0, 0, 0));
        leg.addOrReplaceChild("piston", box(skin, CubeListBuilder.create(), part, side, PISTON, -0.8F, 6.8F, 2.2F, 1.6F, 4.2F, 0.8F), PartPose.ZERO);
    }

    /**
     * A box on the right side as given, mirrored across x on the left.
     */
    private static CubeListBuilder box(ArmorSkin skin, CubeListBuilder builder, String part, int side, Material material, float x, float y, float z, float width,
                                       float height, float depth)
    {
        return skin.box(builder, part, material, side > 0 ? x : -(x + width), y, z, width, height, depth);
    }

    // Pose

    @Override
    public void setupAnim(AvatarRenderState state)
    {
        super.setupAnim(state);
        var look = ((FabricRenderState) state).getDataOrDefault(VoidArmorLook.KEY, VoidArmorLook.NONE);
        float closed = look.closed();

        float skirt = stage(closed, 0, SKIRT_DONE);
        float guards = stage(closed, ROWS_DONE, GUARDS_DONE);
        float maskClosed = stage(closed, GUARDS_DONE, 1);

        // The legs push the skirt panels before and behind them as they swing
        float ahead = Math.max(0, -Math.min(rightLeg.xRot, leftLeg.xRot));
        float behind = Math.max(0, Math.max(rightLeg.xRot, leftLeg.xRot));
        skirtFront.xRot = -OPEN_SKIRT * (1 - skirt) - ahead * SKIRT_SWING;
        skirtBack.xRot = OPEN_SKIRT * (1 - skirt) + behind * SKIRT_SWING;
        float sway = Mth.sin(state.walkAnimationPos * 0.6662F) * state.walkAnimationSpeed * SIDE_SWAY;
        skirtRight.zRot = OPEN_SKIRT * (1 - skirt) + sway;
        skirtLeft.zRot = -OPEN_SKIRT * (1 - skirt) + sway;
        rightThigh.xRot = -OPEN_THIGH * (1 - skirt);
        leftThigh.xRot = -OPEN_THIGH * (1 - skirt);

        // The chest rows drop one after another from the top
        for (int i = 0; i < ROWS; i++)
        {
            float from = SKIRT_DONE + (ROWS_DONE - SKIRT_DONE) * i / ROWS;
            float row = stage(closed, from, from + (ROWS_DONE - SKIRT_DONE) / ROWS);
            rows.get(i).xRot = -OPEN_ROW * (1 - row) + ROW_SKEW[i];
        }

        rightGuard.zRot = GUARD_TILT + OPEN_GUARD * (1 - guards);
        leftGuard.zRot = -GUARD_TILT - OPEN_GUARD * (1 - guards);

        // A blow knocks the plates out of line for the moment the wearer flashes red
        if (state.hasRedOverlay)
        {
            for (int i = 0; i < ROWS; i++)
                rows.get(i).xRot += i % 2 == 0 ? HIT_JOLT : -HIT_JOLT;
            rightGuard.zRot += HIT_JOLT;
            leftGuard.zRot -= HIT_JOLT;
        }
        mask.xRot = -OPEN_MASK * (1 - maskClosed);

        if (look.limp())
            head.xRot += LIMP_BOW;

        nightLens.visible = look.nightVision() && (!glow || look.nightOn());
        spectralLens.visible = look.spectral() && (!glow || look.spectralOn());
        apparatusLens.visible = look.socket();
        for (var piston : pistons)
            piston.visible = look.exoskeleton() && !glow;
        for (int i = 0; i < tubes.size(); i++)
            tubes.get(i).visible = i < (glow ? look.litTubes() : look.tubes());
    }

    /**
     * How far one stage of closing has gone, eased so each piece settles into place.
     */
    private static float stage(float closed, float from, float to)
    {
        float t = Mth.clamp((closed - from) / (to - from), 0, 1);
        return t * t * (3 - 2 * t);
    }
}
