package net.voxelindustry.voidheart.common.content.research;

import lombok.Getter;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import java.util.Optional;

/**
 * What the client shows of a repository, eased from what the server tells it about once a second.
 * A finished research stays up for a moment with a full ring, whether the server's update or its event arrives first.
 */
public class KnowledgeRepositoryDisplay
{
    private static final float FADE_STEP    = 0.1F;
    private static final float EASE         = 0.12F;
    private static final float FLARE_STEP   = 1 / 15F;
    private static final int   LINGER_TICKS = 40;

    public static final class Track
    {
        private float previous;
        private float current;

        public float at(float partialTicks)
        {
            return Mth.lerp(partialTicks, previous, current);
        }

        private void fade(float target, float step)
        {
            previous = current;
            current = current < target ? Math.min(target, current + step) : Math.max(target, current - step);
        }

        private void ease(float target)
        {
            previous = current;
            current += (target - current) * EASE;
        }

        private void set(float value)
        {
            previous = value;
            current = value;
        }
    }

    @Getter
    private final Track hud      = new Track();
    @Getter
    private final Track ring     = new Track();
    @Getter
    private final Track progress = new Track();
    @Getter
    private final Track fill     = new Track();
    @Getter
    private final Track flare    = new Track();

    @Getter
    private Optional<Identifier> icon = Optional.empty();
    private int linger;

    public void tick(KnowledgeRepositoryStatus status, Optional<Identifier> running, float researched, float filled)
    {
        hud.fade(status == KnowledgeRepositoryStatus.DORMANT ? 0 : 1, FADE_STEP);
        fill.ease(filled);
        flare.fade(0, FLARE_STEP);

        if (linger > 0)
        {
            linger--;
            ring.fade(1, FADE_STEP);
            progress.ease(1);
        }
        else if (running.isPresent())
        {
            if (icon.isEmpty())
                progress.set(researched);
            icon = running;
            ring.fade(1, FADE_STEP);
            progress.ease(researched);
        }
        else
        {
            ring.fade(0, FADE_STEP);
            if (ring.current == 0)
                icon = Optional.empty();
        }
    }

    public void finish()
    {
        linger = LINGER_TICKS;
        flare.set(1);
    }
}
