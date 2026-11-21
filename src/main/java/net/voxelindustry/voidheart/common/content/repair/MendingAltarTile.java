package net.voxelindustry.voidheart.common.content.repair;

import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.voxelindustry.steamlayer.tile.TileBase;
import net.voxelindustry.voidheart.common.content.altar.AltarExperienceParticleEffect;
import net.voxelindustry.voidheart.common.content.altar.AltarItemParticleEffect;
import net.voxelindustry.voidheart.common.content.altar.AltarStreams;
import net.voxelindustry.voidheart.common.content.altar.VoidAltarTile;
import net.voxelindustry.voidheart.common.content.pillar.PillarLinkedTile;
import net.voxelindustry.voidheart.common.content.pillar.VoidPillarTile;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Mends the damaged tool laid on it, first with repair materials eaten from the Void Pillars beside it, then with experience drawn from Experience
 * Skulls nearby. It keeps mending until the tool is whole or taken back.
 */
public class MendingAltarTile extends TileBase implements PillarLinkedTile
{
    public static final int ITEM_EATING_TIME     = 60;
    public static final int EXPERIENCE_GULP_TIME = 10;
    public static final int EXPERIENCE_GULP      = 20;
    /**
     * How often an altar with nothing to mend from looks again for materials and skulls.
     */
    public static final int SEARCH_INTERVAL = 20;

    @Getter
    private ItemStack tool = ItemStack.EMPTY;

    @Getter
    private boolean repairing;

    private int consumeProgress;

    private BlockPos  consumingPillarPos = BlockPos.ZERO;
    private ItemStack cachedToConsume    = ItemStack.EMPTY;

    private BlockPos consumingSkullPos    = BlockPos.ZERO;
    private int      skullConsumeProgress;

    private final List<VoidPillarTile> pillars = new ArrayList<>(4);

    ////////////
    // CLIENT //
    ////////////

    private Vector3f @Nullable [] itemStream;
    private Vector3f @Nullable [] skullStream;

    public MendingAltarTile(BlockPos pos, BlockState state)
    {
        super(VoidHeartTiles.MENDING_ALTAR, pos, state);
    }

    @Override
    public void setLevel(Level world)
    {
        super.setLevel(world);

        // Called on world load
        sync();
    }

    public static void tick(Level world, BlockPos pos, BlockState state, MendingAltarTile altar)
    {
        if (world.isClientSide())
        {
            altar.playParticleEffects();
            return;
        }

        if (!altar.repairing)
            return;

        if (altar.tool.isEmpty() || !altar.tool.isDamaged())
        {
            altar.stopRepairing();
            if (!altar.tool.isEmpty())
                world.playSound(null, pos, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.4F, 1.4F);
            altar.sync();
            return;
        }

        if (!altar.consumingPillarPos.equals(BlockPos.ZERO))
            altar.eatMaterial();
        else if (!altar.consumingSkullPos.equals(BlockPos.ZERO))
            altar.drinkExperience();
        else if (world.getGameTime() % SEARCH_INTERVAL == 0)
            altar.chooseSource();
    }

    public void setTool(Player player, ItemStack stack)
    {
        if (repairing)
            stopRepairing();

        tool = stack;

        if (!stack.isEmpty() && stack.isDamaged())
        {
            startRepairing();
            if (!chooseSource())
                player.sendOverlayMessage(Component.translatable(MODID + ".mending_altar.no_source"));
        }

        setChanged();
        sync();
    }

    /**
     * @return whether a repair material or some experience was found to mend from
     */
    private boolean chooseSource()
    {
        resetConsumption();
        searchPillars();

        for (var pillar : pillars)
        {
            if (!pillar.getStack().isEmpty() && tool.isValidRepairItem(pillar.getStack()))
            {
                consumingPillarPos = pillar.getBlockPos().immutable();
                cachedToConsume = pillar.getStack().copyWithCount(1);
                sync();
                return true;
            }
        }

        var skull = findSkullToDrain();
        if (skull != null)
        {
            consumingSkullPos = skull.getBlockPos().immutable();
            sync();
            return true;
        }
        return false;
    }

