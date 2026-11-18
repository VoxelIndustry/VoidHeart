package net.voxelindustry.voidheart.common.content.blight;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * The crossbow blight's links, kept on each wielder: its bolts stitch the mobs they strike together, and damage any of them takes runs out to the rest
 * at a share, never passed on again. A link comes apart once its time is up, or once two of its mobs stand too far apart.
 */
public final class EclipseLinks
{
    public static final AttachmentType<EclipseLink> LINK = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(MODID, "eclipse_link"),
            builder -> builder.syncWith(EclipseLink.STREAM_CODEC, AttachmentSyncPredicate.all()));

    /** Damage shared along a link, which is never shared again. */
    public static final ResourceKey<DamageType> SHARE = ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath(MODID, "eclipse_share"));

    private EclipseLinks()
    {
    }

    public static void register()
    {
        ServerLivingEntityEvents.AFTER_DAMAGE.register(EclipseLinks::afterDamage);
        ServerTickEvents.END_SERVER_TICK.register(EclipseLinks::tick);
    }

    /** A wielder's bolt struck a mob. */
    public static void struck(ServerPlayer wielder, LivingEntity target)
    {
        if (!target.isAlive() || ThrownBlightSpear.isSpared(target, wielder))
            return;
        var link = wielder.getAttachedOrElse(LINK, EclipseLink.NONE);
        wielder.setAttached(LINK, link.struck(target.getId(), wielder.level().getGameTime()));
    }

    private static void afterDamage(LivingEntity hurt, DamageSource source, float baseTaken, float taken, boolean blocked)
    {
        if (taken <= 0 || source.is(SHARE) || !(hurt.level() instanceof ServerLevel level))
            return;
        long now = level.getGameTime();
        for (var wielder : level.players())
        {
            var link = wielder.getAttached(LINK);
            if (link == null || !link.contains(hurt.getId()) || !link.holds(now))
                continue;
            float share = EclipseLink.shareOf(taken);
            for (int id : link.othersThan(hurt.getId()))
                if (level.getEntity(id) instanceof LivingEntity other && other.isAlive())
                    other.hurtServer(level, level.damageSources().source(SHARE, wielder), share);
            // Read again: a member may have died of its share and been let go meanwhile
            wielder.setAttached(LINK, wielder.getAttachedOrElse(LINK, link).shared(hurt.getId(), now));
        }
    }

    /** Lets go of members gone from the world, and comes apart once its time is up or it is stretched too far. */
    private static void tick(MinecraftServer server)
    {
        for (var wielder : server.getPlayerList().getPlayers())
        {
            var link = wielder.getAttached(LINK);
            if (link == null)
                continue;
            var level = wielder.level();
            long now = level.getGameTime();
            var kept = link;
            var places = new ArrayList<Vec3>();
            for (int id : link.members())
            {
                if (level.getEntity(id) instanceof LivingEntity member && member.isAlive())
                    places.add(member.position());
                else
                    kept = kept.without(id);
            }
            if (kept.isOver(now) || EclipseLink.isStretched(places))
                wielder.removeAttached(LINK);
            else if (kept != link)
                wielder.setAttached(LINK, kept);
        }
    }
}
