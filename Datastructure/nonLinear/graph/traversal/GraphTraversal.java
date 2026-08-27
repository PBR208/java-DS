package nonLinear.graph.traversal;

import nonLinear.graph.base.Graph;
import nonLinear.graph.base.Vertex;

import linear.list.SinglyLinkedList;
import linear.queue.Queue;
import linear.stack.Stack;

/**
 * Purpose:
 * Provides the two systematic ways of walking a graph from a starting vertex,
 * breadth-first and depth-first, and the reachability question they answer. Both
 * walks solve the same problem, namely that a graph offers no order of its own:
 * unlike a list or a tree, it has no first element and no defined succession, it
 * may lead back to where the walk began, and the same vertex may be offered by
 * several of its neighbours, so any procedure visiting it must impose an order
 * and must remember what it has already seen or it will not terminate. The two
 * walks differ only in which of the discovered vertices is taken up next, the
 * oldest one or the newest, and that single decision is what makes one of them
 * explore the graph in rings of equal distance around the start and the other
 * follow a single path as far as it leads before turning back. This class exists
 * so that the choice is expressed once rather than rewritten inside every
 * algorithm needing it, and it is written against the graph contract of this
 * package rather than against any one representation, so that the same walk runs
 * over the adjacency list, the adjacency matrix and the directed graph alike and
 * reports, in each case, what that representation regards as reachable.
 *
 * Owner:
 * PBR208 - https://github.com/PBR208/
 *
 * Version:
 * 1.0
 */

/**
 * Breadth-first and depth-first traversal over any graph implementing the graph
 * contract of this package.
 *
 * Responsibility: Encapsulates the bookkeeping both walks require, namely the
 * order in which discovered vertices are taken up, the record of what has already
 * been visited, and the collection of the visiting order into a result the caller
 * can read. It maintains the guarantee that every vertex reachable from the start
 * appears exactly once in a returned order and that no unreachable vertex appears
 * at all.
 *
 * Scope: Used wherever a graph has to be walked rather than merely queried, for
 * instance to enumerate what a vertex can reach, to test whether a route exists,
 * or as the frame that a more specific algorithm fills in. It is not itself a
 * data structure and holds no graph content: it reads a graph it does not own and
 * reports orders over the vertices that graph already holds.
 *
 * Dependencies: Depends on the Graph contract and on Vertex, on SinglyLinkedList
 * for the reported order, and on the Queue and the Stack of the linear package
 * for the pending vertices of the two walks. Those two dependencies are the
 * substance of the difference between the walks rather than an implementation
 * detail: a queue hands back the oldest discovery and a stack the newest, and
 * exchanging one for the other turns either walk into the other.
 *
 * Thread-safety: This class is not thread-safe, and the reason lies outside it.
 * The record of what has been visited is kept in the marks of the vertices
 * themselves, which belong to the graph and are shared by everything holding it,
 * so two traversals running at the same time over one graph would overwrite each
 * other's bookkeeping even though each holds its own pending collection. External
 * synchronization is required, and two traversals of the same graph must in any
 * case be run one after the other.
 *
 * Lifecycle: An instance is bound to one graph at construction and keeps it for
 * its whole lifetime. It holds no state between calls, so a single instance may
 * be reused for any number of walks, and the graph may change between them: each
 * walk reads the graph as it stands when it is started.
 *
 * Architectural role: Serves as the first algorithm of this repository as opposed
 * to a container, and as the demonstration of what the shared graph contract is
 * for. It never asks which representation it is walking, only what the neighbours
 * of a vertex are, which is why it works unchanged over a directed graph and
 * there reports precisely the vertices reachable along the direction of the arcs.
 *
 * Complexity summary, with v as the number of vertices, e as the number of edges
 * and n as the cost the underlying representation charges for a single neighbour
 * query:
 * - construction: O(1) time, O(1) space
 * - breadthFirst: O(v * n + e) time, O(v) space
 * - depthFirst: O(v * n + e) time, O(e) space
 * - isReachable: O(v * n + e) time, O(v) space
 *
 * The bound both walks are usually quoted with, O(v + e), assumes a
 * representation that hands back the neighbours of a vertex in time proportional
 * to their number. None of the representations of this package does: the
 * adjacency list and the directed graph examine every edge for each query, which
 * makes a walk O(v * e) over them, and the adjacency matrix examines one row,
 * which makes it O(v * v) there. That is a property of what is being walked
 * rather than of the walk, and it is stated here because the difference is large
 * enough to decide which representation a graph should be held in when it is
 * walked often. The walks themselves take up each vertex at most once and examine
 * each edge at most once from either end it is reachable through.
 *
 * The space bounds differ between the two walks for a reason worth keeping in
 * mind when choosing between them on a large graph. The breadth-first queue holds
 * each vertex at most once, because a vertex is marked as it enters; the
 * depth-first stack may hold a vertex once per edge leading to it, because
 * marking has to be deferred until a vertex is taken up for the order to be a
 * depth-first order at all. Both walks are iterative, so neither consumes call
 * stack in proportion to the depth of the graph, which is what a recursive
 * depth-first walk would do and what would make a long chain of vertices fail
 * where these walks succeed.
 *
 * Neither walk allocates anything per vertex beyond the pending collection and
 * the reported order, since the record of what has been visited is kept in the
 * marks the graph already carries. That is what makes the marks part of this
 * class's contract rather than an implementation detail: they are cleared when a
 * walk begins and left set on exactly the visited vertices when it ends, so the
 * unmarked vertices afterwards are precisely those the start could not reach.
 */
