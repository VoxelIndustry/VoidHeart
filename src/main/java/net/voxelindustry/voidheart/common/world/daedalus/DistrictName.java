package net.voxelindustry.voidheart.common.world.daedalus;

import java.util.List;

/** A district's generated name, as translation keys so that word lists stay in the language file. */
public record DistrictName(String pattern, List<String> words)
{
    private static final long NAME_SALT = 0x6E616D65L;
    private static final String PREFIX = "voidheart.daedalus.";
    private static final String PATTERN_PLAIN = PREFIX + "pattern.plain";
    private static final String PATTERN_OF = PREFIX + "pattern.of";

    public static final int ADJECTIVES = 8;
    public static final int NOUNS = 6;
    public static final int SUBJECTS = 8;

    public static DistrictName of(long worldSeed, DistrictPos pos)
    {
        long hash = DaedalusHash.mix(worldSeed, pos.x(), pos.z(), NAME_SALT);
        String theme = PREFIX + DaedalusTheme.at(pos).getSerializedName();
        String adjective = theme + ".adjective." + DaedalusHash.pick(hash, ADJECTIVES);
        String noun = theme + ".noun." + DaedalusHash.pick(hash >>> 8, NOUNS);
        String subject = theme + ".subject." + DaedalusHash.pick(hash >>> 16, SUBJECTS);

        return (hash >>> 32 & 1) == 0
               ? new DistrictName(PATTERN_PLAIN, List.of(adjective, noun))
               : new DistrictName(PATTERN_OF, List.of(noun, subject));
    }
}
