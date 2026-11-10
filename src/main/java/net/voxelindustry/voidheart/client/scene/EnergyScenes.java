package net.voxelindustry.voidheart.client.scene;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.client.scene.Scene.Flow;
import net.voxelindustry.voidheart.client.scene.Scene.Moment;
import net.voxelindustry.voidheart.client.scene.Scene.Role;
import net.voxelindustry.voidheart.common.energy.engine.RavenousEngineBlock;
import net.voxelindustry.voidheart.common.energy.engine.RavenousEngineContent;
import net.voxelindustry.voidheart.common.energy.fissure.SeepMath;
import net.voxelindustry.voidheart.common.energy.fissure.VoidFissureBalance;
import net.voxelindustry.voidheart.common.energy.fissure.VoidFissureContent;
import net.voxelindustry.voidheart.common.energy.grid.PylonBundle;
import net.voxelindustry.voidheart.common.energy.grid.VoidGridContent;
import net.voxelindustry.voidheart.common.energy.reactor.ContravoidReactorContent;
import net.voxelindustry.voidheart.common.energy.reactor.ReactorContainmentBlock;
import net.voxelindustry.voidheart.common.energy.reactor.ReactorLayout;
import net.voxelindustry.voidheart.common.energy.spike.AzuriumSpikeBlock;
import net.voxelindustry.voidheart.common.energy.spike.LeySpikeBalance;
import net.voxelindustry.voidheart.common.energy.spike.LeySpikeContent;
import net.voxelindustry.voidheart.common.energy.storage.ConstructBundles;
import net.voxelindustry.voidheart.common.energy.storage.ConstructFrameBlock;
import net.voxelindustry.voidheart.common.energy.storage.ConstructLayout;
import net.voxelindustry.voidheart.common.energy.storage.RubrumContent;
import net.voxelindustry.voidheart.common.energy.storage.SocketBundle;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;

import java.util.Comparator;

import static net.voxelindustry.voidheart.client.scene.ResearchScenes.FLOOR;
import static net.voxelindustry.voidheart.client.scene.ResearchScenes.above;
import static net.voxelindustry.voidheart.client.scene.ResearchScenes.id;

/**
 * The scenes of the energy system. Every reach drawn in them is read from the constants the game itself uses, and the structures are laid out by the layouts
 * the tiles check, so a number changed for balance changes here too.
 */
final class EnergyScenes
{
    // Tones of the things themselves: the azurium bundles, an awake ley line, the essences as their relays carry them, and the void's seep
    static final int AZURIUM    = 0x6FB4FF;
    static final int LEY        = 0x36C5B0;
    static final int STILLNESS  = 0x7FB8FF;
    static final int FIERY      = 0xFF8A3D;
    static final int CONTRAVOID = 0xE9DDFF;
    static final int SEEP       = 0x9C62F5;

    // Lines along the ground are lifted off it, clear of the fill of a ring
    static final double GROUND = 0.08;

    private EnergyScenes()
    {
    }

    static Component blocks(int count)
    {
        return Component.translatable("voidheart.scene.blocks", count);
    }

    private static Component rate(long perTick)
    {
        return Component.translatable("voidheart.scene.rate", perTick);
    }

    static Vec3 center(BlockPos pos)
    {
        return Vec3.atCenterOf(pos);
    }

    static Vec3 ground(BlockPos pos)
    {
        return new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
    }

    private static Vec3 onGround(BlockPos pos, double dx, double dz)
    {
        return new Vec3(pos.getX() + 0.5 + dx, pos.getY() + GROUND, pos.getZ() + 0.5 + dz);
    }

    static BlockState bundle(PylonBundle size)
    {
        return bundleBlock(size).defaultBlockState();
    }

    private static Block bundleBlock(PylonBundle size)
    {
        return switch (size)
        {
            case MEDIUM -> VoidHeartBlocks.MEDIUM_AZURIUM_BUNDLE;
            case LARGE -> VoidHeartBlocks.LARGE_AZURIUM_BUNDLE;
            default -> VoidHeartBlocks.SMALL_AZURIUM_BUNDLE;
        };
    }

