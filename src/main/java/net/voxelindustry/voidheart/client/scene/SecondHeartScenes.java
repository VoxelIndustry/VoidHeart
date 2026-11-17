package net.voxelindustry.voidheart.client.scene;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.client.scene.Scene.Flow;
import net.voxelindustry.voidheart.client.scene.Scene.Moment;
import net.voxelindustry.voidheart.common.block.StateProperties;
import net.voxelindustry.voidheart.common.content.secondheart.ventricle.TwinVentricleContent;
import net.voxelindustry.voidheart.common.content.secondheart.ventricle.VentricleBalance;
import net.voxelindustry.voidheart.common.content.secondheart.ventricle.VentricleLayout;
import net.voxelindustry.voidheart.common.content.secondheart.ventricle.VentricleLayout.Part;
import net.voxelindustry.voidheart.common.energy.grid.PylonBundle;
import net.voxelindustry.voidheart.common.energy.grid.VoidGridContent;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;

import java.util.List;

import static net.voxelindustry.voidheart.client.scene.EnergyScenes.AZURIUM;
import static net.voxelindustry.voidheart.client.scene.EnergyScenes.CONTRAVOID;
import static net.voxelindustry.voidheart.client.scene.EnergyScenes.bundle;
import static net.voxelindustry.voidheart.client.scene.EnergyScenes.center;
import static net.voxelindustry.voidheart.client.scene.EnergyScenes.ground;
import static net.voxelindustry.voidheart.client.scene.ResearchScenes.FLOOR;
import static net.voxelindustry.voidheart.client.scene.ResearchScenes.above;
import static net.voxelindustry.voidheart.client.scene.ResearchScenes.id;

/**
 * The Twin Ventricle raised around its cradle, fed, offered to and run until the heart stops. The chamber is laid out by the layout the cradle
 * checks, along X with its essence side toward positive X.
 */
final class SecondHeartScenes
{
    static final VentricleLayout.Orientation ORIENTATION = new VentricleLayout.Orientation(Direction.Axis.X, true);

    /** Where the relays filling each basin stand: on the far wall of its ring, since the ring hides the basin from anything level with it. */
    static final BlockPos ESSENCE_RELAY    = new BlockPos(4, 1, 0);
    static final BlockPos CONTRAVOID_RELAY = new BlockPos(-4, 1, 0);
    static final BlockPos PYLON            = new BlockPos(0, 0, 3);
    static final BlockPos SKULL            = new BlockPos(2, 0, -3);

    // The red the game's strands give Suffering
    private static final int SUFFERING = 0xC0182F;

    // Where the beat calls its tears, inside the ring the game opens them in
    private static final List<Vec3> TEARS = List.of(new Vec3(0.5, 1.6, -6.5), new Vec3(6.5, 1.6, 5.5), new Vec3(-5.5, 1.6, 6.5));

    private SecondHeartScenes()
    {
    }

