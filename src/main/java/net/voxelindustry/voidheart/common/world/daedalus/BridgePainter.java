package net.voxelindustry.voidheart.common.world.daedalus;

import static net.voxelindustry.voidheart.common.world.daedalus.DaedalusMaterial.*;

/**
 * Draws one bridge across its chasm, in coordinates along the span and across its lane. A lane's centre is the same local 3 as the corridors it joins.
 */
final class BridgePainter
{
    private static final int LANE_CENTRE = 3;
    private static final int SPAN = DistrictPos.CHASM;
    private static final int RIB_SPACING = 8;
    private static final int RIB_OFFSET = 4;
    private static final int COVERED_HEIGHT = 4;
    private static final int ARCADE_HEIGHT = 5;
    private static final int PIER_HALF_WIDTH = 3;
    private static final int ARCH_REACH = 12;
    private static final int ARCH_CROWN = 6;
    private static final int ARCH_CURVE = 5;
    private static final int LAMP_SPACING = 8;

    private final DaedalusBridge bridge;
    private final DaedalusCanvas canvas;
    private final int low;
    private final int high;
    private final DaedalusMaterial railAlong;

    private BridgePainter(DaedalusBridge bridge, DaedalusCanvas canvas)
    {
        this.bridge = bridge;
        this.canvas = canvas;
        low = LANE_CENTRE - (bridge.width() - 1) / 2;
        high = LANE_CENTRE + bridge.width() / 2;
        railAlong = bridge.isAlongX() ? RAIL_X : RAIL_Z;
    }

    static void paint(DaedalusBridge bridge, DaedalusCanvas canvas)
    {
        var painter = new BridgePainter(bridge, canvas);
        for (int span = 0; span < SPAN; span++)
            painter.paintSlice(span);
    }

    private void put(int span, int lateral, int y, DaedalusMaterial material)
    {
        canvas.set(bridge.worldX(span, lateral), y, bridge.worldZ(span, lateral), material);
    }

    private void column(int span, int lateral, int fromY, int toY, DaedalusMaterial material)
    {
        int x = bridge.worldX(span, lateral);
        int z = bridge.worldZ(span, lateral);
        canvas.fill(x, fromY, z, x, toY, z, material);
    }

    private void paintSlice(int span)
    {
        int deck = bridge.deckY(span);
        boolean hasEdges = bridge.style() != DaedalusBridge.BridgeStyle.BEAM;
        for (int lateral = low; lateral <= high; lateral++)
            put(span, lateral, deck, lateral == LANE_CENTRE ? FLOOR_TRIM : FLOOR);
        if (hasEdges)
        {
            put(span, low - 1, deck, isLampSpot(span) ? LAMP : TRIM);
            put(span, high + 1, deck, isLampSpot(span) ? LAMP : TRIM);
        }
        else
        {
            put(span, low, deck, isLampSpot(span) ? LAMP : TRIM);
            put(span, high, deck, isLampSpot(span) ? LAMP : TRIM);
        }

        if (bridge.isRampStep(span))
        {
            for (int lateral = low; lateral <= high; lateral++)
                put(span, lateral, deck + 1, stairs(bridge.climbDirection()));
        }

        switch (bridge.style())
        {
            case PARAPET, RAMP -> paintRails(span, deck);
            case ARCADE -> paintArcade(span, deck);
            case TUNNEL -> paintTunnel(span, deck);
            case PIER -> paintPier(span, deck);
            case CHAINED -> paintChains(span, deck);
            case BEAM ->
            {
                // A bare deck over the drop is the whole point of this one.
            }
        }
    }

    private static boolean isLampSpot(int span)
    {
        return span % LAMP_SPACING == LAMP_SPACING / 2;
    }

    private static boolean isRib(int span)
    {
        return span % RIB_SPACING == RIB_OFFSET;
    }

    private void paintRails(int span, int deck)
    {
        int y = deck + (bridge.isRampStep(span) ? 2 : 1);
        put(span, low - 1, y, railAlong);
        put(span, high + 1, y, railAlong);
    }

    private void paintArcade(int span, int deck)
    {
        int roof = deck + ARCADE_HEIGHT + 1;
        if (isRib(span))
        {
            column(span, low - 1, deck + 1, roof, COLUMN);
            column(span, high + 1, deck + 1, roof, COLUMN);
            for (int lateral = low; lateral <= high; lateral++)
                put(span, lateral, roof - 1, TRIM);
        }
        else
        {
            paintRails(span, deck);
        }
        for (int lateral = low - 1; lateral <= high + 1; lateral++)
            put(span, lateral, roof, isRib(span) ? TRIM : SLAB);
    }

    private void paintTunnel(int span, int deck)
    {
        int roof = deck + COVERED_HEIGHT + 1;
        boolean window = Math.floorMod(span, RIB_SPACING) == 3 || Math.floorMod(span, RIB_SPACING) == 4;
        for (int lateral : new int[] { low - 1, high + 1 })
        {
            column(span, lateral, deck + 1, roof - 1, isRib(span) ? COLUMN : WALL);
            if (window)
                column(span, lateral, deck + 2, deck + 3, AIR);
        }
        for (int lateral = low - 1; lateral <= high + 1; lateral++)
            put(span, lateral, roof, CEILING);
        if (isLampSpot(span))
            put(span, LANE_CENTRE, roof, LAMP);
    }

    /**
     * A pier down to the floor of the chasm, with an arch on each side carrying the deck to it.
     */
    private void paintPier(int span, int deck)
    {
        paintRails(span, deck);
        int fromMiddle = Math.abs(span * 2 + 1 - SPAN) / 2;
        int lowest;
        if (fromMiddle < PIER_HALF_WIDTH)
        {
            lowest = 1;
        }
        else
        {
            int fromCrown = Math.abs(Math.min(span, SPAN - 1 - span) - ARCH_CROWN);
            int reach = Math.min(span, SPAN - 1 - span) <= ARCH_REACH ? fromCrown * fromCrown / ARCH_CURVE : 0;
            lowest = deck - 1 - reach;
        }
        for (int lateral = low - 1; lateral <= high + 1; lateral++)
            column(span, lateral, lowest, deck - 1, fromMiddle < PIER_HALF_WIDTH ? COLUMN : WALL);
    }

    private void paintChains(int span, int deck)
    {
        if (!isRib(span))
            return;
        column(span, low - 1, deck + 1, DistrictPos.TOP_Y - 1, CHAIN);
        column(span, high + 1, deck + 1, DistrictPos.TOP_Y - 1, CHAIN);
    }
}
