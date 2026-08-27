package nonLinear.graph.shortestPath;

import nonLinear.graph.base.Edge;
import nonLinear.graph.base.Graph;
import nonLinear.graph.base.Vertex;

import linear.list.SinglyLinkedList;

/**
 * Purpose:
 * Computes the cheapest route between every pair of vertices of a weighted graph
 * at once, rather than from one source outwards as the two single-source searches
 * of this package do. The problem is not merely those searches repeated: running
 * one of them per vertex would answer the same question, and for sparse graphs
 * that is indeed the better plan, but it would also rebuild the same partial
 * routes over and over, once for every source that happens to pass through them.
 * This algorithm avoids that by asking a different question entirely. Instead of
 * growing routes outwards from a source, it considers the vertices one at a time
 * and asks, for every pair, whether being allowed to travel through the vertex
 * just admitted makes the route between them cheaper. After every vertex has been
 * admitted, every route that could exist has been considered, and the table holds
 * the answer for all pairs together. The reformulation is what makes the method
 * short enough to fit in three nested loops with no auxiliary structure at all,
 * and it is also what makes it indifferent to negative weights, since it never
 * declares anything final before the last vertex has been admitted.
 *
 * Owner:
 * PBR208 - https://github.com/PBR208/
 *
 * Version:
 * 1.0
 */

/**
 * All-pairs shortest paths over an arbitrarily weighted graph, computed once at
 * construction and queried afterwards.
 *
 * Responsibility: Encapsulates the distance between every ordered pair of
 * vertices, the first step of the cheapest route for each pair, the successive
 * admission of intermediate vertices that establishes both, the detection of
 * negative cycles, and the reconstruction of a route from the first-step table.
 * It maintains the invariant that a reported finite distance is the total weight
 * of a genuine route between the two vertices and that no cheaper route exists,
 * and that every pair whose distance has no lower bound is reported as such
 * rather than given a number.
 *
 * Scope: Used when the routes between many or all pairs are wanted, for instance
 * to find the two most distant vertices of a network, to fill a distance table
 * that is queried far more often than the graph changes, or to answer
 * reachability between arbitrary pairs. When only one source is of interest, the
 * Dijkstra search of this package is the better answer for non-negative weights
 * and the Bellman-Ford algorithm for arbitrary ones, both of which do markedly
 * less work than this table costs.
 *
 * Dependencies: Depends on the Graph contract, on Vertex and Edge, and on
 * SinglyLinkedList for the route it hands back. Like the two single-source
 * algorithms beside it, it is written against the contract rather than a
 * representation and follows the neighbours a graph reports, so it runs over the
 * adjacency list, the adjacency matrix and the directed graph alike, and over the
 * last of those it follows the arcs in their own direction, which is what makes
 * the resulting table asymmetric there.
 *
 * Thread-safety: An instance is safe to share between threads once constructed,
 * since everything it holds is written during construction and only read
 * afterwards. The construction itself is not thread-safe, because it reads a
 * graph another thread may be changing.
 *
 * Lifecycle: The whole computation happens in the constructor, so an instance is
 * a finished table rather than a calculator waiting to be started. The graph is
 * read into the initial table and then dropped, so the result describes the graph
 * as it stood at construction and cannot be disturbed by later changes to it.
 *
 * Architectural role: Completes the shortest-path package as the all-pairs
 * counterpart to its two single-source algorithms, and stands apart from both in
 * how it stores its answer: a table of quadratic size rather than one value per
 * vertex, which is simultaneously the reason it can answer any pair immediately
 * and the reason it cannot be afforded on a large graph.
 */
public class FloydWarshall {

    /**
     * Distance reported for a pair no route connects.
     *
     * Positive infinity rather than an agreed sentinel number, for the same reason
     * as in the single-source algorithms of this package: it is the only value
     * that behaves correctly in the comparisons of the improvement step, being
     * larger than every real distance and remaining infinite when a weight is
     * added to it, so a pair that is not connected can never appear to offer a
     * cheap route through itself.
     */
    public static final double UNREACHABLE = Double.POSITIVE_INFINITY;