public class GraphTraversal {

    /**
     * The graph this instance walks.
     *
     * Held final because the bookkeeping of a walk lives in the marks of that
     * graph's vertices: exchanging the graph afterwards would leave an instance
     * whose walks clear the marks of one graph and read the neighbours of
     * another. The graph is referenced rather than copied, so changes made to it
     * between two walks are visible to the second of them.
     */
    private final Graph graph;

    /**
     * Constructs a traversal bound to the specified graph.
     *
     * Detailed explanation of:
     * - Purpose: Fixes the graph every walk of this instance will read.
     * - Business context: The graph is supplied once rather than per call because
     *   a caller walking a graph normally walks it repeatedly, from several
     *   starting vertices or with both strategies, and because binding it here
     *   makes it impossible to start a walk with one graph and finish it with
     *   another. The traversal deliberately takes the contract rather than a
     *   concrete representation, which is what allows the same instance to be
     *   constructed over an adjacency list, an adjacency matrix or a directed
     *   graph without any of the walks below knowing the difference.
     * - Processing steps: Rejects a null graph and stores the reference.
     * - Assumptions: Assumes the graph honours the contract of its interface, in
     *   particular that the neighbour query returns a list positioned at its first
     *   element and never returns null.
     * - Side effects: None; the graph is neither read nor modified at
     *   construction.
     *
     * Time complexity: O(1); one comparison and one assignment.
     * Space complexity: O(1); the graph is referenced, not copied.
     *
     * @param pGraph
     * The graph to walk. Must not be null. May be empty, in which case every walk
     * reports an empty order, and may be modified between walks, each of which
     * reads the graph as it stands at its own start.
     *
     * @throws IllegalArgumentException
     * Thrown when pGraph is null. Without a graph the instance could answer
     * nothing at all, and reporting that at construction is more useful than
     * failing inside the first walk, where the cause would be further from the
     * mistake.
     */
    public GraphTraversal(Graph pGraph) {
        // A traversal without a graph has nothing to walk and no way to acquire
        // one later, since the binding is deliberately permanent.
        if (pGraph == null) {
            throw new IllegalArgumentException("The graph must not be null.");
        }

        this.graph = pGraph;
    }

