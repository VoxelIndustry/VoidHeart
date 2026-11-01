package net.voxelindustry.voidheart.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.voxelindustry.voidheart.common.content.permeablebarrier.BarrierConfig;
import net.voxelindustry.voidheart.common.content.permeablebarrier.BarrierConfig.CoOwner;
import net.voxelindustry.voidheart.common.content.permeablebarrier.BarrierConfig.Flag;
import net.voxelindustry.voidheart.common.content.permeablebarrier.BarrierConfigurePayload;
import net.voxelindustry.voidheart.common.content.permeablebarrier.BarrierConfigurePayload.Action;
import net.voxelindustry.voidheart.common.content.permeablebarrier.PermeableBarrierMenu;
import net.voxelindustry.voidheart.common.content.permeablebarrier.PermeableBarrierStructure;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Main panel centered like any container screen, with the owners pane attached on its right.
 */
public class PermeableBarrierScreen extends AbstractContainerScreen<PermeableBarrierMenu>
{
    private static final int PANEL_COLOR   = 0xFFC6C6C6;
    private static final int LABEL_COLOR   = 0xFF404040;
    private static final int DIVIDER_COLOR = 0xFF8B8B8B;
    private static final int WARN_COLOR    = 0xFF8B1A1A;

    private static final int APPEARANCE_X = 8;
    private static final int APPEARANCE_Y = 18;

    private static final int SECTION_Y       = 40;
    private static final int TOGGLES_Y       = SECTION_Y + 10;
    private static final int FILTERS_X       = 8;
    private static final int CONTROLS_X      = 92;
    private static final int TOGGLE_SPACING  = IconToggleButton.SIZE + 2;

    private static final int SIDE_WIDTH    = 104;
    private static final int SIDE_GAP      = 4;
    private static final int TEAM_Y        = 18;
    private static final int CO_OWNERS_Y   = 40;
    private static final int ROWS_Y        = CO_OWNERS_Y + 11;
    private static final int ROW_HEIGHT    = 14;

    private static final Map<Flag, ItemStack> ICONS = Map.of(
            Flag.ALLIES, new ItemStack(Items.PLAYER_HEAD),
            Flag.PEACEFUL_MOBS, new ItemStack(Items.COW_SPAWN_EGG),
            Flag.HOSTILE_MOBS, new ItemStack(Items.ZOMBIE_HEAD),
            Flag.INVERTED, new ItemStack(Items.REDSTONE_TORCH),
            Flag.REDSTONE, new ItemStack(Items.LEVER),
            Flag.INVISIBLE_WHEN_OPEN, new ItemStack(Items.GLASS)
    );
    private static final Map<Flag, Identifier> OFF_SPRITES = Map.of(
            Flag.INVERTED, Identifier.withDefaultNamespace("block/redstone_torch_off")
    );
    private static final List<Flag> FILTERS  = List.of(Flag.ALLIES, Flag.PEACEFUL_MOBS, Flag.HOSTILE_MOBS);
    private static final List<Flag> CONTROLS = List.of(Flag.INVERTED, Flag.REDSTONE, Flag.INVISIBLE_WHEN_OPEN);

    private final Map<Flag, IconToggleButton> flagButtons    = new LinkedHashMap<>();
    private final List<Button>                coOwnerButtons = new ArrayList<>();
    private       Button                      teamButton;
    private       List<CoOwner>               coOwnerEntries = List.of();
    private       int                         coOwnerScroll;
    private       int                         sideLeft;

    public PermeableBarrierScreen(PermeableBarrierMenu menu, Inventory inventory, Component title)
    {
        super(menu, inventory, title, 176, PermeableBarrierMenu.INVENTORY_TOP + 82);
    }

    private BarrierConfig config()
    {
        var tile = PermeableBarrierStructure.getTile(minecraft.level, menu.getPos());
        return tile != null ? tile.getConfig() : BarrierConfig.EMPTY;
    }

    private static void send(BarrierConfigurePayload payload)
    {
        ClientPlayNetworking.send(payload);
    }

    private static String flagKey(Flag flag)
    {
        return "gui.voidheart.permeable_barrier.flag." + flag.name().toLowerCase(Locale.ROOT);
    }

    private int sideRows()
    {
        return (imageHeight - ROWS_Y - 6) / (ROW_HEIGHT + 1);
    }

    @Override
    protected void init()
    {
        super.init();

        // Only shift the centered panel when the owners pane would leave the screen
        int overflow = leftPos + imageWidth + SIDE_GAP + SIDE_WIDTH - (width - 4);
        if (overflow > 0)
            leftPos = Math.max(4, leftPos - overflow);
        sideLeft = leftPos + imageWidth + SIDE_GAP;

        flagButtons.clear();
        addFlagButtons(FILTERS, FILTERS_X);
        addFlagButtons(CONTROLS, CONTROLS_X);

        teamButton = addRenderableWidget(Button.builder(Component.empty(), pressed -> send(BarrierConfigurePayload.of(Action.TOGGLE_TEAM)))
                .bounds(sideLeft + 4, topPos + TEAM_Y, SIDE_WIDTH - 8, 16)
                .tooltip(Tooltip.create(Component.translatable("gui.voidheart.permeable_barrier.team.tooltip")))
                .build());

        rebuildCoOwnerButtons();
        refreshLabels();
    }

