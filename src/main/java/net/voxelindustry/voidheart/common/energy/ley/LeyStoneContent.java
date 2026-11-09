package net.voxelindustry.voidheart.common.energy.ley;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

import static net.minecraft.commands.Commands.literal;
import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Startup registrar for W4 Standing stones and wards, called once by EnergySetup: the three blocks, their tiles, the spurs as the real ley provider, and the
 * ward's corruption modifier.
 */
public final class LeyStoneContent
{
    /**
     * Plants a grower quickens: crops and saplings, as the user ruled, with the cane, cacti, berries, bamboo, kelp and mushrooms that grow the same way.
     */
    public static final TagKey<Block> GROWER_BOOSTS = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(MODID, "ley_grower_boosts"));

    public static Block LEY_STONE;
    public static Block LEY_WARD;
    public static Block LEY_GROWER;

    public static BlockEntityType<LeyStoneTile>  LEY_STONE_TILE;
    public static BlockEntityType<LeyWardTile>   LEY_WARD_TILE;
    public static BlockEntityType<LeyGrowerTile> LEY_GROWER_TILE;

    private LeyStoneContent()
    {
    }

    public static void register()
    {
        LEY_STONE = VoidHeartBlocks.registerBlock("ley_stone", LeyStoneBlock::new, stone(2F).noOcclusion().pushReaction(PushReaction.IMMOVEABLE), true);
        LEY_WARD = VoidHeartBlocks.registerBlock("ley_ward", LeyWardBlock::new, stone(1.5F).noOcclusion(), true);
        LEY_GROWER = VoidHeartBlocks.registerBlock("ley_grower", LeyGrowerBlock::new, stone(1.5F).noOcclusion(), true);

        VoidHeartTiles.registerTile(LEY_STONE_TILE = FabricBlockEntityTypeBuilder.create(LeyStoneTile::new, LEY_STONE).build(), "ley_stone");
        VoidHeartTiles.registerTile(LEY_WARD_TILE = FabricBlockEntityTypeBuilder.create(LeyWardTile::new, LEY_WARD).build(), "ley_ward");
        VoidHeartTiles.registerTile(LEY_GROWER_TILE = FabricBlockEntityTypeBuilder.create(LeyGrowerTile::new, LEY_GROWER).build(), "ley_grower");

        LeyStonePayloads.register();
        LeySpurs.register();
        LeyWards.register();
    }

    /**
     * Plain overworld stone, mined like it.
     */
    private static BlockBehaviour.Properties stone(float strength)
    {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.STONE)
                .instrument(NoteBlockInstrument.BASEDRUM)
                .requiresCorrectToolForDrops()
                .strength(strength, 6F)
                .sound(SoundType.STONE);
    }

    /**
     * Adds /voidheart energy spurs, which lists the spurs near the caller, what each asks of its line and what it gets.
     */
    public static void registerCommands(LiteralArgumentBuilder<CommandSourceStack> energy)
    {
        energy.then(literal("spurs").executes(LeyStoneContent::listSpurs));
    }

    private static final int LIST_RADIUS = 160;

    private static int listSpurs(CommandContext<CommandSourceStack> context)
    {
        var source = context.getSource();
        var spurs = LeySpurs.get(source.getLevel());
        var plan = spurs.plan();
        var here = BlockPos.containing(source.getPosition());

        int shown = 0;
        for (var root : plan.roots())
        {
            if (SpurPlan.horizontalSq(root, here) > (double) LIST_RADIUS * LIST_RADIUS)
                continue;
            var tap = plan.tap(root);
            long stones = plan.links().stream().filter(link -> link.root().equals(root)).count();
            source.sendSuccess(() -> Component.literal(String.format("Spur from %d %d %d: %d stones, %.0f blocks long, tapping line %d->%d at %.1f %.1f. Flow %.2f, "
                            + "%d machines ask %.2f, granted %.2f", root.getX(), root.getY(), root.getZ(), stones, plan.length(root), tap.fromNode(), tap.toNode(),
                    tap.x(), tap.z(), spurs.flow(root), spurs.network().consumers(root), spurs.network().claimed(root), spurs.network().granted(root))), false);
            shown++;
        }
        if (shown == 0)
            source.sendSuccess(() -> Component.literal("No spur within " + LIST_RADIUS + " blocks"), false);
        return shown;
    }
}