    /**
     * Distance reported for a pair whose cheapest route has no lower bound.
     *
     * As in the Bellman-Ford algorithm of this package, negative infinity is the
     * literal answer rather than a stand-in for a very small number: when a cycle
     * of negative total weight can be reached on the way from one vertex to
     * another, going around it once more lowers the total, so no cheapest route
     * exists. Reporting it per pair rather than refusing the whole table is what
     * keeps the many pairs unaffected by such a cycle usable.
     */
    public static final double UNBOUNDED = Double.NEGATIVE_INFINITY;

    /**
     * Index reported when no vertex satisfies a search, and stored in the
     * first-step table where no route exists.
     *
     * Vertices are addressed by their position in the snapshot below, so no valid
     * index is negative and this value cannot be mistaken for one.
     */
    private static final int NO_VERTEX = -1;

    /**
     * The vertices of the graph, in the order it reported them at construction.
     *
     * Defines the index space both tables are addressed by, in each of their two
     * dimensions. This library holds no map from objects to values, so numbering
     * the vertices once and indexing plain arrays by that number is what keeps the
     * triple loop of the computation free of lookups entirely.
     */
    private final Vertex[] vertices;

    /**
     * Total weight of the cheapest known route from each vertex to each other,
     * indexed by the position of the origin and then of the destination.
     *
     * The first index is where a route starts and the second where it ends, an
     * order that matters over a directed graph, where the table is generally not
     * symmetric. During the computation an entry is the cheapest route using only
     * the intermediate vertices admitted so far, which is why no entry may be read
     * before the last vertex has been admitted; afterwards every entry is either a
     * true distance, UNREACHABLE, or UNBOUNDED. The diagonal starts at zero, since
     * a vertex is reached from itself at no cost, and only a negative cycle
     * through a vertex can push its own entry below that.
     */
    private final double[][] distances;

    /**
     * The vertex to move to first when travelling from each vertex to each other,
     * as an index into the snapshot and indexed like the distance table.
     *
     * A route is stored as one step per pair rather than as a sequence, which is
     * what keeps the result quadratic rather than cubic in size: following the
     * first steps from the origin, each time asking the table again from the
     * vertex just reached, reproduces the whole route. The entry is NO_VERTEX
     * wherever no route exists.
     */
    private final int[][] firstSteps;

    /**
     * Whether the graph contains a cycle of negative total weight.
     *
     * Recorded because its absence is the guarantee a caller needs in order to
     * trust the table, and its presence is a finding in its own right. Unlike the
     * single-source algorithms of this package, this one sees the whole graph
     * rather than the part one source can reach, so the answer here is about the
     * graph itself: a negative cycle in a corner nothing else connects to is
     * reported, even though it spoils no entry beyond its own neighbourhood.
     */
    private final boolean negativeCycleFound;

