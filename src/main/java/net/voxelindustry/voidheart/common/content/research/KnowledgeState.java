package net.voxelindustry.voidheart.common.content.research;

import com.mojang.serialization.Codec;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * The knowledge of every player, kept outside of them so it can be worked on while they are away.
 */
public class KnowledgeState extends SavedData
{
    public static final Codec<KnowledgeState> CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC, PlayerKnowledge.CODEC)
            .fieldOf("players")
            .codec()
            .xmap(KnowledgeState::new, state -> state.players);

    public static final SavedDataType<KnowledgeState> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(MODID, "knowledge"),
            KnowledgeState::new,
            CODEC,
            null);

    private final Map<UUID, PlayerKnowledge> players = new HashMap<>();

    public KnowledgeState()
    {
    }

    private KnowledgeState(Map<UUID, PlayerKnowledge> players)
    {
        this.players.putAll(players);
    }

    public PlayerKnowledge of(UUID player)
    {
        return players.computeIfAbsent(player, id -> new PlayerKnowledge());
    }

    public static KnowledgeState get(MinecraftServer server)
    {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }
}
