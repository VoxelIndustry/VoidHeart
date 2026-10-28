package net.voxelindustry.voidheart.common.content.beam;

import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Result of resolving the beam graph feeding a Shatter Forge.
 *
 * @param result                 composition of the single beam entering the forge
 * @param segments               every visible beam run, for client rendering
 * @param collectorContributions per collector position, the amount of its aspect it contributes to {@code result}
 * @param itemSource             position of the pillar holding the input item, if any
 * @param totalPairs             monolith pairs on every track of the graph, the forge's own pair excluded
 * @param structureBlocks        conduits, monoliths, foci and collectors that should light up while crafting
 * @param experienceSources      experience skulls sitting on monolith pairs, where collectors would retune the beam
 * @param error                  first formation error encountered, empty if the graph is valid
 */
public record BeamResolution(BeamComposition result,
                             List<BeamSegment> segments,
                             Map<BlockPos, Integer> collectorContributions,
                             Optional<BlockPos> itemSource,
                             int totalPairs,
                             List<BlockPos> structureBlocks,
                             List<BlockPos> experienceSources,
                             Optional<FormationError> error)
{
    public static BeamResolution failed(FormationError error)
    {
        return new BeamResolution(BeamComposition.EMPTY, List.of(), Map.of(), Optional.empty(), 0, List.of(), List.of(), Optional.of(error));
    }

    public boolean isValid()
    {
        return error.isEmpty();
    }
}
