package net.voxelindustry.voidheart.client.screen;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResearchTextTest
{
    private static ResearchText.Segment research(String path)
    {
        return new ResearchText.Segment(null, path, null);
    }

    private static ResearchText.Segment item(String path)
    {
        return new ResearchText.Segment(null, null, path);
    }

    @Test
    void parse_findsNumbersByName()
    {
        assertThat(ResearchText.parse("Within ((column_radius)) blocks")).containsExactly(
                ResearchText.Segment.plain("Within "),
                new ResearchText.Segment(null, null, null, "column_radius"),
                ResearchText.Segment.plain(" blocks"));
    }

    @Test
    void parse_leavesPlainTextAlone()
    {
        assertThat(ResearchText.parse("Nothing to see.")).containsExactly(ResearchText.Segment.plain("Nothing to see."));
    }

    @Test
    void parse_findsAMentionInItsSentence()
    {
        assertThat(ResearchText.parse("Comes with [[far_sight]] first."))
                .containsExactly(ResearchText.Segment.plain("Comes with "), research("far_sight"), ResearchText.Segment.plain(" first."));
    }

    @Test
    void parse_tellsItemsFromResearches()
    {
        assertThat(ResearchText.parse("A {{void_heart}} from [[void_flesh]]"))
                .containsExactly(ResearchText.Segment.plain("A "), item("void_heart"), ResearchText.Segment.plain(" from "), research("void_flesh"));
    }

    @Test
    void parse_findsMentionsAtBothEnds()
    {
        assertThat(ResearchText.parse("[[a]] and {{b}}"))
                .containsExactly(research("a"), ResearchText.Segment.plain(" and "), item("b"));
    }

    @Test
    void parse_ignoresWhatIsNotAMention()
    {
        assertThat(ResearchText.parse("[far_sight] {void_heart} [[Not One]]")).containsExactly(ResearchText.Segment.plain("[far_sight] {void_heart} [[Not One]]"));
    }
}