    /**
     * The top of a bundle's crystals, where arcs leave and land.
     */
    private static Vec3 tip(BlockPos pylon, PylonBundle size)
    {
        return new Vec3(pylon.getX() + 0.5, pylon.getY() + 1 + size.height() / 16D, pylon.getZ() + 0.5);
    }

    /**
     * A pylon's field on the ground it stands on, with its reach written along a line from the pylon to its edge.
     */
    private static void field(Scene.Builder scene, int offset, Moment to, BlockPos pylon, PylonBundle size, double towardX, double towardZ)
    {
        scene.reach(offset, to, ground(pylon), size.radius(), AZURIUM);
        scene.measure(offset + 8, to, onGround(pylon, 0, 0), onGround(pylon, towardX * size.radius(), towardZ * size.radius()), blocks(size.radius()), AZURIUM);
    }

    /**
     * One pylon and its three bundles, a second one arcing to it, an engine fed by relays, and a construct to keep what is left over.
     */
    static Scene grid()
    {
        var first = BlockPos.ZERO;
        var second = new BlockPos(-PylonBundle.LARGE.radius() - 2, 0, 0);
        var engine = new BlockPos(4, 0, 0);
        var relay = new BlockPos(4, 0, -3);
        var collector = new BlockPos(4, 0, -6);
        var core = second.offset(-3, 1, -3);

        var pylonState = VoidGridContent.AZURIUM_PYLON.defaultBlockState();
        var engineState = RavenousEngineContent.RAVENOUS_ENGINE.defaultBlockState();
        var frameState = RubrumContent.RUBRUM_CONSTRUCT_FRAME.defaultBlockState();

        var close = new SceneView(25, 30, 5.5F, 0.5F, 1, 0.5F);
        var small = new SceneView(25, 40, 17, 0.5F, 0.5F, 0.5F);
        var large = new SceneView(25, 46, 38, 0.5F, 0.5F, 0.5F);
        var chain = new SceneView(25, 42, 34, second.getX() / 2F + 0.5F, 0.5F, 0.5F);
        var feed = new SceneView(40, 32, 11, 3.5F, 0.8F, -2.5F);
        var store = new SceneView(35, 30, 8.5F, core.getX() + 0.5F, 1.5F, core.getZ() + 0.5F);

        var scene = Scene.builder(id("void_grid"))
                .floor(second.getX() - 8, -8, 8, 4, -1, FLOOR.defaultBlockState());

        scene.step("pylon", 50, close, above(first, 1));
        scene.place(10, first, pylonState);

        var sizes = new Moment(2, 0);
        scene.use("bundle", 100, small, above(first, 1.4F));
        scene.flight(8, 14, scene.at(26), new ItemStack(VoidHeartBlocks.SMALL_AZURIUM_BUNDLE), above(first, 3.2F), above(first, 1.5F));
        scene.click(20, above(first, 1.4F));
        scene.place(26, first.above(), bundle(PylonBundle.SMALL));
        field(scene, 36, new Moment(sizes.step(), 10), first, PylonBundle.SMALL, 0, 1);

        scene.step("sizes", 170, large, above(first, 1.6F));
        scene.place(10, first.above(), bundle(PylonBundle.MEDIUM));
        field(scene, 10, scene.at(80), first, PylonBundle.MEDIUM, 0, 1);
        scene.place(80, first.above(), bundle(PylonBundle.LARGE));
        field(scene, 80, new Moment(4, 0), first, PylonBundle.LARGE, 0, 1);

        scene.step("arc", 120, chain, tip(first, PylonBundle.LARGE).lerp(tip(second, PylonBundle.SMALL), 0.5));
        scene.place(10, second, pylonState);
        scene.place(22, second.above(), bundle(PylonBundle.SMALL));
        field(scene, 30, scene.next(), second, PylonBundle.SMALL, 0, 1);
        scene.link(50, Moment.NEVER, tip(first, PylonBundle.LARGE), tip(second, PylonBundle.SMALL), AZURIUM, Flow.ARC);

        scene.step("engine", 170, feed, above(engine, 1.2F));
        scene.place(10, engine, engineState);
        scene.place(10, engine.above(), engineState.setValue(RavenousEngineBlock.PART, RavenousEngineBlock.Part.CHAMBER));
        scene.actor(10, Moment.NEVER, engine, Role.ENGINE, scene.at(84), center(first.above()), STILLNESS, 1);
        scene.actor(24, Moment.NEVER, collector, Role.COLLECTOR);
        scene.place(24, collector, VoidHeartBlocks.RAVENOUS_COLLECTOR.defaultBlockState());
        scene.flight(34, 12, scene.at(58), new ItemStack(Items.WATER_BUCKET), above(collector, 2.6F), above(collector, 1.4F));
        scene.click(46, above(collector, 1));
        scene.place(58, relay, VoidHeartBlocks.RAVENOUS_RELAY.defaultBlockState());
        scene.highlight(58, scene.next(), relay);
        scene.link(70, Moment.NEVER, center(collector), center(relay), STILLNESS, Flow.PULSE);
        scene.link(76, Moment.NEVER, center(relay), center(engine), STILLNESS, Flow.PULSE);

        scene.use("construct", 170, store, center(core));
        var frame = ConstructLayout.frame(ConstructLayout.Size.SMALL).stream().sorted(Comparator.comparingInt(BlockPos::getY)).toList();
        scene.flight(4, 10, scene.at(76), new ItemStack(RubrumContent.RUBRUM_CONSTRUCT_FRAME, frame.size()), above(core, 3.4F), above(core, 2.7F));
        for (int index = 0; index < frame.size(); index++)
            scene.place(12 + index * 3, core.offset(frame.get(index)), frameState);
        scene.flight(84, 14, scene.at(104), new ItemStack(RubrumContent.RUBRUM_CONSTRUCT_CORE), above(core.offset(2, 0, -2), 1.6F), center(core.offset(1, 0, -1)));
        scene.click(98, center(core.offset(1, 0, -1)));
        scene.place(104, core, RubrumContent.RUBRUM_CONSTRUCT_CORE.defaultBlockState());
        // Whole, every frame takes the part of the cube it stands on
        for (var offset : frame)
            scene.place(108, core.offset(offset), frameState.setValue(ConstructFrameBlock.PART, ConstructLayout.partAt(ConstructLayout.Size.SMALL, offset)));

        scene.use("sockets", 150, store, center(core.above()));
        var filled = new SocketBundle[] { SocketBundle.SMALL_RUBRUM, SocketBundle.SMALL_AZURIUM };
        var sockets = new BlockPos[] { BlockPos.ZERO.above(), BlockPos.ZERO.north() };
        for (int index = 0; index < filled.length; index++)
        {
            var socket = core.offset(sockets[index]);
            var inward = ConstructLayout.inward(ConstructLayout.Size.SMALL, sockets[index]);
            var state = ConstructBundles.stateOf(filled[index], inward);
            int start = 12 + index * 55;
            scene.flight(start, 14, scene.at(start + 22), new ItemStack(state.getBlock()), center(socket.relative(inward.getOpposite(), 2)), center(socket));
            scene.click(start + 16, center(socket));
            scene.place(start + 22, socket, state);
        }

        return scene.build();
    }

