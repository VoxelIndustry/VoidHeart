package net.voxelindustry.voidheart.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.voxelindustry.voidheart.client.screen.ResearchScreen.Bounds;
import net.voxelindustry.voidheart.common.content.blight.BlueprintPayloads;
import net.voxelindustry.voidheart.common.content.blight.Blueprints;

import java.util.ArrayList;
import java.util.List;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * The designs an unfolded blessed contraption shows, as plates in the research slab's hole. A click picks one and the tablet beneath holds the choice.
 */
public class BlueprintScreen extends Screen
{
    private static final Identifier SLAB       = Identifier.fromNamespaceAndPath(MODID, "dynamic/blueprint_slab");
    private static final int        DEEP_COLOR = 0xFF07050B;
    private static final int        RIM_MIN    = 20;
    private static final int        RIM_MAX    = 40;
    private static final int        PAD        = 8;
    private static final int        GAP        = 12;
    private static final int        PLATE_MAX  = 130;
    private static final int        PLATE_H    = 140;
    private static final int        ICON_SCALE = 2;
    private static final int        BUTTON_W   = 110;
    private static final int        BUTTON_H   = 18;

    private final InteractionHand                hand;
    private final List<BlueprintPayloads.Design> designs;
    private final List<Bounds>                   plates = new ArrayList<>();

    private int            left;
    private int            right;
    private int            selected = -1;
    private ResearchButton keep;

    public BlueprintScreen(InteractionHand hand, List<BlueprintPayloads.Design> designs)
    {
        super(Component.translatable("voidheart.blueprint.screen.title"));
        this.hand = hand;
        this.designs = List.copyOf(designs);
    }

    public static void receive(BlueprintPayloads.Offer offer)
    {
        Minecraft.getInstance().gui.setScreen(new BlueprintScreen(offer.hand(), offer.designs()));
    }

    @Override
    protected void init()
    {
        int rim = Math.clamp((int) (width * 0.045F), RIM_MIN, RIM_MAX);
        left = rim;
        right = width - rim;
        ResearchVoid.bakeSlab(minecraft, SLAB, width, height, left, right, height, false, seed());

        plates.clear();
        int inner = right - left - PAD * 2;
        int plateWidth = Math.min(PLATE_MAX, (inner - GAP * (designs.size() - 1)) / Math.max(1, designs.size()));
        int total = plateWidth * designs.size() + GAP * (designs.size() - 1);
        int x = (width - total) / 2;
        int y = Math.max(PAD * 4, (height - PLATE_H - BUTTON_H - GAP) / 2);
        for (int i = 0; i < designs.size(); i++)
            plates.add(new Bounds(x + i * (plateWidth + GAP), y, plateWidth, PLATE_H));

        keep = addRenderableWidget(new ResearchButton((width - BUTTON_W) / 2, y + PLATE_H + GAP, BUTTON_W, BUTTON_H, pressed -> choose()));
        keep.setMessage(Component.translatable("voidheart.blueprint.screen.choose"));
        // A resize rebuilds the widgets but keeps what was picked
        keep.visible = selected >= 0;
    }

    private long seed()
    {
        return minecraft.player == null ? 0 : minecraft.player.getUUID().getMostSignificantBits();
    }

    private void choose()
    {
        if (selected < 0 || selected >= designs.size())
            return;
        ClientPlayNetworking.send(new BlueprintPayloads.Choose(hand, designs.get(selected).blueprint()));
        onClose();
    }

    private int plateAt(double mouseX, double mouseY)
    {
        for (int i = 0; i < plates.size(); i++)
        {
            if (plates.get(i).contains(mouseX, mouseY))
                return i;
        }
        return -1;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick)
    {
        if (super.mouseClicked(event, doubleClick))
            return true;

        int plate = plateAt(event.x(), event.y());
        if (plate >= 0 && plate != selected)
        {
            select(plate);
            return true;
        }
        if (doubleClick && plate >= 0 && plate == selected)
        {
            choose();
            return true;
        }
        return false;
    }