    /**
     * Reports whether the specified vertex is one this graph currently holds.
     *
     * Detailed explanation of:
     * - Purpose: Provides the single place in which a starting vertex is accepted
     *   or rejected before a walk begins.
     * - Business context: A walk started from a vertex the graph does not hold
     *   would report that vertex as visited and would then ask the graph for its
     *   neighbours, which the representations of this package answer with an empty
     *   list, so the caller would receive a one-element order describing something
     *   that is not part of the graph at all. Refusing such a vertex up front
     *   turns that misleading answer into an empty one. The check is by identity
     *   rather than by identifier, so a detached vertex merely carrying an
     *   identifier that is in use here is refused as well; this mirrors what the
     *   graph implementations of this package do when they validate the endpoints
     *   of an edge.
     * - Processing steps: Rejects a null vertex and a vertex without an
     *   identifier, then compares the instance the graph holds under that
     *   identifier against the vertex itself.
     * - Assumptions: Assumes identifiers are unique within a graph, which the
     *   implementations of this package enforce on insertion, so that the lookup
     *   can return at most one candidate.
     * - Side effects: None on the graph content, though the lookup moves the
     *   internal cursor of the representation being asked.
     *
     * Time complexity: O(v) in the number of vertices for the list-based
     * representations, which scan for the identifier; determined by the
     * implementation in general.
     * Space complexity: O(1); nothing is allocated.
     *
     * @param pVertex
     * The vertex to check. Any value is accepted, including null and a vertex
     * belonging to a different graph, since deciding exactly those cases is the
     * purpose of this method.
     *
     * @return
     * True when the graph holds this very vertex instance; false when it is null,
     * carries no identifier, or is not the instance the graph holds under it.
     */
    private boolean belongsToGraph(Vertex pVertex) {
        // A vertex without identity cannot be looked up, and no graph of this
        // package accepts one, so it can never be a member.
        if (pVertex == null || pVertex.getID() == null) {
            return false;
        }

        // Identity rather than equality of identifiers: two distinct instances
        // sharing an identifier are two different vertices, and only the one the
        // graph holds has neighbours in it.
        return graph.getVertex(pVertex.getID()) == pVertex;
    }