    /**
     * The shell closed around the core, its two intakes fed from each end, and the light it takes from a field to start.
     */
    static Scene reactor()
    {
        var core = new BlockPos(0, 1, 0);
        var axis = Direction.Axis.X;
        var shell = ReactorLayout.shell(axis).stream().sorted(Comparator.comparingInt(expected -> expected.offset().getY())).toList();
        var pylon = new BlockPos(3, 0, 4);
        var contravoidRelay = new BlockPos(-4, 1, 0);
        var essenceRelay = new BlockPos(4, 1, 0);
        var contravoidCollector = new BlockPos(-7, 0, 0);
        var essenceCollector = new BlockPos(7, 0, 0);

        var containment = ContravoidReactorContent.REACTOR_CONTAINMENT.defaultBlockState();
        var intake = ContravoidReactorContent.REACTOR_INTAKE.defaultBlockState();
        var relayState = VoidHeartBlocks.RAVENOUS_RELAY.defaultBlockState();

        var close = new SceneView(35, 28, 6.5F, 0.5F, 1.5F, 0.5F);
        var feed = new SceneView(35, 30, 15, 0.5F, 1.2F, 0.5F);
        var light = new SceneView(35, 30, 11, 1.5F, 1.4F, 1.5F);

        var scene = Scene.builder(id("contravoid_reactor"))
                .floor(-9, -4, 9, 6, -1, FLOOR.defaultBlockState());

        scene.step("core", 50, close, center(core));
        scene.place(10, core, ContravoidReactorContent.CONTRAVOID_REACTOR_CORE.defaultBlockState());

        var intakes = new Moment(2, 0);
        scene.step("shell", 130, close, center(core.offset(0, 1, -1)));
        scene.outline(4, scene.next(), core.offset(-1, -1, -1), core.offset(1, 1, 1), Scene.THEME);
        int placed = 0;
        for (var expected : shell)
        {
            if (expected.part() == ReactorLayout.Part.INTAKE)
                scene.ghost(60, new Moment(intakes.step(), 10 + placed % 2 * 12), core.offset(expected.offset()), intake);
            else
                scene.place(10 + placed++ * 3, core.offset(expected.offset()), containment);
        }

        scene.step("intakes", 110, close, center(core.west()));
        int set = 0;
        for (var offset : ReactorLayout.intakes(axis))
        {
            scene.place(10 + set * 12, core.offset(offset), intake);
            scene.highlight(10 + set++ * 12, scene.next(), core.offset(offset));
        }
        // Whole, the middle of every other side opens its slit
        for (var expected : shell)
        {
            if (expected.slit() != ReactorLayout.Slit.NONE)
                scene.place(50, core.offset(expected.offset()), containment.setValue(ReactorContainmentBlock.SLIT, expected.slit()));
        }

        scene.use("feed", 170, feed, center(contravoidRelay));
        var relays = new BlockPos[] { contravoidRelay, essenceRelay };
        var collectors = new BlockPos[] { contravoidCollector, essenceCollector };
        var catalysts = new ItemStack[] { new ItemStack(VoidHeartItems.AZURIUM_SHARD), new ItemStack(Items.LAVA_BUCKET) };
        var colors = new int[] { CONTRAVOID, FIERY };
        var ends = ReactorLayout.intakes(axis);
        for (int side = 0; side < 2; side++)
        {
            scene.place(10, relays[side].below(), VoidHeartBlocks.VOIDSTONE_BRICKS.defaultBlockState());
            scene.place(16, relays[side], relayState);
            scene.actor(16, Moment.NEVER, collectors[side], Role.COLLECTOR);
            scene.place(16, collectors[side], VoidHeartBlocks.RAVENOUS_COLLECTOR.defaultBlockState());

            int start = 30 + side * 30;
            scene.flight(start, 12, scene.at(start + 22), catalysts[side], above(collectors[side], 2.6F), above(collectors[side], 1.4F));
            scene.click(start + 12, above(collectors[side], 1));
            scene.link(start + 22, Moment.NEVER, center(collectors[side]), center(relays[side]), colors[side], Flow.PULSE);
            scene.link(start + 28, Moment.NEVER, center(relays[side]), center(core.offset(ends.get(side))), colors[side], Flow.PULSE);
        }

        scene.use("light", 150, light, center(core.offset(0, 0, -1)));
        scene.place(6, pylon, VoidGridContent.AZURIUM_PYLON.defaultBlockState());
        scene.place(14, pylon.above(), bundle(PylonBundle.LARGE));
        scene.click(44, center(core.offset(0, 0, -1)).add(0, 0, -0.5));
        scene.actor(48, Moment.NEVER, core, Role.REACTOR, scene.at(48), center(pylon.above()), FIERY, 1);

        scene.step("lit", 150, close, center(core.offset(0, 0, -1)));

        return scene.build();
    }

