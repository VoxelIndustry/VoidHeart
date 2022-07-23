package net.voxelindustry.voidheart.common.content.repair;

import lombok.Getter;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
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
    private BlockPos  consumingPillarPos   = BlockPos.ORIGIN;
    private ItemStack cachedToConsume      = ItemStack.EMPTY;

    private final List<VoidPillarTile>      pillars          = new ArrayList<>(2);
    private final List<ExperienceSkullTile> experienceSkulls = new ArrayList<>(1);

    public MendingAltarTile(BlockPos pos, BlockState state)
    {
        super(VoidHeartTiles.MENDING_ALTAR, pos, state);
    }

    public static void tick(World world, BlockPos pos, BlockState state, MendingAltarTile altar)
    {
        if (!altar.isCrafting())
            return;

        if (altar.getTool().isEmpty() || altar.getTool().getDamage() == 0)
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

    public void setTool(PlayerEntity player, ItemStack stack)
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
                player.sendMessage(Text.translatable(MODID + ".altar.missing_pillars", 8 - pillars.size()), true);
        }
        else if (isCrafting)
            stopCrafting();

        markDirty();
        sync();
    }

    @Override
    public void readNbt(NbtCompound nbt)
    {
        super.readNbt(nbt);

        tool = ItemStack.fromNbt(nbt.getCompound("tool"));
    }

    @Override
    protected void writeNbt(NbtCompound nbt)
    {
        super.writeNbt(nbt);

        nbt.put("tool", tool.writeNbt(new NbtCompound()));
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
        BlockPos.Mutable searchPos = getPos().mutableCopy();

        searchPos.set(getPos(), -1, 1, 0);
        findExperienceSkull(searchPos);

        searchPos.set(getPos(), 1, 1, 0);
        findExperienceSkull(searchPos);

        searchPos.set(getPos(), 0, 1, -1);
        findExperienceSkull(searchPos);

        searchPos.set(getPos(), 0, 1, 1);
        findExperienceSkull(searchPos);
    }

    private void findExperienceSkull(BlockPos pos)
    {
        if (world.getBlockState(pos).getBlock() != VoidHeartBlocks.EXPERIENCE_SKULL)
            return;

        var skull = world.getBlockEntity(pos, VoidHeartTiles.EXPERIENCE_SKULL);
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
        BlockPos.Mutable searchPos = getPos().mutableCopy();

        searchPos.set(getPos(), -1, 0, 0);
        findPillar(searchPos);

        searchPos.set(getPos(), 1, 0, 0);
        findPillar(searchPos);

        searchPos.set(getPos(), 0, 0, -1);
        findPillar(searchPos);

        searchPos.set(getPos(), 0, 0, 1);
        findPillar(searchPos);
    }

    private void findPillar(BlockPos pos)
    {
        if (world.getBlockState(pos).getBlock() != VoidHeartBlocks.VOID_PILLAR)
            return;

        var pillar = world.getBlockEntity(pos, VoidHeartTiles.VOID_PILLAR);
        if (pillar.isEmpty())
            return;

        if (isCrafting)
            pillar.get().addAltar(getPos());

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

        if (!getCachedState().get(Properties.LIT))
            getWorld().setBlockState(getPos(), getCachedState().with(Properties.LIT, true));

        pillars.forEach(pillar -> pillar.addAltar(getPos()));
    }

    private void stopCrafting()
    {
        isCrafting = false;

        if (getCachedState().get(Properties.LIT))
            getWorld().setBlockState(getPos(), getCachedState().with(Properties.LIT, false));

        pillars.forEach(pillar -> pillar.removeAltar(getPos()));
    }
}
