package nonLinear.graph.shortestPath;

import nonLinear.graph.base.Edge;
import nonLinear.graph.base.Graph;
import nonLinear.graph.base.Vertex;

import linear.list.SinglyLinkedList;

/**
 * Purpose:
 * Computes the shortest routes from one vertex of a weighted graph to every
 * other vertex it can reach, measuring a route by the sum of the weights along
 * it rather than by the number of edges. The problem is not solved by the
 * breadth-first walk of the neighbouring traversal package, which finds the route
 * of fewest edges and is therefore only correct when every edge costs the same;
 * as soon as edges carry different weights, a detour over three cheap edges may
 * well cost less than a single expensive one, and a walk that settles a vertex on
 * first sight has already committed to the wrong answer. This class settles
 * vertices in order of increasing distance instead, taking up the nearest vertex
 * whose distance is not yet final, fixing that distance, and offering the routes
 * through it to its neighbours. That order is what makes the greedy step sound,
 * and it is also what the algorithm pays for with its one substantial
 * restriction: it holds only while no edge carries a negative weight, because a
 * negative edge could otherwise cheapen a route that had already been declared
 * final. The result is kept as a distance and a predecessor per vertex, which
 * together form the tree of shortest routes and allow any single route to be read
 * back edge by edge.
 *
 * Owner:
 * PBR208 - https://github.com/PBR208/
 *
 * Version:
 * 1.0
 */

/**
 * Single-source shortest paths over a non-negatively weighted graph, computed
 * once at construction and queried afterwards.
 *
 * Responsibility: Encapsulates the distance from the source to every vertex, the
 * predecessor each vertex is best reached through, the search that establishes
 * both, and the reconstruction of a route from that predecessor chain. It
 * maintains the invariant that a reported distance is the total weight of some
 * genuine route from the source, and that no cheaper route exists in the graph
 * the search was run over.
 *
 * Scope: Used wherever a cheapest route is wanted rather than a shortest one in
 * edges, and wherever the weights are costs that cannot be negative, which covers
 * distances, durations, capacities consumed and prices. A graph containing a
 * negative weight is refused rather than answered wrongly, and belongs to the
 * Bellman-Ford algorithm of this package instead.
 *
 * Dependencies: Depends on the Graph contract, on Vertex and Edge, and on
 * SinglyLinkedList for the route it hands back. It is written against the
 * contract rather than a representation, so it runs over the adjacency list, the
 * adjacency matrix and the directed graph alike; over a directed graph it follows
 * the arcs in their own direction, because it asks for the neighbours of a vertex
 * and for the edge leading to each of them, and the directed graph answers both
 * of those questions directionally.
 *
 * Thread-safety: An instance is safe to share between threads once constructed,
 * because everything it holds is written during construction and only read
 * afterwards. The construction itself is not thread-safe, since it reads a graph
 * that another thread may be changing at the same time.
 *
 * Lifecycle: The whole computation happens in the constructor, so an instance is
 * a finished result rather than a calculator waiting to be started, and there is
 * no order in which its methods must be called. The graph is deliberately not
 * retained: the distances describe the graph as it stood when the instance was
 * built, and a later change to that graph cannot silently alter answers already
 * given. A caller needing the routes from a second source, or from the same
 * source after a change, constructs a second instance.
 *
 * Architectural role: Serves as the first weighted algorithm of this repository
 * and as the counterpart to the unweighted breadth-first walk of the traversal
 * package: both settle vertices in order of increasing distance from a source,
 * and they differ only in how distance is measured, which is why the queue that
 * suffices there becomes a repeated search for the nearest unsettled vertex here.
 */
public class Dijkstra {

    /**
     * Distance reported for a vertex no route from the source reaches.
     *
     * Positive infinity is used rather than an agreed sentinel number because it
     * is the only value that behaves correctly in the comparison the search is
     * built on: every real distance is smaller than it, and extending an
     * unreachable vertex by any edge weight yields infinity again rather than
     * wrapping into a small number that would look like a cheap route. It is
     * exposed because callers comparing a returned distance against it is the
     * intended way to recognise an unreachable vertex, alongside the reachability
     * query offered for that purpose.
     */
    public static final double UNREACHABLE = Double.POSITIVE_INFINITY;

