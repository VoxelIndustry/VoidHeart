package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import eu.pb4.trinkets.api.TrinketsApi;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemStack;
import net.voxelindustry.voidheart.client.apparatus.ShieldHits;
import net.voxelindustry.voidheart.client.screen.ApparatusScreen;
import net.voxelindustry.voidheart.common.content.apparatus.ApparatusComponent;
import net.voxelindustry.voidheart.common.content.apparatus.ApparatusEdits;
import net.voxelindustry.voidheart.common.content.apparatus.ShieldPayloads;
import net.voxelindustry.voidheart.common.content.apparatus.ShieldState;
import net.voxelindustry.voidheart.common.content.apparatus.ShieldingApparatus;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;

import java.util.function.Function;

import static net.voxelindustry.voidheart.gametest.GameTestKit.expect;
import static net.voxelindustry.voidheart.gametest.GameTestKit.player;

/**
 * Components are set in a Lesser apparatus from the inventory and refused where they overlap. Worn in its Trinkets slot, its shield fills after the wait,
 * takes a blow before health with runes lit where it landed, breaks into its Mending Core's heal and lets falls through. The grid screen and the HUD are captured.
 */
public class ShieldingApparatusClientTest implements FabricClientGameTest
{
    private static final int   GROUND_Y = -60;
    private static final float CAPACITY = 4 + 3.5F + 1 + 1;
    private static final String SLOT    = "chest/shielding_apparatus";
    private static final int   FILL_TICKS_MAX = 600;

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

