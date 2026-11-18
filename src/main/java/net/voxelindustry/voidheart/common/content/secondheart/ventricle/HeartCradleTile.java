package net.voxelindustry.voidheart.common.content.secondheart.ventricle;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.steamlayer.tile.TileBase;
import net.voxelindustry.voidheart.common.content.repair.ExperienceSkullTile;
import net.voxelindustry.voidheart.common.content.research.Knowledge;
import net.voxelindustry.voidheart.common.content.research.VoidGrafts;
import net.voxelindustry.voidheart.common.content.secondheart.SecondHeart;
import net.voxelindustry.voidheart.common.content.secondheart.ventricle.VentricleCeremony.Conditions;
import net.voxelindustry.voidheart.common.content.secondheart.ventricle.VentricleCeremony.Ingredient;
import net.voxelindustry.voidheart.common.content.secondheart.ventricle.VentricleCeremony.Phase;
import net.voxelindustry.voidheart.common.content.secondheart.ventricle.VentricleLayout.Orientation;
import net.voxelindustry.voidheart.common.energy.VoidEnergy;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;
import net.voxelindustry.voidheart.common.setup.VoidHeartSounds;
import net.voxelindustry.voidheart.common.setup.VoidHeartTags;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static net.voxelindustry.voidheart.VoidHeart.MODID;
import static net.voxelindustry.voidheart.common.content.secondheart.ventricle.VentricleBalance.*;

/**
 * The Twin Ventricle's heart cradle, which runs the rite of the second heart. It takes the offering from one celebrant, draws its experience from
 * skulls nearby, then kindles the basins and beats while the celebrant stands in it, calling waves through tears, until the heart stops on them.
 * <p>
 * Clients get the phase, the progress and whether it is moving on, to draw the chamber's pulses, and count on between syncs.
 */
public class HeartCradleTile extends TileBase
{
    public static final Identifier RESEARCH = Identifier.fromNamespaceAndPath(MODID, "second_heart");

    private static final int CHECK_INTERVAL = 20;
    private static final int SYNC_INTERVAL  = 40;

    private VentricleCeremony ceremony = new VentricleCeremony();
    private ItemStack         contraption = ItemStack.EMPTY;
    private Orientation       orientation = Orientation.ALL.getFirst();
    private boolean           complete;
    private int               missing = VentricleLayout.BLOCKS;
    private boolean           running;
    private boolean           checkRequested = true;

    public HeartCradleTile(BlockPos pos, BlockState state)
    {
        super(TwinVentricleContent.HEART_CRADLE_TILE, pos, state);
    }

    public static @Nullable Ingredient ingredientOf(ItemStack stack)
    {
        if (stack.is(VoidHeartItems.KERNACIAN_CORE))
            return Ingredient.KERNACIAN_CORE;
        if (stack.is(VoidHeartTags.BLESSED_CONTRAPTIONS))
            return Ingredient.CONTRAPTION;
        if (stack.is(VoidHeartItems.MEMORIAM_SHARD))
            return Ingredient.MEMORIAM;
        if (stack.is(VoidHeartItems.WRAITH_INK))
            return Ingredient.WRAITH_INK;
        return null;
    }

    public VentricleCeremony ceremony()
    {
        return ceremony;
    }

    public Orientation orientation()
    {
        return orientation;
    }

    public boolean isComplete()
    {
        return complete;
    }

    public boolean isRunning()
    {
        return running;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, HeartCradleTile tile)
    {
        if (level instanceof ServerLevel serverLevel)
            tile.serverTick(serverLevel);
        else
            tile.clientTick();
    }

    //
    // Server
    //

    private void serverTick(ServerLevel level)
    {
        if (checkRequested || level.getGameTime() % CHECK_INTERVAL == 0)
            checkStructure(level);

        boolean wasRunning = running;
        running = false;
        switch (ceremony.phase())
        {
            case OFFERING -> drawExperience(level);
            case KINDLING, BEATING -> runRite(level);
            default ->
            {
            }
        }
        if (running != wasRunning || running && level.getGameTime() % SYNC_INTERVAL == 0)
            sync();
    }

