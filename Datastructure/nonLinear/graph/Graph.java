package nonLinear.graph;

import linear.list.SinglyLinkedList;

/**
 * Purpose:
 * Defines the contract for a graph data structure supporting vertices and
 * weighted edges, along with the operations required to build, query, and
 * maintain such a structure. This interface abstracts the underlying
 * storage and traversal mechanisms so that multiple concrete graph
 * implementations (e.g., adjacency list, adjacency matrix) can be
 * substituted interchangeably wherever a Graph is required. It establishes
 * the core operations needed by graph algorithms such as traversal,
 * pathfinding, and spanning tree construction, including vertex and edge
 * management, marking/visitation state, and neighbor discovery. By
 * decoupling algorithmic consumers from a specific implementation, this
 * interface promotes flexibility, testability, and maintainability across
 * the non-linear graph package.
 *
 * Owner:
 * P.B.R. - https://github.com/PBR208/
 *
 * Version:
 * 1.0
 */

/**
 * Represents the contract for a graph structure composed of vertices and
 * weighted edges.
 *
 * Responsibility: Defines the required operations for managing vertices and
 * edges, querying structural relationships, tracking visitation state via
 * marks, and discovering neighboring vertices.
 *
 * Scope: Serves as the primary abstraction for all graph implementations
 * within the nonLinear.graph package.
 *
 * Dependencies: Depends on Vertex and Edge as the fundamental structural
 * units, and on SinglyLinkedList as the collection type used to expose
 * groups of vertices and edges to callers.
 *
 * Thread-safety: This interface makes no guarantees regarding thread
 * safety. Concrete implementations must document their own thread-safety
 * characteristics.
 *
 * Lifecycle: Implementations are expected to be mutable, supporting
 * dynamic addition and removal of vertices and edges over the lifetime of
 * the graph instance.
 *
 * Architectural role: Acts as the central abstraction consumed by graph
 * algorithms (e.g., traversal, shortest-path, minimum spanning tree) that
 * operate independently of the underlying storage implementation.
 */
public interface Graph {

    /**
     * Retrieves all vertices currently contained within the graph.
     *
     * Detailed explanation of:
     * - Purpose: Provides access to the complete set of vertices for
     *   iteration, inspection, or algorithmic processing.
     * - Business context: Serves as the entry point for algorithms that
     *   need to process every vertex in the graph, such as initialization
     *   routines or full-graph traversals.
     * - Processing steps: Implementation-defined; expected to return the
     *   current collection of vertices maintained by the graph.
     * - Assumptions: Assumes the graph has been properly initialized prior
     *   to invocation.
     * - Side effects: None expected; this operation should not modify
     *   graph state.
     *
     * @return
     * A list containing all vertices currently present in the graph.
     * Returns an empty list when the graph contains no vertices. Never
     * returns null.
     */
    SinglyLinkedList<Vertex> getVertices();

    /**
     * Retrieves the vertex identified by the specified unique identifier.
     *
     * Detailed explanation of:
     * - Purpose: Enables lookup of a specific vertex by its unique
     *   identifier without requiring the caller to iterate the full vertex
     *   collection.
     * - Business context: Used by algorithms and client code that need to
     *   reference a specific, known vertex, such as when constructing edges
     *   or initiating traversal from a designated starting point.
     * - Processing steps: Implementation-defined; expected to search the
     *   internal vertex collection for a matching identifier.
     * - Assumptions: Assumes vertex identifiers are unique within the
     *   graph.
     * - Side effects: None expected; this operation should not modify
     *   graph state.
     *
     * @param pID
     * The unique identifier of the vertex to retrieve. Must not be null or
     * empty.
     *
     * @return
     * The vertex matching the specified identifier, or null if no vertex
     * with the given identifier exists in the graph.
     *
     * @throws IllegalArgumentException
     * Thrown when pID is null or empty.
     */
    Vertex getVertex(String pID);

    /**
     * Adds the specified vertex to the graph.
     *
     * Detailed explanation of:
     * - Purpose: Expands the graph's vertex set to include a new node,
     *   enabling it to participate in subsequent edge connections and
     *   traversal operations.
     * - Business context: Supports dynamic graph construction, allowing
     *   vertices to be introduced incrementally as data becomes available.
     * - Processing steps: Implementation-defined; expected to insert the
     *   vertex into the internal vertex collection.
     * - Assumptions: Assumes the supplied vertex is non-null and not
     *   already present in the graph, depending on implementation-specific
     *   duplicate-handling policy.
     * - Side effects: Mutates the internal vertex collection of the graph.
     *
     * @param pVertex
     * The vertex to add to the graph. Must not be null.
     *
     * @throws IllegalArgumentException
     * Thrown when pVertex is null or already exists within the graph,
     * depending on the implementation's duplicate-handling policy.
     */
    void addVertex(Vertex pVertex);