    /**
     * Walks the graph breadth-first from the specified vertex and reports the
     * order in which the vertices were visited.
     *
     * Detailed explanation of:
     * - Purpose: Visits every vertex reachable from the start, taking up the
     *   discovered vertices in the order they were discovered.
     * - Business context: Because the oldest discovery is always taken up first,
     *   the walk leaves the start along all of its neighbours before it follows
     *   any of them further, and then along all of their neighbours, so the
     *   vertices appear in the order of their distance from the start measured in
     *   number of edges. That property is what this walk is chosen for: the first
     *   time a vertex is discovered, it is discovered along a shortest route in
     *   edges, which makes this the basis of shortest-path search in an unweighted
     *   graph and of any question about how far apart two vertices are. It is also
     *   the walk to prefer on a graph that is wide but not deep, since it never
     *   descends further than it must.
     * - Processing steps:
     *   1. Refuse a start vertex the graph does not hold, reporting an empty
     *      order.
     *   2. Clear the marks of all vertices, so that the walk begins from a known
     *      state regardless of what ran before it.
     *   3. Mark the start and place it in the queue of pending vertices.
     *   4. While vertices are pending, take the oldest, record it in the result,
     *      and place every neighbour that is not yet marked into the queue,
     *      marking it as it goes in.
     * - Assumptions: Assumes the graph reports the neighbours of a vertex without
     *   modifying the graph, which the contract requires, and that no other
     *   traversal is running over the same graph, whose marks this one uses as its
     *   record.
     * - Side effects: Clears every vertex mark at the start and leaves the marks
     *   of exactly the visited vertices set when it returns. That residue is
     *   deliberately not cleaned up, because it answers a question the result list
     *   does not: after the call, the unmarked vertices are precisely those the
     *   start cannot reach, so a caller may ask the graph whether all vertices are
     *   marked to learn whether the graph, read from this vertex, covers
     *   everything.
     *
     * A vertex is marked when it enters the queue rather than when it leaves it,
     * which is the detail the correctness of this walk rests on. Several vertices
     * may name the same neighbour, and marking on entry is what keeps that
     * neighbour from being queued once per naming and consequently from appearing
     * several times in the result. Marking on exit would still terminate, since
     * the marks would eventually catch up, but the queue could grow to the number
     * of edges and a vertex could be reported more than once.
     *
     * Time complexity: O(v * n + e) with v vertices and e edges, where n is the
     * cost the underlying representation charges for one neighbour query; each
     * vertex is taken up at most once and each edge is examined at most once from
     * each end it is reachable through. Over the list-based representations of
     * this package a neighbour query scans the whole edge collection, so the walk
     * costs O(v * e) there, and only a representation holding an adjacency list
     * per vertex reaches the O(v + e) that this walk is usually quoted with.
     * Space complexity: O(v); the queue holds at most every vertex once, and the
     * result holds every visited vertex exactly once. The marks add nothing, since
     * they live on the vertices the graph already holds.
     *
     * @param pStartVertex
     * The vertex to start from. Must be an instance the graph currently holds; a
     * null vertex, or one belonging to another graph, yields an empty order rather
     * than an exception, which matches how the graph implementations of this
     * package answer a request they cannot serve.
     *
     * @return
     * A new list holding every vertex reachable from the start, the start itself
     * first and the remaining vertices in non-decreasing distance from it,
     * positioned at its first element. Empty exactly when the start vertex is not
     * one of this graph's. Never null.
     */
    public SinglyLinkedList<Vertex> breadthFirst(Vertex pStartVertex) {
        SinglyLinkedList<Vertex> visitingOrder = new SinglyLinkedList<>();

        // A vertex the graph does not hold has no neighbours here, and reporting
        // it as visited would describe a walk through something outside the graph.
        if (!belongsToGraph(pStartVertex)) {
            return visitingOrder;
        }

        /*
         * Start from a known state. The marks belong to the graph and survive
         * whatever ran before this call, so a walk that skipped this step could
         * mistake the residue of an earlier one for its own progress and would
         * report only part of what is reachable.
         */
        graph.setAllVertexMarks(false);

        // Pending vertices are held in a queue, and that choice is the whole
        // difference to the depth-first walk: the queue hands back the oldest
        // discovery, which is what keeps the exploration close to the start.
        Queue<Vertex> pending = new Queue<>();

        // The start is discovered by definition, so it is marked and queued
        // before the loop rather than inside it.
        pStartVertex.setMark(true);
        pending.enqueue(pStartVertex);

        while (!pending.isEmpty()) {
            // Take the oldest pending vertex. The queue separates reading from
            // removing, so both steps are needed here.
            Vertex current = pending.front();
            pending.dequeue();

            // Every vertex that reaches this point has been marked already and is
            // therefore recorded exactly once.
            visitingOrder.append(current);

            /*
             * Ask the graph, not the representation: whatever getNeighbours means
             * for the graph at hand is what this walk follows. Over a directed
             * graph that is the successors alone, which is precisely why the same
             * code reports directed reachability there.
             */
            SinglyLinkedList<Vertex> neighbours = graph.getNeighbours(current);
            neighbours.toFirst();
            while (neighbours.hasAccess()) {
                Vertex neighbour = neighbours.getContent();

                // Mark on entry, not on exit: a neighbour named by several
                // vertices must be queued once, or it would be reported several
                // times over.
                if (!neighbour.isMarked()) {
                    neighbour.setMark(true);
                    pending.enqueue(neighbour);
                }
                neighbours.next();
            }
        }

        // Hand the order back ready to be read, as every list-returning operation
        // of this package does.
        visitingOrder.toFirst();
        return visitingOrder;
    }

