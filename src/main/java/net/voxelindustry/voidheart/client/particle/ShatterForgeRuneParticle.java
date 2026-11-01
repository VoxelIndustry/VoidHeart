package net.voxelindustry.voidheart.client.particle;

import net.minecraft.client.renderer.texture.OverlayTexture;
import net.fabricmc.fabric.api.client.particle.v1.FabricSpriteSet;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.voxelindustry.voidheart.VoidHeart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.voxelindustry.voidheart.common.content.shatterforge.ShatterForgeTile;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.util.Mth;
import org.joml.Vector3f;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.stream.IntStream;

public class ShatterForgeRuneParticle extends GeometryParticle
{
    private static final Identifier HALO_SPRITE     = Identifier.fromNamespaceAndPath(VoidHeart.MODID, "rune_halo");
    private static final float      GLYPH_HALF_SIZE = 0.1875F / 2;
    private static final float      HALO_HALF_SIZE  = GLYPH_HALF_SIZE * 2.6F;
    private static final float      HALO_ALPHA      = 0.55F;
    private static final float      HALO_RED        = 0.8F;
    private static final float      HALO_GREEN      = 1F;
    private static final float      HALO_BLUE       = 0.3F;
    private static final float      HALO_DEPTH      = 0.01F;
    private static final int        FULL_BRIGHT     = 15728880;

    private final FabricSpriteSet spriteProvider;

    private final Axis axis;
    private final int[] spriteIndices;
    private final BlockPos forge;
    private final int index;

    public ShatterForgeRuneParticle(ClientLevel world,
                                    double x,
                                    double y,
                                    double z,
                                    FabricSpriteSet spriteProvider,
                                    Axis axis,
                                    BlockPos forge,
                                    int index)
    {
        super(world, x, y, z);
        this.spriteProvider = spriteProvider;
        this.axis = axis;
        this.forge = forge;
        this.index = index;
        this.setAlpha(0.95F);
        this.rCol = 0.62F;
        this.gCol = 1F;
        this.bCol = 0.68F;

        spriteIndices = IntStream.generate(() -> this.random.nextInt(12)).limit(14).toArray();
    }

    @Override
    public void render(VertexConsumer vertexConsumer, Camera camera, float tickDelta)
    {
        var cameraPos = camera.position();
        float posX = (float) (Mth.lerp(tickDelta, xo, x) - cameraPos.x());
        float posY = (float) (Mth.lerp(tickDelta, yo, y) - cameraPos.y()) - 1.31F;
        float posZ = (float) (Mth.lerp(tickDelta, zo, z) - cameraPos.z());

        var axisStep = Direction.fromAxisAndDirection(axis, AxisDirection.POSITIVE).step();
        var horizontal = new Vector3f(axisStep).cross(Direction.UP.step());
        var vertical = new Vector3f(horizontal).cross(axisStep);

        var perpendicular = (axis == Axis.X ? Direction.NORTH : Direction.WEST).step();
        var leftColumn = new Vector3f(posX, posY, posZ).add(new Vector3f(perpendicular).mul(1.35F));
        var rightColumn = new Vector3f(posX, posY, posZ).add(new Vector3f(perpendicular).mul(-1.3F));

        // The emissive pipeline does not write depth, halos are drawn first and pushed toward the monoliths to stay behind
        var halo = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.PARTICLES).getSprite(HALO_SPRITE);
        var time = level.getGameTime() + tickDelta;
        for (int i = 0; i < 7; i++)
        {
            var offsetY = i * (7 / 16F);
            // Each glyph breathes slightly out of phase with its neighbours
            var pulse = Mth.sin(time * 0.12F + i * 0.9F);
            var haloAlpha = HALO_ALPHA * (0.75F + 0.25F * pulse);
            var haloSize = HALO_HALF_SIZE * (1 + 0.08F * pulse);
            drawQuad(vertexConsumer, new Vector3f(leftColumn).add(new Vector3f(perpendicular).mul(HALO_DEPTH)).add(0, offsetY, 0), horizontal, vertical, haloSize, halo, HALO_RED, HALO_GREEN, HALO_BLUE, haloAlpha);
            drawQuad(vertexConsumer, new Vector3f(rightColumn).add(new Vector3f(perpendicular).mul(-HALO_DEPTH)).add(0, offsetY, 0), horizontal, vertical, haloSize, halo, HALO_RED, HALO_GREEN, HALO_BLUE, haloAlpha);
        }

        for (int i = 0; i < 7; i++)
        {
            var offsetY = i * (7 / 16F);
            drawQuad(vertexConsumer, new Vector3f(leftColumn).add(0, offsetY, 0), horizontal, vertical, GLYPH_HALF_SIZE, spriteProvider.get(spriteIndices[i], 12), rCol, gCol, bCol, alpha);
            drawQuad(vertexConsumer, new Vector3f(rightColumn).add(0, offsetY, 0), horizontal, vertical, GLYPH_HALF_SIZE, spriteProvider.get(spriteIndices[i + 7], 29), rCol, gCol, bCol, alpha);
        }
    }

    private void drawQuad(VertexConsumer vertexConsumer, Vector3f center, Vector3f horizontal, Vector3f vertical, float halfSize, TextureAtlasSprite sprite, float red, float green, float blue, float quadAlpha)
    {
        var h = new Vector3f(horizontal).mul(halfSize);
        var v = new Vector3f(vertical).mul(halfSize);

        vertex(vertexConsumer, new Vector3f(center).sub(h).add(v), sprite.getU0(), sprite.getV0(), red, green, blue, quadAlpha);
        vertex(vertexConsumer, new Vector3f(center).add(h).add(v), sprite.getU1(), sprite.getV0(), red, green, blue, quadAlpha);
        vertex(vertexConsumer, new Vector3f(center).add(h).sub(v), sprite.getU1(), sprite.getV1(), red, green, blue, quadAlpha);
        vertex(vertexConsumer, new Vector3f(center).sub(h).sub(v), sprite.getU0(), sprite.getV1(), red, green, blue, quadAlpha);
    }

    private void vertex(VertexConsumer vertexConsumer, Vector3f pos, float u, float v, float red, float green, float blue, float quadAlpha)
    {
        vertexConsumer.addVertex(pos.x(), pos.y(), pos.z())
                .setUv(u, v)
                .setColor(red, green, blue, quadAlpha)
                .setLight(FULL_BRIGHT)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setNormal(0, 1, 0);
    }

    @Override
    public void tick()
    {
        xo = x;
        yo = y;
        zo = z;

        // Lives as long as its pair stays lit, respawning every second used to reroll the glyphs and blink
        if (level.getBlockEntity(forge) instanceof ShatterForgeTile forgeTile && forgeTile.isRuneLit(index))
            forgeTile.runeHeartbeat(index);
        else
            remove();
    }

    @Override
    protected RenderType renderType()
    {
        return EMISSIVE;
    }

    @Override
    boolean isVisible(Frustum frustum)
    {
        // The glyph columns stand away from the particle position, a point check culls them while still on screen
        return frustum.isVisible(new AABB(x - 2, y - 1.5, z - 2, x + 2, y + 2, z + 2));
    }
}
