package net.voxelindustry.voidheart.common.content.armor;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/**
 * The frame a player has on, and where they are in putting it on or taking it off.
 *
 * @param since the game time the phase began
 */
public record WornArmor(ItemStack frame, Phase phase, long since)
{
    public enum Phase implements StringRepresentable
    {
        /** The frame closes around the wearer, who can't act yet. */
        STEPPING_IN,
        WORN,
        /** The frame opens and lets the wearer out, who can't act meanwhile. */
        STEPPING_OUT,
        /** The wearer died and the frame's base was gone or taken, so it waits to stand beside them once they are back. */
        HOMELESS;

        public static final Codec<Phase> CODEC = StringRepresentable.fromEnum(Phase::values);

        @Override
        public String getSerializedName()
        {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final Codec<WornArmor> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ItemStack.CODEC.fieldOf("frame").forGetter(WornArmor::frame),
            Phase.CODEC.fieldOf("phase").forGetter(WornArmor::phase),
            Codec.LONG.optionalFieldOf("since", 0L).forGetter(WornArmor::since)
    ).apply(instance, WornArmor::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, WornArmor> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

    public boolean isOn()
    {
        return phase != Phase.HOMELESS;
    }

    public boolean isMoving()
    {
        return phase == Phase.STEPPING_IN || phase == Phase.STEPPING_OUT;
    }

    public WornArmor in(Phase next, long time)
    {
        return new WornArmor(frame, next, time);
    }

    @Override
    public boolean equals(Object other)
    {
        return other instanceof WornArmor worn && phase == worn.phase && since == worn.since && ItemStack.matches(frame, worn.frame);
    }

    @Override
    public int hashCode()
    {
        return 31 * (31 * ItemStack.hashItemAndComponents(frame) + phase.hashCode()) + Long.hashCode(since);
    }
}