    /**
     * Walks the graph depth-first from the specified vertex and reports the order
     * in which the vertices were visited.
     *
     * Detailed explanation of:
     * - Purpose: Visits every vertex reachable from the start, always taking up
     *   the most recently discovered vertex next.
     * - Business context: Because the newest discovery is taken up first, the walk
     *   follows one route as far as it leads before it returns to the most recent
     *   junction and tries the next branch there. That is the shape a great many
     *   graph algorithms need rather than merely prefer: cycle detection, the
     *   ordering of dependencies and the search for strongly connected components
     *   all rest on the fact that a depth-first walk finishes everything below a
     *   vertex before it leaves that vertex behind. It is also the walk to prefer
     *   on a graph that is deep but narrow, where the breadth-first queue would
     *   hold a whole level at once for no benefit.
     * - Processing steps:
     *   1. Refuse a start vertex the graph does not hold, reporting an empty
     *      order.
     *   2. Clear the marks of all vertices.
     *   3. Push the start onto the stack of pending vertices.
     *   4. While vertices are pending, take the newest; skip it when it has been
     *      visited in the meantime, otherwise mark it, record it, and push its
     *      unmarked neighbours.
     * - Assumptions: Assumes, as the breadth-first walk does, that the graph
     *   reports neighbours without modifying itself and that no other traversal is
     *   using the marks at the same time.
     * - Side effects: Clears every vertex mark at the start and leaves the marks
     *   of exactly the visited vertices set, which afterwards identifies the
     *   unreachable vertices as the unmarked ones.
     *
     * The bookkeeping differs from the breadth-first walk in one deliberate
     * respect: a vertex is marked when it is taken off the stack, not when it is
     * pushed. Pushing the same vertex several times is therefore possible, and the
     * mark test on removal is what discards the repeats. The difference is forced
     * by what depth-first order means. A vertex discovered from two junctions must
     * be visited from the later one, since the walk has to finish that branch
     * before returning, and marking on entry would freeze the earlier discovery in
     * place and produce an order that is not a depth-first order at all. The price
     * is that the stack may hold a vertex once per edge leading to it, which is
     * why its space bound is stated in edges below while the queue of the
     * breadth-first walk is bounded by the vertices.
     *
     * The order among the branches leaving one vertex is the reverse of the order
     * in which the graph reports its neighbours, because the stack returns them
     * in the opposite order to the one they were pushed in. Any order among
     * branches yields a valid depth-first walk, so this is not corrected; callers
     * that depend on a particular branch order must impose it themselves.
     *
     * Time complexity: O(v * n + e) with v vertices and e edges, where n is the
     * cost of one neighbour query in the underlying representation, giving
     * O(v * e) over the list-based representations of this package for the same
     * reason as the breadth-first walk.
     * Space complexity: O(e); the stack may hold one entry per edge that leads to
     * an unvisited vertex, and the result holds every visited vertex exactly once.
     *
     * @param pStartVertex
     * The vertex to start from. Must be an instance the graph currently holds; a
     * null vertex, or one belonging to another graph, yields an empty order.
     *
     * @return
     * A new list holding every vertex reachable from the start, the start itself
     * first and the remaining vertices in a depth-first order, positioned at its
     * first element. The set of reported vertices is exactly the same as that of
     * the breadth-first walk from the same start; only the order differs. Empty
     * exactly when the start vertex is not one of this graph's. Never null.
     */
    public SinglyLinkedList<Vertex> depthFirst(Vertex pStartVertex) {
        SinglyLinkedList<Vertex> visitingOrder = new SinglyLinkedList<>();

        // As with the breadth-first walk, a vertex outside the graph is refused
        // rather than reported as a one-vertex order.
        if (!belongsToGraph(pStartVertex)) {
            return visitingOrder;
        }

        // Begin from a known state; the marks outlive whatever ran before.
        graph.setAllVertexMarks(false);

        // Pending vertices are held in a stack, which hands back the newest
        // discovery and thereby produces the descent this walk is named for.
        Stack<Vertex> pending = new Stack<>();

        // The start is pushed unmarked: like every other vertex, it is marked
        // when it is taken up, which keeps the loop below to a single rule.
        pending.push(pStartVertex);

        while (!pending.isEmpty()) {
            // Take the newest pending vertex; the stack, like the queue,
            // separates reading from removing.
            Vertex current = pending.top();
            pending.pop();

            /*
             * A vertex may have been pushed several times, from several of its
             * predecessors, and may already have been visited through one of the
             * later pushes. The repeats are discarded here, which is what keeps
             * every vertex in the result exactly once.
             */
            if (!current.isMarked()) {
                current.setMark(true);
                visitingOrder.append(current);

                SinglyLinkedList<Vertex> neighbours = graph.getNeighbours(current);
                neighbours.toFirst();
                while (neighbours.hasAccess()) {
                    Vertex neighbour = neighbours.getContent();

                    /*
                     * Already visited neighbours are not pushed at all. This is an
                     * economy rather than a correctness measure, since the test
                     * above would discard them anyway, but it keeps the stack from
                     * filling with vertices the walk is finished with.
                     */
                    if (!neighbour.isMarked()) {
                        pending.push(neighbour);
                    }
                    neighbours.next();
                }
            }
        }

        visitingOrder.toFirst();
        return visitingOrder;
    }