    private void checkStructure(ServerLevel level)
    {
        checkRequested = false;
        orientation = VentricleLayout.bestFit(offset -> TwinVentricleContent.partAt(level, worldPosition.offset(offset)));
        int nowMissing = VentricleLayout.missing(orientation, offset -> TwinVentricleContent.partAt(level, worldPosition.offset(offset))).size();
        boolean nowComplete = nowMissing == 0;
        if (nowComplete != complete || nowMissing != missing)
        {
            complete = nowComplete;
            missing = nowMissing;
            sync();
        }
    }

    public void requestCheck()
    {
        checkRequested = true;
    }

    private void drawExperience(ServerLevel level)
    {
        if (!ceremony.itemsComplete())
            return;
        int wanted = Math.min(EXPERIENCE_PER_TICK, ceremony.experienceNeeded());
        for (var pos : BlockPos.betweenClosed(worldPosition.offset(-SKULL_RADIUS, -1, -SKULL_RADIUS), worldPosition.offset(SKULL_RADIUS, 3, SKULL_RADIUS)))
        {
            if (wanted <= 0)
                break;
            if (!(level.getBlockEntity(pos) instanceof ExperienceSkullTile skull) || skull.getExperience() <= 0)
                continue;
            int taken = ceremony.takeExperience(Math.min(wanted, skull.getExperience()));
            skull.setExperience(skull.getExperience() - taken);
            skull.setChanged();
            wanted -= taken;
        }
        setChanged();
        if (ceremony.phase() == Phase.KINDLING)
        {
            level.playSound(null, worldPosition, VoidHeartSounds.VENTRICLE_KINDLE, SoundSource.BLOCKS, 1.5F, 1);
            sync();
        }
    }

    private void runRite(ServerLevel level)
    {
        var celebrantId = ceremony.celebrant();
        var celebrant = celebrantId == null ? null : level.getServer().getPlayerList().getPlayer(celebrantId);
        boolean lost = celebrant == null || !celebrant.isAlive();
        boolean here = !lost && celebrant.level() == level && inCradle(celebrant.position());

        var basins = basins(level);
        int fuel = ceremony.phase() == Phase.KINDLING ? KINDLING_FUEL_PER_TICK : BEATING_FUEL_PER_TICK;
        boolean fueled = basins != null && basins[0].holds(fuel) && basins[1].holds(fuel);
        boolean powered = false;
        if (ceremony.wantsPower(complete, here) && fueled && VoidEnergy.available(level, worldPosition) >= ENERGY_PER_TICK)
            powered = VoidEnergy.draw(level, worldPosition, ENERGY_PER_TICK) >= ENERGY_PER_TICK;
        if (powered)
        {
            basins[0].burn(fuel);
            basins[1].burn(fuel);
            running = true;
        }

        var events = ceremony.tick(new Conditions(complete, powered, fueled, here, lost));
        if (!events.isEmpty() || running)
            setChanged();
        for (var event : events)
        {
            switch (event)
            {
                case KINDLED -> sync();
                case BEAT -> level.playSound(null, worldPosition, VoidHeartSounds.VENTRICLE_BEAT, SoundSource.BLOCKS, 2, 1);
                case WAVE -> VentricleWaveSpawner.callWave(level, worldPosition, ceremony.waves() - 1, celebrantId);
                case RESTARTED -> sync();
                case STOPPED -> stopHeart(level, celebrant);
            }
        }
    }

    private void stopHeart(ServerLevel level, @Nullable ServerPlayer celebrant)
    {
        contraption = ItemStack.EMPTY;
        sync();
        level.playSound(null, worldPosition, VoidHeartSounds.VENTRICLE_STOP, SoundSource.BLOCKS, 2, 1);
        if (celebrant == null)
            return;
        Knowledge.attune(level.getServer(), celebrant.getUUID(), VoidGrafts.SECOND_HEART);
        SecondHeart.fireRite(celebrant);
        celebrant.sendSystemMessage(Component.translatable("voidheart.heart_cradle.stopped"));
    }

