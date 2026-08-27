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
     * Distance reported for a vertex whose cheapest route has no lower bound.
     *
     * Negative infinity is not a stand-in for a very small number here but the
     * literal answer: when a cycle of negative total weight lies on a route to a
     * vertex, going around that cycle once more lowers the total, so no cheapest
     * route exists and no finite number could be reported without being wrong.
     * Choosing this value rather than an exception lets a caller keep the
     * distances that are still meaningful, since a graph may well hold a negative
     * cycle in one corner and perfectly ordinary routes everywhere else.
     */
    public static final double UNBOUNDED = Double.NEGATIVE_INFINITY;

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
     * Whether a cycle of negative total weight lies on a route out of the source.
     *
     * Recorded because its absence is a guarantee the caller needs in order to
     * trust the distances, and its presence is a finding in its own right: it is
     * the answer to questions such as whether a sequence of conversions can be
     * repeated for unbounded gain. It reports only cycles the source can reach,
     * since a cycle in a part of the graph no route leads to affects no distance
     * this instance holds and is not this instance's business to report.
     */
    private final boolean negativeCycleFound;

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

        // Spread that one known distance through the arcs until nothing improves.
        computeShortestPaths();

        // Anything that still improves afterwards can only do so through a
        // negative cycle, which is recorded rather than answered with a number.
        this.negativeCycleFound = markUnboundedVertices();
    }

    /**
     * Offers every arc to the distance estimates once and reports whether any of
     * them improved.
     *
     * Detailed explanation of:
     * - Purpose: Performs a single relaxation round, which is the step the whole
     *   algorithm consists of repeating.
     * - Business context: Relaxing an arc means asking whether reaching its head
     *   by way of its tail would be cheaper than the cheapest route to that head
     *   known so far, and recording the improvement when it would. Sweeping all
     *   arcs blindly, rather than choosing which to relax as the greedy search of
     *   this package does, is exactly what makes negative weights harmless here:
     *   no estimate is ever declared final, so an arc that cheapens a route long
     *   after that route was first found still gets its chance in a later round.
     * - Processing steps: Walks the three arc arrays in step, skipping arcs whose
     *   tail is still unreached, and replaces a head's distance and predecessor
     *   whenever the route through the tail is cheaper.
     * - Assumptions: Assumes the arc arrays are filled and consistent with the
     *   vertex snapshot, which the extraction at construction guarantees.
     * - Side effects: Writes distances and predecessors for every improved vertex.
     *
     * An arc leaving an unreached tail is skipped rather than relaxed. Arithmetic
     * on infinity would give the right answer here, since infinity plus a weight
     * is infinity again and cannot undercut anything, but the skip states the
     * intent directly and keeps the loop from depending on that property.
     *
     * Time complexity: O(a) in the number of arcs; each is examined once and
     * costs an addition and a comparison.
     * Space complexity: O(1); the round works entirely in the existing arrays.
     *
     * @return
     * True when at least one distance improved during this round, which means a
     * further round may still find more; false when the round changed nothing, in
     * which case no later round could change anything either.
     */
    private boolean relaxAllArcs() {
        boolean improved = false;

        for (int arc = 0; arc < arcTails.length; arc++) {
            double distanceToTail = distances[arcTails[arc]];

            // An arc leaving a vertex no route has reached offers no route to its
            // head either.
            if (distanceToTail != UNREACHABLE) {
                // The cost of reaching the head by travelling this arc.
                double offeredDistance = distanceToTail + arcWeights[arc];

                if (offeredDistance < distances[arcHeads[arc]]) {
                    distances[arcHeads[arc]] = offeredDistance;
                    predecessors[arcHeads[arc]] = vertices[arcTails[arc]];
                    improved = true;
                }
            }
        }

        return improved;
    }

    /**
     * Repeats the relaxation until the distances have settled.
     *
     * Detailed explanation of:
     * - Purpose: Turns the initial state, in which only the source has a known
     *   distance, into the final one, in which every reachable vertex holds the
     *   cost of its cheapest route.
     * - Business context: The number of rounds follows from a simple count. A
     *   cheapest route that repeats no vertex has at most as many edges as the
     *   graph has vertices, less one, and after the first round every route of one
     *   edge is accounted for, after the second every route of two, and so on. As
     *   many rounds as vertices less one therefore suffice, whatever order the
     *   arcs happen to be swept in, which is the property that makes this
     *   algorithm indifferent to the structure of the graph in a way the greedy
     *   search is not.
     * - Processing steps: Runs relaxation rounds, stopping after as many rounds as
     *   the count allows or as soon as a round changes nothing.
     * - Assumptions: Assumes that a cheapest route repeats no vertex, which holds
     *   whenever no cycle of negative total weight is reachable. When one is, the
     *   assumption fails by design and the rounds below do not converge; detecting
     *   that condition is the task of the step that follows this one.
     * - Side effects: Writes the distance and predecessor arrays.
     *
     * The early exit is worth more than it looks. The bound of one round fewer
     * than there are vertices is a worst case reached only by a graph laid out
     * adversarially against the sweep order; most graphs settle in a handful of
     * rounds, and stopping as soon as a round changes nothing turns the guaranteed
     * bound into the actual cost.
     *
     * Time complexity: O(v * a) in the worst case, with v vertices and a arcs;
     * O(r * a) in practice, where r is the number of rounds until nothing changes.
     * Space complexity: O(1) beyond the arrays already held.
     */
    private void computeShortestPaths() {
        /*
         * One round fewer than there are vertices. The loop condition is written
         * against the vertex count directly so that the reasoning behind the bound
         * stays visible at the place it is applied.
         */
        for (int round = 0; round + 1 < vertices.length; round++) {
            if (!relaxAllArcs()) {
                // Nothing improved, so nothing can improve any more: a round
                // depends only on the distances it starts from, and those are
                // unchanged.
                break;
            }
        }
    }

    /**
     * Finds the vertices whose distance is driven down without bound by a negative
     * cycle and records them as such.
     *
     * Detailed explanation of:
     * - Purpose: Separates the vertices with a genuine cheapest route from those
     *   that have none, and reports whether any of the latter exist.
     * - Business context: The rounds before this one settle every distance that
     *   can settle. If an arc can still improve a distance afterwards, the route it
     *   improves must use more edges than a route without repeated vertices could,
     *   so it goes around a cycle, and since it improves the total, that cycle has
     *   negative weight. Every vertex behind such an arc can then be made
     *   arbitrarily cheap by going around the cycle again, so it has no cheapest
     *   route and is recorded as unbounded rather than given whichever number the
     *   rounds happened to stop at. The distinction matters to a caller: an
     *   unbounded vertex is reachable, which an unreachable one is not, and the two
     *   would otherwise be indistinguishable from the outside.
     * - Processing steps:
     *   1. Sweep all arcs once more and mark the head of every arc that still
     *      improves. These heads sit on or behind a negative cycle.
     *   2. Spread that mark along the arcs until it stops spreading, which carries
     *      it around the whole cycle and into everything reachable from it.
     *   3. Overwrite the distance of every marked vertex with the unbounded value
     *      and drop its predecessor, since no cheapest route exists to record.
     * - Assumptions: Assumes the relaxation rounds have already run to completion,
     *   so that a further improvement can only come from a cycle rather than from
     *   a route the rounds had not yet reached.
     * - Side effects: Overwrites distances and predecessors of the affected
     *   vertices. Vertices not behind a negative cycle keep the results the rounds
     *   established for them, which is what allows a result to be partly usable.
     *
     * The mark is spread with the same repeated sweep the algorithm uses
     * elsewhere, rather than with a traversal from each affected vertex. The
     * arcs are already at hand in their flat form, the bound is the same, and
     * reusing the shape keeps the class free of a second way of walking the graph.
     *
     * Time complexity: O(v * a) in the worst case, with v vertices and a arcs: one
     * detection sweep plus at most as many spreading sweeps as there are vertices.
     * The spreading stops as soon as a sweep marks nothing new, and is skipped
     * entirely when the detection sweep finds nothing, which is the ordinary case.
     * Space complexity: O(v) for the record of affected vertices, which is
     * discarded when this method returns.
     *
     * @return
     * True when at least one vertex reachable from the source lies on or behind a
     * cycle of negative total weight; false when every reachable vertex has a
     * genuine cheapest route, in which case no distance was changed here.
     */
    private boolean markUnboundedVertices() {
        /*
         * Records which vertices have no lower bound. Kept local because it is
         * scaffolding: once the distances have been overwritten, the unbounded
         * value in the distance array says the same thing.
         */
        boolean[] unbounded = new boolean[vertices.length];
        boolean anyFound = false;

        /*
         * One sweep beyond the rounds. An arc that still improves its head cannot
         * be part of any route without repeated vertices, so it goes around a
         * cycle that lowers the total.
         */
        for (int arc = 0; arc < arcTails.length; arc++) {
            double distanceToTail = distances[arcTails[arc]];

            if (distanceToTail != UNREACHABLE && distanceToTail + arcWeights[arc] < distances[arcHeads[arc]]) {
                unbounded[arcHeads[arc]] = true;
                anyFound = true;
            }
        }

        // Nothing improves any more, so every reachable vertex has a genuine
        // cheapest route and the distances stand as they are.
        if (!anyFound) {
            return false;
        }

        /*
         * Spread the mark forward along the arcs. This carries it around the whole
         * cycle, since a cycle leads back to itself, and onwards into everything
         * that can be reached from it, all of which can be made arbitrarily cheap
         * by looping first.
         */
        for (int round = 0; round < vertices.length; round++) {
            boolean spread = false;

            for (int arc = 0; arc < arcTails.length; arc++) {
                if (unbounded[arcTails[arc]] && !unbounded[arcHeads[arc]]) {
                    unbounded[arcHeads[arc]] = true;
                    spread = true;
                }
            }

            // The mark has reached everything it can; further sweeps would repeat
            // the same comparisons for nothing.
            if (!spread) {
                break;
            }
        }

        /*
         * Replace the meaningless numbers the rounds left behind. The predecessor
         * is dropped as well, because the chain it belongs to runs around the
         * cycle and describes no route a caller could travel.
         */
        for (int index = 0; index < vertices.length; index++) {
            if (unbounded[index]) {
                distances[index] = UNBOUNDED;
                predecessors[index] = null;
            }
        }

        return true;
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

    /**
     * Reports the vertex every distance and route of this result is measured
     * from.
     *
     * Detailed explanation of:
     * - Purpose: Names the source the computation was run from.
     * - Business context: A result is only meaningful together with its source,
     *   and a caller holding one result per source needs to tell them apart
     *   without keeping that association itself. It is also what makes a
     *   reconstructed route recognisable as complete, since a route ends exactly
     *   when it arrives here.
     * - Processing steps: Returns the vertex recorded at construction.
     * - Assumptions: None.
     * - Side effects: None.
     *
     * Time complexity: O(1).
     * Space complexity: O(1).
     *
     * @return
     * The source vertex, which is the instance the graph held at construction.
     * Never null, since the constructor refuses any other source.
     */
    public Vertex getSource() {
        return sourceVertex;
    }

    /**
     * Reports whether a cycle of negative total weight lies on a route out of the
     * source.
     *
     * Detailed explanation of:
     * - Purpose: States whether the graph contains the one structure that makes
     *   the shortest-path question unanswerable for part of it.
     * - Business context: This is the second reason to choose this algorithm over
     *   the greedy search of this package, and for some callers the first: the
     *   presence of such a cycle is itself the finding, for instance when the
     *   weights are gains and losses and a cycle of negative total weight means a
     *   sequence that can be repeated for unbounded profit. It is also the check a
     *   cautious caller makes before trusting any distance, since a graph may hold
     *   such a cycle in one corner and ordinary routes everywhere else.
     * - Processing steps: Returns the flag established during construction.
     * - Assumptions: None.
     * - Side effects: None.
     *
     * Only cycles the source can reach are reported. A negative cycle sitting in a
     * part of the graph no route from the source leads into cannot lower any
     * distance this instance holds, so reporting it would describe the graph
     * rather than this result; a caller wanting that broader question answered
     * runs the algorithm from a vertex reaching everything, or once per component.
     *
     * Time complexity: O(1); the flag is held as a field.
     * Space complexity: O(1).
     *
     * @return
     * True when at least one vertex reachable from the source has no cheapest
     * route because a negative cycle lies before it; false when every reachable
     * vertex has a genuine cheapest route, in which case every reported distance
     * is a finite total over a real route.
     */
    public boolean hasNegativeCycle() {
        return negativeCycleFound;
    }

    /**
     * Reports the total weight of the cheapest route from the source to the
     * specified vertex.
     *
     * Detailed explanation of:
     * - Purpose: Answers the question the computation was run for, for one vertex.
     * - Business context: This is the number a caller compares, sums or ranks, in
     *   whatever unit the edge weights are kept in. Because negative weights are
     *   permitted, it may itself be negative, which is a perfectly ordinary result
     *   and not to be confused with the unbounded value that marks the absence of
     *   a cheapest route.
     * - Processing steps: Locates the vertex in the snapshot and returns its
     *   recorded distance.
     * - Assumptions: Assumes the vertex is compared by identity, so a detached
     *   vertex carrying a familiar identifier is reported as unreachable rather
     *   than answered for.
     * - Side effects: None.
     *
     * Time complexity: O(v) in the number of vertices, for the lookup of the
     * vertex within the snapshot.
     * Space complexity: O(1); nothing is allocated.
     *
     * @param pVertex
     * The vertex to report the distance of. May be null or foreign to the graph
     * the computation ran over, both of which are answered as unreachable.
     *
     * @return
     * The total weight of the cheapest route from the source, which is zero for
     * the source itself and may be negative when negative weights are present;
     * UNREACHABLE when no route leads there, which includes a vertex that was not
     * part of the searched graph; or UNBOUNDED when a negative cycle lies on every
     * route to it, so that no cheapest route exists.
     */
    public double getDistance(Vertex pVertex) {
        int index = indexOf(pVertex);

        // A vertex outside the snapshot has no recorded distance, and the
        // unreachable marker is the truthful answer for it: this result knows no
        // route to it.
        if (index == NO_VERTEX) {
            return UNREACHABLE;
        }

        return distances[index];
    }

    /**
     * Reports whether any route leads from the source to the specified vertex.
     *
     * Detailed explanation of:
     * - Purpose: Answers the existence of a route without the caller having to
     *   interpret a distance.
     * - Business context: Comparing a returned distance against infinity is easy
     *   to write incorrectly, so the test is offered as its own operation. A vertex
     *   behind a negative cycle counts as reachable here, because it genuinely is:
     *   routes to it exist, there is merely no cheapest one among them. That is
     *   the distinction the unbounded value exists to preserve, and a caller
     *   interested in it asks separately.
     * - Processing steps: Compares the recorded distance against the unreachable
     *   marker.
     * - Assumptions: Assumes no genuine route has infinite total weight, which
     *   holds because edge weights are finite and a route has finitely many edges.
     * - Side effects: None.
     *
     * Time complexity: O(v), dominated by the vertex lookup.
     * Space complexity: O(1).
     *
     * @param pVertex
     * The vertex to test. May be null or foreign to the searched graph, both of
     * which are reported as not reachable.
     *
     * @return
     * True when at least one route leads from the source to the vertex, which
     * includes the source itself and every vertex behind a negative cycle; false
     * otherwise.
     */
    public boolean isReachable(Vertex pVertex) {
        return getDistance(pVertex) < UNREACHABLE;
    }

    /**
     * Reports whether the specified vertex has no cheapest route because a
     * negative cycle lies before it.
     *
     * Detailed explanation of:
     * - Purpose: Distinguishes the vertices the result cannot give a number for
     *   from those it can.
     * - Business context: A caller processing distances one vertex at a time needs
     *   this per vertex, not merely the graph-wide answer, since a single negative
     *   cycle usually spoils part of a graph and leaves the rest perfectly usable.
     *   Testing it explicitly is also clearer at a call site than comparing a
     *   distance against negative infinity, and it keeps the meaning of that value
     *   in one place.
     * - Processing steps: Compares the recorded distance against the unbounded
     *   value.
     * - Assumptions: Assumes the detection during construction marked every
     *   affected vertex, which it does by spreading the mark along the arcs until
     *   it stops spreading.
     * - Side effects: None.
     *
     * Time complexity: O(v), dominated by the vertex lookup.
     * Space complexity: O(1).
     *
     * @param pVertex
     * The vertex to test. May be null or foreign to the searched graph, neither of
     * which is unbounded, since neither is reachable in the first place.
     *
     * @return
     * True when routes to the vertex can be made arbitrarily cheap and no
     * cheapest one exists; false for every vertex with a genuine distance and for
     * every vertex no route reaches.
     */
    public boolean isUnbounded(Vertex pVertex) {
        return getDistance(pVertex) == UNBOUNDED;
    }

    /**
     * Reports the vertex immediately before the specified one on its cheapest
     * route from the source.
     *
     * Detailed explanation of:
     * - Purpose: Exposes one link of the tree of cheapest routes.
     * - Business context: The predecessors are how routes are stored at all, one
     *   vertex per vertex rather than one route per vertex, which keeps the result
     *   linear in the size of the graph. This operation serves callers walking the
     *   tree themselves, for instance to find where two routes diverge; callers
     *   wanting a whole route ask for the path instead.
     * - Processing steps: Locates the vertex in the snapshot and returns the
     *   predecessor recorded for it.
     * - Assumptions: Assumes the predecessor chain of a vertex with a finite
     *   distance leads back to the source without repeating a vertex, which holds
     *   because every chain running through a negative cycle was cleared during
     *   the detection.
     * - Side effects: None.
     *
     * Time complexity: O(v), dominated by the vertex lookup.
     * Space complexity: O(1).
     *
     * @param pVertex
     * The vertex whose predecessor is wanted. May be null or foreign to the
     * searched graph, both of which are answered with none.
     *
     * @return
     * The vertex the given one is best reached through, or null when it is the
     * source, when no route reaches it, when its distance is unbounded, or when it
     * was not part of the searched graph. Null therefore means the route does not
     * continue, not that something went wrong.
     */
    public Vertex getPredecessor(Vertex pVertex) {
        int index = indexOf(pVertex);

        if (index == NO_VERTEX) {
            return null;
        }

        return predecessors[index];
    }

    /**
     * Reconstructs the cheapest route from the source to the specified vertex.
     *
     * Detailed explanation of:
     * - Purpose: Turns the predecessor tree into one explicit route, listed in the
     *   order it is travelled.
     * - Business context: The distance answers what a route costs and this answers
     *   which route it is, which is what a caller needs in order to follow it,
     *   display it, or attribute the cost of each step to the arc carrying it.
     *   Routes are reconstructed on demand rather than stored, because storing one
     *   per vertex would cost quadratic space to hold what the predecessors
     *   already imply.
     * - Processing steps:
     *   1. Report an empty route when none exists, which covers an unreachable
     *      vertex, one the searched graph did not contain, and one whose distance
     *      is unbounded.
     *   2. Follow the predecessors from the target backwards, putting each vertex
     *      at the front of the result, until the source is passed.
     * - Assumptions: Assumes the predecessor chain of a vertex with a finite
     *   distance ends at the source, which the computation guarantees: a
     *   predecessor is only recorded when it improves a distance, and every chain
     *   that ran through a negative cycle was cleared during the detection, so the
     *   walk below cannot circle.
     * - Side effects: None on this result or on the graph; a new list is allocated
     *   per call.
     *
     * A vertex whose distance is unbounded is answered with an empty route rather
     * than with the sequence its cleared predecessors would imply, because there is
     * no cheapest route to hand back: for any route offered, going around the
     * negative cycle once more would produce a cheaper one. An empty result
     * therefore means no cheapest route exists, whether because none exists at all
     * or because no cheapest one does, and a caller separating the two cases asks
     * whether the vertex is reachable or unbounded.
     *
     * The route is assembled by inserting at the front rather than by appending
     * and reversing afterwards, since the chain is naturally walked from the
     * target backwards while the caller wants it from the source forwards. The
     * list of this library inserts before its current element, so positioning the
     * cursor at the first element before each insertion prepends.
     *
     * Time complexity: O(k * v) with k as the number of vertices on the route,
     * since each step looks its vertex up in the snapshot to find the next
     * predecessor.
     * Space complexity: O(k) for the returned list.
     *
     * @param pVertex
     * The vertex to reconstruct the route to. May be null or foreign to the
     * searched graph, both of which yield an empty route.
     *
     * @return
     * A new list holding the vertices of a cheapest route in travelling order,
     * beginning with the source and ending with the requested vertex, positioned
     * at its first element. A route to the source itself holds that one vertex.
     * Empty when no cheapest route exists. Never null.
     */
    public SinglyLinkedList<Vertex> getPath(Vertex pVertex) {
        SinglyLinkedList<Vertex> route = new SinglyLinkedList<>();

        /*
         * Only a vertex with a finite distance has a route worth handing back. The
         * single comparison excludes the unreachable vertices, the unknown ones and
         * the unbounded ones at once, since the first two hold positive infinity
         * and the last holds negative infinity.
         */
        double distance = getDistance(pVertex);
        if (distance == UNREACHABLE || distance == UNBOUNDED) {
            return route;
        }

        /*
         * Walk the predecessors backwards, prepending each vertex. The chain ends
         * at the source, whose predecessor is none, so the loop stops there with
         * the source already in the list.
         */
        Vertex step = pVertex;
        while (step != null) {
            // Position at the first element so that the insertion goes in front of
            // it; on an empty list the same call makes this vertex the only one.
            route.toFirst();
            route.insert(step);

            step = getPredecessor(step);
        }

        route.toFirst();
        return route;
    }

}
