package net.voxelindustry.voidheart.client.scene;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.client.scene.SceneEmitter.Crumbs;
import net.voxelindustry.voidheart.client.scene.SceneEmitter.Drift;
import net.voxelindustry.voidheart.client.scene.SceneEmitter.Frames;
import net.voxelindustry.voidheart.client.scene.SceneEmitter.Look;
import net.voxelindustry.voidheart.client.scene.SceneEmitter.Spiral;
import net.voxelindustry.voidheart.client.scene.SceneEmitter.Stream;

import java.util.List;
import java.util.stream.IntStream;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * The effects scenes are written with, each made to look like what the game shows when the same thing happens in a world: the same pictures, tints,
 * sizes and paths. They are thicker than the game's own where a few specks would be lost on a stage a few hundred pixels wide.
 */
public final class SceneFx
{
    private static final int WHITE = 0xFFFFFF;

    private static final List<Identifier> FLAME = List.of(Identifier.withDefaultNamespace("flame"));
    // The game's eight round specks, largest last: smoke and ink play them down, portal dust keeps one
    private static final List<Identifier> SPECKS = IntStream.range(0, 8).mapToObj(index -> Identifier.withDefaultNamespace("generic_" + index)).toList();
    private static final List<Identifier> PUFF   = SPECKS.reversed();
    private static final List<Identifier> VOID   = IntStream.range(0, 4).mapToObj(index -> Identifier.fromNamespaceAndPath(MODID, "altar_void" + index)).toList();

    private static final float FLAME_SIZE  = 0.11F;
    private static final float FLAME_RATE  = 0.35F;
    private static final float FLAME_LIFE  = 22;
    private static final double FLAME_RISE = 0.012;

    private static final int    SMOKE_RGB  = 0x2B2B2B;
    private static final float  SMOKE_SIZE = 0.16F;
    private static final float  SMOKE_RATE = 0.6F;
    private static final float  SMOKE_LIFE = 26;
    private static final double SMOKE_RISE = 0.03;
    private static final double SMOKE_LIFT = 0.002;

    private static final int   PUFF_COUNT = 12;
    private static final float PUFF_SIZE  = 0.3F;
    private static final float PUFF_LIFE  = 30;

    private static final float CRUMB_SIZE = 0.075F;
    private static final float CRUMB_RATE = 1.5F;
    private static final float CRUMB_LIFE = 30;
    private static final float CRUMB_BOW  = 0.2F;

    // As BeamPathParticle winds what the forge's beam carries
    private static final float BEAM_RATE   = 2;
    private static final float BEAM_RADIUS = 0.25F;
    private static final float BEAM_TWIST  = 5;
    private static final float BEAM_TICKS  = 2.5F;

    // The green of the altar's void, as AltarVoidFillingParticle tints it
    private static final int   VOID_RGB       = 0x366654;
    private static final float VOID_SIZE      = 0.05F;
    private static final float VOID_REACH     = 1.2F;
    private static final float VOID_LIFE      = 24;
    private static final float VOID_GATHER_RATE = 1.5F;
    private static final float VOID_FALL_LIFE = 40;
    private static final int   VOID_BURST     = 16;
    private static final double VOID_THROW    = 0.09;

    // As SquidInkParticle: blots a block wide that thin out over the second half of their lives. The game's sink into the ground, which hides them here
    private static final int    INK_RGB   = 0x0B0810;
    private static final int    INK_COUNT = 12;
    private static final float  INK_SIZE  = 0.45F;
    private static final float  INK_LIFE  = 18;
    private static final double INK_RISE  = 0.004;

    private static final int   RESHAPE_RGB   = 0xC98BFF;
    private static final int   RESHAPE_COUNT = 40;
    private static final float RESHAPE_SIZE  = 0.13F;
    private static final float RESHAPE_LIFE  = 40;

    private SceneFx()
    {
    }

    /**
     * A fire burning: small flames that hardly rise.
     *
     * @param spread how far from the point they may light, to each side
     */
    public static SceneEmitter flame(Vec3 at, double spread)
    {
        return new SceneEmitter(new Look(new Frames(FLAME, false), WHITE, FLAME_SIZE, 0.5F, 1, 0, 0.2F),
                new Drift(at, new Vec3(spread, 0, spread), new Vec3(0, FLAME_RISE, 0), new Vec3(0.002, 0.004, 0.002), Vec3.ZERO),
                FLAME_RATE, 0, FLAME_LIFE, 0.4F);
    }

    /**
     * Smoke leaving a chimney, thinning as it climbs.
     */
    public static SceneEmitter smoke(Vec3 at)
    {
        return new SceneEmitter(new Look(new Frames(PUFF, true), SMOKE_RGB, SMOKE_SIZE, 1, 0.9F, 0, 0.25F),
                new Drift(at, new Vec3(0.06, 0, 0.06), new Vec3(0, SMOKE_RISE, 0), new Vec3(0.004, 0.004, 0.004), new Vec3(0, SMOKE_LIFT, 0)),
                SMOKE_RATE, 0, SMOKE_LIFE, 0.4F);
    }

