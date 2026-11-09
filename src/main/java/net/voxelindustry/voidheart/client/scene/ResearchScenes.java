package net.voxelindustry.voidheart.client.scene;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.client.render.ArmillaryPortalRender;
import net.voxelindustry.voidheart.client.render.ForgePreviewRender;
import net.voxelindustry.voidheart.common.content.armillary.ArmillaryCoreBlock;
import net.voxelindustry.voidheart.common.content.armillary.ArmillaryLayout;
import net.voxelindustry.voidheart.common.content.armillary.ArmillaryMotion;
import net.voxelindustry.voidheart.common.content.shatterforge.ShatterForgeLayout;
import net.voxelindustry.voidheart.common.energy.grid.VoidGridContent;
import net.voxelindustry.voidheart.common.world.dungeon.DungeonTheme;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * The scenes a finished research can show. The scripts name real blocks and a real recipe: the Shatter Forge item at the altar, and gold ingots at the forge.
 * They are built on first use since blocks only exist once the game has registered them.
 */
public final class ResearchScenes
{
    public static final List<BlockPos> ALTAR_PILLARS = List.of(
            new BlockPos(-1, 0, 3), new BlockPos(1, 0, 3), new BlockPos(3, 0, 1), new BlockPos(3, 0, -1),
            new BlockPos(1, 0, -3), new BlockPos(-1, 0, -3), new BlockPos(-3, 0, -1), new BlockPos(-3, 0, 1));

    public static final int FORGE_TRACK_PAIRS = 2;

    private static final Identifier ALTAR_RESEARCH = Identifier.fromNamespaceAndPath(MODID, "altar_rite");
    private static final Identifier FORGE_RESEARCH = Identifier.fromNamespaceAndPath(MODID, "shatter_forge");
    private static final Identifier ATHANOR_RESEARCH = Identifier.fromNamespaceAndPath(MODID, "tria_prima");
    private static final Identifier ARMILLARY_RESEARCH = Identifier.fromNamespaceAndPath(MODID, "armillary");

    /** Where the armillary scene's pylon stands, its small bundle's field reaching the core with room to spare. */
    public static final BlockPos ARMILLARY_PYLON = new BlockPos(-4, 0, 2);

    // Not a block of the mod: pale, so that the void's dark stone and what floats above it stand out from the ground
    private static final Block FLOOR = Blocks.SMOOTH_STONE;

    private static final float ITEM_HEIGHT = 1.25F;
    private static final Vec3  ALTAR_TOP   = new Vec3(0.5, 1.25, 0.5);
    private static final Vec3  ABOVE_ALTAR = new Vec3(0.5, 2.3, 0.5);

    private static final Map<Identifier, Scene> BUILT = new HashMap<>();

    private ResearchScenes()
    {
    }

    public static boolean has(Identifier research)
    {
        return research.equals(ALTAR_RESEARCH) || research.equals(FORGE_RESEARCH) || research.equals(ATHANOR_RESEARCH) || research.equals(ARMILLARY_RESEARCH);
    }

    /**
     * The research whose scene shows this item at work. The forge's parts are shown in the forge's scene, whichever research teaches them.
     */
    public static Optional<Identifier> forItem(Identifier item)
    {
        return switch (item.getPath())
        {
            case "void_altar" -> Optional.of(ALTAR_RESEARCH);
            case "shatter_forge", "void_monolith", "void_conduit" -> Optional.of(FORGE_RESEARCH);
            case "athanor", "alembic", "crucible", "aspect_intake" -> Optional.of(ATHANOR_RESEARCH);
            case "armillary_core" -> Optional.of(ARMILLARY_RESEARCH);
            default -> Optional.empty();
        };
    }

    public static Optional<Scene> of(Identifier research)
    {
        if (!has(research))
            return Optional.empty();
        return Optional.of(BUILT.computeIfAbsent(research, id ->
        {
            if (id.equals(ALTAR_RESEARCH))
                return altar();
            if (id.equals(ARMILLARY_RESEARCH))
                return armillary();
            return id.equals(FORGE_RESEARCH) ? forge() : athanor();
        }));
    }

    private static BlockState lit(BlockState state)
    {
        return state.hasProperty(BlockStateProperties.LIT) ? state.setValue(BlockStateProperties.LIT, true) : state;
    }

    private static Vec3 above(BlockPos pos, float height)
    {
        return new Vec3(pos.getX() + 0.5, pos.getY() + height, pos.getZ() + 0.5);
    }

    private static Identifier id(String path)
    {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }

