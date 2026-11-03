package net.voxelindustry.voidheart.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.voxelindustry.voidheart.common.content.research.Constellation;
import net.voxelindustry.voidheart.common.content.research.RecipeSheet;
import net.voxelindustry.voidheart.common.content.research.Research;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * What the repository knows about one thing: what it is for, how it is made, and what it goes into. Whatever else has a page is one click away from here.
 */
final class ResearchItemPage
{
    private static final int HEADER      = 24;
    private static final int PAD         = 8;
    private static final int LINE        = 10;
    private static final int ICON        = 9;
    private static final int ICON_V      = 141;
    private static final int CLOSE_U     = 50;
    private static final int CLOSE_SIZE  = 16;
    private static final int SLOT        = 18;
    private static final int LABEL       = 12;
    private static final int SECTION_GAP = 7;
    private static final int COLUMN_GAP  = 10;
    private static final int MAX_WIDTH   = 440;
    private static final int BUTTON_H    = 18;
    private static final int BUTTON_W    = 120;
    private static final int MARGIN      = 6;
    private static final int LINK_TINT   = 0x90;
    private static final int GRID        = 3;
    private static final int BARE_CELL   = 0xFF241E2C;

    private static final int ALTAR_U       = 70;
    private static final int ALTAR_WIDTH   = 13;
    private static final int PILLAR_U      = 84;
    private static final int PILLAR_WIDTH  = 9;
    private static final int RITE_ICON_V   = 141;
    private static final int RITE_ICON_H   = 13;
    private static final int RITE_ICON_GAP = 2;
    private static final int RITE_RULE_GAP = 4;
    private static final int CATALYST_X    = ALTAR_WIDTH + RITE_ICON_GAP;
    private static final int RITE_RULE_X   = CATALYST_X + SLOT - 1 + RITE_RULE_GAP;
    private static final int PILLAR_ICON_X = RITE_RULE_X + 1 + RITE_RULE_GAP;
    private static final int PILLARS_X     = PILLAR_ICON_X + PILLAR_WIDTH + RITE_ICON_GAP;

    private static final int  TABLET_COLOR = 0xFF0B0810;
    private static final int  RULE_COLOR   = 0xFF4A3860;
    private static final int  TEXT_COLOR   = 0xFFEDE6FF;
    private static final int  MUTED_COLOR  = 0xFFA899BC;
    private static final int  ICON_COLOR   = 0xFFA899BC;
    private static final int  ICON_HOVER   = 0xFFEDE6FF;
    // Behind every icon, so that the darkest items in the mod keep an outline against the tablet
    static final int          SLOT_PLATE   = 0xFF6A6178;

    record Rect(int x, int y, int width, int height)
    {
        boolean contains(double px, double py)
        {
            return px >= x && px < x + width && py >= y && py < y + height;
        }
    }

    private record Slot(Identifier item, Rect bounds)
    {
    }

    private record RiteIcon(Component tooltip, Rect bounds)
    {
    }

    /**
     * One titled group of things, with a line beneath it when there is more to say.
     *
     * @param cells   an empty one is a place left bare, as in a crafting pattern
     * @param columns how many cells make a row, 0 to fill the column's width
     */
    private record Section(Component label, List<Optional<RecipeSheet.Part>> cells, Optional<Component> note, int columns, boolean rite)
    {
        static Section flow(Component label, List<RecipeSheet.Part> parts, Optional<Component> note)
        {
            return new Section(label, parts.stream().map(Optional::of).toList(), note, 0, false);
        }

        static Section grid(Component label, List<Optional<Identifier>> cells)
        {
            return new Section(label, cells.stream().map(cell -> cell.map(item -> new RecipeSheet.Part(item, 1))).toList(), Optional.empty(), GRID, false);
        }

        /**
         * @param parts what goes on the altar first, then what goes on the pillars
         */
        static Section rite(Component label, List<RecipeSheet.Part> parts, Optional<Component> note)
        {
            return new Section(label, parts.stream().map(Optional::of).toList(), note, 0, true);
        }

