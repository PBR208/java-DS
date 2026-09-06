package nonLinear.graph.minimumSpanningTree;

import nonLinear.graph.base.Edge;
import nonLinear.graph.base.Graph;
import nonLinear.graph.base.Vertex;

import linear.list.SinglyLinkedList;

/**
 * Purpose:
 * Builds a minimum spanning forest of a weighted graph: the cheapest possible set
 * of edges connecting every vertex it can reach without ever closing a cycle,
 * one tree per connected part of the graph and a single tree covering everything
 * when the graph is itself connected. The problem shows up wherever a set of
 * points must be linked as cheaply as possible without redundancy, a road network
 * joining every town with the least total length of road, a circuit board wiring
 * every pin with the least total trace, a cluster of machines wiring every node
 * with the least total cable. Kruskal's algorithm answers it with a greedy rule of
 * striking simplicity: consider every edge exactly once, in order of increasing
 * weight, and accept it precisely when its two endpoints do not already lie in the
 * same growing component, since accepting it otherwise would close a cycle without
 * connecting anything new. That the cheapest edge crossing any cut of the graph
 * belongs to some minimum spanning tree is what makes this greedy choice safe at
 * every step, and taking the edges in ascending order is what guarantees the
 * cheapest available choice is always the one considered first. The same rule that
 * decides which edges to keep also reveals when the graph itself falls into
 * several disconnected parts, since such a graph can only ever be spanned by a
 * forest rather than by a single tree, and this class reports that condition
 * rather than presenting a forest as though it were a complete spanning tree.
 *
 * Owner:
 * PBR208 - https://github.com/PBR208/
 *
 * Version:
 * 1.0
 */

/**
 * Minimum spanning forest of a weighted graph, computed once at construction and
 * queried afterwards.
 *
 * Responsibility: Encapsulates the edges accepted into the spanning structure,
 * the count of how many were accepted, the sorting of every edge of the graph by
 * weight, and the union-find bookkeeping that decides whether accepting an edge
 * would close a cycle. It maintains the invariant that the accepted edges never
 * close a cycle among themselves and that no edge of lower weight could have
 * taken the place of an accepted one without being ruled out by a cycle it would
 * have closed.
 *
 * Scope: Meaningful for a graph whose edges are read as undirected connections,
 * which is how the adjacency list and the adjacency matrix of this package
 * present them. Handed a directed graph, this class still runs to completion,
 * because it reads edges through the general edge query rather than through the
 * direction-aware queries that representation also offers, and it treats the
 * tail and the head of every arc as the two endpoints of an ordinary connection;
 * the result is then a minimum spanning structure of the graph with its
 * direction erased, which answers a different question than the one a caller
 * holding a directed graph usually means to ask. Unlike the shortest-path
 * algorithms of the neighbouring package, a negative edge weight is not a
 * hazard here and needs no separate algorithm to survive it: a negative edge is
 * merely an attractive one, and the greedy rule remains correct whatever the
 * sign of a weight is.
 *
 * Dependencies: Depends on the Graph contract, on Vertex and Edge, on
 * SinglyLinkedList for the result it hands back, and on DisjointSet for the
 * membership test the greedy rule is built from.
 *
 * Thread-safety: An instance is safe to share between threads once constructed,
 * since everything it holds is written during construction and only read
 * afterwards. The construction itself is not thread-safe, because it reads a
 * graph another thread may be changing and builds a DisjointSet of its own that
 * no other thread has visibility into.
 *
 * Lifecycle: The whole computation happens in the constructor, so an instance is
 * a finished result rather than a calculator waiting to be started. The graph is
 * read during construction and then dropped, so the result describes the graph
 * as it stood at that moment and cannot be disturbed by a later change to it.
 *
 * Architectural role: Completes the graph package with the algorithm that
 * connects rather than routes: where the shortest-path algorithms of the
 * neighbouring package find the cheapest way between two vertices and the
 * traversal finds what can be reached at all, this one finds the cheapest way to
 * make everything reachable from everything else at once. It is also the
 * intended consumer of DisjointSet, which names Kruskal's algorithm among its own
 * reasons for existing.
 */