    private void select(int plate)
    {
        selected = plate;
        keep.visible = true;
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.SCULK_CLICKING, 1.4F));
    }

    /** The number keys pick a design, the arrows move between them, and Enter or Space keeps the one picked. */
    @Override
    public boolean keyPressed(KeyEvent event)
    {
        int key = event.key();
        if (key >= InputConstants.KEY_1 && key < InputConstants.KEY_1 + designs.size())
        {
            select(key - InputConstants.KEY_1);
            return true;
        }
        if ((key == InputConstants.KEY_LEFT || key == InputConstants.KEY_RIGHT) && !designs.isEmpty())
        {
            int step = key == InputConstants.KEY_LEFT ? -1 : 1;
            select(selected < 0 ? (step > 0 ? 0 : designs.size() - 1) : Math.floorMod(selected + step, designs.size()));
            return true;
        }
        if ((key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER || key == InputConstants.KEY_SPACE) && selected >= 0)
        {
            choose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a)
    {
        graphics.fill(left, 0, right, height, DEEP_COLOR);
        graphics.blit(RenderPipelines.GUI_TEXTURED, SLAB, 0, 0, 0, 0, width, height, width, height);

        graphics.centeredText(font, title, width / 2, plates.isEmpty() ? PAD * 2 : plates.getFirst().y() - font.lineHeight - GAP, ResearchScreen.KNOWN_COLOR);

        int hovered = plateAt(mouseX, mouseY);
        for (int i = 0; i < designs.size(); i++)
            extractPlate(graphics, plates.get(i), designs.get(i), i == selected, i == hovered);

        ResearchScreen.whisper(graphics, font, Component.translatable("voidheart.blueprint.screen.whisper"), ResearchScreen.MUTED_COLOR, width / 2, height);
    }

    private void extractPlate(GuiGraphicsExtractor graphics, Bounds plate, BlueprintPayloads.Design design, boolean chosen, boolean hovered)
    {
        ResearchScreen.tablet(graphics, plate);
        if (chosen || hovered)
            graphics.outline(plate.x(), plate.y(), plate.width(), plate.height(), chosen ? ResearchScreen.ACTIVE_COLOR : ResearchScreen.FRONTIER_COLOR);

        int inner = plate.width() - PAD * 2;
        int y = plate.y() + PAD;
        var item = BuiltInRegistries.ITEM.getOptional(design.icon()).map(ItemStack::new).orElse(ItemStack.EMPTY);
        int iconSize = 16 * ICON_SCALE;
        var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(plate.x() + (plate.width() - iconSize) / 2F, y);
        pose.scale(ICON_SCALE);
        graphics.item(item, 0, 0);
        pose.popMatrix();
        y += iconSize + PAD;

        int nameColor = chosen ? ResearchScreen.ACTIVE_COLOR : ResearchScreen.KNOWN_COLOR;
        for (var line : font.split(Component.translatable(Blueprints.nameKey(design.blueprint())), inner))
        {
            graphics.centeredText(font, line, plate.x() + plate.width() / 2, y, nameColor);
            y += font.lineHeight + 1;
        }
        y += 3;
        graphics.fill(plate.x() + PAD, y, plate.x() + plate.width() - PAD, y + 1, ResearchScreen.RULE_COLOR);
        y += 5;
        var reveals = font.split(Component.translatable("voidheart.blueprint.screen.reveals"), inner);
        int revealsTop = plate.y() + plate.height() - PAD - reveals.size() * (font.lineHeight + 1) + 1;
        for (var line : font.split(Component.translatable(Blueprints.loreKey(design.blueprint())), inner))
        {
            if (y + font.lineHeight > revealsTop - 3)
                break;
            graphics.text(font, line, plate.x() + PAD, y, ResearchScreen.TEXT_COLOR);
            y += font.lineHeight + 1;
        }
        for (int i = 0; i < reveals.size(); i++)
            graphics.text(font, reveals.get(i), plate.x() + PAD, revealsTop + i * (font.lineHeight + 1), ResearchScreen.MUTED_COLOR);
    }

    /** Where a design's plate is, in GUI units, for game tests to click. */
    public int[] plateCenter(int index)
    {
        var plate = plates.get(index);
        return new int[] { plate.x() + plate.width() / 2, plate.y() + plate.height() / 2 };
    }

    public int[] keepCenter()
    {
        return new int[] { keep.getX() + keep.getWidth() / 2, keep.getY() + keep.getHeight() / 2 };
    }

    public int designCount()
    {
        return designs.size();
    }

    public Identifier designAt(int index)
    {
        return designs.get(index).blueprint();
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }

    @Override
    public void removed()
    {
        super.removed();
        minecraft.getTextureManager().release(SLAB);
    }
}
