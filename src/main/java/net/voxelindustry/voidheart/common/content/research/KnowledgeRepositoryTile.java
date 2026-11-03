package net.voxelindustry.voidheart.common.content.research;

import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.voxelindustry.steamlayer.tile.TileBase;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

import java.util.Optional;
import java.util.UUID;

/**
 * Only remembers who it answers to: the knowledge itself belongs to that player and outlives the block. It looks around for the Knowledge Columns that let them hold more.
 */
public class KnowledgeRepositoryTile extends TileBase
{
    private static final int COLUMN_SCAN_INTERVAL = 40;

    @Getter
    private Optional<UUID> owner = Optional.empty();

    public KnowledgeRepositoryTile(BlockPos pos, BlockState state)
    {
        super(VoidHeartTiles.KNOWLEDGE_REPOSITORY, pos, state);
    }

    public static void tick(Level world, BlockPos pos, BlockState state, KnowledgeRepositoryTile repository)
    {
        if (!(world instanceof ServerLevel level) || repository.owner.isEmpty() || (level.getGameTime() + pos.hashCode()) % COLUMN_SCAN_INTERVAL != 0)
            return;

        // A repository its owner moved on from lets go of its columns, see the column
        var here = GlobalPos.of(level.dimension(), pos);
        var server = level.getServer();
        var data = KnowledgeState.get(server).of(repository.owner.get());
        if (data.getRepository().filter(here::equals).isEmpty())
            return;

        int radius = Knowledge.columnRadius();
        var from = pos.offset(-radius, -radius, -radius);
        var to = pos.offset(radius, radius, radius);
        // Reading an unloaded chunk would load it, and its columns are still there anyway: the last count stands
        if (!level.hasChunksAt(from, to))
            return;

        boolean usable = data.columnCapacity(Knowledge.tree(server.registryAccess())) > 0;
        int columns = 0;
        for (var scanned : BlockPos.betweenClosed(from, to))
        {
            var scannedState = level.getBlockState(scanned);
            if (scannedState.is(VoidHeartBlocks.KNOWLEDGE_COLUMN) && level.getBlockEntity(scanned) instanceof KnowledgeColumnTile column && column.claim(pos, repository.owner.get(), usable))
                columns += Integer.bitCount(KnowledgeColumnBlock.maskOf(scannedState));
        }
        Knowledge.reportColumns(server, repository.owner.get(), here, columns);
    }

    public void setOwner(UUID owner)
    {
        this.owner = Optional.of(owner);
        setChanged();
    }

    @Override
    protected void loadAdditional(ValueInput tag)
    {
        super.loadAdditional(tag);

        owner = tag.read("owner", UUIDUtil.CODEC);
    }

    @Override
    protected void saveAdditional(ValueOutput tag)
    {
        owner.ifPresent(uuid -> tag.store("owner", UUIDUtil.CODEC, uuid));

        super.saveAdditional(tag);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state)
    {
        if (level instanceof ServerLevel serverLevel)
            owner.ifPresent(uuid -> Knowledge.unbind(serverLevel.getServer(), uuid, GlobalPos.of(serverLevel.dimension(), pos)));

        super.preRemoveSideEffects(pos, state);
    }
}