    /**
     * Removes the specified vertex from the graph.
     *
     * Detailed explanation of:
     * - Purpose: Eliminates a vertex and, implicitly, its participation in
     *   any connected edges, from the graph structure.
     * - Business context: Supports dynamic graph maintenance, such as
     *   removing obsolete or invalid nodes from the model.
     * - Processing steps: Implementation-defined; expected to remove the
     *   vertex from the internal vertex collection and to handle any
     *   edges connected to it.
     * - Assumptions: Assumes the supplied vertex currently exists within
     *   the graph.
     * - Side effects: Mutates the internal vertex collection and
     *   potentially the internal edge collection of the graph.
     *
     * @param pVertex
     * The vertex to remove from the graph. Must not be null.
     *
     * @throws IllegalArgumentException
     * Thrown when pVertex is null or does not exist within the graph.
     */
    void removeVertex(Vertex pVertex);

    /**
     * Retrieves all edges currently contained within the graph.
     *
     * Detailed explanation of:
     * - Purpose: Provides access to the complete set of edges for
     *   iteration, inspection, or algorithmic processing.
     * - Business context: Serves as the entry point for algorithms that
     *   need to process every edge in the graph, such as minimum spanning
     *   tree construction or full-graph cost analysis.
     * - Processing steps: Implementation-defined; expected to return the
     *   current collection of edges maintained by the graph.
     * - Assumptions: Assumes the graph has been properly initialized prior
     *   to invocation.
     * - Side effects: None expected; this operation should not modify
     *   graph state.
     *
     * @return
     * A list containing all edges currently present in the graph. Returns
     * an empty list when the graph contains no edges. Never returns null.
     */
    SinglyLinkedList<Edge> getEdges();

    /**
     * Retrieves all edges connected to the specified vertex.
     *
     * Detailed explanation of:
     * - Purpose: Provides access to the subset of edges that have the
     *   specified vertex as one of their endpoints.
     * - Business context: Used by traversal and neighbor-discovery
     *   algorithms that need to evaluate all connections originating from
     *   or arriving at a particular vertex.
     * - Processing steps: Implementation-defined; expected to filter the
     *   internal edge collection for edges referencing the specified
     *   vertex.
     * - Assumptions: Assumes the supplied vertex currently exists within
     *   the graph.
     * - Side effects: None expected; this operation should not modify
     *   graph state.
     *
     * @param pVertex
     * The vertex whose connected edges are to be retrieved. Must not be
     * null.
     *
     * @return
     * A list containing all edges connected to the specified vertex.
     * Returns an empty list when the vertex has no connected edges. Never
     * returns null.
     *
     * @throws IllegalArgumentException
     * Thrown when pVertex is null or does not exist within the graph.
     */
    SinglyLinkedList<Edge> getEdges(Vertex pVertex);

    /**
     * Retrieves the edge connecting the two specified vertices, if one
     * exists.
     *
     * Detailed explanation of:
     * - Purpose: Enables lookup of a specific connection between two known
     *   vertices without requiring the caller to iterate the full edge
     *   collection.
     * - Business context: Used by algorithms and client code that need to
     *   determine whether, and how, two vertices are connected, such as
     *   when validating adjacency or retrieving connection weight.
     * - Processing steps: Implementation-defined; expected to search the
     *   internal edge collection for an edge whose endpoints match the
     *   supplied vertices.
     * - Assumptions: Assumes both supplied vertices currently exist within
     *   the graph.
     * - Side effects: None expected; this operation should not modify
     *   graph state.
     *
     * @param pVertex
     * The first endpoint vertex of the edge to retrieve. Must not be null.
     * @param pAnotherVertex
     * The second endpoint vertex of the edge to retrieve. Must not be
     * null.
     *
     * @return
     * The edge connecting the two specified vertices, or null if no such
     * edge exists in the graph.
     *
     * @throws IllegalArgumentException
     * Thrown when pVertex or pAnotherVertex is null or does not exist
     * within the graph.
     */
    Edge getEdge(Vertex pVertex, Vertex pAnotherVertex);

    /**
     * Adds the specified edge to the graph.
     *
     * Detailed explanation of:
     * - Purpose: Expands the graph's edge set to include a new connection
     *   between two vertices, enabling it to participate in subsequent
     *   traversal and cost-based algorithms.
     * - Business context: Supports dynamic graph construction, allowing
     *   relationships between vertices to be introduced incrementally as
     *   data becomes available.
     * - Processing steps: Implementation-defined; expected to insert the
     *   edge into the internal edge collection and ensure both referenced
     *   vertices are recognized as connected.
     * - Assumptions: Assumes the supplied edge references vertices that
     *   already exist within the graph.
     * - Side effects: Mutates the internal edge collection of the graph.
     *
     * @param pEdge
     * The edge to add to the graph. Must not be null.
     *
     * @throws IllegalArgumentException
     * Thrown when pEdge is null, references vertices not present in the
     * graph, or already exists within the graph, depending on the
     * implementation's duplicate-handling policy.
     */
    void addEdge(Edge pEdge);

