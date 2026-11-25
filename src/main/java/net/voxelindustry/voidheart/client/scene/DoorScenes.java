package net.voxelindustry.voidheart.client.scene;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.client.scene.Scene.Flow;
import net.voxelindustry.voidheart.client.scene.Scene.Moment;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;

import static net.voxelindustry.voidheart.client.scene.ResearchScenes.FLOOR;
import static net.voxelindustry.voidheart.client.scene.ResearchScenes.id;

/**
 * The doors of the pockets: a void door linked to a door of the world by its key, and a rift raised out of thin air onto one.
 */
final class DoorScenes
{
    private static final Direction FACING = Direction.SOUTH;

    private static final BlockPos POCKET_DOOR = BlockPos.ZERO;
    private static final BlockPos WORLD_DOOR  = new BlockPos(7, 0, 0);
    private static final BlockPos FIRST_RIFT  = new BlockPos(7, 0, 0);
    private static final BlockPos SECOND_RIFT = new BlockPos(11, 0, 0);

    private DoorScenes()
    {
    }

    private static BlockState door(Block block, DoubleBlockHalf half, boolean open)
    {
        return block.defaultBlockState()
                .setValue(DoorBlock.FACING, FACING)
                .setValue(DoorBlock.HALF, half)
                .setValue(DoorBlock.OPEN, open);
    }

    private static void placeDoor(Scene.Builder scene, int offset, BlockPos pos, Block block, boolean open)
    {
        scene.place(offset, pos, door(block, DoubleBlockHalf.LOWER, open));
        scene.place(offset, pos.above(), door(block, DoubleBlockHalf.UPPER, open));
    }

    private static void clearDoor(Scene.Builder scene, int offset, BlockPos pos)
    {
        scene.clear(offset, pos.above());
        scene.clear(offset, pos);
    }

    /** Where a click on a closed door lands, on its face toward the camera. */
    private static Vec3 face(BlockPos door)
    {
        return new Vec3(door.getX() + 0.5, door.getY() + 0.9, door.getZ() + 0.9);
    }

    private static Vec3 middle(BlockPos door)
    {
        return new Vec3(door.getX() + 0.5, door.getY() + 1, door.getZ() + 0.5);
    }

    private static Vec3 overhead(BlockPos door)
    {
        return new Vec3(door.getX() + 0.5, door.getY() + 2.6, door.getZ() + 0.5);
    }

    /**
     * A void door set in the pocket, a Void Key bound to it, then pressed to a door of the world, and the two opening onto each other.
     */
    static Scene voidDoor()
    {
        var key = new ItemStack(VoidHeartItems.VOID_KEY);
        var one = new SceneView(205, 22, 7, 0.5F, 1, 0.5F);
        var both = new SceneView(205, 24, 12, 3.5F, 1, 0.5F);

        var scene = Scene.builder(id("void_door"))
                .floor(-2, -2, 2, 2, -1, FLOOR.defaultBlockState())
                .floor(WORLD_DOOR.getX() - 2, -2, WORLD_DOOR.getX() + 2, 2, -1, Blocks.MOSS_BLOCK.defaultBlockState());

        scene.step("door", 80, one, overhead(POCKET_DOOR));
        scene.label(0, Moment.NEVER, overhead(POCKET_DOOR).add(0, 0.4, 0), Component.translatable("voidheart.scene.void_door.pocket"));
        placeDoor(scene, 12, POCKET_DOOR, VoidHeartBlocks.VOID_DOOR, false);

        scene.use("bind", 100, one, face(POCKET_DOOR));
        scene.flight(6, 14, scene.at(40), key, face(POCKET_DOOR).add(0.8, 0.6, 1.6), face(POCKET_DOOR).add(0, 0, 0.3));
        scene.sneakClick(24, face(POCKET_DOOR));
        scene.highlight(28, scene.next(), POCKET_DOOR);
        scene.puff(28, SceneFx.voidBurst(middle(POCKET_DOOR)));

        scene.use("link", 120, both, face(WORLD_DOOR));
        scene.label(0, Moment.NEVER, overhead(WORLD_DOOR).add(0, 0.4, 0), Component.translatable("voidheart.scene.void_door.outside"));
        placeDoor(scene, 8, WORLD_DOOR, Blocks.OAK_DOOR, false);
        scene.flight(14, 20, scene.at(60), key, face(POCKET_DOOR).add(0, 0, 0.3), face(WORLD_DOOR).add(0, 0, 0.3));
        scene.sneakClick(38, face(WORLD_DOOR));
        scene.link(44, Moment.NEVER, middle(POCKET_DOOR), middle(WORLD_DOOR), Scene.THEME, Flow.PULSE);

        scene.step("walk", 130, both, middle(WORLD_DOOR));
        placeDoor(scene, 10, POCKET_DOOR, VoidHeartBlocks.VOID_DOOR, true);
        placeDoor(scene, 10, WORLD_DOOR, Blocks.OAK_DOOR, true);
        scene.particles(24, scene.at(70), SceneFx.voidThread(middle(WORLD_DOOR), middle(POCKET_DOOR)));
        // The last step has none after it, so this ends within it
        scene.particles(76, scene.at(125), SceneFx.voidThread(middle(POCKET_DOOR), middle(WORLD_DOOR)));

        return scene.build();
    }