    /**
     * The spike driven into a line inside a field, the clicks that set its share, and the Contravoid a bruised node gives.
     */
    static Scene spike()
    {
        var spike = BlockPos.ZERO;
        var pylon = new BlockPos(3, 0, 3);
        var collector = new BlockPos(-3, 0, -3);
        var bundle = PylonBundle.MEDIUM;
        var spikeState = LeySpikeContent.AZURIUM_SPIKE.defaultBlockState();
        var over = above(spike, 2.7F);

        var land = new SceneView(30, 44, 25, 1.5F, 0.5F, 1.5F);
        var close = new SceneView(30, 32, 9, 1.5F, 1, 1.5F);
        var harvest = new SceneView(30, 40, 20, 0.5F, 0.5F, 0.5F);

        var scene = Scene.builder(id("azurium_spike"))
                .floor(-10, -8, 12, 10, -1, FLOOR.defaultBlockState());

        scene.step("line", 80, land, new Vec3(-5.5, GROUND, 0.5));
        scene.link(6, Moment.NEVER, new Vec3(-10, GROUND, 0.5), new Vec3(13, GROUND, 0.5), LEY, Flow.PULSE);
        scene.label(16, scene.next(), new Vec3(-5.5, 0.6, 0.5), Component.translatable("voidheart.scene.ley_line"));
        scene.place(0, pylon, VoidGridContent.AZURIUM_PYLON.defaultBlockState());
        scene.place(0, pylon.above(), bundle(bundle));
        field(scene, 24, new Moment(2, 0), pylon, bundle, 0, 1);

        var shares = new Moment(2, 0);
        scene.use("spike", 90, land, above(spike, 1.8F));
        scene.flight(8, 12, scene.at(24), new ItemStack(LeySpikeContent.AZURIUM_SPIKE), above(spike, 3.4F), above(spike, 1.4F));
        scene.click(20, above(spike, 0.1F));
        scene.place(24, spike, spikeState);
        scene.place(24, spike.above(), spikeState.setValue(AzuriumSpikeBlock.PART, AzuriumSpikeBlock.Part.TIP));
        scene.actor(24, Moment.NEVER, spike, Role.SPIKE, scene.at(44), center(pylon.above()), AZURIUM, 1);

        // The setting it is placed with, then one click up for each larger share and a sneaking one back down
        int[] clicks = { 20, 52, 84, 118 };
        int[] settings = { 1, 2, 3, 2 };
        scene.label(44, new Moment(shares.step(), clicks[0]), over, share(LeySpikeBalance.DEFAULT_SETTING));

        scene.use("share", 160, close, above(spike, 1.2F));
        for (int index = 0; index < clicks.length; index++)
        {
            if (settings[index] < (index == 0 ? LeySpikeBalance.DEFAULT_SETTING : settings[index - 1]))
                scene.sneakClick(clicks[index] - 4, above(spike, 1.2F));
            else
                scene.click(clicks[index] - 4, above(spike, 1.2F));
            var until = index + 1 < clicks.length ? scene.at(clicks[index + 1]) : Moment.NEVER;
            scene.label(clicks[index], until, over, share(settings[index]));
        }

        scene.use("contravoid", 160, harvest, center(collector));
        scene.reach(6, Moment.NEVER, ground(spike), LeySpikeBalance.CONTRAVOID_RANGE, CONTRAVOID);
        scene.measure(14, Moment.NEVER, onGround(spike, 0, 0), onGround(spike, LeySpikeBalance.CONTRAVOID_RANGE, 0), blocks(LeySpikeBalance.CONTRAVOID_RANGE), CONTRAVOID);
        scene.actor(20, Moment.NEVER, collector, Role.COLLECTOR);
        scene.place(20, collector, VoidHeartBlocks.RAVENOUS_COLLECTOR.defaultBlockState());
        scene.flight(34, 12, scene.at(58), new ItemStack(VoidHeartItems.AZURIUM_SHARD), above(collector, 2.6F), above(collector, 1.4F));
        scene.click(46, above(collector, 1));
        scene.link(60, Moment.NEVER, above(spike, 0.3F), center(collector), CONTRAVOID, Flow.PULSE);

        return scene.build();
    }

