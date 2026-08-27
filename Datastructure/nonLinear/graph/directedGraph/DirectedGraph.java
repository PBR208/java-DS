package nonLinear.graph.directedGraph;

import nonLinear.graph.base.Edge;
import nonLinear.graph.base.Vertex;

import linear.list.SinglyLinkedList;

/**
 * Purpose:
 * Provides a directed graph, in which a connection runs from one vertex to
 * another rather than between the two, and reaching the head of a connection
 * therefore says nothing about being able to travel back to its tail. The
 * structure exists because the two representations already present in this
 * package, the adjacency list and the adjacency matrix, both store every
 * connection symmetrically and expose no direction at all, which makes them
 * unable to model any relation that is not mutual: a one-way street, a
 * dependency between build targets, a link from one page to another, the flow of
 * a supply network. Direction is not an attribute added to such a relation but
 * the whole content of it, and a representation that discards it cannot be
 * repaired by the algorithms reading it afterwards. This class therefore stores
 * each connection exactly once and fixes how its endpoints are to be read, so
 * that the connection from a vertex and the connection to it become two different
 * questions with two different answers, and it exposes both of them separately
 * alongside the operations the graph contract of this package already demands.
 * It is the representation the algorithms that only make sense with direction
 * rest on, among them topological sorting, the detection of strongly connected
 * components and every reachability question in which the direction of travel
 * matters.
 *
 * Owner:
 * PBR208 - https://github.com/PBR208/
 *
 * Version:
 * 1.0
 */

/**
 * List-based directed graph, storing vertices and one-way arcs in singly linked
 * lists.
 *
 * Responsibility: Encapsulates the set of vertices, the set of arcs between
 * them, the direction each arc runs in, and the queries that direction makes
 * meaningful, namely the arcs leaving a vertex as opposed to those arriving at
 * it, the vertices reachable in one step as opposed to those reaching it in one
 * step, and the two degrees derived from them. It maintains the invariant that
 * every arc references two distinct vertices currently registered with this
 * graph and that no two arcs run from the same tail to the same head.
 *
 * Scope: Used within the nonLinear.graph package wherever a relation is
 * one-directional. A relation that is genuinely mutual is better modelled by the
 * adjacency list or the adjacency matrix of the neighbouring packages, which
 * store it once instead of requiring the two opposing arcs this class would need
 * for it.
 *
 * Dependencies: Depends on Vertex and Edge as its structural units and on
 * SinglyLinkedList as its storage, matching the list-based representation of
 * this package. It deliberately introduces no arc type of its own: the Edge class
 * holds its two endpoints in a fixed order, and this class supplies the missing
 * meaning by fixing how that order is read, treating the first endpoint as the
 * tail an arc leaves and the second as the head it arrives at. That reading is
 * confined to this class, which is why the shared Edge type needed no change and
 * why an Edge handed to one of the undirected representations continues to mean
 * exactly what it meant there.
 *
 * Thread-safety: This class is not thread-safe. Structural changes are performed
 * as several list operations in sequence, so a concurrent reader may observe a
 * vertex that has already lost some of its arcs but not all of them. Marks are
 * shared mutable state on the vertices and arcs themselves, so even two
 * concurrently running traversals interfere with one another. External
 * synchronization is required whenever an instance is shared across threads.
 *
 * Lifecycle: A DirectedGraph begins empty and is expected to be built up
 * incrementally, vertices first and arcs afterwards, since an arc is only
 * accepted once both of its endpoints are registered. It stays mutable for its
 * whole lifetime, and removing a vertex also removes every arc touching it, so
 * that no arc can ever reference a vertex the graph no longer holds.
 *
 * Architectural role: Serves as the third representation of this package and as
 * the one that completes it, standing beside the adjacency list and the adjacency
 * matrix rather than replacing either. All three implement the same contract, so
 * an algorithm written against that contract runs unchanged over any of them,
 * which is precisely how a traversal is meant to be reused: it asks for the
 * neighbours of a vertex and receives, from this class, the vertices that can be
 * reached from it, and from the other two, the vertices it is connected to.
 */
