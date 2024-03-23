package net.voxelindustry.voidheart.data.provider;

import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricAdvancementProvider;
import net.minecraft.advancement.Advancement;
import net.minecraft.advancement.AdvancementFrame;
import net.minecraft.advancement.criterion.ChangedDimensionCriterion;
import net.minecraft.advancement.criterion.InventoryChangedCriterion;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
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
        var rootAdvancement = Advancement.Builder.create()
                .display(
                        VoidHeartBlocks.VOIDSTONE,
                        Text.literal("Outside stone"),
                        Text.literal("That's not from here"),
                        new Identifier(MODID, "textures/block/voidstone.png"),
                        AdvancementFrame.TASK,
                        true,
                        true,
                        false
                )
                .criterion("got_voidstone", InventoryChangedCriterion.Conditions.items(VoidHeartBlocks.VOIDSTONE))
                .build(consumer, MODID + "/root");

        var altarAdvancement = Advancement.Builder.create()
                .parent(rootAdvancement)
                .display(
                        VoidHeartBlocks.VOID_ALTAR,
                        Text.literal("Altar to the Void"),
                        Text.literal("Great rewards awaits great sacrifice to the outside"),
                        null,
                        AdvancementFrame.TASK,
                        true,
                        false,
                        false
                )
                .criterion("got_altar", InventoryChangedCriterion.Conditions.items(VoidHeartBlocks.VOID_ALTAR))
                .build(consumer, MODID + "/altar");

        Advancement.Builder.create()
                .parent(altarAdvancement)
                .display(
                        VoidHeartBlocks.EXPERIENCE_SKULL,
                        Text.literal("Experience Skull"),
                        Text.literal("Store experience and use it later"),
                        null,
                        AdvancementFrame.TASK,
                        true,
                        false,
                        false
                )
                .criterion("got_experience_skull", InventoryChangedCriterion.Conditions.items(VoidHeartBlocks.EXPERIENCE_SKULL))
                .build(consumer, MODID + "/experience_skull");

        Advancement.Builder.create()
                .parent(altarAdvancement)
                .display(
                        VoidHeartBlocks.INVENTORY_INSERTER,
                        Text.literal("Inventory Inserter"),
                        Text.literal("Slow automated way to move items"),
                        null,
                        AdvancementFrame.TASK,
                        true,
                        false,
                        false
                )
                .criterion("got_inventory_inserter", InventoryChangedCriterion.Conditions.items(VoidHeartBlocks.INVENTORY_INSERTER))
                .build(consumer, MODID + "/inventory_inserter");

        var amalgamAdvancement = Advancement.Builder.create()
                .parent(altarAdvancement)
                .display(
                        VoidHeartItems.VOID_AMALGAM,
                        Text.literal("Ominous amalgam"),
                        Text.literal("Eat it and offer the result to the altar"),
                        null,
                        AdvancementFrame.TASK,
                        true,
                        false,
                        false
                )
                .criterion("got_altar", InventoryChangedCriterion.Conditions.items(VoidHeartItems.VOID_AMALGAM))
                .build(consumer, MODID + "/amalgam");

        Advancement.Builder.create()
                .parent(amalgamAdvancement)
                .display(
                        VoidHeartBlocks.EYE_BOTTLE,
                        Text.literal("Eye In a Bottle"),
                        Text.literal("Cheaper way to travel to the Pocket"),
                        null,
                        AdvancementFrame.TASK,
                        true,
                        false,
                        false
                )
                .criterion("got_eye_bottle", InventoryChangedCriterion.Conditions.items(VoidHeartBlocks.EYE_BOTTLE))
                .build(consumer, MODID + "/eye_bottle");

        var pocketAdvancement = Advancement.Builder.create()
                .parent(amalgamAdvancement)
                .display(
                        VoidHeartItems.VOID_HEART,
                        Text.literal("Pocket dimension"),
                        Text.literal("The void will reward. Double click the heart to go back."),
                        null,
                        AdvancementFrame.TASK,
                        true,
                        true,
                        false
                )
                .criterion("been_to_pocket", ChangedDimensionCriterion.Conditions.to(VoidHeart.VOID_WORLD_KEY))
                .build(consumer, MODID + "/pocket");

        var shatterForgeAdvancement = Advancement.Builder.create()
                .parent(pocketAdvancement)
                .display(
                        VoidHeartBlocks.SHATTER_FORGE,
                        Text.literal("Shatter forge"),
                        Text.literal("Project matter through the Void with prejudice"),
                        null,
                        AdvancementFrame.TASK,
                        true,
                        false,
                        false
                )
                .criterion("got_shatter_forge", InventoryChangedCriterion.Conditions.items(VoidHeartBlocks.SHATTER_FORGE))
                .build(consumer, MODID + "/shatter_forge");

        var portalCoreAdvancement = Advancement.Builder.create()
                .parent(shatterForgeAdvancement)
                .display(
                        VoidHeartItems.PORTAL_CORE,
                        Text.literal("Portal Core"),
                        Text.literal("Bend the space between two dimensions"),
                        null,
                        AdvancementFrame.TASK,
                        true,
                        false,
                        false
                )
                .criterion("got_portal_core", InventoryChangedCriterion.Conditions.items(VoidHeartItems.PORTAL_CORE))
                .build(consumer, MODID + "/portal_core");

        var makePortalAdvancement = Advancement.Builder.createUntelemetered()
                .parent(portalCoreAdvancement)
                .display(
                        VoidHeartBlocks.PORTAL_FRAME_CORE,
                        Text.literal("Portal"),
                        Text.literal("The flexion is made. The spheres are entwined"),
                        null,
                        AdvancementFrame.GOAL,
                        true,
                        true,
                        false
                )
                .criterion("make_portal", new PortalLinkCriterion.Conditions())
                .build(consumer, MODID + "/make_portal");
    }
}
