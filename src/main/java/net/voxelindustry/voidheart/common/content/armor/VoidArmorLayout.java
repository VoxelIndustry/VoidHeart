package net.voxelindustry.voidheart.common.content.armor;

/**
 * Where everything sits on the frame's screen, from the panel's top left corner. Shared by the menu, which places its slots, and the screen, which draws
 * around them.
 */
public final class VoidArmorLayout
{
    public static final int PANEL_W = 304;
    public static final int PANEL_H = 236;
    public static final int PAD     = 10;

    public static final int CELL       = 20;
    public static final int WELL_RIM   = 4;
    public static final int LABEL_Y    = 18;
    public static final int WELL_Y     = 28;
    public static final int HELM_X     = PAD;
    public static final int CORE_X     = 88;
    public static final int LIMBS_X    = 206;

    public static final int RACK_X     = 214;
    public static final int RACK_Y     = 122;
    public static final int SLOT       = 18;

    public static final int CHARGE_LABEL_Y = 102;
    public static final int CHARGE_Y       = 114;

    public static final int WHISPER_Y   = 141;
    public static final int INVENTORY_X = PAD;
    public static final int INVENTORY_Y = 153;
    public static final int HOTBAR_GAP  = 4;

    public static final int TABLET_X = 182;
    public static final int TABLET_Y = 152;
    public static final int TABLET_W = PANEL_W - PAD - TABLET_X;
    public static final int TABLET_H = 76;

    private VoidArmorLayout()
    {
    }

    public static int wellX(ArmorGridKind kind)
    {
        return switch (kind)
        {
            case HELM -> HELM_X;
            case CORE -> CORE_X;
            case LIMBS -> LIMBS_X;
        };
    }

    public static int wellW(ArmorGridKind kind)
    {
        return kind.width() * CELL + 2 * WELL_RIM;
    }

    public static int wellH(ArmorGridKind kind)
    {
        return kind.height() * CELL + 2 * WELL_RIM;
    }

    public static int gridX(ArmorGridKind kind)
    {
        return wellX(kind) + WELL_RIM;
    }

    public static int gridY(ArmorGridKind kind)
    {
        return WELL_Y + WELL_RIM;
    }

    /**
     * Where the apparatus socket's slot draws its item: centred on the core's middle cell.
     */
    public static int socketX()
    {
        return gridX(ArmorGridKind.CORE) + ArmorGridKind.CORE.socketX() * CELL + (CELL - 16) / 2;
    }

    public static int socketY()
    {
        return gridY(ArmorGridKind.CORE) + ArmorGridKind.CORE.socketY() * CELL + (CELL - 16) / 2;
    }
}
