package net.voxelindustry.voidheart.data.provider;

import java.util.Optional;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.core.ClientAsset;
import net.minecraft.advancements.triggers.ChangeDimensionTrigger;
import net.minecraft.advancements.triggers.InventoryChangeTrigger;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.AdvancementHolder;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricAdvancementProvider;
import net.minecraft.advancements.Advancement;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.common.content.portalframe.PortalLinkCriterion;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;

import java.util.function.Consumer;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class AdvancementsProvider extends FabricAdvancementProvider
{
    public AdvancementsProvider(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registryLookup)
    {
        super(output, registryLookup);
    }

    @Override
    public void generateAdvancement(HolderLookup.Provider registryLookup, Consumer<AdvancementHolder> consumer)
    {
        var rootAdvancement = Advancement.Builder.advancement()
                .display(new DisplayInfo(
                        new ItemStackTemplate(VoidHeartBlocks.VOIDSTONE.asItem()),
                        Component.literal("Outside stone"),
                        Component.literal("That's not from here"),
                        Optional.of(new ClientAsset.ResourceTexture(Identifier.fromNamespaceAndPath(MODID, "block/voidstone"))),
                        AdvancementType.TASK,
                        true,
                        true,
                        false
                ))
                .addCriterion("got_voidstone", InventoryChangeTrigger.TriggerInstance.hasItems(VoidHeartBlocks.VOIDSTONE))
                .build(Identifier.fromNamespaceAndPath(MODID, "root"));
        consumer.accept(rootAdvancement);

        var altarAdvancement = Advancement.Builder.advancement()
                .parent(rootAdvancement)
                .display(
                        VoidHeartBlocks.VOID_ALTAR.asItem(),
                        Component.literal("Altar to the Void"),                        Component.literal("Great rewards awaits great sacrifice to the outside"),
                        AdvancementType.TASK,
                        true,
                        false,
                        false
                )
                .addCriterion("got_altar", InventoryChangeTrigger.TriggerInstance.hasItems(VoidHeartBlocks.VOID_ALTAR))
                .build(Identifier.fromNamespaceAndPath(MODID, "altar"));
        consumer.accept(altarAdvancement);

        var experienceSkullAdvancement = Advancement.Builder.advancement()
                .parent(altarAdvancement)
                .display(
                        VoidHeartBlocks.EXPERIENCE_SKULL.asItem(),
                        Component.literal("Experience Skull"),                        Component.literal("Store experience and use it later"),
                        AdvancementType.TASK,
                        true,
                        false,
                        false
                )
                .addCriterion("got_experience_skull", InventoryChangeTrigger.TriggerInstance.hasItems(VoidHeartBlocks.EXPERIENCE_SKULL))
                .build(Identifier.fromNamespaceAndPath(MODID, "experience_skull"));
        consumer.accept(experienceSkullAdvancement);

        var inventoryInserterAdvancement = Advancement.Builder.advancement()
                .parent(altarAdvancement)
                .display(
                        VoidHeartBlocks.INVENTORY_INSERTER.asItem(),
                        Component.literal("Inventory Inserter"),                        Component.literal("Slow automated way to move items"),
                        AdvancementType.TASK,
                        true,
                        false,
                        false
                )
                .addCriterion("got_inventory_inserter", InventoryChangeTrigger.TriggerInstance.hasItems(VoidHeartBlocks.INVENTORY_INSERTER))
                .build(Identifier.fromNamespaceAndPath(MODID, "inventory_inserter"));
        consumer.accept(inventoryInserterAdvancement);

        var amalgamAdvancement = Advancement.Builder.advancement()
                .parent(altarAdvancement)
                .display(
                        VoidHeartItems.VOID_AMALGAM.asItem(),
                        Component.literal("Ominous amalgam"),                        Component.literal("Eat it and offer the result to the altar"),
                        AdvancementType.TASK,
                        true,
                        false,
                        false
                )
                .addCriterion("got_altar", InventoryChangeTrigger.TriggerInstance.hasItems(VoidHeartItems.VOID_AMALGAM))
                .build(Identifier.fromNamespaceAndPath(MODID, "amalgam"));
        consumer.accept(amalgamAdvancement);

        var eyeBottleAdvancement = Advancement.Builder.advancement()
                .parent(amalgamAdvancement)
                .display(
                        VoidHeartBlocks.EYE_BOTTLE.asItem(),
                        Component.literal("Eye In a Bottle"),                        Component.literal("Cheaper way to travel to the Pocket"),
                        AdvancementType.TASK,
                        true,
                        false,
                        false
                )
                .addCriterion("got_eye_bottle", InventoryChangeTrigger.TriggerInstance.hasItems(VoidHeartBlocks.EYE_BOTTLE))
                .build(Identifier.fromNamespaceAndPath(MODID, "eye_bottle"));
        consumer.accept(eyeBottleAdvancement);

        var pocketAdvancement = Advancement.Builder.advancement()
                .parent(amalgamAdvancement)
                .display(
                        VoidHeartItems.VOID_HEART.asItem(),
                        Component.literal("Pocket dimension"),                        Component.literal("The void will reward. Double click the heart to go back."),
                        AdvancementType.TASK,
                        true,
                        true,
                        false
                )
                .addCriterion("been_to_pocket", ChangeDimensionTrigger.TriggerInstance.changedDimensionTo(VoidHeart.VOID_WORLD_KEY))
                .build(Identifier.fromNamespaceAndPath(MODID, "pocket"));
        consumer.accept(pocketAdvancement);

        var shatterForgeAdvancement = Advancement.Builder.advancement()
                .parent(pocketAdvancement)
                .display(
                        VoidHeartBlocks.SHATTER_FORGE.asItem(),
                        Component.literal("Shatter forge"),                        Component.literal("Project matter through the Void with prejudice"),
                        AdvancementType.TASK,
                        true,
                        false,
                        false
                )
                .addCriterion("got_shatter_forge", InventoryChangeTrigger.TriggerInstance.hasItems(VoidHeartBlocks.SHATTER_FORGE))
                .build(Identifier.fromNamespaceAndPath(MODID, "shatter_forge"));
        consumer.accept(shatterForgeAdvancement);

        var portalCoreAdvancement = Advancement.Builder.advancement()
                .parent(shatterForgeAdvancement)
                .display(
                        VoidHeartItems.PORTAL_CORE.asItem(),
                        Component.literal("Portal Core"),                        Component.literal("Bend the space between two dimensions"),
                        AdvancementType.TASK,
                        true,
                        false,
                        false
                )
                .addCriterion("got_portal_core", InventoryChangeTrigger.TriggerInstance.hasItems(VoidHeartItems.PORTAL_CORE))
                .build(Identifier.fromNamespaceAndPath(MODID, "portal_core"));
        consumer.accept(portalCoreAdvancement);

        var makePortalAdvancement = Advancement.Builder.recipeAdvancement()
                .parent(portalCoreAdvancement)
                .display(
                        VoidHeartBlocks.PORTAL_FRAME_CORE.asItem(),
                        Component.literal("Portal"),                        Component.literal("The flexion is made. The spheres are entwined"),
                        AdvancementType.GOAL,
                        true,
                        true,
                        false
                )
                .addCriterion("make_portal", PortalLinkCriterion.Conditions.linked())
                .build(Identifier.fromNamespaceAndPath(MODID, "make_portal"));
        consumer.accept(makePortalAdvancement);
    }
}
