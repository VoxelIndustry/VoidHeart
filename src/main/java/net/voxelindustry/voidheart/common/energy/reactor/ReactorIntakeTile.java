package net.voxelindustry.voidheart.common.energy.reactor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.voxelindustry.steamlayer.tile.TileBase;
import net.voxelindustry.voidheart.common.content.essence.EssenceRegistry;
import net.voxelindustry.voidheart.common.content.essence.EssenceStorage;
import net.voxelindustry.voidheart.common.content.essence.EssenceType;
import net.voxelindustry.voidheart.common.content.essence.IEssenceReceiver;
import org.jspecify.annotations.Nullable;

/**
 * One of a reactor's two intakes, which relays link to like any receiver. It holds one kind at a time: Contravoid, or one essence the reactor cancels
 * it against. Across the core, the other intake must hold the other side, so an empty intake takes only what its partner lacks.
 * <p>
 * It gives nothing back to the relays, so a Shatter Forge never draws its fuel away. Clients never need its contents, so it doesn't sync them.
 */
public class ReactorIntakeTile extends TileBase implements IEssenceReceiver
{
    private EssenceStorage tank = new EssenceStorage(EssenceRegistry.EMPTY, ReactorBalance.INTAKE_TANK);

    public ReactorIntakeTile(BlockPos pos, BlockState state)
    {
        super(ContravoidReactorContent.REACTOR_INTAKE_TILE, pos, state);
    }

    public static boolean isContravoid(EssenceType type)
    {
        return type == EssenceRegistry.CONTRAVOID;
    }

    @Nullable
    public static ReactorEssence essenceOf(EssenceType type)
    {
        if (type == EssenceRegistry.STILLNESS)
            return ReactorEssence.STILLNESS;
        if (type == EssenceRegistry.FIERY)
            return ReactorEssence.FIERY;
        if (type == EssenceRegistry.SUFFERING)
            return ReactorEssence.SUFFERING;
        return null;
    }

    public static EssenceType typeOf(ReactorEssence essence)
    {
        return switch (essence)
        {
            case STILLNESS -> EssenceRegistry.STILLNESS;
            case FIERY -> EssenceRegistry.FIERY;
            case SUFFERING -> EssenceRegistry.SUFFERING;
        };
    }

    /**
     * What it holds, or EMPTY once it runs dry.
     */
    public EssenceType held()
    {
        return tank.amount() > 0 ? tank.type() : EssenceRegistry.EMPTY;
    }

    public int amount()
    {
        return tank.amount();
    }

    public boolean holdsContravoid()
    {
        return tank.amount() > 0 && isContravoid(tank.type());
    }

    public boolean holdsEssence()
    {
        return tank.amount() > 0 && essenceOf(tank.type()) != null;
    }

    /**
     * The intake across the core from this one, when a core stands between them.
     */
    @Nullable
    public ReactorIntakeTile partner()
    {
        if (level == null)
            return null;
        for (var direction : Direction.Plane.HORIZONTAL)
        {
            if (!level.getBlockState(worldPosition.relative(direction)).is(ContravoidReactorContent.CONTRAVOID_REACTOR_CORE))
                continue;
            if (level.getBlockEntity(worldPosition.relative(direction, 2)) instanceof ReactorIntakeTile other)
                return other;
        }
        return null;
    }

    private boolean accepts(EssenceType type)
    {
        var contravoid = isContravoid(type);
        if (!contravoid && essenceOf(type) == null)
            return false;
        if (tank.amount() > 0)
            return tank.type() == type;

        var partner = partner();
        if (partner == null)
            return true;
        return contravoid ? !partner.holdsContravoid() : !partner.holdsEssence();
    }

    @Override
    public int spaceFor(EssenceType type)
    {
        return accepts(type) ? tank.maximumAmount() - tank.amount() : 0;
    }

    @Override
    public int receive(EssenceType type, int amount)
    {
        if (amount <= 0 || !accepts(type))
            return 0;
        if (tank.amount() == 0 && tank.type() != type)
            tank.type(type);
        var received = tank.fill(type, amount);
        if (received > 0)
            setChanged();
        return received;
    }

    /**
     * None of it can be drawn back out.
     */
    @Override
    public int amountOf(EssenceType type)
    {
        return 0;
    }

    @Override
    public int extract(EssenceType type, int amount)
    {
        return 0;
    }

    /**
     * Takes fuel for the reactor's burn.
     */
    int burn(int amount)
    {
        if (amount <= 0 || tank.amount() == 0)
            return 0;
        var drained = tank.drain(tank.type(), amount);
        if (tank.amount() == 0)
            tank.type(EssenceRegistry.EMPTY);
        setChanged();
        return drained;
    }

    /**
     * A failed reactor takes the fuel inside with it.
     */
    void loseFuel()
    {
        tank.type(EssenceRegistry.EMPTY);
        setChanged();
    }

    public EssenceStorage tank()
    {
        return tank;
    }

    /**
     * Restores what a broken intake carried.
     */
    public void restore(EssenceStorage carried)
    {
        tank = new EssenceStorage(EssenceRegistry.EMPTY, ReactorBalance.INTAKE_TANK);
        var type = carried.type();
        if (carried.amount() > 0 && (isContravoid(type) || essenceOf(type) != null))
        {
            tank.type(type);
            tank.fill(type, carried.amount());
        }
        setChanged();
    }

    @Override
    protected void loadAdditional(ValueInput tag)
    {
        super.loadAdditional(tag);
        tank = tag.read("tank", EssenceStorage.CODEC)
                .filter(read -> read.amount() > 0 && (isContravoid(read.type()) || essenceOf(read.type()) != null))
                .map(read ->
                {
                    var fresh = new EssenceStorage(read.type(), ReactorBalance.INTAKE_TANK);
                    fresh.fill(read.type(), read.amount());
                    return fresh;
                })
                .orElseGet(() -> new EssenceStorage(EssenceRegistry.EMPTY, ReactorBalance.INTAKE_TANK));
    }

    @Override
    protected void saveAdditional(ValueOutput tag)
    {
        super.saveAdditional(tag);
        if (tank.amount() > 0)
            tag.store("tank", EssenceStorage.CODEC, tank);
    }
}
