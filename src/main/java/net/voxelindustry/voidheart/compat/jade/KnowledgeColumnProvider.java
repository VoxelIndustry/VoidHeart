package net.voxelindustry.voidheart.compat.jade;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.NameAndId;
import net.voxelindustry.voidheart.common.content.research.KnowledgeColumnTile;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import java.util.Optional;
import java.util.UUID;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class KnowledgeColumnProvider implements IServerDataProvider<BlockAccessor>
{
    private static final Identifier KNOWLEDGE_COLUMN = Identifier.fromNamespaceAndPath(MODID, "knowledge_column");

    @Override
    public Identifier getUid()
    {
        return KNOWLEDGE_COLUMN;
    }

    @Override
    public void appendServerData(CompoundTag tag, BlockAccessor accessor)
    {
        if (!(accessor.getBlockEntity() instanceof KnowledgeColumnTile column))
            return;

        tag.putBoolean("linked", column.getLink().isPresent());
        tag.putBoolean("active", column.isActive());
        column.getLink().flatMap(link -> nameOf(accessor.getLevel().getServer(), link.owner())).ifPresent(name -> tag.putString("owner", name));
    }

    /** The owner may be offline, in which case only the server's memory of them knows their name. */
    static Optional<String> nameOf(MinecraftServer server, UUID owner)
    {
        var online = server.getPlayerList().getPlayer(owner);
        if (online != null)
            return Optional.of(online.getGameProfile().name());
        return server.services().nameToIdCache().get(owner).map(NameAndId::name);
    }

    public static class Client implements IBlockComponentProvider
    {
        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config)
        {
            var data = accessor.getServerData();
            if (!data.contains("linked"))
                return;

            if (!data.getBooleanOr("linked", false))
            {
                tooltip.add(Component.translatable(MODID + ".jade.column.unlinked"));
                return;
            }

            boolean active = data.getBooleanOr("active", false);
            if (data.contains("owner"))
                tooltip.add(Component.translatable(MODID + (active ? ".jade.column.linked" : ".jade.column.pending"), data.getStringOr("owner", "")));
            else
                tooltip.add(Component.translatable(MODID + (active ? ".jade.column.linked_unknown" : ".jade.column.pending_unknown")));

            if (!active)
                tooltip.add(Component.translatable(MODID + ".jade.column.unusable").withStyle(ChatFormatting.ITALIC));
        }

        @Override
        public Identifier getUid()
        {
            return KNOWLEDGE_COLUMN;
        }
    }
}