public class Kruskal {

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
     * Defines the index space the union-find bookkeeping is addressed by, this
     * library holding no map from objects to values. Taking this snapshot once
     * also decouples the result from the graph, which is what allows the graph
     * reference to be dropped after construction.
     */
    private final Vertex[] vertices;

    /**
     * Builds the minimum spanning forest of the specified graph.
     *
     * Detailed explanation of:
     * - Purpose: Establishes the complete result this instance exists to be
     *   queried for, namely the edges of a cheapest structure connecting every
     *   vertex the graph allows to be connected at all.
     * - Business context: The computation is performed here rather than in a
     *   method that must be called first, matching the algorithms of the
     *   neighbouring packages, so that a caller can never read a partially
     *   assembled forest.
     * - Processing steps:
     *   1. Reject a null graph.
     *   2. Snapshot the vertices, which fixes the index space the union-find
     *      bookkeeping is addressed by.
     * - Assumptions: Assumes the graph does not change while the constructor
     *   runs.
     * - Side effects: Allocates the vertex snapshot. The graph is read but
     *   neither modified nor retained.
     *
     * Time complexity: O(v) in the number of vertices for the snapshot alone;
     * the sort and the selection that follow dominate and are documented on the
     * class.
     * Space complexity: O(v) for the snapshot.
     *
     * @param pGraph
     * The graph to span. Must not be null. May be empty, may consist of several
     * unconnected parts, and may contain edges of negative weight; none of these
     * is an error, the second of them being a finding this class reports.
     *
     * @throws IllegalArgumentException
     * Thrown when pGraph is null, which leaves the instance unable to describe
     * anything. Reporting that at construction is more useful than handing back
     * an empty forest that a caller could mistake for a graph without vertices.
     */
    public Kruskal(Graph pGraph) {
        // Without a graph there is nothing to span, and every step below would
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
     * - Purpose: Fixes the index space the union-find bookkeeping is addressed
     *   by.
     * - Business context: The algorithm needs to translate a vertex into an
     *   index quickly, and this library holds no map from objects to values, so
     *   the vertices are numbered by their position here. Taking the snapshot
     *   once also decouples the result from the graph, which is what allows the
     *   graph reference to be dropped after construction.
     * - Processing steps: Walks the vertex list once to count the vertices,
     *   then walks it again to fill an array of exactly that length.
     * - Assumptions: Assumes the graph reports the same vertices in both walks,
     *   which holds because nothing modifies it in between.
     * - Side effects: None on the graph; the list it hands out is already a
     *   copy.
     *
     * Two passes are used because the list of this library reports no size and
     * the array must be allocated at its final length; counting first is
     * cheaper than growing an array repeatedly and clearer than guessing a
     * capacity.
     *
     * Time complexity: O(v) in the number of vertices.
     * Space complexity: O(v) for the returned array, which holds references to
     * the graph's own vertex instances rather than copies of them.
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

        // First pass: establish the length, which the list itself does not
        // report.
        int count = 0;
        vertexList.toFirst();
        while (vertexList.hasAccess()) {
            count = count + 1;
            vertexList.next();
        }

        // Second pass: fill the array in the same order, which is the order
        // every index of this class refers to.
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
     * - Purpose: Translates a vertex into the index the union-find bookkeeping
     *   addresses it by.
     * - Business context: Every edge considered during selection needs both of
     *   its endpoints translated this way before the disjoint set can be asked
     *   whether they already belong to the same component. The lookup is a
     *   linear scan because the snapshot is a plain array and this library
     *   provides no hash-based lookup; the cost is documented on each operation
     *   rather than hidden.
     * - Processing steps: Scans the snapshot and returns the position of the
     *   matching vertex.
     * - Assumptions: Assumes vertices are compared by identity, as everywhere
     *   in this package, so a detached vertex carrying a familiar identifier is
     *   correctly reported as unknown.
     * - Side effects: None.
     *
     * Time complexity: O(v) in the number of vertices.
     * Space complexity: O(1); nothing is allocated.
     *
     * @param pVertex
     * The vertex to locate. May be null or foreign to the graph, both of which
     * are reported as absent.
     *
     * @return
     * The position of the vertex in the snapshot, or NO_VERTEX when the
     * snapshot does not contain it.
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
     * Copies the edges of the specified graph into an array.
     *
     * Detailed explanation of:
     * - Purpose: Produces a working copy of every edge that can be reordered
     *   freely without disturbing the graph itself.
     * - Business context: The selection considers edges in ascending order of
     *   weight, which requires sorting them first, and sorting the graph's own
     *   collection in place would leave the graph in an order that has nothing
     *   to do with however it was built. A private copy is sorted instead and
     *   discarded once construction finishes.
     * - Processing steps: Walks the edge list once to count the edges, then
     *   walks it again to fill an array of exactly that length.
     * - Assumptions: Assumes the graph reports the same edges in both walks,
     *   which holds because nothing modifies it in between.
     * - Side effects: None on the graph; the list it hands out is already a
     *   copy.
     *
     * Time complexity: O(e) in the number of edges.
     * Space complexity: O(e) for the returned array, which holds references to
     * the graph's own edge instances rather than copies of them.
     *
     * @param pGraph
     * The graph whose edges are to be captured. Must not be null, which the
     * caller has already ensured.
     *
     * @return
     * A new array holding every edge of the graph, in the order the graph
     * reported them. Empty when the graph holds no edges. Never null.
     */
    private static Edge[] snapshotEdges(Graph pGraph) {
        SinglyLinkedList<Edge> edgeList = pGraph.getEdges();

        // First pass: establish the length, which the list itself does not
        // report.
        int count = 0;
        edgeList.toFirst();
        while (edgeList.hasAccess()) {
            count = count + 1;
            edgeList.next();
        }

        // Second pass: fill the array, in whatever order the graph reported
        // its edges; that order carries no meaning here and is discarded by
        // the sort that follows.
        Edge[] result = new Edge[count];
        int position = 0;
        edgeList.toFirst();
        while (edgeList.hasAccess()) {
            result[position] = edgeList.getContent();
            position = position + 1;
            edgeList.next();
        }

        return result;
    }

    /**
     * Sorts the specified edges by weight, ascending, in place.
     *
     * Detailed explanation of:
     * - Purpose: Puts the edges into the order the greedy selection relies on,
     *   namely the order in which they are safe to consider.
     * - Business context: The correctness of the selection depends on the
     *   cheapest available edge always being the next one examined, which only
     *   holds once every edge has been arranged by weight. A merge sort is used
     *   rather than a repeated scan for the minimum, since the latter would
     *   cost as much as this sort already does the very first time every edge
     *   must be examined once, and would cost far more on every subsequent one.
     * - Processing steps: Allocates one buffer sized to the input and delegates
     *   to a recursive merge sort that halves the array, orders each half, and
     *   merges the two ordered halves back together through that shared
     *   buffer.
     * - Assumptions: None beyond the array being non-null, which the caller has
     *   already ensured by constructing it.
     * - Side effects: Reorders the elements of the supplied array; allocates
     *   one buffer for the whole sort.
     *
     * Time complexity: O(e log e) in the number of edges.
     * Space complexity: O(e) for the one buffer allocated and shared by every
     * merge step, plus O(log e) recursion depth.
     *
     * @param pEdges
     * The edges to sort, reordered in place. Must not be null.
     */
    private static void sortByWeight(Edge[] pEdges) {
        if (pEdges.length < 2) {
            // Zero or one edge is already sorted, and allocating a buffer for
            // it would spend more than the sort could ever save.
            return;
        }

        Edge[] buffer = new Edge[pEdges.length];
        mergeSort(pEdges, buffer, 0, pEdges.length);
    }

    /**
     * Sorts the elements of the specified range by weight, ascending, in
     * place.
     *
     * Detailed explanation of:
     * - Purpose: Performs one level of the divide-and-conquer recursion the
     *   sort is built from.
     * - Business context: A range of one element is trivially in order; a
     *   longer range is put in order by ordering its two halves independently
     *   and then merging the two ordered halves, which is cheaper than
     *   ordering the whole range directly because each half is itself sorted
     *   this same way, all the way down to ranges too short to need it.
     * - Processing steps:
     *   1. Stop when the range holds fewer than two elements.
     *   2. Sort the first half and the second half, each by this same method.
     *   3. Merge the two ordered halves through the shared buffer.
     * - Assumptions: Assumes the buffer is at least as long as the array it is
     *   paired with, which the caller guarantees by allocating it to that
     *   length.
     * - Side effects: Reorders the elements of the range within pEdges; writes
     *   into and reads back from pBuffer without retaining anything in it
     *   afterwards.
     *
     * Time complexity: O(k log k) for a range of k elements, counting the two
     * recursive calls together with the merge that follows them.
     * Space complexity: O(1) beyond the buffer supplied by the caller, plus
     * O(log k) recursion depth for a range of k elements.
     *
     * @param pEdges
     * The array holding the range to sort. Must not be null.
     * @param pBuffer
     * Scratch space at least as long as pEdges, shared by every call this sort
     * makes. Must not be null.
     * @param pFrom
     * Index of the first element of the range, inclusive. Must be within
     * pEdges.
     * @param pTo
     * Index one past the last element of the range, exclusive. Must not exceed
     * the length of pEdges.
     */
    private static void mergeSort(Edge[] pEdges, Edge[] pBuffer, int pFrom, int pTo) {
        if (pTo - pFrom < 2) {
            return;
        }

        int middle = pFrom + (pTo - pFrom) / 2;
        mergeSort(pEdges, pBuffer, pFrom, middle);
        mergeSort(pEdges, pBuffer, middle, pTo);
        merge(pEdges, pBuffer, pFrom, middle, pTo);
    }

    /**
     * Merges two adjacent, already-ordered ranges of the specified array into
     * one ordered range, ascending by weight.
     *
     * Detailed explanation of:
     * - Purpose: Combines two ranges already sorted by the recursion into one
     *   range covering both, in order.
     * - Business context: This is the step that does the actual comparing and
     *   ordering; the recursive splitting merely arranges for it to be applied
     *   to ranges short enough, eventually single elements, that it always has
     *   two already-ordered inputs to work with.
     * - Processing steps: Walks both ranges from their front, repeatedly
     *   taking the smaller of the two elements currently under consideration
     *   into the shared buffer, then appends whichever range still has
     *   elements left once the other is exhausted, and finally copies the
     *   assembled result back over the original range.
     * - Assumptions: Assumes both ranges, pFrom to pMiddle and pMiddle to pTo,
     *   are already individually ordered by weight, which the recursive calls
     *   that precede this one guarantee.
     * - Side effects: Overwrites the buffer positions pFrom to pTo and then
     *   the corresponding positions of pEdges with the merged result.
     *
     * Ties are broken in favour of the first range, which keeps the sort
     * stable: two edges of equal weight keep the relative order they had
     * before sorting. Nothing in this algorithm depends on that stability, but
     * it costs nothing to keep and makes a run reproducible when several edges
     * tie on weight.
     *
     * Time complexity: O(k) for a merged range of k = pTo - pFrom elements,
     * one comparison-or-copy per position.
     * Space complexity: O(1) beyond the buffer supplied by the caller.
     *
     * @param pEdges
     * The array holding both ranges to merge and receiving the merged result.
     * Must not be null.
     * @param pBuffer
     * Scratch space at least as long as pEdges. Must not be null.
     * @param pFrom
     * Index of the first element of the first range, inclusive. Must be within
     * pEdges.
     * @param pMiddle
     * Index of the first element of the second range, inclusive, and one past
     * the last element of the first range, exclusive. Must lie between pFrom
     * and pTo.
     * @param pTo
     * Index one past the last element of the second range, exclusive. Must not
     * exceed the length of pEdges.
     */
    private static void merge(Edge[] pEdges, Edge[] pBuffer, int pFrom, int pMiddle, int pTo) {
        int left = pFrom;
        int right = pMiddle;
        int position = pFrom;

        while (left < pMiddle && right < pTo) {
            if (pEdges[left].getWeight() <= pEdges[right].getWeight()) {
                pBuffer[position] = pEdges[left];
                left = left + 1;
            } else {
                pBuffer[position] = pEdges[right];
                right = right + 1;
            }
            position = position + 1;
        }

        while (left < pMiddle) {
            pBuffer[position] = pEdges[left];
            left = left + 1;
            position = position + 1;
        }

        while (right < pTo) {
            pBuffer[position] = pEdges[right];
            right = right + 1;
            position = position + 1;
        }

        for (int index = pFrom; index < pTo; index++) {
            pEdges[index] = pBuffer[index];
        }
    }

}
