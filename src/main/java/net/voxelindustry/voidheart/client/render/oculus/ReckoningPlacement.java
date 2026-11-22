package net.voxelindustry.voidheart.client.render.oculus;

import net.voxelindustry.voidheart.common.content.works.WorksLayout;

/**
 * Where a plan's items stand on a Reckoning table, worked out from its layout alone: columns along the flow, rows across it, both centred on the
 * table and kept a margin inside its rim, each item sized to its spacing within bounds.
 *
 * @param along  each node's place along the flow from the table's centre, raw kinds negative
 * @param across each node's place across the flow from the table's centre, toward the viewer positive
 * @param item   how tall each item stands
 * @param plate  the side of the plate under each item
 */
public record ReckoningPlacement(float[] along, float[] across, float item, float plate)
{
    static final float MARGIN        = 0.3F;
    static final float MAX_STEP      = 0.75F;
    static final float ITEM_FRACTION = 0.62F;
    static final float MIN_ITEM      = 0.18F;
    static final float MAX_ITEM      = 0.34F;
    static final float PLATE_SCALE   = 1.2F;

    /**
     * @param length  table blocks along the flow
     * @param breadth table blocks across it
     */
    public static ReckoningPlacement of(WorksLayout<Integer> layout, int nodes, float length, float breadth)
    {
        float usableAlong = Math.max(0, length - 2 * MARGIN);
        float usableAcross = Math.max(0, breadth - 2 * MARGIN);
        float columnStep = layout.columns() > 1 ? Math.min(MAX_STEP, usableAlong / (layout.columns() - 1)) : 0;
        float rowStep = layout.rows() > 1 ? Math.min(MAX_STEP, usableAcross / (layout.rows() - 1)) : 0;

        float spacing = MAX_STEP;
        if (columnStep > 0)
            spacing = Math.min(spacing, columnStep);
        if (rowStep > 0)
            spacing = Math.min(spacing, rowStep);
        float item = Math.clamp(spacing * ITEM_FRACTION, MIN_ITEM, MAX_ITEM);

        var along = new float[nodes];
        var across = new float[nodes];
        for (var entry : layout.spots().entrySet())
        {
            int node = entry.getKey();
            if (node < 0 || node >= nodes)
                continue;
            along[node] = (entry.getValue().column() - (layout.columns() - 1) / 2F) * columnStep;
            across[node] = entry.getValue().y() * rowStep;
        }
        return new ReckoningPlacement(along, across, item, item * PLATE_SCALE);
    }
}