    /**
     * Reports whether one vertex can be reached from another by following edges.
     *
     * Detailed explanation of:
     * - Purpose: Answers the existence of a route between two vertices without
     *   requiring the caller to walk the graph and search the result itself.
     * - Business context: Reachability is the question a traversal is most often
     *   run for, and phrasing it as its own operation keeps the answer honest in
     *   the one case that is easy to get wrong by hand, namely a directed graph,
     *   where a route from one vertex to another says nothing about a route back.
     *   The direction of the question is therefore fixed by the order of the
     *   arguments, and a caller interested in both directions asks twice.
     * - Processing steps: Walks the graph breadth-first from the first vertex and
     *   scans the reported order for the second.
     * - Assumptions: Assumes reachability along the edges as the graph reports
     *   them, which for a directed graph means along the direction of its arcs.
     * - Side effects: Those of the walk it performs, namely the marks cleared at
     *   the start and left set on every vertex reachable from the first argument.
     *
     * The walk is reused rather than repeated with an early exit, which is a
     * deliberate trade. A search stopping as soon as the target appears would
     * often finish sooner, but it would be a second copy of the traversal, and the
     * two would have to be kept in agreement about what reachable means for the
     * rest of this class to remain consistent. The walk is bounded by the size of
     * the graph in either case, and this class exists to state the walks once.
     *
     * The breadth-first walk is used rather than the depth-first one because the
     * two report the same set of vertices, so the choice is free, and because the
     * breadth-first one holds fewer pending vertices while doing so.
     *
     * Time complexity: O(v * n + e) for the walk, where n is the cost of one
     * neighbour query in the underlying representation, plus O(v) for the scan of
     * its result; the walk dominates.
     * Space complexity: O(v) for the order the walk produces, which is discarded
     * once it has been scanned.
     *
     * @param pFromVertex
     * The vertex to start from, which must be an instance the graph currently
     * holds. A null vertex, or one belonging to another graph, reaches nothing.
     *
     * @param pToVertex
     * The vertex to look for. May be null or foreign to this graph, in which case
     * it appears in no walk and is consequently not reachable.
     *
     * @return
     * True when a route of zero or more edges leads from the first vertex to the
     * second, which includes the case of both arguments being the same vertex of
     * this graph, since a vertex is reachable from itself along the empty route;
     * false otherwise, including when either vertex is not one of this graph's.
     */
    public boolean isReachable(Vertex pFromVertex, Vertex pToVertex) {
        // The walk already refuses a start outside the graph, and it reports the
        // start itself first, which is what makes a vertex reachable from itself
        // without a special case here.
        SinglyLinkedList<Vertex> reached = breadthFirst(pFromVertex);

        reached.toFirst();
        while (reached.hasAccess()) {
            // Identity comparison, as everywhere in this package: the walk reports
            // the very instances the graph holds.
            if (reached.getContent() == pToVertex) {
                return true;
            }
            reached.next();
        }

        // The target did not appear in the reachable set, so no route leads to it.
        return false;
    }

}
