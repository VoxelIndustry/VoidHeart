package net.voxelindustry.voidheart.common.content.hoard;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.voxelindustry.voidheart.common.world.VoidPocketState;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Folds open onto their placer's pocket, so only a player who has one may place them.
 */
final class HoardPlacing
{
    private HoardPlacing()
    {
    }

    /**
     * @return whether the placer has a pocket; the client lets it through and the server decides
     */
    static boolean mayPlace(BlockPlaceContext context)
    {
        var player = context.getPlayer();
        if (player == null || !(context.getLevel() instanceof ServerLevel level))
            return true;
        if (VoidPocketState.getVoidPocketState(level).hasPocket(player.getUUID()))
            return true;
        player.sendOverlayMessage(Component.translatable(MODID + ".fold.no_pocket"));
        return false;
    }
}