    private static Scene altar()
    {
        var altar = BlockPos.ZERO;
        var altarState = VoidHeartBlocks.VOID_ALTAR.defaultBlockState();
        var pillarState = VoidHeartBlocks.VOID_PILLAR.defaultBlockState();

        var close = new SceneView(35, 32, 5.5F, 0.5F, 0.6F, 0.5F);
        var whole = new SceneView(35, 36, 11, 0.5F, 0.4F, 0.5F);
        var rite = new SceneView(62, 26, 11, 0.5F, 1.2F, 0.5F);

        var scene = Scene.builder(id("void_altar"))
                .floor(-4, -4, 4, 4, -1, FLOOR.defaultBlockState());

        scene.step("altar", 50, close, above(altar, 1));
        scene.place(10, altar, altarState);

        scene.step("pillars", 90, whole, above(ALTAR_PILLARS.get(3), 1));
        for (int index = 0; index < ALTAR_PILLARS.size(); index++)
        {
            scene.place(14 + index * 7, ALTAR_PILLARS.get(index), pillarState);
            scene.highlight(14 + index * 7, scene.next(), ALTAR_PILLARS.get(index));
        }

        scene.use("offerings", 90, whole, above(ALTAR_PILLARS.get(3), 1.2F));
        var offerings = new Item[] { VoidHeartItems.OBSIDIAN_SHARD, VoidHeartItems.OBSIDIAN_SHARD, VoidHeartItems.OBSIDIAN_SHARD, VoidHeartItems.ENDER_SHARD,
                VoidHeartItems.OBSIDIAN_SHARD, VoidHeartItems.OBSIDIAN_SHARD, VoidHeartItems.OBSIDIAN_SHARD, VoidHeartItems.ENDER_SHARD };
        var riteStep = new Scene.Moment(4, 0);
        for (int index = 0; index < ALTAR_PILLARS.size(); index++)
        {
            var pillar = ALTAR_PILLARS.get(index);
            scene.flight(10 + index * 8, 10, new Scene.Moment(4, 45 + index * 16), new ItemStack(offerings[index]), above(pillar, 2.2F), above(pillar, ITEM_HEIGHT));
        }

        scene.use("catalyst", 70, close, ABOVE_ALTAR);
        var doneStep = new Scene.Moment(5, 0);
        scene.flight(14, 14, doneStep, new ItemStack(Items.ANVIL), new Vec3(0.5, 3, 0.5), ALTAR_TOP);

        scene.step("rite", 190, rite, new Vec3(0.5, 3, 0.5));
        scene.place(0, altar, lit(altarState));
        ALTAR_PILLARS.forEach(pillar -> scene.place(0, pillar, lit(pillarState)));
        scene.voidCube(0, doneStep);
        for (int index = 0; index < ALTAR_PILLARS.size(); index++)
        {
            var pillar = ALTAR_PILLARS.get(index);
            scene.flight(45 + index * 16, 14, scene.at(45 + index * 16 + 14), new ItemStack(offerings[index]), above(pillar, ITEM_HEIGHT), new Vec3(0.5, 3, 0.5));
        }

        scene.step("result", 80, close, ABOVE_ALTAR);
        scene.place(0, altar, altarState);
        ALTAR_PILLARS.forEach(pillar -> scene.place(0, pillar, pillarState));
        scene.flight(30, 25, Scene.Moment.NEVER, new ItemStack(VoidHeartBlocks.SHATTER_FORGE), new Vec3(0.5, 3, 0.5), ALTAR_TOP);

        return scene.build();
    }

    private static Scene forge()
    {
        var forge = BlockPos.ZERO;
        var track = Direction.NORTH;
        var forgeState = VoidHeartBlocks.SHATTER_FORGE.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_AXIS, Direction.Axis.Z);
        var expected = ShatterForgeLayout.expected(forge, track, FORGE_TRACK_PAIRS);

        var conduits = new HashSet<BlockPos>();
        expected.stream().filter(block -> block.part() == ShatterForgeLayout.Part.CONDUIT).forEach(block -> conduits.add(block.pos()));

        var source = ShatterForgeLayout.endSlot(forge, track, FORGE_TRACK_PAIRS);

        var close = new SceneView(158, 34, 6, 0.5F, 0.7F, 0.5F);
        var whole = new SceneView(158, 38, 12, 0.5F, 0.4F, -3F);
        var craft = new SceneView(172, 36, 11, 0.5F, 0.8F, -3F);
        var end = new SceneView(158, 34, 6, 0.5F, 1, 0.5F);

        var scene = Scene.builder(id("shatter_forge"))
                .floor(-3, -8, 3, 1, -2, FLOOR.defaultBlockState());

        scene.step("forge", 50, close, above(forge, 0.9F));
        scene.place(10, forge, forgeState);

        scene.use("preview", 110, whole, above(forge, 0.9F));
        expected.forEach(block -> scene.ghost(20, scene.next(), block.pos(), ForgePreviewRender.ghostState(block, conduits)));

