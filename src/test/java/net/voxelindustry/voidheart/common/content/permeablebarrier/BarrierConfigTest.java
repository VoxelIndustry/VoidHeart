package net.voxelindustry.voidheart.common.content.permeablebarrier;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.voxelindustry.voidheart.common.content.permeablebarrier.BarrierConfig.CoOwner;
import net.voxelindustry.voidheart.common.content.permeablebarrier.BarrierConfig.Flag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class BarrierConfigTest
{
    @BeforeAll
    static void bootstrap()
    {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private final UUID owner = UUID.randomUUID();

    private final Player ownerPlayer    = player(owner);
    private final Player stranger       = player(UUID.randomUUID());
    private final Mob    zombie         = mock(Mob.class, withSettings().extraInterfaces(Enemy.class));
    private final Mob    cow            = mock(Mob.class);

    private static Player player(UUID id)
    {
        var player = mock(Player.class);
        when(player.getUUID()).thenReturn(id);
        return player;
    }

    private BarrierConfig configured(Flag... flags)
    {
        var config = BarrierConfig.ownedBy(owner).toggle(Flag.ALLIES).withAppearance(Optional.of(mock(BlockState.class)));
        for (var flag : flags)
            config = config.toggle(flag);
        return config;
    }

    @Test
    void isSolidFor_givenUnconfigured_thenShouldBeOpenForAll()
    {
        var config = BarrierConfig.ownedBy(owner);

        assertThat(config.isSolidFor(ownerPlayer, false)).isFalse();
        assertThat(config.isSolidFor(zombie, true)).isFalse();
    }

    @Test
    void isSolidFor_givenDrawbridge_thenShouldOnlyHoldAllies()
    {
        var config = configured(Flag.ALLIES);

        assertThat(config.isSolidFor(ownerPlayer, false)).isTrue();
        assertThat(config.isSolidFor(stranger, false)).isFalse();
        assertThat(config.isSolidFor(zombie, false)).isFalse();
    }

    @Test
    void isSolidFor_givenCoOwner_thenShouldCountAsAlly()
    {
        var config = configured(Flag.ALLIES).toggleCoOwner(new CoOwner(stranger.getUUID(), "Stranger"));

        assertThat(config.isSolidFor(stranger, false)).isTrue();
        assertThat(config.toggleCoOwner(new CoOwner(stranger.getUUID(), "Stranger")).isSolidFor(stranger, false)).isFalse();
    }

    @Test
    void isSolidFor_givenLeverWallTrap_thenShouldOnlyHoldMobsWhilePowered()
    {
        var config = configured(Flag.PEACEFUL_MOBS, Flag.HOSTILE_MOBS, Flag.REDSTONE);

        assertThat(config.isSolidFor(zombie, false)).isFalse();
        assertThat(config.isSolidFor(ownerPlayer, false)).isFalse();

        assertThat(config.isSolidFor(zombie, true)).isTrue();
        assertThat(config.isSolidFor(cow, true)).isTrue();
        assertThat(config.isSolidFor(ownerPlayer, true)).isFalse();
    }

    @Test
    void isSolidFor_givenOubliette_thenShouldOnlyOpenForHostilesWhilePowered()
    {
        var config = configured(Flag.HOSTILE_MOBS, Flag.REDSTONE, Flag.INVERTED);

        assertThat(config.isSolidFor(zombie, false)).isTrue();
        assertThat(config.isSolidFor(stranger, false)).isTrue();

        assertThat(config.isSolidFor(zombie, true)).isFalse();
        assertThat(config.isSolidFor(cow, true)).isTrue();
        assertThat(config.isSolidFor(stranger, true)).isTrue();
    }

    @Test
    void isSolidFor_givenNoEntity_thenShouldFollowInactiveState()
    {
        assertThat(configured(Flag.HOSTILE_MOBS).isSolidFor(null, true)).isFalse();
        assertThat(configured(Flag.HOSTILE_MOBS, Flag.INVERTED).isSolidFor(null, true)).isTrue();
    }
}