        int rows(int width)
        {
            int perRow = columnsIn(width);
            int flowing = rite ? cells.size() - 1 : cells.size();
            return Math.max(rite ? 1 : 0, (flowing + perRow - 1) / perRow);
        }

        int columnsIn(int width)
        {
            if (rite)
                return Math.max(1, (width - PILLARS_X) / SLOT);
            return columns > 0 ? columns : Math.max(1, width / SLOT);
        }

        Rect cellBounds(int index, int x, int y, int width)
        {
            if (rite && index == 0)
                return new Rect(x + CATALYST_X, y, SLOT - 1, SLOT - 1);

            int perRow = columnsIn(width);
            int flowing = rite ? index - 1 : index;
            return new Rect(x + (rite ? PILLARS_X : 0) + flowing % perRow * SLOT, y + flowing / perRow * SLOT, SLOT - 1, SLOT - 1);
        }
    }

    private final Font              font;
    private final Identifier        item;
    private final ItemStack         stack;
    private final Constellation     constellation;
    private final Predicate<Identifier> hasPage;
    private final Supplier<Component> description;
    private final Function<Identifier, Optional<Constellation>> concealedIn;
    private final List<ResearchText.Placed> placed = new ArrayList<>();
    private final List<Section>     sections = new ArrayList<>();
    private final List<Slot>        slots    = new ArrayList<>();
    private final List<RiteIcon>    riteIcons = new ArrayList<>();
    private final Component         lockedNote;

    private Rect tablet = new Rect(0, 0, 0, 0);
    private Rect close  = new Rect(0, 0, 0, 0);
    private Rect button = new Rect(0, 0, 0, 0);

    /**
     * @param owner   the research this page belongs to
     * @param hasPage     whether another item can be opened from here
     * @param concealedIn the constellation hiding an item the player is not to see yet
     */
    ResearchItemPage(Font font, Identifier item, Identifier owner, Constellation constellation, List<RecipeSheet> sheets, List<Identifier> completed, Predicate<Identifier> hasPage, Supplier<Component> description,
                     Function<Identifier, Optional<Constellation>> concealedIn)
    {
        this.description = description;
        this.concealedIn = concealedIn;
        this.font = font;
        this.item = item;
        this.constellation = constellation;
        this.hasPage = hasPage;
        stack = new ItemStack(BuiltInRegistries.ITEM.getValue(item));

        addMaking(RecipeSheet.making(sheets, item, completed));

        var used = RecipeSheet.usedFor(sheets, item, completed);
        if (!used.isEmpty())
            sections.add(Section.flow(Component.translatable("voidheart.page.used"), used.stream().map(output -> new RecipeSheet.Part(output, 1)).toList(), Optional.empty()));

        lockedNote = RecipeSheet.hasLockedMaking(sheets, item, completed)
                ? Component.translatable("voidheart.page.locked", Component.translatable(Research.nameKey(owner)))
                : null;
    }

    /**
     * The forge takes a single item, so what it can change into the same thing reads as one choice rather than several recipes.
     */
    private void addMaking(List<RecipeSheet> making)
    {
        var alternatives = new LinkedHashMap<Integer, List<RecipeSheet>>();
        for (var sheet : making)
        {
            if (sheet.kind() == RecipeSheet.Kind.FORGE)
                alternatives.computeIfAbsent(sheet.monoliths(), monoliths -> new ArrayList<>()).add(sheet);
            else if (!sheet.gridCells().isEmpty())
                sections.add(Section.grid(Component.translatable("voidheart.page.made.crafting"), sheet.gridCells()));
            else if (sheet.kind() == RecipeSheet.Kind.CRAFTING)
                sections.add(Section.flow(Component.translatable("voidheart.page.made.crafting_any"), sheet.inputs(), noteOf(sheet)));
            else
                sections.add(Section.rite(Component.translatable("voidheart.page.made.altar"), sheet.inputs(), noteOf(sheet)));
        }

        alternatives.values().forEach(group -> sections.add(Section.flow(
                Component.translatable(group.size() > 1 ? "voidheart.page.made.forge_any" : "voidheart.page.made.forge"),
                group.stream().flatMap(sheet -> sheet.inputs().stream()).toList(),
                noteOf(group.getFirst()))));
    }

