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
 * making it, what goes into what, and the essence each kind's crafts drink.
 *
 * @param drinks the essence each made kind's crafts drink, by node
 */
public record WorksGraph(List<Node> nodes, List<Edge> edges, Map<String, Long> essence, List<Drink> drinks)
{
    public static final WorksGraph EMPTY = new WorksGraph(List.of(), List.of(), Map.of(), List.of());

    /**
     * @param aspect the aspect's id
     */
    public record Drink(int node, String aspect, long amount)
    {
        static final Codec<Drink> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("node").forGetter(Drink::node),
                Codec.STRING.fieldOf("aspect").forGetter(Drink::aspect),
                Codec.LONG.fieldOf("amount").forGetter(Drink::amount)
        ).apply(instance, Drink::new));

        static final StreamCodec<RegistryFriendlyByteBuf, Drink> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Drink::node,
                ByteBufCodecs.STRING_UTF8, Drink::aspect,
                ByteBufCodecs.VAR_LONG, Drink::amount,
                Drink::new);
    }

    /**
     * The graph with each drink as a leaf of its own after the kinds, feeding the kind that drinks it, for the works view to lay out.
     *
     * @param plan the nodes and the drink leaves, the drinks numbered from {@code nodes().size()}
     */
    public record Display(WorksPlan<Integer> plan, List<Drink> leaves)
    {
    }

    /**
     * @param recipe  the recipe making it, empty for a kind taken from the hoard
     * @param station the station kind's ordinal, -1 for a kind taken from the hoard
     * @param repack  whether its recipe only packs nine of one kind into one or unpacks one into nine, like ingots from a block or nuggets
     */
    public record Node(ItemVariant item, long needed, long fromStock, long crafts, String recipe, int station, long lacking, boolean repack)
    {
        static final Codec<Node> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ItemVariant.CODEC.fieldOf("item").forGetter(Node::item),
                Codec.LONG.fieldOf("needed").forGetter(Node::needed),
                Codec.LONG.fieldOf("from_stock").forGetter(Node::fromStock),
                Codec.LONG.fieldOf("crafts").forGetter(Node::crafts),
                Codec.STRING.fieldOf("recipe").forGetter(Node::recipe),
                Codec.INT.fieldOf("station").forGetter(Node::station),
                Codec.LONG.fieldOf("lacking").forGetter(Node::lacking),
                Codec.BOOL.optionalFieldOf("repack", false).forGetter(Node::repack)
        ).apply(instance, Node::new));

        static final StreamCodec<RegistryFriendlyByteBuf, Node> STREAM_CODEC = StreamCodec.composite(
                ItemVariant.PACKET_CODEC, Node::item,
                ByteBufCodecs.VAR_LONG, Node::needed,
                ByteBufCodecs.VAR_LONG, Node::fromStock,
                ByteBufCodecs.VAR_LONG, Node::crafts,
                ByteBufCodecs.STRING_UTF8, Node::recipe,
                ByteBufCodecs.VAR_INT, Node::station,
                ByteBufCodecs.VAR_LONG, Node::lacking,
                ByteBufCodecs.BOOL, Node::repack,
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
            Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("essence", Map.of()).forGetter(WorksGraph::essence),
            Drink.CODEC.listOf().optionalFieldOf("drinks", List.of()).forGetter(WorksGraph::drinks)
    ).apply(instance, WorksGraph::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, WorksGraph> STREAM_CODEC = StreamCodec.composite(
            Node.STREAM_CODEC.apply(ByteBufCodecs.list()), WorksGraph::nodes,
            Edge.STREAM_CODEC.apply(ByteBufCodecs.list()), WorksGraph::edges,
            ByteBufCodecs.map(LinkedHashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_LONG), graph -> new LinkedHashMap<>(graph.essence()),
            Drink.STREAM_CODEC.apply(ByteBufCodecs.list()), WorksGraph::drinks,
            WorksGraph::new);

    public WorksGraph
    {
        nodes = List.copyOf(nodes);
        edges = List.copyOf(edges);
        essence = Map.copyOf(essence);
        drinks = List.copyOf(drinks);
    }

    public static WorksGraph of(WorksPlan<ItemVariant> plan)
    {
        var index = new LinkedHashMap<ItemVariant, Integer>();
        var nodes = new ArrayList<Node>();
        plan.nodes().forEach((item, node) ->
        {
            index.put(item, nodes.size());
            nodes.add(new Node(item, node.needed(), node.fromStock(), node.crafts(), node.isMade() ? node.recipe().id() : "",
                    node.isMade() ? node.recipe().station().ordinal() : -1, plan.lacking().getOrDefault(item, 0L), node.isMade() && isRepack(node.recipe())));
        });
        var edges = plan.edges().stream().map(edge -> new Edge(index.get(edge.product()), index.get(edge.ingredient()), edge.count())).toList();
        var drinks = new ArrayList<Drink>();
        plan.nodes().forEach((item, node) ->
        {
            if (node.isMade())
                node.recipe().essence().forEach((aspect, perCraft) -> drinks.add(new Drink(index.get(item), aspect, (long) perCraft * node.crafts())));
        });
        return new WorksGraph(nodes, edges, plan.essence(), drinks);
    }

    /** How many of one kind a packing recipe puts into one of another, or one gives back. */
    private static final int PACKED = 9;

    /**
     * @return whether a recipe only packs nine of one kind into one, or unpacks one into nine, like ingots and their block or their nuggets
     */
    static boolean isRepack(WorksRecipe<ItemVariant> recipe)
    {
        if (recipe.station() != StationKind.TABLE || recipe.inputs().size() != 1 || recipe.outputs().size() != 1 || recipe.inputs().getFirst().options().size() != 1)
            return false;
        var in = recipe.inputs().getFirst().count();
        var out = recipe.outputs().getFirst().count();
        return in == 1 && out == PACKED || in == PACKED && out == 1;
    }

    /**
     * The graph as the works view shows it. A kind repacked from another the hoard holds none of is shown as the kind itself, wanted from the hoard,
     * with what lay behind it left out: players bring ingots rather than blocks or nuggets, and the packed form only shows once the hoard holds some.
     * The plan itself is unchanged, so packed forms brought later are still used.
     */
    public WorksGraph trimmed()
    {
        var collapsed = new java.util.HashSet<Integer>();
        for (int index = 1; index < nodes.size(); index++)
        {
            if (!nodes.get(index).repack())
                continue;
            int product = index;
            var ingredients = edges.stream().filter(edge -> edge.product() == product).map(edge -> nodes.get(edge.ingredient())).toList();
            if (!ingredients.isEmpty() && ingredients.stream().allMatch(ingredient -> !ingredient.isMade() && ingredient.fromStock() == 0))
                collapsed.add(index);
        }
        if (collapsed.isEmpty())
            return this;

        // What is still reached from the target without passing behind a collapsed kind
        var kept = new java.util.TreeSet<Integer>();
        var open = new java.util.ArrayDeque<Integer>();
        if (!nodes.isEmpty())
        {
            kept.add(0);
            open.add(0);
        }
        while (!open.isEmpty())
        {
            int node = open.poll();
            if (collapsed.contains(node))
                continue;
            for (var edge : edges)
                if (edge.product() == node && kept.add(edge.ingredient()))
                    open.add(edge.ingredient());
        }

        var index = new java.util.HashMap<Integer, Integer>();
        var keptNodes = new ArrayList<Node>();
        for (int node : kept)
        {
            index.put(node, keptNodes.size());
            var original = nodes.get(node);
            keptNodes.add(collapsed.contains(node)
                    ? new Node(original.item(), original.needed(), original.fromStock(), 0, "", -1, original.needed() - original.fromStock(), false)
                    : original);
        }
        var keptEdges = edges.stream().filter(edge -> kept.contains(edge.product()) && !collapsed.contains(edge.product()) && kept.contains(edge.ingredient()))
                .map(edge -> new Edge(index.get(edge.product()), index.get(edge.ingredient()), edge.count())).toList();
        var keptDrinks = drinks.stream().filter(drink -> kept.contains(drink.node()) && !collapsed.contains(drink.node()))
                .map(drink -> new Drink(index.get(drink.node()), drink.aspect(), drink.amount())).toList();
        return new WorksGraph(keptNodes, keptEdges, essence, keptDrinks);
    }

    /**
     * @return the graph with its drinks as leaves, for the works view
     */
    public Display display()
    {
        var plan = asPlan();
        var planNodes = new LinkedHashMap<>(plan.nodes());
        var planEdges = new ArrayList<>(plan.edges());
        for (int leaf = 0; leaf < drinks.size(); leaf++)
        {
            var drink = drinks.get(leaf);
            int id = nodes.size() + leaf;
            planNodes.put(id, new WorksPlan.Node<>(id, drink.amount(), 0, 0, 0, null));
            planEdges.add(new WorksPlan.Edge<>(drink.node(), id, drink.amount()));
        }
        return new Display(new WorksPlan<>(0, plan.count(), planNodes, planEdges, Map.of(), Map.of(), List.of()), drinks);
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
