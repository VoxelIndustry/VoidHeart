package net.voxelindustry.voidheart.common.content.portalframe;

import com.google.gson.JsonObject;
import net.minecraft.advancements.critereon.AbstractCriterionTriggerInstance;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.DeserializationContext;
import net.minecraft.advancements.critereon.SerializationContext;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.voxelindustry.voidheart.common.content.portalframe.PortalLinkCriterion.Conditions;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class PortalLinkCriterion extends SimpleCriterionTrigger<Conditions>
{
    public static final ResourceLocation ID = new ResourceLocation(MODID, "portal_linked");

    protected void trigger(ServerPlayer player)
    {
        trigger(player, Conditions::requirementsMet);
    }

    @Override
    protected Conditions createInstance(JsonObject obj, ContextAwarePredicate playerPredicate, DeserializationContext predicateDeserializer)
    {
        return new Conditions();
    }

    @Override
    public ResourceLocation getId()
    {
        return ID;
    }

    public static class Conditions extends AbstractCriterionTriggerInstance
    {
        public Conditions()
        {
            super(ID, ContextAwarePredicate.ANY);
        }

        public JsonObject serializeToJson(SerializationContext predicateSerializer)
        {
            return new JsonObject();
        }

        boolean requirementsMet()
        {
            return true;
        }
    }
}
