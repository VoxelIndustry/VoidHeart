package net.voxelindustry.voidheart.common.energy;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EnergyFieldsTest
{
    @Test
    void fieldIsASphereAroundItsCenter()
    {
        var fields = new EnergyFields();
        var center = new BlockPos(5, 60, -5);
        fields.add(center, 16);

        assertThat(fields.covers(center)).isTrue();
        assertThat(fields.covers(center.east(16))).isTrue();
        assertThat(fields.covers(center.below(16))).isTrue();
        assertThat(fields.covers(center.east(17))).isFalse();
        // A corner of the bounding cube lies outside the sphere
        assertThat(fields.covers(center.offset(12, 12, 0))).isFalse();
        assertThat(fields.covers(center.offset(11, 11, 0))).isTrue();
    }

    @Test
    void anyFieldCovers()
    {
        var fields = new EnergyFields();
        fields.add(BlockPos.ZERO, 2);
        fields.add(new BlockPos(100, 0, 0), 2);

        assertThat(fields.covers(new BlockPos(101, 0, 0))).isTrue();
        assertThat(fields.covers(new BlockPos(50, 0, 0))).isFalse();
    }

    @Test
    void removingAFieldUncoversIt()
    {
        var fields = new EnergyFields();
        var center = new BlockPos(1, 2, 3);
        fields.add(center.mutable(), 4);
        fields.remove(center);

        assertThat(fields.isEmpty()).isTrue();
        assertThat(fields.covers(center)).isFalse();
    }

    @Test
    void readdingAFieldReplacesItsRadius()
    {
        var fields = new EnergyFields();
        fields.add(BlockPos.ZERO, 10);
        fields.add(BlockPos.ZERO, 1);

        assertThat(fields.centers()).containsExactly(BlockPos.ZERO);
        assertThat(fields.covers(new BlockPos(5, 0, 0))).isFalse();
    }
}
