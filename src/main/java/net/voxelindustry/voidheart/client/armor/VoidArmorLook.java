package net.voxelindustry.voidheart.client.armor;

import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.voxelindustry.voidheart.common.content.armor.ArmorBalance;
import net.voxelindustry.voidheart.common.content.armor.ArmorModule;
import net.voxelindustry.voidheart.common.content.armor.ArmorWear;
import net.voxelindustry.voidheart.common.content.armor.LoadShedding;
import net.voxelindustry.voidheart.common.content.armor.VoidArmorItem;
import net.voxelindustry.voidheart.common.content.armor.WornArmor;

/**
 * How a Void Armor frame is to be drawn this frame, worked out where its wearer, base or parked spot is known and handed to the model through its render
 * state.
 *
 * @param closed    0 standing open, 1 closed around its wearer; the plates close in stages as it rises
 * @param glow      how lit its lacing and slit are, 0 dark
 * @param tubes     battery tubes on its back, one for its own battery and one for each battery cell, up to three
 * @param litTubes  of those, how many still hold charge
 */
public record VoidArmorLook(boolean worn, float closed, float glow, boolean limp, boolean nightVision, boolean nightOn, boolean spectral, boolean spectralOn,
                            boolean exoskeleton, int tubes, int litTubes, boolean socket)
{
    public static final RenderStateDataKey<VoidArmorLook> KEY = RenderStateDataKey.create(() -> "voidheart:void_armor_look");

    public static final VoidArmorLook NONE = new VoidArmorLook(false, 0, 0, false, false, false, false, false, false, 0, 0, false);

    public static final int MOST_TUBES = 3;

    public boolean shown()
    {
        return this != NONE;
    }

    /**
     * Whether a render state is a player's inside a frame, which hides whatever else they wear.
     */
    public static boolean framed(EntityRenderState state)
    {
        return ((FabricRenderState) state).getDataOrDefault(KEY, NONE).shown();
    }

    /**
     * The frame a player has on, stepping in and out included, or {@link #NONE}.
     */
    public static VoidArmorLook of(Player player, float partialTicks)
    {
        var worn = ArmorWear.worn(player);
        if (worn == null)
            return NONE;

        float elapsed = player.level().getGameTime() - worn.since() + partialTicks;
        float closed;
        float glow;
        var stage = LoadShedding.stage(worn.charge(), VoidArmorItem.stats(worn.frame()).capacity());
        switch (worn.phase())
        {
            case STEPPING_IN ->
            {
                closed = Mth.clamp(elapsed / ArmorBalance.STEP_IN_TICKS, 0, 1);
                glow = stage.limp() ? 0 : Mth.clamp((closed - LACING_LIGHTS) / (1 - LACING_LIGHTS), 0, 1);
            }
            // Out, the lacing goes dark first and the plates open in the reverse order they closed
            case STEPPING_OUT ->
            {
                closed = 1 - Mth.clamp(elapsed / ArmorBalance.STEP_OUT_TICKS, 0, 1);
                glow = 0;
            }
            default ->
            {
                closed = 1;
                glow = stage.limp() ? 0 : 1;
            }
        }
        return of(worn.frame(), worn, closed, glow, stage.limp());
    }

    /** How far closed the frame is when its lacing starts to light. */
    public static final float LACING_LIGHTS = 0.93F;

    /**
     * A frame standing open on its base, its lacing lit by how full it is, or parked and dark.
     */
    public static VoidArmorLook standing(ItemStack frame, float glow)
    {
        return of(frame, null, 0, glow, false);
    }

    private static VoidArmorLook of(ItemStack frame, WornArmor worn, float closed, float glow, boolean limp)
    {
        var stats = VoidArmorItem.stats(frame);
        long capacity = stats.capacity();
        long charge = worn != null ? worn.charge() : VoidArmorItem.charge(frame);
        var stage = LoadShedding.stage(charge, capacity);
        int tubes = 1 + Math.min(MOST_TUBES - 1, stats.batteries());
        int lit = capacity <= 0 || charge <= 0 ? 0 : Mth.clamp((int) Math.ceil(tubes * charge / (double) capacity), 1, tubes);
        boolean nightOn = worn != null && worn.on().contains(ArmorModule.NIGHT_VISION) && stage.keeps(ArmorModule.NIGHT_VISION);
        boolean spectralOn = worn != null && worn.on().contains(ArmorModule.SPECTRAL_LENS) && stage.keeps(ArmorModule.SPECTRAL_LENS);
        return new VoidArmorLook(worn != null, closed, glow, limp, stats.nightVision(), nightOn, stats.spectralLens(), spectralOn, stats.exoskeleton(), tubes, lit,
                !VoidArmorItem.socketed(frame).isEmpty());
    }
}
