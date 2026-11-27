package net.voxelindustry.voidheart.client.render.mausoleum;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import org.joml.Vector3f;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * The mausoleum's green funerary fire as drawn in the world: crossed flame sprites from the block atlas, animated like a fire block's, cut out and
 * lit the same everywhere, and balls of it facing the viewer. Shared by the floating orbs, the Dullahan's crown, the wisps and whatever a wisp sets alight.
 */
public final class FuneraryFlames
{
    public static final Identifier FIRE_0 = Identifier.fromNamespaceAndPath(MODID, "block/funerary_fire_0");
    public static final Identifier FIRE_1 = Identifier.fromNamespaceAndPath(MODID, "block/funerary_fire_1");
    public static final Identifier WISP = Identifier.fromNamespaceAndPath(MODID, "block/maleficent_wisp");
    /** A ball of the fire with a small dark heart: a floating orb, and each flame of the Dullahan's crown. */
    public static final Identifier FIREBALL = Identifier.fromNamespaceAndPath(MODID, "block/funerary_fireball");

    /** Both sides of every face show, so a flame crossed with another reads from all round without a second copy. */
    public static final RenderType TYPE = RenderTypes.entityCutout(TextureAtlas.LOCATION_BLOCKS);

    private FuneraryFlames()
    {
    }

    public static TextureAtlasSprite sprite(Identifier id)
    {
        return Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS).getSprite(id);
    }

    /**
     * An upright flame turned {@code angle} radians about the vertical, standing on a point.
     *
     * @param mirror whether the sprite is flipped, so neighbouring flames don't flicker in step
     */
    public static void upright(PoseStack.Pose pose, VertexConsumer buffer, TextureAtlasSprite sprite, float x, float y, float z, float halfWidth, float height,
                               float angle, boolean mirror)
    {
        float dx = (float) Math.cos(angle) * halfWidth;
        float dz = (float) Math.sin(angle) * halfWidth;
        float u0 = mirror ? sprite.getU1() : sprite.getU0();
        float u1 = mirror ? sprite.getU0() : sprite.getU1();
        vertex(pose, buffer, x - dx, y, z - dz, u0, sprite.getV1());
        vertex(pose, buffer, x + dx, y, z + dz, u1, sprite.getV1());
        vertex(pose, buffer, x + dx, y + height, z + dz, u1, sprite.getV0());
        vertex(pose, buffer, x - dx, y + height, z - dz, u0, sprite.getV0());
    }

    /** Two upright flames crossed at right angles, the second from the other fire sprite, as a fire block stands. */
    public static void crossed(PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float z, float halfWidth, float height, float angle, boolean mirror)
    {
        upright(pose, buffer, sprite(FIRE_0), x, y, z, halfWidth, height, angle, mirror);
        upright(pose, buffer, sprite(FIRE_1), x, y, z, halfWidth, height, angle + (float) Math.PI / 2, !mirror);
    }

    /** A sprite facing the camera, given the camera's right and up in the pose's space. */
    public static void billboard(PoseStack.Pose pose, VertexConsumer buffer, TextureAtlasSprite sprite, float x, float y, float z, Vector3f right, Vector3f up,
                                 float halfWidth, float halfHeight)
    {
        float rx = right.x() * halfWidth, ry = right.y() * halfWidth, rz = right.z() * halfWidth;
        float ux = up.x() * halfHeight, uy = up.y() * halfHeight, uz = up.z() * halfHeight;
        vertex(pose, buffer, x - rx - ux, y - ry - uy, z - rz - uz, sprite.getU0(), sprite.getV1());
        vertex(pose, buffer, x + rx - ux, y + ry - uy, z + rz - uz, sprite.getU1(), sprite.getV1());
        vertex(pose, buffer, x + rx + ux, y + ry + uy, z + rz + uz, sprite.getU1(), sprite.getV0());
        vertex(pose, buffer, x - rx + ux, y - ry + uy, z - rz + uz, sprite.getU0(), sprite.getV0());
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float z, float u, float v)
    {
        buffer.addVertex(pose, x, y, z)
                .setColor(-1)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightCoordsUtil.FULL_BRIGHT)
                .setNormal(pose, 0, 1, 0);
    }
}