    private static Optional<Component> noteOf(RecipeSheet sheet)
    {
        if (sheet.monoliths() > 0)
            return Optional.of(Component.translatable("voidheart.page.monoliths", sheet.monoliths()));
        if (sheet.experience() > 0)
            return Optional.of(Component.translatable("voidheart.page.experience", sheet.experience()));
        return Optional.empty();
    }

    Identifier item()
    {
        return item;
    }

    private int sectionsHeight(int width)
    {
        int total = lockedNote == null ? 0 : font.split(lockedNote, width).size() * LINE + SECTION_GAP;
        for (var section : sections)
        {
            total += LABEL + section.rows(width) * SLOT + SECTION_GAP;
            if (section.note().isPresent())
                total += LINE;
        }
        return total;
    }

    /**
     * Sized to what it has to say, between the top of the screen and the bottom of the map.
     */
    private void layout(int left, int right, int windowBottom, boolean hasButton, int textHeight, int sideHeight)
    {
        int width = tablet.width();
        int body = Math.max(textHeight + (hasButton ? BUTTON_H + SECTION_GAP : 0), sideHeight);
        int height = Math.min(HEADER + body + PAD * 2, windowBottom - MARGIN);
        tablet = new Rect((left + right - width) / 2, MARGIN + (windowBottom - MARGIN - height) / 2, width, height);
        close = new Rect(tablet.x() + width - CLOSE_SIZE - 2, tablet.y() + 2, CLOSE_SIZE, CLOSE_SIZE);
    }

    /**
     * @return where the button to the scene should go, when there is one
     */
    Rect extract(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int screenWidth, int left, int right, int windowBottom, boolean hasButton)
    {
        int width = Math.min(Math.min((int) (screenWidth * 0.86F), right - left), MAX_WIDTH);
        boolean twoColumns = !sections.isEmpty() || lockedNote != null;
        int sideWidth = twoColumns ? (width - PAD * 2 - COLUMN_GAP) * 9 / 20 : 0;
        int textWidth = width - PAD * 2 - (twoColumns ? sideWidth + COLUMN_GAP : 0);

        var text = font.split(description.get(), textWidth);
        int textHeight = LINE + 4 + text.size() * LINE;

        tablet = new Rect(0, 0, width, 0);
        layout(left, right, windowBottom, hasButton, textHeight, sectionsHeight(sideWidth));

        graphics.fill(tablet.x(), tablet.y(), tablet.x() + tablet.width(), tablet.y() + tablet.height(), TABLET_COLOR);
        graphics.outline(tablet.x(), tablet.y(), tablet.width(), tablet.height(), RULE_COLOR);

        graphics.fill(tablet.x() + PAD - 3, tablet.y() + 3, tablet.x() + PAD + 15, tablet.y() + 21, SLOT_PLATE);
        graphics.item(stack, tablet.x() + PAD - 2, tablet.y() + 4);
        graphics.text(font, stack.getHoverName(), tablet.x() + PAD + 18, tablet.y() + 8, TEXT_COLOR);
        int tint = close.contains(mouseX, mouseY) ? ICON_HOVER : ICON_COLOR;
        graphics.blit(RenderPipelines.GUI_TEXTURED, ResearchMapView.SPRITES, close.x() + (CLOSE_SIZE - ICON) / 2, close.y() + (CLOSE_SIZE - ICON) / 2, CLOSE_U, ICON_V, ICON, ICON, ResearchMapView.ATLAS, ResearchMapView.ATLAS, tint);
        graphics.fill(tablet.x() + 1, tablet.y() + HEADER - 1, tablet.x() + tablet.width() - 1, tablet.y() + HEADER, RULE_COLOR);

        int x = tablet.x() + PAD;
        int y = tablet.y() + HEADER + PAD;
        int look = 0xFF000000 | ConstellationLook.of(constellation).rgb();
        ResearchMapView.sigil(graphics, constellation, x, y - 1, 255);
        graphics.text(font, Component.translatable(constellation.nameKey()), x + ResearchMapView.SIGIL_SIZE + ResearchMapView.SIGIL_GAP, y, look);
        y += LINE + 4;
        placed.clear();
        for (var line : text)
        {
            graphics.text(font, line, x, y, TEXT_COLOR, false);
            placed.add(new ResearchText.Placed(line, x, y));
            y += LINE;
        }

        slots.clear();
        riteIcons.clear();
        if (twoColumns)
            extractSide(graphics, mouseX, mouseY, tablet.x() + width - PAD - sideWidth, tablet.y() + HEADER + PAD, sideWidth, look);

        button = new Rect(x, tablet.y() + tablet.height() - PAD - BUTTON_H, Math.min(BUTTON_W, textWidth), BUTTON_H);
        return button;
    }

