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
