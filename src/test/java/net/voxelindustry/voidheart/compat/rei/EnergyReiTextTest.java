package net.voxelindustry.voidheart.compat.rei;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REI only exists at compile time here, so the energy guide pages can't be opened in a game test. This reads each {@code line("key", args...)} call in
 * the energy REI classes instead, and checks that its text exists and takes exactly the arguments given.
 */
class EnergyReiTextTest
{
    private static final Path    SOURCES = Path.of("src/main/java/net/voxelindustry/voidheart");
    private static final Path    LANG    = Path.of("src/main/resources/assets/voidheart/lang/en_us.json");
    private static final Pattern LINE    = Pattern.compile("\\bline\\(\"([a-z_.]+)\"");
    private static final Pattern FORMAT  = Pattern.compile("%(?:\\d+\\$)?s");

    private record Line(String file, String key, int args)
    {
    }

    @Test
    void everyGuideLine_hasItsTextAndArguments() throws IOException
    {
        var lang = JsonParser.parseString(Files.readString(LANG)).getAsJsonObject();
        var lines = guideLines();
        assertThat(lines).as("guide lines found in the energy REI classes").hasSizeGreaterThan(30);

        for (var line : lines)
        {
            var key = "voidheart.rei." + line.key();
            assertThat(lang.has(key)).as("%s in %s", key, line.file()).isTrue();
            var text = lang.get(key).getAsString();
            assertThat(placeholders(text)).as("placeholders in %s, used by %s", key, line.file()).isEqualTo(line.args());
        }
    }

    private static List<Line> guideLines() throws IOException
    {
        var found = new ArrayList<Line>();
        var files = new ArrayList<Path>();
        files.add(SOURCES.resolve("compat/rei/EnergyRei.java"));
        try (var walk = Files.walk(SOURCES.resolve("common/energy")))
        {
            walk.filter(path -> path.getFileName().toString().endsWith("Rei.java")).forEach(files::add);
        }
        for (var file : files)
        {
            var source = Files.readString(file);
            Matcher matcher = LINE.matcher(source);
            while (matcher.find())
                found.add(new Line(file.getFileName().toString(), matcher.group(1), argumentsAfter(source, matcher.end())));
        }
        return found;
    }

    /**
     * Counts the arguments left in a call, from just after its first one to its closing parenthesis, by top level commas.
     */
    private static int argumentsAfter(String source, int from)
    {
        int depth = 0;
        int args = 0;
        for (int i = from; i < source.length(); i++)
        {
            char c = source.charAt(i);
            if (c == '(')
                depth++;
            else if (c == ')')
            {
                if (depth == 0)
                    return args;
                depth--;
            }
            else if (c == ',' && depth == 0)
                args++;
        }
        throw new IllegalStateException("Unclosed line( call");
    }

    private static int placeholders(String text)
    {
        int count = 0;
        var matcher = FORMAT.matcher(text.replace("%%", ""));
        while (matcher.find())
            count++;
        return count;
    }
}
