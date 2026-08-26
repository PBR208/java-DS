package nonLinear.graph.listGraph;

import nonLinear.graph.base.Edge;
import nonLinear.graph.base.Graph;
import nonLinear.graph.base.Vertex;

import linear.list.SinglyLinkedList;

/**
 * Purpose:
 * Provides a concrete, list-based implementation of the Graph interface,
 * using singly linked lists to store the collections of vertices and edges
 * that compose the graph. This class implements all structural operations
 * required to build, query, and maintain a graph — including vertex and
 * edge insertion and removal, adjacency lookup, neighbor discovery, and
 * bulk mark manipulation — by iterating over the underlying linked list
 * storage. It represents the default, straightforward storage strategy for
 * the non-linear graph package, favoring simplicity of implementation over
 * the lookup performance characteristics of alternative structures such as
 * adjacency matrices, and serves as the primary data structure consumed by
 * higher-level graph algorithms (e.g., traversal, shortest-path, minimum
 * spanning tree).
 *
 * Owner:
 * P.B.R. - https://github.com/PBR208/
 *
 * Version:
 * 1.0
 */

/**
 * List-based implementation of the Graph interface, storing vertices and
 * edges in singly linked lists.
 *
 * Responsibility: Implements all vertex and edge management, adjacency
 * queries, mark manipulation, and neighbor discovery operations defined by
 * the Graph contract, using linear traversal over internal linked list
 * storage.
 *
 * Scope: Used within the nonLinear.graph.listGraph package as a concrete,
 * instantiable graph implementation for consumption by graph algorithms.
 *
 * Dependencies: Depends on Vertex and Edge as structural units, on the
 * Graph interface as the contract being implemented, and on
 * SinglyLinkedList as the underlying storage collection.
 *
 * Thread-safety: This class is not thread-safe. Concurrent structural
 * modification (addition/removal of vertices or edges) or mark
 * manipulation from multiple threads without external synchronization may
 * result in inconsistent state or traversal errors.
 *
 * Lifecycle: A ListGraph instance begins empty upon construction and is
 * expected to be mutated dynamically over its lifetime through repeated
 * addition and removal of vertices and edges.
 *
 * Architectural role: Serves as the primary concrete data structure
 * underlying graph algorithms elsewhere in the system, fulfilling the
 * abstraction defined by the Graph interface.
 */
public class ListGraph implements Graph {

    // Stores all vertices currently belonging to this graph. Backed by a
    // singly linked list to support dynamic, incremental growth without a
    // fixed capacity constraint.
    private SinglyLinkedList<Vertex> vertices;

    // Stores all edges currently belonging to this graph. Backed by a
    // singly linked list to support dynamic, incremental growth without a
    // fixed capacity constraint.
    private SinglyLinkedList<Edge> edges;

    /**
     * Constructs a new, empty ListGraph with no vertices or edges.
     *
     * Detailed explanation of:
     * - Purpose: Initializes the internal storage structures required to
     *   hold vertices and edges before any graph content is added.
     * - Business context: Represents the starting state of a graph model
     *   prior to incremental construction by client code.
     * - Processing steps: Instantiates two empty SinglyLinkedList
     *   instances to back the vertex and edge collections.
     * - Assumptions: None; this constructor takes no parameters and
     *   performs unconditional initialization.
     * - Side effects: Allocates two new SinglyLinkedList instances.
     */
    public ListGraph() {
        // Initialize the vertex storage as an empty linked list.
        vertices = new SinglyLinkedList<>();

        // Initialize the edge storage as an empty linked list.
        edges = new SinglyLinkedList<>();
    }