public class DirectedGraph {

    /**
     * Position of the tail vertex within the endpoint pair of an arc.
     *
     * An arc leaves its tail. The Edge type stores its endpoints as an ordered
     * pair but attaches no meaning to that order, so this constant and its
     * counterpart are what turn an undirected pair into a direction. They are
     * named rather than written as literals because every query of this class
     * depends on them and a confusion of the two would not fail loudly; it would
     * silently reverse every arc in the graph.
     */
    private static final int TAIL_INDEX = 0;

    /**
     * Position of the head vertex within the endpoint pair of an arc.
     *
     * An arc arrives at its head. Together with the tail index this constant
     * defines the reading of an endpoint pair used throughout this class, and the
     * two are deliberately kept adjacent so that the opposite endpoint of a match
     * can be derived from the matched one rather than passed separately.
     */
    private static final int HEAD_INDEX = 1;

    /**
     * All vertices currently belonging to this graph.
     *
     * Held in a singly linked list to match the list-based representation of this
     * package and to grow without a capacity bound, which suits a graph that is
     * built up incrementally. Vertex identity within the list is by reference, and
     * the identifier is used only to reject a second vertex claiming an
     * identifier that is already taken.
     */
    private SinglyLinkedList<Vertex> vertices;

    /**
     * All arcs currently belonging to this graph.
     *
     * The field is named for arcs rather than for edges because the direction is
     * the entire difference this class makes: an entry of this list denotes the
     * one-way connection from its first endpoint to its second, and the reverse
     * connection, if the graph is meant to hold it, is a second entry of its own.
     * A single flat list is kept rather than an adjacency list per vertex, which
     * makes every adjacency query a scan over all arcs but keeps insertion,
     * removal and the maintenance of the vertex set free of any per-vertex
     * bookkeeping. That is the same trade the adjacency list of this package
     * makes, and it keeps the two comparable.
     */
    private SinglyLinkedList<Edge> arcs;

    /**
     * Constructs a new, empty directed graph holding neither vertices nor arcs.
     *
     * Detailed explanation of:
     * - Purpose: Establishes the two storage lists a graph is assembled into.
     * - Business context: This is the only starting state offered, matching the
     *   other representations of this package. A graph is described by its
     *   content rather than by its size, so there is no capacity to declare here
     *   and nothing a caller could usefully supply at construction.
     * - Processing steps: Allocates one empty list for the vertices and one for
     *   the arcs.
     * - Assumptions: Assumes the caller registers vertices before the arcs
     *   between them, since an arc referencing an unregistered vertex is refused.
     * - Side effects: Allocates two empty lists.
     *
     * Time complexity: O(1); two allocations and no traversal.
     * Space complexity: O(1); both lists start empty and grow only with the
     * content later added to them.
     */
    public DirectedGraph() {
        // Vertices are stored separately from arcs so that an isolated vertex,
        // which no arc references, remains part of the graph.
        vertices = new SinglyLinkedList<>();

        // Arcs are stored in one flat list; the direction of each is carried by
        // the order of its own endpoints rather than by the list it sits in.
        arcs = new SinglyLinkedList<>();
    }

