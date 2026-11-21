package net.voxelindustry.voidheart.common.content.works;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A plan as the works view draws it, kept with an order so its progress can be shown: each kind with how many it needs and crafts and the recipe
 * making it, and what goes into what.
 *
 * @param recipe  the recipe making it, empty for a kind taken from the hoard
 * @param station the station kind's ordinal, -1 for a kind taken from the hoard
 */
public record WorksGraph(List<Node> nodes, List<Edge> edges, Map<String, Long> essence)
{
    public record Node(ItemVariant item, long needed, long fromStock, long crafts, String recipe, int station, long lacking)
    {
        static final Codec<Node> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ItemVariant.CODEC.fieldOf("item").forGetter(Node::item),
                Codec.LONG.fieldOf("needed").forGetter(Node::needed),
                Codec.LONG.fieldOf("from_stock").forGetter(Node::fromStock),
                Codec.LONG.fieldOf("crafts").forGetter(Node::crafts),
                Codec.STRING.fieldOf("recipe").forGetter(Node::recipe),
                Codec.INT.fieldOf("station").forGetter(Node::station),
                Codec.LONG.fieldOf("lacking").forGetter(Node::lacking)
        ).apply(instance, Node::new));

        static final StreamCodec<RegistryFriendlyByteBuf, Node> STREAM_CODEC = StreamCodec.composite(
                ItemVariant.PACKET_CODEC, Node::item,
                ByteBufCodecs.VAR_LONG, Node::needed,
                ByteBufCodecs.VAR_LONG, Node::fromStock,
                ByteBufCodecs.VAR_LONG, Node::crafts,
                ByteBufCodecs.STRING_UTF8, Node::recipe,
                ByteBufCodecs.VAR_INT, Node::station,
                ByteBufCodecs.VAR_LONG, Node::lacking,
                Node::new);

        public boolean isMade()
        {
            return station >= 0;
        }
    }

    public record Edge(int product, int ingredient, long count)
    {
        static final Codec<Edge> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("product").forGetter(Edge::product),
                Codec.INT.fieldOf("ingredient").forGetter(Edge::ingredient),
                Codec.LONG.fieldOf("count").forGetter(Edge::count)
        ).apply(instance, Edge::new));

        static final StreamCodec<RegistryFriendlyByteBuf, Edge> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Edge::product,
                ByteBufCodecs.VAR_INT, Edge::ingredient,
                ByteBufCodecs.VAR_LONG, Edge::count,
                Edge::new);
    }

    public static final Codec<WorksGraph> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Node.CODEC.listOf().fieldOf("nodes").forGetter(WorksGraph::nodes),
            Edge.CODEC.listOf().fieldOf("edges").forGetter(WorksGraph::edges),
            Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("essence", Map.of()).forGetter(WorksGraph::essence)
    ).apply(instance, WorksGraph::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, WorksGraph> STREAM_CODEC = StreamCodec.composite(
            Node.STREAM_CODEC.apply(ByteBufCodecs.list()), WorksGraph::nodes,
            Edge.STREAM_CODEC.apply(ByteBufCodecs.list()), WorksGraph::edges,
            ByteBufCodecs.map(LinkedHashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_LONG), graph -> new LinkedHashMap<>(graph.essence()),
            WorksGraph::new);

    public WorksGraph
    {
        nodes = List.copyOf(nodes);
        edges = List.copyOf(edges);
        essence = Map.copyOf(essence);
    }

    public static WorksGraph of(WorksPlan<ItemVariant> plan)
    {
        var index = new LinkedHashMap<ItemVariant, Integer>();
        var nodes = new ArrayList<Node>();
        plan.nodes().forEach((item, node) ->
        {
            index.put(item, nodes.size());
            nodes.add(new Node(item, node.needed(), node.fromStock(), node.crafts(), node.isMade() ? node.recipe().id() : "",
                    node.isMade() ? node.recipe().station().ordinal() : -1, plan.lacking().getOrDefault(item, 0L)));
        });
        var edges = plan.edges().stream().map(edge -> new Edge(index.get(edge.product()), index.get(edge.ingredient()), edge.count())).toList();
        return new WorksGraph(nodes, edges, plan.essence());
    }

    /**
     * @return the graph as a plan the layout reads, its first node the target
     */
    public WorksPlan<Integer> asPlan()
    {
        var planNodes = new LinkedHashMap<Integer, WorksPlan.Node<Integer>>();
        for (int i = 0; i < nodes.size(); i++)
            planNodes.put(i, new WorksPlan.Node<>(i, nodes.get(i).needed(), nodes.get(i).fromStock(), 0, nodes.get(i).crafts(), null));
        var planEdges = edges.stream().map(edge -> new WorksPlan.Edge<>(edge.product(), edge.ingredient(), edge.count())).toList();
        return new WorksPlan<>(0, nodes.isEmpty() ? 0 : nodes.getFirst().needed(), planNodes, planEdges, Map.of(), Map.of(), List.of());
    }

    public boolean isComplete()
    {
        return nodes.stream().allMatch(node -> node.lacking() == 0);
    }
}
