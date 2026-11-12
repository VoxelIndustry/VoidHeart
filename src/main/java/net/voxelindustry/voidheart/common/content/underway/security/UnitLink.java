package net.voxelindustry.voidheart.common.content.underway.security;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.voxelindustry.voidheart.common.content.underway.UnderwaySecurity;
import net.voxelindustry.voidheart.common.world.daedalus.DistrictPos;
import net.voxelindustry.voidheart.common.world.underway.UnderwaySection;
import net.voxelindustry.voidheart.common.world.underway.UnderwayUnit;
import org.jspecify.annotations.Nullable;

/**
 * What ties a unit to the section it was posted in: the fort, its place in the fort's list of units, and its circuit. A unit nobody posted, summoned by hand or by a test, answers to no section and is always awake.
 */
public final class UnitLink
{
    private boolean posted;
    private int districtX;
    private int districtZ;
    private UnderwaySection section = UnderwaySection.ENTRY;
    private int unit;
    private boolean emergency;

    public void post(DistrictPos district, int unit, UnderwayUnit plan)
    {
        posted = true;
        districtX = district.x();
        districtZ = district.z();
        section = plan.section();
        this.unit = unit;
        emergency = plan.emergency();
    }

    public boolean isPosted()
    {
        return posted;
    }

    public UnderwaySecurity.@Nullable Key key()
    {
        return posted ? new UnderwaySecurity.Key(districtX, districtZ, section) : null;
    }

    public boolean isAwake(ServerLevel level)
    {
        var security = UnderwaySecurity.of(level);
        return !posted || security == null || security.isAwake(level, key(), emergency);
    }

    /** @return whether its section has power. A unit nobody posted has none. */
    public boolean isLive(ServerLevel level)
    {
        var security = UnderwaySecurity.of(level);
        return posted && security != null && security.isLive(level, key());
    }

    /** @return whether its section already counts it as destroyed, which a stale copy of it must not outlive */
    public boolean isWreck(ServerLevel level)
    {
        var security = UnderwaySecurity.of(level);
        return posted && security != null && security.isDestroyed(key(), unit);
    }

    public void raiseAlarm(ServerLevel level)
    {
        var security = UnderwaySecurity.of(level);
        if (posted && security != null)
            security.raiseAlarm(level, key());
    }

    public boolean isAlarmed(ServerLevel level)
    {
        var security = UnderwaySecurity.of(level);
        return posted && security != null && security.isAlarmed(key());
    }

    /** Answers a broken tripwire at a place of its section. */
    public void trip(ServerLevel level, BlockPos wire)
    {
        var security = UnderwaySecurity.of(level);
        if (posted && security != null)
            security.trip(level, key(), wire);
    }

    /** Tells its whole section where someone is. */
    public void spot(ServerLevel level, Player player)
    {
        var security = UnderwaySecurity.of(level);
        if (posted && security != null)
            security.spot(level, key(), player);
    }

    /** @return whoever a searchlight of its section caught lately */
    public @Nullable Player spotted(ServerLevel level)
    {
        var security = UnderwaySecurity.of(level);
        return posted && security != null ? security.spotted(level, key()) : null;
    }

    public void destroyed(ServerLevel level)
    {
        var security = UnderwaySecurity.of(level);
        if (posted && security != null)
            security.destroyed(level, key(), unit);
    }

    public void save(ValueOutput output)
    {
        output.putBoolean("Posted", posted);
        if (!posted)
            return;
        output.putInt("DistrictX", districtX);
        output.putInt("DistrictZ", districtZ);
        output.putInt("Section", section.ordinal());
        output.putInt("Unit", unit);
        output.putBoolean("Emergency", emergency);
    }

    public void load(ValueInput input)
    {
        int saved = input.getIntOr("Section", -1);
        posted = input.getBooleanOr("Posted", false) && saved >= 0 && saved < UnderwaySection.values().length;
        if (!posted)
            return;
        districtX = input.getIntOr("DistrictX", 0);
        districtZ = input.getIntOr("DistrictZ", 0);
        section = UnderwaySection.of(saved);
        unit = input.getIntOr("Unit", 0);
        emergency = input.getBooleanOr("Emergency", false);
    }
}
