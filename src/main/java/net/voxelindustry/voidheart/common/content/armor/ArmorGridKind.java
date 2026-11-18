package net.voxelindustry.voidheart.common.content.armor;

import java.util.Locale;

/**
 * The frame's three grids. The core's middle cell is the apparatus socket, which no module covers.
 */
public enum ArmorGridKind
{
    HELM(3, 3, false),
    CORE(5, 5, true),
    LIMBS(4, 4, false);

    private final int     width;
    private final int     height;
    private final boolean socket;

    ArmorGridKind(int width, int height, boolean socket)
    {
        this.width = width;
        this.height = height;
        this.socket = socket;
    }

    public int width()
    {
        return width;
    }

    public int height()
    {
        return height;
    }

    public boolean hasSocket()
    {
        return socket;
    }

    public int socketX()
    {
        return width / 2;
    }

    public int socketY()
    {
        return height / 2;
    }

    public boolean isSocket(int x, int y)
    {
        return socket && x == socketX() && y == socketY();
    }

    public boolean contains(int x, int y)
    {
        return x >= 0 && y >= 0 && x < width && y < height;
    }

    public String key()
    {
        return name().toLowerCase(Locale.ROOT);
    }
}
