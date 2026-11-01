package net.voxelindustry.voidheart.common.content.research;

import net.fabricmc.fabric.api.event.registry.DynamicRegistries;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.voxelindustry.voidheart.common.config.VoidHeartConfig;
import net.voxelindustry.voidheart.common.setup.VoidHeartTags;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Ties the knowledge of players to the running server: settles it as time passes, feeds it, and answers whether a recipe is still locked.
 */
public class Knowledge
{
    public static final ResourceKey<Registry<Research>> REGISTRY = ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath(MODID, "research"));

    private static final Map<TagKey<Item>, Double> ITEM_VALUES = Map.of(
            VoidHeartTags.KNOWLEDGE_FAINT, 2D,
            VoidHeartTags.KNOWLEDGE_LUCID, 5D,
            VoidHeartTags.KNOWLEDGE_PROFOUND, 15D,
            VoidHeartTags.KNOWLEDGE_TRANSCENDENT, 40D
    );

    private static final int SETTLE_INTERVAL = 20;

    public static void register()
    {
        DynamicRegistries.registerSynced(REGISTRY, Research.CODEC);
    }

    public static KnowledgeRules rules()
    {
        var config = VoidHeartConfig.get();
        return new KnowledgeRules(config.knowledgeBaseRate, config.knowledgeBaseCap, config.insightBaseCost, config.insightCostGrowth);
    }

    public static Map<Identifier, Research> tree(RegistryAccess registries)
    {
        var tree = new HashMap<Identifier, Research>();
        registries.lookupOrThrow(REGISTRY).listElements().forEach(holder -> tree.put(holder.key().identifier(), holder.value()));
        return tree;
    }

    public static PlayerKnowledge of(MinecraftServer server, UUID player)
    {
        var state = KnowledgeState.get(server);
        var data = state.of(player);
        var finished = data.settle(server.overworld().getGameTime(), rules(), tree(server.registryAccess()));
        state.setDirty();

        var online = server.getPlayerList().getPlayer(player);
        if (online != null)
            finished.ifPresent(id -> announceFinished(online, id));
        return data;
    }

    private static void announceFinished(ServerPlayer player, Identifier research)
    {
        player.sendSystemMessage(Component.translatable(MODID + ".research.finished", Component.translatable(Research.nameKey(research))));
        player.level().playSound(null, player.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.5F, 1.2F);
    }

    public static void tickServer(MinecraftServer server)
    {
        if (server.getTickCount() % SETTLE_INTERVAL != 0)
            return;

        for (var player : server.getPlayerList().getPlayers())
        {
            of(server, player.getUUID());
            if (player.containerMenu instanceof KnowledgeRepositoryMenu)
                sendSnapshot(player);
        }
    }

    public static void sendSnapshot(ServerPlayer player)
    {
        var server = player.level().getServer();
        var data = of(server, player.getUUID());
        var tree = tree(server.registryAccess());
        var rules = rules();

        ServerPlayNetworking.send(player, new KnowledgePayloads.Snapshot(
                data.getKnowledge(),
                data.cap(rules, tree),
                data.rate(rules, tree),
                data.getInsights(),
                rules.insightCost(data.getInsightsBought()),
                data.getActive(),
                new HashMap<>(data.getProgress()),
                List.copyOf(data.getRevealed()),
                List.copyOf(data.getCompleted()),
                new HashMap<>(data.getFed())));
    }

    public static void bind(MinecraftServer server, UUID player, GlobalPos repository)
    {
        // Settled first, so that the time spent unbound does not count as time at the new rate
        of(server, player).bind(Optional.of(repository));
        KnowledgeState.get(server).setDirty();
    }

    public static void unbind(MinecraftServer server, UUID player, GlobalPos repository)
    {
        var data = of(server, player);
        if (data.getRepository().filter(repository::equals).isPresent())
        {
            data.bind(Optional.empty());
            KnowledgeState.get(server).setDirty();
        }
    }

    public static double valueOf(ItemStack stack)
    {
        return ITEM_VALUES.entrySet().stream()
                .filter(entry -> stack.is(entry.getKey()))
                .mapToDouble(Map.Entry::getValue)
                .max()
                .orElse(0);
    }

    /**
     * Feeds a single item of the held stack, which is left alone when it would be wasted.
     */
    public static void feed(ServerPlayer player, ItemStack stack)
    {
        var server = player.level().getServer();
        var data = of(server, player.getUUID());
        var item = BuiltInRegistries.ITEM.getKey(stack.getItem());
        double value = valueOf(stack);
        double before = data.getKnowledge();

        switch (data.feed(item, value, data.cap(rules(), tree(server.registryAccess()))))
        {
            case EXHAUSTED -> player.sendOverlayMessage(Component.translatable(MODID + ".knowledge.exhausted", stack.getHoverName()));
            case FULL -> player.sendOverlayMessage(Component.translatable(MODID + ".knowledge.full"));
            case ACCEPTED ->
            {
                player.sendOverlayMessage(Component.translatable(MODID + ".knowledge.gained", String.format("%.1f", data.getKnowledge() - before), stack.getHoverName()));
                stack.consume(1, player);
                KnowledgeState.get(server).setDirty();

                var level = player.level();
                level.playSound(null, player.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.8F, 1.3F);
                level.sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1, player.getZ(), 12, 0.4, 0.4, 0.4, 0.5);
            }
        }
    }

    /**
     * The research a recipe waits for. Recipes no research names stay free.
     */
    public static Optional<Identifier> lockOf(RegistryAccess registries, Identifier recipe)
    {
        if (!VoidHeartConfig.get().researchGating)
            return Optional.empty();

        return tree(registries).entrySet().stream()
                .filter(entry -> entry.getValue().recipes().contains(recipe))
                .map(Map.Entry::getKey)
                .sorted()
                .findFirst();
    }

    /**
     * Whether the player may use a recipe, telling them what is missing when not.
     */
    public static boolean canUse(ServerPlayer player, Identifier recipe)
    {
        var server = player.level().getServer();
        var lock = lockOf(server.registryAccess(), recipe);
        if (lock.isEmpty() || of(server, player.getUUID()).getCompleted().contains(lock.get()))
            return true;

        player.sendOverlayMessage(Component.translatable(MODID + ".research.locked", Component.translatable(Research.nameKey(lock.get()))));
        return false;
    }

    /**
     * For crafting nobody in particular asked for, such as a crafter fed by hoppers: it never knows a locked recipe.
     */
    public static boolean isLocked(ServerLevel level, Identifier recipe)
    {
        return lockOf(level.registryAccess(), recipe).isPresent();
    }
}
