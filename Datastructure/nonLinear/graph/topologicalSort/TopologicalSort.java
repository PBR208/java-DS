package nonLinear.graph.topologicalSort;

import nonLinear.graph.base.Graph;
import nonLinear.graph.base.Vertex;

import linear.list.SinglyLinkedList;
import linear.queue.Queue;

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
 *
 * Complexity summary, with v as the number of vertices, a as the number of arcs
 * and n as the cost the underlying representation charges for one neighbour
 * query:
 * - construction, which performs the whole computation: O(v * n + a * v); each
 *   vertex is asked for its neighbours twice, once while counting and once while
 *   discharging, and each reported neighbour costs a lookup of its position in
 *   the snapshot
 * - hasCycle: O(1)
 * - getOrder: O(v), one append per vertex
 * - getUnorderedVertices: O(1) when the graph could be ordered, O(v * v) when it
 *   could not, that being a diagnostic path taken only after a failure
 * - overall space: O(v); the snapshot, the order, and the counts and queue that
 *   the placement discards when it finishes
 *
 * The bound this algorithm is usually quoted with, O(v + e), assumes both a
 * representation that hands back the neighbours of a vertex in time proportional
 * to their number and a constant-time way of turning a vertex into an index. This
 * library provides neither: the list-based representations examine their whole
 * edge collection per neighbour query and the snapshot is searched linearly, so a
 * run costs O(v * e + a * v) over them and O(v * v + a * v) over the adjacency
 * matrix. The algorithm itself remains linear in the graph; what is quadratic
 * here is the access to it.
 *
 * The placement visits each vertex once and each connection twice regardless of
 * the shape of the graph, so there is no worst case to speak of and no early
 * exit to hope for. A circular dependency does not make the work longer, it
 * merely makes it stop sooner, since the vertices caught in the circle are never
 * placed and never have their neighbours discharged.
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
     * The vertices in the order they were placed, filled from the front.
     *
     * Allocated for every vertex of the graph, but only the first entries are
     * meaningful: when a circular dependency prevents a complete ordering, the
     * placement stops early and the remainder of this array stays empty. The
     * boundary between the two parts is what the count below records, and reading
     * this array without it would present a prefix of an ordering as though it
     * were a whole one.
     */
    private final Vertex[] order;

    /**
     * How many vertices could be placed.
     *
     * Equal to the number of vertices exactly when the graph admits a complete
     * ordering, and smaller by the number of vertices caught in or behind a circle
     * otherwise. This single number therefore carries both the extent of the
     * result and the diagnosis, which is why the placement returns it rather than
     * a success flag.
     */
    private final int orderedCount;

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
        this.order = new Vertex[vertices.length];

        // Place the vertices that are free, one after another; the count reports
        // how far that got and thereby whether the graph admits an order at all.
        this.orderedCount = placeFreeVertices(pGraph);
    }

    /**
     * Places the vertices one after another, always taking one that nothing
     * outstanding leads to.
     *
     * Detailed explanation of:
     * - Purpose: Produces the ordering, and reports by its length whether the
     *   graph admits one at all.
     * - Business context: This is the algorithm, and it follows the definition of
     *   the problem directly. A vertex nothing points at depends on nothing and may
     *   be placed at once; placing it discharges it as a dependency of everything
     *   it leads to, which may in turn leave some of those vertices free. Keeping
     *   the freed vertices in a queue and taking them one at a time therefore
     *   builds the sequence from the front, and the only way for it to stop short
     *   is for every remaining vertex to be waiting on another remaining vertex,
     *   which is a circle. The procedure thus produces the order and the diagnosis
     *   in one pass, without ever having to look for a circle explicitly.
     * - Processing steps:
     *   1. Count, for every vertex, how many vertices lead to it.
     *   2. Put every vertex with a count of zero into the queue of free vertices.
     *   3. Take a free vertex, place it, and lower the count of each of its
     *      neighbours, adding those that reach zero to the queue.
     *   4. Stop when no free vertex is left and report how many were placed.
     * - Assumptions: Assumes the counts describe the graph as the neighbour query
     *   reports it, and that the graph does not change while the placement runs.
     * - Side effects: Fills the order array from the front.
     *
     * A vertex is added to the queue exactly when its count reaches zero, which is
     * once, since the count is only ever lowered and never raised. That is what
     * keeps every vertex in the result exactly once and makes the equality test
     * against zero, rather than a test for zero or less, the correct one: reaching
     * zero is an event, whereas being at zero is a state that would be observed
     * again and again.
     *
     * Which of several valid orderings is produced depends on the order the queue
     * hands the free vertices back, and thereby on the order the graph reported
     * its vertices in. Any of them satisfies every constraint equally, and the
     * choice among them carries no meaning; a caller needing a particular
     * tie-breaking rule, such as alphabetical among the free vertices, needs a
     * different collection here rather than a different algorithm.
     *
     * Time complexity: O(v * n + a) with v vertices, a arcs and n as the cost of
     * one neighbour query, counting the initial pass and the placement together:
     * each vertex is placed at most once and each connection is examined at most
     * twice, once while counting and once while discharging.
     * Space complexity: O(v) for the counts and the queue, both of which are
     * discarded when this method returns.
     *
     * @param pGraph
     * The graph to order. Must not be null, which the caller has already ensured.
     *
     * @return
     * The number of vertices placed, which equals the number of vertices of the
     * graph exactly when a complete ordering exists.
     */
    private int placeFreeVertices(Graph pGraph) {
        /*
         * How many vertices each vertex is still waiting for. The counts are
         * consumed by the placement below and are meaningless afterwards, which is
         * why they are held locally rather than as a field.
         */
        int[] outstanding = countIncoming(pGraph);

        /*
         * The vertices that may be placed right now. A queue is used because the
         * order among free vertices is unconstrained and any discipline would do;
         * a queue keeps the result close to the order the graph reported its
         * vertices in, which makes it reproducible and easy to read.
         */
        Queue<Vertex> free = new Queue<>();

        // Everything that depends on nothing is free from the outset. A graph
        // without such a vertex is circular throughout and yields nothing at all.
        for (int index = 0; index < vertices.length; index++) {
            if (outstanding[index] == 0) {
                free.enqueue(vertices[index]);
            }
        }

        int placed = 0;

        while (!free.isEmpty()) {
            // Take the next free vertex; the queue separates reading from
            // removing, so both steps are needed here.
            Vertex current = free.front();
            free.dequeue();

            order[placed] = current;
            placed = placed + 1;

            /*
             * Placing this vertex discharges it as a dependency of everything it
             * leads to. Over a directed graph those are its successors; over an
             * undirected one they are all of its neighbours, which is why an
             * undirected edge makes both of its endpoints wait for each other.
             */
            SinglyLinkedList<Vertex> neighbours = pGraph.getNeighbours(current);
            neighbours.toFirst();
            while (neighbours.hasAccess()) {
                int neighbourIndex = indexOf(neighbours.getContent());

                if (neighbourIndex != NO_VERTEX) {
                    outstanding[neighbourIndex] = outstanding[neighbourIndex] - 1;

                    // Reaching zero is the moment a vertex becomes free, and it
                    // happens exactly once per vertex.
                    if (outstanding[neighbourIndex] == 0) {
                        free.enqueue(vertices[neighbourIndex]);
                    }
                }
                neighbours.next();
            }
        }

        return placed;
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

    /**
     * Reports whether the graph contains a circular dependency.
     *
     * Detailed explanation of:
     * - Purpose: States whether an ordering exists at all.
     * - Business context: This is the check a caller makes before doing anything
     *   with the order, and for many callers it is the point of running the
     *   algorithm: a build system asking whether its targets can be built in any
     *   order at all, a schedule asking whether its constraints are satisfiable, a
     *   package manager asking whether a dependency has come round on itself. The
     *   answer is derived from the count of placed vertices rather than from a
     *   separate search, because a vertex is placed exactly when nothing
     *   outstanding leads to it, and vertices left over at the end can only be
     *   waiting on one another.
     * - Processing steps: Compares the number of placed vertices against the number
     *   of vertices in the graph.
     * - Assumptions: None beyond the placement having run, which the constructor
     *   guarantees.
     * - Side effects: None.
     *
     * An undirected graph holding any edge reports a cycle here, and truthfully
     * so: an undirected edge is a mutual dependency, which is a circle of length
     * two. A caller passing an undirected graph and expecting an ordering has
     * asked a question that has no answer, rather than met a limitation of this
     * class.
     *
     * Time complexity: O(1); both numbers are held as fields.
     * Space complexity: O(1).
     *
     * @return
     * True when at least one vertex could not be placed and no complete ordering
     * exists; false when every vertex was placed, in which case the reported order
     * covers the whole graph. An empty graph reports false, there being no
     * constraint it fails to satisfy.
     */
    public boolean hasCycle() {
        return orderedCount != vertices.length;
    }

    /**
     * Reports the vertices in an order that respects every connection.
     *
     * Detailed explanation of:
     * - Purpose: Hands over the sequence the algorithm produced.
     * - Business context: This is the result the class exists for, and it is meant
     *   to be consumed in sequence: processing the vertices in this order
     *   guarantees that whatever a vertex leads to comes after it, which is exactly
     *   what a build, an installation or a schedule needs. The order is one of
     *   possibly many valid ones and carries no further meaning: two vertices
     *   unrelated by any chain of connections may appear in either order here, and
     *   a caller must not read their positions as a statement that one must
     *   precede the other.
     * - Processing steps: Copies the placed vertices into a fresh list, unless the
     *   placement was incomplete, in which case an empty list is reported.
     * - Assumptions: Assumes the placement filled the order array from the front,
     *   which it does.
     * - Side effects: None; a new list is allocated per call.
     *
     * A graph with a circular dependency is answered with an empty list rather
     * than with the vertices that could still be placed. The prefix would look
     * exactly like a complete ordering and would very likely be acted upon as one,
     * which is a worse failure than being given nothing; a caller wanting to know
     * what stood in the way asks for the unordered vertices instead.
     *
     * Time complexity: O(v) in the number of vertices; one append per placed
     * vertex.
     * Space complexity: O(v) for the returned list, which holds references to the
     * graph's own vertex instances.
     *
     * @return
     * A new list holding every vertex of the graph in a valid order, positioned at
     * its first element. Empty when the graph contains a circular dependency, and
     * equally empty for a graph without vertices; the two are distinguished by
     * asking whether a cycle was found. Never null.
     */
    public SinglyLinkedList<Vertex> getOrder() {
        SinglyLinkedList<Vertex> result = new SinglyLinkedList<>();

        // Without a complete ordering there is nothing to hand over, since a
        // partial one cannot be recognised as partial by the caller.
        if (hasCycle()) {
            return result;
        }

        for (int position = 0; position < orderedCount; position++) {
            result.append(order[position]);
        }

        result.toFirst();
        return result;
    }

    /**
     * Reports the vertices that could not be placed.
     *
     * Detailed explanation of:
     * - Purpose: Names the vertices caught in a circular dependency or waiting
     *   behind one.
     * - Business context: Knowing that a graph cannot be ordered is rarely enough;
     *   the caller has to repair it, and for that it needs to know where to look.
     *   These are the vertices whose dependencies never all cleared, which is the
     *   circle itself together with everything downstream of it. That is a wider
     *   set than the circle alone, and deliberately so: a vertex behind a circle is
     *   just as unschedulable as one in it, and narrowing the report to the circle
     *   would require a second search for information the caller can obtain by
     *   following the graph from any of these vertices.
     * - Processing steps: Marks every placed vertex and collects the vertices of
     *   the snapshot that were not marked, in the order the graph reported them.
     * - Assumptions: Assumes vertices are compared by identity, so that the
     *   placed vertices can be located in the snapshot.
     * - Side effects: None; a new list is allocated per call.
     *
     * Time complexity: O(1) when the graph could be ordered completely, which is
     * recognised before anything is examined; O(v * v) otherwise, one lookup in the
     * snapshot per placed vertex. The quadratic case is a diagnostic path taken
     * only when the ordering has already failed.
     * Space complexity: O(v) for the marks and the returned list.
     *
     * @return
     * A new list holding every vertex that could not be placed, in the order the
     * graph reported its vertices, positioned at its first element. Empty exactly
     * when the graph admits a complete ordering. Never null.
     */
    public SinglyLinkedList<Vertex> getUnorderedVertices() {
        SinglyLinkedList<Vertex> result = new SinglyLinkedList<>();

        // Everything was placed, so nothing was left over; recognising this here
        // keeps the ordinary case free of the marking below.
        if (!hasCycle()) {
            return result;
        }

        /*
         * Mark what was placed. The placement recorded the vertices themselves
         * rather than their positions, so each one is located in the snapshot
         * again; this runs only when an ordering has already failed.
         */
        boolean[] placed = new boolean[vertices.length];
        for (int position = 0; position < orderedCount; position++) {
            int index = indexOf(order[position]);

            if (index != NO_VERTEX) {
                placed[index] = true;
            }
        }

        // Whatever remains never became free, which means it is waiting, directly
        // or at a distance, on a vertex that is waiting on it.
        for (int index = 0; index < vertices.length; index++) {
            if (!placed[index]) {
                result.append(vertices[index]);
            }
        }

        result.toFirst();
        return result;
    }

}
