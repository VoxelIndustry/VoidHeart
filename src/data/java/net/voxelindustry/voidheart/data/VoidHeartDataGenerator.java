package net.voxelindustry.voidheart.data;

import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;
import net.voxelindustry.voidheart.data.provider.AdvancementsProvider;

public class VoidHeartDataGenerator implements DataGeneratorEntrypoint
{

    @Override
    public void onInitializeDataGenerator(FabricDataGenerator generator)
    {
        var pack = generator.createPack();

        pack.addProvider(AdvancementsProvider::new);
    }
}
