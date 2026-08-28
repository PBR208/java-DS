package nonLinear.graph.topologicalSort;

import nonLinear.graph.base.Graph;
import nonLinear.graph.base.Vertex;

import linear.list.SinglyLinkedList;

/**
 * Purpose:
 * Arranges the vertices of a directed graph in a sequence that respects every
 * connection, so that a vertex always appears before the vertices it leads to.
 * The problem is the one behind every dependency: a build target that must be
 * compiled after the libraries it uses, a course that may only be taken once its
 * prerequisites are passed, a set of tasks whose order is fixed in places and
 * free everywhere else. Such a graph does not describe a sequence directly, it
 * describes constraints between pairs, and the work of this class is to turn
 * those constraints into one concrete sequence satisfying all of them at once.
 * The method used here follows the definition rather than the graph: a vertex
 * nothing points at may safely be placed next, and removing it from the graph can
 * only free further vertices, so repeatedly taking a vertex with nothing left
 * pointing at it produces a valid sequence. The same procedure answers the other
 * question such a graph raises: if vertices remain when no free one is left, they
 * depend on one another in a circle, and no sequence can exist at all. Reporting
 * that, and naming the vertices caught in it, is as much a part of this class as
 * the ordering itself.
 *
 * Owner:
 * PBR208 - https://github.com/PBR208/
 *
 * Version:
 * 1.0
 */

/**
 * Topological ordering of a graph, computed once at construction and queried
 * afterwards.
 *
 * Responsibility: Encapsulates the count of incoming connections per vertex, the
 * repeated removal of vertices that have none left, the sequence this produces,
 * and the reporting of the vertices that could not be placed. It maintains the
 * invariant that, in the reported sequence, every vertex appears before all
 * vertices it leads to, and that a sequence is reported only when it covers every
 * vertex of the graph.
 *
 * Scope: Meaningful for directed graphs, since a topological order is defined by
 * the direction of the connections. An undirected graph holding at least one edge
 * is answered correctly rather than refused: an undirected edge makes each of its
 * endpoints depend on the other, which is a circle of two, so such a graph is
 * reported as having no order at all. That is not a limitation to work around but
 * the truthful answer, and it is the reason this class is written against the
 * graph contract rather than against the directed representation alone.
 *
 * Dependencies: Depends on the Graph contract and on Vertex, and on
 * SinglyLinkedList for the sequences it hands back. It reads a graph exclusively
 * through the neighbour query, ignoring edge weights entirely, since a dependency
 * either exists or does not and its cost has no bearing on the order.
 *
 * Thread-safety: An instance is safe to share between threads once constructed,
 * since everything it holds is written during construction and only read
 * afterwards. The construction itself is not thread-safe, because it reads a
 * graph another thread may be changing.
 *
 * Lifecycle: The whole computation happens in the constructor, so an instance is
 * a finished result rather than a calculator waiting to be started. The graph is
 * read during construction and then dropped, so the result describes the graph as
 * it stood at that moment and cannot be disturbed by later changes to it.
 *
 * Architectural role: Completes the graph package with the algorithm that is not
 * about routes at all. Where the traversal reports what can be reached and the
 * shortest-path algorithms report how cheaply, this one reports in which order
 * things may be done, which is the question a directed graph is most often built
 * to answer outside of navigation.
 */
public class TopologicalSort {

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
     * Defines the index space the degree bookkeeping is addressed by, this library
     * holding no map from objects to values. The order of this snapshot also
     * decides which of several valid sequences is produced, since vertices that
     * become free at the same moment are taken up in the order they appear here.
     */
    private final Vertex[] vertices;

