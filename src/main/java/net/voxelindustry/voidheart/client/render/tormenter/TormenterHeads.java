package net.voxelindustry.voidheart.client.render.tormenter;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.voxelindustry.voidheart.VoidHeart;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The head of each mob as copied out of its model, or its whole body for those without one. It keeps its own poses, because
 * renderers share their model parts between all the entities they draw and these move them at any moment.
 */
public final class TormenterHeads
{
    private static final int DETACHED_ENTITY_ID = -1;

    private static final Map<EntityType<?>, Optional<Head>> HEADS = new HashMap<>();

    record Piece(Matrix4f pose, Matrix3f normal, ModelPart.Cube cube)
    {
    }

    /**
     * @param centerX where the middle of the head is in the space of its pieces
     * @param scale   what brings the largest side of the head down to one block
     */
    record Head(Identifier texture, List<Piece> pieces, float centerX, float centerY, float centerZ, float scale)
    {
    }

    private TormenterHeads()
    {
    }

    public static boolean hasHead(EntityType<?> type, Level level)
    {
        return of(type, level).isPresent();
    }

    static Optional<Head> of(EntityType<?> type, Level level)
    {
        return HEADS.computeIfAbsent(type, key -> copy(key, level));
    }

    public static void clear()
    {
        HEADS.clear();
    }

    /**
     * Any mod's mob can land here, and a renderer that does not cope with a mob out of the world costs a head, not the game.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Optional<Head> copy(EntityType<?> type, Level level)
    {
        try
        {
            if (!(type.create(level, EntitySpawnReason.LOAD) instanceof LivingEntity entity))
                return Optional.empty();
            // Its render state asks for an id, and this mob never joins a world to be given one
            entity.setId(DETACHED_ENTITY_ID);
            if (!(Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity) instanceof LivingEntityRenderer renderer))
                return Optional.empty();

            var state = (LivingEntityRenderState) renderer.createRenderState(entity, 0);
            return copy(renderer.getTextureLocation(state), (Model<?>) renderer.getModel());
        }
        catch (RuntimeException exception)
        {
            VoidHeart.LOGGER.log(java.util.logging.Level.WARNING, "No tormenter head for " + type, exception);
            return Optional.empty();
        }
    }

    private static Optional<Head> copy(Identifier texture, Model<?> model)
    {
        var part = headOf(model);
        part.resetPose();

        var pieces = new ArrayList<Piece>();
        part.visit(new PoseStack(), (pose, path, index, cube) ->
                pieces.add(new Piece(new Matrix4f(pose.pose()), new Matrix3f(pose.normal()), cube)));
        if (pieces.isEmpty())
            return Optional.empty();

        var min = new Vector3f(Float.POSITIVE_INFINITY);
        var max = new Vector3f(Float.NEGATIVE_INFINITY);
        var corner = new Vector3f();
        for (var piece : pieces)
        {
            var cube = piece.cube();
            for (var at = 0; at < 8; at++)
            {
                piece.pose().transformPosition(
                        ((at & 1) == 0 ? cube.minX : cube.maxX) / 16F,
                        ((at & 2) == 0 ? cube.minY : cube.maxY) / 16F,
                        ((at & 4) == 0 ? cube.minZ : cube.maxZ) / 16F, corner);
                min.min(corner);
                max.max(corner);
            }
        }

        var largest = Math.max(max.x - min.x, Math.max(max.y - min.y, max.z - min.z));
        if (largest <= 0)
            return Optional.empty();

        return Optional.of(new Head(texture, List.copyOf(pieces),
                (min.x + max.x) / 2, (min.y + max.y) / 2, (min.z + max.z) / 2, 1 / largest));
    }

    private static ModelPart headOf(Model<?> model)
    {
        if (model instanceof HeadedModel headed)
            return headed.getHead();

        var root = model.root();
        return root.hasChild("head") ? root.getChild("head") : root;
    }
}