    /**
     * Index reported when no vertex satisfies a search.
     *
     * Vertices are addressed by their position in the snapshot below, so no valid
     * index is negative and this value cannot be confused with one. It marks both
     * a vertex that is foreign to the graph and the absence of any unsettled
     * vertex still worth taking up.
     */
    private static final int NO_VERTEX = -1;

    /**
     * The vertices of the graph, in the order it reported them at construction.
     *
     * This array defines the index space every other array of this class is
     * addressed by, which is what allows distances and predecessors to be held in
     * plain arrays rather than in a map from vertices to values, the library
     * being deliberately free of the collection framework. The snapshot is taken
     * once and kept, so the result stays readable even if the graph loses vertices
     * afterwards; the vertices themselves are shared instances rather than copies.
     */
    private final Vertex[] vertices;

    /**
     * Total weight of the cheapest known route from the source to each vertex.
     *
     * Indexed like the vertex snapshot. During the search an entry is an upper
     * bound that may still fall; once its vertex has been settled the entry is
     * final, and when the search ends every entry is either the true distance or
     * UNREACHABLE. The type is double because edge weights are, which also means
     * the usual caution about accumulated rounding applies: a distance is the sum
     * of the weights along a route and is exact only as far as those additions
     * are.
     */
    private final double[] distances;

    /**
     * The vertex each vertex is best reached through, indexed like the snapshot.
     *
     * Together these entries form the tree of shortest routes: following them
     * from any reachable vertex leads back to the source along a cheapest route,
     * which is what makes a full route recoverable without storing one per vertex.
     * The entry stays null for the source, which is reached through nothing, and
     * for every unreachable vertex, which is reached at all.
     */
    private final Vertex[] predecessors;

    /**
     * The vertex all reported distances and routes start from.
     *
     * Held so that a result can be interpreted without the caller having to
     * remember which source produced it, and so that a route can be recognised as
     * complete when it arrives back here.
     */
    private final Vertex sourceVertex;

    /**
     * Computes the shortest routes from the specified source vertex through the
     * specified graph.
     *
     * Detailed explanation of:
     * - Purpose: Establishes the complete result this instance exists to be
     *   queried for, namely a distance and a predecessor for every vertex of the
     *   graph.
     * - Business context: The computation is performed here rather than in a
     *   method that must be called first, because a partially computed result has
     *   no useful meaning and a caller could otherwise read distances that are
     *   still provisional. An instance is therefore a finished answer to one
     *   question, and asking about a second source, or about the same source after
     *   the graph has changed, means constructing a second instance. That is not a
     *   limitation of the algorithm but a property of what it computes: the
     *   distances are only jointly meaningful for a single source.
     * - Processing steps:
     *   1. Reject a null graph, a source that is not a vertex of that graph, and a
     *      graph containing a negative edge weight.
     *   2. Take a snapshot of the vertices, which fixes the index space the result
     *      is held in.
     *   3. Set every distance to unreachable and every predecessor to none, then
     *      set the distance of the source to zero, which is the only thing known
     *      before the search begins.
     * - Assumptions: Assumes the graph does not change while the constructor runs,
     *   and that its neighbour and edge queries agree with one another, which the
     *   contract requires and all three representations of this package honour.
     * - Side effects: Allocates the three result arrays. The graph is read but
     *   neither modified nor retained; in particular, and unlike the traversals of
     *   this package, the marks of its vertices are left untouched, because the
     *   search keeps its own record of what has been settled and has no reason to
     *   disturb state a caller may be relying on.
     *
     * Time complexity: O(v + e) for the validation and the snapshot alone, with v
     * vertices and e edges; the search that follows dominates and is documented on
     * the class.
     * Space complexity: O(v) for the three arrays, one entry per vertex each.
     *
     * @param pGraph
     * The graph to search. Must not be null and must not contain an edge of
     * negative weight. May contain vertices the source cannot reach, which are
     * reported as unreachable rather than treated as an error.
     *
     * @param pSourceVertex
     * The vertex every reported distance is measured from. Must be the very
     * instance the graph holds, not a detached vertex carrying the same
     * identifier, since a foreign vertex has no neighbours in this graph and would
     * yield a result in which nothing is reachable.
     *
     * @throws IllegalArgumentException
     * Thrown when pGraph is null, when pSourceVertex is null or is not a vertex of
     * pGraph, or when any edge of pGraph carries a negative weight. The last case
     * is refused rather than computed because the algorithm would return a wrong
     * answer rather than fail: it declares a distance final as soon as its vertex
     * is the nearest unsettled one, and a negative edge encountered later could
     * still undercut it. Callers whose weights can be negative want the
     * Bellman-Ford algorithm, which relaxes every edge repeatedly and therefore
     * survives them.
     */
    public Dijkstra(Graph pGraph, Vertex pSourceVertex) {
        // Without a graph there is nothing to search, and every step below would
        // fail on a reference the caller could have checked more cheaply.
        if (pGraph == null) {
            throw new IllegalArgumentException("The graph must not be null.");
        }

        /*
         * The source must be the instance the graph holds. Comparing by identity
         * rather than by identifier rules out a detached vertex that merely
         * carries a matching identifier, which would have no neighbours here and
         * would produce a result declaring the whole graph unreachable.
         */
        if (pSourceVertex == null || pSourceVertex.getID() == null
                || pGraph.getVertex(pSourceVertex.getID()) != pSourceVertex) {
            throw new IllegalArgumentException("The source vertex must be a vertex of the graph.");
        }

        // Refuse the one input the algorithm cannot answer correctly; see this
        // constructor's documentation for why silence would be worse than an
        // exception here.
        requireNonNegativeWeights(pGraph);

        this.sourceVertex = pSourceVertex;
        this.vertices = snapshotVertices(pGraph);
        this.distances = new double[vertices.length];
        this.predecessors = new Vertex[vertices.length];

        /*
         * Before anything has been explored, every vertex is as far away as it can
         * be and is reached through nothing. Starting from infinity rather than
         * from a large finite number is what lets the first route found to a
         * vertex always improve on its current estimate.
         */
        for (int index = 0; index < vertices.length; index++) {
            distances[index] = UNREACHABLE;
            predecessors[index] = null;
        }

        // The source is reached from itself at no cost, and this single known
        // value is what the whole search grows out of.
        distances[indexOf(pSourceVertex)] = 0.0;

        // Grow that one known distance into the complete result. The graph is
        // passed rather than stored, so that nothing of it outlives this call.
        computeShortestPaths(pGraph);
    }

