package net.voxelindustry.voidheart.client.model.armor;

import net.minecraft.client.model.geom.builders.CubeListBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Where each of the Void Armor's boxes takes its skin, and what it is made of. Boxes are packed in rows across the texture in the order the model adds them,
 * each taking the footprint of its unfolded faces, so the model and the script that paints its skin ({@code .claude/tools/void_armor_skin.lua}) read the same
 * layout. Changing the model means painting the skin again: {@code DUNGEON_PREVIEW=true ./gradlew test --tests '*VoidArmorSkinTest*'} writes the layout.
 */
public final class ArmorSkin
{
    public static final int WIDTH  = 128;
    public static final int HEIGHT = 128;

    /**
     * What a box is painted as, in the base texture and in the glow drawn over it.
     */
    public enum Material
    {
        /** The Dragoon's dark void metal, lit at the top of each face and edged in dull Ravenous Gold at its hem, laced to the plate above. */
        PLATE,
        /** The same plates a shade deeper, like lacquer, so rows of plates separate. */
        LACQUER,
        /** What the plates are laced over: darker, plain metal. */
        CORE,
        GOLD,
        /** The menpō: a face mask with a mouth grille. */
        MASK,
        /** The plate over the eyes, cut by the Dragoon's eye slit. */
        SLIT,
        LENS_NIGHT,
        LENS_SPECTRAL,
        LENS_APPARATUS,
        VENT,
        TUBE,
        PISTON;

        public String key()
        {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * @param u where the box's unfolded faces start, in texels
     */
    public record Box(String part, Material material, int u, int v, float width, float height, float depth)
    {
        public int footprintWidth()
        {
            return (int) Math.ceil(2 * (width + depth));
        }

        public int footprintHeight()
        {
            return (int) Math.ceil(depth + height);
        }
    }

    private final List<Box> boxes = new ArrayList<>();
    private int x;
    private int y;
    private int rowHeight;

    /**
     * Adds a box to the builder at the next free place in the texture.
     */
    public CubeListBuilder box(CubeListBuilder builder, String part, Material material, float x0, float y0, float z0, float width, float height, float depth)
    {
        var footprint = new Box(part, material, 0, 0, width, height, depth);
        if (x + footprint.footprintWidth() > WIDTH)
        {
            x = 0;
            y += rowHeight;
            rowHeight = 0;
        }
        if (y + footprint.footprintHeight() > HEIGHT)
            throw new IllegalStateException("The Void Armor's boxes no longer fit its " + WIDTH + "x" + HEIGHT + " skin");

        boxes.add(new Box(part, material, x, y, width, height, depth));
        builder.texOffs(x, y).addBox(x0, y0, z0, width, height, depth);
        x += footprint.footprintWidth();
        rowHeight = Math.max(rowHeight, footprint.footprintHeight());
        return builder;
    }

    public List<Box> boxes()
    {
        return List.copyOf(boxes);
    }

    /**
     * The layout as JSON, for the script that paints the skin.
     */
    public String toJson()
    {
        var json = new StringBuilder("{\"width\":" + WIDTH + ",\"height\":" + HEIGHT + ",\"boxes\":[");
        for (int i = 0; i < boxes.size(); i++)
        {
            var box = boxes.get(i);
            if (i > 0)
                json.append(',');
            json.append(String.format(Locale.ROOT, "{\"part\":\"%s\",\"material\":\"%s\",\"u\":%d,\"v\":%d,\"w\":%.3f,\"h\":%.3f,\"d\":%.3f}",
                    box.part(), box.material().key(), box.u(), box.v(), box.width(), box.height(), box.depth()));
        }
        return json.append("]}").toString();
    }
}
