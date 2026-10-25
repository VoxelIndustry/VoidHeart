package net.voxelindustry.voidheart.common.util;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.component.ResolvableProfile;

import java.util.UUID;
import java.util.function.Consumer;

public class ProfileUtil
{
    /**
     * Resolve the full profile of a player off-thread, then hand it back on the server thread.
     */
    public static void resolve(MinecraftServer server, UUID playerID, Consumer<ResolvableProfile> onResolved)
    {
        var player = server.getPlayerList().getPlayer(playerID);
        if (player != null)
        {
            onResolved.accept(ResolvableProfile.createResolved(player.getGameProfile()));
            return;
        }

        ResolvableProfile.createUnresolved(playerID)
                .resolveProfile(server.services().profileResolver())
                .thenAccept(profile -> server.execute(() -> onResolved.accept(ResolvableProfile.createResolved(withCachedName(server, profile)))));
    }

    /**
     * Offline-mode players have no Mojang profile, their name is only known by the server user cache.
     */
    private static GameProfile withCachedName(MinecraftServer server, GameProfile profile)
    {
        if (profile.name() != null && !profile.name().isEmpty())
            return profile;

        return server.services().nameToIdCache().get(profile.id())
                .map(cached -> new GameProfile(profile.id(), cached.name(), profile.properties()))
                .orElse(profile);
    }

    public static String getName(ResolvableProfile profile)
    {
        return profile.name().orElse("");
    }
}