    /**
     * Settles every reachable vertex in order of increasing distance from the
     * source, filling the distance and predecessor arrays.
     *
     * Detailed explanation of:
     * - Purpose: Turns the initial state, in which only the source has a known
     *   distance, into the final one, in which every reachable vertex has its
     *   cheapest distance and the predecessor it is reached through.
     * - Business context: This is the algorithm itself, and its shape follows from
     *   a single observation: among the vertices whose distance is not yet final,
     *   the one with the smallest current estimate cannot be improved any further.
     *   Any route to it must leave the settled region at some point, and every
     *   such route already costs at least that estimate before it goes anywhere
     *   else, because all weights are non-negative. Its estimate is therefore its
     *   true distance and may be declared final, after which the routes leading
     *   onwards through it are offered to its neighbours. Repeating that step
     *   settles the vertices in order of increasing distance, which is the same
     *   order the breadth-first walk of the traversal package settles them in when
     *   every edge has the same weight.
     * - Processing steps:
     *   1. Take up the unsettled vertex with the smallest current estimate.
     *   2. Stop when none is left that any route reaches; the remaining vertices
     *      are unreachable and keep their initial values.
     *   3. Mark it settled and offer, to each of its neighbours, the route that
     *      goes through it, keeping the offer when it is cheaper than the
     *      neighbour's current estimate.
     * - Assumptions: Assumes non-negative weights, which the constructor has
     *   enforced, and assumes the neighbour query and the edge lookup of the graph
     *   agree with each other. The edge is fetched from the current vertex to the
     *   neighbour in that order, which is what makes the search follow a directed
     *   graph along its arcs rather than against them.
     * - Side effects: Writes the distance and predecessor arrays. The graph is
     *   only read.
     *
     * The routes are offered to neighbours that are not yet settled only. A
     * settled neighbour cannot be improved, by the argument above, so testing it
     * would cost a lookup and an addition to reach a comparison that can never
     * succeed.
     *
     * Time complexity: O(v * (v + n) + e) with v vertices, e edges and n as the
     * cost the representation charges for one neighbour query, plus one edge
     * lookup and one index lookup per examined neighbour. The v * v term is the
     * repeated search for the nearest unsettled vertex; see the class
     * documentation for what the representations of this package make of the rest.
     * Space complexity: O(v) for the record of settled vertices; the result arrays
     * were allocated by the constructor.
     *
     * @param pGraph
     * The graph to search, already validated by the constructor. Must not be null
     * and must not change while this method runs.
     */
    private void computeShortestPaths(Graph pGraph) {
        /*
         * Records which distances are final. This is kept here rather than in a
         * field because it is scaffolding of the search and meaningless
         * afterwards: when the method returns, every reachable vertex is settled
         * and every unsettled one is unreachable, which the distances already say.
         */
        boolean[] settled = new boolean[vertices.length];

        /*
         * Each round settles exactly one vertex, so at most as many rounds as
         * there are vertices can be needed. The loop normally ends earlier,
         * through the break below, whenever part of the graph cannot be reached.
         */
        for (int round = 0; round < vertices.length; round++) {
            int nearestIndex = nearestUnsettledIndex(settled);

            /*
             * No unsettled vertex has a finite estimate any more. Everything the
             * source can reach has been settled, and the vertices left keep the
             * unreachable distance and the absent predecessor they started with.
             */
            if (nearestIndex == NO_VERTEX) {
                break;
            }

            // The estimate of the nearest unsettled vertex cannot be undercut, so
            // it is declared final and never reconsidered.
            settled[nearestIndex] = true;

            Vertex current = vertices[nearestIndex];
            double distanceToCurrent = distances[nearestIndex];

            SinglyLinkedList<Vertex> neighbours = pGraph.getNeighbours(current);
            neighbours.toFirst();
            while (neighbours.hasAccess()) {
                Vertex neighbour = neighbours.getContent();
                int neighbourIndex = indexOf(neighbour);

                /*
                 * A settled neighbour is already final, and a neighbour outside
                 * the snapshot cannot be recorded at all. The latter cannot arise
                 * from a consistent graph and is guarded against so that a
                 * representation reporting a stray vertex causes an ignored
                 * neighbour rather than a failure deep inside the search.
                 */
                if (neighbourIndex != NO_VERTEX && !settled[neighbourIndex]) {
                    /*
                     * Ask for the edge in the direction it is to be travelled.
                     * Over the undirected representations the order of the two
                     * arguments makes no difference; over the directed graph it
                     * decides whether the arc exists at all, which is what keeps
                     * this search on the arcs rather than against them.
                     */
                    Edge connection = pGraph.getEdge(current, neighbour);

                    if (connection != null) {
                        // The cost of reaching the neighbour by going through the
                        // vertex just settled.
                        double offeredDistance = distanceToCurrent + connection.getWeight();

                        // Keep the offer only when it beats what is already known,
                        // which for an untouched vertex is infinity and therefore
                        // always improves.
                        if (offeredDistance < distances[neighbourIndex]) {
                            distances[neighbourIndex] = offeredDistance;
                            predecessors[neighbourIndex] = current;
                        }
                    }
                }
                neighbours.next();
            }
        }
    }

