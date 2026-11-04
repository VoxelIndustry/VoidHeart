package net.voxelindustry.voidheart.common.world.dungeon.rooms;

import net.minecraft.core.Direction;
import net.voxelindustry.voidheart.common.world.dungeon.DungeonSize;
import net.voxelindustry.voidheart.common.world.dungeon.DungeonTemplate;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/**
 * A type of room and its size range, in lattice cells.
 */
public record RoomKind(String name,
                       int minWidth,
                       int maxWidth,
                       int minHeight,
                       int maxHeight,
                       int weight,
                       Consumer<RoomCanvas> builder,
                       @Nullable DungeonTemplate template,
                       List<Direction> doorSides,
                       boolean isLeaf)
{
    public static RoomKind of(String name, int minWidth, int maxWidth, int minHeight, int maxHeight, int weight, Consumer<RoomCanvas> builder)
    {
        return new RoomKind(name, minWidth, maxWidth, minHeight, maxHeight, weight, builder, null, List.of(), false);
    }

    // Nobody crosses a leaf to reach another room, so a walkway never cuts through what it holds
    public static RoomKind leaf(String name, int minWidth, int maxWidth, int minHeight, int maxHeight, int weight, Consumer<RoomCanvas> builder)
    {
        return new RoomKind(name, minWidth, maxWidth, minHeight, maxHeight, weight, builder, null, List.of(), true);
    }

    public static RoomKind of(DungeonTemplate template, int weight, Direction... doorSides)
    {
        return of(template, weight, canvas -> { }, doorSides);
    }

    public static RoomKind of(DungeonTemplate template, int weight, Consumer<RoomCanvas> additions, Direction... doorSides)
    {
        int width = (Math.max(template.size().getX(), template.size().getZ()) - 1) / DungeonSize.CELL;
        int height = (template.size().getY() - 1) / DungeonSize.CELL;
        Consumer<RoomCanvas> builder = canvas ->
        {
            canvas.template(template, 0, 0, 0);
            additions.accept(canvas);
        };
        return new RoomKind(template.file(), width, width, height, height, weight, builder, template, List.of(doorSides), true);
    }

    public boolean fits(DungeonSize size)
    {
        return minWidth <= size.cells() && minHeight <= size.cells();
    }
}