    /**
     * Retrieves a copy of all vertices currently contained in this graph.
     *
     * Detailed explanation of:
     * - Purpose: Provides access to the vertex set without exposing the internal
     *   list or its cursor.
     * - Business context: This is the entry point for every algorithm that has to
     *   consider all vertices rather than follow arcs from one of them, such as
     *   the initial marking pass of a traversal, the outer loop of a search for
     *   unreached components, or the collection of the sources a topological sort
     *   starts from. A copy is handed out because the internal list carries a
     *   cursor that the graph relies on: a caller iterating the original would
     *   move that cursor underneath any operation running at the same time.
     * - Processing steps: Walks the internal list from its first element,
     *   appending every vertex to a freshly allocated list, and positions the
     *   copy at its first element before returning it.
     * - Assumptions: Assumes no other operation is iterating the internal list at
     *   the same time, which the single-threaded use this class is written for
     *   guarantees.
     * - Side effects: Moves the cursor of the internal vertex list to the end as
     *   a consequence of the walk; the content of the graph is untouched.
     *
     * Time complexity: O(v) in the number of vertices; one append per vertex.
     * Space complexity: O(v) for the returned list, which holds references to the
     * same vertex instances rather than copies of them, so a mark set on a
     * returned vertex is visible inside the graph.
     *
     * @return
     * A new list holding every vertex of this graph, positioned at its first
     * element so that it can be traversed immediately. Empty when the graph holds
     * no vertices. Never null.
     */
    public SinglyLinkedList<Vertex> getVertices() {
        // Collect into a fresh list so that the caller cannot disturb the cursor
        // the internal list is navigated by.
        SinglyLinkedList<Vertex> result = new SinglyLinkedList<>();

        // Start the walk at the first vertex; the cursor may have been left
        // anywhere by an earlier operation.
        vertices.toFirst();
        while (vertices.hasAccess()) {
            result.append(vertices.getContent());
            vertices.next();
        }

        // Hand the copy back ready to be read, matching what the other
        // representations of this package do with every list they return.
        result.toFirst();
        return result;
    }

    /**
     * Retrieves the vertex carrying the specified identifier.
     *
     * Detailed explanation of:
     * - Purpose: Turns the identifier a caller knows into the vertex instance the
     *   graph actually holds.
     * - Business context: Vertices are compared by reference throughout this
     *   class, while callers and input data normally carry identifiers, so this
     *   lookup is the bridge between the two. It is also how insertion decides
     *   whether an identifier is still free and how the arc validation confirms
     *   that an endpoint really belongs to this graph rather than being a
     *   detached vertex that merely claims a matching identifier.
     * - Processing steps: Scans the vertex list from its first element and
     *   returns the first vertex whose identifier equals the requested one.
     * - Assumptions: Assumes identifiers are unique within a graph, which
     *   insertion enforces, so that the first match is the only match.
     * - Side effects: Moves the cursor of the internal vertex list; the content
     *   of the graph is untouched.
     *
     * Time complexity: O(v) in the number of vertices, since the list must be
     * scanned; the flat list-based storage buys its simplicity at the price of
     * this scan.
     * Space complexity: O(1); nothing is allocated.
     *
     * @param pID
     * Identifier of the vertex to look up. May be null, which matches no vertex,
     * since a vertex without an identifier is never accepted into the graph.
     *
     * @return
     * The vertex carrying the identifier, or null when no vertex of this graph
     * carries it. Null is unambiguous here because a graph never holds a null
     * vertex.
     */
    public Vertex getVertex(String pID) {
        // A null identifier cannot match anything, and testing it once here keeps
        // the comparison below free of a null check per vertex.
        if (pID == null) {
            return null;
        }

        vertices.toFirst();
        while (vertices.hasAccess()) {
            // Identifiers are compared by value, since a caller normally supplies
            // an identifier read from input data rather than the instance held by
            // the vertex.
            if (pID.equals(vertices.getContent().getID())) {
                return vertices.getContent();
            }
            vertices.next();
        }

        // No vertex of this graph carries the requested identifier.
        return null;
    }