    private static Component share(int setting)
    {
        return Component.literal(Math.round(LeySpikeBalance.SHARES[setting] * 100) + "%");
    }

    /**
     * The seed set in a field, the four bundles that tear it wider in their order, how far its seep ends up, and the Contravoid that closes it.
     */
    static Scene fissure()
    {
        var fissure = BlockPos.ZERO;
        var pylon = new BlockPos(0, 0, 4);
        var relay = new BlockPos(0, 0, -3);
        var collector = new BlockPos(0, 0, -6);
        var over = above(fissure, 1.5F);
        var widest = VoidFissureBalance.MAX_WIDTH;

        var close = new SceneView(30, 36, 10, 0.5F, 0.5F, 1.5F);
        int reach = Math.round(SeepMath.targetRadius(widest));
        var seep = new SceneView(30, 50, reach * 2.3F, 0.5F, 0, 0.5F);
        var mend = new SceneView(30, 36, 13, 0.5F, 0.5F, -1.5F);

        var scene = Scene.builder(id("void_fissure"))
                .floor(-5, -8, 5, 6, -1, FLOOR.defaultBlockState());

        // What each widening takes, in the order the fissure asks for them, and when each bundle is pressed in
        var sizes = new PylonBundle[] { PylonBundle.NONE, PylonBundle.SMALL, PylonBundle.MEDIUM, PylonBundle.LARGE };
        var bundles = new PylonBundle[widest - 1];
        int[] pressed = new int[widest - 1];
        for (int width = 1; width < widest; width++)
        {
            bundles[width - 1] = sizes[SeepMath.widenBundle(width)];
            pressed[width - 1] = 34 + (width - 1) * 46;
        }
        var widening = new Moment(1, 0);
        var closing = new Moment(3, 96);

        scene.use("seed", 90, close, above(fissure, 0.1F));
        scene.place(0, pylon, VoidGridContent.AZURIUM_PYLON.defaultBlockState());
        scene.place(0, pylon.above(), bundle(PylonBundle.SMALL));
        scene.flight(8, 12, scene.at(24), new ItemStack(VoidFissureContent.FISSURE_SEED), above(fissure, 2.6F), above(fissure, 0.5F));
        scene.click(20, above(fissure, 0.1F));
        scene.place(24, fissure, VoidFissureContent.VOID_FISSURE.defaultBlockState());
        scene.actor(24, new Moment(widening.step(), pressed[0]), fissure, Role.FISSURE, scene.at(24), center(pylon.above()), Scene.THEME, 1);
        scene.label(46, new Moment(widening.step(), pressed[0]), over, rate(SeepMath.output(1)));

        scene.use("widen", 220, close, above(fissure, 0.1F));
        for (int index = 0; index < bundles.length; index++)
        {
            int at = pressed[index];
            var until = index + 1 < bundles.length ? scene.at(pressed[index + 1]) : closing;
            scene.flight(at - 24, 12, scene.at(at), new ItemStack(bundleBlock(bundles[index])), above(fissure, 2.6F), above(fissure, 0.5F));
            scene.click(at - 8, above(fissure, 0.1F));
            scene.actor(at, until, fissure, Role.FISSURE, scene.at(at), center(pylon.above()), Scene.THEME, index + 2);
            scene.label(at + 10, index + 1 < bundles.length ? until : scene.next(), over, rate(SeepMath.output(index + 2)));
        }

        scene.step("seep", 130, seep, new Vec3(0.5 - reach * 0.7, 0, 0.5 + reach * 0.7));
        scene.reach(14, scene.next(), ground(fissure), reach, SEEP);
        scene.measure(26, scene.next(), onGround(fissure, 0, 0), onGround(fissure, -reach, 0), blocks(reach), SEEP);

        scene.step("close", 170, mend, center(relay));
        scene.actor(10, Moment.NEVER, collector, Role.COLLECTOR);
        scene.place(10, collector, VoidHeartBlocks.RAVENOUS_COLLECTOR.defaultBlockState());
        scene.place(22, relay, VoidHeartBlocks.RAVENOUS_RELAY.defaultBlockState());
        scene.link(34, scene.at(136), center(collector), center(relay), CONTRAVOID, Flow.PULSE);
        scene.link(40, scene.at(136), center(relay), above(fissure, 0.15F), CONTRAVOID, Flow.PULSE);
        scene.actor(closing.offset(), scene.at(136), fissure, Role.FISSURE_CLOSING, closing, null, Scene.THEME, widest);
        scene.clear(136, fissure);

        return scene.build();
    }
}
