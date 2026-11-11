package net.voxelindustry.voidheart.client.render.state;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public class WraithScarabRenderState extends CrawlerRenderState
{
    // Ticks since it last landed, which flares its wing cases
    public float sinceHop;
    public final List<WraithScarabRenderState> afterimages = new ArrayList<>();
    // For an afterimage only: where it stands from the scarab, and how faint it has grown
    public Vec3 offset = Vec3.ZERO;
    public float fade;
}
