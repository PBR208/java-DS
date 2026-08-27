package nonLinear.graph.shortestPath;

import nonLinear.graph.base.Edge;
import nonLinear.graph.base.Graph;
import nonLinear.graph.base.Vertex;

import linear.list.SinglyLinkedList;

/**
 * Purpose:
 * Computes the cheapest routes from one vertex of a weighted graph to every other
 * vertex, in the general case that the neighbouring Dijkstra search refuses,
 * namely when edges may carry negative weights. Where that search settles the
 * nearest unsettled vertex and never reconsiders it, this one gives up the idea
 * of settling anything early: it simply offers every arc of the graph to every
 * distance estimate, over and over, until no estimate can be improved any
 * further. That is slower by a factor of the number of vertices, and it is the
 * price of an assumption dropped rather than of a weaker idea. A negative edge
 * makes a route cheaper the further it goes, which destroys the argument the
 * greedy search rests on but leaves this one intact, because nothing here is ever
 * declared final while a round can still change it. The repeated sweeps also
 * answer a question the greedy search cannot even ask: if an estimate still
 * improves after as many rounds as a route can have edges, the graph contains a
 * cycle of negative total weight, and the vertices behind it have no cheapest
 * route at all, since going around the cycle once more is always cheaper.
 * Detecting that condition, rather than looping on it or reporting an arbitrary
 * number, is as much a part of this algorithm as the distances it produces.
 *
 * Owner:
 * PBR208 - https://github.com/PBR208/
 *
 * Version:
 * 1.0
 */

/**
 * Single-source shortest paths over an arbitrarily weighted graph, computed once
 * at construction and queried afterwards.
 *
 * Responsibility: Encapsulates the distance from the source to every vertex, the
 * predecessor each vertex is best reached through, the repeated relaxation that
 * establishes both, the detection of negative cycles, and the reconstruction of a
 * route from the predecessor chain. It maintains the invariant that a reported
 * finite distance is the total weight of a genuine route from the source and that
 * no cheaper route exists, and that every vertex whose distance is unbounded
 * below is reported as such rather than given a number.
 *
 * Scope: Used wherever weights may be negative, which is the case whenever they
 * are gains and losses rather than distances: a sequence of trades or currency
 * conversions, a flow with refunds, a schedule with credits. When every weight is
 * known to be non-negative, the Dijkstra search of this package answers the same
 * question and should be preferred, since it visits each vertex once where this
 * algorithm sweeps the whole graph as many times as there are vertices.
 *
 * Dependencies: Depends on the Graph contract, on Vertex and Edge, and on
 * SinglyLinkedList for the route it hands back. Like the Dijkstra search beside
 * it, it is written against the contract rather than a representation and follows
 * the neighbours a graph reports, so it runs over the adjacency list, the
 * adjacency matrix and the directed graph alike, and over the last of those it
 * follows the arcs in their own direction.
 *
 * Thread-safety: An instance is safe to share between threads once constructed,
 * since everything it holds is written during construction and only read
 * afterwards. The construction itself is not thread-safe, because it reads a
 * graph another thread may be changing.
 *
 * Lifecycle: The whole computation happens in the constructor, so an instance is
 * a finished result for one source rather than a calculator waiting to be
 * started. The graph is read into an internal arc list and then dropped, so the
 * result describes the graph as it stood at construction and cannot be disturbed
 * by later changes to it.
 *
 * Architectural role: Serves as the general counterpart to the Dijkstra search of
 * this package, and is laid out deliberately like it, with the same result state
 * and the same queries, so that the two can be read side by side and the
 * difference between them is confined to how that state is filled. The pair
 * together makes the trade explicit that the choice between the two algorithms
 * always is: an assumption about the weights, bought with a factor of the number
 * of vertices in running time.
 */
public class BellmanFord {

    /**
     * Distance reported for a vertex no route from the source reaches.
     *
     * Positive infinity rather than an agreed sentinel number, for the same reason
     * as in the Dijkstra search of this package: it is the only value that behaves
     * correctly in the comparisons of the relaxation, being larger than every real
     * distance and remaining infinite when an edge weight is added to it, so an
     * unreached vertex can never appear to offer a cheap route onwards.
     */
    public static final double UNREACHABLE = Double.POSITIVE_INFINITY;

    /**
     * Index reported when no vertex satisfies a search.
     *
     * Vertices are addressed by their position in the snapshot below, so no valid
     * index is negative and this value cannot be mistaken for one.
     */
    private static final int NO_VERTEX = -1;

    /**
     * The vertices of the graph, in the order it reported them at construction.
     *
     * Defines the index space that the distances, the predecessors and the two
     * endpoint arrays of the arc list are addressed by. This library holds no map
     * from objects to values, so numbering the vertices once and indexing plain
     * arrays by that number is what keeps the state of the algorithm flat and its
     * inner loop free of lookups.
     */
    private final Vertex[] vertices;