    /**
     * One thick breath of smoke, as a brew lets out when it is done.
     */
    public static SceneEmitter puff(Vec3 at)
    {
        return new SceneEmitter(new Look(new Frames(PUFF, true), SMOKE_RGB, PUFF_SIZE, 1, 0.9F, 0, 0.25F),
                new Drift(at, new Vec3(0.1, 0.1, 0.1), new Vec3(0, 0.015, 0), new Vec3(0.02, 0.02, 0.02), new Vec3(0, SMOKE_LIFT, 0)),
                0, PUFF_COUNT, PUFF_LIFE, 0.4F);
    }

    /**
     * An item coming apart and streaming to where it is taken, as an offering leaves its pillar for the altar.
     */
    public static SceneEmitter crumbs(ItemStack stack, Vec3 from, Vec3 to)
    {
        return new SceneEmitter(new Look(new Crumbs(stack, null), WHITE, CRUMB_SIZE, 1, 1, 0, 0),
                new Stream(from, new Vec3(0.05, 0.05, 0.05), to, CRUMB_BOW), CRUMB_RATE, 0, CRUMB_LIFE, 0);
    }

    /**
     * What a forge's beam carries, winding along it and turning from what was laid down into what is made.
     */
    public static SceneEmitter beamCrumbs(ItemStack input, ItemStack output, Vec3 from, Vec3 to)
    {
        float life = Math.max(6, (float) from.distanceTo(to) * BEAM_TICKS);
        return new SceneEmitter(new Look(new Crumbs(input, output), WHITE, CRUMB_SIZE, 1, 1, 0, 0),
                new Spiral(from, to, BEAM_RADIUS, BEAM_TWIST), BEAM_RATE, 0, life, 0);
    }

    /**
     * The void gathering on a point from all around it, as it does over an altar that warms.
     */
    public static SceneEmitter voidGather(Vec3 center)
    {
        return new SceneEmitter(new Look(new Frames(VOID, true), VOID_RGB, VOID_SIZE, 1, 1, 0.15F, 0),
                new Stream(center, new Vec3(VOID_REACH, VOID_REACH, VOID_REACH), center, 0), VOID_GATHER_RATE, 0, VOID_LIFE, 0.3F);
    }

    /**
     * The void running down a thin thread, from an altar's void to what lies on the altar.
     */
    public static SceneEmitter voidThread(Vec3 from, Vec3 to)
    {
        return new SceneEmitter(new Look(new Frames(VOID, true), VOID_RGB, VOID_SIZE, 1, 1, 0.1F, 0.1F),
                new Stream(from, new Vec3(0.15, 0, 0.15), to, 0), 1, 0, VOID_FALL_LIFE, 0.2F);
    }

    /**
     * The void let go at once, thrown every way as an altar's rite ends.
     */
    public static SceneEmitter voidBurst(Vec3 center)
    {
        return new SceneEmitter(new Look(new Frames(VOID, true), VOID_RGB, VOID_SIZE, 1, 1, 0, 0.3F),
                new Drift(center, Vec3.ZERO, Vec3.ZERO, new Vec3(VOID_THROW, VOID_THROW, VOID_THROW), Vec3.ZERO), 0, VOID_BURST, VOID_LIFE, 0.3F);
    }

    /**
     * A blot of dark thrown out along a crack, as a fissure gives when it is torn wider.
     *
     * @param alongX how far to each side along X the blot is thrown
     * @param alongZ likewise along Z
     */
    public static SceneEmitter ink(Vec3 center, double alongX, double alongZ)
    {
        return new SceneEmitter(new Look(new Frames(PUFF, true), INK_RGB, INK_SIZE, 1, 1, 0, 0.5F),
                new Drift(center, new Vec3(alongX, 0.05, alongZ), new Vec3(0, INK_RISE, 0), new Vec3(0.012, 0.012, 0.012), Vec3.ZERO),
                0, INK_COUNT, INK_LIFE, 0.6F);
    }

    /**
     * Purple dust thrown off a structure as it takes a new shape, as an armillary's base does when it settles.
     *
     * @param box how far from the center the dust starts, along each axis
     */
    public static SceneEmitter reshape(Vec3 center, Vec3 box)
    {
        return new SceneEmitter(new Look(new Frames(SPECKS, false), RESHAPE_RGB, RESHAPE_SIZE, 0, 1, 0, 0),
                new Drift(center, box, Vec3.ZERO, new Vec3(0.03, 0.03, 0.03), Vec3.ZERO), 0, RESHAPE_COUNT, RESHAPE_LIFE, 0.3F);
    }
}
