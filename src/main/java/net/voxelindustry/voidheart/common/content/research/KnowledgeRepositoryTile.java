package net.voxelindustry.voidheart.common.content.research;

import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.steamlayer.tile.TileBase;
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

import java.util.Optional;
import java.util.UUID;

/**
 * Only remembers who it answers to: the knowledge itself belongs to that player and outlives the block. It looks around for the Knowledge Columns that let them hold more.
 * Clients are told what it is doing so that it shows without opening the screen. That is read back from its owner each time, so it is never saved.
 */
public class KnowledgeRepositoryTile extends TileBase
{
    /** How high above the block the ring of the running research floats, which the glyphs head for. */
    public static final float RING_HEIGHT = 1.62F;

    private static final int   COLUMN_SCAN_INTERVAL = 40;
    private static final int   STATUS_INTERVAL      = 20;
    private static final float STATUS_STEP          = 0.004F;

    private static final int EVENT_FINISHED = 0;
    private static final int EVENT_FED      = 1;

    private static final double TABLET_HEIGHT = 1.056;
    private static final double TABLET_OFFSET = 0.129;

    private static final float GLYPH_CHANCE         = 0.09F;
    private static final float GLYPH_CHANCE_STARVED = 0.03F;
    private static final float GLYPH_SPREAD         = 0.22F;
    private static final float GLYPH_TARGET_SPREAD  = 0.05F;
    private static final float GLYPH_TARGET_DROP    = 0.19F;
    private static final int   FINISH_GLYPHS        = 12;
    private static final float FINISH_REACH         = 0.9F;

    private static final float AIR_MOTE_CHANCE         = 0.08F;
    private static final float AIR_MOTE_CHANCE_STARVED = 0.025F;
    private static final float AIR_MOTE_RADIUS         = 1;
    private static final float AIR_MOTE_RADIUS_ADD     = 0.6F;
    private static final float AIR_MOTE_HEIGHT         = 0.2F;
    private static final float AIR_MOTE_HEIGHT_ADD     = 1.1F;
    private static final int   FED_MOTES               = 12;
    private static final float COLUMN_MOTE_LEAD        = 0.3F;
    private static final float MOTES_PER_TICK          = 0.3F;
    private static final float MOTES_PER_TICK_STARVED  = 0.08F;
    private static final float MOTE_BUDGET             = 2;

    @Getter
    private Optional<UUID> owner = Optional.empty();

    @Getter
    private KnowledgeRepositoryStatus status     = KnowledgeRepositoryStatus.DORMANT;
    private Optional<Identifier>      icon       = Optional.empty();
    private float                     progress;
    private float                     fill;
    private boolean                   hasColumns;

    /** Server only, what it last saw running, to notice that finishing. */
    private Optional<Identifier> watchedResearch = Optional.empty();

    /** Client only. */
    @Getter
    private final KnowledgeRepositoryDisplay display = new KnowledgeRepositoryDisplay();
    private float moteBudget;

    public KnowledgeRepositoryTile(BlockPos pos, BlockState state)
    {
        super(VoidHeartTiles.KNOWLEDGE_REPOSITORY, pos, state);
    }

