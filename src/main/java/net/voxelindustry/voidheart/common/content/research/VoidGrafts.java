package net.voxelindustry.voidheart.common.content.research;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.common.world.daedalus.VoidDaedalus;
import net.voxelindustry.voidheart.common.world.dungeon.VoidDungeons;

import java.util.UUID;

/**
 * What each graft eaten does to the body: a flaw away from the void, and a gift inside it. The Vein is the second step of attunement, the Marrow the third.
 */
public final class VoidGrafts
{
    public static final int VEIN   = 2;
    public static final int MARROW = 3;
    /** The second heart, earned through the Twin Ventricle's rite rather than eaten. */
    public static final int SECOND_HEART = 4;

    private static final float VEIN_HUNGER_OUTSIDE  = 1.5F;
    private static final float VEIN_HUNGER_INSIDE   = 0.5F;
    private static final float MARROW_HEAL_OUTSIDE  = 0.5F;
    private static final float MARROW_HEAL_INSIDE   = 1.5F;

    private VoidGrafts()
    {
    }

    /** How fast hunger drains. */
    public static float hungerFactor(int attunement, boolean inVoid)
    {
        if (attunement < VEIN)
            return 1;
        return inVoid ? VEIN_HUNGER_INSIDE : VEIN_HUNGER_OUTSIDE;
    }

    /** How much natural regeneration heals. */
    public static float healFactor(int attunement, boolean inVoid)
    {
        if (attunement < MARROW)
            return 1;
        return inVoid ? MARROW_HEAL_INSIDE : MARROW_HEAL_OUTSIDE;
    }

    /** The pockets, the dungeons and the Daedalus. */
    public static boolean isVoid(ResourceKey<Level> dimension)
    {
        return dimension.equals(VoidHeart.VOID_WORLD_KEY) || dimension.equals(VoidDungeons.WORLD_KEY) || dimension.equals(VoidDaedalus.WORLD_KEY);
    }

    /** Read without settling, since this is asked many times a second. */
    public static int attunementOf(ServerPlayer player)
    {
        return attunementOf(player.level().getServer(), player.getUUID());
    }

    /** As {@link #attunementOf(ServerPlayer)}, also for players who are away. */
    public static int attunementOf(MinecraftServer server, UUID player)
    {
        return KnowledgeState.get(server).of(player).getAttunement();
    }
}
