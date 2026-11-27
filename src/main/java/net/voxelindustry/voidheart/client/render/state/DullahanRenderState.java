package net.voxelindustry.voidheart.client.render.state;

import net.voxelindustry.voidheart.common.content.mausoleum.Dullahan;
import org.joml.Quaternionf;

/** The Dullahan's mount is a wraith beast, so its state carries the beast's, always solid and never rearing. */
public class DullahanRenderState extends WraithBeastRenderState
{
    public Dullahan.Phase phase = Dullahan.Phase.IDLE;
    /** Ticks since its phase last changed, with the partial tick. */
    public float phaseAge;
    public boolean swingLeft;
    /** Flames gone from its crown. */
    public int released;
    /** The camera's turn, so the crown's fireballs can face it. */
    public final Quaternionf camera = new Quaternionf();
}