    private void extractSide(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int x, int top, int width, int look)
    {
        int bottom = tablet.y() + tablet.height() - PAD;
        int y = top;

        if (lockedNote != null)
        {
            for (var line : font.split(lockedNote, width))
            {
                graphics.text(font, line, x, y, MUTED_COLOR, false);
                y += LINE;
            }
            y += SECTION_GAP;
        }

        for (var section : sections)
        {
            int rows = section.rows(width);
            int needed = LABEL + rows * SLOT + (section.note().isPresent() ? LINE : 0);
            if (y + needed > bottom)
            {
                graphics.text(font, Component.translatable("voidheart.page.more"), x, Math.min(y, bottom - LINE), MUTED_COLOR, false);
                return;
            }

            graphics.text(font, section.label(), x, y, MUTED_COLOR);
            y += LABEL;
            if (section.rite())
                extractRiteFrame(graphics, x, y, rows, section.cells().size() > 1, mouseX, mouseY);
            for (int index = 0; index < section.cells().size(); index++)
            {
                var bounds = section.cellBounds(index, x, y, width);
                if (section.cells().get(index).isEmpty())
                {
                    graphics.fill(bounds.x(), bounds.y(), bounds.x() + bounds.width(), bounds.y() + bounds.height(), BARE_CELL);
                    continue;
                }

                var part = section.cells().get(index).get();
                var shown = new ItemStack(BuiltInRegistries.ITEM.getValue(part.item()), part.count());
                boolean hovered = bounds.contains(mouseX, mouseY);
                graphics.fill(bounds.x(), bounds.y(), bounds.x() + bounds.width(), bounds.y() + bounds.height(), SLOT_PLATE);
                if (hovered && hasPage.test(part.item()))
                    graphics.fill(bounds.x(), bounds.y(), bounds.x() + bounds.width(), bounds.y() + bounds.height(), (LINK_TINT << 24) | (look & 0xFFFFFF));
                var concealed = concealedIn.apply(part.item());
                if (concealed.isPresent())
                    ResearchMapView.sigil(graphics, concealed.get(), bounds.x() + (bounds.width() - ResearchMapView.SIGIL_SIZE) / 2, bounds.y() + (bounds.height() - ResearchMapView.SIGIL_SIZE) / 2, 255);
                else
                {
                    graphics.item(shown, bounds.x(), bounds.y());
                    if (part.count() > 1)
                        graphics.itemDecorations(font, shown, bounds.x(), bounds.y());
                }
                slots.add(new Slot(part.item(), bounds));
            }
            y += rows * SLOT;

            if (section.note().isPresent())
            {
                graphics.text(font, section.note().get(), x, y, MUTED_COLOR, false);
                y += LINE;
            }
            y += SECTION_GAP;
        }
    }

    /**
     * The altar's icon before what goes on it, and past a rule the pillar's icon before what goes on them.
     */
    private void extractRiteFrame(GuiGraphicsExtractor graphics, int x, int y, int rows, boolean hasPillars, int mouseX, int mouseY)
    {
        extractRiteIcon(graphics, x, y, ALTAR_U, ALTAR_WIDTH, "voidheart.page.rite.altar", mouseX, mouseY);
        if (!hasPillars)
            return;

        graphics.fill(x + RITE_RULE_X, y, x + RITE_RULE_X + 1, y + rows * SLOT - 1, RULE_COLOR);
        extractRiteIcon(graphics, x + PILLAR_ICON_X, y, PILLAR_U, PILLAR_WIDTH, "voidheart.page.rite.pillars", mouseX, mouseY);
    }