    private void eatMaterial()
    {
        var pillar = pillarAt(consumingPillarPos);
        if (pillar == null || !ItemStack.isSameItemSameComponents(pillar.getStack(), cachedToConsume))
        {
            chooseSource();
            return;
        }

        if (consumeProgress < ITEM_EATING_TIME)
        {
            consumeProgress++;
            return;
        }

        var repaired = MendingRepair.materialRepair(tool.getMaxDamage(), tool.getDamageValue());
        tool.setDamageValue(tool.getDamageValue() - repaired);
        var left = pillar.getStack().copy();
        left.shrink(1);
        pillar.setStack(left);
        getLevel().playSound(null, getBlockPos(), SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.3F, getLevel().getRandom().nextFloat() * 0.2F + 1.2F);

        setChanged();
        if (tool.isDamaged())
            chooseSource();
        else
            resetConsumption();
        sync();
    }

    private void drinkExperience()
    {
        if (!(getLevel().getBlockEntity(consumingSkullPos) instanceof ExperienceSkullTile skull) || skull.getExperience() <= 0)
        {
            chooseSource();
            return;
        }

        if (skullConsumeProgress < EXPERIENCE_GULP_TIME)
        {
            skullConsumeProgress++;
            return;
        }
        skullConsumeProgress = 0;

        var taken = MendingRepair.experienceToDrain(tool.getDamageValue(), skull.getExperience(), EXPERIENCE_GULP);
        skull.setExperience(skull.getExperience() - taken);
        skull.setChanged();
        tool.setDamageValue(MendingRepair.damageAfterExperience(tool.getDamageValue(), taken));
        getLevel().playSound(null, getBlockPos(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.3F, getLevel().getRandom().nextFloat() * 0.4F + 0.8F);

        setChanged();
        sync();
    }

    private @Nullable ExperienceSkullTile findSkullToDrain()
    {
        var radius = VoidAltarTile.SKULL_SEARCH_RADIUS;
        ExperienceSkullTile leastFilled = null;
        for (var pos : BlockPos.betweenClosed(getBlockPos().offset(-radius, -1, -radius), getBlockPos().offset(radius, 3, radius)))
        {
            if (getLevel().getBlockEntity(pos) instanceof ExperienceSkullTile skull && skull.getExperience() > 0
                    && (leastFilled == null || skull.getExperience() < leastFilled.getExperience()))
                leastFilled = skull;
        }
        return leastFilled;
    }

    private void searchPillars()
    {
        pillars.removeIf(VoidPillarTile::isRemoved);
        for (var direction : Direction.Plane.HORIZONTAL)
        {
            if (getLevel().getBlockEntity(getBlockPos().relative(direction)) instanceof VoidPillarTile pillar && !pillars.contains(pillar))
            {
                pillars.add(pillar);
                if (repairing)
                    pillar.addAltar(getBlockPos());
            }
        }
    }

    private @Nullable VoidPillarTile pillarAt(BlockPos pos)
    {
        return pillars.stream().filter(pillar -> !pillar.isRemoved() && pillar.getBlockPos().equals(pos)).findFirst().orElse(null);
    }

    private void startRepairing()
    {
        repairing = true;
        setLit(true);
        searchPillars();
    }

    private void stopRepairing()
    {
        repairing = false;
        resetConsumption();
        setLit(false);
        pillars.forEach(pillar -> pillar.removeAltar(getBlockPos()));
        pillars.clear();
        setChanged();
    }

    private void resetConsumption()
    {
        consumeProgress = 0;
        consumingPillarPos = BlockPos.ZERO;
        cachedToConsume = ItemStack.EMPTY;
        consumingSkullPos = BlockPos.ZERO;
        skullConsumeProgress = 0;
    }

    private void setLit(boolean lit)
    {
        var state = getBlockState();
        if (state.hasProperty(BlockStateProperties.LIT) && state.getValue(BlockStateProperties.LIT) != lit)
            getLevel().setBlockAndUpdate(getBlockPos(), state.setValue(BlockStateProperties.LIT, lit));
    }

    @Override
    public void onPillarStackChanged(VoidPillarTile pillar)
    {
        if (pillar.getBlockPos().equals(consumingPillarPos) && !ItemStack.isSameItemSameComponents(pillar.getStack(), cachedToConsume))
        {
            resetConsumption();
            sync();
        }
    }

    @Override
    public void removePillar(VoidPillarTile pillar)
    {
        if (pillar.getBlockPos().equals(consumingPillarPos))
            resetConsumption();
        pillars.remove(pillar);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state)
    {
        pillars.forEach(pillar -> pillar.removeAltar(pos));
        Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, tool);
        super.preRemoveSideEffects(pos, state);
    }