    /**
     * Retrieves a defensive copy of all vertices currently contained within
     * the graph.
     *
     * Detailed explanation of:
     * - Purpose: Provides external access to the graph's vertex collection
     *   without exposing the internal list reference or its cursor state.
     * - Business context: Serves as the entry point for algorithms that
     *   need to process every vertex in the graph, such as initialization
     *   routines or full-graph traversals.
     * - Processing steps: Resets the internal cursor to the first vertex,
     *   iterates over the entire internal list appending each element to a
     *   newly created list, then resets the cursor of the copy before
     *   returning it.
     * - Assumptions: Assumes the internal vertices list is in a valid,
     *   navigable state.
     * - Side effects: Resets the internal cursor position of the internal
     *   vertices list as a consequence of iteration; does not alter the
     *   list's contents.
     *
     * @return
     * A new list containing all vertices currently present in the graph,
     * positioned at its first element. Returns an empty list when the
     * graph contains no vertices. Never returns null.
     */
    public SinglyLinkedList<Vertex> getVertices() {
        // Reset the internal cursor to the beginning of the list to ensure
        // a complete traversal from the start.
        vertices.toFirst();

        // Allocate a new list to hold the defensive copy, preserving
        // encapsulation of the internal storage.
        SinglyLinkedList<Vertex> copyOf = new SinglyLinkedList<>();

        // Iterate over every vertex in the internal list while the cursor
        // has access to a valid element.
        while (vertices.hasAccess()) {
            // Append the current vertex reference to the copy.
            copyOf.append(vertices.getContent());

            // Advance the cursor to the next vertex.
            vertices.next();
        }

        // Reset the cursor of the copy so callers receive it in a
        // ready-to-iterate state.
        copyOf.toFirst();

        // Return the completed defensive copy.
        return copyOf;
    }

    /**
     * Retrieves the vertex identified by the specified unique identifier.
     *
     * Detailed explanation of:
     * - Purpose: Performs a linear search over the internal vertex
     *   collection to locate a vertex matching the given identifier.
     * - Business context: Used by client code and algorithms that need to
     *   reference a specific, known vertex, such as when constructing
     *   edges or initiating traversal from a designated starting point.
     * - Processing steps: Resets the internal cursor to the first vertex,
     *   then iterates through the list comparing each vertex's identifier
     *   to the supplied identifier until a match is found or the list is
     *   exhausted.
     * - Assumptions: Assumes vertex identifiers are unique within the
     *   graph and that the supplied identifier is non-null, since equality
     *   comparison is invoked directly on it.
     * - Side effects: Leaves the internal cursor positioned at the matching
     *   vertex on success, or in an exhausted state if no match is found.
     *
     * @param pID
     * The unique identifier of the vertex to retrieve. Must not be null or
     * empty.
     *
     * @return
     * The vertex matching the specified identifier, or null if no vertex
     * with the given identifier exists in the graph.
     */
    public Vertex getVertex(String pID) {
        // Reset the internal cursor to the beginning of the list to ensure
        // a complete search from the start.
        vertices.toFirst();

        // Iterate over every vertex in the internal list while the cursor
        // has access to a valid element.
        while (vertices.hasAccess()) {
            // Compare the current vertex's identifier against the
            // requested identifier.
            if (vertices.getContent().getID().equals(pID)) {
                // Match found; return the current vertex immediately.
                return vertices.getContent();

            } else {
                // No match; advance the cursor to examine the next vertex.
                vertices.next();
            }
        }
        // No vertex with the requested identifier was found in the graph.
        return null;
    }

