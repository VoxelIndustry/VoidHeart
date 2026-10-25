package net.voxelindustry.voidheart.common.content.essence;

import com.mojang.serialization.Lifecycle;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.MutableRegistry;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.SimpleDefaultedRegistry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import net.voxelindustry.voidheart.common.setup.VoidHeartTags;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class EssenceRegistry
{
    public static final RegistryKey<Registry<EssenceType>> ESSENCE_REGISTRY_TYPE = RegistryKey.ofRegistry(new Identifier(MODID, "registry/essence"));
    public static MutableRegistry<EssenceType> ESSENCE_REGISTRY;

    public static RegistryKey<EssenceType> KEY_EMPTY;
    public static RegistryKey<EssenceType> KEY_WATER;
    public static RegistryKey<EssenceType> KEY_LAVA;

    public static EssenceType EMPTY;
    public static EssenceType WATER;
    public static EssenceType LAVA;

    public static void bootstrap()
    {
        ESSENCE_REGISTRY = new SimpleDefaultedRegistry<>(MODID + ":empty", ESSENCE_REGISTRY_TYPE, Lifecycle.stable(), false);

        KEY_EMPTY = ESSENCE_REGISTRY.getKey(EMPTY = createEssenceType("empty",
                MODID + ".essence.empty",
                null,
                ItemStack.EMPTY)
        ).get();
        KEY_WATER = ESSENCE_REGISTRY.getKey(WATER = createEssenceType("water",
                "block.minecraft.water",
                VoidHeartTags.ESSENCE_WATER_TAG,
                new ItemStack(Items.WATER_BUCKET))
        ).get();
        KEY_LAVA = ESSENCE_REGISTRY.getKey(LAVA = createEssenceType("lava",
                "block.minecraft.lava",
                VoidHeartTags.ESSENCE_LAVA_TAG,
                new ItemStack(Items.LAVA_BUCKET))
        ).get();
    }

    private static EssenceType createEssenceType(String identifierPath, String translatableName, TagKey<Block> consumables, ItemStack catalyst)
    {
        var key = RegistryKey.of(ESSENCE_REGISTRY_TYPE, new Identifier(MODID, identifierPath));
        var essenceType = new EssenceType(key.getValue(), translatableName, consumables, catalyst);
        ESSENCE_REGISTRY.add(key, essenceType, Lifecycle.stable());
        return essenceType;
    }

    public static EssenceType getTypeFromCatalyst(ItemStack stack)
    {
        return ESSENCE_REGISTRY.stream().filter(type -> ItemStack.areEqual(stack, type.catalyst())).findFirst().orElse(null);
    }
}
