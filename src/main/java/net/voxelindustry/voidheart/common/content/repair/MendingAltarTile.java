package net.voxelindustry.voidheart.common.content.repair;

import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.voxelindustry.steamlayer.tile.TileBase;
import net.voxelindustry.voidheart.common.content.pillar.PillarLinkedTile;
import net.voxelindustry.voidheart.common.content.pillar.VoidPillarTile;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

import java.util.ArrayList;
import java.util.List;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class MendingAltarTile extends TileBase implements PillarLinkedTile
{
    public static final int ITEM_EATING_TIME = 60;

    @Getter
    private ItemStack tool = ItemStack.EMPTY;

    @Getter
    private boolean isCrafting;

    private int repairProgress;
    private int consumeProgress;

    private int       consumingPillarIndex = -1;
    private BlockPos  consumingPillarPos   = BlockPos.ZERO;
    private ItemStack cachedToConsume      = ItemStack.EMPTY;

    private final List<VoidPillarTile>      pillars          = new ArrayList<>(2);
    private final List<ExperienceSkullTile> experienceSkulls = new ArrayList<>(1);

    public MendingAltarTile(BlockPos pos, BlockState state)
    {
        super(VoidHeartTiles.MENDING_ALTAR, pos, state);
    }

    public static void tick(Level world, BlockPos pos, BlockState state, MendingAltarTile altar)
    {
        if (!altar.isCrafting())
            return;

        if (altar.getTool().isEmpty() || altar.getTool().getDamageValue() == 0)
        {
            altar.stopCrafting();
            altar.sync();
            return;
        }

        if (altar.consumeProgress == 0 && altar.pillars.size() < 2)
            altar.refreshPillars();

        if (altar.experienceSkulls.isEmpty())
            altar.refreshExperienceSkulls();
    }

    public void setTool(Player player, ItemStack stack)
    {
        this.tool = stack;

        if (!stack.isEmpty())
        {
            searchPillars();
            if (pillars.size() >= 2)
            {
                startCrafting();
            }
            else
                player.displayClientMessage(Component.translatable(MODID + ".altar.missing_pillars", 8 - pillars.size()), true);
        }
        else if (isCrafting)
            stopCrafting();

        setChanged();
        sync();
    }

    @Override
    public void load(CompoundTag nbt)
    {
        super.load(nbt);

        tool = ItemStack.of(nbt.getCompound("tool"));
    }

    @Override
    protected void saveAdditional(CompoundTag nbt)
    {
        super.saveAdditional(nbt);

        nbt.put("tool", tool.save(new CompoundTag()));
    }

    private void refreshExperienceSkulls()
    {
        experienceSkulls.removeIf(BlockEntity::isRemoved);

        if (experienceSkulls.isEmpty())
            searchExperienceSkulls();
    }

    public void searchExperienceSkulls()
    {
        experienceSkulls.clear();
        BlockPos.MutableBlockPos searchPos = getBlockPos().mutable();

        searchPos.setWithOffset(getBlockPos(), -1, 1, 0);
        findExperienceSkull(searchPos);

        searchPos.setWithOffset(getBlockPos(), 1, 1, 0);
        findExperienceSkull(searchPos);

        searchPos.setWithOffset(getBlockPos(), 0, 1, -1);
        findExperienceSkull(searchPos);

        searchPos.setWithOffset(getBlockPos(), 0, 1, 1);
        findExperienceSkull(searchPos);
    }

    private void findExperienceSkull(BlockPos pos)
    {
        if (level.getBlockState(pos).getBlock() != VoidHeartBlocks.EXPERIENCE_SKULL)
            return;

        var skull = level.getBlockEntity(pos, VoidHeartTiles.EXPERIENCE_SKULL);
        if (skull.isEmpty())
            return;

        experienceSkulls.add(skull.get());
    }

    private void refreshPillars()
    {
        pillars.removeIf(BlockEntity::isRemoved);

        if (pillars.size() < 2)
            searchPillars();
    }

    public void searchPillars()
    {
        pillars.clear();
        BlockPos.MutableBlockPos searchPos = getBlockPos().mutable();

        searchPos.setWithOffset(getBlockPos(), -1, 0, 0);
        findPillar(searchPos);

        searchPos.setWithOffset(getBlockPos(), 1, 0, 0);
        findPillar(searchPos);

        searchPos.setWithOffset(getBlockPos(), 0, 0, -1);
        findPillar(searchPos);

        searchPos.setWithOffset(getBlockPos(), 0, 0, 1);
        findPillar(searchPos);
    }

    private void findPillar(BlockPos pos)
    {
        if (level.getBlockState(pos).getBlock() != VoidHeartBlocks.VOID_PILLAR)
            return;

        var pillar = level.getBlockEntity(pos, VoidHeartTiles.VOID_PILLAR);
        if (pillar.isEmpty())
            return;

        if (isCrafting)
            pillar.get().addAltar(getBlockPos());

        pillars.add(pillar.get());
    }

    @Override
    public void removePillar(VoidPillarTile pillar)
    {
        if (consumingPillarIndex == pillars.indexOf(pillar))
        {
            consumingPillarIndex = -1;
            cachedToConsume = ItemStack.EMPTY;
        }

        pillars.remove(pillar);
    }

    private void startCrafting()
    {
        isCrafting = true;

        if (!getBlockState().getValue(BlockStateProperties.LIT))
            getLevel().setBlockAndUpdate(getBlockPos(), getBlockState().setValue(BlockStateProperties.LIT, true));

        pillars.forEach(pillar -> pillar.addAltar(getBlockPos()));
    }

    private void stopCrafting()
    {
        isCrafting = false;

        if (getBlockState().getValue(BlockStateProperties.LIT))
            getLevel().setBlockAndUpdate(getBlockPos(), getBlockState().setValue(BlockStateProperties.LIT, false));

        pillars.forEach(pillar -> pillar.removeAltar(getBlockPos()));
    }
}
