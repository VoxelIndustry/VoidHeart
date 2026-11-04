package net.voxelindustry.voidheart.common.world.dungeon.rooms;

import net.minecraft.core.Direction;
import net.voxelindustry.voidheart.common.world.dungeon.DungeonTemplate;
import net.voxelindustry.voidheart.common.world.dungeon.DungeonTheme;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The rooms a theme is made of. Landmarks are placed first, near the middle of the largest dungeons.
 * The hand built pockets stand in every theme, redrawn in its materials when placed.
 */
public record DungeonKit(List<RoomKind> landmarks, List<RoomKind> rooms)
{
    private static final RoomKind SANCTUM = RoomKind.leaf("sanctum", 4, 5, 2, 3, 2, TempleRooms::sanctum);
    private static final RoomKind MONOLITH_COURT = RoomKind.leaf("monolith_court", 4, 5, 2, 3, 2, TempleRooms::monolithCourt);
    private static final RoomKind ATRIUM = RoomKind.of("atrium", 5, 8, 3, 7, 1, Architecture::atrium);

    private static final RoomKind TEMPLE = RoomKind.of(DungeonTemplate.TEMPLE, 1, Direction.EAST, Direction.NORTH, Direction.SOUTH);
    private static final RoomKind TOWER = RoomKind.of(DungeonTemplate.TOWER, 1, Direction.EAST);
    private static final RoomKind TEMPLE_FAVORED = RoomKind.of(DungeonTemplate.TEMPLE, 4, Direction.EAST, Direction.NORTH, Direction.SOUTH);
    private static final RoomKind TOWER_FAVORED = RoomKind.of(DungeonTemplate.TOWER, 4, Direction.EAST);
    private static final RoomKind KEEP = RoomKind.of(DungeonTemplate.KEEP, 1, Architecture::keepStairs, Direction.EAST, Direction.WEST, Direction.NORTH, Direction.SOUTH);

    private static final Map<DungeonTheme, DungeonKit> KITS = new EnumMap<>(Map.of(
            DungeonTheme.CATHEDRAL, new DungeonKit(
                    List.of(RoomKind.of("nave", 3, 8, 3, 6, 2, CathedralRooms::nave), ATRIUM, KEEP),
                    List.of(RoomKind.of("chapel", 2, 4, 2, 3, 4, CathedralRooms::chapel),
                            RoomKind.of("crypt", 2, 5, 1, 2, 4, CathedralRooms::crypt),
                            RoomKind.of("cloister", 3, 5, 2, 3, 2, CathedralRooms::cloister),
                            RoomKind.of("belfry", 2, 2, 3, 5, 2, CathedralRooms::belfry),
                            TEMPLE, TOWER, SANCTUM, MONOLITH_COURT)),
            DungeonTheme.FOUNDRY, new DungeonKit(
                    List.of(RoomKind.of("boiler_hall", 4, 8, 3, 6, 2, FoundryRooms::boilerHall), ATRIUM, KEEP),
                    List.of(RoomKind.of("smelter", 2, 4, 2, 3, 4, FoundryRooms::smelter),
                            RoomKind.of("gantry", 2, 5, 2, 4, 3, FoundryRooms::gantry),
                            RoomKind.of("store", 2, 3, 1, 2, 4, FoundryRooms::store),
                            TEMPLE, TOWER, SANCTUM, MONOLITH_COURT)),
            DungeonTheme.PALACE, new DungeonKit(
                    List.of(RoomKind.of("throne_hall", 3, 8, 3, 5, 2, PalaceRooms::throneHall), ATRIUM, KEEP),
                    List.of(RoomKind.of("courtyard", 3, 6, 2, 3, 3, PalaceRooms::courtyard),
                            RoomKind.of("bath", 2, 4, 2, 2, 3, PalaceRooms::bath),
                            RoomKind.of("tomb", 2, 3, 1, 2, 4, PalaceRooms::tomb),
                            TEMPLE, TOWER, SANCTUM, MONOLITH_COURT)),
            DungeonTheme.TEMPLE, new DungeonKit(
                    List.of(KEEP, ATRIUM, RoomKind.of("great_hall", 4, 8, 3, 6, 1, TempleRooms::hall)),
                    List.of(RoomKind.of("hall", 2, 5, 2, 3, 4, TempleRooms::hall),
                            RoomKind.of("shrine", 3, 5, 2, 4, 4, TempleRooms::shrine),
                            TEMPLE_FAVORED, TOWER_FAVORED, SANCTUM, MONOLITH_COURT)),
            DungeonTheme.FOREST, new DungeonKit(
                    List.of(RoomKind.of("grove", 4, 8, 3, 6, 3, ForestRooms::grove), ATRIUM, KEEP),
                    List.of(RoomKind.of("cabin", 3, 4, 3, 3, 3, ForestRooms::cabin),
                            RoomKind.of("pond", 2, 4, 1, 2, 3, ForestRooms::pond),
                            RoomKind.of("terraces", 3, 5, 2, 3, 3, ForestRooms::terraces),
                            RoomKind.of("glade", 3, 5, 3, 4, 2, ForestRooms::grove),
                            TOWER, SANCTUM, MONOLITH_COURT)),
            DungeonTheme.MYCOSIS, new DungeonKit(
                    List.of(RoomKind.of("village", 4, 8, 3, 5, 3, MycosisRooms::village), ATRIUM, KEEP),
                    List.of(RoomKind.of("hut", 3, 4, 2, 3, 4, MycosisRooms::hut),
                            RoomKind.of("farm", 3, 5, 2, 3, 3, MycosisRooms::farm),
                            RoomKind.of("pit", 2, 4, 1, 2, 2, MycosisRooms::pit),
                            TOWER, SANCTUM, MONOLITH_COURT))));

    public static DungeonKit of(DungeonTheme theme)
    {
        return KITS.get(theme);
    }
}