    /**
     * Tail vertex of each arc, as an index into the vertex snapshot.
     *
     * An arc is a connection in the direction it may be travelled, which is what
     * the relaxation needs and what a graph does not directly report: over the
     * undirected representations every edge yields two arcs, one for each
     * direction, and over the directed graph it yields the single arc it already
     * is. The arcs are extracted once because the algorithm sweeps all of them in
     * every round, and asking the graph again each time would multiply the cost of
     * its neighbour and edge queries by the number of rounds.
     */
    private final int[] arcTails;

    /**
     * Head vertex of each arc, as an index into the vertex snapshot.
     *
     * Held in a separate array from the tails rather than in a pair object, since
     * the inner loop of the relaxation reads both by index and nothing else ever
     * needs an arc as a single value.
     */
    private final int[] arcHeads;

    /**
     * Weight of each arc, indexed like the two endpoint arrays.
     *
     * Copied out of the edges once, so that a round consists of arithmetic over
     * three arrays rather than of edge lookups against the graph. The copy is also
     * what makes the result independent of later changes to the edge weights.
     */
    private final double[] arcWeights;

    /**
     * Total weight of the cheapest known route from the source to each vertex.
     *
     * Indexed like the vertex snapshot. Every entry is an upper bound that may
     * still fall while rounds remain, and no entry is final before the last round
     * has passed, which is the whole difference to the greedy search of this
     * package.
     */
    private final double[] distances;

    /**
     * The vertex each vertex is currently best reached through, indexed like the
     * snapshot.
     *
     * Together these entries form the tree of cheapest routes and allow a route to
     * be recovered without storing one per vertex. An entry stays null for the
     * source and for every vertex no route reaches.
     */
    private final Vertex[] predecessors;

    /**
     * The vertex all reported distances and routes start from.
     *
     * Held so that a result can be interpreted without the caller having to
     * remember which source produced it, and so that a reconstructed route is
     * recognisable as complete when it arrives back here.
     */
    private final Vertex sourceVertex;

    /**
     * Computes the cheapest routes from the specified source vertex through the
     * specified graph.
     *
     * Detailed explanation of:
     * - Purpose: Establishes the complete result this instance exists to be
     *   queried for.
     * - Business context: As with the Dijkstra search of this package, the work is
     *   done here rather than in a method that must be called first, so that no
     *   caller can read an estimate that later rounds would still have changed.
     *   Unlike that search, no restriction is placed on the weights: accepting
     *   negative ones is the reason this algorithm exists, and the only input it
     *   cannot answer with a number, a cycle of negative total weight, is reported
     *   as a finding rather than refused.
     * - Processing steps:
     *   1. Reject a null graph and a source that is not a vertex of that graph.
     *   2. Snapshot the vertices, which fixes the index space of the result.
     *   3. Extract every arc of the graph into three parallel arrays.
     *   4. Set every distance to unreachable and every predecessor to none, then
     *      set the distance of the source to zero.
     * - Assumptions: Assumes the graph does not change while the constructor runs
     *   and that its neighbour query and its edge lookup agree with one another.
     * - Side effects: Allocates the result arrays and the arc list. The graph is
     *   read but neither modified nor retained, and its vertex and edge marks are
     *   left untouched, since this algorithm keeps its state in its own arrays and
     *   has no reason to disturb bookkeeping a caller may rely on.
     *
     * Time complexity: O(v * n + e * (v + m)) for the extraction alone, with v
     * vertices, e edges, n as the cost of one neighbour query and m as the cost of
     * one edge lookup in the underlying representation; the relaxation that
     * follows dominates and is documented on the class.
     * Space complexity: O(v + a) with a as the number of arcs, which is e over a
     * directed graph and twice e over an undirected one.
     *
     * @param pGraph
     * The graph to search. Must not be null. May contain edges of negative weight
     * and may contain vertices the source cannot reach; neither is an error.
     *
     * @param pSourceVertex
     * The vertex every reported distance is measured from. Must be the very
     * instance the graph holds, not a detached vertex carrying the same
     * identifier, which would have no neighbours here and would yield a result
     * declaring the whole graph unreachable.
     *
     * @throws IllegalArgumentException
     * Thrown when pGraph is null, or when pSourceVertex is null or is not a vertex
     * of pGraph. Both leave the instance unable to describe anything, and
     * reporting that at construction is more useful than answering every later
     * query with a distance of infinity.
     */
    public BellmanFord(Graph pGraph, Vertex pSourceVertex) {
        // Without a graph there is nothing to search, and every step below would
        // fail on a reference the caller could have checked more cheaply.
        if (pGraph == null) {
            throw new IllegalArgumentException("The graph must not be null.");
        }

        /*
         * The source must be the instance the graph holds. Comparing by identity
         * rather than by identifier rules out a detached vertex that merely
         * carries a matching identifier, which would have no arcs leaving it here
         * and would produce a result declaring the whole graph unreachable.
         */
        if (pSourceVertex == null || pSourceVertex.getID() == null
                || pGraph.getVertex(pSourceVertex.getID()) != pSourceVertex) {
            throw new IllegalArgumentException("The source vertex must be a vertex of the graph.");
        }

        this.sourceVertex = pSourceVertex;
        this.vertices = snapshotVertices(pGraph);

        // The arcs are read out once here; from this point on the algorithm works
        // on its own arrays and never asks the graph again.
        int arcCount = countArcs(pGraph);
        this.arcTails = new int[arcCount];
        this.arcHeads = new int[arcCount];
        this.arcWeights = new double[arcCount];
        extractArcs(pGraph);

        this.distances = new double[vertices.length];
        this.predecessors = new Vertex[vertices.length];

        /*
         * Before any arc has been offered, every vertex is as far away as it can
         * be and is reached through nothing. Starting from infinity is what lets
         * the first route found to a vertex always improve on its estimate.
         */
        for (int index = 0; index < vertices.length; index++) {
            distances[index] = UNREACHABLE;
            predecessors[index] = null;
        }

        // The source is reached from itself at no cost, and this single known
        // value is what every round spreads further into the graph.
        distances[indexOf(pSourceVertex)] = 0.0;
    }