    /**
     * Adds the specified vertex to the graph if it is valid and not already
     * present.
     *
     * Detailed explanation of:
     * - Purpose: Expands the graph's vertex set to include a new node,
     *   while guarding against null input and duplicate identifiers.
     * - Business context: Supports dynamic graph construction, allowing
     *   vertices to be introduced incrementally while preserving the
     *   uniqueness constraint on vertex identifiers.
     * - Processing steps: Validates that the supplied vertex and its
     *   identifier are non-null, then performs a full scan of the existing
     *   vertex collection to detect an identifier collision. If no
     *   collision is found, appends the new vertex to the internal list.
     * - Assumptions: Assumes vertex identifiers are intended to be unique
     *   within the graph; silently ignores invalid or duplicate input
     *   rather than raising an exception.
     * - Side effects: Mutates the internal vertex collection by appending
     *   the new vertex when validation succeeds; leaves the internal
     *   cursor in an exhausted state after the duplicate-check scan.
     *
     * @param pVertex
     * The vertex to add to the graph. Must not be null and must have a
     * non-null identifier; vertices failing this check, or whose
     * identifier already exists in the graph, are silently ignored.
     */
    public void addVertex(Vertex pVertex) {

        // Guard against null vertex references and vertices with a null
        // identifier, since identifier-based lookups depend on a valid ID.
        if (pVertex != null && pVertex.getID() != null) {
            // Reset the internal cursor to the beginning of the list to
            // perform a full duplicate-detection scan.
            vertices.toFirst();

            // Tracks whether the vertex identifier is free to use; assumed
            // true until a collision is detected during the scan.
            boolean flag = true;

            // Scan every existing vertex to check for an identifier
            // collision with the vertex being added.
            while (vertices.hasAccess()) {
                if (vertices.getContent().getID().equals(pVertex.getID())) {
                    // Identifier collision detected; the vertex cannot be
                    // added because identifiers must remain unique.
                    flag = false;
                }
                vertices.next();

            }

            // Only append the vertex if no identifier collision was found
            // during the scan.
            if (flag) {
                vertices.append(pVertex);
            }
        }
    }

    /**
     * Removes the specified vertex from the graph, along with any edges
     * connected to it.
     *
     * Detailed explanation of:
     * - Purpose: Eliminates a vertex from the graph while also removing
     *   all edges that reference it, preserving structural integrity by
     *   preventing dangling edge references.
     * - Business context: Supports dynamic graph maintenance, such as
     *   removing obsolete or invalid nodes from the model without leaving
     *   orphaned connections.
     * - Processing steps: Returns immediately if the supplied vertex is
     *   null. Otherwise, first scans the entire edge collection, removing
     *   any edge whose endpoints include the target vertex, then scans the
     *   vertex collection and removes the matching vertex by reference
     *   identity.
     * - Assumptions: Assumes vertex identity is determined by reference
     *   equality rather than by identifier equality.
     * - Side effects: Mutates both the internal edge collection and the
     *   internal vertex collection; leaves both internal cursors in an
     *   exhausted state after the scans.
     *
     * @param pVertex
     * The vertex to remove from the graph. If null, the method performs no
     * action.
     */
    public void removeVertex(Vertex pVertex) {

        // Guard clause: if no vertex was supplied, there is nothing to
        // remove.
        if (pVertex == null) {
            return;
        }

        // Reset the internal edge cursor to the beginning of the list to
        // scan for edges connected to the vertex being removed.
        edges.toFirst();
        while (edges.hasAccess()){

            // Retrieve the current edge and its two endpoint vertices.
            Edge currentEdge = edges.getContent();
            Vertex[] vertexesOfEdge = currentEdge.getVertices();

            // If either endpoint of the current edge matches the vertex
            // being removed, the edge must be removed to avoid a dangling
            // reference to a non-existent vertex.
            if (vertexesOfEdge[0] == pVertex || vertexesOfEdge[1] == pVertex) {

                // Remove the current edge; the underlying list is expected
                // to advance the cursor to the next element automatically
                // as part of the removal operation.
                edges.remove();
            } else {
                // Edge is unrelated to the vertex being removed; advance to
                // the next edge.
                edges.next();
            }
        }

        // Reset the internal vertex cursor to the beginning of the list to
        // locate and remove the target vertex itself.
        vertices.toFirst();
        while (vertices.hasAccess()) {
            // Identify the target vertex by reference equality.
            if (vertices.getContent() == pVertex) {
                // Remove the matching vertex; the underlying list is
                // expected to advance the cursor automatically.
                vertices.remove();
            } else {
                // Not the target vertex; advance to the next one.
                vertices.next();
            }

        }
    }