    /**
     * Computes a topological order of the specified graph.
     *
     * Detailed explanation of:
     * - Purpose: Establishes the sequence and the diagnosis this instance exists
     *   to be queried for.
     * - Business context: The work is done here rather than in a method that must
     *   be called first, matching the algorithms of the neighbouring packages, so
     *   that a caller cannot read a partial sequence. A partial sequence would be
     *   particularly misleading here, since a prefix of a topological order looks
     *   exactly like a complete one and only the count reveals the difference.
     * - Processing steps:
     *   1. Reject a null graph.
     *   2. Snapshot the vertices, which fixes the index space and the tie-breaking
     *      order of the result.
     * - Assumptions: Assumes the graph does not change while the constructor runs.
     * - Side effects: Allocates the vertex snapshot. The graph is read but neither
     *   modified nor retained, and its vertex and edge marks are left untouched,
     *   since this algorithm keeps its bookkeeping in its own arrays rather than in
     *   the marks a traversal would use.
     *
     * Time complexity: O(v) in the number of vertices for the snapshot alone; the
     * ordering that follows dominates and is documented on the class.
     * Space complexity: O(v) for the snapshot.
     *
     * @param pGraph
     * The graph to order. Must not be null. May be empty, may consist of several
     * unconnected parts, and may contain circular dependencies; none of these is
     * an error, the last of them being a finding this class reports.
     *
     * @throws IllegalArgumentException
     * Thrown when pGraph is null, which leaves the instance unable to describe
     * anything. Reporting that at construction is more useful than handing back an
     * empty sequence that a caller could mistake for a graph without vertices.
     */
    public TopologicalSort(Graph pGraph) {
        // Without a graph there is nothing to order, and every step below would
        // fail on a reference the caller could have checked more cheaply.
        if (pGraph == null) {
            throw new IllegalArgumentException("The graph must not be null.");
        }

        this.vertices = snapshotVertices(pGraph);
    }

    /**
     * Copies the vertices of the specified graph into an array.
     *
     * Detailed explanation of:
     * - Purpose: Fixes the index space the degree bookkeeping is addressed by.
     * - Business context: The algorithm needs a count per vertex, and this library
     *   holds no map from objects to values, so the vertices are numbered by their
     *   position here. Taking the snapshot once also decouples the result from the
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
        // index of this class refers to and the order ties are broken in.
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
     * Counts, for every vertex, how many other vertices lead to it.
     *
     * Detailed explanation of:
     * - Purpose: Establishes the number of unfulfilled dependencies each vertex
     *   starts with.
     * - Business context: This count is what the ordering is driven by. A vertex
     *   with a count of zero has nothing pointing at it and may therefore be placed
     *   immediately; every other vertex must wait for exactly as many placements as
     *   its count records. Deriving the counts from the neighbour query rather than
     *   from an incoming-edge query is deliberate, since the graph contract of this
     *   package offers no incoming query at all and only the directed
     *   representation could answer one.
     * - Processing steps: Walks the neighbours of every vertex and raises the count
     *   of each neighbour by one.
     * - Assumptions: Assumes the neighbour query reports one entry per connection
     *   leaving a vertex, which the representations of this package guarantee by
     *   refusing loops and parallel connections. Over an undirected graph each edge
     *   is reported from both ends, so both endpoints receive a count, which is
     *   precisely what makes such a graph turn out to be circular below.
     * - Side effects: None on the graph content.
     *
     * Time complexity: O(v * n + a) with v vertices, a arcs and n as the cost the
     * representation charges for one neighbour query.
     * Space complexity: O(v) for the returned counts.
     *
     * @param pGraph
     * The graph to measure. Must not be null, which the caller has already
     * ensured.
     *
     * @return
     * A new array holding, at the position of each vertex, the number of vertices
     * leading to it. Never null; every entry is zero for a graph without edges.
     */
    private int[] countIncoming(Graph pGraph) {
        int[] incoming = new int[vertices.length];

        for (int index = 0; index < vertices.length; index++) {
            SinglyLinkedList<Vertex> neighbours = pGraph.getNeighbours(vertices[index]);

            neighbours.toFirst();
            while (neighbours.hasAccess()) {
                int neighbourIndex = indexOf(neighbours.getContent());

                // A neighbour outside the snapshot cannot be counted and cannot be
                // ordered either; it can only arise from an inconsistent graph and
                // is skipped rather than allowed to fail on an invalid index.
                if (neighbourIndex != NO_VERTEX) {
                    incoming[neighbourIndex] = incoming[neighbourIndex] + 1;
                }
                neighbours.next();
            }
        }

        return incoming;
    }

    /**
     * Reports the position of the specified vertex within the snapshot.
     *
     * Detailed explanation of:
     * - Purpose: Translates a vertex into the index its bookkeeping is held under.
     * - Business context: The counting of incoming connections and the ordering
     *   itself both need this translation, since the graph reports neighbours as
     *   vertices while the counts live in an array. The lookup is a linear scan
     *   because the snapshot is a plain array and this library provides no
     *   hash-based lookup; the cost is documented on each operation rather than
     *   hidden.
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