    /**
     * Copies the vertices of the specified graph into an array.
     *
     * Detailed explanation of:
     * - Purpose: Fixes the index space that every other array of this class is
     *   addressed by.
     * - Business context: The result needs a value per vertex and the arcs need to
     *   name their endpoints cheaply, and this library holds no map from objects to
     *   values, so the vertices are numbered by their position here. Taking the
     *   snapshot once also decouples the result from the graph, which is what
     *   allows the graph reference to be dropped after construction.
     * - Processing steps: Walks the vertex list once to count the vertices, then
     *   walks it again to fill an array of exactly that length.
     * - Assumptions: Assumes the graph reports the same vertices in both walks,
     *   which holds because nothing modifies it in between.
     * - Side effects: None on the graph; the list it hands out is already a copy.
     *
     * Two passes are used because the list of this library reports no size and the
     * array must be allocated at its final length; counting first is cheaper than
     * growing an array repeatedly and clearer than guessing a capacity.
     *
     * Time complexity: O(v) in the number of vertices.
     * Space complexity: O(v) for the returned array, which holds references to the
     * graph's own vertex instances rather than copies of them.
     *
     * @param pGraph
     * The graph whose vertices are to be captured. Must not be null, which the
     * caller has already ensured.
     *
     * @return
     * A new array holding every vertex of the graph, in the order the graph
     * reported them. Never null.
     */
    private static Vertex[] snapshotVertices(Graph pGraph) {
        SinglyLinkedList<Vertex> vertexList = pGraph.getVertices();

        // First pass: establish the length, which the list itself does not report.
        int count = 0;
        vertexList.toFirst();
        while (vertexList.hasAccess()) {
            count = count + 1;
            vertexList.next();
        }

        // Second pass: fill the array in the same order, which is the order every
        // index of this class refers to.
        Vertex[] result = new Vertex[count];
        int position = 0;
        vertexList.toFirst();
        while (vertexList.hasAccess()) {
            result[position] = vertexList.getContent();
            position = position + 1;
            vertexList.next();
        }

        return result;
    }

    /**
     * Counts the arcs the specified graph offers.
     *
     * Detailed explanation of:
     * - Purpose: Establishes the length the three arc arrays must be allocated at.
     * - Business context: The number of arcs cannot be derived from the number of
     *   edges without knowing the representation, which this class deliberately
     *   does not: an undirected edge may be travelled in both directions and
     *   therefore counts twice, a directed arc counts once, and only the graph
     *   itself knows which it holds. Counting through the neighbour query rather
     *   than through the edge collection is what keeps that knowledge where it
     *   belongs, at the price of one extra sweep over the neighbours.
     * - Processing steps: Sums, over all vertices, the number of neighbours each
     *   one reports.
     * - Assumptions: Assumes the neighbour query reports one entry per arc leaving
     *   the vertex, which the representations of this package guarantee by
     *   refusing loops and parallel connections.
     * - Side effects: None on the graph content.
     *
     * Time complexity: O(v * n) with n as the cost of one neighbour query, plus
     * O(a) for walking the reported lists.
     * Space complexity: O(1) beyond the neighbour lists the graph allocates and
     * this method discards.
     *
     * @param pGraph
     * The graph to measure. Must not be null, which the caller has already
     * ensured.
     *
     * @return
     * The number of arcs, which is the number of directed connections the graph
     * offers. Never negative, and zero for a graph without edges.
     */
    private int countArcs(Graph pGraph) {
        int count = 0;

        for (int index = 0; index < vertices.length; index++) {
            SinglyLinkedList<Vertex> neighbours = pGraph.getNeighbours(vertices[index]);

            neighbours.toFirst();
            while (neighbours.hasAccess()) {
                count = count + 1;
                neighbours.next();
            }
        }

        return count;
    }

