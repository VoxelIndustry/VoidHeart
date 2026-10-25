package net.voxelindustry.voidheart.common.content.essence;

import net.minecraft.core.RegistrationInfo;
import com.mojang.serialization.Lifecycle;
import net.minecraft.core.DefaultedMappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.WritableRegistry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.voxelindustry.voidheart.common.setup.VoidHeartTags;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class EssenceRegistry
{
    public static final ResourceKey<Registry<EssenceType>> ESSENCE_REGISTRY_TYPE = ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath(MODID, "registry/essence"));
    public static WritableRegistry<EssenceType> ESSENCE_REGISTRY;

    public static ResourceKey<EssenceType> KEY_EMPTY;
    public static ResourceKey<EssenceType> KEY_WATER;
    public static ResourceKey<EssenceType> KEY_LAVA;

    public static EssenceType EMPTY;
    public static EssenceType WATER;
    public static EssenceType LAVA;

    public static void bootstrap()
    {
        ESSENCE_REGISTRY = new DefaultedMappedRegistry<>(MODID + ":empty", ESSENCE_REGISTRY_TYPE, Lifecycle.stable(), false);

        KEY_EMPTY = ESSENCE_REGISTRY.getResourceKey(EMPTY = createEssenceType("empty",
                MODID + ".essence.empty",
                null,
                ItemStack.EMPTY)
        ).get();
        KEY_WATER = ESSENCE_REGISTRY.getResourceKey(WATER = createEssenceType("water",
                "block.minecraft.water",
                VoidHeartTags.ESSENCE_WATER_TAG,
                new ItemStack(Items.WATER_BUCKET))
        ).get();
        KEY_LAVA = ESSENCE_REGISTRY.getResourceKey(LAVA = createEssenceType("lava",
                "block.minecraft.lava",
                VoidHeartTags.ESSENCE_LAVA_TAG,
                new ItemStack(Items.LAVA_BUCKET))
        ).get();
    }

    private static EssenceType createEssenceType(String identifierPath, String translatableName, TagKey<Block> consumables, ItemStack catalyst)
    {
        var key = ResourceKey.create(ESSENCE_REGISTRY_TYPE, Identifier.fromNamespaceAndPath(MODID, identifierPath));
        var essenceType = new EssenceType(key.identifier(), translatableName, consumables, catalyst);
        ESSENCE_REGISTRY.register(key, essenceType, RegistrationInfo.BUILT_IN);
        return essenceType;
    }

    public static EssenceType getTypeFromCatalyst(ItemStack stack)
    {
        return ESSENCE_REGISTRY.stream().filter(type -> ItemStack.matches(stack, type.catalyst())).findFirst().orElse(null);
    }
}
