package net.voxelindustry.voidheart.common.setup;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * The mod's own sound ids. sounds.json points them at pitched vanilla audio for now, so real recordings can replace them without touching code.
 */
public final class VoidHeartSounds
{
    public record MobSounds(SoundEvent ambient, SoundEvent hurt, SoundEvent death, SoundEvent step)
    {
    }

    public static final MobSounds VOID_SKELETON = mob("void_skeleton");
    public static final MobSounds VOID_GHOUL    = mob("void_ghoul");
    public static final MobSounds WRAITH        = mob("wraith");
    public static final MobSounds CRAB          = mob("corrupted_crab");
    public static final MobSounds BARNACLE      = mob("corrupted_barnacle");
    public static final MobSounds FALLEN_NOVICE = mob("fallen_novice");
    public static final MobSounds FALLEN_ADEPT  = mob("fallen_adept");
    public static final MobSounds FALLEN_ELDER  = mob("fallen_elder");
    public static final MobSounds EYE_MAW       = mob("eye_maw");
    public static final MobSounds LARGE_EYE_MAW = mob("large_eye_maw");
    public static final MobSounds ARCANE_EYE    = mob("arcane_eye");

    public static final SoundEvent GHOUL_LUNGE      = register("entity.void_ghoul.lunge");
    public static final SoundEvent WRAITH_WHISPER   = register("entity.wraith.whisper");
    public static final SoundEvent WRAITH_EMERGE    = register("entity.wraith.emerge");
    public static final SoundEvent BARNACLE_INFECT  = register("entity.corrupted_barnacle.infect");
    public static final SoundEvent BARNACLE_STIR    = register("entity.corrupted_barnacle.stir");
    public static final SoundEvent BARNACLE_BURST   = register("entity.corrupted_barnacle.burst");
    public static final SoundEvent NEST_RELEASE     = register("block.barnacle_nest.release");
    public static final SoundEvent ELDER_CAST       = register("entity.fallen_elder.cast");
    public static final SoundEvent ELDER_RAISE      = register("entity.fallen_elder.raise");
    public static final SoundEvent ORB_HIT          = register("entity.void_orb.hit");
    public static final SoundEvent ORB_DEFLECT      = register("entity.void_orb.deflect");
    public static final SoundEvent EYE_MAW_OPEN     = register("entity.eye_maw.open");
    public static final SoundEvent EYE_MAW_LUNGE    = register("entity.eye_maw.lunge");
    public static final SoundEvent EYE_MAW_BITE     = register("entity.eye_maw.bite");
    public static final SoundEvent LARGE_EYE_MAW_OPEN = register("entity.large_eye_maw.open");
    public static final SoundEvent LARGE_EYE_MAW_SPIT = register("entity.large_eye_maw.spit");
    public static final SoundEvent ARCANE_EYE_CHARGE = register("entity.arcane_eye.charge");
    public static final SoundEvent ARCANE_EYE_FIRE  = register("entity.arcane_eye.fire");
    public static final SoundEvent ARCANE_BOLT_HIT  = register("entity.arcane_bolt.hit");

    private VoidHeartSounds()
    {
    }

    public static void register()
    {
        // Touching the class is enough to register the sounds
    }

    private static MobSounds mob(String name)
    {
        var prefix = "entity." + name + ".";
        return new MobSounds(register(prefix + "ambient"), register(prefix + "hurt"), register(prefix + "death"), register(prefix + "step"));
    }

    private static SoundEvent register(String name)
    {
        var id = Identifier.fromNamespaceAndPath(MODID, name);
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
    }
}
