package net.voxelindustry.voidheart.common.item;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.Rarity;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;
import net.voxelindustry.voidheart.common.world.VoidPocketState;

import java.util.List;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class VoidHeartItem extends Item
{
    public VoidHeartItem()
    {
        super(new Settings()
                .group(VoidHeart.ITEMGROUP)
                .rarity(Rarity.RARE)
                .maxCount(1));
    }

    @Override
    public Text getName(ItemStack stack)
    {
        CompoundTag tag = stack.getOrCreateTag();
        if (!tag.containsUuid("player"))
            return new TranslatableText(getTranslationKey(stack) + ".empty");

        return new TranslatableText(getTranslationKey(stack), tag.getString("playerName"));
    }

    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context)
    {
        super.appendTooltip(stack, world, tooltip, context);

        tooltip.add(new TranslatableText(MODID + ".void_heart.lore"));
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand)
    {
        ItemStack itemStack = user.getStackInHand(hand);
        CompoundTag tag = itemStack.getOrCreateTag();

        if (!tag.containsUuid("player"))
            return TypedActionResult.pass(itemStack);

        world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ENTITY_ENDER_PEARL_THROW, SoundCategory.NEUTRAL, 0.5F, 0.4F / (RANDOM.nextFloat() * 0.4F + 0.8F));
        user.getItemCooldownManager().set(this, 20);

        if (!world.isClient())
        {
            // TODO
            // Throw heart entity and make portal effect

            ServerWorld voidWorld = world.getServer().getWorld(VoidHeart.VOID_WORLD_KEY);
            VoidPocketState.getVoidPocketState(voidWorld).createPocket(voidWorld, tag.getUuid("player"));

            ItemStack pieceStack = new ItemStack(VoidHeartItems.VOID_PEARL);
            pieceStack.getOrCreateTag().putUuid("player", tag.getUuid("player"));
            pieceStack.getOrCreateTag().putString("playerName", tag.getString("playerName"));

            ItemStack secondPiece = pieceStack.copy();
            ItemStack thirdPiece = pieceStack.copy();
            ItemScatterer.spawn(world, user.getBlockPos().getX(), user.getBlockPos().getY(), user.getBlockPos().getZ(), pieceStack);
            ItemScatterer.spawn(world, user.getBlockPos().getX(), user.getBlockPos().getY(), user.getBlockPos().getZ(), secondPiece);
            ItemScatterer.spawn(world, user.getBlockPos().getX(), user.getBlockPos().getY(), user.getBlockPos().getZ(), thirdPiece);
        }

        if (!user.abilities.creativeMode)
            itemStack.decrement(1);

        return TypedActionResult.method_29237(itemStack, world.isClient());
    }
}