    static Scene ventricle()
    {
        var cradle = BlockPos.ZERO;
        var expected = VentricleLayout.expected(ORIENTATION);
        var essenceBasin = ORIENTATION.basin(true);
        var contravoidBasin = ORIENTATION.basin(false);
        var top = above(cradle, 0.5F);

        var close = new SceneView(25, 32, 5, 0.5F, 0.3F, 0.5F);
        var whole = new SceneView(25, 40, 12, 0.5F, 0.3F, 0.5F);
        var fed = new SceneView(25, 42, 16, 0.5F, 0.3F, 1.5F);
        var offer = new SceneView(25, 34, 7, 0.5F, 0.5F, 0F);
        var rite = new SceneView(25, 44, 19, 0.5F, 0.5F, 0.5F);

        var scene = Scene.builder(id("twin_ventricle"))
                .floor(-8, -8, 8, 8, -1, FLOOR.defaultBlockState());

        scene.step("cradle", 50, close, above(cradle, 0.6F));
        scene.place(10, cradle, TwinVentricleContent.HEART_CRADLE.defaultBlockState());

        scene.step("dais", 70, close, above(cradle.north(), 0.2F));
        int placed = 0;
        for (var block : expected)
        {
            if (block.part() == Part.DAIS)
                scene.place(10 + placed++ * 4, block.offset(), VoidHeartBlocks.VOIDSTONE_POLISHED.defaultBlockState());
        }

        scene.step("ventricles", 150, whole, above(essenceBasin, 1));
        placed = 0;
        for (var block : expected)
        {
            if (block.part() == Part.DAIS)
                continue;
            int at = 10 + placed++ * 4;
            scene.place(at, block.offset(), stateOf(block.part()));
            if (block.part() == Part.VALVE)
                scene.highlight(at, scene.next(), block.offset());
        }

        var heartStops = new Moment(8, 0);
        var kindles = new Moment(6, 0);
        scene.step("fuel", 170, fed, above(cradle, 1));
        scene.place(10, ESSENCE_RELAY, VoidHeartBlocks.RAVENOUS_RELAY.defaultBlockState());
        scene.place(16, CONTRAVOID_RELAY, VoidHeartBlocks.RAVENOUS_RELAY.defaultBlockState());
        scene.link(24, heartStops, center(ESSENCE_RELAY), center(essenceBasin), SUFFERING, Flow.PULSE);
        scene.link(30, heartStops, center(CONTRAVOID_RELAY), center(contravoidBasin), CONTRAVOID, Flow.PULSE);
        scene.place(70, PYLON, VoidGridContent.AZURIUM_PYLON.defaultBlockState());
        scene.place(80, PYLON.above(), bundle(PylonBundle.SMALL));
        scene.reach(88, scene.next(), ground(PYLON), PylonBundle.SMALL.radius(), AZURIUM);
        scene.label(96, scene.next(), above(PYLON, 2.6F), EnergyScenes.rate(VentricleBalance.ENERGY_PER_TICK));

        scene.use("offering", 150, offer, top);
        var offerings = new ItemStack[] { new ItemStack(VoidHeartItems.KERNACIAN_CORE), new ItemStack(VoidHeartItems.MEMORIAM_SHARD, VentricleBalance.MEMORIAM_SHARDS),
                new ItemStack(VoidHeartItems.WRAITH_INK, VentricleBalance.WRAITH_INKS) };
        for (int index = 0; index < offerings.length; index++)
        {
            int start = 10 + index * 32;
            var into = top.add((index - 1) * 0.25, 0.1, 0);
            scene.flight(start, 14, kindles, offerings[index], into.add(0, 1.6, 0.6), into);
            scene.click(start + 14, top);
        }

        scene.step("experience", 110, offer, above(SKULL, 0.5F));
        // The skull's block hides its model and leaves it to its renderer, so the scene asks for the model itself
        scene.place(10, SKULL, VoidHeartBlocks.EXPERIENCE_SKULL.defaultBlockState().setValue(StateProperties.MODEL, true)
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
        scene.highlight(10, scene.next(), SKULL);
        scene.particles(30, scene.at(100), SceneFx.crumbs(new ItemStack(Items.EXPERIENCE_BOTTLE), above(SKULL, 0.5F), top));

        scene.step("kindle", 140, whole, above(cradle, 1));
        // What runs from each basin up to the heart, as the strands do over a real chamber
        scene.particles(0, scene.at(12), SceneFx.crumbs(offerings[0], top, top.add(0, 0.6, 0)));
        scene.link(10, heartStops, above(essenceBasin, 0.9F), top.add(0, 0.4, 0), SUFFERING, Flow.PULSE);
        scene.link(40, heartStops, above(contravoidBasin, 0.9F), top.add(0, 0.4, 0), CONTRAVOID, Flow.PULSE);

        scene.step("beat", 200, rite, above(cradle, 1));
        scene.outline(6, scene.next(), cradle, cradle, Scene.THEME);
        for (int index = 0; index < TEARS.size(); index++)
            scene.particles(30 + index * 45, scene.at(60 + index * 45), SceneFx.voidGather(TEARS.get(index)));

        scene.step("heart", 90, close, above(cradle, 1));
        scene.puff(0, SceneFx.voidBurst(above(cradle, 1)));

        return scene.build();
    }

    private static BlockState stateOf(Part part)
    {
        return switch (part)
        {
            case DAIS -> VoidHeartBlocks.VOIDSTONE_POLISHED.defaultBlockState();
            case VALVE -> TwinVentricleContent.RAVENOUS_GOLD_VALVE.defaultBlockState();
            case BASIN -> TwinVentricleContent.VENTRICLE_BASIN.defaultBlockState();
            case RUBRUM_WALL -> TwinVentricleContent.RUBRUM_VEINED_VOIDSTONE.defaultBlockState();
            case AZURIUM_WALL -> TwinVentricleContent.AZURIUM_VEINED_VOIDSTONE.defaultBlockState();
        };
    }
}
