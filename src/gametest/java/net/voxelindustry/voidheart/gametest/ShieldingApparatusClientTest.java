package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import eu.pb4.trinkets.api.TrinketsApi;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.voxelindustry.voidheart.client.apparatus.ShieldHits;
import net.voxelindustry.voidheart.client.screen.ApparatusScreen;
import net.voxelindustry.voidheart.common.content.apparatus.ApparatusComponent;
import net.voxelindustry.voidheart.common.content.apparatus.ApparatusGrid;
import net.voxelindustry.voidheart.common.content.apparatus.ApparatusHolder;
import net.voxelindustry.voidheart.common.content.apparatus.ApparatusMenu;
import net.voxelindustry.voidheart.common.content.apparatus.ShieldState;
import net.voxelindustry.voidheart.common.content.apparatus.ShieldingApparatus;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;
import org.jspecify.annotations.Nullable;

import java.util.function.Function;

import static net.voxelindustry.voidheart.gametest.GameTestKit.expect;
import static net.voxelindustry.voidheart.gametest.GameTestKit.player;

/**
 * Components are set in a Lesser apparatus with real clicks and drags in its grid beside the inventory, refused where they overlap, taken back onto the
 * cursor or into the inventory, and shift clicked into the first spot they fit, while the apparatus' own slot stays locked. Its grid opens from the hand,
 * from a right click in the inventory and from a right click on the slot it is worn in, and its tooltip lists what is set. Worn, it shows on the torso and
 * its shield fills after the wait, shown as rune shields over the armor row; it takes a blow before health with runes lit where it landed, breaks into
 * its Mending Core's heal and lets falls through.
 */
public class ShieldingApparatusClientTest implements FabricClientGameTest
{
    private static final int    GROUND_Y       = -60;
    private static final float  CAPACITY       = 4 + 3.5F + 1 + 1;
    private static final String SLOT           = "chest/shielding_apparatus";
    private static final int    FILL_TICKS_MAX = 600;
    private static final int    CORE_SLOT      = 9;
    private static final int    BANK_SLOT      = 10;
    private static final int    CELL_SLOT      = 11;
    private static final int    SPARE_SLOT     = 12;

    @Override
    public void runTest(ClientGameTestContext context)
    {
        try (var world = context.worldBuilder().create())
        {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            server.runCommand("time set noon");
            server.runCommand("gamerule advance_time false");
            server.runCommand("gamerule spawn_mobs false");
            server.runCommand("gamemode survival @a");
            server.runCommand("clear @a");
            server.runCommand("tp @a 0.5 " + GROUND_Y + " 0.5 0 0");
            context.waitTicks(10);
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));