    /**
     * Retrieves a defensive copy of all edges currently contained within
     * the graph.
     *
     * Detailed explanation of:
     * - Purpose: Provides external access to the graph's edge collection
     *   without exposing the internal list reference or its cursor state.
     * - Business context: Serves as the entry point for algorithms that
     *   need to process every edge in the graph, such as minimum spanning
     *   tree construction or full-graph cost analysis.
     * - Processing steps: Allocates a new list, resets the internal cursor
     *   to the first edge, iterates over the entire internal list
     *   appending each element to the new list, then resets the cursor of
     *   the copy before returning it.
     * - Assumptions: Assumes the internal edges list is in a valid,
     *   navigable state.
     * - Side effects: Resets the internal cursor position of the internal
     *   edges list as a consequence of iteration; does not alter the
     *   list's contents.
     *
     * @return
     * A new list containing all edges currently present in the graph,
     * positioned at its first element. Returns an empty list when the
     * graph contains no edges. Never returns null.
     */
    public SinglyLinkedList<Edge> getEdges() {

        // Allocate a new list to hold the defensive copy, preserving
        // encapsulation of the internal storage.
        SinglyLinkedList<Edge> copyOf = new SinglyLinkedList<>();

        // Reset the internal cursor to the beginning of the list to ensure
        // a complete traversal from the start.
        edges.toFirst();
        while (edges.hasAccess()) {

            // Append the current edge reference to the copy.
            copyOf.append(edges.getContent());

            // Advance the cursor to the next edge.
            edges.next();
        }

        // Reset the cursor of the copy so callers receive it in a
        // ready-to-iterate state.
        copyOf.toFirst();

        // Return the completed defensive copy.
        return copyOf;
    }

    /**
     * Retrieves all edges connected to the specified vertex.
     *
     * Detailed explanation of:
     * - Purpose: Filters the graph's edge collection to identify only
     *   those edges that have the specified vertex as one of their two
     *   endpoints.
     * - Business context: Used by traversal and neighbor-discovery
     *   algorithms that need to evaluate all connections originating from
     *   or arriving at a particular vertex.
     * - Processing steps: Allocates a new result list, then iterates over
     *   the entire internal edge collection, appending each edge whose
     *   endpoints include the target vertex to the result list.
     * - Assumptions: Assumes vertex identity within an edge is determined
     *   by reference equality rather than by identifier equality.
     * - Side effects: Leaves the internal edges cursor in an exhausted
     *   state after the full scan; does not alter the internal edge
     *   collection's contents.
     *
     * @param pVertex
     * The vertex whose connected edges are to be retrieved.
     *
     * @return
     * A list containing all edges connected to the specified vertex.
     * Returns an empty list when the vertex has no connected edges or is
     * null. Never returns null.
     */
    public SinglyLinkedList<Edge> getEdges(Vertex pVertex) {

        // Allocate a new list to accumulate matching edges.
        SinglyLinkedList<Edge> copyOf = new SinglyLinkedList<>();

        // Reset the internal cursor to the beginning of the list to ensure
        // a complete scan from the start.
        edges.toFirst();
        while (edges.hasAccess()) {

            // Retrieve the current edge and its two endpoint vertices.
            Edge currentEdge = edges.getContent();
            Vertex[] vertexesOfEdge = currentEdge.getVertices();

            // If either endpoint of the current edge matches the target
            // vertex, include this edge in the result set.
            if (vertexesOfEdge[0] == pVertex || vertexesOfEdge[1] == pVertex) {

                copyOf.append(edges.getContent());
            }
            // Advance the cursor to the next edge regardless of match
            // outcome.
            edges.next();
        }

        // Return the accumulated list of connected edges.
        // Position the cursor on the first element so that the returned
        // list is immediately iterable. Both Graph implementations do this
        // for every list they hand out, so a caller can traverse the result
        // without knowing which implementation produced it.
        copyOf.toFirst();
        return copyOf;
    }


