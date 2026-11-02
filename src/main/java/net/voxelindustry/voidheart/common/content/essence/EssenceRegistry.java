package net.voxelindustry.voidheart.common.content.essence;

import net.minecraft.core.RegistrationInfo;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Lifecycle;
import net.minecraft.core.DefaultedMappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.WritableRegistry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

import net.voxelindustry.voidheart.common.setup.VoidHeartTags;

import java.util.Map;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class EssenceRegistry
{
    public static final ResourceKey<Registry<EssenceType>> ESSENCE_REGISTRY_TYPE = ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath(MODID, "registry/essence"));
    public static WritableRegistry<EssenceType> ESSENCE_REGISTRY;

    private static final Map<Identifier, Identifier> LEGACY_IDS = Map.of(
            Identifier.fromNamespaceAndPath(MODID, "water"), Identifier.fromNamespaceAndPath(MODID, "stillness"),
            Identifier.fromNamespaceAndPath(MODID, "lava"), Identifier.fromNamespaceAndPath(MODID, "fiery"));

    /**
     * Codec for essence type keys, remapping ids used before the aspect rename.
     */
    public static final Codec<ResourceKey<EssenceType>> TYPE_CODEC = Identifier.CODEC.xmap(
            id -> ResourceKey.create(ESSENCE_REGISTRY_TYPE, LEGACY_IDS.getOrDefault(id, id)),
            ResourceKey::identifier);

    public static ResourceKey<EssenceType> KEY_EMPTY;
    public static ResourceKey<EssenceType> KEY_STILLNESS;
    public static ResourceKey<EssenceType> KEY_FIERY;

    public static EssenceType EMPTY;
    public static EssenceType STILLNESS;
    public static EssenceType FIERY;

    public static void bootstrap()
    {
        ESSENCE_REGISTRY = new DefaultedMappedRegistry<>(MODID + ":empty", ESSENCE_REGISTRY_TYPE, Lifecycle.stable(), false);

        KEY_EMPTY = ESSENCE_REGISTRY.getResourceKey(EMPTY = createEssenceType("empty",
                MODID + ".aspect.unattuned",
                null,
                Items.AIR)
        ).get();
        KEY_STILLNESS = ESSENCE_REGISTRY.getResourceKey(STILLNESS = createEssenceType("stillness",
                MODID + ".aspect.stillness",
                VoidHeartTags.ASPECT_STILLNESS_TAG,
                Items.WATER_BUCKET)
        ).get();
        KEY_FIERY = ESSENCE_REGISTRY.getResourceKey(FIERY = createEssenceType("fiery",
                MODID + ".aspect.fiery",
                VoidHeartTags.ASPECT_FIERY_TAG,
                Items.LAVA_BUCKET)
        ).get();
    }

    private static EssenceType createEssenceType(String identifierPath, String translatableName, TagKey<Block> consumables, Item catalyst)
    {
        var key = ResourceKey.create(ESSENCE_REGISTRY_TYPE, Identifier.fromNamespaceAndPath(MODID, identifierPath));
        var essenceType = new EssenceType(key.identifier(), translatableName, consumables, catalyst);
        ESSENCE_REGISTRY.register(key, essenceType, RegistrationInfo.BUILT_IN);
        return essenceType;
    }

    public static EssenceType getTypeFromCatalyst(ItemStack stack)
    {
        return ESSENCE_REGISTRY.stream().filter(type -> stack.is(type.catalyst())).findFirst().orElse(null);
    }
}