            setComponents(context, server);
            openFromInventory(context, server);
            equip(context, server);
            editWorn(context, server);
            fill(context, server);
            showWorn(context, server);
            blockAHit(context, server);
            breakTheShield(server);
            letAFallThrough(server);
            unequip(context, server);
        }
    }

    private static void setComponents(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            var inventory = player(minecraft).getInventory();
            inventory.setItem(0, new ItemStack(VoidHeartItems.LESSER_SHIELDING_APPARATUS));
            inventory.setSelectedSlot(0);
            inventory.setItem(CORE_SLOT, component(ApparatusComponent.MENDING_CORE, 1));
            inventory.setItem(BANK_SLOT, component(ApparatusComponent.BANK, 1));
            inventory.setItem(CELL_SLOT, component(ApparatusComponent.CELL, 3));
        });
        context.waitTicks(5);
        context.getInput().pressKey(options -> options.keyUse);
        context.waitTicks(10);
        expectGrid(context, new ApparatusHolder.Carried(0), "using the apparatus in hand");
        double scale = context.computeOnClient(minecraft -> minecraft.getWindow().getGuiScale());
        context.getInput().setCursorPos(10, 10);
        context.waitTicks(2);
        context.takeScreenshot("apparatus-grid-empty");

        // Picked up from its slot, then set with a click; the core covers its middle cell, so the click lands one right of its corner
        click(context, scale, screen -> screen.slotCenter(CORE_SLOT));
        click(context, scale, screen -> screen.cellCenter(1, 0));
        context.waitTicks(5);
        expectAt(server, 0, 0, ApparatusComponent.MENDING_CORE, "a core picked up and clicked onto the grid");
        expect(carriedEmpty(context), "Expected the core gone from the cursor once set");

        // Dragged from its slot and let go over the grid
        aim(context, scale, screen -> screen.slotCenter(BANK_SLOT));
        context.getInput().holdMouse(InputConstants.MOUSE_BUTTON_LEFT);
        context.waitTicks(2);
        aim(context, scale, screen -> screen.cellCenter(1, 2));
        context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
        context.waitTicks(5);
        expectAt(server, 0, 2, ApparatusComponent.BANK, "a bank dragged from its slot onto the bottom row");

        click(context, scale, screen -> screen.slotCenter(CELL_SLOT));
        aim(context, scale, screen -> screen.cellCenter(2, 0));
        context.takeScreenshot("apparatus-grid-ghost");
        aim(context, scale, screen -> screen.cellCenter(1, 1));
        context.takeScreenshot("apparatus-grid-refused");
        click(context, scale, screen -> screen.cellCenter(1, 1));
        context.waitTicks(5);
        expect(grid(server).at(1, 1).component() == ApparatusComponent.MENDING_CORE, "Expected a cell on the core to be refused");
        expect(carriedCount(context) == 3, "Expected the refused cell still on the cursor, carrying " + carriedCount(context));

        click(context, scale, screen -> screen.cellCenter(2, 0));
        click(context, scale, screen -> screen.cellCenter(0, 1));
        context.waitTicks(5);
        expectAt(server, 2, 0, ApparatusComponent.CELL, "a cell clicked into the free corner");
        expectAt(server, 0, 1, ApparatusComponent.CELL, "a cell clicked beside the core");
        click(context, scale, screen -> screen.slotCenter(CELL_SLOT));
        context.waitTicks(5);
        expect(count(server, ApparatusComponent.CELL) == 1, "Expected the last cell put back in its slot");

        // A piece clicked comes onto the cursor and is dropped in an empty slot
        click(context, scale, screen -> screen.cellCenter(2, 0));
        context.waitTicks(5);
        expect(grid(server).at(2, 0) == null && carriedCount(context) == 1, "Expected the corner cell taken onto the cursor");
        click(context, scale, screen -> screen.slotCenter(SPARE_SLOT));
        context.waitTicks(5);
        expect(count(server, ApparatusComponent.CELL) == 2 && carriedEmpty(context), "Expected the taken cell dropped in the spare slot");

        // A piece dragged from one cell to another moves there
        aim(context, scale, screen -> screen.cellCenter(0, 1));
        context.getInput().holdMouse(InputConstants.MOUSE_BUTTON_LEFT);
        context.waitTicks(2);
        aim(context, scale, screen -> screen.cellCenter(2, 0));
        context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
        context.waitTicks(5);
        expectAt(server, 2, 0, ApparatusComponent.CELL, "a cell dragged to the corner");
        expect(grid(server).at(0, 1) == null && carriedEmpty(context), "Expected the dragged cell gone from where it was");

        // Shift clicking a component in the inventory sets it in the first spot it fits, and shift clicking a piece puts it away. The test input sends
        // mouse clicks without modifier keys, so these go through the same container calls a shift click makes
        quickMove(context, CELL_SLOT);
        expectAt(server, 0, 1, ApparatusComponent.CELL, "a cell shift clicked from the inventory into the first free cell");
        shiftTake(context, 2, 0);
        expect(grid(server).at(2, 0) == null && count(server, ApparatusComponent.CELL) == 2, "Expected a shift clicked piece put back in the inventory");
        quickMove(context, SPARE_SLOT);
        expectAt(server, 2, 0, ApparatusComponent.CELL, "the cell shift clicked back into the corner");

        // The apparatus' own slot can't be picked up nor swapped away with a number key
        click(context, scale, screen -> screen.slotCenter(0));
        context.waitTicks(3);
        aim(context, scale, screen -> screen.slotCenter(BANK_SLOT));
        context.getInput().pressKey(InputConstants.KEY_1);
        context.waitTicks(5);
        boolean locked = server.computeOnServer(minecraft -> player(minecraft).getInventory().getItem(0).is(VoidHeartItems.LESSER_SHIELDING_APPARATUS));
        expect(locked && carriedEmpty(context), "Expected the apparatus kept in its slot while its grid is open");

        expect(grid(server).placements().size() == 4, "Expected four pieces set, got " + grid(server).placements());
        var stats = server.computeOnServer(minecraft -> ShieldingApparatus.stats(player(minecraft).getInventory().getItem(0)));
        expect(stats != null && stats.capacity() == CAPACITY, "Expected a capacity of " + CAPACITY + ", got " + (stats == null ? null : stats.capacity()));

        aim(context, scale, screen -> screen.cellCenter(1, 0));
        context.takeScreenshot("apparatus-grid-set");
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitTicks(5);
    }

    private static void openFromInventory(ClientGameTestContext context, TestServerContext server)
    {
        context.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
        context.waitTicks(3);
        double scale = context.computeOnClient(minecraft -> minecraft.getWindow().getGuiScale());
        int x = context.computeOnClient(minecraft -> minecraft.getWindow().getGuiScaledWidth() / 2 - 88 + 16);
        int y = context.computeOnClient(minecraft -> minecraft.getWindow().getGuiScaledHeight() / 2 - 83 + 150);
        context.getInput().setCursorPos(x * scale, y * scale);
        context.waitTicks(3);
        context.takeScreenshot("apparatus-tooltip");

        var lines = context.computeOnClient(minecraft -> minecraft.player.getInventory().getItem(0)
                .getTooltipLines(Item.TooltipContext.of(minecraft.level), minecraft.player, TooltipFlag.NORMAL).stream().map(line -> line.getString()).toList());
        expect(lines.stream().anyMatch(line -> line.contains("Mending Core")) && lines.stream().anyMatch(line -> line.contains("2× Ward Cell")),
                "Expected the tooltip to list the components set, got " + lines);
        boolean image = context.computeOnClient(minecraft -> minecraft.player.getInventory().getItem(0).getTooltipImage().isPresent());
        expect(image, "Expected the tooltip to draw the grid");

        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
        context.waitTicks(10);
        expectGrid(context, new ApparatusHolder.Carried(0), "a right click on the apparatus in the inventory");
        boolean kept = server.computeOnServer(minecraft -> player(minecraft).getInventory().getItem(0).is(VoidHeartItems.LESSER_SHIELDING_APPARATUS));
        expect(kept && carriedEmpty(context), "Expected the right click to leave the apparatus in its slot");
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitTicks(5);
    }

    private static void equip(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            var apparatus = player.getInventory().getItem(0).copy();
            player.getInventory().setItem(0, ItemStack.EMPTY);
            var access = TrinketsApi.getAttachment(player).getInventory(SLOT).getOrCreateSlotAccess(0);
            expect(access.set(apparatus), "Expected the apparatus to go into its slot");
        });
        context.waitTicks(2);
        float points = server.computeOnServer(minecraft -> ShieldingApparatus.state(player(minecraft)).points());
        expect(points == 0, "Expected a newly worn apparatus to start empty, got " + points);
    }

    /**
     * Right clicks the slot the apparatus is worn in, through the inventory menu as the inventory screen would, and edits the worn grid.
     */
    private static void editWorn(ClientGameTestContext context, TestServerContext server)
    {
        int slot = context.computeOnClient(minecraft ->
        {
            var worn = TrinketsApi.getAttachment(minecraft.player).getInventory(SLOT).getItem(0);
            var slots = minecraft.player.inventoryMenu.slots;
            for (int index = 0; index < slots.size(); index++)
                if (slots.get(index).getItem() == worn)
                    return index;
            return -1;
        });
        expect(slot >= 0, "Expected the worn apparatus to have a slot in the inventory menu");
        context.runOnClient(minecraft -> minecraft.gameMode.handleContainerInput(minecraft.player.inventoryMenu.containerId, slot, 1, ContainerInput.PICKUP, minecraft.player));
        context.waitTicks(10);
        var reference = server.computeOnServer(minecraft -> TrinketsApi.getAttachment(player(minecraft)).getInventory(SLOT).getOrCreateSlotAccess(0).reference());
        expectGrid(context, new ApparatusHolder.Worn(reference), "a right click on the slot it is worn in");

        double scale = context.computeOnClient(minecraft -> minecraft.getWindow().getGuiScale());
        click(context, scale, screen -> screen.cellCenter(2, 0));
        context.waitTicks(5);
        expect(wornGrid(server).at(2, 0) == null && carriedCount(context) == 1, "Expected a cell taken out of the worn apparatus onto the cursor");
        boolean seen = context.computeOnClient(minecraft -> ((ApparatusScreen) minecraft.gui.screen()).getMenu().grid().at(2, 0) == null);
        expect(seen, "Expected the client to see the worn grid change");
        click(context, scale, screen -> screen.cellCenter(2, 0));
        context.waitTicks(5);
        expect(wornGrid(server).at(2, 0) != null && carriedEmpty(context), "Expected the cell set back into the worn apparatus");
        context.takeScreenshot("apparatus-grid-worn");
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitTicks(5);
        expect(!server.computeOnServer(minecraft -> ShieldingApparatus.equipped(player(minecraft)).isEmpty()), "Expected the apparatus still worn after editing it");
    }

    private static void fill(ClientGameTestContext context, TestServerContext server)
    {
        // The Lesser wait of 100 ticks plus about 190 to refill, checked every 5 ticks for up to 600
        for (int waited = 0; waited < FILL_TICKS_MAX; waited += 5)
        {
            var state = server.computeOnServer(minecraft -> ShieldingApparatus.state(player(minecraft)));
            if (state.capacity() > 0 && state.isFull())
                break;
            context.waitTicks(5);
        }
        var state = server.computeOnServer(minecraft -> ShieldingApparatus.state(player(minecraft)));
        expect(state.isFull() && state.capacity() == CAPACITY, "Expected the shield to fill to " + CAPACITY + ", got " + state);
        context.waitTicks(5);
        float clientCapacity = context.computeOnClient(minecraft -> ShieldingApparatus.state(minecraft.player).capacity());
        expect(clientCapacity == CAPACITY, "Expected the shield synced to its wearer, got " + clientCapacity);
    }

    /**
     * The rune shields over the armor row, full and partly spent, and the apparatus on the torso from the front, the back and the side, bare and over a
     * chestplate. The chestplate comes off again, since the hits that follow count on no armor.
     */
    private static void showWorn(ClientGameTestContext context, TestServerContext server)
    {
        // The chestplate's advancement would sit in chat right over the armor row
        server.runCommand("gamerule show_advancement_messages false");
        server.runOnServer(minecraft -> player(minecraft).setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE)));
        context.waitTicks(3);
        context.runOnClient(minecraft -> minecraft.gui.hud.getChat().clearMessages(false));
        context.waitTicks(2);
        context.takeScreenshot("apparatus-hud-full-over-armor");
        server.runOnServer(minecraft -> player(minecraft).setAttached(ShieldingApparatus.SHIELD, new ShieldState(5, CAPACITY, 200)));
        context.waitTicks(2);
        context.takeScreenshot("apparatus-hud-spent-flash");
        // Past the white flash a drop gives
        context.waitTicks(8);
        context.takeScreenshot("apparatus-hud-spent-over-armor");
        // A shield over twenty points, refilling, set on the client alone: the server sizes it from the apparatus, and syncs only a change, so it is held full
        server.runOnServer(minecraft -> player(minecraft).setAttached(ShieldingApparatus.SHIELD, new ShieldState(CAPACITY, CAPACITY, 0)));
        context.waitTicks(5);
        context.runOnClient(minecraft -> minecraft.player.setAttached(ShieldingApparatus.SHIELD, new ShieldState(31, 38, 0)));
        context.waitTicks(3);
        context.takeScreenshot("apparatus-hud-second-layer");
        context.runOnClient(minecraft -> minecraft.player.setAttached(ShieldingApparatus.SHIELD, new ShieldState(CAPACITY, CAPACITY, 0)));

        context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
        context.waitTicks(3);
        wornShot(context, "apparatus-worn-front-armor");
        server.runOnServer(minecraft -> player(minecraft).setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY));
        context.waitTicks(3);
        wornShot(context, "apparatus-worn-front");
        context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK));
        context.waitTicks(3);
        wornShot(context, "apparatus-worn-back");
        server.runCommand("execute as @a run tp @s 0.5 " + GROUND_Y + " 0.5 90 0");
        context.waitTicks(3);
        wornShot(context, "apparatus-worn-side");
        server.runCommand("execute as @a run tp @s 0.5 " + GROUND_Y + " 0.5 0 0");

        // Turned back to face the camera, the head leads and the body follows a moment later, so the straps are seen with the head turned from them
        context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
        context.waitTicks(1);
        wornShot(context, "apparatus-worn-head-turned");
        context.waitTicks(30);

        // The other tiers' trims, worn in turn with the same grid, then the Lesser one back
        var lesser = server.computeOnServer(minecraft -> ShieldingApparatus.equipped(player(minecraft)).copy());
        for (var tier : new Item[] { VoidHeartItems.GREATER_SHIELDING_APPARATUS, VoidHeartItems.SUPREME_SHIELDING_APPARATUS })
        {
            server.runOnServer(minecraft -> TrinketsApi.getAttachment(player(minecraft)).getInventory(SLOT).getOrCreateSlotAccess(0).set(lesser.transmuteCopy(tier)));
            context.waitTicks(3);
            wornShot(context, "apparatus-worn-front-" + tier.getDescriptionId().replace("item.voidheart.", ""));
        }
        server.runOnServer(minecraft -> TrinketsApi.getAttachment(player(minecraft)).getInventory(SLOT).getOrCreateSlotAccess(0).set(lesser.copy()));
        context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
        server.runOnServer(minecraft -> player(minecraft).setAttached(ShieldingApparatus.SHIELD, new ShieldState(CAPACITY, CAPACITY, 0)));
        context.waitTicks(5);
    }

    /**
     * Strikes the player from a mob standing at the given offset, and returns the points the shield has left.
     */
    private static float strike(TestServerContext server, double offsetX, double offsetZ, float damage)
    {
        return server.computeOnServer(minecraft ->
        {
            var player = player(minecraft);
            var level = minecraft.overworld();
            var zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
            zombie.setNoAi(true);
            zombie.snapTo(player.getX() + offsetX, GROUND_Y, player.getZ() + offsetZ, 0, 0);
            level.addFreshEntity(zombie);
            player.damageCooldownTime = 0;
            player.hurtServer(level, player.damageSources().mobAttack(zombie), damage);
            zombie.discard();
            return ShieldingApparatus.state(player).points();
        });
    }

    private static void blockAHit(ClientGameTestContext context, TestServerContext server)
    {
        // Time holds still around the hit, then steps through its life so each moment can be seen
        server.runCommand("tick freeze");
        context.waitTicks(2);
        float left = strike(server, 1, 2, 4);
        expect(Math.abs(left - (CAPACITY - 4)) < 0.01F, "Expected 4 points taken, " + left + " left");
        float health = server.computeOnServer(minecraft -> player(minecraft).getHealth());
        expect(health == 20, "Expected the shield to take the whole hit, health is " + health);
        context.waitTicks(3);
        int hits = context.computeOnClient(minecraft -> ShieldHits.of(minecraft.player.getId()).size());
        expect(hits == 1, "Expected the client told of the hit, got " + hits);

        server.runCommand("tick step 3");
        context.waitTicks(5);
        context.takeScreenshot("apparatus-hit-hud-first-person");

        context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
        context.waitTicks(2);
        int age = 3;
        for (int next : new int[] { 1, 3, 6, 10, 14, 18, 21 })
        {
            if (next > age)
            {
                server.runCommand("tick step " + (next - age));
                context.waitTicks(next - age + 3);
                age = next;
            }
            long seen = context.computeOnClient(minecraft ->
            {
                var hit = ShieldHits.of(minecraft.player.getId());
                return hit.isEmpty() ? -1L : minecraft.level.getGameTime() - hit.getFirst().tick();
            });
            context.takeScreenshot("apparatus-hit-runes-step-" + age + "-client-age-" + seen);
        }

        // Two hits from either side at once each light their own patch, and nothing overlaps where they meet
        server.runCommand("tick step 10");
        context.waitTicks(12);
        strike(server, 2, 1, 2);
        strike(server, -2, 1, 2);
        context.waitTicks(3);
        server.runCommand("tick step 6");
        context.waitTicks(8);
        context.takeScreenshot("apparatus-two-hits-front");
        // The body turns the hull with it while the time holds still, so the same tiles are seen from a sweep of angles
        for (int step = 0; step < 6; step++)
        {
            int yaw = 20 + step * 12;
            server.runCommand("execute as @a run tp @s 0.5 " + GROUND_Y + " 0.5 " + yaw + " 10");
            context.waitTicks(1);
            context.takeScreenshot("apparatus-two-hits-sweep-" + step);
        }
        context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
        server.runCommand("execute as @a run tp @s 0.5 " + GROUND_Y + " 0.5 0 0");
        server.runCommand("tick unfreeze");
        context.waitTicks(30);
    }

    private static void breakTheShield(TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            // At full health, so a heal made before the hit lands would be wasted and leave the wearer hurt
            player.setHealth(player.getMaxHealth());
            var level = minecraft.overworld();
            float points = ShieldingApparatus.state(player).points();
            player.damageCooldownTime = 0;
            player.hurtServer(level, player.damageSources().generic(), points + 3);
            var state = ShieldingApparatus.state(player);
            expect(state.points() == 0, "Expected the shield broken, " + state.points() + " left");
            expect(player.getHealth() == player.getMaxHealth(), "Expected the Mending Core to heal the 3 health the breaking hit took, health is " + player.getHealth());
            expect(state.delay() > 80, "Expected a broken shield to wait longer than a hit one, waits " + state.delay());
        });
    }

    private static void letAFallThrough(TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            player.setHealth(player.getMaxHealth());
            player.setAttached(ShieldingApparatus.SHIELD, new ShieldState(CAPACITY, CAPACITY, 0));
            player.damageCooldownTime = 0;
            player.hurtServer(minecraft.overworld(), player.damageSources().fall(), 3);
            expect(ShieldingApparatus.state(player).points() == CAPACITY, "Expected a fall to leave the shield alone");
            expect(player.getHealth() == player.getMaxHealth() - 3, "Expected the fall to hurt, health is " + player.getHealth());
        });
    }

    private static void unequip(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft -> TrinketsApi.getAttachment(player(minecraft)).getInventory(SLOT).getOrCreateSlotAccess(0).set(ItemStack.EMPTY));
        context.waitTicks(3);
        boolean gone = server.computeOnServer(minecraft -> !player(minecraft).hasAttached(ShieldingApparatus.SHIELD));
        expect(gone, "Expected the shield dropped with the apparatus");
    }

    private static void expectGrid(ClientGameTestContext context, ApparatusHolder holder, String how)
    {
        @Nullable ApparatusHolder open = context.computeOnClient(minecraft ->
                minecraft.gui.screen() instanceof ApparatusScreen screen ? screen.getMenu().getHolder() : null);
        expect(holder.equals(open), "Expected " + how + " to open the grid of " + holder + ", got " + open);
    }

    private static void expectAt(TestServerContext server, int x, int y, ApparatusComponent component, String what)
    {
        var placement = grid(server).at(x, y);
        expect(placement != null && placement.component() == component && placement.x() == x && placement.y() == y,
                "Expected " + what + " at " + x + "," + y + ", got " + placement);
    }

    private static ApparatusGrid grid(TestServerContext server)
    {
        return server.computeOnServer(minecraft -> ShieldingApparatus.grid(player(minecraft).getInventory().getItem(0)));
    }

    private static ApparatusGrid wornGrid(TestServerContext server)
    {
        return server.computeOnServer(minecraft -> ShieldingApparatus.grid(ShieldingApparatus.equipped(player(minecraft))));
    }

    private static boolean carriedEmpty(ClientGameTestContext context)
    {
        return carriedCount(context) == 0;
    }

    private static int carriedCount(ClientGameTestContext context)
    {
        return context.computeOnClient(minecraft -> minecraft.player.containerMenu.getCarried().getCount());
    }

    /**
     * Moves the cursor to a point the open apparatus screen gives, in GUI pixels.
     */
    private static void aim(ClientGameTestContext context, double scale, Function<ApparatusScreen, int[]> point)
    {
        int[] at = context.computeOnClient(minecraft -> point.apply((ApparatusScreen) minecraft.gui.screen()));
        context.getInput().setCursorPos(at[0] * scale, at[1] * scale);
        context.waitTicks(2);
    }

    private static void click(ClientGameTestContext context, double scale, Function<ApparatusScreen, int[]> point)
    {
        aim(context, scale, point);
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        context.waitTicks(2);
    }

    /**
     * Shift clicks the slot holding an inventory index in the open apparatus screen.
     */
    private static void quickMove(ClientGameTestContext context, int inventoryIndex)
    {
        context.runOnClient(minecraft ->
        {
            var menu = ((ApparatusScreen) minecraft.gui.screen()).getMenu();
            for (var slot : menu.slots)
                if (slot.getContainerSlot() == inventoryIndex)
                    minecraft.gameMode.handleContainerInput(menu.containerId, slot.index, 0, ContainerInput.QUICK_MOVE, minecraft.player);
        });
        context.waitTicks(5);
    }

    /**
     * Shift clicks a piece in the open apparatus screen, as the screen does: at once on the client, then through the server.
     */
    private static void shiftTake(ClientGameTestContext context, int cellX, int cellY)
    {
        context.runOnClient(minecraft ->
        {
            var menu = ((ApparatusScreen) minecraft.gui.screen()).getMenu();
            int button = ApparatusMenu.takeButton(cellX, cellY, true);
            if (menu.clickMenuButton(minecraft.player, button))
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
        });
        context.waitTicks(5);
    }

    /**
     * At twice the usual size, so the torso is large enough to judge the worn model's texture.
     */
    private static void wornShot(ClientGameTestContext context, String name)
    {
        context.takeScreenshot(TestScreenshotOptions.of(name).withSize(1708, 960));
    }

    private static ItemStack component(ApparatusComponent component, int count)
    {
        return new ItemStack(VoidHeartItems.APPARATUS_COMPONENTS.get(component), count);
    }

    private static int count(TestServerContext server, ApparatusComponent component)
    {
        return server.computeOnServer(minecraft ->
        {
            ServerPlayer player = player(minecraft);
            var item = VoidHeartItems.APPARATUS_COMPONENTS.get(component);
            int count = 0;
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++)
                if (player.getInventory().getItem(slot).is(item))
                    count += player.getInventory().getItem(slot).getCount();
            return count;
        });
    }
}