    /**
     * Reports the unsettled vertex with the smallest current distance estimate.
     *
     * Detailed explanation of:
     * - Purpose: Chooses the vertex the search takes up next.
     * - Business context: This choice is the algorithm's only decision, and making
     *   it correctly is what allows a distance to be declared final. The search is
     *   performed as a linear scan over the estimates rather than through a
     *   priority queue, which is a deliberate choice for this library and is
     *   discussed on the class: the priority queue of the linear package orders by
     *   integer priority and cannot carry a floating-point distance without
     *   rounding it, and rounding here would not merely blunt the order but settle
     *   vertices in the wrong one.
     * - Processing steps: Scans every vertex, ignoring those already settled and
     *   those no route has reached, and keeps the position of the smallest
     *   estimate seen.
     * - Assumptions: Assumes an unreached vertex still holds the unreachable
     *   distance, so that the comparison excludes it without a separate test.
     * - Side effects: None; this method only reads.
     *
     * Time complexity: O(v) in the number of vertices, which is what makes the
     * whole search quadratic in the vertices.
     * Space complexity: O(1); nothing is allocated.
     *
     * @param pSettled
     * The record of which vertices already hold their final distance, indexed like
     * the vertex snapshot. Must not be null and must have one entry per vertex.
     *
     * @return
     * The position of the unsettled vertex with the smallest finite estimate, or
     * NO_VERTEX when every remaining vertex is unreachable, which is the signal to
     * end the search.
     */
    private int nearestUnsettledIndex(boolean[] pSettled) {
        int nearestIndex = NO_VERTEX;
        double smallestDistance = UNREACHABLE;

        for (int index = 0; index < vertices.length; index++) {
            /*
             * The strict comparison against the smallest distance seen so far also
             * excludes every vertex still at infinity, since no value is smaller
             * than infinity while the running minimum is infinity itself. An
             * unreached vertex is therefore never chosen, which is exactly the
             * condition the search stops on.
             */
            if (!pSettled[index] && distances[index] < smallestDistance) {
                smallestDistance = distances[index];
                nearestIndex = index;
            }
        }

        return nearestIndex;
    }

