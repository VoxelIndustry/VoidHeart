package net.voxelindustry.voidheart.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.voxelindustry.voidheart.common.content.sin.DiggingProficiency;
import net.voxelindustry.voidheart.common.content.sin.Sin;
import net.voxelindustry.voidheart.common.content.sin.SinBalance;
import net.voxelindustry.voidheart.common.energy.gnawer.GnawerBlock;
import net.voxelindustry.voidheart.common.energy.gnawer.GnawerContent;
import net.voxelindustry.voidheart.common.energy.gnawer.GnawerTile;
import net.voxelindustry.voidheart.common.setup.VoidHeartDataComponents;

import java.util.List;

import static net.voxelindustry.voidheart.gametest.GameTestKit.FLAT_FEET;
import static net.voxelindustry.voidheart.gametest.GameTestKit.FLAT_GROUND;
import static net.voxelindustry.voidheart.gametest.GameTestKit.aimFrom;
import static net.voxelindustry.voidheart.gametest.GameTestKit.clearArea;
import static net.voxelindustry.voidheart.gametest.GameTestKit.clearScene;
import static net.voxelindustry.voidheart.gametest.GameTestKit.expect;
import static net.voxelindustry.voidheart.gametest.GameTestKit.freezeTime;
import static net.voxelindustry.voidheart.gametest.GameTestKit.player;
import static net.voxelindustry.voidheart.gametest.GameTestKit.useOn;
import static net.voxelindustry.voidheart.gametest.GameTestKit.waitFor;

/**
 * A Ravenous pickaxe bites into a mixed stone wall for hunger and no durability, gathers the drops, and leaves dirt, sneaking and starving alone. A Gnawer
 * placed by hand is given a box with two stakes, eats it into a chest with a lent silk touch pickaxe while skipping a block entity and bedrock, then erases
 * a second box, and drops its pickaxe when broken.
 */
public class ExcavationClientTest implements FabricClientGameTest
{
    // The wall the pickaxe bites, its front face at z = WALL_Z
    private static final int      TX     = 20;
    private static final int      WALL_Z = 3;
    private static final BlockPos WALL_MIN = new BlockPos(TX, FLAT_FEET, WALL_Z);
    private static final BlockPos WALL_MAX = new BlockPos(TX + 6, FLAT_FEET + 4, WALL_Z + 6);
    private static final BlockPos BITTEN   = new BlockPos(TX + 3, FLAT_FEET + 2, WALL_Z);

    // The Gnawer, a chest on its west side, and the box east of it from the dirt under the grass to above the stone
    private static final int      GX      = 60;
    private static final BlockPos GNAWER  = new BlockPos(GX, FLAT_FEET, 0);
    private static final BlockPos CHEST   = GNAWER.west();
    private static final BlockPos WELL    = new BlockPos(GX - 3, FLAT_FEET, -4);
    private static final BlockPos BOX_TOP = new BlockPos(GX + 2, FLAT_FEET + 3, -2);
    private static final BlockPos BOX_LOW = new BlockPos(GX + 7, FLAT_GROUND - 2, 3);
    private static final BlockPos INSIDE_CHEST = new BlockPos(GX + 4, FLAT_FEET, 0);
    private static final BlockPos INSIDE_BEDROCK = new BlockPos(GX + 5, FLAT_FEET + 1, 1);

    @Override
    public void runTest(ClientGameTestContext context)
    {
        var camera = context.computeOnClient(minecraft -> minecraft.options.getCameraType());
        var hudHidden = context.computeOnClient(minecraft -> minecraft.gui.hud.isHidden());
        try
        {
            runScenes(context);
        }
        finally
        {
            context.runOnClient(minecraft -> minecraft.options.setCameraType(camera));
            setHudHidden(context, hudHidden);
        }
    }

    private static void setHudHidden(ClientGameTestContext context, boolean hidden)
    {
        context.runOnClient(minecraft ->
        {
            if (minecraft.gui.hud.isHidden() != hidden)
                minecraft.gui.hud.toggle();
        });
    }