    public boolean inCradle(Vec3 feet)
    {
        double dx = feet.x - (worldPosition.getX() + 0.5), dz = feet.z - (worldPosition.getZ() + 0.5);
        return dx * dx + dz * dz <= CRADLE_REACH * CRADLE_REACH && feet.y >= worldPosition.getY() && feet.y <= worldPosition.getY() + 1.2;
    }

    /**
     * @return the essence basin then the Contravoid basin, or null while either is missing
     */
    public VentricleBasinTile @Nullable [] basins(Level level)
    {
        if (!(level.getBlockEntity(worldPosition.offset(orientation.basin(true))) instanceof VentricleBasinTile essence))
            return null;
        if (!(level.getBlockEntity(worldPosition.offset(orientation.basin(false))) instanceof VentricleBasinTile contravoid))
            return null;
        return new VentricleBasinTile[]{essence, contravoid};
    }

    //
    // Players
    //

    public void offer(ServerPlayer player, ItemStack stack)
    {
        var ingredient = ingredientOf(stack);
        if (ingredient == null)
            return;
        var refusal = refusalFor(player);
        if (refusal != null)
        {
            player.sendOverlayMessage(Component.translatable(refusal));
            return;
        }
        int taken = ceremony.offer(player.getUUID(), ingredient, stack.getCount());
        if (taken <= 0)
        {
            player.sendOverlayMessage(Component.translatable("voidheart.heart_cradle.has_enough"));
            return;
        }
        if (ingredient == Ingredient.CONTRAPTION)
            contraption = stack.copyWithCount(1);
        stack.consume(taken, player);
        level.playSound(null, worldPosition, VoidHeartSounds.VENTRICLE_OFFER, SoundSource.BLOCKS, 1, 0.9F + level.getRandom().nextFloat() * 0.2F);
        setChanged();
        sync();
    }

    private @Nullable String refusalFor(ServerPlayer player)
    {
        if (!ceremony.acceptsFrom(player.getUUID()))
            return "voidheart.heart_cradle.not_yours";
        if (SecondHeart.state(player).owned())
            return "voidheart.heart_cradle.already_beating";
        if (!Knowledge.hasCompleted(player.level().getServer(), player.getUUID(), RESEARCH))
            return "voidheart.heart_cradle.unknown";
        if (VoidGrafts.attunementOf(player) < VoidGrafts.MARROW)
            return "voidheart.heart_cradle.not_attuned";
        return null;
    }

    void withdraw(ServerPlayer player)
    {
        boolean hadContraption = ceremony.offered(Ingredient.CONTRAPTION) > 0;
        int drawn = ceremony.experience();
        var back = ceremony.withdraw(player.getUUID());
        if (back.isEmpty())
        {
            tellStatus(player);
            return;
        }
        // What was already drunk from the skulls comes back too, as it does when the cradle is broken
        if (drawn > 0)
            player.giveExperiencePoints(drawn);
        for (var entry : back.entrySet())
            for (var stack : stacksOf(entry.getKey(), entry.getValue(), hadContraption))
                if (!player.getInventory().add(stack))
                    Containers.dropItemStack(player.level(), player.getX(), player.getY(), player.getZ(), stack);
        contraption = ItemStack.EMPTY;
        setChanged();
        sync();
    }

    private List<ItemStack> stacksOf(Ingredient ingredient, int count, boolean hadContraption)
    {
        var stacks = new ArrayList<ItemStack>();
        if (count <= 0)
            return stacks;
        switch (ingredient)
        {
            case KERNACIAN_CORE -> stacks.add(new ItemStack(VoidHeartItems.KERNACIAN_CORE, count));
            case CONTRAPTION ->
            {
                if (hadContraption && !contraption.isEmpty())
                    stacks.add(contraption.copy());
            }
            case MEMORIAM -> stacks.add(new ItemStack(VoidHeartItems.MEMORIAM_SHARD, count));
            case WRAITH_INK -> stacks.add(new ItemStack(VoidHeartItems.WRAITH_INK, count));
        }
        return stacks;
    }