    /**
     * Rejects a graph containing an edge of negative weight.
     *
     * Detailed explanation of:
     * - Purpose: Enforces the one precondition the correctness of the search
     *   depends on.
     * - Business context: The check is worth its cost because the failure it
     *   prevents is silent. With a negative edge the algorithm still terminates
     *   and still returns distances; they are simply wrong for the vertices whose
     *   cheapest route runs through that edge, and nothing in the result marks
     *   them as such. Detecting the condition once, up front, converts an answer
     *   the caller would have trusted into an exception naming the cause.
     * - Processing steps: Walks every edge of the graph and throws as soon as one
     *   carries a weight below zero.
     * - Assumptions: Assumes the graph reports all of its edges, which the
     *   contract requires. A weight of exactly zero is permitted: it makes routes
     *   ambiguous but never makes a settled distance improvable, which is all the
     *   algorithm needs.
     * - Side effects: None on the graph, though the traversal of its edge
     *   collection moves the internal cursor of the representation.
     *
     * Time complexity: O(e) in the number of edges, stopping at the first negative
     * one.
     * Space complexity: O(e) for the edge list the graph hands out, which is
     * discarded immediately afterwards.
     *
     * @param pGraph
     * The graph to inspect. Must not be null, which the caller has already
     * ensured.
     *
     * @throws IllegalArgumentException
     * Thrown as soon as an edge of negative weight is found, naming the algorithm
     * that does handle such weights so that the message points somewhere useful.
     */
    private static void requireNonNegativeWeights(Graph pGraph) {
        SinglyLinkedList<Edge> edges = pGraph.getEdges();

        edges.toFirst();
        while (edges.hasAccess()) {
            if (edges.getContent().getWeight() < 0.0) {
                throw new IllegalArgumentException(
                        "The graph must not contain edges of negative weight; use the Bellman-Ford algorithm instead.");
            }
            edges.next();
        }
    }