    /**
     * Computes the cheapest routes between all pairs of vertices of the specified
     * graph.
     *
     * Detailed explanation of:
     * - Purpose: Establishes the complete table this instance exists to be queried
     *   for.
     * - Business context: As with the single-source algorithms of this package,
     *   the work is done here rather than in a method that must be called first,
     *   so that no caller can read an entry that later steps would still have
     *   changed. That matters more here than elsewhere: an entry of this table is
     *   meaningless until every vertex has been admitted as a possible
     *   intermediate, so a partially computed table is not merely incomplete but
     *   actively misleading.
     * - Processing steps:
     *   1. Reject a null graph.
     *   2. Snapshot the vertices, which fixes the index space of both tables.
     *   3. Allocate the tables and fill them from the graph: infinity everywhere,
     *      zero on the diagonal, and the weight of each arc in its own entry.
     * - Assumptions: Assumes the graph does not change while the constructor runs
     *   and that its neighbour query and its edge lookup agree with one another.
     * - Side effects: Allocates two tables of quadratic size. The graph is read but
     *   neither modified nor retained, and its vertex and edge marks are left
     *   untouched, since this algorithm keeps its state in its own tables.
     *
     * An empty graph is accepted and yields an empty table, which answers every
     * query with unreachable. That is deliberate: a caller building a table over
     * whatever a data set produced should not have to special-case the empty case,
     * and unlike the single-source algorithms this one needs no starting vertex
     * that would have to exist.
     *
     * Time complexity: O(v * v + v * n + e * (v + m)) for the initialisation, with
     * v vertices, e edges, n as the cost of one neighbour query and m as the cost
     * of one edge lookup; the improvement step that follows dominates and is
     * documented on the class.
     * Space complexity: O(v * v) for the two tables, which is the defining cost of
     * this algorithm and the reason it is not the default answer for a single
     * source.
     *
     * @param pGraph
     * The graph to compute the table for. Must not be null. May contain edges of
     * negative weight, may consist of several unconnected parts, and may be empty;
     * none of these is an error.
     *
     * @throws IllegalArgumentException
     * Thrown when pGraph is null, which leaves the instance unable to describe
     * anything. Reporting that at construction is more useful than answering every
     * later query with a distance of infinity.
     */
    public FloydWarshall(Graph pGraph) {
        // Without a graph there is nothing to tabulate, and every step below would
        // fail on a reference the caller could have checked more cheaply.
        if (pGraph == null) {
            throw new IllegalArgumentException("The graph must not be null.");
        }

        this.vertices = snapshotVertices(pGraph);
        this.distances = new double[vertices.length][vertices.length];
        this.firstSteps = new int[vertices.length][vertices.length];

        // Read the graph into the tables; from this point on the algorithm works
        // on its own arrays and never asks the graph again.
        initialiseTables(pGraph);

        // Widen those direct routes into the cheapest routes over any number of
        // intermediate vertices.
        admitIntermediateVertices();

        // Replace the entries that a negative cycle robs of any meaning, and
        // record whether there was one at all.
        this.negativeCycleFound = markUnboundedPairs();
    }

