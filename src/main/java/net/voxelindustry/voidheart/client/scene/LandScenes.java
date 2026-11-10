package net.voxelindustry.voidheart.client.scene;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.client.scene.Scene.Flow;
import net.voxelindustry.voidheart.client.scene.Scene.Moment;
import net.voxelindustry.voidheart.client.scene.Scene.Role;
import net.voxelindustry.voidheart.common.block.PortalFrameStateProperties;
import net.voxelindustry.voidheart.common.block.PortalFrameStateProperties.FrameConnection;
import net.voxelindustry.voidheart.common.energy.grid.PylonBundle;
import net.voxelindustry.voidheart.common.energy.grid.VoidGridContent;
import net.voxelindustry.voidheart.common.energy.ley.LeyStoneBalance;
import net.voxelindustry.voidheart.common.energy.ley.LeyStoneContent;
import net.voxelindustry.voidheart.common.energy.machine.MachineEnergyBalance;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static net.voxelindustry.voidheart.client.scene.EnergyScenes.AZURIUM;
import static net.voxelindustry.voidheart.client.scene.EnergyScenes.GROUND;
import static net.voxelindustry.voidheart.client.scene.EnergyScenes.LEY;
import static net.voxelindustry.voidheart.client.scene.EnergyScenes.blocks;
import static net.voxelindustry.voidheart.client.scene.EnergyScenes.center;
import static net.voxelindustry.voidheart.client.scene.EnergyScenes.ground;
import static net.voxelindustry.voidheart.client.scene.ResearchScenes.FLOOR;
import static net.voxelindustry.voidheart.client.scene.ResearchScenes.above;
import static net.voxelindustry.voidheart.client.scene.ResearchScenes.id;
import static net.voxelindustry.voidheart.client.scene.ResearchScenes.lit;

/**
 * The scenes of what is set down in the land: standing stones along a ley line, the two frames of a portal, and a woven barrier.
 */
final class LandScenes
{
    // A portal as tall as a door and two wide, the least a player walks through without ducking
    private static final int PORTAL_WIDTH  = 4;
    private static final int PORTAL_HEIGHT = 5;

    private LandScenes()
    {
    }

    private static Component within(int count)
    {
        return Component.translatable("voidheart.scene.within", count);
    }

