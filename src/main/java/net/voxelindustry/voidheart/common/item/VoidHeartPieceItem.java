package net.voxelindustry.voidheart.common.item;

import net.fabricmc.fabric.api.dimension.v1.FabricDimensions;
import net.minecraft.block.pattern.BlockPattern;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.FoodComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.voxelindustry.voidheart.VoidHeart;

import java.util.List;

import static net.voxelindustry.voidheart.VoidHeart.MODID;
import static net.voxelindustry.voidheart.common.world.VoidPocketState.getVoidPocketState;

public class VoidHeartPieceItem extends Item
{
    public VoidHeartPieceItem()
    {
        super(new Settings()
                .group(VoidHeart.ITEMGROUP)
                .rarity(Rarity.COMMON)
                .maxCount(1)
                .food(new FoodComponent.Builder().alwaysEdible().build()));
    }

    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context)
    {
        super.appendTooltip(stack, world, tooltip, context);

        CompoundTag tag = stack.getOrCreateTag();

        if (tag.contains("pocketPos"))
        {
            BlockPos blockPos = BlockPos.fromLong(tag.getLong("pocketPos"));
            tooltip.add(new TranslatableText(MODID + ".void_heart_piece.pocket.lore",
                    tag.getString("playerName")));
            tooltip.add(new TranslatableText(MODID + ".void_heart_piece.pocket.lore2",
                    "[" + blockPos.getX() + "/" + blockPos.getY() + "/" + blockPos.getZ() + "]"));
        }
        else if (tag.contains("externalPos"))
        {
            BlockPos blockPos = BlockPos.fromLong(tag.getLong("externalPos"));
            tooltip.add(new TranslatableText(MODID + ".void_heart_piece.external.lore",
                    new Identifier(tag.getString("externalDimension")).getPath()));
            tooltip.add(new TranslatableText(MODID + ".void_heart_piece.pocket.lore2",
                    "[" + blockPos.getX() + "/" + blockPos.getY() + "/" + blockPos.getZ() + "]"));
        }
        else if (tag.containsUuid("player"))
            tooltip.add(new TranslatableText(MODID + ".void_heart_piece.lore", tag.getString("playerName"), Formatting.AQUA, Formatting.RESET));
    }

    @Override
    public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user)
    {
        if (!world.isClient())
        {
            CompoundTag tag = stack.getOrCreateTag();
            if (!tag.containsUuid("player"))
                return stack;

            ServerWorld voidWorld = world.getServer().getWorld(VoidHeart.VOID_WORLD_KEY);
            FabricDimensions.teleport(user, voidWorld,
                    (entity, newWorld, direction, offsetX, offsetY) ->
                    {
                        BlockPos pos = getVoidPocketState(voidWorld).getPosForPlayer(tag.getUuid("player"));
                        return new BlockPattern.TeleportTarget(Vec3d.of(pos.up()).add(0.5, 0.5, 0.5), Vec3d.ZERO, 0);
                    });
        }
        return ItemStack.EMPTY;
    }

    @Override
    public Text getName(ItemStack stack)
    {
        CompoundTag tag = stack.getOrCreateTag();
        if (!tag.containsUuid("player"))
            return new TranslatableText(getTranslationKey(stack) + ".empty");

        return new TranslatableText(getTranslationKey(stack), tag.getString("playerName"));
    }
}
