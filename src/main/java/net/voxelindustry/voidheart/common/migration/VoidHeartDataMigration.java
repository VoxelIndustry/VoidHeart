package net.voxelindustry.voidheart.common.migration;

import lombok.extern.log4j.Log4j2;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

@Log4j2
public class VoidHeartDataMigration
{
    public static final  int                                CURRENT_VERSION = 1;
    private static final Map<String, Consumer<NbtCompound>> migrations      = new HashMap<>();

    public static void initMigrations()
    {
        migrations.put("voidheart:portal_wall", tag ->
        {
            if (tag.getBoolean("isCore"))
                tag.putString("id", BlockEntityType.getId(VoidHeartTiles.PORTAL_FRAME_CORE).toString());
            else
                tag.putString("id", BlockEntityType.getId(VoidHeartTiles.PORTAL_FRAME).toString());
        });
    }

    public static int getModDataVersion(NbtCompound tag)
    {
        return tag.getInt(MODID + "_DataVersion");
    }

    public static NbtCompound migrateBlockEntities(NbtCompound input)
    {
        if (!input.contains("block_entities"))
            return input;
        var blockEntities = input.getList("block_entities", NbtElement.COMPOUND_TYPE);

        if (blockEntities.isEmpty())
            return input;
        if (getModDataVersion(input) >= CURRENT_VERSION)
            return input;

        for (NbtElement tag : blockEntities)
        {
            var compound = ((NbtCompound) tag);
            var migration = migrations.get(compound.getString("id"));

            if (migration != null)
                migration.accept(compound);
        }

        return input;
    }
}