    /**
     * Retrieves the edge connecting the two specified vertices, if one
     * exists.
     *
     * Detailed explanation of:
     * - Purpose: Performs a linear search over the internal edge
     *   collection to locate an edge whose endpoints match the two
     *   supplied vertices, regardless of their order.
     * - Business context: Used by algorithms and client code that need to
     *   determine whether, and how, two vertices are connected, such as
     *   when validating adjacency or retrieving connection weight; also
     *   used internally to detect duplicate edges before insertion.
     * - Processing steps: Resets the internal cursor to the first edge,
     *   then iterates through the list comparing each edge's endpoint pair
     *   against the supplied vertices in both possible orderings until a
     *   match is found or the list is exhausted.
     * - Assumptions: Assumes vertex identity is determined by reference
     *   equality rather than by identifier equality, and that the graph is
     *   undirected, since both endpoint orderings are treated as
     *   equivalent.
     * - Side effects: Leaves the internal cursor positioned at the matching
     *   edge on success, or in an exhausted state if no match is found.
     *
     * @param pVertex
     * The first endpoint vertex of the edge to retrieve.
     * @param pAnotherVertex
     * The second endpoint vertex of the edge to retrieve.
     *
     * @return
     * The edge connecting the two specified vertices, or null if no such
     * edge exists in the graph.
     */
    public Edge getEdge(Vertex pVertex, Vertex pAnotherVertex) {

        // Reset the internal cursor to the beginning of the list to ensure
        // a complete search from the start.
        edges.toFirst();
        while (edges.hasAccess()) {
            // Retrieve the endpoint pair of the current edge.
            Vertex[] v = edges.getContent().getVertices();

            // An undirected edge matches regardless of which endpoint is
            // stored first, so both orderings must be checked.
            if ((v[0] == pVertex && v[1] == pAnotherVertex) || (v[0] == pAnotherVertex && v[1] == pVertex)) {
                // Match found; return the current edge immediately.
                return edges.getContent();
            }
            // No match; advance the cursor to examine the next edge.
            edges.next();

        }

        // No edge connecting the two specified vertices was found.
        return null;
    }

    /**
     * Adds the specified edge to the graph after validating its endpoints
     * and uniqueness.
     *
     * Detailed explanation of:
     * - Purpose: Expands the graph's edge set to include a new connection
     *   between two vertices, while enforcing structural integrity
     *   constraints on the endpoints.
     * - Business context: Supports dynamic graph construction, allowing
     *   relationships between vertices to be introduced incrementally
     *   while preventing self-loops, references to non-member vertices,
     *   and duplicate connections.
     * - Processing steps: Validates that the supplied edge is non-null,
     *   that both endpoint vertices are non-null and distinct from one
     *   another, that both endpoints are actually registered members of
     *   this graph (verified via identifier-based lookup and reference
     *   comparison), and that no existing edge already connects the same
     *   pair of vertices. Only when all checks pass is the edge appended
     *   to the internal edge collection.
     * - Assumptions: Assumes the graph is undirected and does not permit
     *   self-loops (an edge connecting a vertex to itself) or parallel
     *   edges (multiple edges connecting the same vertex pair).
     * - Side effects: Mutates the internal edge collection by appending
     *   the new edge when validation succeeds; invokes getVertex and
     *   getEdge internally, which leave their respective internal cursors
     *   in a modified state as a side effect of the lookups they perform.
     *
     * @param pEdge
     * The edge to add to the graph. Edges failing any of the validation
     * checks (null edge, null or identical endpoints, endpoints not
     * present in the graph, or a pre-existing connection between the same
     * vertices) are silently ignored.
     */
    public void addEdge(Edge pEdge) {

        // Guard against a null edge reference.
        if (pEdge != null) {

            // Extract the two endpoint vertices declared by the edge.
            Vertex v1 = pEdge.getVertices()[0];
            Vertex v2 = pEdge.getVertices()[1];

            // Validate structural integrity before allowing insertion:
            // both endpoints must be non-null, must be distinct from one
            // another (no self-loops), must each be a vertex that is
            // actually registered within this graph (verified by
            // confirming that looking up each vertex's identifier returns
            // the exact same instance), and no edge may already connect
            // this pair of vertices (no duplicate/parallel edges).
            if (v1 != null && v2 != null
                    && v1 != v2
                    && getVertex(v1.getID()) == v1
                    && getVertex(v2.getID()) == v2
                    && getEdge(v1, v2) == null) {

                // All validation checks passed; the edge may be safely
                // added to the graph.
                edges.append(pEdge);
            }
        }
    }

