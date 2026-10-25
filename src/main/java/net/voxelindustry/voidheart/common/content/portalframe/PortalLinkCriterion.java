package net.voxelindustry.voidheart.common.content.portalframe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.triggers.Criterion;
import net.minecraft.advancements.triggers.SimpleCriterionTrigger;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.voxelindustry.voidheart.VoidHeart;

import java.util.Optional;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class PortalLinkCriterion extends SimpleCriterionTrigger<PortalLinkCriterion.Conditions>
{
    public static final Identifier ID = Identifier.fromNamespaceAndPath(MODID, "portal_linked");

    @Override
    public Codec<Conditions> codec()
    {
        return Conditions.CODEC;
    }

    public void trigger(ServerPlayer player)
    {
        trigger(player, conditions -> true);
    }

    public record Conditions(Optional<Holder<LootItemCondition>> player) implements SimpleCriterionTrigger.SimpleInstance
    {
        public static final Codec<Conditions> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                LootItemCondition.CODEC.optionalFieldOf("player").forGetter(Conditions::player)
        ).apply(instance, Conditions::new));

        public static Criterion<Conditions> linked()
        {
            return VoidHeart.PORTAL_LINK_CRITERION.createCriterion(new Conditions(Optional.empty()));
        }
    }
}