    /**
     * Copies the vertices of the specified graph into an array.
     *
     * Detailed explanation of:
     * - Purpose: Fixes the index space that the distances and the predecessors are
     *   held in.
     * - Business context: The result needs a value per vertex, and this library
     *   holds no map from objects to values, so the vertices are numbered by their
     *   position in this array and every other array is addressed the same way.
     *   Taking the snapshot once also decouples the result from the graph, which
     *   is what allows the graph reference to be dropped after construction.
     * - Processing steps: Walks the vertex list once to count the vertices, then
     *   walks it again to fill an array of exactly that length.
     * - Assumptions: Assumes the graph reports the same vertices in both walks,
     *   which holds because nothing modifies the graph in between.
     * - Side effects: None on the graph; the lists it hands out are copies.
     *
     * Two passes are used because the list of this library reports no size and the
     * array must be allocated at its final length. Counting first is cheaper than
     * growing an array repeatedly and is clearer than guessing a capacity.
     *
     * Time complexity: O(v) in the number of vertices; two walks of a list the
     * graph has already built.
     * Space complexity: O(v) for the returned array, which holds references to the
     * graph's own vertex instances rather than copies of them.
     *
     * @param pGraph
     * The graph whose vertices are to be captured. Must not be null, which the
     * caller has already ensured.
     *
     * @return
     * A new array holding every vertex of the graph. Empty when the graph holds no
     * vertices, which cannot occur here because a valid source is one of them.
     * Never null.
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
     * Reports the position of the specified vertex within the snapshot.
     *
     * Detailed explanation of:
     * - Purpose: Translates a vertex into the index its distance and predecessor
     *   are stored under.
     * - Business context: Every query of this class begins here, and so does every
     *   relaxation during the search. The lookup is a linear scan because the
     *   snapshot is a plain array and this library provides no hash-based lookup;
     *   the cost is documented on each operation rather than hidden, since it is
     *   what raises the search above the bound usually quoted for this algorithm.
     * - Processing steps: Scans the snapshot and returns the position of the
     *   matching vertex.
     * - Assumptions: Assumes vertices are compared by identity, as everywhere in
     *   this package, so that a detached vertex carrying a familiar identifier is
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
     * - Purpose: Names the source the search was run from.
     * - Business context: A result is only meaningful together with its source,
     *   and a caller holding several results, one per source, needs to be able to
     *   tell them apart without keeping that association itself. It is also what
     *   makes a reconstructed route recognisable as complete, since a route ends
     *   exactly when it arrives here.
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
     * Reports the total weight of the cheapest route from the source to the
     * specified vertex.
     *
     * Detailed explanation of:
     * - Purpose: Answers the question the search was run for, for one vertex.
     * - Business context: This is the number a caller compares, sums or ranks:
     *   the cost of getting there, in whatever unit the edge weights are kept in.
     *   It is a total over a route rather than a property of the vertex, so it
     *   only means anything relative to the source of this result, and it is
     *   exactly as precise as the additions of the weights along that route were.
     * - Processing steps: Locates the vertex in the snapshot and returns its
     *   recorded distance.
     * - Assumptions: Assumes the vertex is compared by identity, so that a
     *   detached vertex carrying a familiar identifier is reported as unreachable
     *   rather than answered for.
     * - Side effects: None.
     *
     * Time complexity: O(v) in the number of vertices, for the lookup of the
     * vertex within the snapshot.
     * Space complexity: O(1); nothing is allocated.
     *
     * @param pVertex
     * The vertex to report the distance of. May be null or foreign to the graph
     * the search ran over, both of which are answered as unreachable.
     *
     * @return
     * The total weight of the cheapest route from the source to the vertex, which
     * is zero for the source itself, or UNREACHABLE when no route leads there and
     * likewise when the vertex was not part of the searched graph. A caller
     * needing to tell those two cases apart compares the vertex against the ones
     * the graph holds before asking.
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
     *   to write incorrectly and easy to read as an oversight, so the test is
     *   offered as its own operation. Note that reachability here is measured
     *   along the same edges the search followed, so over a directed graph it
     *   means reachable along the direction of the arcs, and it may well differ
     *   from the answer for the two vertices exchanged.
     * - Processing steps: Compares the recorded distance against the unreachable
     *   marker.
     * - Assumptions: Assumes no genuine route has infinite total weight, which
     *   holds because edge weights are finite numbers and a route has finitely
     *   many edges.
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
     * True when a route of finite total weight leads from the source to the
     * vertex, which for the source itself is trivially the case; false otherwise.
     */
    public boolean isReachable(Vertex pVertex) {
        return getDistance(pVertex) < UNREACHABLE;
    }

    /**
     * Reports the vertex immediately before the specified one on its cheapest
     * route from the source.
     *
     * Detailed explanation of:
     * - Purpose: Exposes one link of the tree of shortest routes.
     * - Business context: The predecessors are how a route is stored at all: one
     *   vertex per vertex rather than one route per vertex, which is what keeps
     *   the result linear in the size of the graph. A caller wanting a whole route
     *   asks for the path instead; this operation is for callers walking the tree
     *   themselves, for instance to find where two routes diverge or to attribute
     *   the cost of a step to the edge that carries it.
     * - Processing steps: Locates the vertex in the snapshot and returns the
     *   predecessor recorded for it.
     * - Assumptions: Assumes the predecessor chain is acyclic and ends at the
     *   source, which the search guarantees: a predecessor is only recorded when
     *   it improves a distance, and improvements strictly decrease along the
     *   chain.
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
     * source, when no route reaches it, or when it was not part of the searched
     * graph. Null therefore means the route does not continue, not that something
     * went wrong.
     */
    public Vertex getPredecessor(Vertex pVertex) {
        int index = indexOf(pVertex);

        if (index == NO_VERTEX) {
            return null;
        }

        return predecessors[index];
    }

}