    /**
     * Removes the specified edge from the graph.
     *
     * Detailed explanation of:
     * - Purpose: Eliminates a connection between two vertices from the
     *   graph structure without removing the vertices themselves.
     * - Business context: Supports dynamic graph maintenance, such as
     *   removing obsolete or invalid relationships from the model.
     * - Processing steps: Returns immediately if the supplied edge is
     *   null. Otherwise, scans the entire internal edge collection and
     *   removes the edge matching the supplied reference by reference
     *   identity.
     * - Assumptions: Assumes edge identity is determined by reference
     *   equality rather than by structural equivalence (i.e., two distinct
     *   edge instances connecting the same vertex pair are not considered
     *   equal for removal purposes).
     * - Side effects: Mutates the internal edge collection when a match is
     *   found; leaves the internal cursor in an exhausted state after the
     *   scan.
     *
     * @param pEdge
     * The edge to remove from the graph. If null, the method performs no
     * action.
     */
    public void removeEdge(Edge pEdge) {

        // Guard clause: if no edge was supplied, there is nothing to
        // remove.
        if (pEdge == null) {
            return;
        }

        // Reset the internal cursor to the beginning of the list to ensure
        // a complete scan from the start.
        edges.toFirst();

        while (edges.hasAccess()) {
            // Identify the target edge by reference equality.
            if (edges.getContent() == pEdge) {
                // Remove the matching edge; the underlying list is
                // expected to advance the cursor automatically.
                edges.remove();
            } else {
                // Not the target edge; advance to the next one.
                edges.next();
            }
        }

    }

    /**
     * Sets the mark state on all vertices within the graph.
     *
     * Detailed explanation of:
     * - Purpose: Provides a bulk operation to reset or set the visitation
     *   state of every vertex, typically used prior to initiating a new
     *   traversal.
     * - Business context: Ensures algorithms operating on marks (e.g.,
     *   depth-first or breadth-first traversal) begin from a known,
     *   consistent state across all vertices.
     * - Processing steps: Resets the internal cursor to the first vertex,
     *   then iterates over the entire vertex collection applying the
     *   specified mark value to each vertex in turn.
     * - Assumptions: Assumes the graph has been properly initialized prior
     *   to invocation.
     * - Side effects: Mutates the mark state of every vertex in the graph;
     *   leaves the internal cursor in an exhausted state after the scan.
     *
     * @param pMark
     * The mark value to apply to all vertices. True indicates marked;
     * false indicates unmarked.
     */
    public void setAllVertexMarks(boolean pMark) {

        // Reset the internal cursor to the beginning of the list to ensure
        // every vertex is updated.
        vertices.toFirst();
        while (vertices.hasAccess()) {
            // Apply the requested mark value to the current vertex.
            vertices.getContent().setMark(pMark);

            // Advance the cursor to the next vertex.
            vertices.next();
        }

    }

