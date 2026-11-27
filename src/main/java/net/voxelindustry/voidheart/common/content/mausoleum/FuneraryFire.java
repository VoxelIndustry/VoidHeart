package net.voxelindustry.voidheart.common.content.mausoleum;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * The green fire a Maleficent Wisp leaves on whoever it rams. It burns as any fire does, and is put out as any fire is; only its flames are drawn
 * green, for as long as the burning it lit would last.
 */
public final class FuneraryFire
{
    /** The game time until which the fire on an entity is the wisp's. Every client tracking it is told, so they all draw it green. */
    public static final AttachmentType<Long> UNTIL = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(MODID, "funerary_fire"),
            builder -> builder.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.all()));

    private FuneraryFire()
    {
    }

    public static void register()
    {
        // Touching the class is enough to register the attachment
    }

    public static void ignite(Entity entity, int ticks)
    {
        if (entity.fireImmune())
            return;
        entity.igniteForTicks(ticks);
        entity.setAttached(UNTIL, entity.level().getGameTime() + entity.getRemainingFireTicks());
    }

    /** @return whether the fire on an entity is the wisp's, so its flames are drawn green */
    public static boolean burnsGreen(Entity entity)
    {
        if (!entity.isOnFire())
            return false;
        var until = entity.getAttached(UNTIL);
        return until != null && entity.level().getGameTime() < until;
    }
}
