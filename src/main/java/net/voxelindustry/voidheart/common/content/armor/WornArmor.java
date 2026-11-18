package net.voxelindustry.voidheart.common.content.armor;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import net.voxelindustry.voidheart.common.energy.storage.RubrumContent;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The frame a player has on, and where they are in putting it on or taking it off. While worn, the frame's own charge is what the wearer has left.
 *
 * @param since the game time the phase began
 * @param on    the sights the wearer has switched on; passive modules aren't listed
 */
public record WornArmor(ItemStack frame, Phase phase, long since, Set<ArmorModule> on)
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
            Codec.LONG.optionalFieldOf("since", 0L).forGetter(WornArmor::since),
            ArmorModule.CODEC.listOf().optionalFieldOf("on", List.of()).forGetter(worn -> List.copyOf(worn.on()))
    ).apply(instance, (frame, phase, since, on) -> new WornArmor(frame, phase, since, Set.copyOf(on))));

    public static final StreamCodec<RegistryFriendlyByteBuf, WornArmor> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

    public WornArmor
    {
        on = on.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(on));
    }

    public WornArmor(ItemStack frame, Phase phase, long since)
    {
        this(frame, phase, since, Set.of());
    }

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
        return new WornArmor(frame, next, time, on);
    }

    public long charge()
    {
        return VoidArmorItem.charge(frame);
    }

    public WornArmor withCharge(long charge)
    {
        var copy = frame.copy();
        copy.set(RubrumContent.VOID_CHARGE, Math.max(0, charge));
        return new WornArmor(copy, phase, since, on);
    }

    public WornArmor toggled(ArmorModule module)
    {
        var next = on.isEmpty() ? EnumSet.noneOf(ArmorModule.class) : EnumSet.copyOf(on);
        if (!next.remove(module))
            next.add(module);
        return new WornArmor(frame, phase, since, next);
    }

    @Override
    public boolean equals(Object other)
    {
        return other instanceof WornArmor worn && phase == worn.phase && since == worn.since && on.equals(worn.on) && ItemStack.matches(frame, worn.frame);
    }

    @Override
    public int hashCode()
    {
        return 31 * (31 * (31 * ItemStack.hashItemAndComponents(frame) + phase.hashCode()) + Long.hashCode(since)) + on.hashCode();
    }
}