    /**
     * Sets the mark state on all edges within the graph.
     *
     * Detailed explanation of:
     * - Purpose: Provides a bulk operation to reset or set the processing
     *   state of every edge, typically used prior to initiating algorithms
     *   such as minimum spanning tree construction.
     * - Business context: Ensures algorithms operating on marks (e.g.,
     *   spanning tree or cycle detection algorithms) begin from a known,
     *   consistent state across all edges.
     * - Processing steps: Resets the internal cursor to the first edge,
     *   then iterates over the entire edge collection applying the
     *   specified mark value to each edge in turn.
     * - Assumptions: Assumes the graph has been properly initialized prior
     *   to invocation.
     * - Side effects: Mutates the mark state of every edge in the graph;
     *   leaves the internal cursor in an exhausted state after the scan.
     *
     * @param pMark
     * The mark value to apply to all edges. True indicates marked; false
     * indicates unmarked.
     */
    public void setAllEdgeMarks(boolean pMark) {


        // Reset the internal cursor to the beginning of the list to ensure
        // every edge is updated.
        edges.toFirst();
        while (edges.hasAccess()) {
            // Apply the requested mark value to the current edge.
            edges.getContent().setMark(pMark);

            // Advance the cursor to the next edge.
            edges.next();
        }
    }

    /**
     * Determines whether all vertices in the graph are currently marked.
     *
     * Detailed explanation of:
     * - Purpose: Provides a convenient way to verify traversal completeness
     *   without requiring the caller to inspect each vertex individually.
     * - Business context: Used by traversal algorithms to determine
     *   whether all reachable or all existing vertices have been visited,
     *   which may indicate traversal completion or graph connectivity.
     * - Processing steps: Resets the internal cursor to the first vertex,
     *   assumes a true result by default, then iterates over the entire
     *   vertex collection, flipping the result to false the moment an
     *   unmarked vertex is encountered. Note that the loop does not
     *   short-circuit and continues scanning the full collection even
     *   after a negative result is found.
     * - Assumptions: Assumes the graph has been properly initialized prior
     *   to invocation. An empty graph (no vertices) yields true, since the
     *   result defaults to true and the loop body never executes.
     * - Side effects: Leaves the internal cursor in an exhausted state
     *   after the scan; does not modify vertex mark state.
     *
     * @return
     * True if every vertex in the graph is currently marked, or if the
     * graph contains no vertices; false if at least one vertex is
     * unmarked.
     */
    public boolean allVerticesMarked() {

        // Reset the internal cursor to the beginning of the list to ensure
        // a complete scan from the start.
        vertices.toFirst();

        // Assume all vertices are marked until proven otherwise by the
        // scan below.
        boolean result = true;

        while (vertices.hasAccess()) {
            // If any vertex is found unmarked, the overall result becomes
            // false.
            if (!vertices.getContent().isMarked()) {
                result = false;
            }

            // Advance the cursor to the next vertex regardless of outcome.
            vertices.next();
        }
        // Return the final determination after scanning all vertices.
        return result;
    }

    /**
     * Determines whether all edges in the graph are currently marked.
     *
     * Detailed explanation of:
     * - Purpose: Provides a convenient way to verify processing
     *   completeness without requiring the caller to inspect each edge
     *   individually.
     * - Business context: Used by algorithms such as minimum spanning tree
     *   construction to determine whether all edges have been considered
     *   or included.
     * - Processing steps: Resets the internal cursor to the first edge,
     *   assumes a true result by default, then iterates over the entire
     *   edge collection, flipping the result to false the moment an
     *   unmarked edge is encountered. Note that the loop does not
     *   short-circuit and continues scanning the full collection even
     *   after a negative result is found.
     * - Assumptions: Assumes the graph has been properly initialized prior
     *   to invocation. A graph with no edges yields true, since the result
     *   defaults to true and the loop body never executes.
     * - Side effects: Leaves the internal cursor in an exhausted state
     *   after the scan; does not modify edge mark state.
     *
     * @return
     * True if every edge in the graph is currently marked, or if the graph
     * contains no edges; false if at least one edge is unmarked.
     */
    public boolean allEdgesMarked() {

        // Reset the internal cursor to the beginning of the list to ensure
        // a complete scan from the start.
        edges.toFirst();

        // Assume all edges are marked until proven otherwise by the scan
        // below.
        boolean result = true;

        while (edges.hasAccess()) {
            // If any edge is found unmarked, the overall result becomes
            // false.
            if (!edges.getContent().isMarked()) {
                result = false;
            }

            // Advance the cursor to the next edge regardless of outcome.
            edges.next();
        }
        // Return the final determination after scanning all edges.
        return result;
    }


