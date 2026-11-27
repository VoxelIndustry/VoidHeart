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
    public static final MobSounds STALKING_WRAITH   = mob("stalking_wraith");
    public static final MobSounds FALLEN_BISHOP     = mob("fallen_bishop");
    public static final MobSounds MALIGNANT_BOULDER = mob("malignant_boulder");
    public static final MobSounds LEGIONARY         = mob("legionary");
    public static final MobSounds CENTURION         = mob("centurion");
    public static final MobSounds LESSER_SHOGGOTH   = mob("lesser_shoggoth");
    public static final MobSounds VILE_FLESH_MASS   = mob("vile_flesh_mass");
    public static final MobSounds DULLAHAN          = mob("dullahan");
    public static final MobSounds MALEFICENT_WISP   = mob("maleficent_wisp");
    public static final SoundEvent DULLAHAN_WINDUP  = register("entity.dullahan.windup");
    public static final SoundEvent DULLAHAN_SWEEP   = register("entity.dullahan.sweep");
    public static final SoundEvent DULLAHAN_RELEASE = register("entity.dullahan.release");
    public static final SoundEvent DULLAHAN_GLANCE  = register("entity.dullahan.glance");
    public static final SoundEvent WISP_WINDUP      = register("entity.maleficent_wisp.windup");
    public static final SoundEvent WISP_CHARGE      = register("entity.maleficent_wisp.charge");
    public static final SoundEvent SARCOPHAGUS_CRACK = register("block.sarcophagus.crack");
    public static final MobSounds KERNACIAN_DRAGOON = mob("kernacian_dragoon");
    public static final MobSounds TREANT            = mob("treant");
    public static final MobSounds WRAITH_SCARAB     = mob("wraith_scarab");
    public static final MobSounds WRAITH_BEAST      = mob("wraith_beast");

    public static final SoundEvent GHOUL_LUNGE      = register("entity.void_ghoul.lunge");
    public static final SoundEvent WRAITH_WHISPER   = register("entity.wraith.whisper");
    public static final SoundEvent WRAITH_EMERGE    = register("entity.wraith.emerge");
    public static final SoundEvent WRAITH_SCARAB_HOP  = register("entity.wraith_scarab.hop");
    public static final SoundEvent WRAITH_SCARAB_BITE = register("entity.wraith_scarab.bite");
    public static final SoundEvent WRAITH_BEAST_MANIFEST = register("entity.wraith_beast.manifest");
    public static final SoundEvent WRAITH_BEAST_REAR     = register("entity.wraith_beast.rear");
    public static final SoundEvent WRAITH_BEAST_STRIKE   = register("entity.wraith_beast.strike");
    public static final SoundEvent WRAITH_BEAST_FADE     = register("entity.wraith_beast.fade");
    public static final SoundEvent WRAITH_BEAST_DODGE    = register("entity.wraith_beast.dodge");
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
    public static final SoundEvent STALKER_SMILE    = register("entity.stalking_wraith.smile");
    public static final SoundEvent STALKER_RUSH     = register("entity.stalking_wraith.rush");
    public static final SoundEvent STALKER_STRIKE   = register("entity.stalking_wraith.strike");
    public static final SoundEvent STALKER_SLURP    = register("entity.stalking_wraith.slurp");
    public static final SoundEvent BISHOP_CHARGE    = register("entity.fallen_bishop.charge");
    public static final SoundEvent BISHOP_LANCE     = register("entity.fallen_bishop.lance");
    public static final SoundEvent BISHOP_CHANT     = register("entity.fallen_bishop.chant");
    public static final SoundEvent BOULDER_RAISE    = register("entity.malignant_boulder.raise");
    public static final SoundEvent BOULDER_SLAM     = register("entity.malignant_boulder.slam");
    public static final SoundEvent BOULDER_WARD     = register("entity.malignant_boulder.ward");
    public static final SoundEvent BOULDER_BREAK    = register("entity.malignant_boulder.break");
    public static final SoundEvent LEGIONARY_FRENZY = register("entity.legionary.frenzy");
    public static final SoundEvent LEGIONARY_CHOP   = register("entity.legionary.chop");
    public static final SoundEvent CENTURION_BASH   = register("entity.centurion.bash");
    public static final SoundEvent CENTURION_SWEEP  = register("entity.centurion.sweep");
    public static final SoundEvent DRAGOON_OPEN     = register("entity.kernacian_dragoon.open");
    public static final SoundEvent DRAGOON_CHARGE   = register("entity.kernacian_dragoon.charge");
    public static final SoundEvent DRAGOON_BEAM     = register("entity.kernacian_dragoon.beam");
    public static final SoundEvent SHOGGOTH_LASH    = register("entity.lesser_shoggoth.lash");
    public static final SoundEvent SHOGGOTH_SPLIT   = register("entity.lesser_shoggoth.split");
    public static final SoundEvent MASS_SWEEP       = register("entity.vile_flesh_mass.sweep");
    public static final SoundEvent MASS_GRAB        = register("entity.vile_flesh_mass.grab");
    public static final SoundEvent MASS_SPIT        = register("entity.vile_flesh_mass.spit");
    public static final SoundEvent MASS_BITE        = register("entity.vile_flesh_mass.bite");
    public static final SoundEvent MASS_FEED        = register("entity.vile_flesh_mass.feed");
    public static final SoundEvent TENTACLE_WARN    = register("entity.flesh_tentacle.warn");
    public static final SoundEvent TENTACLE_ERUPT   = register("entity.flesh_tentacle.erupt");
    public static final SoundEvent TENTACLE_WRAP    = register("entity.flesh_tentacle.wrap");
    public static final SoundEvent TENTACLE_HURT    = register("entity.flesh_tentacle.hurt");
    public static final SoundEvent TENTACLE_SEVER   = register("entity.flesh_tentacle.sever");
    public static final SoundEvent BILE_SPLASH      = register("entity.bile_glob.splash");
    public static final SoundEvent DRAGOON_KICK     = register("entity.kernacian_dragoon.kick");
    public static final SoundEvent DRAGOON_ADAPT    = register("entity.kernacian_dragoon.adapt");
    public static final SoundEvent TREANT_EMERGE    = register("entity.treant.emerge");
    public static final SoundEvent TREANT_TEAR      = register("entity.treant.tear");
    public static final SoundEvent TREANT_WINDUP    = register("entity.treant.windup");
    public static final SoundEvent TREANT_SWEEP     = register("entity.treant.sweep");

    public static final SoundEvent ARMILLARY_FORM         = register("block.armillary.form");
    public static final SoundEvent ARMILLARY_BREAK        = register("block.armillary.break");
    public static final SoundEvent ARMILLARY_FOUND        = register("block.armillary.found");
    public static final SoundEvent ARMILLARY_REACHED      = register("block.armillary.reached");
    public static final SoundEvent ARMILLARY_OPEN         = register("block.armillary.open");
    public static final SoundEvent ARMILLARY_CLOSE        = register("block.armillary.close");
    public static final SoundEvent CONNECTION_TRAVEL      = register("connection.travel");
    public static final SoundEvent CONNECTION_RUMBLE_FAR  = register("connection.rumble_far");
    public static final SoundEvent CONNECTION_RUMBLE_NEAR = register("connection.rumble_near");

    public static final SoundEvent SECOND_HEART_DISSOLVE = register("player.second_heart.dissolve");
    public static final SoundEvent SECOND_HEART_REFORM   = register("player.second_heart.reform");
    public static final SoundEvent SECOND_HEART_BURST    = register("player.second_heart.burst");
    public static final SoundEvent SECOND_HEART_READY    = register("player.second_heart.ready");
    public static final SoundEvent SECOND_HEART_PULSE    = register("player.second_heart.pulse");
    public static final SoundEvent VENTRICLE_OFFER       = register("block.heart_cradle.offer");
    public static final SoundEvent VENTRICLE_KINDLE      = register("block.heart_cradle.kindle");
    public static final SoundEvent VENTRICLE_BEAT        = register("block.heart_cradle.beat");
    public static final SoundEvent VENTRICLE_STOP        = register("block.heart_cradle.stop");
    public static final SoundEvent VENTRICLE_TEAR        = register("entity.ventricle_tear.open");

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
