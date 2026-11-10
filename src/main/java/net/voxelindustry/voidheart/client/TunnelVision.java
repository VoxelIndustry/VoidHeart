package net.voxelindustry.voidheart.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.wraithveil.WallPassagePlan;
import net.voxelindustry.voidheart.common.content.wraithveil.WallPassagePlan.Plan;
import net.voxelindustry.voidheart.common.content.wraithveil.WallPassages;

/**
 * From inside a Wraith Veil tunnel, and only there, every living thing near enough shows through stone as Insight shows it, fading in as the tunnel opens and out as
 * it collapses. The tunnel's own blocks count as air when judging what is in plain view, since its bearer sees through them.
 */
public final class TunnelVision
{
    public static final float RANGE      = 16;
    public static final float FULL_RANGE = 8;
    public static final float ALPHA      = 0.45F;
    // Steps along the sight line when judging plain view, a quarter block apart
    private static final double SIGHT_STEP = 0.25;

    private TunnelVision()
    {
    }

    public static float silhouette(LivingEntity entity, float partialTicks)
    {
        var minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        if (player == null || entity == player)
            return 0;
        var plan = WallPassages.passageOf(player);
        if (plan == null || !WallPassages.isInside(player))
            return 0;
        float ticks = WallPassages.ticksOf(player) + partialTicks;
        if (!Plan.phaseAt(ticks).passable())
            return 0;

        var eye = minecraft.gameRenderer.mainCamera().position();
        var center = entity.getPosition(partialTicks).add(0, entity.getBbHeight() / 2, 0);
        double distance = eye.distanceTo(center);
        if (distance > RANGE)
            return 0;
        if (inPlainView(plan, entity, eye))
            return 0;

        float reach = 1 - Mth.clamp((float) (distance - FULL_RANGE) / (RANGE - FULL_RANGE), 0, 1);
        float open = Mth.clamp((ticks - WallPassagePlan.MARK_TICKS) / 4, 0, 1) * (1 - Plan.collapse(ticks));
        return ALPHA * reach * open * InsightSight.breath(entity);
    }

    private static boolean inPlainView(Plan plan, Entity entity, Vec3 eye)
    {
        var center = entity.position().add(0, entity.getBbHeight() / 2, 0);
        return clear(plan, entity, eye, center) && clear(plan, entity, eye, center.add(0, entity.getBbHeight() * 0.4, 0));
    }

    private static boolean clear(Plan plan, Entity entity, Vec3 from, Vec3 to)
    {
        var level = entity.level();
        double length = from.distanceTo(to);
        var step = to.subtract(from).normalize().scale(SIGHT_STEP);
        var pos = new BlockPos.MutableBlockPos();
        var at = from;
        for (double travelled = 0; travelled < length; travelled += SIGHT_STEP)
        {
            pos.set(at.x, at.y, at.z);
            if (!plan.contains(pos) && level.getBlockState(pos).isSolidRender())
                return false;
            at = at.add(step);
        }
        return true;
    }
}
