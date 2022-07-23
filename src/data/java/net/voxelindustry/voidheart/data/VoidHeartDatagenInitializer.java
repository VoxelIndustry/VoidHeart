package net.voxelindustry.voidheart.data;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import net.minecraft.Bootstrap;
import net.minecraft.data.DataGenerator;
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.data.provider.VoidHeartRecipeProvider;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

import static java.util.Collections.emptyList;

public class VoidHeartDatagenInitializer implements PreLaunchEntrypoint
{
    @Override
    public void onPreLaunch()
    {
        try
        {
            Bootstrap.initialize();
            FabricLoader.getInstance().getEntrypoints("main", ModInitializer.class).forEach(ModInitializer::onInitialize);

            generateData();
        } catch (Exception e)
        {
            VoidHeart.LOGGER.log(Level.SEVERE, "Could not generate");
            VoidHeart.LOGGER.log(Level.SEVERE, e.getMessage(), e);
            System.exit(1);
        }

        System.exit(0);
    }

    private void generateData() throws IOException
    {
        DataGenerator generator = new DataGenerator(new File(".").toPath(), emptyList());
        generator.addProvider(new VoidHeartRecipeProvider(generator));

        generator.run();
    }
}
