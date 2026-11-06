package net.voxelindustry.voidheart.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.armillary.ArmillaryRiftTile;
import net.voxelindustry.voidheart.common.world.daedalus.DaedalusTheme;
import net.voxelindustry.voidheart.common.world.daedalus.DistrictPos;
import net.voxelindustry.voidheart.common.world.daedalus.VoidDaedalus;
import net.voxelindustry.voidheart.common.world.dungeon.DungeonAddress;
import org.jspecify.annotations.Nullable;

public class ArmillaryRiftRender implements BlockEntityRenderer<ArmillaryRiftTile, ArmillaryRiftRender.State>
{
    private static final float RADIUS        = 0.62F;
    private static final Vec3  CENTER        = new Vec3(0.5, 0.5, 0.5);
    private static final int   VIEW_DISTANCE = 96;

    public static class State extends BlockEntityRenderState
    {
        Vec3  toCamera = Vec3.ZERO;
        int   rgb;
        float time;
    }

    public ArmillaryRiftRender(BlockEntityRendererProvider.Context context)
    {
    }

    @Override
    public State createRenderState()
    {
        return new State();
    }

    @Override
    public void extractRenderState(ArmillaryRiftTile rift, State state, float partialTicks, Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress)
    {
        BlockEntityRenderer.super.extractRenderState(rift, state, partialTicks, cameraPosition, breakProgress);

        var pos = rift.getBlockPos();
        if (rift.getLevel().dimension().equals(VoidDaedalus.WORLD_KEY))
            state.rgb = DaedalusTheme.at(DistrictPos.at(pos.getX(), pos.getZ())).color();
        else
        {
            var address = DungeonAddress.at(pos.getX(), pos.getZ());
            state.rgb = ArmillaryPortalRender.themeColor(address == null ? null : address.theme());
        }
        state.toCamera = cameraPosition.subtract(Vec3.atCenterOf(pos));
        state.time = ArmillaryPortalRender.animationTime(rift.getLevel().getGameTime(), partialTicks);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera)
    {
        ArmillaryPortalRender.submit(poseStack, collector, CENTER, state.toCamera, RADIUS, 1, state.rgb, state.time);
    }

    @Override
    public int getViewDistance()
    {
        return VIEW_DISTANCE;
    }
}