    /**
     * Adds the specified vertex to this graph.
     *
     * Detailed explanation of:
     * - Purpose: Registers a vertex so that arcs may afterwards be attached to
     *   it.
     * - Business context: A graph is built vertices first, because an arc is only
     *   accepted once both of its endpoints are known here. The identifier
     *   collision is refused rather than overwritten, since two vertices sharing
     *   an identifier would make the lookup ambiguous and would let a caller
     *   attach arcs to whichever of the two the scan happens to reach first.
     * - Processing steps: Rejects a null vertex and a vertex without an
     *   identifier, rejects an identifier already taken, and otherwise appends the
     *   vertex to the vertex list.
     * - Assumptions: Assumes the caller does not rely on being told that the
     *   vertex was refused; the operation is deliberately tolerant, matching the
     *   other representations of this package, so that building a graph from
     *   input containing repetitions needs no exception handling.
     * - Side effects: Appends to the vertex list when the vertex is accepted, and
     *   moves the cursor of that list in every case. Arcs are not affected, since
     *   a newly added vertex is isolated by definition.
     *
     * Time complexity: O(v) in the number of vertices, dominated by the scan for
     * an identifier collision; the append itself is O(1).
     * Space complexity: O(1); the list node is the only allocation.
     *
     * @param pVertex
     * The vertex to register. Ignored when null, when its identifier is null, or
     * when a vertex carrying the same identifier is already present, none of
     * which changes the graph.
     */
    public void addVertex(Vertex pVertex) {
        // A vertex without an identity cannot be looked up afterwards and could
        // never be named as the endpoint of an arc, so it is refused outright.
        if (pVertex == null || pVertex.getID() == null) {
            return;
        }

        // Refuse a second vertex claiming an identifier that is already in use.
        // Reusing the lookup keeps the definition of a collision in one place.
        if (getVertex(pVertex.getID()) != null) {
            return;
        }

        vertices.append(pVertex);
    }

    /**
     * Removes the specified vertex and every arc touching it from this graph.
     *
     * Detailed explanation of:
     * - Purpose: Takes a vertex out of the graph without leaving any arc behind
     *   that references it.
     * - Business context: An arc whose endpoint has been removed would describe a
     *   connection to something that is no longer part of the graph, and every
     *   later query touching it would either report a vertex the graph does not
     *   hold or skip it inconsistently. The arcs are therefore removed first and
     *   the vertex afterwards, so that the graph never passes through a state in
     *   which such an arc exists. Both directions are swept: an arc arriving at
     *   the vertex is as dangling as one leaving it, which is precisely the
     *   distinction the undirected representations of this package cannot make and
     *   do not have to.
     * - Processing steps:
     *   1. Ignore a null vertex.
     *   2. Scan the arc list and remove every arc naming the vertex as tail or as
     *      head.
     *   3. Scan the vertex list and remove the vertex itself, identified by
     *      reference.
     * - Assumptions: Assumes the list removes an element by advancing its own
     *   cursor to the following one, which is the behaviour the other
     *   representations of this package rely on as well; the loops therefore
     *   advance only when nothing was removed.
     * - Side effects: Removes list entries and moves both cursors. Vertices at the
     *   far end of the removed arcs stay in the graph and merely lose those arcs,
     *   which may leave them isolated.
     *
     * Time complexity: O(v + a) in the numbers of vertices and arcs; one full
     * scan of each list.
     * Space complexity: O(1); removal is performed in place.
     *
     * @param pVertex
     * The vertex to remove, identified by reference rather than by identifier, so
     * that a detached vertex merely carrying a matching identifier cannot remove
     * a graph vertex by accident. Ignored when null, and without effect when the
     * vertex does not belong to this graph, in which case no arc names it either.
     */
    public void removeVertex(Vertex pVertex) {
        // Nothing to look for, and the scans below would compare every entry
        // against a reference that no arc and no vertex of a valid graph holds.
        if (pVertex == null) {
            return;
        }

        /*
         * Remove the arcs first. Doing it in this order means the graph is never
         * observable in a state where an arc names a vertex that has already been
         * taken out, which matters because the removal is not atomic.
         */
        arcs.toFirst();
        while (arcs.hasAccess()) {
            Vertex[] endpoints = arcs.getContent().getVertices();

            // Both roles are checked: an arc is dangling whether the removed
            // vertex was the one it leaves or the one it arrives at.
            if (endpoints[TAIL_INDEX] == pVertex || endpoints[HEAD_INDEX] == pVertex) {
                // The list advances its own cursor as part of the removal, so
                // advancing here as well would skip the following arc.
                arcs.remove();
            } else {
                arcs.next();
            }
        }

        // Remove the vertex itself, now that no arc can reference it any more.
        vertices.toFirst();
        while (vertices.hasAccess()) {
            if (vertices.getContent() == pVertex) {
                vertices.remove();
            } else {
                vertices.next();
            }
        }
    }

}
