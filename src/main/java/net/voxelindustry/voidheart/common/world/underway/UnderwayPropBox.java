package net.voxelindustry.voidheart.common.world.underway;

import java.util.ArrayList;
import java.util.List;

/**
 * One box of a prop's model, in sixteenths of a block. Faces draw the texture a pixel to a sixteenth, at the place the box takes in the block, so nothing is ever stretched.
 *
 * @param texture the texture of every face not listed in {@code faces}, a file of textures/block/underway
 */
public record UnderwayPropBox(int x0, int y0, int z0, int x1, int y1, int z1, String texture, List<Face> faces)
{
    /**
     * A face showing another texture.
     *
     * @param side the first letter of north, south, east, west, up or down
     * @param u    where the face's left edge falls on the texture
     * @param v    where its top edge falls
     */
    public record Face(char side, String texture, int u, int v)
    {
    }

    static List<UnderwayPropBox> parse(String boxes)
    {
        var parsed = new ArrayList<UnderwayPropBox>();
        for (var box : boxes.split("\\|"))
        {
            var words = box.trim().split(" ");
            var faces = new ArrayList<Face>();
            for (int word = 7; word < words.length; word++)
            {
                var corner = words[word].substring(words[word].indexOf('@') + 1).split(",");
                faces.add(new Face(words[word].charAt(0), words[word].substring(2, words[word].indexOf('@')), Integer.parseInt(corner[0]), Integer.parseInt(corner[1])));
            }
            parsed.add(new UnderwayPropBox(Integer.parseInt(words[0]), Integer.parseInt(words[1]), Integer.parseInt(words[2]),
                    Integer.parseInt(words[3]), Integer.parseInt(words[4]), Integer.parseInt(words[5]), words[6], List.copyOf(faces)));
        }
        return List.copyOf(parsed);
    }

    /** @return how wide a face is on its texture */
    public int faceWidth(char side)
    {
        return side == 'e' || side == 'w' ? z1 - z0 : x1 - x0;
    }

    /** @return how tall a face is on its texture */
    public int faceHeight(char side)
    {
        return side == 'u' || side == 'd' ? z1 - z0 : y1 - y0;
    }

    public boolean overlaps(UnderwayPropBox other)
    {
        return x0 < other.x1 && other.x0 < x1 && y0 < other.y1 && other.y0 < y1 && z0 < other.z1 && other.z0 < z1;
    }
}