    private void addFlagButtons(List<Flag> flags, int sectionX)
    {
        for (int i = 0; i < flags.size(); i++)
        {
            var flag = flags.get(i);
            var button = new IconToggleButton(leftPos + sectionX + i * TOGGLE_SPACING, topPos + TOGGLES_Y, ICONS.get(flag), OFF_SPRITES.get(flag),
                    Component.translatable(flagKey(flag)), () -> config().has(flag),
                    pressed -> send(new BarrierConfigurePayload(Action.TOGGLE_FLAG, flag.ordinal(), Optional.empty())));
            flagButtons.put(flag, addRenderableWidget(button));
        }
    }

    private List<CoOwner> collectCoOwnerEntries()
    {
        var self = minecraft.player.getUUID();
        var entries = new LinkedHashMap<UUID, CoOwner>();
        for (var info : minecraft.getConnection().getOnlinePlayers())
        {
            var profile = info.getProfile();
            if (!profile.id().equals(self))
                entries.put(profile.id(), new CoOwner(profile.id(), profile.name()));
        }
        // Co-owners that are offline stay listed so they can still be removed
        for (var coOwner : config().coOwners())
            entries.putIfAbsent(coOwner.id(), coOwner);
        return List.copyOf(entries.values());
    }

    private void rebuildCoOwnerButtons()
    {
        coOwnerButtons.forEach(this::removeWidget);
        coOwnerButtons.clear();

        coOwnerEntries = collectCoOwnerEntries();
        coOwnerScroll = Math.clamp(coOwnerScroll, 0, Math.max(0, coOwnerEntries.size() - sideRows()));

        for (int row = 0; row < sideRows() && coOwnerScroll + row < coOwnerEntries.size(); row++)
        {
            var entry = coOwnerEntries.get(coOwnerScroll + row);
            var button = Button.builder(Component.literal(entry.name()), pressed -> send(new BarrierConfigurePayload(Action.TOGGLE_CO_OWNER, 0, Optional.of(entry))))
                    .bounds(sideLeft + 4, topPos + ROWS_Y + row * (ROW_HEIGHT + 1), SIDE_WIDTH - 8, ROW_HEIGHT)
                    .build();
            coOwnerButtons.add(addRenderableWidget(button));
        }
    }

    private static Component checkbox(boolean checked, Component label)
    {
        return Component.literal(checked ? "■ " : "□ ").withStyle(checked ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY).append(label.copy().withStyle(ChatFormatting.WHITE));
    }

    private void refreshLabels()
    {
        var config = config();
        flagButtons.forEach((flag, button) ->
        {
            var state = Component.translatable(config.has(flag) ? "gui.voidheart.permeable_barrier.on" : "gui.voidheart.permeable_barrier.off")
                    .withStyle(config.has(flag) ? ChatFormatting.GREEN : ChatFormatting.GRAY);
            button.setTooltip(Tooltip.create(Component.translatable(flagKey(flag)).withStyle(ChatFormatting.WHITE)
                    .append(" ").append(state)
                    .append("\n").append(Component.translatable(flagKey(flag) + ".tooltip").withStyle(ChatFormatting.GRAY))));
        });

        var team = config.ownerTeam();
        teamButton.setMessage(team
                .map(name -> checkbox(true, Component.translatable("gui.voidheart.permeable_barrier.team", name)))
                .orElseGet(() -> checkbox(false, Component.translatable("gui.voidheart.permeable_barrier.team.none"))));
        teamButton.active = team.isPresent() || minecraft.player.getTeam() != null;

        for (int row = 0; row < coOwnerButtons.size(); row++)
        {
            var entry = coOwnerEntries.get(coOwnerScroll + row);
            boolean isCoOwner = config.coOwners().stream().anyMatch(coOwner -> coOwner.id().equals(entry.id()));
            coOwnerButtons.get(row).setMessage(checkbox(isCoOwner, Component.literal(entry.name())));
        }
    }

    @Override
    protected void containerTick()
    {
        super.containerTick();

        if (!collectCoOwnerEntries().equals(coOwnerEntries))
            rebuildCoOwnerButtons();
        refreshLabels();
    }

    private boolean isOverAppearance(double mouseX, double mouseY)
    {
        return isHovering(APPEARANCE_X, APPEARANCE_Y, 16, 16, mouseX, mouseY);
    }