    /**
     * Fills the arc arrays from the specified graph.
     *
     * Detailed explanation of:
     * - Purpose: Turns the graph into the flat list of directed connections the
     *   relaxation sweeps.
     * - Business context: The algorithm offers every arc to every estimate in
     *   every round, so the arcs are read out once and worked on as plain arrays
     *   afterwards. Doing so is not merely an optimisation over asking the graph
     *   repeatedly: it also pins down what an arc means for the representation at
     *   hand, since the direction of travel is taken from the neighbour query and
     *   the weight from the edge leading to that neighbour, which is exactly the
     *   pairing that makes the same code correct over a directed and an undirected
     *   graph.
     * - Processing steps: For every vertex, asks for its neighbours and, for each
     *   of them, for the edge leading there, recording tail, head and weight.
     * - Assumptions: Assumes the arc arrays have been allocated to the counted
     *   length and that the graph reports the same neighbours as it did during the
     *   count. An edge lookup that unexpectedly finds nothing is skipped rather
     *   than recorded with a guessed weight, which leaves a trailing unused entry
     *   in the arrays; such an entry is harmless because it can only arise from an
     *   inconsistent graph and would otherwise have to be invented.
     * - Side effects: Writes all three arc arrays.
     *
     * Time complexity: O(v * n + a * (v + m)) with n as the cost of a neighbour
     * query, m as the cost of an edge lookup and the v per arc as the lookup of the
     * neighbour's position in the vertex snapshot.
     * Space complexity: O(1) beyond the arrays being filled and the neighbour
     * lists the graph allocates.
     *
     * @param pGraph
     * The graph to read. Must not be null, which the caller has already ensured.
     */
    private void extractArcs(Graph pGraph) {
        int arcPosition = 0;

        for (int tailIndex = 0; tailIndex < vertices.length; tailIndex++) {
            Vertex tail = vertices[tailIndex];

            SinglyLinkedList<Vertex> neighbours = pGraph.getNeighbours(tail);
            neighbours.toFirst();
            while (neighbours.hasAccess()) {
                Vertex head = neighbours.getContent();
                int headIndex = indexOf(head);

                /*
                 * Ask for the edge in the direction it is to be travelled. Over an
                 * undirected graph the order of the arguments makes no difference;
                 * over the directed graph it decides whether the arc exists at all,
                 * which is what keeps the relaxation on the arcs rather than
                 * against them.
                 */
                Edge connection = pGraph.getEdge(tail, head);

                // A neighbour outside the snapshot, or a neighbour the graph
                // reports without an edge to it, describes no arc that could be
                // relaxed and is left out rather than recorded with an invented
                // weight.
                if (headIndex != NO_VERTEX && connection != null) {
                    arcTails[arcPosition] = tailIndex;
                    arcHeads[arcPosition] = headIndex;
                    arcWeights[arcPosition] = connection.getWeight();
                    arcPosition = arcPosition + 1;
                }
                neighbours.next();
            }
        }
    }

    /**
     * Reports the position of the specified vertex within the snapshot.
     *
     * Detailed explanation of:
     * - Purpose: Translates a vertex into the index its distance and predecessor
     *   are stored under.
     * - Business context: Every query of this class begins here, and so does the
     *   extraction of the arcs. The lookup is a linear scan because the snapshot is
     *   a plain array and this library provides no hash-based lookup; the cost is
     *   documented on each operation rather than hidden. It is notably absent from
     *   the relaxation itself, which works on the indices the extraction has
     *   already resolved.
     * - Processing steps: Scans the snapshot and returns the position of the
     *   matching vertex.
     * - Assumptions: Assumes vertices are compared by identity, as everywhere in
     *   this package, so a detached vertex carrying a familiar identifier is
     *   correctly reported as unknown.
     * - Side effects: None.
     *
     * Time complexity: O(v) in the number of vertices.
     * Space complexity: O(1); nothing is allocated.
     *
     * @param pVertex
     * The vertex to locate. May be null or foreign to the graph, both of which are
     * reported as absent.
     *
     * @return
     * The position of the vertex in the snapshot, or NO_VERTEX when the snapshot
     * does not contain it.
     */
    private int indexOf(Vertex pVertex) {
        for (int index = 0; index < vertices.length; index++) {
            if (vertices[index] == pVertex) {
                return index;
            }
        }

        return NO_VERTEX;
    }

}
