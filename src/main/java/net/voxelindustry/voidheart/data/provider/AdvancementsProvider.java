package net.voxelindustry.voidheart.data.provider;

import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricAdvancementProvider;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.FrameType;
import net.minecraft.advancements.critereon.ChangeDimensionTrigger;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.common.content.portalframe.PortalLinkCriterion;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;

import java.util.function.Consumer;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class AdvancementsProvider extends FabricAdvancementProvider
{
    public AdvancementsProvider(FabricDataOutput output)
    {
        super(output);
    }

    @Override
    public void generateAdvancement(Consumer<Advancement> consumer)
    {
        var rootAdvancement = Advancement.Builder.advancement()
                .display(
                        VoidHeartBlocks.VOIDSTONE,
                        Component.literal("Outside stone"),
                        Component.literal("That's not from here"),
                        new ResourceLocation(MODID, "textures/block/voidstone.png"),
                        FrameType.TASK,
                        true,
                        true,
                        false
                )
                .addCriterion("got_voidstone", InventoryChangeTrigger.TriggerInstance.hasItems(VoidHeartBlocks.VOIDSTONE))
                .save(consumer, MODID + "/root");

        var altarAdvancement = Advancement.Builder.advancement()
                .parent(rootAdvancement)
                .display(
                        VoidHeartBlocks.VOID_ALTAR,
                        Component.literal("Altar to the Void"),
                        Component.literal("Great rewards awaits great sacrifice to the outside"),
                        null,
                        FrameType.TASK,
                        true,
                        false,
                        false
                )
                .addCriterion("got_altar", InventoryChangeTrigger.TriggerInstance.hasItems(VoidHeartBlocks.VOID_ALTAR))
                .save(consumer, MODID + "/altar");

        Advancement.Builder.advancement()
                .parent(altarAdvancement)
                .display(
                        VoidHeartBlocks.EXPERIENCE_SKULL,
                        Component.literal("Experience Skull"),
                        Component.literal("Store experience and use it later"),
                        null,
                        FrameType.TASK,
                        true,
                        false,
                        false
                )
                .addCriterion("got_experience_skull", InventoryChangeTrigger.TriggerInstance.hasItems(VoidHeartBlocks.EXPERIENCE_SKULL))
                .save(consumer, MODID + "/experience_skull");

        Advancement.Builder.advancement()
                .parent(altarAdvancement)
                .display(
                        VoidHeartBlocks.INVENTORY_INSERTER,
                        Component.literal("Inventory Inserter"),
                        Component.literal("Slow automated way to move items"),
                        null,
                        FrameType.TASK,
                        true,
                        false,
                        false
                )
                .addCriterion("got_inventory_inserter", InventoryChangeTrigger.TriggerInstance.hasItems(VoidHeartBlocks.INVENTORY_INSERTER))
                .save(consumer, MODID + "/inventory_inserter");

        var amalgamAdvancement = Advancement.Builder.advancement()
                .parent(altarAdvancement)
                .display(
                        VoidHeartItems.VOID_AMALGAM,
                        Component.literal("Ominous amalgam"),
                        Component.literal("Eat it and offer the result to the altar"),
                        null,
                        FrameType.TASK,
                        true,
                        false,
                        false
                )
                .addCriterion("got_altar", InventoryChangeTrigger.TriggerInstance.hasItems(VoidHeartItems.VOID_AMALGAM))
                .save(consumer, MODID + "/amalgam");

        Advancement.Builder.advancement()
                .parent(amalgamAdvancement)
                .display(
                        VoidHeartBlocks.EYE_BOTTLE,
                        Component.literal("Eye In a Bottle"),
                        Component.literal("Cheaper way to travel to the Pocket"),
                        null,
                        FrameType.TASK,
                        true,
                        false,
                        false
                )
                .addCriterion("got_eye_bottle", InventoryChangeTrigger.TriggerInstance.hasItems(VoidHeartBlocks.EYE_BOTTLE))
                .save(consumer, MODID + "/eye_bottle");

        var pocketAdvancement = Advancement.Builder.advancement()
                .parent(amalgamAdvancement)
                .display(
                        VoidHeartItems.VOID_HEART,
                        Component.literal("Pocket dimension"),
                        Component.literal("The void will reward. Double click the heart to go back."),
                        null,
                        FrameType.TASK,
                        true,
                        true,
                        false
                )
                .addCriterion("been_to_pocket", ChangeDimensionTrigger.TriggerInstance.changedDimensionTo(VoidHeart.VOID_WORLD_KEY))
                .save(consumer, MODID + "/pocket");

        var shatterForgeAdvancement = Advancement.Builder.advancement()
                .parent(pocketAdvancement)
                .display(
                        VoidHeartBlocks.SHATTER_FORGE,
                        Component.literal("Shatter forge"),
                        Component.literal("Project matter through the Void with prejudice"),
                        null,
                        FrameType.TASK,
                        true,
                        false,
                        false
                )
                .addCriterion("got_shatter_forge", InventoryChangeTrigger.TriggerInstance.hasItems(VoidHeartBlocks.SHATTER_FORGE))
                .save(consumer, MODID + "/shatter_forge");

        var portalCoreAdvancement = Advancement.Builder.advancement()
                .parent(shatterForgeAdvancement)
                .display(
                        VoidHeartItems.PORTAL_CORE,
                        Component.literal("Portal Core"),
                        Component.literal("Bend the space between two dimensions"),
                        null,
                        FrameType.TASK,
                        true,
                        false,
                        false
                )
                .addCriterion("got_portal_core", InventoryChangeTrigger.TriggerInstance.hasItems(VoidHeartItems.PORTAL_CORE))
                .save(consumer, MODID + "/portal_core");

        var makePortalAdvancement = Advancement.Builder.recipeAdvancement()
                .parent(portalCoreAdvancement)
                .display(
                        VoidHeartBlocks.PORTAL_FRAME_CORE,
                        Component.literal("Portal"),
                        Component.literal("The flexion is made. The spheres are entwined"),
                        null,
                        FrameType.GOAL,
                        true,
                        true,
                        false
                )
                .addCriterion("make_portal", new PortalLinkCriterion.Conditions())
                .save(consumer, MODID + "/make_portal");
    }
}
