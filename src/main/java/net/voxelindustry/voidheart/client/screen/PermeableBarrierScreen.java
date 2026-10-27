package net.voxelindustry.voidheart.client.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
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
import java.util.Optional;
import java.util.UUID;

public class PermeableBarrierScreen extends AbstractContainerScreen<PermeableBarrierMenu>
{
    private static final int PANEL_COLOR  = 0xFFC6C6C6;
    private static final int LABEL_COLOR  = 0xFF404040;
    private static final int WARN_COLOR   = 0xFF8B1A1A;
    private static final int SIDE_WIDTH   = 104;
    private static final int SIDE_GAP     = 4;
    private static final int SIDE_ROWS    = 11;
    private static final int ROW_HEIGHT   = 14;
    private static final int APPEARANCE_X = 8;
    private static final int APPEARANCE_Y = 18;

    private final List<Button> flagButtons     = new ArrayList<>();
    private final List<Button> coOwnerButtons  = new ArrayList<>();
    private       Button       teamButton;
    private       List<CoOwner> coOwnerEntries = List.of();
    private       int          coOwnerScroll;

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

    @Override
    protected void init()
    {
        super.init();
        leftPos -= (SIDE_WIDTH + SIDE_GAP) / 2;

        flagButtons.clear();
        var flags = Flag.values();
        for (int i = 0; i < flags.length; i++)
        {
            var flag = flags[i];
            var key = "gui.voidheart.permeable_barrier.flag." + flag.name().toLowerCase(Locale.ROOT);
            var button = Button.builder(Component.translatable(key), pressed -> send(new BarrierConfigurePayload(Action.TOGGLE_FLAG, flag.ordinal(), Optional.empty())))
                    .bounds(leftPos + 8 + (i / 3) * 82, topPos + 42 + (i % 3) * 18, 78, 16)
                    .tooltip(Tooltip.create(Component.translatable(key + ".tooltip")))
                    .build();
            flagButtons.add(addRenderableWidget(button));
        }

        teamButton = addRenderableWidget(Button.builder(Component.empty(), pressed -> send(BarrierConfigurePayload.of(Action.TOGGLE_TEAM)))
                .bounds(leftPos + 8, topPos + 98, 160, 16)
                .tooltip(Tooltip.create(Component.translatable("gui.voidheart.permeable_barrier.team.tooltip")))
                .build());

        rebuildCoOwnerButtons();
        refreshLabels();
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
        coOwnerScroll = Math.clamp(coOwnerScroll, 0, Math.max(0, coOwnerEntries.size() - SIDE_ROWS));

        int x = leftPos + imageWidth + SIDE_GAP + 4;
        for (int row = 0; row < SIDE_ROWS && coOwnerScroll + row < coOwnerEntries.size(); row++)
        {
            var entry = coOwnerEntries.get(coOwnerScroll + row);
            var button = Button.builder(Component.literal(entry.name()), pressed -> send(new BarrierConfigurePayload(Action.TOGGLE_CO_OWNER, 0, Optional.of(entry))))
                    .bounds(x, topPos + 18 + row * (ROW_HEIGHT + 1), SIDE_WIDTH - 8, ROW_HEIGHT)
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
        var flags = Flag.values();
        for (int i = 0; i < flags.length; i++)
        {
            var key = "gui.voidheart.permeable_barrier.flag." + flags[i].name().toLowerCase(Locale.ROOT);
            flagButtons.get(i).setMessage(checkbox(config.has(flags[i]), Component.translatable(key)));
        }

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

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick)
    {
        if (isOverAppearance(event.x(), event.y()))
        {
            if (event.button() == 1)
                send(BarrierConfigurePayload.of(Action.CLEAR_APPEARANCE));
            else if (!menu.getCarried().isEmpty())
                send(BarrierConfigurePayload.of(Action.SET_APPEARANCE));
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY)
    {
        int sideLeft = leftPos + imageWidth + SIDE_GAP;
        if (x >= sideLeft && x < sideLeft + SIDE_WIDTH && coOwnerEntries.size() > SIDE_ROWS)
        {
            coOwnerScroll -= (int) Math.signum(scrollY);
            rebuildCoOwnerButtons();
            refreshLabels();
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a)
    {
        super.extractBackground(graphics, mouseX, mouseY, a);

        drawPanel(graphics, leftPos, topPos, imageWidth, imageHeight);
        drawPanel(graphics, leftPos + imageWidth + SIDE_GAP, topPos, SIDE_WIDTH, 18 + SIDE_ROWS * (ROW_HEIGHT + 1) + 6);

        for (var slot : menu.slots)
            drawSlot(graphics, leftPos + slot.x - 1, topPos + slot.y - 1);
        drawSlot(graphics, leftPos + APPEARANCE_X - 1, topPos + APPEARANCE_Y - 1);

        var appearance = config().appearance();
        appearance.ifPresent(state -> graphics.fakeItem(new ItemStack(state.getBlock()), leftPos + APPEARANCE_X, topPos + APPEARANCE_Y));
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

        graphics.text(font, Component.translatable("gui.voidheart.permeable_barrier.co_owners"), imageWidth + SIDE_GAP + 6, 6, LABEL_COLOR, false);
        if (coOwnerEntries.isEmpty())
            graphics.text(font, Component.translatable("gui.voidheart.permeable_barrier.co_owners.empty"), imageWidth + SIDE_GAP + 6, 20, LABEL_COLOR, false);
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