        scene.step("conduits", 110, whole, new Vec3(2.5, 0, -2.5));
        int conduitIndex = 0;
        for (var block : expected)
        {
            if (block.part() == ShatterForgeLayout.Part.CONDUIT)
                scene.place(10 + conduitIndex++ * 4, block.pos(), ForgePreviewRender.ghostState(block, conduits));
        }

        scene.step("monoliths", 110, whole, new Vec3(2.5, 3, -6 + 0.5));
        int monolithIndex = 0;
        for (var block : expected)
        {
            if (block.part() != ShatterForgeLayout.Part.CONDUIT && block.part() != ShatterForgeLayout.Part.PILLAR)
                scene.place(10 + monolithIndex++ * 5, block.pos(), ForgePreviewRender.ghostState(block, conduits));
        }

        scene.step("source", 50, whole, above(source, 1));
        scene.place(8, source, VoidHeartBlocks.VOID_PILLAR.defaultBlockState());
        scene.highlight(8, scene.next(), source);

        scene.use("craft", 180, craft, above(source, 1.2F));
        scene.flight(10, 12, new Scene.Moment(5, 120), new ItemStack(Items.GOLD_INGOT), above(source, 2.4F), above(source, 1.2F));
        scene.place(30, forge, lit(forgeState));
        scene.place(30, source, lit(VoidHeartBlocks.VOID_PILLAR.defaultBlockState()));
        expected.stream().filter(block -> block.part() == ShatterForgeLayout.Part.CONDUIT)
                .forEach(block -> scene.place(30, block.pos(), lit(ForgePreviewRender.ghostState(block, conduits))));
        scene.beam(30, new Scene.Moment(6, 0), forge);

        scene.step("result", 90, end, above(forge, 1.35F));
        scene.place(0, forge, forgeState);
        scene.place(0, source, VoidHeartBlocks.VOID_PILLAR.defaultBlockState());
        expected.stream().filter(block -> block.part() == ShatterForgeLayout.Part.CONDUIT)
                .forEach(block -> scene.place(0, block.pos(), ForgePreviewRender.ghostState(block, conduits)));
        scene.flight(25, 0, Scene.Moment.NEVER, new ItemStack(VoidHeartItems.RAVENOUS_GOLD_INGOT), above(forge, 1.35F), above(forge, 1.35F));

