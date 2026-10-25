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
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.client.util.GuiSprites;
import net.voxelindustry.voidheart.client.util.MathUtil;
import net.voxelindustry.voidheart.common.content.heart.VoidHeartTile;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;
import net.voxelindustry.voidheart.common.util.ProfileUtil;
import org.jspecify.annotations.Nullable;

import static java.lang.Math.abs;
import static java.lang.Math.floor;
import static java.lang.Math.sin;
import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class VoidHeartRender implements BlockEntityRenderer<VoidHeartTile, VoidHeartRender.VoidHeartRenderState>
{
    private static final Identifier PORTAL_ICONS = Identifier.fromNamespaceAndPath(MODID, "textures/hud/portal_icons.png");
    private static final int        FULL_BRIGHT  = 15728880;

    // Owner overlay layout, the two flexion slot columns are as wide as the head and its bottom lines up with the hearts
    private static final int OWNER_HEAD_X    = -40;
    private static final int OWNER_HEAD_Y    = -4;
    private static final int OWNER_HEAD_SIZE = 18;
    private static final int OWNER_TEXT_X    = OWNER_HEAD_X + OWNER_HEAD_SIZE + 2;

    public static class VoidHeartRenderState extends BlockEntityRenderState
    {
        public final ItemStackRenderState heart = new ItemStackRenderState();
        public       float                worldTime;

        public @Nullable String     ownerName;
        public @Nullable RenderType ownerSkin;

        public boolean ownerPresent;
        public float   health;
        public float   maxHealth;

        public boolean hasHeartData;
        public int     currentFlexion;
        public int     maxFlexion;
    }

    private final ItemModelResolver     itemModelResolver;
    private final PlayerSkinRenderCache playerSkinRenderCache;

    // Renderers are built before item components are bound, the stack is created on first extraction
    private ItemStack voidHeartStack;

    public VoidHeartRender(BlockEntityRendererProvider.Context context)
    {
        itemModelResolver = context.itemModelResolver();
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

        state.worldTime = voidHeart.getLevel().getGameTime() + partialTicks;
        if (voidHeartStack == null)
            voidHeartStack = new ItemStack(VoidHeartItems.VOID_HEART);
        itemModelResolver.updateForTopItem(state.heart, voidHeartStack, ItemDisplayContext.GROUND, voidHeart.getLevel(), null, 0);

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
            state.maxFlexion = heartData.maxFlexion();
        }
    }

    @Override
    public void submit(VoidHeartRenderState state, PoseStack matrices, SubmitNodeCollector collector, CameraRenderState camera)
    {
        matrices.pushPose();

        matrices.translate(0.5, 0.3, 0.5);
        matrices.rotate(MathUtil.quatFromAngleDegrees(state.worldTime, MathUtil.NEGATIVE_Y));

        float sinHeight = 6;
        float amplitude = 5;
        float scale = (float) (2 - abs(sin(state.worldTime / amplitude)) / sinHeight);
        matrices.scale(scale, scale, scale);

        state.heart.submit(matrices, collector, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);

        matrices.popPose();

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