    /**
     * Admits each vertex in turn as a permitted intermediate stop and improves
     * every pair that can profit from it.
     *
     * Detailed explanation of:
     * - Purpose: Turns the table of direct routes into the table of cheapest
     *   routes.
     * - Business context: This is the algorithm itself, and its correctness rests
     *   on a statement about the table rather than about any single route. After
     *   the first k vertices have been admitted, each entry holds the cheapest
     *   route between its pair that stops only at those k vertices on the way.
     *   Admitting one more vertex can only help a pair in one way, by letting its
     *   route pass through that vertex, and the cheapest such route is the
     *   cheapest way to the new vertex followed by the cheapest way onwards, both
     *   of which the table already holds. One comparison per pair therefore
     *   carries the statement from k to k plus one, and after every vertex has
     *   been admitted, no route remains that the table has not considered.
     * - Processing steps: For each vertex in turn, and for every ordered pair,
     *   compares the route through that vertex against the entry standing for the
     *   pair and replaces the entry when the detour is cheaper.
     * - Assumptions: Assumes the tables have been initialised with the direct
     *   routes and the zero diagonal. Nothing is assumed about the weights: unlike
     *   the greedy search of this package, this method never declares an entry
     *   final while vertices remain to be admitted, which is why negative weights
     *   cost it nothing.
     * - Side effects: Writes both tables.
     *
     * The order of the three loops is not interchangeable. The admitted vertex
     * must be the outermost, because the statement above is about the whole table
     * after each admission; moving it inwards would let a pair be improved through
     * a vertex whose own row and column had not yet been completed for the
     * admissions before it, and the result would depend on the order the pairs
     * happen to be visited in.
     *
     * The first-step entry of an improved pair is taken from the route to the
     * admitted vertex rather than set to that vertex. What the table records is
     * where to go first, and the first step of the detour is the first step of the
     * way to the intermediate vertex, which may well be several steps before it.
     *
     * Time complexity: O(v * v * v) in the number of vertices, one comparison per
     * pair per admitted vertex. The bound is also the best case: unlike the
     * repeated relaxation of the Bellman-Ford algorithm, this method has no state
     * that would let it notice it is finished early.
     * Space complexity: O(1) beyond the tables already held; every improvement is
     * written in place.
     */
    private void admitIntermediateVertices() {
        for (int intermediate = 0; intermediate < vertices.length; intermediate++) {
            for (int origin = 0; origin < vertices.length; origin++) {
                double distanceToIntermediate = distances[origin][intermediate];

                /*
                 * A pair that cannot reach the admitted vertex at all cannot be
                 * improved through it, and skipping the whole row here saves a
                 * pass over the destinations rather than merely one comparison.
                 */
                if (distanceToIntermediate != UNREACHABLE) {
                    for (int destination = 0; destination < vertices.length; destination++) {
                        double distanceFromIntermediate = distances[intermediate][destination];

                        // The admitted vertex must also lead onwards to the
                        // destination for the detour to exist at all.
                        if (distanceFromIntermediate != UNREACHABLE) {
                            double detour = distanceToIntermediate + distanceFromIntermediate;

                            if (detour < distances[origin][destination]) {
                                distances[origin][destination] = detour;

                                /*
                                 * The route now begins the way the route to the
                                 * intermediate vertex begins; that vertex itself
                                 * may lie much further along.
                                 */
                                firstSteps[origin][destination] = firstSteps[origin][intermediate];
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Finds the pairs whose distance is driven down without bound by a negative
     * cycle and records them as such.
     *
     * Detailed explanation of:
     * - Purpose: Separates the pairs with a genuine cheapest route from those that
     *   have none, and reports whether any negative cycle exists at all.
     * - Business context: The table this algorithm produces gives the condition
     *   away almost for free. An entry on the diagonal is the cost of leaving a
     *   vertex and returning to it, which is zero when nothing better exists and
     *   negative exactly when the vertex lies on a cycle of negative total weight.
     *   Every route that can reach such a vertex and continue from it to a
     *   destination can then be made arbitrarily cheap by looping first, so those
     *   pairs have no cheapest route and are recorded as unbounded rather than
     *   given whichever number the improvement step happened to leave behind. The
     *   distinction matters to a caller: an unbounded pair is connected, which an
     *   unreachable one is not.
     * - Processing steps:
     *   1. Read the diagonal and note every vertex sitting on a negative cycle.
     *   2. Report that there is none, leaving the table untouched, when the
     *      diagonal is clean.
     *   3. For every ordered pair, mark it unbounded as soon as one of those
     *      vertices can be reached from its origin and reached onwards to its
     *      destination.
     * - Assumptions: Assumes the improvement step has already run, so that the
     *   diagonal describes complete round trips rather than single arcs, and that
     *   reaching a vertex and continuing from it is enough to loop arbitrarily
     *   often, which holds because a cycle returns to where it started.
     * - Side effects: Overwrites the distance and first-step entries of the
     *   affected pairs. Every other entry keeps the result the improvement step
     *   established, which is what allows a table to be partly usable.
     *
     * Note that the diagonal of an affected vertex is itself marked unbounded by
     * this sweep, since such a vertex trivially reaches itself in both directions.
     * That is the intended answer: the cheapest round trip through a negative
     * cycle has no lower bound either.
     *
     * Time complexity: O(v * v * v) in the worst case, one scan over the cycle
     * vertices per pair, and O(v) when the diagonal is clean, which is the
     * ordinary case and costs no more than reading it.
     * Space complexity: O(v) for the record of vertices on negative cycles, which
     * is discarded when this method returns.
     *
     * @return
     * True when the graph contains at least one cycle of negative total weight;
     * false when every route has a lower bound, in which case no entry was changed
     * here.
     */
    private boolean markUnboundedPairs() {
        /*
         * A vertex lies on a negative cycle exactly when the table says it can
         * leave and return for less than nothing. Nothing else has to be searched
         * for: the improvement step has already found every route, including the
         * round trips.
         */
        boolean[] onNegativeCycle = new boolean[vertices.length];
        boolean anyFound = false;

        for (int index = 0; index < vertices.length; index++) {
            if (distances[index][index] < 0.0) {
                onNegativeCycle[index] = true;
                anyFound = true;
            }
        }

        // With a clean diagonal every route has a cheapest form and the table
        // stands as the improvement step left it.
        if (!anyFound) {
            return false;
        }

        for (int origin = 0; origin < vertices.length; origin++) {
            for (int destination = 0; destination < vertices.length; destination++) {

                for (int cycleVertex = 0; cycleVertex < vertices.length; cycleVertex++) {
                    /*
                     * The pair is spoiled as soon as one cycle vertex lies on some
                     * route between its two ends: the origin can reach it, it can
                     * reach the destination, and in between the cycle may be
                     * repeated as often as one likes.
                     */
                    if (onNegativeCycle[cycleVertex]
                            && distances[origin][cycleVertex] != UNREACHABLE
                            && distances[cycleVertex][destination] != UNREACHABLE) {

                        distances[origin][destination] = UNBOUNDED;

                        // No cheapest route exists, so there is no first step of
                        // one to record either.
                        firstSteps[origin][destination] = NO_VERTEX;

                        // One spoiling vertex is enough; the remaining ones would
                        // reach the same conclusion.
                        break;
                    }
                }
            }
        }

        return true;
    }

    /**
     * Copies the vertices of the specified graph into an array.
     *
     * Detailed explanation of:
     * - Purpose: Fixes the index space that both tables are addressed by.
     * - Business context: The result needs a value per pair of vertices, and this
     *   library holds no map from objects to values, so the vertices are numbered
     *   by their position here and both dimensions of both tables use that
     *   numbering. Taking the snapshot once also decouples the result from the
     *   graph, which is what allows the graph reference to be dropped after
     *   construction.
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
     * reported them. Empty when the graph holds no vertices. Never null.
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
     * Fills both tables with the routes that use no intermediate vertex at all.
     *
     * Detailed explanation of:
     * - Purpose: Establishes the starting state of the computation, in which a
     *   pair is connected only if a single arc connects it directly.
     * - Business context: The improvement step that follows admits one
     *   intermediate vertex at a time, so it has to begin from the routes that use
     *   none: staying where one is, which costs nothing, and travelling a single
     *   arc, which costs its weight. Everything else is unknown at this point and
     *   is recorded as unreachable, which the improvement step is free to better.
     * - Processing steps:
     *   1. Set every entry to unreachable with no first step.
     *   2. Set the diagonal to zero, a vertex being reached from itself at no
     *      cost, with itself as the first step.
     *   3. For every vertex, ask for its neighbours and for the edge leading to
     *      each of them, and record that weight as the direct route.
     * - Assumptions: Assumes the graph reports at most one connection per ordered
     *   pair, which the representations of this package guarantee by refusing
     *   parallel connections; were there several, the entry would keep whichever
     *   the graph reported last rather than the cheapest.
     * - Side effects: Writes both tables.
     *
     * The direction of each arc is taken from the pairing of the neighbour query
     * with the edge lookup, exactly as in the Bellman-Ford algorithm of this
     * package: the neighbours of a vertex are the vertices it leads to, and the
     * edge is asked for in that same direction. Over an undirected graph this
     * records both entries of every edge, since each endpoint reports the other as
     * a neighbour; over a directed graph it records only the entry the arc allows.
     *
     * Time complexity: O(v * v) for clearing the tables, plus O(v * n + a * (v + m))
     * for reading the arcs, with a as the number of arcs, n as the cost of one
     * neighbour query, m as the cost of one edge lookup and the v per arc as the
     * lookup of the neighbour's position in the snapshot.
     * Space complexity: O(1) beyond the tables being filled and the neighbour lists
     * the graph allocates.
     *
     * @param pGraph
     * The graph to read. Must not be null, which the caller has already ensured.
     */
    private void initialiseTables(Graph pGraph) {
        /*
         * Nothing is known before the arcs are read: no pair is connected and no
         * route has a first step. Starting from infinity is what lets the first
         * route found for a pair always improve on its entry.
         */
        for (int origin = 0; origin < vertices.length; origin++) {
            for (int destination = 0; destination < vertices.length; destination++) {
                distances[origin][destination] = UNREACHABLE;
                firstSteps[origin][destination] = NO_VERTEX;
            }

            /*
             * A vertex reaches itself without travelling. The first step of that
             * empty route is the vertex itself, which is what lets the
             * reconstruction treat a route to oneself like any other and stop
             * immediately.
             */
            distances[origin][origin] = 0.0;
            firstSteps[origin][origin] = origin;
        }

        for (int originIndex = 0; originIndex < vertices.length; originIndex++) {
            Vertex origin = vertices[originIndex];

            SinglyLinkedList<Vertex> neighbours = pGraph.getNeighbours(origin);
            neighbours.toFirst();
            while (neighbours.hasAccess()) {
                Vertex neighbour = neighbours.getContent();
                int neighbourIndex = indexOf(neighbour);

                // Ask for the edge in the direction it is to be travelled, which is
                // what keeps the table asymmetric over a directed graph.
                Edge connection = pGraph.getEdge(origin, neighbour);

                /*
                 * A neighbour outside the snapshot, or one the graph reports
                 * without an edge to it, describes no route that could be recorded
                 * and is skipped rather than entered with an invented weight.
                 */
                if (neighbourIndex != NO_VERTEX && connection != null) {
                    distances[originIndex][neighbourIndex] = connection.getWeight();
                    firstSteps[originIndex][neighbourIndex] = neighbourIndex;
                }
                neighbours.next();
            }
        }
    }

    /**
     * Reports the position of the specified vertex within the snapshot.
     *
     * Detailed explanation of:
     * - Purpose: Translates a vertex into the index its row and column of the
     *   tables are held under.
     * - Business context: Every query of this class begins here, and so does the
     *   initialisation. The lookup is a linear scan because the snapshot is a plain
     *   array and this library provides no hash-based lookup; the cost is
     *   documented on each operation rather than hidden. It is notably absent from
     *   the improvement step, which works entirely on indices.
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
     * Reports whether the graph contains a cycle of negative total weight.
     *
     * Detailed explanation of:
     * - Purpose: States whether the graph contains the one structure that makes
     *   the shortest-path question unanswerable for part of the table.
     * - Business context: This is the check a cautious caller makes before
     *   trusting any entry, and for some callers the finding itself, for instance
     *   when the weights are gains and losses and such a cycle means a sequence
     *   that can be repeated for unbounded profit. Unlike the single-source
     *   algorithms of this package, which only see what their source can reach,
     *   this one answers for the whole graph, which makes it the natural way to
     *   ask whether a graph contains such a cycle anywhere at all.
     * - Processing steps: Returns the flag established during construction.
     * - Assumptions: None.
     * - Side effects: None.
     *
     * Time complexity: O(1); the flag is held as a field.
     * Space complexity: O(1).
     *
     * @return
     * True when at least one vertex can leave and return to itself for a negative
     * total, false when every route in the graph has a lower bound, in which case
     * every reported distance is a finite total over a real route or the
     * unreachable marker.
     */
    public boolean hasNegativeCycle() {
        return negativeCycleFound;
    }

    /**
     * Reports the total weight of the cheapest route from one vertex to another.
     *
     * Detailed explanation of:
     * - Purpose: Answers the question the table was computed for, for one ordered
     *   pair.
     * - Business context: This is the number a caller compares, sums or ranks, in
     *   whatever unit the edge weights are kept in. The order of the two arguments
     *   matters over a directed graph, where the route back may be longer, may
     *   cost differently, or may not exist at all; over an undirected graph the
     *   two orders always agree. Because negative weights are permitted, the value
     *   may itself be negative, which is an ordinary result and not to be confused
     *   with the unbounded value that marks the absence of a cheapest route.
     * - Processing steps: Locates both vertices in the snapshot and reads the
     *   table entry for that pair.
     * - Assumptions: Assumes vertices are compared by identity, so a detached
     *   vertex carrying a familiar identifier is reported as unreachable rather
     *   than answered for.
     * - Side effects: None.
     *
     * Time complexity: O(v) in the number of vertices, for the two lookups; the
     * table access itself is constant, which is the property this algorithm is
     * chosen for.
     * Space complexity: O(1); nothing is allocated.
     *
     * @param pFromVertex
     * The vertex the route starts at. May be null or foreign to the graph the
     * table was computed over, both of which are answered as unreachable.
     *
     * @param pToVertex
     * The vertex the route ends at. May be null or foreign, with the same result.
     *
     * @return
     * The total weight of the cheapest route between the two, which is zero when
     * both are the same vertex and may be negative when negative weights are
     * present; UNREACHABLE when no route connects them, which includes a vertex
     * that was not part of the graph; or UNBOUNDED when a negative cycle lies on a
     * route between them, so that no cheapest route exists.
     */
    public double getDistance(Vertex pFromVertex, Vertex pToVertex) {
        int originIndex = indexOf(pFromVertex);
        int destinationIndex = indexOf(pToVertex);

        /*
         * A vertex outside the snapshot has no row and no column, and the
         * unreachable marker is the truthful answer for it: this table knows no
         * route to or from it.
         */
        if (originIndex == NO_VERTEX || destinationIndex == NO_VERTEX) {
            return UNREACHABLE;
        }

        return distances[originIndex][destinationIndex];
    }

    /**
     * Reports whether any route leads from one vertex to another.
     *
     * Detailed explanation of:
     * - Purpose: Answers the existence of a route without the caller having to
     *   interpret a distance.
     * - Business context: Comparing a returned distance against infinity is easy
     *   to write incorrectly, so the test is offered as its own operation. Over the
     *   whole table it is also the transitive closure of the graph, which is a
     *   question of its own: asked for every pair, it says which vertices can
     *   influence which. A pair spoiled by a negative cycle counts as connected
     *   here, because it genuinely is; there is merely no cheapest route among the
     *   many that exist.
     * - Processing steps: Compares the table entry against the unreachable marker.
     * - Assumptions: Assumes no genuine route has infinite total weight, which
     *   holds because edge weights are finite and a route has finitely many edges.
     * - Side effects: None.
     *
     * Time complexity: O(v), dominated by the two vertex lookups.
     * Space complexity: O(1).
     *
     * @param pFromVertex
     * The vertex the route would start at. May be null or foreign to the graph,
     * both of which are reported as not connected.
     *
     * @param pToVertex
     * The vertex the route would end at. May be null or foreign, with the same
     * result.
     *
     * @return
     * True when at least one route leads from the first vertex to the second,
     * which includes a vertex and itself and every pair spoiled by a negative
     * cycle; false otherwise.
     */
    public boolean isReachable(Vertex pFromVertex, Vertex pToVertex) {
        return getDistance(pFromVertex, pToVertex) < UNREACHABLE;
    }

    /**
     * Reports whether a pair has no cheapest route because a negative cycle lies
     * between its two vertices.
     *
     * Detailed explanation of:
     * - Purpose: Distinguishes the pairs the table cannot give a number for from
     *   those it can.
     * - Business context: A caller reading the table pair by pair needs this per
     *   pair, not merely the graph-wide answer, since a single negative cycle
     *   usually spoils a region of the table and leaves the rest perfectly usable.
     *   Testing it explicitly is also clearer at a call site than comparing a
     *   distance against negative infinity, and it keeps the meaning of that value
     *   in one place.
     * - Processing steps: Compares the table entry against the unbounded value.
     * - Assumptions: Assumes the detection during construction marked every
     *   affected pair, which it does by checking each pair against every vertex
     *   sitting on a negative cycle.
     * - Side effects: None.
     *
     * Time complexity: O(v), dominated by the two vertex lookups.
     * Space complexity: O(1).
     *
     * @param pFromVertex
     * The vertex the route would start at. May be null or foreign to the graph,
     * neither of which is unbounded, since neither is connected in the first
     * place.
     *
     * @param pToVertex
     * The vertex the route would end at. May be null or foreign, with the same
     * result.
     *
     * @return
     * True when routes between the two can be made arbitrarily cheap and no
     * cheapest one exists; false for every pair with a genuine distance and for
     * every pair no route connects.
     */
    public boolean isUnbounded(Vertex pFromVertex, Vertex pToVertex) {
        return getDistance(pFromVertex, pToVertex) == UNBOUNDED;
    }

}