    /**
     * Retrieves all vertices directly adjacent to the specified vertex.
     *
     * Detailed explanation of:
     * - Purpose: Identifies the set of vertices reachable from the
     *   specified vertex via a single edge, by inspecting each edge's
     *   endpoint pair.
     * - Business context: Used by graph traversal algorithms (e.g.,
     *   breadth-first search, depth-first search) to determine which
     *   vertices to visit next from a given position in the graph.
     * - Processing steps: Allocates a new result list, resets the internal
     *   edge cursor to the first edge, then iterates over every edge in
     *   the graph. For each edge, if the target vertex occupies the first
     *   endpoint position, the second endpoint is appended as a neighbor;
     *   if the target vertex occupies the second endpoint position, the
     *   first endpoint is appended as a neighbor.
     * - Assumptions: Assumes vertex identity within an edge is determined
     *   by reference equality rather than by identifier equality, and that
     *   the graph is undirected, since adjacency is evaluated in both
     *   endpoint positions independently.
     * - Side effects: Leaves the internal edges cursor in an exhausted
     *   state after the full scan; does not alter the internal edge
     *   collection's contents.
     *
     * @param pVertex
     * The vertex whose neighbors are to be retrieved.
     *
     * @return
     * A list containing all vertices directly connected to the specified
     * vertex via an edge. Returns an empty list when the vertex has no
     * neighbors or is null. Never returns null.
     */
    public SinglyLinkedList<Vertex> getNeighbours(Vertex pVertex) {

        // Allocate a new list to accumulate discovered neighboring
        // vertices.
        SinglyLinkedList<Vertex> result = new SinglyLinkedList<>();

        // Reset the internal cursor to the beginning of the list to ensure
        // a complete scan from the start.
        edges.toFirst();

        while (edges.hasAccess()) {

            // Retrieve the endpoint pair of the current edge.
            Vertex[] vertexPair = edges.getContent().getVertices();

            // If the target vertex is the first endpoint, the second
            // endpoint is a neighbor reachable via this edge.
            if (vertexPair[0] == pVertex) {
                result.append(vertexPair[1]);
            }

            // If the target vertex is the second endpoint, the first
            // endpoint is a neighbor reachable via this edge.
            if (vertexPair[1] == pVertex) {
                result.append(vertexPair[0]);
            }

            // Advance the cursor to the next edge.
            edges.next();
        }

        // Return the accumulated list of neighboring vertices.
        // Position the cursor on the first element so that the returned
        // list is immediately iterable. Both Graph implementations do this
        // for every list they hand out, so a caller can traverse the result
        // without knowing which implementation produced it.
        result.toFirst();
        return result;
    }

    /**
     * Determines whether the graph currently contains no vertices.
     *
     * Detailed explanation of:
     * - Purpose: Provides a convenient way to check whether the graph has
     *   any content without requiring the caller to inspect the vertex
     *   collection directly.
     * - Business context: Used by algorithms and client code to guard
     *   against operating on an uninitialized or fully cleared graph.
     * - Processing steps: Delegates directly to the internal vertices
     *   list's own emptiness check.
     * - Assumptions: Assumes the graph has been properly initialized prior
     *   to invocation. Note that emptiness is determined solely by the
     *   absence of vertices; a graph could theoretically be considered
     *   non-empty due to vertices alone even if it has no edges.
     * - Side effects: None; this method does not modify internal state.
     *
     * @return
     * True if the graph contains no vertices; false otherwise.
     */
    public boolean isEmpty() {
        return vertices.isEmpty();
    }
}