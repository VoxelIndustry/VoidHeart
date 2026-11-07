package net.voxelindustry.voidheart.common.content.oil;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

/**
 * How a brew left the athanor: with an Azurium shard beside it, a Rubrum shard, a Memoriam shard, a sin-metal flask for its vessel, or none of these.
 */
public record Brew(boolean empowered, boolean prolonged, boolean remembering, Optional<SinVessel> vessel)
{
    public static final Brew PLAIN = new Brew(false, false, false, Optional.empty());

    public Brew(boolean empowered, boolean prolonged, Optional<SinVessel> vessel)
    {
        this(empowered, prolonged, false, vessel);
    }

    /**
     * How much longer a brew lasts with Rubrum, in duration for a drink and in hits for an oil.
     */
    public static final float PROLONGED_SCALE = 1.5F;

    public static final Codec<Brew> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("empowered", false).forGetter(Brew::empowered),
            Codec.BOOL.optionalFieldOf("prolonged", false).forGetter(Brew::prolonged),
            Codec.BOOL.optionalFieldOf("remembering", false).forGetter(Brew::remembering),
            SinVessel.CODEC.optionalFieldOf("vessel").forGetter(Brew::vessel)
    ).apply(instance, Brew::new));

    public boolean isPlain()
    {
        return !empowered && !prolonged && !remembering && vessel.isEmpty();
    }

    public boolean in(SinVessel sin)
    {
        return vessel.isPresent() && vessel.get() == sin;
    }

    /**
     * @return how long a drunk brew lasts, from what it lasts plain
     */
    public int duration(int plain)
    {
        float scale = (prolonged ? PROLONGED_SCALE : 1) * vessel.map(SinVessel::durationScale).orElse(1F);
        return Math.max(1, Math.round(plain * scale));
    }

    /**
     * @return the level of a drunk brew's effect, from its plain level
     */
    public int amplifier(int plain)
    {
        return plain + (empowered ? 1 : 0) + vessel.map(SinVessel::potency).orElse(0);
    }

    /**
     * @return the hits a coat of oil lasts, from what it lasts plain
     */
    public int charges(int plain)
    {
        float scale = (prolonged ? PROLONGED_SCALE : 1) * vessel.map(SinVessel::chargeScale).orElse(1F);
        return Math.max(1, Math.round(plain * scale));
    }

    /**
     * @return how many of the product one brew gives
     */
    public int yield()
    {
        return vessel.map(SinVessel::yield).orElse(1);
    }
}