    void tellStatus(ServerPlayer player)
    {
        Component message;
        if (!complete)
            message = Component.translatable("voidheart.heart_cradle.incomplete", missing);
        else
            message = switch (ceremony.phase())
            {
                case IDLE -> Component.translatable("voidheart.heart_cradle.idle");
                case OFFERING -> ceremony.itemsComplete()
                        ? Component.translatable("voidheart.heart_cradle.drinking")
                        : Component.translatable("voidheart.heart_cradle.waiting", lacking());
                case KINDLING -> Component.translatable(running ? "voidheart.heart_cradle.kindling" : "voidheart.heart_cradle.starved");
                case BEATING -> Component.translatable(running ? "voidheart.heart_cradle.beating" : "voidheart.heart_cradle.waiting_celebrant");
            };
        player.sendOverlayMessage(message);
    }

    private Component lacking()
    {
        var text = Component.empty();
        boolean first = true;
        for (var ingredient : Ingredient.values())
        {
            int needed = ceremony.stillNeeded(ingredient);
            if (needed <= 0)
                continue;
            if (!first)
                text.append(", ");
            first = false;
            text.append(Component.translatable("voidheart.heart_cradle.ingredient." + ingredient.name().toLowerCase(), needed));
        }
        return text;
    }

    /** For commands and tests. */
    public void skipTo(Phase phase, int ticksIn, @Nullable ServerPlayer celebrant)
    {
        if (celebrant != null)
            ceremony.setCelebrant(celebrant.getUUID());
        ceremony.skipTo(phase, ticksIn);
        setChanged();
        sync();
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state)
    {
        if (level != null && !level.isClientSide() && ceremony.phase() != Phase.IDLE)
        {
            boolean hadContraption = ceremony.offered(Ingredient.CONTRAPTION) > 0;
            for (var ingredient : Ingredient.values())
                for (var stack : stacksOf(ingredient, ceremony.offered(ingredient), hadContraption))
                    Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
            if (ceremony.phase() == Phase.OFFERING && ceremony.experience() > 0 && level instanceof ServerLevel serverLevel)
                ExperienceOrb.award(serverLevel, Vec3.atCenterOf(pos), ceremony.experience());
        }
        super.preRemoveSideEffects(pos, state);
    }

    //
    // Client
    //

    private int clientTicks;

    private void clientTick()
    {
        // Counts on between syncs, so the pulses stay smooth
        if (running)
            clientTicks++;
    }

    /**
     * @return ticks of kindling or beating the client has seen, synced progress plus what it counted since
     */
    public int clientProgress()
    {
        return (ceremony.phase() == Phase.KINDLING ? ceremony.kindling() : ceremony.beating()) + clientTicks;
    }

    //
    // Saving
    //

    @Override
    protected void loadAdditional(ValueInput tag)
    {
        super.loadAdditional(tag);
        ceremony = tag.read("ceremony", VentricleCeremony.CODEC).orElseGet(VentricleCeremony::new);
        contraption = tag.read("contraption", ItemStack.CODEC).orElse(ItemStack.EMPTY);
        complete = tag.getBooleanOr("complete", false);
        missing = tag.getIntOr("missing", VentricleLayout.BLOCKS);
        running = tag.getBooleanOr("running", false);
        var axis = tag.getStringOr("axis", "x").equals("z") ? Direction.Axis.Z : Direction.Axis.X;
        orientation = new Orientation(axis, tag.getBooleanOr("rubrumPositive", true));
        clientTicks = 0;
        checkRequested = true;
    }

    @Override
    protected void saveAdditional(ValueOutput tag)
    {
        super.saveAdditional(tag);
        tag.store("ceremony", VentricleCeremony.CODEC, ceremony);
        if (!contraption.isEmpty())
            tag.store("contraption", ItemStack.CODEC, contraption);
        tag.putBoolean("complete", complete);
        tag.putInt("missing", missing);
        tag.putBoolean("running", running);
        tag.putString("axis", orientation.axis() == Direction.Axis.Z ? "z" : "x");
        tag.putBoolean("rubrumPositive", orientation.rubrumPositive());
    }
}