    /**
     * Removes the specified edge from the graph.
     *
     * Detailed explanation of:
     * - Purpose: Eliminates a connection between two vertices from the
     *   graph structure without removing the vertices themselves.
     * - Business context: Supports dynamic graph maintenance, such as
     *   removing obsolete or invalid relationships from the model.
     * - Processing steps: Implementation-defined; expected to remove the
     *   edge from the internal edge collection.
     * - Assumptions: Assumes the supplied edge currently exists within the
     *   graph.
     * - Side effects: Mutates the internal edge collection of the graph.
     *
     * @param pEdge
     * The edge to remove from the graph. Must not be null.
     *
     * @throws IllegalArgumentException
     * Thrown when pEdge is null or does not exist within the graph.
     */
    void removeEdge(Edge pEdge);

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
     * - Processing steps: Implementation-defined; expected to iterate all
     *   vertices and apply the specified mark value to each.
     * - Assumptions: Assumes the graph has been properly initialized prior
     *   to invocation.
     * - Side effects: Mutates the mark state of every vertex in the graph.
     *
     * @param pMark
     * The mark value to apply to all vertices. True indicates marked;
     * false indicates unmarked.
     */
    void setAllVertexMarks(boolean pMark);

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
     * - Processing steps: Implementation-defined; expected to iterate all
     *   edges and apply the specified mark value to each.
     * - Assumptions: Assumes the graph has been properly initialized prior
     *   to invocation.
     * - Side effects: Mutates the mark state of every edge in the graph.
     *
     * @param pMark
     * The mark value to apply to all edges. True indicates marked; false
     * indicates unmarked.
     */
    void setAllEdgeMarks(boolean pMark);

    /**
     * Determines whether all vertices in the graph are currently marked.
     *
     * Detailed explanation of:
     * - Purpose: Provides a convenient way to verify traversal completeness
     *   without requiring the caller to inspect each vertex individually.
     * - Business context: Used by traversal algorithms to determine
     *   whether all reachable or all existing vertices have been visited,
     *   which may indicate traversal completion or graph connectivity.
     * - Processing steps: Implementation-defined; expected to iterate all
     *   vertices and verify that each reports a marked state.
     * - Assumptions: Assumes the graph has been properly initialized prior
     *   to invocation.
     * - Side effects: None expected; this operation should not modify
     *   graph state.
     *
     * @return
     * True if every vertex in the graph is currently marked; false if at
     * least one vertex is unmarked, or if the graph contains no vertices,
     * depending on the implementation's handling of the empty case.
     */
    boolean allVerticesMarked();

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
     * - Processing steps: Implementation-defined; expected to iterate all
     *   edges and verify that each reports a marked state.
     * - Assumptions: Assumes the graph has been properly initialized prior
     *   to invocation.
     * - Side effects: None expected; this operation should not modify
     *   graph state.
     *
     * @return
     * True if every edge in the graph is currently marked; false if at
     * least one edge is unmarked, or if the graph contains no edges,
     * depending on the implementation's handling of the empty case.
     */
    boolean allEdgesMarked();

    /**
     * Retrieves all vertices directly adjacent to the specified vertex.
     *
     * Detailed explanation of:
     * - Purpose: Provides the set of vertices reachable from the specified
     *   vertex via a single edge, supporting traversal and pathfinding
     *   algorithms.
     * - Business context: Used by graph traversal algorithms (e.g.,
     *   breadth-first search, depth-first search) to determine which
     *   vertices to visit next from a given position in the graph.
     * - Processing steps: Implementation-defined; expected to examine
     *   edges connected to the specified vertex and collect the opposing
     *   endpoint of each.
     * - Assumptions: Assumes the supplied vertex currently exists within
     *   the graph.
     * - Side effects: None expected; this operation should not modify
     *   graph state.
     *
     * @param pVertex
     * The vertex whose neighbors are to be retrieved. Must not be null.
     *
     * @return
     * A list containing all vertices directly connected to the specified
     * vertex via an edge. Returns an empty list when the vertex has no
     * neighbors. Never returns null.
     *
     * @throws IllegalArgumentException
     * Thrown when pVertex is null or does not exist within the graph.
     */
    SinglyLinkedList<Vertex> getNeighbours(Vertex pVertex);

    /**
     * Determines whether the graph currently contains no vertices.
     *
     * Detailed explanation of:
     * - Purpose: Provides a convenient way to check whether the graph has
     *   any content without requiring the caller to inspect the vertex
     *   collection directly.
     * - Business context: Used by algorithms and client code to guard
     *   against operating on an uninitialized or fully cleared graph.
     * - Processing steps: Implementation-defined; expected to evaluate
     *   whether the internal vertex collection contains any elements.
     * - Assumptions: Assumes the graph has been properly initialized prior
     *   to invocation.
     * - Side effects: None expected; this operation should not modify
     *   graph state.
     *
     * @return
     * True if the graph contains no vertices; false otherwise.
     */
    boolean isEmpty();
}