        return scene.build();
    }

    /**
     * Brews searing oil: the glass bottle in the mouth, sulfur and quicksilver in two crucibles, and the fiery aspect drawn up through the intake.
     * The intake sits in a gap of the floor so that the athanor and its crucibles stand on the ground, and is set first since the athanor then hides it.
     */
    private static Scene athanor()
    {
        var athanor = BlockPos.ZERO;
        var alembic = athanor.above();
        var intake = athanor.below();
        var crucibles = List.of(athanor.east(), athanor.west());
        var reagents = new Item[] { Items.SULFUR, VoidHeartItems.QUICKSILVER };

        var athanorState = VoidHeartBlocks.ATHANOR.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH);
        var alembicState = VoidHeartBlocks.ALEMBIC.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH);
        var crucibleState = VoidHeartBlocks.CRUCIBLE.defaultBlockState();

        var mouth = new Vec3(0.5, 0.45, 1.15);
        var close = new SceneView(165, 30, 5, 0.5F, 0.8F, 0.5F);
        var whole = new SceneView(160, 36, 6.5F, 0.5F, 0.6F, 0.5F);
        var below = new SceneView(160, 45, 6, 0.5F, 0, 0.5F);
        var brew = new SceneView(190, 32, 6, 0.5F, 0.7F, 0.5F);

        var scene = Scene.builder(id("athanor"))
                .floor(-3, -3, 3, -1, -1, FLOOR.defaultBlockState())
                .floor(-3, 1, 3, 3, -1, FLOOR.defaultBlockState())
                .floor(-3, 0, -1, 0, -1, FLOOR.defaultBlockState())
                .floor(1, 0, 3, 0, -1, FLOOR.defaultBlockState());

        scene.step("intake", 70, below, above(intake, 1));
        scene.place(10, intake, VoidHeartBlocks.ASPECT_INTAKE.defaultBlockState());
        scene.highlight(10, scene.next(), intake);

        scene.step("athanor", 50, close, above(athanor, 1));
        scene.place(10, athanor, athanorState);

        scene.step("alembic", 50, close, above(alembic, 1));
        scene.place(10, alembic, alembicState);
        scene.highlight(10, scene.next(), alembic);

        scene.step("crucibles", 70, whole, above(crucibles.getFirst(), 0.7F));
        for (int index = 0; index < crucibles.size(); index++)
        {
            scene.place(10 + index * 12, crucibles.get(index), crucibleState);
            scene.highlight(10 + index * 12, scene.next(), crucibles.get(index));
        }

        var brewStep = new Scene.Moment(6, 0);
        var resultStep = new Scene.Moment(7, 0);

        scene.use("base", 50, close, mouth);
        scene.flight(10, 14, resultStep, new ItemStack(Items.GLASS_BOTTLE), mouth.add(0, 1.2, 0.6), mouth);

        scene.use("reagents", 70, whole, above(crucibles.getFirst(), 0.85F));
        for (int index = 0; index < crucibles.size(); index++)
        {
            var crucible = crucibles.get(index);
            scene.flight(10 + index * 16, 12, resultStep, new ItemStack(reagents[index]), above(crucible, 2), above(crucible, 0.85F));
        }

        scene.step("brew", 160, brew, above(alembic, 1));
        scene.place(20, athanor, lit(athanorState));

        scene.step("result", 80, close, mouth);
        scene.place(0, athanor, athanorState);
        scene.flight(10, 0, Scene.Moment.NEVER, new ItemStack(VoidHeartItems.SEARING_OIL), mouth, mouth);

        return scene.build();
    }

    /**
     * Assembles an armillary from its core and base, lets its rings rise, powers it from a pylon, then searches, opens a dungeon and lets the connection fail.
     * The chart itself is a screen of its own, so the scene only shows what the rings do while it is used.
     */
    private static Scene armillary()
    {
        var core = BlockPos.ZERO;
        var base = ArmillaryLayout.base(core);
        var coreState = VoidHeartBlocks.ARMILLARY_CORE.defaultBlockState();
        var hub = new Vec3(0.5, 2.5, 0.5);

        var close = new SceneView(35, 30, 5, 0.5F, 0.5F, 0.5F);
        var whole = new SceneView(35, 38, 7.5F, 0.5F, 0.3F, 0.5F);
        var risen = new SceneView(35, 22, 7, 0.5F, 1.6F, 0.5F);
        var powered = new SceneView(35, 34, 10, -1.2F, 1, 1.2F);
        var portal = new SceneView(25, 12, 5, 0.5F, 2.2F, 0.5F);

        var scene = Scene.builder(id("armillary"))
                .floor(-5, -4, 4, 4, -1, FLOOR.defaultBlockState());

        scene.step("core", 50, close, above(core, 1));
        scene.place(10, core, coreState);

        scene.use("preview", 110, whole, above(core, 1));
        base.forEach(part -> scene.ghost(20, scene.next(), part.pos(), part.block().defaultBlockState()));

        scene.step("sides", 90, whole, above(base.getFirst().pos(), 1));
        int sides = 0;
        for (var part : base)
        {
            if (part.block() != VoidHeartBlocks.CUT_RAVENOUS_GOLD)
                continue;
            scene.place(10 + sides * 8, part.pos(), part.block().defaultBlockState());
            scene.highlight(10 + sides++ * 8, scene.next(), part.pos());
        }

        scene.step("corners", 90, whole, above(base.get(1).pos(), 1));
        int corners = 0;
        for (var part : base)
        {
            if (part.block() != VoidHeartBlocks.VOIDSTONE_CHISELED)
                continue;
            scene.place(10 + corners * 8, part.pos(), part.block().defaultBlockState());
            scene.highlight(10 + corners++ * 8, scene.next(), part.pos());
        }

        scene.step("rings", 130, risen, hub);
        scene.place(10, core, coreState.setValue(ArmillaryCoreBlock.FORMED, true));
        base.forEach(part -> scene.place(10, part.pos(), part.formed()));
        scene.rings(10, core);

        scene.step("power", 110, powered, above(ARMILLARY_PYLON, 1.5F));
        scene.place(10, ARMILLARY_PYLON, VoidGridContent.AZURIUM_PYLON.defaultBlockState());
        scene.place(30, ARMILLARY_PYLON.above(), VoidHeartBlocks.SMALL_AZURIUM_BUNDLE.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
        scene.highlight(30, scene.next(), ARMILLARY_PYLON.above());

        scene.use("chart", 150, risen, hub);
        scene.spin(10, ArmillaryMotion.SEARCHING_SPIN, 1);

        scene.use("open", 130, risen, hub);
        scene.spin(0, ArmillaryMotion.REACHED_SPIN, 1);
        for (int pearl = 0; pearl < 3; pearl++)
            scene.flight(14 + pearl * 10, 16, scene.at(70), new ItemStack(VoidHeartItems.VOID_PEARL), new Vec3(0.5 + (pearl - 1) * 0.9, 4.2, 0.5), hub);
        scene.portal(70, ArmillaryPortalRender.themeColor(DungeonTheme.TEMPLE));
        scene.spin(70, ArmillaryMotion.STEADY_SPIN, 1);

        scene.use("cross", 90, portal, hub);

        scene.step("decay", 190, risen, hub);
        scene.falter(10, scene.at(150));

        return scene.build();
    }
}