    /**
     * A Rift Key bound to a pocket's void door, raising a diaphane door in open ground, raising it again elsewhere, and the rift closing as the pocket door shuts.
     */
    static Scene riftKey()
    {
        var key = new ItemStack(VoidHeartItems.RIFT_KEY);
        var one = new SceneView(205, 22, 7, 0.5F, 1, 0.5F);
        var both = new SceneView(205, 24, 12, 3.5F, 1, 0.5F);
        var wide = new SceneView(205, 26, 15, 5.5F, 1, 0.5F);

        var scene = Scene.builder(id("rift_key"))
                .floor(-2, -2, 2, 2, -1, FLOOR.defaultBlockState())
                .floor(FIRST_RIFT.getX() - 2, -2, SECOND_RIFT.getX() + 2, 2, -1, Blocks.MOSS_BLOCK.defaultBlockState());

        // Steps in order, so the rifts' links can end in a later one
        int move = 3;
        int shut = 4;

        scene.use("bind", 110, one, face(POCKET_DOOR));
        scene.label(0, Moment.NEVER, overhead(POCKET_DOOR).add(0, 0.4, 0), Component.translatable("voidheart.scene.rift_key.pocket"));
        placeDoor(scene, 6, POCKET_DOOR, VoidHeartBlocks.VOID_DOOR, false);
        scene.flight(14, 14, scene.at(50), key, face(POCKET_DOOR).add(0.8, 0.6, 1.6), face(POCKET_DOOR).add(0, 0, 0.3));
        scene.click(30, face(POCKET_DOOR));
        scene.puff(34, SceneFx.voidBurst(middle(POCKET_DOOR)));
        // In front of the door and low, clear of the pocket's name above it
        scene.label(40, Moment.NEVER, new Vec3(0.5, 0.2, 2.4), Component.translatable("voidheart.scene.rift_key.flexion"));

        scene.use("raise", 120, both, middle(FIRST_RIFT));
        scene.label(0, Moment.NEVER, overhead(FIRST_RIFT).add(0, 0.4, 0), Component.translatable("voidheart.scene.rift_key.outside"));
        scene.flight(4, 18, scene.at(40), key, face(POCKET_DOOR).add(0, 0, 0.3), face(FIRST_RIFT).add(0, 0, 0.6));
        scene.click(26, face(FIRST_RIFT));
        scene.particles(26, scene.at(40), SceneFx.voidGather(middle(FIRST_RIFT)));
        placeDoor(scene, 34, FIRST_RIFT, VoidHeartBlocks.RIFT_ENTRY, true);
        placeDoor(scene, 34, POCKET_DOOR, VoidHeartBlocks.VOID_DOOR, true);
        scene.link(40, new Moment(move, 30), middle(POCKET_DOOR), middle(FIRST_RIFT), Scene.THEME, Flow.PULSE);

        scene.step("walk", 120, both, middle(FIRST_RIFT));
        scene.particles(10, scene.at(60), SceneFx.voidThread(middle(FIRST_RIFT), middle(POCKET_DOOR)));
        scene.particles(64, scene.next(), SceneFx.voidThread(middle(POCKET_DOOR), middle(FIRST_RIFT)));

        scene.use("move", 130, wide, middle(SECOND_RIFT));
        scene.click(22, face(SECOND_RIFT));
        scene.particles(22, scene.at(30), SceneFx.voidGather(middle(SECOND_RIFT)));
        clearDoor(scene, 30, FIRST_RIFT);
        scene.puff(30, SceneFx.voidBurst(middle(FIRST_RIFT)));
        placeDoor(scene, 30, SECOND_RIFT, VoidHeartBlocks.RIFT_ENTRY, true);
        scene.link(30, new Moment(shut, 30), middle(POCKET_DOOR), middle(SECOND_RIFT), Scene.THEME, Flow.PULSE);

        scene.use("shut", 110, wide, face(POCKET_DOOR));
        scene.click(22, face(POCKET_DOOR));
        placeDoor(scene, 24, POCKET_DOOR, VoidHeartBlocks.VOID_DOOR, false);
        clearDoor(scene, 30, SECOND_RIFT);
        scene.puff(30, SceneFx.voidBurst(middle(SECOND_RIFT)));

        return scene.build();
    }
}
