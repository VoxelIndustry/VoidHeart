package net.voxelindustry.voidheart.common.content.portalframe;

import com.google.gson.JsonObject;
import net.minecraft.advancement.criterion.AbstractCriterion;
import net.minecraft.advancement.criterion.AbstractCriterionConditions;
import net.minecraft.predicate.entity.AdvancementEntityPredicateDeserializer;
import net.minecraft.predicate.entity.AdvancementEntityPredicateSerializer;
import net.minecraft.predicate.entity.LootContextPredicate;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.voxelindustry.voidheart.common.content.portalframe.PortalLinkCriterion.Conditions;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class PortalLinkCriterion extends AbstractCriterion<Conditions>
{
    public static final Identifier ID = new Identifier(MODID, "portal_linked");

    protected void trigger(ServerPlayerEntity player)
    {
        trigger(player, Conditions::requirementsMet);
    }

    @Override
    protected Conditions conditionsFromJson(JsonObject obj, LootContextPredicate playerPredicate, AdvancementEntityPredicateDeserializer predicateDeserializer)
    {
        return new Conditions();
    }

    @Override
    public Identifier getId()
    {
        return ID;
    }

    public static class Conditions extends AbstractCriterionConditions
    {
        public Conditions()
        {
            super(ID, LootContextPredicate.EMPTY);
        }

        public JsonObject toJson(AdvancementEntityPredicateSerializer predicateSerializer)
        {
            return new JsonObject();
        }

        boolean requirementsMet()
        {
            return true;
        }
    }
}
