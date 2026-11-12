package net.voxelindustry.voidheart.common.world.underway;

/** Draws on a canvas in a frame's own directions. */
record UnderwayBrush(UnderwayCanvas canvas, UnderwayFrame frame)
{
    UnderwayMaterial get(int along, int y, int across)
    {
        return canvas.get(frame.x(along, across), y, frame.z(along, across));
    }

    void set(int along, int y, int across, UnderwayMaterial material)
    {
        canvas.set(frame.x(along, across), y, frame.z(along, across), material);
    }

    void setInAir(int along, int y, int across, UnderwayMaterial material)
    {
        if (get(along, y, across) == UnderwayMaterial.AIR)
            set(along, y, across, material);
    }

    void fill(int a0, int y0, int c0, int a1, int y1, int c1, UnderwayMaterial material)
    {
        replace(a0, y0, c0, a1, y1, c1, null, material);
    }

    void replace(int a0, int y0, int c0, int a1, int y1, int c1, UnderwayMaterial from, UnderwayMaterial to)
    {
        int x0 = frame.x(a0, c0);
        int x1 = frame.x(a1, c1);
        int z0 = frame.z(a0, c0);
        int z1 = frame.z(a1, c1);
        canvas.replace(Math.min(x0, x1), y0, Math.min(z0, z1), Math.max(x0, x1), y1, Math.max(z0, z1), from, to);
    }

    /** Hollows a room and lines the rock around it with shell. Two rooms side by side share no wall. */
    void room(int a0, int c0, int a1, int c1, int floorY, int height, UnderwayMaterial floor)
    {
        replace(a0 - 1, floorY, c0 - 1, a1 + 1, floorY + height + 1, c1 + 1, UnderwayMaterial.ROCK, UnderwayMaterial.SHELL);
        fill(a0, floorY, c0, a1, floorY, c1, floor);
        fill(a0, floorY + 1, c0, a1, floorY + height, c1, UnderwayMaterial.AIR);
    }

    /** Cuts a way through walls, at the height of the floors it joins. */
    void opening(int a0, int c0, int a1, int c1, int floorY, int height)
    {
        replace(a0 - 1, floorY, c0 - 1, a1 + 1, floorY + height + 1, c1 + 1, UnderwayMaterial.ROCK, UnderwayMaterial.SHELL);
        replace(a0, floorY, c0, a1, floorY, c1, UnderwayMaterial.SHELL, UnderwayMaterial.FLOOR);
        fill(a0, floorY + 1, c0, a1, floorY + height, c1, UnderwayMaterial.AIR);
    }

    UnderwayMaterial stairs(int climbAlong, int climbAcross)
    {
        return UnderwayMaterial.stairs(frame.compass(climbAlong, climbAcross));
    }

    /** Striped armour whose band lies flat, along the frame or across it. */
    UnderwayMaterial hazard(boolean runsAlong)
    {
        return runsAlong == frame.alongX() ? UnderwayMaterial.HAZARD_X : UnderwayMaterial.HAZARD_Z;
    }

    UnderwayMaterial trunkRail()
    {
        return frame.alongX() ? UnderwayMaterial.TRUNK_RAIL_X : UnderwayMaterial.TRUNK_RAIL_Z;
    }

    UnderwayMaterial feederRail(boolean runsAlong)
    {
        return runsAlong == frame.alongX() ? UnderwayMaterial.FEEDER_RAIL_X : UnderwayMaterial.FEEDER_RAIL_Z;
    }
}