            setComponents(server);
            openScreen(context, server);
            equip(context, server);
            blockAHit(context, server);
            breakTheShield(server);
            letAFallThrough(server);
            unequip(context, server);
        }
    }

    private static void setComponents(TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            player.getInventory().setItem(0, new ItemStack(VoidHeartItems.LESSER_SHIELDING_APPARATUS));
            player.getInventory().setSelectedSlot(0);
            give(player, ApparatusComponent.MENDING_CORE, 1);
            give(player, ApparatusComponent.BANK, 1);
            give(player, ApparatusComponent.CELL, 3);

            expect(ApparatusEdits.apply(player, place(ApparatusComponent.MENDING_CORE, 0, 0, 0)), "Expected the core to fit the top rows");
            expect(ApparatusEdits.apply(player, place(ApparatusComponent.BANK, 0, 2, 0)), "Expected the bank to fit the bottom row");
            expect(!ApparatusEdits.apply(player, place(ApparatusComponent.CELL, 1, 1, 0)), "Expected a cell on the core to be refused");
            expect(ApparatusEdits.apply(player, place(ApparatusComponent.CELL, 2, 0, 0)), "Expected a cell in the free corner");
            expect(ApparatusEdits.apply(player, place(ApparatusComponent.CELL, 0, 1, 0)), "Expected a cell beside the core");
            expect(!ApparatusEdits.apply(player, place(ApparatusComponent.CELL, 3, 0, 0)), "Expected a cell outside the grid to be refused");

            var grid = ShieldingApparatus.grid(player.getMainHandItem());
            expect(grid.placements().size() == 4, "Expected four pieces set, got " + grid.placements());
            expect(count(player, ApparatusComponent.CELL) == 1, "Expected one cell left carried");
            expect(count(player, ApparatusComponent.MENDING_CORE) == 0, "Expected the core taken from the inventory");

            expect(ApparatusEdits.apply(player, new ShieldPayloads.Edit(true, ApparatusComponent.CELL, 2, 0, 0)), "Expected the corner cell taken back");
            expect(count(player, ApparatusComponent.CELL) == 2, "Expected the cell given back");
            expect(ApparatusEdits.apply(player, place(ApparatusComponent.CELL, 2, 0, 0)), "Expected the cell set again");

            var stats = ShieldingApparatus.stats(player.getMainHandItem());
            expect(stats != null && stats.capacity() == CAPACITY, "Expected a capacity of " + CAPACITY + ", got " + (stats == null ? null : stats.capacity()));
        });
    }

    private static void openScreen(ClientGameTestContext context, TestServerContext server)
    {
        context.waitTicks(5);
        context.getInput().pressKey(options -> options.keyUse);
        context.waitTicks(10);
        GameTestKit.expectClient(context, "Expected using the apparatus to open its grid", minecraft -> minecraft.gui.screen() instanceof ApparatusScreen);
        context.getInput().setCursorPos(10, 10);
        context.takeScreenshot("apparatus-grid-screen");
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitTicks(5);

        // More kinds carried than the tablet shows scroll into view under the wheel
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            for (var component : ApparatusComponent.values())
                give(player, component, 1);
        });
        context.waitTicks(5);
        context.getInput().pressKey(options -> options.keyUse);
        context.waitTicks(10);
        double scale = context.computeOnClient(minecraft -> minecraft.getWindow().getGuiScale());
        int[] view = context.computeOnClient(minecraft -> ((ApparatusScreen) minecraft.gui.screen()).trayView());
        expect(view[3] > 0, "Expected more kinds carried than the tablet shows");
        context.getInput().setCursorPos(view[0] * scale, view[1] * scale);
        context.waitTicks(2);
        for (int notch = 0; notch < view[3] + 2; notch++)
        {
            context.getInput().scroll(-1);
            context.waitTicks(1);
        }
        int[] scrolled = context.computeOnClient(minecraft -> ((ApparatusScreen) minecraft.gui.screen()).trayView());
        expect(scrolled[2] == view[3], "Expected the wheel to scroll the tablet to its last row, at " + scrolled[2] + " of " + view[3]);
        context.takeScreenshot("apparatus-grid-screen-scrolled");

        // Real clicks: taking the corner cell back, then picking a cell on the tablet and setting it in the same corner
        for (int notch = 0; notch < view[3] + 2; notch++)
        {
            context.getInput().scroll(1);
            context.waitTicks(1);
        }
        int cellsBefore = server.computeOnServer(minecraft -> count(player(minecraft), ApparatusComponent.CELL));
        clickScreen(context, scale, screen -> screen.cellCenter(2, 0));
        context.waitTicks(5);
        boolean taken = server.computeOnServer(minecraft -> ShieldingApparatus.grid(player(minecraft).getMainHandItem()).at(2, 0) == null);
        int cellsTaken = server.computeOnServer(minecraft -> count(player(minecraft), ApparatusComponent.CELL));
        expect(taken && cellsTaken == cellsBefore + 1, "Expected clicking the corner piece to take it back, carried " + cellsBefore + " then " + cellsTaken);

        clickScreen(context, scale, screen -> screen.trayRowCenter(ApparatusComponent.CELL));
        context.waitTicks(2);
        clickScreen(context, scale, screen -> screen.cellCenter(2, 0));
        context.waitTicks(5);
        var placed = server.computeOnServer(minecraft -> ShieldingApparatus.grid(player(minecraft).getMainHandItem()).at(2, 0));
        int cellsSet = server.computeOnServer(minecraft -> count(player(minecraft), ApparatusComponent.CELL));
        expect(placed != null && placed.component() == ApparatusComponent.CELL && cellsSet == cellsBefore,
                "Expected picking a cell and clicking the corner to set it there, got " + placed + " with " + cellsSet + " carried");
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitTicks(5);
    }

    private static void equip(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            var apparatus = player.getMainHandItem().copy();
            player.getInventory().setItem(0, ItemStack.EMPTY);
            var access = TrinketsApi.getAttachment(player).getInventory(SLOT).getOrCreateSlotAccess(0);
            expect(access.set(apparatus), "Expected the apparatus to go into its slot");
        });
        context.waitTicks(2);
        float points = server.computeOnServer(minecraft -> ShieldingApparatus.state(player(minecraft)).points());
        expect(points == 0, "Expected a newly worn apparatus to start empty, got " + points);

        // The Lesser wait of 100 ticks plus about 190 to refill, checked every 5 ticks for up to 600
        for (int waited = 0; waited < FILL_TICKS_MAX; waited += 5)
        {
            if (server.computeOnServer(minecraft -> ShieldingApparatus.state(player(minecraft)).isFull()))
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

    /**
     * Moves the cursor to a point the open apparatus screen gives, in GUI pixels, and left clicks there.
     */
    private static void clickScreen(ClientGameTestContext context, double scale, Function<ApparatusScreen, int[]> point)
    {
        int[] at = context.computeOnClient(minecraft -> point.apply((ApparatusScreen) minecraft.gui.screen()));
        expect(at != null, "Expected the point to click to be in view");
        context.getInput().setCursorPos(at[0] * scale, at[1] * scale);
        context.waitTicks(1);
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
    }

    private static ShieldPayloads.Edit place(ApparatusComponent component, int x, int y, int rotation)
    {
        return new ShieldPayloads.Edit(false, component, x, y, rotation);
    }

    private static void give(ServerPlayer player, ApparatusComponent component, int count)
    {
        player.getInventory().add(new ItemStack(VoidHeartItems.APPARATUS_COMPONENTS.get(component), count));
    }

    private static int count(ServerPlayer player, ApparatusComponent component)
    {
        var item = VoidHeartItems.APPARATUS_COMPONENTS.get(component);
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++)
            if (player.getInventory().getItem(slot).is(item))
                count += player.getInventory().getItem(slot).getCount();
        return count;
    }
}
