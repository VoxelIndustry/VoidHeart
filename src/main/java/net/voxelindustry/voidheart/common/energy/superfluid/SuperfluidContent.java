package net.voxelindustry.voidheart.common.energy.superfluid;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.serialization.Codec;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Rarity;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Startup registrar for superfluid azurium, called once by EnergySetup after the pearls. A Crystal Cradle of azurium standing near a mature
 * Collapsing Rubrum Pearl still on its chain pours into it, and the azurium winds about the pearl as rings of plasma. A Tesseract draws the rings off
 * and keeps them, and a full one is the catalyst of the Void Armor's levitation module.
 * <p>
 * Superfluid has no item of its own: it only ever exists as a pearl's rings or a tesseract's fill.
 */
public final class SuperfluidContent
{
    /** The research that teaches it, which a cradle's owner must know before it pours. */
    public static final Identifier RESEARCH = Identifier.fromNamespaceAndPath(MODID, "superfluid_azurium");

    public static TesseractItem TESSERACT;

    /**
     * Units of superfluid a tesseract holds.
     */
    public static DataComponentType<Integer> SUPERFLUID;

    private SuperfluidContent()
    {
    }

    public static void register()
    {
        SUPERFLUID = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Identifier.fromNamespaceAndPath(MODID, "superfluid"),
                DataComponentType.<Integer>builder()
                        // Not bound by the capacity, so tuning it down never fails to load a saved tesseract; TesseractItem.held clamps instead
                        .persistent(Codec.intRange(0, Integer.MAX_VALUE))
                        .networkSynchronized(ByteBufCodecs.VAR_INT)
                        .build());

        TESSERACT = VoidHeartItems.registerItem("tesseract", properties -> new TesseractItem(properties.stacksTo(1).rarity(Rarity.RARE)));
        VoidHeartItems.addCreativeVariant(TESSERACT, TesseractItem::full);
    }

    /**
     * Adds /voidheart energy superfluid under /voidheart energy.
     */
    public static void registerCommands(LiteralArgumentBuilder<CommandSourceStack> energy)
    {
        SuperfluidCommands.register(energy);
    }
}
