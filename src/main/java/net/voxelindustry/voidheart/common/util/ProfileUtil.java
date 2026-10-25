package net.voxelindustry.voidheart.common.util;

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
        ResolvableProfile.createUnresolved(playerID)
                .resolveProfile(server.services().profileResolver())
                .thenAccept(profile -> server.execute(() -> onResolved.accept(ResolvableProfile.createResolved(profile))));
    }

    public static String getName(ResolvableProfile profile)
    {
        return profile.name().orElse("");
    }
}
