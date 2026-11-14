package net.voxelindustry.voidheart.common.world.underway;

/** Draws a fort over the trunks and galleries already drawn through its district. */
final class FortPainter
{
    private FortPainter()
    {
    }

    static void paint(UnderwayFort fort, UnderwayCanvas canvas)
    {
        var brush = new UnderwayBrush(canvas, fort.frame());
        for (var room : fort.rooms())
            brush.room(room.a0(), room.c0(), room.a1(), room.c1(), room.floorY(), room.height(), floorOf(room.kind()));
        for (var opening : fort.openings())
            brush.opening(opening.a0(), opening.c0(), opening.a1(), opening.c1(), opening.floorY(), opening.height());
        for (var shaft : fort.shafts())
            StairShaft.paint(brush, shaft);
        for (var stroke : fort.strokes())
            brush.replace(stroke.a0(), stroke.y0(), stroke.c0(), stroke.a1(), stroke.y1(), stroke.c1(), stroke.over(), stroke.material());
        var frame = fort.frame();
        for (var spot : fort.props())
            canvas.setProp(frame.x(spot.along(), spot.across()), spot.y(), frame.z(spot.along(), spot.across()), spot.prop(), frame.compass(spot.faceAlong(), spot.faceAcross()));
        if (fort.temple() != null)
            mosaic(fort.temple(), frame, canvas);
    }

    /** Sets a temple's mosaic into the wall behind its dais, its picture the right way round whichever way the fort was turned, and leaves the bed bare where tiles fell. */
    static void mosaic(UnderwayTemple temple, UnderwayFrame frame, UnderwayCanvas canvas)
    {
        for (int w = UnderwayTemple.FIRST_COLUMN; w < UnderwayTemple.FIRST_COLUMN + UnderwayTemple.COLUMNS; w++)
        {
            int along = temple.along(w, UnderwayTemple.DEPTH);
            int across = temple.across(w, UnderwayTemple.DEPTH);
            int x = frame.x(along, across);
            int z = frame.z(along, across);
            int column = temple.imageColumn(frame, w);
            for (int row = 0; row < UnderwayTemple.ROWS; row++)
            {
                int y = temple.rowY(row);
                if (canvas.get(x, y, z) != UnderwayMaterial.SHELL)
                    continue;
                var wear = temple.wear(w, row);
                if (wear == UnderwayTemple.Wear.FALLEN)
                    canvas.set(x, y, z, UnderwayMaterial.MOSAIC_BED);
                else
                    canvas.setMosaic(x, y, z, row * UnderwayTemple.COLUMNS + column, wear == UnderwayTemple.Wear.CHIPPED);
            }
        }
    }

    private static UnderwayMaterial floorOf(UnderwayRoomKind kind)
    {
        return switch (kind)
        {
            case PLATFORM -> UnderwayMaterial.PLATFORM;
            case CROSSING -> UnderwayMaterial.BALLAST;
            default -> UnderwayMaterial.FLOOR;
        };
    }
}