    private static void runScenes(ClientGameTestContext context)
    {
        try (var world = context.worldBuilder().create())
        {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            freezeTime(server, "noon");
            // Peaceful never spends exhaustion, so the bite's hunger wouldn't show
        server.runCommand("difficulty easy");
        server.runCommand("gamerule spawn_mobs false");
            server.runCommand("gamemode survival @a");
            server.runCommand("execute as @a run voidheart research grant all");
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
            clearArea(server, TX);
            clearArea(server, GX);

            ravenousBite(context, server);
            biteSparesWhatItShould(context, server);
            toolTooltipAndInlay(context, server);

            gnawerKeeps(context, server);
            gnawerErases(context, server);
            gnawerDropsItsPickaxe(context, server);
            clearScene(server);
        }
    }

    // The Ravenous pickaxe

    private static ItemStack gluttonous(Item item)
    {
        var stack = new ItemStack(item);
        stack.set(VoidHeartDataComponents.SIN, Sin.GLUTTONY);
        return stack;
    }

    private static void buildWall(TestServerContext server)
    {
        server.runCommand("fill " + WALL_MIN.getX() + " " + WALL_MIN.getY() + " " + WALL_MIN.getZ() + " "
                + WALL_MAX.getX() + " " + WALL_MAX.getY() + " " + WALL_MAX.getZ() + " stone");
        // A deepslate column and a tuff band cross the bitten block, so the bite has to take them as one kind
        server.runCommand("fill " + (TX + 4) + " " + WALL_MIN.getY() + " " + WALL_Z + " " + (TX + 4) + " " + WALL_MAX.getY() + " " + (WALL_Z + 6) + " deepslate");
        server.runCommand("fill " + TX + " " + (FLAT_FEET + 1) + " " + WALL_Z + " " + (TX + 6) + " " + (FLAT_FEET + 1) + " " + (WALL_Z + 6) + " tuff");
        // Stone under the wall too, so a swing at foot level would bite into the floor if it could
        server.runCommand("fill " + TX + " " + FLAT_GROUND + " " + (WALL_Z - 2) + " " + (TX + 6) + " " + FLAT_GROUND + " " + (WALL_Z + 6) + " stone");
    }

    private static boolean isAir(MinecraftServer minecraft, BlockPos pos)
    {
        return minecraft.overworld().getBlockState(pos).isAir();
    }

    private static int airIn(MinecraftServer minecraft, BlockPos min, BlockPos max)
    {
        var level = minecraft.overworld();
        var count = 0;
        for (var pos : BlockPos.betweenClosed(min, max))
        {
            if (level.getBlockState(pos).isAir())
                count++;
        }
        return count;
    }

    private static void holdAndFeed(MinecraftServer minecraft, ItemStack tool, int food, float saturation)
    {
        var player = player(minecraft);
        player.getInventory().clearContent();
        player.getInventory().setItem(0, tool);
        player.getInventory().setSelectedSlot(0);
        player.getFoodData().setFoodLevel(food);
        player.getFoodData().setSaturation(saturation);
        player.setShiftKeyDown(false);
    }

