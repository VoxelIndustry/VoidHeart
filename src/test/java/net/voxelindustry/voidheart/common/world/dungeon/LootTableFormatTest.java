package net.voxelindustry.voidheart.common.world.dungeon;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The game ignores fields it does not know without a word, so an old table loads fine and then drops the wrong things.
 */
class LootTableFormatTest
{
    private static final Path TABLES = Path.of("src/main/resources/data/voidheart/loot_table");

    @Test
    void handWrittenTables_onlyUseFieldsTheGameReads() throws IOException
    {
        try (var files = Files.walk(TABLES))
        {
            var tables = files.filter(file -> file.toString().endsWith(".json")).toList();
            assertThat(tables).isNotEmpty();
            for (var table : tables)
            {
                assertThat(Files.readString(table)).as(table.toString())
                        .doesNotContain("\"conditions\"", "\"functions\"", "\"function\"", "block_state_property")
                        .doesNotContainPattern("\"condition\"\\s*:\\s*\"");
            }
        }
    }
}