    /**
     * A stone tapping a line, a second one carrying the spur on, what a connected stone supplies, and the ward and grower that live on it.
     * The stones stand closer than they could, so that two of them fit a stage: the lines between them say how far they may be.
     */
    static Scene stones()
    {
        var tap = new BlockPos(18, 0, 0);
        var chained = new BlockPos(44, 0, 0);
        var ward = new BlockPos(48, 0, 3);
        var grower = new BlockPos(25, 0, 0);
        int field = LeyStoneBalance.GROWER_RADIUS;

        var stone = LeyStoneContent.LEY_STONE.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST);
        var upper = stone.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER);
        var wheat = Blocks.WHEAT.defaultBlockState();

        var line = new SceneView(200, 38, 20, 5, 0.5F, 0.5F);
        var tapped = new SceneView(200, 40, 28, 9.5F, 0.5F, 0.5F);
        var chain = new SceneView(200, 42, 40, 30, 0.5F, 0.5F);
        var supply = new SceneView(200, 44, 30, chained.getX() - 2, 0.5F, 0.5F);
        var warded = new SceneView(200, 46, 46, ward.getX() - 4, 0.5F, 0.5F);
        var grown = new SceneView(200, 36, 15, grower.getX() + 0.5F, 0.5F, 0.5F);

        // The floor leaves a square of tilled earth around where the grower will stand
        var scene = Scene.builder(id("ley_stone"))
                .floor(-4, -9, grower.getX() - field - 1, 9, -1, FLOOR.defaultBlockState())
                .floor(grower.getX() + field + 1, -9, 56, 9, -1, FLOOR.defaultBlockState())
                .floor(grower.getX() - field, -9, grower.getX() + field, -field - 1, -1, FLOOR.defaultBlockState())
                .floor(grower.getX() - field, field + 1, grower.getX() + field, 9, -1, FLOOR.defaultBlockState())
                .floor(grower.getX() - field, -field, grower.getX() + field, field, -1, Blocks.FARMLAND.defaultBlockState());

        var onLine = new Vec3(0.5, 0.4, 0.5);

        scene.step("line", 80, line, new Vec3(0.5, GROUND, 3.5));
        scene.link(6, Moment.NEVER, new Vec3(0.5, GROUND, -9), new Vec3(0.5, GROUND, 10), LEY, Flow.PULSE);
        scene.label(16, scene.next(), new Vec3(0.5, 0.6, 3.5), Component.translatable("voidheart.scene.ley_line"));

        scene.step("tap", 110, tapped, above(tap, 2));
        scene.place(10, tap, stone);
        scene.place(10, tap.above(), upper);
        scene.link(30, Moment.NEVER, onLine, above(tap, 1.2F), LEY, Flow.PULSE);
        scene.measure(40, scene.next(), new Vec3(0.5, GROUND, 3.5), new Vec3(tap.getX() + 0.5, GROUND, 3.5), within(LeyStoneBalance.STONE_REACH), LEY);

        scene.step("chain", 120, chain, above(chained, 2));
        scene.place(10, chained, stone);
        scene.place(10, chained.above(), upper);
        scene.link(30, Moment.NEVER, above(tap, 1.2F), above(chained, 1.2F), LEY, Flow.PULSE);
        scene.measure(40, scene.next(), new Vec3(tap.getX() + 0.5, GROUND, 3.5), new Vec3(chained.getX() + 0.5, GROUND, 3.5), within(LeyStoneBalance.STONE_REACH), LEY);

        scene.step("supply", 120, supply, ground(chained.offset(-4, 0, 4)));
        int reach = LeyStoneBalance.STONE_SUPPLY_RADIUS;
        scene.reach(10, scene.next(), ground(chained), reach, LEY);
        scene.reach(10, scene.next(), ground(tap), reach, LEY);
        scene.measure(22, scene.next(), new Vec3(chained.getX() + 0.5, GROUND, 0.5), new Vec3(chained.getX() + 0.5, GROUND, 0.5 + reach), blocks(reach), LEY);

        scene.step("ward", 120, warded, above(ward, 1));
        scene.place(10, ward, LeyStoneContent.LEY_WARD.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
        scene.highlight(10, scene.next(), ward);
        scene.link(24, Moment.NEVER, above(chained, 1.2F), above(ward, 0.8F), LEY, Flow.PULSE);
        int warded16 = LeyStoneBalance.WARD_RADIUS;
        scene.reach(34, scene.next(), ground(ward), warded16, LEY);
        scene.measure(44, scene.next(), new Vec3(ward.getX() + 0.5, GROUND, ward.getZ() + 0.5), new Vec3(ward.getX() + 0.5 - warded16, GROUND, ward.getZ() + 0.5), blocks(warded16), LEY);

        scene.step("grower", 140, grown, above(grower, 1));
        scene.place(10, grower, LeyStoneContent.LEY_GROWER.defaultBlockState());
        scene.highlight(10, scene.next(), grower);
        scene.link(24, Moment.NEVER, above(tap, 1.2F), above(grower, 0.8F), LEY, Flow.PULSE);
        scene.outline(30, Moment.NEVER, grower.offset(-field, 0, -field), grower.offset(field, 0, field), LEY);
        for (int x = -field; x <= field; x++)
        {
            for (int z = -field; z <= field; z++)
            {
                if (x == 0 && z == 0)
                    continue;
                var crop = grower.offset(x, 0, z);
                scene.place(0, crop, wheat.setValue(CropBlock.AGE, 1));
                // Ripening outward from the grower
                int ripens = 50 + (Math.abs(x) + Math.abs(z)) * 8;
                scene.place(ripens, crop, wheat.setValue(CropBlock.AGE, 4));
                scene.place(ripens + 16, crop, wheat.setValue(CropBlock.AGE, CropBlock.MAX_AGE));
            }
        }

        return scene.build();
    }

    private static EnumProperty<FrameConnection> side(Direction direction)
    {
        return switch (direction)
        {
            case DOWN -> PortalFrameStateProperties.DOWN;
            case UP -> PortalFrameStateProperties.UP;
            case NORTH -> PortalFrameStateProperties.NORTH;
            case SOUTH -> PortalFrameStateProperties.SOUTH;
            case WEST -> PortalFrameStateProperties.WEST;
            case EAST -> PortalFrameStateProperties.EAST;
        };
    }

    /**
     * A frame block as the game leaves it once the portal is formed: tied to the frame blocks beside it, and open toward the way it surrounds.
     */
    private static BlockState formed(BlockState state, BlockPos pos, Set<BlockPos> frame, Set<BlockPos> opening)
    {
        for (var direction : Direction.values())
        {
            var beside = pos.relative(direction);
            var connection = opening.contains(beside) ? FrameConnection.INTERIOR : frame.contains(beside) ? FrameConnection.FRAME : FrameConnection.NONE;
            state = state.setValue(side(direction), connection);
        }
        return state;
    }

    /**
     * One frame in the pocket and one outside, the core used on the first and then on the second, and the field that holds the way open.
     * Each side stands on ground of its own, since no walk leads from one to the other.
     */
    static Scene portal()
    {
        var pocket = BlockPos.ZERO;
        var outside = new BlockPos(9, 0, 0);
        var pylon = outside.offset(PORTAL_WIDTH + 1, 0, 2);
        var facing = Direction.SOUTH;
        var coreItem = new ItemStack(VoidHeartItems.PORTAL_CORE);

        var bricks = VoidHeartBlocks.VOIDSTONE_BRICKS.defaultBlockState();
        var frameState = VoidHeartBlocks.PORTAL_FRAME.defaultBlockState();
        var coreState = VoidHeartBlocks.PORTAL_FRAME_CORE.defaultBlockState().setValue(BlockStateProperties.FACING, facing);
        var way = VoidHeartBlocks.PORTAL_INTERIOR.defaultBlockState().setValue(BlockStateProperties.FACING, facing);

        var frame = new ArrayList<BlockPos>();
        var opening = new ArrayList<BlockPos>();
        for (int y = 0; y < PORTAL_HEIGHT; y++)
        {
            for (int x = 0; x < PORTAL_WIDTH; x++)
                (x == 0 || x == PORTAL_WIDTH - 1 || y == 0 || y == PORTAL_HEIGHT - 1 ? frame : opening).add(new BlockPos(x, y, 0));
        }
        var frameSet = new HashSet<>(frame);
        var openingSet = new HashSet<>(opening);
        var core = new BlockPos(1, 0, 0);
        var face = new Vec3(1.5, 0.5, 1);
        float middleX = (PORTAL_WIDTH) / 2F;
        float middleY = PORTAL_HEIGHT / 2F;

        var one = new SceneView(205, 24, 9.5F, middleX, middleY, 0.5F);
        var both = new SceneView(205, 26, 17, (outside.getX() + PORTAL_WIDTH) / 2F, middleY, 0.5F);
        var held = new SceneView(205, 30, 15, outside.getX() + middleX + 1, middleY - 0.5F, 0.5F);

        var scene = Scene.builder(id("portal"))
                .floor(-2, -3, PORTAL_WIDTH + 1, 3, -1, FLOOR.defaultBlockState())
                .floor(outside.getX() - 2, -3, outside.getX() + PORTAL_WIDTH + 3, 3, -1, Blocks.MOSS_BLOCK.defaultBlockState());

        scene.step("frame", 100, one, new Vec3(0.5, PORTAL_HEIGHT - 0.5, 1));
        scene.label(0, Moment.NEVER, new Vec3(middleX, PORTAL_HEIGHT + 0.9, 0.5), Component.translatable("voidheart.scene.portal.pocket"));
        for (int index = 0; index < frame.size(); index++)
            scene.place(10 + index * 4, pocket.offset(frame.get(index)), bricks);

        scene.use("form", 100, one, face);
        scene.flight(8, 14, scene.at(40), coreItem, face.add(0.6, 1.4, 1.6), face.add(0, 0, 0.3));
        scene.click(24, face);
        for (var pos : frame)
            scene.place(30, pocket.offset(pos), formed(pos.equals(core) ? coreState : frameState, pos, frameSet, openingSet));

        scene.step("second", 110, both, new Vec3(outside.getX() + 0.5, PORTAL_HEIGHT - 0.5, 1));
        scene.label(0, Moment.NEVER, new Vec3(outside.getX() + middleX, PORTAL_HEIGHT + 0.9, 0.5), Component.translatable("voidheart.scene.portal.outside"));
        for (int index = 0; index < frame.size(); index++)
            scene.place(10 + index * 4, outside.offset(frame.get(index)), bricks);

        var otherFace = face.add(outside.getX(), 0, 0);
        scene.use("link", 130, both, otherFace);
        scene.flight(6, 22, scene.at(44), coreItem, face.add(0, 0, 0.3), otherFace.add(0, 0, 0.3));
        scene.click(34, otherFace);
        for (var base : List.of(pocket, outside))
        {
            for (var pos : frame)
                scene.place(44, base.offset(pos), lit(formed(pos.equals(core) ? coreState : frameState, pos, frameSet, openingSet)));
            for (var pos : opening)
                scene.place(44, base.offset(pos), way);
        }
        scene.link(50, scene.next(), new Vec3(middleX, middleY, 0.8), new Vec3(outside.getX() + middleX, middleY, 0.8), Scene.THEME, Flow.PULSE);

        scene.step("hold", 120, held, above(pylon, 1.4F));
        scene.place(10, pylon, VoidGridContent.AZURIUM_PYLON.defaultBlockState());
        scene.place(22, pylon.above(), EnergyScenes.bundle(PylonBundle.SMALL));
        scene.reach(34, Moment.NEVER, ground(pylon), PylonBundle.SMALL.radius(), AZURIUM);
        scene.label(44, Moment.NEVER, above(pylon, 2.6F), Component.translatable("voidheart.scene.rate", MachineEnergyBalance.PORTAL_HOLD));

        return scene.build();
    }

    /**
     * A doorway closed in two clicks, corner to corner, then given a look.
     */
    static Scene barrier()
    {
        var first = new BlockPos(-1, 0, 0);
        var second = new BlockPos(1, 2, 0);
        var firstFace = new Vec3(first.getX(), first.getY() + 0.5, 0.5);
        var secondFace = new Vec3(second.getX() + 1, second.getY() + 0.5, 0.5);
        var weaver = new ItemStack(VoidHeartItems.PERMEABLE_BARRIER);
        var bricks = VoidHeartBlocks.VOIDSTONE_BRICKS.defaultBlockState();

        var view = new SceneView(205, 24, 8, 0.5F, 1.8F, 0.5F);

        var scene = Scene.builder(id("permeable_barrier"))
                .floor(-5, -3, 5, 3, -1, FLOOR.defaultBlockState());

        var cells = new ArrayList<BlockPos>();
        BlockPos.betweenClosed(first, second).forEach(pos -> cells.add(pos.immutable()));

        scene.step("wall", 60, view, new Vec3(0.5, 1.5, 0.5));
        for (int x = -3; x <= 3; x++)
        {
            for (int y = 0; y <= 3; y++)
            {
                if (!cells.contains(new BlockPos(x, y, 0)))
                    scene.place(4 + (x + 3) * 2, new BlockPos(x, y, 0), bricks);
            }
        }

        var woven = new Moment(2, 30);
        scene.use("first", 90, view, firstFace);
        scene.flight(6, 14, Moment.NEVER, weaver, new Vec3(-3.6, 1.6, 3), new Vec3(-3, 1.1, 2.2));
        scene.click(26, firstFace);
        scene.outline(30, woven, first, first, Scene.THEME);

        var chosen = new Moment(3, 50);
        scene.use("second", 120, view, secondFace);
        scene.click(20, secondFace);
        scene.outline(woven.offset(), scene.next(), first, second, Scene.THEME);
        for (int index = 0; index < cells.size(); index++)
        {
            scene.actor(woven.offset() + index * 3, chosen, cells.get(index), Role.BARRIER);
            scene.place(woven.offset() + index * 3, cells.get(index), VoidHeartBlocks.PERMEABLE_BARRIER.defaultBlockState());
        }

        scene.use("look", 130, view, center(new BlockPos(0, 1, 0)));
        scene.click(24, center(new BlockPos(0, 1, 0)).add(0, 0, 0.5));
        // Closed, it shows as the block it was told to look like
        for (var cell : cells)
            scene.place(chosen.offset(), cell, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());

        return scene.build();
    }
}
