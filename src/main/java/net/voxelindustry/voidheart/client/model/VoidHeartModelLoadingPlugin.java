package net.voxelindustry.voidheart.client.model;

import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.client.render.model.UnbakedModel;
import net.minecraft.client.util.ModelIdentifier;
import net.voxelindustry.voidheart.client.model.conduit.ConduitUnbakedModel;
import net.voxelindustry.voidheart.client.model.monolith.VoidMonolithUnbakedModel;
import net.voxelindustry.voidheart.client.model.portalframe.PortalFrameCoreUnbakedModel;
import net.voxelindustry.voidheart.client.model.portalframe.PortalFrameUnbakedModel;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class VoidHeartModelLoadingPlugin implements ModelLoadingPlugin
{
    public final ModelIdentifier PORTAL_FRAME = new ModelIdentifier(MODID, "portal_frame", "");
    public final ModelIdentifier PORTAL_FRAME_CORE = new ModelIdentifier(MODID, "portal_frame_core", "");
    public final ModelIdentifier VOID_MONOLITH = new ModelIdentifier(MODID, "void_monolith", "");
    public final ModelIdentifier CONDUIT = new ModelIdentifier(MODID, "void_conduit", "");

    private final Map<ModelIdentifier, Function<UnbakedModel, UnbakedModel>> modelMap = new HashMap<>();

    @Override
    public void onInitializeModelLoader(Context pluginContext)
    {
        modelMap.put(PORTAL_FRAME, PortalFrameUnbakedModel::new);
        modelMap.put(PORTAL_FRAME_CORE, PortalFrameCoreUnbakedModel::new);
        modelMap.put(VOID_MONOLITH, VoidMonolithUnbakedModel::new);
        modelMap.put(CONDUIT, ConduitUnbakedModel::new);

        pluginContext.modifyModelOnLoad().register((original, context) ->
        {
            if (!context.id().getNamespace().equals(MODID))
                return original;

            if (context.id() instanceof ModelIdentifier modelIdentifier)
            {
                if (modelIdentifier.getVariant().equals("inventory"))
                    return original;
            }

            if (context.id().getPath().equals(PORTAL_FRAME.getPath()))
                return modelMap.get(PORTAL_FRAME).apply(original);
            if (context.id().getPath().equals(PORTAL_FRAME_CORE.getPath()))
                return modelMap.get(PORTAL_FRAME_CORE).apply(original);
            if (context.id().getPath().equals(VOID_MONOLITH.getPath()))
                return modelMap.get(VOID_MONOLITH).apply(original);
            if (context.id().getPath().equals(CONDUIT.getPath()))
                return modelMap.get(CONDUIT).apply(original);
            return original;
        });
    }
}
