package net.voxelindustry.voidheart.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.voxelindustry.voidheart.common.content.research.Research;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Text of the repository that can name things: [[far_sight]] in a translation reads as a link to that research, and {{void_heart}} as a link to that item's page, as much as the player is allowed to know of them.
 * A hidden research is only placed in its constellation, an item it would unlock is only the constellation's sigil and the word Unknown, and a research still to be finished carries a lock beside its name.
 */
final class ResearchText
{
    /** The lock of the controls font, added to the game's default font by assets/minecraft/font/default.json. */
    private static final String LOCK = "";
    /** The first sigil of the same font, in the order of the sigils in the research atlas. */
    private static final int FIRST_SIGIL = 0xE104;
    private static final int SIGIL_PITCH = 10;

    private static final String ITEM_PREFIX     = "item|";
    private static final String RESEARCH_PREFIX = "research|";

    private static final Pattern MENTION = Pattern.compile("\\[\\[([a-z0-9_]+)]]|\\{\\{([a-z0-9_]+)}}");

    /**
     * @param research the research named by this piece, null otherwise
     * @param item     the item named by this piece, null otherwise
     */
    record Segment(String text, String research, String item)
    {
        static Segment plain(String text)
        {
            return new Segment(text, null, null);
        }
    }

    /**
     * A line as it was drawn, kept to find what is under the cursor.
     */
    record Placed(FormattedCharSequence line, int x, int y)
    {
    }

    /**
     * Something a click on the text leads to: a page if it is an item, otherwise a research on the map.
     */
    record Link(boolean item, Identifier id)
    {
        private String encoded()
        {
            return (item ? ITEM_PREFIX : RESEARCH_PREFIX) + id;
        }

        private static Link decoded(String insertion)
        {
            boolean item = insertion.startsWith(ITEM_PREFIX);
            return new Link(item, Identifier.parse(insertion.substring((item ? ITEM_PREFIX : RESEARCH_PREFIX).length())));
        }

        Component tooltip()
        {
            return Component.translatable(item ? "voidheart.page.read" : "voidheart.research.link.show");
        }
    }

    /**
     * What a text needs to be told about the tree.
     *
     * @param ownerOf the research that unlocks an item
     */
    record Knowing(Map<Identifier, Research> tree, Function<Identifier, ResearchScreen.NodeState> stateOf, Function<Identifier, Optional<Identifier>> ownerOf)
    {
    }

    private ResearchText()
    {
    }

    static List<Segment> parse(String text)
    {
        var segments = new ArrayList<Segment>();
        var matcher = MENTION.matcher(text);
        int from = 0;
        while (matcher.find())
        {
            if (matcher.start() > from)
                segments.add(Segment.plain(text.substring(from, matcher.start())));
            segments.add(new Segment(null, matcher.group(1), matcher.group(2)));
            from = matcher.end();
        }
        if (from < text.length())
            segments.add(Segment.plain(text.substring(from)));
        return segments;
    }

    static Component of(String key, Knowing knowing)
    {
        MutableComponent built = Component.empty();
        for (var segment : parse(I18n.get(key)))
        {
            if (segment.research() != null)
                built.append(mention(Identifier.fromNamespaceAndPath(MODID, segment.research()), knowing));
            else if (segment.item() != null)
                built.append(itemMention(Identifier.fromNamespaceAndPath(MODID, segment.item()), knowing));
            else
                built.append(Component.literal(segment.text()));
        }
        return built;
    }

    private static Component mention(Identifier id, Knowing knowing)
    {
        var research = knowing.tree().get(id);
        if (research == null)
            return Component.translatable(Research.nameKey(id));

        int rgb = ConstellationLook.of(research.constellation()).rgb();
        var state = knowing.stateOf().apply(id);
        if (state == ResearchScreen.NodeState.HIDDEN)
            return Component.translatable("voidheart.research.link.hidden", Component.translatable(research.constellation().nameKey()).withColor(rgb));

        MutableComponent name = Component.translatable(Research.nameKey(id)).withStyle(style -> style.withColor(rgb).withUnderlined(true).withInsertion(new Link(false, id).encoded()));
        if (state != ResearchScreen.NodeState.DONE)
            name.append(Component.literal(" " + LOCK).withStyle(style -> style.withUnderlined(false)));
        return name;
    }

    private static Component itemMention(Identifier id, Knowing knowing)
    {
        var name = new ItemStack(BuiltInRegistries.ITEM.getValue(id)).getHoverName();
        var owner = knowing.ownerOf().apply(id);
        if (owner.isEmpty())
            return name;

        var research = knowing.tree().get(owner.get());
        int rgb = ConstellationLook.of(research.constellation()).rgb();
        if (knowing.stateOf().apply(owner.get()) == ResearchScreen.NodeState.HIDDEN)
            return unknown(research);

        return name.copy().withStyle(style -> style.withColor(rgb).withUnderlined(true).withInsertion(new Link(true, id).encoded()));
    }

    private static Component unknown(Research research)
    {
        var look = ConstellationLook.of(research.constellation());
        return Component.literal(Character.toString(FIRST_SIGIL + look.sigilU() / SIGIL_PITCH) + " ").withColor(look.rgb())
                .append(Component.translatable("voidheart.research.link.unknown"));
    }

    /**
     * What is named at this point of the drawn lines, if it can be followed.
     */
    static Optional<Link> linkAt(Font font, List<Placed> lines, double mouseX, double mouseY)
    {
        for (var placed : lines)
        {
            if (mouseY < placed.y() || mouseY >= placed.y() + font.lineHeight)
                continue;

            float target = (float) mouseX - placed.x();
            float[] at = { 0 };
            String[] found = { null };
            placed.line().accept((index, style, codepoint) ->
            {
                int width = font.width(Character.toString(codepoint));
                if (target >= at[0] && target < at[0] + width)
                {
                    found[0] = style.getInsertion();
                    return false;
                }
                at[0] += width;
                return true;
            });
            if (found[0] != null)
                return Optional.of(Link.decoded(found[0]));
        }
        return Optional.empty();
    }
}