    private void playParticleEffects()
    {
        if (!repairing)
            return;

        var end = AltarStreams.altarTop(getBlockPos());
        if (!consumingPillarPos.equals(BlockPos.ZERO) && itemStream != null)
        {
            for (int index = 0; index < 3; index++)
                getLevel().addParticle(new AltarItemParticleEffect(cachedToConsume, itemStream[0], itemStream[1]),
                        consumingPillarPos.getX() + 0.5, consumingPillarPos.getY() + 1 + 4 / 16D, consumingPillarPos.getZ() + 0.5,
                        end.x(), end.y(), end.z());
        }
        if (!consumingSkullPos.equals(BlockPos.ZERO) && skullStream != null)
        {
            for (int index = 0; index < 2; index++)
                getLevel().addParticle(new AltarExperienceParticleEffect(skullStream[0], skullStream[1]),
                        consumingSkullPos.getX() + 0.5, consumingSkullPos.getY() + 0.5, consumingSkullPos.getZ() + 0.5,
                        end.x(), end.y(), end.z());
        }
    }

    @Override
    protected void loadAdditional(ValueInput tag)
    {
        super.loadAdditional(tag);

        tool = tag.read("tool", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        repairing = tag.getBooleanOr("repairing", false);
        consumeProgress = tag.getIntOr("consumeProgress", 0);
        skullConsumeProgress = tag.getIntOr("skullConsumeProgress", 0);
        cachedToConsume = tag.read("cachedToConsume", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);

        var previousPillar = consumingPillarPos;
        var previousSkull = consumingSkullPos;
        consumingPillarPos = BlockPos.of(tag.getLongOr("consumingPillarPos", BlockPos.ZERO.asLong()));
        consumingSkullPos = BlockPos.of(tag.getLongOr("consumingSkullPos", BlockPos.ZERO.asLong()));

        if (isClient())
        {
            if (!consumingPillarPos.equals(previousPillar))
                itemStream = consumingPillarPos.equals(BlockPos.ZERO) ? null : streamFrom(new Vector3f(consumingPillarPos.getX() + 0.5F,
                        consumingPillarPos.getY() + 1 + 4 / 16F, consumingPillarPos.getZ() + 0.5F));
            if (!consumingSkullPos.equals(previousSkull))
                skullStream = consumingSkullPos.equals(BlockPos.ZERO) ? null : streamFrom(new Vector3f(consumingSkullPos.getX() + 0.5F,
                        consumingSkullPos.getY() + 0.5F, consumingSkullPos.getZ() + 0.5F));
        }
    }

    private Vector3f[] streamFrom(Vector3f start)
    {
        return AltarStreams.controlPoints(getLevel().getRandom(), start, AltarStreams.altarTop(getBlockPos()));
    }

    @Override
    protected void saveAdditional(ValueOutput tag)
    {
        super.saveAdditional(tag);

        tag.store("tool", ItemStack.OPTIONAL_CODEC, tool);
        tag.putBoolean("repairing", repairing);
        tag.putInt("consumeProgress", consumeProgress);
        tag.putInt("skullConsumeProgress", skullConsumeProgress);
        tag.store("cachedToConsume", ItemStack.OPTIONAL_CODEC, cachedToConsume);
        tag.putLong("consumingPillarPos", consumingPillarPos.asLong());
        tag.putLong("consumingSkullPos", consumingSkullPos.asLong());
    }
}
