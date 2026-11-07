package net.voxelindustry.voidheart.common.content.salt;

import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.voxelindustry.voidheart.common.item.LoreItem;

/**
 * Used on the top of a block it lays a 3x3 of salt. Used on salt it grows that patch by a ring. Each salt covers nine blocks.
 */
public class SaltItem extends LoreItem
{
    public SaltItem(Properties properties, String... loreKeys)
    {
        super(properties, loreKeys);
    }

    @Override
    public InteractionResult useOn(UseOnContext context)
    {
        var level = context.getLevel();
        var pos = context.getClickedPos();
        var face = context.getClickedFace();
        if (face != Direction.UP && !SaltWard.isSalt(level, pos))
            return InteractionResult.PASS;
        if (!(level instanceof ServerLevel server))
            return InteractionResult.SUCCESS;

        var player = context.getPlayer();
        var stack = context.getItemInHand();
        boolean free = player != null && player.getAbilities().instabuild;
        int budget = free ? Integer.MAX_VALUE : stack.getCount() * SaltPatch.CELLS_PER_SALT;

        int laid = SaltWard.lay(server, pos, face, budget);
        if (laid == 0)
            return InteractionResult.FAIL;
        if (!free)
            stack.shrink(SaltPatch.cost(laid));
        return InteractionResult.SUCCESS;
    }
}