    /**
     * Either mouse button picks the carried block as the look, or clears it with an empty cursor.
     */
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick)
    {
        if (isOverAppearance(event.x(), event.y()) && (event.button() == InputConstants.MOUSE_BUTTON_LEFT || event.button() == InputConstants.MOUSE_BUTTON_RIGHT))
        {
            send(BarrierConfigurePayload.of(menu.getCarried().isEmpty() ? Action.CLEAR_APPEARANCE : Action.SET_APPEARANCE));
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY)
    {
        if (x >= sideLeft && x < sideLeft + SIDE_WIDTH && coOwnerEntries.size() > sideRows())
        {
            coOwnerScroll -= (int) Math.signum(scrollY);
            rebuildCoOwnerButtons();
            refreshLabels();
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top)
    {
        boolean inSide = mouseX >= sideLeft && mouseX < sideLeft + SIDE_WIDTH && mouseY >= topPos && mouseY < topPos + imageHeight;
        return !inSide && super.hasClickedOutside(mouseX, mouseY, left, top);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a)
    {
        super.extractBackground(graphics, mouseX, mouseY, a);

        drawPanel(graphics, leftPos, topPos, imageWidth, imageHeight);
        drawPanel(graphics, sideLeft, topPos, SIDE_WIDTH, imageHeight);

        for (var slot : menu.slots)
            drawSlot(graphics, leftPos + slot.x - 1, topPos + slot.y - 1);
        drawSlot(graphics, leftPos + APPEARANCE_X - 1, topPos + APPEARANCE_Y - 1);

        int dividerX = leftPos + CONTROLS_X - 6;
        graphics.verticalLine(dividerX, topPos + SECTION_Y - 2, topPos + TOGGLES_Y + IconToggleButton.SIZE, DIVIDER_COLOR);
        graphics.horizontalLine(leftPos + 7, leftPos + imageWidth - 8, topPos + SECTION_Y - 4, DIVIDER_COLOR);
        graphics.horizontalLine(sideLeft + 4, sideLeft + SIDE_WIDTH - 5, topPos + CO_OWNERS_Y - 4, DIVIDER_COLOR);

        config().appearance().ifPresent(state -> graphics.fakeItem(new ItemStack(state.getBlock()), leftPos + APPEARANCE_X, topPos + APPEARANCE_Y));
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int xm, int ym)
    {
        super.extractLabels(graphics, xm, ym);

        var config = config();
        var appearanceLabel = config.isConfigured()
                ? Component.translatable("gui.voidheart.permeable_barrier.appearance")
                : Component.translatable("gui.voidheart.permeable_barrier.unconfigured");
        graphics.text(font, appearanceLabel, APPEARANCE_X + 22, APPEARANCE_Y + 4, config.isConfigured() ? LABEL_COLOR : WARN_COLOR, false);

        graphics.text(font, Component.translatable("gui.voidheart.permeable_barrier.filters"), FILTERS_X, SECTION_Y, LABEL_COLOR, false);
        graphics.text(font, Component.translatable("gui.voidheart.permeable_barrier.controls"), CONTROLS_X, SECTION_Y, LABEL_COLOR, false);

        int side = sideLeft - leftPos;
        graphics.text(font, Component.translatable("gui.voidheart.permeable_barrier.owners"), side + 6, 6, LABEL_COLOR, false);
        graphics.text(font, Component.translatable("gui.voidheart.permeable_barrier.co_owners"), side + 6, CO_OWNERS_Y, LABEL_COLOR, false);
        if (coOwnerEntries.isEmpty())
            graphics.text(font, Component.translatable("gui.voidheart.permeable_barrier.co_owners.empty"), side + 6, ROWS_Y + 2, LABEL_COLOR, false);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY)
    {
        super.extractTooltip(graphics, mouseX, mouseY);

        if (isOverAppearance(mouseX, mouseY) && menu.getCarried().isEmpty())
            graphics.setTooltipForNextFrame(font, Component.translatable("gui.voidheart.permeable_barrier.appearance.tooltip"), mouseX, mouseY);
    }

    private static void drawPanel(GuiGraphicsExtractor graphics, int x, int y, int width, int height)
    {
        graphics.fill(x, y, x + width, y + height, PANEL_COLOR);
        graphics.outline(x, y, width, height, 0xFF000000);
        graphics.horizontalLine(x + 1, x + width - 2, y + 1, 0xFFFFFFFF);
        graphics.verticalLine(x + 1, y + 1, y + height - 2, 0xFFFFFFFF);
        graphics.horizontalLine(x + 1, x + width - 2, y + height - 2, 0xFF555555);
        graphics.verticalLine(x + width - 2, y + 1, y + height - 2, 0xFF555555);
    }

    private static void drawSlot(GuiGraphicsExtractor graphics, int x, int y)
    {
        graphics.fill(x, y, x + 18, y + 18, 0xFF8B8B8B);
        graphics.horizontalLine(x, x + 16, y, 0xFF373737);
        graphics.verticalLine(x, y, y + 17, 0xFF373737);
        graphics.horizontalLine(x + 1, x + 17, y + 17, 0xFFFFFFFF);
        graphics.verticalLine(x + 17, y, y + 17, 0xFFFFFFFF);
    }
}
