package net.voxelindustry.voidheart.common.content.sin;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * What greed repays once a stolen boon ends: its opposite, or weakness when it has none. Debts are saved with the player, so leaving and coming back does not
 * escape them. Death clears the boons and the debts with them.
 */
public final class GreedDebts
{
    private static final Map<Holder<MobEffect>, Holder<MobEffect>> OPPOSITES = Map.ofEntries(
            Map.entry(MobEffects.STRENGTH, MobEffects.WEAKNESS),
            Map.entry(MobEffects.SPEED, MobEffects.SLOWNESS),
            Map.entry(MobEffects.HASTE, MobEffects.MINING_FATIGUE),
            Map.entry(MobEffects.JUMP_BOOST, MobEffects.SLOWNESS),
            Map.entry(MobEffects.REGENERATION, MobEffects.POISON),
            Map.entry(MobEffects.RESISTANCE, MobEffects.SLOWNESS),
            Map.entry(MobEffects.ABSORPTION, MobEffects.HUNGER),
            Map.entry(MobEffects.HEALTH_BOOST, MobEffects.HUNGER),
            Map.entry(MobEffects.SATURATION, MobEffects.HUNGER),
            Map.entry(MobEffects.INVISIBILITY, MobEffects.GLOWING),
            Map.entry(MobEffects.NIGHT_VISION, MobEffects.DARKNESS),
            Map.entry(MobEffects.WATER_BREATHING, MobEffects.NAUSEA),
            Map.entry(MobEffects.LUCK, MobEffects.UNLUCK),
            Map.entry(MobEffects.SLOW_FALLING, MobEffects.SLOWNESS),
            Map.entry(MobEffects.DOLPHINS_GRACE, MobEffects.SLOWNESS),
            Map.entry(MobEffects.CONDUIT_POWER, MobEffects.MINING_FATIGUE));

    /**
     * An effect taken by greed, repaid by its opposite once it ends.
     */
    public record Debt(Holder<MobEffect> stolen, Holder<MobEffect> repaid, int ticks, int level)
    {
        public static final Codec<Debt> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                MobEffect.CODEC.fieldOf("stolen").forGetter(Debt::stolen),
                MobEffect.CODEC.fieldOf("repaid").forGetter(Debt::repaid),
                Codec.INT.fieldOf("ticks").forGetter(Debt::ticks),
                Codec.INT.fieldOf("level").forGetter(Debt::level)
        ).apply(instance, Debt::new));
    }

    public static final AttachmentType<List<Debt>> DEBTS = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(MODID, "greed_debts"),
            builder -> builder.persistent(Debt.CODEC.listOf()));

    private GreedDebts()
    {
    }

    public static void register()
    {
        // Touching the class is enough to register the attachment
    }

    public static Holder<MobEffect> opposite(Holder<MobEffect> effect)
    {
        return OPPOSITES.getOrDefault(effect, MobEffects.WEAKNESS);
    }

    public static void owe(ServerPlayer player, Debt debt)
    {
        var debts = new ArrayList<>(player.getAttachedOrElse(DEBTS, List.of()));
        debts.add(debt);
        player.setAttached(DEBTS, List.copyOf(debts));
    }

    /**
     * Repays every debt whose stolen boon has ended.
     */
    public static void tick(ServerPlayer player)
    {
        var debts = player.getAttached(DEBTS);
        if (debts == null || debts.isEmpty())
            return;

        var kept = new ArrayList<Debt>();
        for (var debt : debts)
        {
            if (player.hasEffect(debt.stolen()))
            {
                kept.add(debt);
                continue;
            }
            if (debt.ticks() > 0 && player.isAlive())
                player.addEffect(new MobEffectInstance(debt.repaid(), debt.ticks(), debt.level()));
        }

        if (kept.size() != debts.size())
        {
            if (kept.isEmpty())
                player.removeAttached(DEBTS);
            else
                player.setAttached(DEBTS, List.copyOf(kept));
        }
    }
}