    public static void tick(Level world, BlockPos pos, BlockState state, KnowledgeRepositoryTile repository)
    {
        if (!(world instanceof ServerLevel level) || repository.owner.isEmpty())
            return;

        long clock = level.getGameTime() + pos.hashCode();
        if (clock % STATUS_INTERVAL == 0)
            repository.reportStatus(level);
        if (clock % COLUMN_SCAN_INTERVAL != 0)
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

    public static void clientTick(Level world, BlockPos pos, BlockState state, KnowledgeRepositoryTile repository)
    {
        repository.display.tick(repository.status, repository.icon, repository.progress, repository.fill);
        repository.emitEffects(world);
    }

    /**
     * Reads without settling: settling here could finish a research while its owner is away, who would then never hear of it.
     * So the repository of an absent owner shows what they left it doing.
     */
    private void reportStatus(ServerLevel level)
    {
        var server = level.getServer();
        var data = KnowledgeState.get(server).of(owner.orElseThrow());
        var tree = Knowledge.tree(server.registryAccess());
        var rules = Knowledge.rules();

        boolean bound = data.getRepository().filter(GlobalPos.of(level.dimension(), worldPosition)::equals).isPresent();
        var running = data.getActive().filter(id -> bound && tree.containsKey(id));
        double cap = data.cap(rules, tree);

        var nowStatus = KnowledgeRepositoryStatus.of(bound, running.isPresent(), data.getKnowledge(), cap);
        var nowIcon = running.map(id -> tree.get(id).icon());
        float nowProgress = running.map(id -> (float) (data.getProgress().getOrDefault(id, 0D) / tree.get(id).knowledgeCost())).orElse(0F);
        float nowFill = cap > 0 ? Mth.clamp((float) (data.getKnowledge() / cap), 0, 1) : 0;
        boolean nowColumns = data.getColumns() > 0;

        if (bound && watchedResearch.filter(data.getCompleted()::contains).isPresent())
            level.blockEvent(worldPosition, getBlockState().getBlock(), EVENT_FINISHED, 0);
        watchedResearch = running;

        if (nowStatus == status && nowIcon.equals(icon) && nowColumns == hasColumns && Math.abs(nowProgress - progress) < STATUS_STEP && Math.abs(nowFill - fill) < STATUS_STEP)
            return;

        status = nowStatus;
        icon = nowIcon;
        progress = nowProgress;
        fill = nowFill;
        hasColumns = nowColumns;
        sync();
    }

    public void onFed(ServerLevel level)
    {
        level.blockEvent(worldPosition, getBlockState().getBlock(), EVENT_FED, 0);
        reportStatus(level);
    }

    @Override
    public boolean triggerEvent(int event, int parameter)
    {
        if (level != null && level.isClientSide())
        {
            if (event == EVENT_FINISHED)
                onFinished();
            else if (event == EVENT_FED)
                onFedEffect();
        }
        return event == EVENT_FINISHED || event == EVENT_FED;
    }

    private void onFinished()
    {
        display.finish();
        var from = tabletCenter().add(0, RING_HEIGHT - TABLET_HEIGHT, 0);
        var random = level.getRandom();
        for (int glyph = 0; glyph < FINISH_GLYPHS; glyph++)
        {
            float angle = Mth.TWO_PI * glyph / FINISH_GLYPHS + random.nextFloat();
            float rise = random.nextFloat() * FINISH_REACH;
            level.addParticle(VoidHeart.KNOWLEDGE_GLYPH_PARTICLE, from.x, from.y, from.z,
                    from.x + Mth.cos(angle) * FINISH_REACH, from.y + rise, from.z + Mth.sin(angle) * FINISH_REACH);
        }
    }

    private void onFedEffect()
    {
        var random = level.getRandom();
        for (int mote = 0; mote < FED_MOTES; mote++)
            airMote(Mth.TWO_PI * mote / FED_MOTES + random.nextFloat());
    }

    private void emitEffects(Level world)
    {
        var random = world.getRandom();
        boolean starved = status == KnowledgeRepositoryStatus.STARVED;

        moteBudget = status.drawsFromColumns() ? Math.min(MOTE_BUDGET, moteBudget + (starved ? MOTES_PER_TICK_STARVED : MOTES_PER_TICK)) : 0;
        if (status.drawsFromColumns() && !hasColumns && random.nextFloat() < (starved ? AIR_MOTE_CHANCE_STARVED : AIR_MOTE_CHANCE))
            airMote(random.nextFloat() * Mth.TWO_PI);

        if (status == KnowledgeRepositoryStatus.RESEARCHING && random.nextFloat() < GLYPH_CHANCE || starved && random.nextFloat() < GLYPH_CHANCE_STARVED)
            risingGlyph();
    }

    private void airMote(float angle)
    {
        var random = level.getRandom();
        var to = tabletCenter();
        float radius = AIR_MOTE_RADIUS + random.nextFloat() * AIR_MOTE_RADIUS_ADD;
        level.addParticle(VoidHeart.KNOWLEDGE_MOTE_PARTICLE,
                worldPosition.getX() + 0.5 + Mth.cos(angle) * radius,
                worldPosition.getY() + AIR_MOTE_HEIGHT + random.nextFloat() * AIR_MOTE_HEIGHT_ADD,
                worldPosition.getZ() + 0.5 + Mth.sin(angle) * radius,
                to.x, to.y, to.z);
    }

    private void risingGlyph()
    {
        var random = level.getRandom();
        var from = tabletCenter();
        level.addParticle(VoidHeart.KNOWLEDGE_GLYPH_PARTICLE,
                from.x + (random.nextFloat() * 2 - 1) * GLYPH_SPREAD,
                from.y,
                from.z + (random.nextFloat() * 2 - 1) * GLYPH_SPREAD,
                worldPosition.getX() + 0.5 + (random.nextFloat() * 2 - 1) * GLYPH_TARGET_SPREAD,
                worldPosition.getY() + RING_HEIGHT - GLYPH_TARGET_DROP,
                worldPosition.getZ() + 0.5 + (random.nextFloat() * 2 - 1) * GLYPH_TARGET_SPREAD);
    }

    /**
     * Called by a column shimmering near the player. A budget keeps a room full of them to a steady flow.
     *
     * @param column the middle of that column, the speck leaving from its surface on this side
     */
    public void drawMote(Vec3 column)
    {
        if (!status.drawsFromColumns() || moteBudget < 1)
            return;
        moteBudget--;

        var to = tabletCenter();
        var from = column.add(to.subtract(column).multiply(1, 0, 1).normalize().scale(COLUMN_MOTE_LEAD));
        level.addParticle(VoidHeart.KNOWLEDGE_MOTE_PARTICLE, from.x, from.y, from.z, to.x, to.y, to.z);
    }

    /** The model's tablet is tilted and sits toward the facing of the block. */
    private Vec3 tabletCenter()
    {
        var facing = getBlockState().getValue(KnowledgeRepositoryBlock.FACING);
        return new Vec3(worldPosition.getX() + 0.5 + facing.getStepX() * TABLET_OFFSET, worldPosition.getY() + TABLET_HEIGHT, worldPosition.getZ() + 0.5 + facing.getStepZ() * TABLET_OFFSET);
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
        status = KnowledgeRepositoryStatus.values()[Mth.clamp(tag.getIntOr("status", 0), 0, KnowledgeRepositoryStatus.values().length - 1)];
        icon = tag.read("icon", Identifier.CODEC);
        progress = tag.getFloatOr("progress", 0);
        fill = tag.getFloatOr("fill", 0);
        hasColumns = tag.getBooleanOr("has_columns", false);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries)
    {
        var tag = super.getUpdateTag(registries);
        tag.putInt("status", status.ordinal());
        icon.ifPresent(id -> tag.putString("icon", id.toString()));
        tag.putFloat("progress", progress);
        tag.putFloat("fill", fill);
        tag.putBoolean("has_columns", hasColumns);
        return tag;
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