    private static void ravenousBite(ClientGameTestContext context, TestServerContext server)
    {
        buildWall(server);
        server.runCommand("tp @a " + (TX + 3.5) + " " + FLAT_FEET + " 0.5 0 20");
        context.waitTicks(5);

        var stage = 3;
        server.runOnServer(minecraft ->
        {
            DiggingProficiency.set(player(minecraft), SinBalance.STAGE_STARTS[stage - 1] * SinBalance.DIGGING_DEVOTED_POINTS);
            holdAndFeed(minecraft, gluttonous(Items.IRON_PICKAXE), 20, 5);
        });
        var before = server.computeOnServer(minecraft -> DiggingProficiency.points(player(minecraft)));

        aimFrom(context, server, TX + 3.5, FLAT_FEET, 0.5, BITTEN.getX() + 0.5, BITTEN.getY() + 0.5, BITTEN.getZ());
        context.waitTicks(5);
        context.takeScreenshot("ravenous-preview-square");
        setHudHidden(context, true);
        server.runOnServer(minecraft -> player(minecraft).gameMode.destroyBlock(BITTEN));
        context.waitTicks(2);
        context.takeScreenshot("ravenous-bite-crumbling");
        context.waitTicks(10);

        var side = 2 * DiggingProficiency.squareRadius(stage) + 1;
        var expected = side * side * DiggingProficiency.squareDepth(stage);
        var eaten = server.computeOnServer(minecraft -> airIn(minecraft, WALL_MIN, WALL_MAX));
        expect(eaten == expected, "Expected the square to eat " + expected + " blocks with the broken one, got " + eaten);

        // A square on the face hit, going straight in and no further
        expect(server.computeOnServer(minecraft -> isAir(minecraft, BITTEN.south(DiggingProficiency.squareDepth(stage) - 1))), "Expected the square's last layer eaten");
        expect(server.computeOnServer(minecraft -> !isAir(minecraft, BITTEN.south(DiggingProficiency.squareDepth(stage)))), "Expected the square to stop at its depth");
        expect(server.computeOnServer(minecraft -> !isAir(minecraft, BITTEN.above(2))), "Expected the square to stop at its radius");

        // The tuff under it and the deepslate beside it go in the same bite
        expect(server.computeOnServer(minecraft -> minecraft.overworld().getBlockState(BITTEN.below()).isAir()), "Expected the tuff under the broken stone bitten");
        expect(server.computeOnServer(minecraft -> minecraft.overworld().getBlockState(BITTEN.east()).isAir()), "Expected the deepslate beside the broken stone bitten");

        var damage = server.computeOnServer(minecraft -> player(minecraft).getMainHandItem().getDamageValue());
        expect(damage == 1, "Expected only the broken block to wear the pickaxe, got damage " + damage);

        var saturation = server.computeOnServer(minecraft -> player(minecraft).getFoodData().getSaturationLevel());
        expect(saturation < 5, "Expected the bite to eat into saturation, still at " + saturation);

        var after = server.computeOnServer(minecraft -> DiggingProficiency.points(player(minecraft)));
        expect(after > before, "Expected digging proficiency to grow, stayed at " + after);

        // Every drop gathered where the first block broke
        var drops = server.computeOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            var around = new AABB(BITTEN).inflate(1, 4, 1);
            var near = 0;
            var all = 0;
            for (var item : level.getEntitiesOfClass(ItemEntity.class, AABB.encapsulatingFullBlocks(WALL_MIN, WALL_MAX).inflate(4)))
            {
                all += item.getItem().getCount();
                if (around.contains(item.position()))
                    near += item.getItem().getCount();
            }
            return new int[]{near, all};
        });
        expect(drops[1] == expected && drops[0] == expected, "Expected all " + expected + " drops gathered at the broken block, got " + drops[0] + " of " + drops[1]);

        // A swing at foot level never bites into the floor
        var foot = new BlockPos(TX + 1, FLAT_FEET, WALL_Z);
        aimFrom(context, server, TX + 1.5, FLAT_FEET, 0.5, foot.getX() + 0.5, foot.getY() + 0.5, foot.getZ());
        server.runOnServer(minecraft ->
        {
            player(minecraft).getFoodData().setFoodLevel(20);
            player(minecraft).gameMode.destroyBlock(foot);
        });
        context.waitTicks(8);
        expect(server.computeOnServer(minecraft -> isAir(minecraft, foot.above()) && isAir(minecraft, foot.west().south())), "Expected the foot-level square eaten");
        expect(server.computeOnServer(minecraft -> !isAir(minecraft, foot.below()) && !isAir(minecraft, foot.below().south())), "Expected the floor under the feet left whole");

        context.waitTicks(10);
        context.takeScreenshot("ravenous-bite-hole");
        setHudHidden(context, false);
        clearScene(server);
    }

    private static void biteSparesWhatItShould(ClientGameTestContext context, TestServerContext server)
    {
        clearArea(server, TX);
        buildWall(server);
        context.waitTicks(2);

        // Sneaking breaks a single block
        server.runOnServer(minecraft ->
        {
            holdAndFeed(minecraft, gluttonous(Items.IRON_PICKAXE), 20, 5);
            player(minecraft).setShiftKeyDown(true);
            player(minecraft).gameMode.destroyBlock(BITTEN);
        });
        context.waitTicks(8);
        var sneaking = server.computeOnServer(minecraft -> airIn(minecraft, WALL_MIN, WALL_MAX));
        expect(sneaking == 1, "Expected a sneaking player to break one block, got " + sneaking);

        // An ore takes its vein, through an edge, and none of the stone around it
        var ore = new BlockPos(TX + 5, FLAT_FEET + 3, WALL_Z);
        var veinNext = ore.south();
        var veinEdge = veinNext.above().east();
        for (var pos : List.of(ore, veinNext, veinEdge))
            server.runCommand("setblock " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " iron_ore");
        server.runOnServer(minecraft -> holdAndFeed(minecraft, gluttonous(Items.IRON_PICKAXE), 20, 5));
        aimFrom(context, server, TX + 5.5, FLAT_FEET, 0.5, ore.getX() + 0.5, ore.getY() + 0.5, ore.getZ());
        context.waitTicks(5);
        context.takeScreenshot("ravenous-preview-vein");
        server.runOnServer(minecraft -> player(minecraft).gameMode.destroyBlock(ore));
        context.waitTicks(8);
        expect(server.computeOnServer(minecraft -> isAir(minecraft, veinNext) && isAir(minecraft, veinEdge)), "Expected the ore's vein eaten");
        var vein = server.computeOnServer(minecraft -> airIn(minecraft, WALL_MIN, WALL_MAX));
        expect(vein == 4, "Expected the vein to take only its three ores, got " + (vein - 1) + " blocks");

        // A starved player gets no bite
        server.runOnServer(minecraft ->
        {
            holdAndFeed(minecraft, gluttonous(Items.IRON_PICKAXE), 0, 0);
            player(minecraft).gameMode.destroyBlock(BITTEN.east(2));
        });
        context.waitTicks(8);
        var starved = server.computeOnServer(minecraft -> airIn(minecraft, WALL_MIN, WALL_MAX));
        expect(starved == 5, "Expected a starved player to break one more block, got " + (starved - 4));

        // A plain pickaxe never bites
        server.runOnServer(minecraft ->
        {
            holdAndFeed(minecraft, new ItemStack(Items.IRON_PICKAXE), 20, 5);
            player(minecraft).gameMode.destroyBlock(BITTEN.west(2));
        });
        context.waitTicks(8);
        var plain = server.computeOnServer(minecraft -> airIn(minecraft, WALL_MIN, WALL_MAX));
        expect(plain == 6, "Expected a plain pickaxe to break one more block, got " + (plain - 5));

        // A pickaxe doesn't bite dirt, which it isn't made for
        var dirtMin = new BlockPos(TX - 8, FLAT_FEET, WALL_Z);
        var dirtMax = dirtMin.offset(4, 3, 4);
        server.runCommand("fill " + dirtMin.getX() + " " + dirtMin.getY() + " " + dirtMin.getZ() + " " + dirtMax.getX() + " " + dirtMax.getY() + " " + dirtMax.getZ() + " dirt");
        context.waitTicks(2);
        server.runOnServer(minecraft ->
        {
            holdAndFeed(minecraft, gluttonous(Items.IRON_PICKAXE), 20, 5);
            player(minecraft).gameMode.destroyBlock(dirtMin.offset(2, 1, 0));
        });
        context.waitTicks(8);
        var dirt = server.computeOnServer(minecraft -> airIn(minecraft, dirtMin, dirtMax));
        expect(dirt == 1, "Expected a pickaxe to break a single dirt block, got " + dirt);

        // A shovel bites it
        aimFrom(context, server, dirtMin.getX() + 2.5, FLAT_FEET, 0.5, dirtMin.getX() + 2.5, FLAT_FEET + 2.5, dirtMin.getZ());
        server.runOnServer(minecraft ->
        {
            holdAndFeed(minecraft, gluttonous(Items.IRON_SHOVEL), 20, 5);
            player(minecraft).gameMode.destroyBlock(dirtMin.offset(2, 2, 0));
        });
        context.waitTicks(8);
        var shovelled = server.computeOnServer(minecraft -> airIn(minecraft, dirtMin, dirtMax));
        expect(shovelled > 2, "Expected a Ravenous shovel to bite into dirt, got " + (shovelled - 1) + " blocks");

        server.runOnServer(minecraft -> player(minecraft).setShiftKeyDown(false));
        clearScene(server);
    }

    private static void toolTooltipAndInlay(ClientGameTestContext context, TestServerContext server)
    {
        var lines = context.computeOnClient(minecraft ->
                gluttonous(Items.DIAMOND_PICKAXE).getTooltipLines(Item.TooltipContext.of(minecraft.level), minecraft.player, TooltipFlag.NORMAL)
                        .stream().map(Component::getString).toList());
        expect(lines.contains("Gluttony"), "Expected the sin named on the tool, got " + lines);
        expect(lines.contains("It gorges on the hillside."), "Expected the third digging stage's lore, got " + lines);
        expect(lines.stream().noneMatch(line -> line.contains("It tastes you.")), "Expected no combat lore on a tool, got " + lines);

        server.runOnServer(minecraft ->
        {
            var inventory = player(minecraft).getInventory();
            inventory.clearContent();
            inventory.setItem(0, gluttonous(Items.DIAMOND_PICKAXE));
            inventory.setItem(1, gluttonous(Items.IRON_SHOVEL));
            inventory.setItem(2, gluttonous(Items.NETHERITE_PICKAXE));
            inventory.setItem(3, gluttonous(Items.WOODEN_SHOVEL));
            inventory.setItem(4, new ItemStack(Items.DIAMOND_PICKAXE));
            inventory.setItem(5, new ItemStack(Items.IRON_SHOVEL));
            inventory.setItem(6, new ItemStack(GnawerContent.GNAWER));
            inventory.setItem(7, new ItemStack(GnawerContent.GNAWING_STAKE));
            inventory.setSelectedSlot(0);
        });
        context.waitTicks(5);
        context.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
        context.waitTicks(5);
        context.takeScreenshot("ravenous-tools-inventory");
        context.setScreen(() -> null);

        // The HUD stays, since hiding it hides the hand too
        aimFrom(context, server, TX + 3.5, FLAT_FEET, 0.5, TX + 3.5, FLAT_FEET + 1, 6);
        context.waitTicks(10);
        context.takeScreenshot("ravenous-pickaxe-first-person");
    }

    // The Gnawer

    private static GnawerTile gnawer(MinecraftServer minecraft)
    {
        return (GnawerTile) minecraft.overworld().getBlockEntity(GNAWER);
    }

    private static int count(Container container, Item item)
    {
        var total = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++)
        {
            if (container.getItem(slot).is(item))
                total += container.getItem(slot).getCount();
        }
        return total;
    }

    private static Container chest(MinecraftServer minecraft)
    {
        return (Container) minecraft.overworld().getBlockEntity(CHEST);
    }

    private static void hold(MinecraftServer minecraft, ItemStack stack)
    {
        var player = player(minecraft);
        player.getInventory().setItem(0, stack);
        player.getInventory().setSelectedSlot(0);
    }

    private static void placeStakesAndLink(ClientGameTestContext context, TestServerContext server, BlockPos top, BlockPos low)
    {
        // The upper stake stands on the stone, the lower one replaces the dirt under it
        server.runCommand("setblock " + low.getX() + " " + low.getY() + " " + low.getZ() + " voidheart:gnawing_stake");
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            hold(minecraft, new ItemStack(GnawerContent.GNAWING_STAKE, 8));
            useOn(player, top.below());
            useOn(player, GNAWER);
        });
        context.waitTicks(2);
        var marking = server.computeOnServer(minecraft -> player(minecraft).getMainHandItem().get(VoidHeartDataComponents.GNAWER_LINK) != null);
        expect(marking, "Expected the stake to remember the Gnawer");

        server.runOnServer(minecraft ->
        {
            useOn(player(minecraft), top);
            useOn(player(minecraft), low);
        });
        context.waitTicks(2);
        var linked = server.computeOnServer(minecraft -> gnawer(minecraft).box() != null);
        expect(linked, "Expected the Gnawer to have its box after the second stake");
    }

    private static void gnawerKeeps(ClientGameTestContext context, TestServerContext server)
    {
        // Creative keeps the camera where it's put for the captures, the Gnawer breaks as its own fake player anyway
        server.runCommand("gamemode creative @a");
        server.runOnServer(minecraft -> player(minecraft).getInventory().clearContent());
        server.runCommand("setblock " + WELL.getX() + " " + WELL.getY() + " " + WELL.getZ() + " voidheart:creative_void_well");
        server.runCommand("setblock " + CHEST.getX() + " " + CHEST.getY() + " " + CHEST.getZ() + " chest");
        server.runCommand("fill " + BOX_TOP.getX() + " " + FLAT_FEET + " " + BOX_TOP.getZ() + " " + BOX_LOW.getX() + " " + (BOX_TOP.getY() - 1) + " " + BOX_LOW.getZ() + " stone");
        server.runCommand("setblock " + INSIDE_CHEST.getX() + " " + INSIDE_CHEST.getY() + " " + INSIDE_CHEST.getZ() + " chest");
        server.runCommand("setblock " + INSIDE_BEDROCK.getX() + " " + INSIDE_BEDROCK.getY() + " " + INSIDE_BEDROCK.getZ() + " bedrock");
        server.runCommand("tp @a " + (GX - 2.5) + " " + FLAT_FEET + " -6.5 -30 20");
        context.waitTicks(5);

        // Placed by hand, so its owner is the player
        server.runOnServer(minecraft ->
        {
            hold(minecraft, new ItemStack(GnawerContent.GNAWER));
            useOn(player(minecraft), GNAWER.below());
        });
        context.waitTicks(2);
        expect(server.computeOnServer(minecraft -> minecraft.overworld().getBlockState(GNAWER).is(GnawerContent.GNAWER)), "Expected the Gnawer placed");

        // A lent silk touch pickaxe keeps the stone whole, and isn't worn
        server.runOnServer(minecraft ->
        {
            var pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
            var silk = minecraft.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH);
            pickaxe.enchant(silk, 1);
            hold(minecraft, pickaxe);
            useOn(player(minecraft), GNAWER);
        });
        context.waitTicks(2);
        expect(server.computeOnServer(minecraft -> !gnawer(minecraft).pickaxe().isEmpty()), "Expected the Gnawer to hold the lent pickaxe");

        placeStakesAndLink(context, server, BOX_TOP, BOX_LOW);

        // A busy Gnawer refuses a new box
        server.runOnServer(minecraft ->
        {
            hold(minecraft, new ItemStack(GnawerContent.GNAWING_STAKE));
            useOn(player(minecraft), GNAWER);
        });
        context.waitTicks(1);
        expect(server.computeOnServer(minecraft -> player(minecraft).getMainHandItem().get(VoidHeartDataComponents.GNAWER_LINK) == null),
                "Expected a busy Gnawer to refuse to be marked again");

        context.waitTicks(40);
        setHudHidden(context, true);
        aimFrom(context, server, GX - 3.5, FLAT_FEET + 3, -7.5, GX + 4, FLAT_FEET, 0.5, 3);
        context.takeScreenshot("gnawer-eating-holding-stake");
        server.runOnServer(minecraft -> hold(minecraft, ItemStack.EMPTY));
        context.waitTicks(10);
        aimFrom(context, server, GX - 1.5, FLAT_FEET + 1.5, -2.5, GX + 0.5, FLAT_FEET + 0.7, 0.5, 3);
        server.runCommand("tick freeze");
        context.waitTicks(2);
        context.takeScreenshot("gnawer-jaw-close");
        server.runCommand("tick unfreeze");
        setHudHidden(context, false);

        waitFor(context, server, 2400, () -> "Expected the Gnawer to finish its box", minecraft -> gnawer(minecraft).isFinished());

        var left = server.computeOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            var standing = 0;
            for (var pos : BlockPos.betweenClosed(BOX_LOW, BOX_TOP))
            {
                if (!level.getBlockState(pos).isAir())
                    standing++;
            }
            return standing;
        });
        // The chest and the bedrock stay, the rest is gone
        expect(left == 2, "Expected only the chest and bedrock left in the box, got " + left);
        expect(server.computeOnServer(minecraft -> minecraft.overworld().getBlockState(INSIDE_CHEST).is(Blocks.CHEST)), "Expected the chest in the box kept");
        expect(server.computeOnServer(minecraft -> minecraft.overworld().getBlockState(INSIDE_BEDROCK).is(Blocks.BEDROCK)), "Expected the bedrock kept");

        var stone = server.computeOnServer(minecraft -> count(chest(minecraft), Items.STONE));
        var cobble = server.computeOnServer(minecraft -> count(chest(minecraft), Items.COBBLESTONE));
        var stakes = server.computeOnServer(minecraft -> count(chest(minecraft), GnawerContent.GNAWING_STAKE.asItem()));
        expect(stone > 100 && cobble == 0, "Expected silk touched stone in the chest, got " + stone + " stone and " + cobble + " cobblestone");
        expect(stakes == 2, "Expected both stakes back in the chest, got " + stakes);
        var worn = server.computeOnServer(minecraft -> gnawer(minecraft).pickaxe().getDamageValue());
        expect(worn == 0, "Expected the lent pickaxe unworn, got damage " + worn);

        setHudHidden(context, true);
        aimFrom(context, server, GX - 3.5, FLAT_FEET + 5, -7.5, GX + 4.5, FLAT_GROUND - 1, 0.5, 5);
        context.takeScreenshot("gnawer-finished-pit");
        setHudHidden(context, false);
    }

    private static void gnawerErases(ClientGameTestContext context, TestServerContext server)
    {
        var top = new BlockPos(GX + 2, FLAT_FEET + 2, -10);
        var low = new BlockPos(GX + 4, FLAT_FEET, -8);
        server.runCommand("fill " + top.getX() + " " + FLAT_FEET + " " + top.getZ() + " " + low.getX() + " " + (top.getY() - 1) + " " + low.getZ() + " stone");

        var chestStone = server.computeOnServer(minecraft -> count(chest(minecraft), Items.STONE));
        server.runOnServer(minecraft ->
        {
            hold(minecraft, ItemStack.EMPTY);
            useOn(player(minecraft), GNAWER);
        });
        context.waitTicks(2);
        expect(server.computeOnServer(minecraft -> minecraft.overworld().getBlockState(GNAWER).getValue(GnawerBlock.ERASE)), "Expected an empty hand to switch the Gnawer to erasing");

        placeStakesAndLink(context, server, top, low);
        waitFor(context, server, 1200, () -> "Expected the Gnawer to finish erasing", minecraft -> gnawer(minecraft).isFinished());

        var erased = server.computeOnServer(minecraft -> airIn(minecraft, new BlockPos(low.getX(), FLAT_FEET, low.getZ()), new BlockPos(top.getX(), top.getY() - 1, top.getZ())));
        expect(erased == 3 * 3 * 2, "Expected the second box erased, got " + erased + " air");
        var after = server.computeOnServer(minecraft -> count(chest(minecraft), Items.STONE));
        expect(after == chestStone, "Expected nothing kept while erasing, the chest went from " + chestStone + " to " + after + " stone");
    }

    private static void gnawerDropsItsPickaxe(ClientGameTestContext context, TestServerContext server)
    {
        clearScene(server);
        // A creative player's breaks drop nothing
        server.runCommand("gamemode survival @a");
        context.waitTicks(2);
        server.runOnServer(minecraft ->
        {
            hold(minecraft, new ItemStack(Items.IRON_PICKAXE));
            player(minecraft).gameMode.destroyBlock(GNAWER);
        });
        context.waitTicks(3);
        var dropped = server.computeOnServer(minecraft ->
        {
            ServerLevel level = minecraft.overworld();
            return level.getEntitiesOfClass(ItemEntity.class, new AABB(GNAWER).inflate(2)).stream().map(entity -> entity.getItem().getItem()).toList();
        });
        expect(dropped.contains(GnawerContent.GNAWER.asItem()), "Expected the Gnawer to drop itself, got " + dropped);
        expect(dropped.contains(Items.DIAMOND_PICKAXE), "Expected the Gnawer to drop its lent pickaxe, got " + dropped);
    }
}
