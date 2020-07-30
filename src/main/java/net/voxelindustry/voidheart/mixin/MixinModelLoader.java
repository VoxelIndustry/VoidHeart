package net.voxelindustry.voidheart.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.model.ModelLoader;
import net.minecraft.client.render.model.UnbakedModel;
import net.minecraft.client.util.ModelIdentifier;
import net.voxelindustry.voidheart.client.model.monolith.VoidMonolithUnbakedModel;
import net.voxelindustry.voidheart.client.model.portalframe.PortalFrameCoreUnbakedModel;
import net.voxelindustry.voidheart.client.model.portalframe.PortalFrameUnbakedModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

@Environment(EnvType.CLIENT)
@Mixin(ModelLoader.class)
public class MixinModelLoader
{
    private static final Map<ModelIdentifier, Function<UnbakedModel, UnbakedModel>> MODEL_MAP = new HashMap<>();

    static
    {
        MODEL_MAP.put(new ModelIdentifier(MODID + ":portal_frame_core#*"), PortalFrameCoreUnbakedModel::new);
        MODEL_MAP.put(new ModelIdentifier(MODID + ":portal_frame#*"), PortalFrameUnbakedModel::new);
        MODEL_MAP.put(new ModelIdentifier(MODID + ":void_monolith#*"), VoidMonolithUnbakedModel::new);
    }

    @ModifyVariable(method = "addModel", at = @At("STORE"))
    private UnbakedModel modifyUnbakedModel(UnbakedModel model, ModelIdentifier modelId)
    {
        if (!MODID.equals(modelId.getNamespace()))
            return model;

        Optional<ModelIdentifier> candidate = MODEL_MAP.keySet().stream().filter(identifier ->
                identifier.getNamespace().equals(modelId.getNamespace())
                        && identifier.getPath().equals(modelId.getPath())
                        && (identifier.getVariant().equals("*") && !modelId.getVariant().equals("inventory") || identifier.getVariant().equals(modelId.getVariant()))).findFirst();

        if (candidate.isPresent())
            return MODEL_MAP.get(candidate.get()).apply(model);

        return model;
    }
}