    private void extractRiteIcon(GuiGraphicsExtractor graphics, int x, int y, int u, int width, String tooltipKey, int mouseX, int mouseY)
    {
        var bounds = new Rect(x, y + (SLOT - 1 - RITE_ICON_H) / 2, width, RITE_ICON_H);
        int tint = bounds.contains(mouseX, mouseY) ? ICON_HOVER : ICON_COLOR;
        graphics.blit(RenderPipelines.GUI_TEXTURED, ResearchMapView.SPRITES, bounds.x(), bounds.y(), u, RITE_ICON_V, width, RITE_ICON_H, ResearchMapView.ATLAS, ResearchMapView.ATLAS, tint);
        riteIcons.add(new RiteIcon(Component.translatable(tooltipKey), bounds));
    }

    static String pageKey(Identifier item)
    {
        return "voidheart.page." + item.getPath();
    }

    Optional<ResearchText.Link> mentionAt(double mouseX, double mouseY)
    {
        return ResearchText.linkAt(font, placed, mouseX, mouseY);
    }

    boolean overClose(double mouseX, double mouseY)
    {
        return close.contains(mouseX, mouseY);
    }

    /**
     * The thing under the cursor that has a page of its own.
     */
    Optional<Identifier> linkAt(double mouseX, double mouseY)
    {
        return slots.stream().filter(slot -> slot.bounds().contains(mouseX, mouseY)).map(Slot::item).filter(hasPage).findFirst();
    }

    List<Component> tooltip(double mouseX, double mouseY)
    {
        if (close.contains(mouseX, mouseY))
            return List.of(Component.translatable("voidheart.page.back"));
        var mention = mentionAt(mouseX, mouseY);
        if (mention.isPresent())
            return List.of(mention.get().tooltip().copy().withColor(MUTED_COLOR));

        for (var icon : riteIcons)
        {
            if (icon.bounds().contains(mouseX, mouseY))
                return List.of(icon.tooltip());
        }

        for (var slot : slots)
        {
            if (!slot.bounds().contains(mouseX, mouseY))
                continue;

            if (concealedIn.apply(slot.item()).isPresent())
                return List.of(Component.translatable("voidheart.research.link.unknown"));

            var name = new ItemStack(BuiltInRegistries.ITEM.getValue(slot.item())).getHoverName();
            return hasPage.test(slot.item()) ? List.of(name, Component.translatable("voidheart.page.read").withColor(MUTED_COLOR)) : List.of(name);
        }
        return List.of();
    }

    Optional<int[]> mentionPosition(ResearchText.Link link)
    {
        for (var line : placed)
        {
            for (int x = 0; x < MAX_WIDTH; x++)
            {
                if (ResearchText.linkAt(font, List.of(line), line.x() + x, line.y() + 1).filter(link::equals).isPresent())
                    return Optional.of(new int[] { line.x() + x + 1, line.y() + 4 });
            }
        }
        return Optional.empty();
    }

    /**
     * @return the centers of the icons of the altar rites on this page, in the order they are drawn
     */
    List<int[]> riteIconPositions()
    {
        return riteIcons.stream().map(icon -> new int[] { icon.bounds().x() + icon.bounds().width() / 2, icon.bounds().y() + icon.bounds().height() / 2 }).toList();
    }

    int[] closePosition()
    {
        return new int[] { close.x() + close.width() / 2, close.y() + close.height() / 2 };
    }

    int[] slotPosition(Identifier target)
    {
        return slots.stream().filter(slot -> slot.item().equals(target)).findFirst()
                .map(slot -> new int[] { slot.bounds().x() + 8, slot.bounds().y() + 8 })
                .orElseThrow(() -> new IllegalStateException(target + " is not on the page of " + item));
    }
}
