package nonLinear.graph.matrixGraph;

import nonLinear.graph.base.Edge;
import nonLinear.graph.base.Vertex;
import nonLinear.graph.base.Graph;

import linear.list.SinglyLinkedList;

/**
 * Purpose:
 * Provides a concrete, adjacency-matrix-based implementation of the Graph
 * interface, using a two-dimensional array of Edge references indexed by
 * vertex position to represent connectivity between vertices. This class
 * implements all structural operations required to build, query, and
 * maintain a graph — including vertex and edge insertion and removal,
 * adjacency lookup, neighbor discovery, and bulk mark manipulation — by
 * directly indexing into the underlying matrix rather than performing
 * linear scans over a linked list of edges. It represents an
 * alternative, lookup-optimized storage strategy for the non-linear graph
 * package, trading increased memory consumption (proportional to the
 * square of vertex capacity) for faster adjacency and edge-existence
 * queries compared to list-based implementations, and serves as an
 * interchangeable data structure consumed by higher-level graph algorithms
 * wherever the Graph abstraction is required.
 *
 * Owner:
 * P.B.R. - https://github.com/PBR208/
 *
 * Version:
 * 1.0
 */

/**
 * Adjacency-matrix-based implementation of the Graph interface, storing
 * vertices in a resizable array and edges in a two-dimensional matrix
 * indexed by vertex position.
 *
 * Responsibility: Implements all vertex and edge management, adjacency
 * queries, mark manipulation, and neighbor discovery operations defined by
 * the Graph contract, using direct array indexing for adjacency lookups
 * and a parallel vertex array for identity and position tracking.
 *
 * Scope: Used within the nonLinear.graph.matrixGraph package as a
 * concrete, instantiable graph implementation for consumption by graph
 * algorithms that benefit from constant-time adjacency lookups.
 *
 * Dependencies: Depends on Vertex and Edge as structural units, on the
 * Graph interface as the contract being implemented, and on
 * SinglyLinkedList as the return type used to expose collections of
 * vertices and edges to callers.
 *
 * Thread-safety: This class is not thread-safe. Concurrent structural
 * modification (addition/removal of vertices or edges, or capacity growth)
 * or mark manipulation from multiple threads without external
 * synchronization may result in inconsistent state or index corruption.
 *
 * Lifecycle: A MatrixGraph instance begins empty with a fixed starting
 * capacity upon construction and is expected to be mutated dynamically
 * over its lifetime through repeated addition and removal of vertices and
 * edges; internal storage grows automatically as capacity is exhausted.
 *
 * Architectural role: Serves as an alternative concrete data structure
 * underlying graph algorithms elsewhere in the system, fulfilling the
 * abstraction defined by the Graph interface with different performance
 * characteristics than list-based implementations.
 */
public class MatrixGraph implements Graph {

    // Defines the initial size allocated for both the vertex array and the
    // matrix dimensions when a MatrixGraph is first constructed. Chosen as
    // a reasonable default starting capacity to avoid excessive early
    // reallocation while keeping initial memory usage modest.
    private static final int START_CAPACITY = 10;

    // Stores all vertices currently belonging to this graph in a
    // fixed-capacity array. The array may contain unused trailing slots
    // beyond vertexCount; only indices [0, vertexCount) hold valid vertex
    // references at any given time.
    private Vertex[] vertices;

    // Stores the adjacency matrix representing edges between vertices.
    // matrix[i][j] holds a reference to the Edge connecting the vertices
    // at positions i and j in the vertices array, or null if no such edge
    // exists. Because the graph is undirected, matrix[i][j] and
    // matrix[j][i] are expected to reference the same Edge instance.
    private Edge[][] matrix;

    // Tracks the number of vertices currently active within the vertices
    // array. Used to distinguish valid, in-use array slots from unused
    // capacity reserved for future growth.
    private int vertexCount;

    /**
     * Constructs a new, empty MatrixGraph with a default starting capacity
     * for vertices and their adjacency matrix.
     *
     * Detailed explanation of:
     * - Purpose: Initializes the internal array-based storage structures
     *   required to hold vertices and their pairwise edge connections
     *   before any graph content is added.
     * - Business context: Represents the starting state of a
     *   matrix-backed graph model prior to incremental construction by
     *   client code.
     * - Processing steps: Allocates a vertex array and a square edge
     *   matrix, both sized according to START_CAPACITY, and initializes
     *   the active vertex count to zero.
     * - Assumptions: None; this constructor takes no parameters and
     *   performs unconditional initialization.
     * - Side effects: Allocates a new Vertex array and a new two-dimensional
     *   Edge array.
     */
    public MatrixGraph() {

        // Allocate the vertex storage array at the default starting
        // capacity.
        this.vertices = new Vertex[START_CAPACITY];

        // Allocate the adjacency matrix as a square array matching the
        // starting capacity, so every vertex slot has a corresponding row
        // and column.
        this.matrix = new Edge[START_CAPACITY][START_CAPACITY];

        // No vertices have been added yet at construction time.
        this.vertexCount = 0;
    }

    /**
     * Locates the array index of the specified vertex within the internal
     * vertices array.
     *
     * Detailed explanation of:
     * - Purpose: Provides the positional index required to access the
     *   corresponding row/column in the adjacency matrix for a given
     *   vertex.
     * - Business context: Serves as an internal lookup utility used by
     *   nearly all matrix-based operations (edge lookup, edge insertion,
     *   removal, neighbor discovery) to translate a vertex reference into
     *   a usable matrix coordinate.
     * - Processing steps: Performs a linear scan across the entire
     *   vertices array, comparing each slot against the target vertex by
     *   reference equality, and returns the index of the first match.
     * - Assumptions: Assumes vertex identity is determined by reference
     *   equality rather than by identifier equality. Scans the full array
     *   length rather than stopping at vertexCount, meaning unused trailing
     *   slots (which are null) are also checked but cannot match a
     *   non-null vertex.
     * - Side effects: None; this method does not modify internal state.
     *
     * @param pVertex
     * The vertex whose array index is to be located.
     *
     * @return
     * The zero-based index of the specified vertex within the internal
     * vertices array, or -1 if the vertex is not present.
     */
    private int indexOf(Vertex pVertex) {

        // Scan every slot of the vertices array, including unused trailing
        // capacity, searching for a reference match.
        for (int i = 0; i < vertices.length; i++){
            if (vertices[i] == pVertex){
                // Match found; return its position immediately.
                return i;
            }
        }
        // No matching vertex was found in the array.
        return -1;
    }

    /**
     * Doubles the capacity of the internal vertex array and the row
     * dimension of the adjacency matrix.
     *
     * Detailed explanation of:
     * - Purpose: Expands internal storage capacity when the vertex array
     *   has been filled to capacity, allowing additional vertices to be
     *   accommodated without a fixed upper bound.
     * - Business context: Supports unbounded, dynamic graph growth by
     *   transparently reallocating storage as needed during vertex
     *   insertion.
     * - Processing steps: Allocates a new vertex array at twice the
     *   current length and copies existing vertex references into it.
     *   Separately allocates a new matrix array with twice the current
     *   row count, retaining the existing column count, and copies
     *   existing matrix rows into it. Replaces the internal vertices and
     *   matrix references with the newly allocated arrays.
     * - Assumptions: Assumes this method is invoked only when the vertex
     *   array is at full capacity. Note that the column dimension of the
     *   newly allocated matrix is derived from matrix[0].length (the
     *   existing column count) and is not doubled alongside the row count
     *   in this method; column growth is not performed here.
     * - Side effects: Replaces the internal vertices and matrix array
     *   references with newly allocated, larger arrays; discards the
     *   previous array instances.
     */
    private void grow() {

        // Allocate a new vertex array at twice the current capacity.
        Vertex[] growDouble = new Vertex[vertices.length * 2];

        // Copy all existing vertex references into the new, larger array.
        System.arraycopy(vertices, 0, growDouble, 0, vertices.length);

        // Capture the current matrix dimensions prior to reallocation.
        int rows = matrix.length;
        int cols = matrix[0].length;

        // Allocate a new matrix with double the row capacity, preserving
        // the existing column count.
        Edge[][] growDoubleEdge = new Edge[rows * 2][cols];

        // Copy all existing matrix rows (each an array reference) into the
        // new matrix structure.
        System.arraycopy(matrix, 0, growDoubleEdge, 0, rows);

        // Replace the internal vertices reference with the newly grown
        // array.
        vertices = growDouble;

        // Replace the internal matrix reference with the newly grown
        // array.
        matrix = growDoubleEdge;
    }

    /**
     * Retrieves a list of all vertices currently contained within the
     * graph.
     *
     * Detailed explanation of:
     * - Purpose: Provides external access to the graph's active vertex
     *   set in list form, independent of the internal array-based storage
     *   representation.
     * - Business context: Serves as the entry point for algorithms that
     *   need to process every vertex in the graph, such as initialization
     *   routines or full-graph traversals.
     * - Processing steps: Allocates a new linked list, then iterates over
     *   the internal vertices array from index 0 up to vertexCount
     *   (exclusive), appending each active vertex to the list.
     * - Assumptions: Assumes vertexCount accurately reflects the number of
     *   valid, non-null entries at the start of the vertices array.
     * - Side effects: None; this method does not modify internal state.
     *
     * @return
     * A list containing all vertices currently present in the graph.
     * Returns an empty list when the graph contains no vertices. Never
     * returns null.
     */
    public SinglyLinkedList<Vertex> getVertices() {
        // Allocate a new list to hold the resulting vertex collection.
        SinglyLinkedList<Vertex> helper = new SinglyLinkedList<>();

        // Iterate only over the active portion of the vertices array,
        // bounded by vertexCount, to avoid including unused capacity.
        for (int i = 0; i < vertexCount; i++) {
            helper.append(vertices[i]);
        }

        // Return the completed list of vertices.
        return helper;
    }

    /**
     * Retrieves the vertex identified by the specified unique identifier.
     *
     * Detailed explanation of:
     * - Purpose: Performs a linear search over the internal vertex array
     *   to locate a vertex matching the given identifier.
     * - Business context: Used by client code and algorithms that need to
     *   reference a specific, known vertex, such as when constructing
     *   edges or initiating traversal from a designated starting point.
     * - Processing steps: Iterates over every element of the vertices
     *   array (using an enhanced for-loop) and compares the supplied
     *   identifier against each vertex's identifier until a match is
     *   found or the array is exhausted.
     * - Assumptions: Assumes vertex identifiers are unique within the
     *   graph. Because the iteration covers the entire array rather than
     *   being bounded by vertexCount, this method relies on unused
     *   trailing slots being null; invoking getID() on a null slot would
     *   otherwise raise a NullPointerException, though such slots are
     *   expected to remain null until populated.
     * - Side effects: None; this method does not modify internal state.
     *
     * @param pID
     * The unique identifier of the vertex to retrieve.
     *
     * @return
     * The vertex matching the specified identifier, or null if no vertex
     * with the given identifier exists in the graph.
     */
    public Vertex getVertex(String pID) {

        // Iterate over every vertex slot in the array, comparing
        // identifiers to locate the requested vertex.
        for (Vertex vertex : vertices) {
            if (pID.equals(vertex.getID())) {
                // Match found; return the vertex immediately.
                return vertex;
            }
        }

        // No vertex with the requested identifier was found.
        return null;
    }

    /**
     * Adds the specified vertex to the graph if it is valid and not
     * already present, growing internal storage capacity if necessary.
     *
     * Detailed explanation of:
     * - Purpose: Expands the graph's vertex set to include a new node,
     *   while guarding against null input and duplicate identifiers, and
     *   transparently handling capacity exhaustion.
     * - Business context: Supports dynamic graph construction, allowing
     *   vertices to be introduced incrementally while preserving the
     *   uniqueness constraint on vertex identifiers.
     * - Processing steps: Validates that the supplied vertex, its
     *   identifier, and the absence of a pre-existing vertex with the same
     *   identifier all hold true. If the vertex array is at full capacity,
     *   triggers a capacity-doubling growth operation before inserting.
     *   Places the new vertex at the next available index and increments
     *   the active vertex count.
     * - Assumptions: Assumes vertex identifiers are intended to be unique
     *   within the graph; silently ignores invalid or duplicate input
     *   rather than raising an exception.
     * - Side effects: Mutates the internal vertices array by inserting the
     *   new vertex and incrementing vertexCount; may trigger a full
     *   reallocation of the vertices and matrix arrays via grow().
     *
     * @param pVertex
     * The vertex to add to the graph. Vertices that are null, have a null
     * identifier, or whose identifier already exists in the graph are
     * silently ignored.
     */
    public void addVertex(Vertex pVertex) {

        // Validate that the vertex is non-null, has a non-null identifier,
        // and that no existing vertex already uses the same identifier.
        if (pVertex != null && pVertex.getID() != null && getVertex(pVertex.getID()) == null) {
            // If the array has reached full capacity, grow it before
            // attempting insertion.
            if (vertices.length == vertexCount) {
                grow();
            }

            // Insert the new vertex at the next available position.
            vertices[vertexCount] = pVertex;

            // Increment the count of active vertices to reflect the
            // insertion.
            vertexCount++;
        }
    }

    /**
     * Removes the specified vertex from the graph, clearing all matrix
     * entries associated with it and compacting the vertex array.
     *
     * Detailed explanation of:
     * - Purpose: Eliminates a vertex from the graph, removes all edges
     *   connected to it by clearing the corresponding matrix row and
     *   column, and maintains a dense, gap-free vertex array by relocating
     *   the last active vertex into the vacated slot.
     * - Business context: Supports dynamic graph maintenance, such as
     *   removing obsolete or invalid nodes from the model, while
     *   preserving the array-based storage invariant that active vertices
     *   occupy a contiguous range starting at index 0.
     * - Processing steps: Locates the index of the target vertex. If
     *   found, clears the vertex's entire matrix row and column
     *   (disconnecting all its edges). If the removed vertex is not the
     *   last active entry, moves the last active vertex (and its
     *   corresponding matrix row and column) into the now-vacant index to
     *   keep the array dense. Finally clears the last active slot and its
     *   matrix row/column, and decrements the active vertex count.
     * - Assumptions: Assumes vertex identity is determined by reference
     *   equality via indexOf. Assumes vertexCount accurately reflects the
     *   number of active vertices.
     * - Side effects: Mutates the internal vertices array and the
     *   adjacency matrix; may relocate the last active vertex to a
     *   different index, which changes the positional mapping used by
     *   indexOf for that vertex; decrements vertexCount.
     *
     * @param pVertex
     * The vertex to remove from the graph. If the vertex is not found
     * within the graph, no action is taken.
     */
    public void removeVertex(Vertex pVertex) {

        // Locate the array index of the vertex to be removed.
        int index = indexOf(pVertex);

        // Proceed only if the vertex was actually found in the graph.
        if (index != -1) {

            // Clear every matrix entry in the target vertex's row and
            // column, effectively disconnecting all edges connected to it.
            for (int j = 0; j < vertexCount; j++) {
                matrix[index][j] = null;
                matrix[j][index] = null;
            }

            // Determine the index of the last active vertex, used to keep
            // the vertex array dense after removal.
            int lastIndex = vertexCount - 1;

            // If the vertex being removed is not already the last active
            // entry, relocate the last active vertex into the vacated slot
            // to avoid leaving a gap in the active range.
            if (index != lastIndex) {
                vertices[index] = vertices[lastIndex];

                // Relocate the corresponding matrix row and column entries
                // for the moved vertex so its adjacency data follows it to
                // the new index.
                for (int j = 0; j < vertexCount; j++) {
                    matrix[index][j] = matrix[lastIndex][j];
                    matrix[j][index] = matrix[j][lastIndex];
                }
            }

            // Clear the now-unused last slot in the vertex array.
            vertices[lastIndex] = null;

            // Clear the matrix row and column corresponding to the
            // now-unused last slot to avoid stale references.
            for (int j = 0; j < vertexCount; j++) {
                matrix[lastIndex][j] = null;
                matrix[j][lastIndex] = null;
            }

            // Decrement the active vertex count to reflect the removal.
            vertexCount--;
        }
    }

    /**
     * Retrieves all edges currently contained within the graph.
     *
     * Detailed explanation of:
     * - Purpose: Provides access to the complete set of unique edges by
     *   scanning the upper triangle of the adjacency matrix, avoiding
     *   duplicate reporting of each undirected edge.
     * - Business context: Serves as the entry point for algorithms that
     *   need to process every edge in the graph, such as minimum spanning
     *   tree construction or full-graph cost analysis.
     * - Processing steps: Iterates over all vertex index pairs (i, j)
     *   where j is strictly greater than i, scanning only the upper
     *   triangle of the matrix since matrix[i][j] and matrix[j][i]
     *   reference the same edge in an undirected graph. Appends each
     *   non-null matrix entry encountered to the result list.
     * - Assumptions: Assumes the matrix is maintained symmetrically, such
     *   that matrix[i][j] and matrix[j][i] always reference the same Edge
     *   instance or are both null.
     * - Side effects: None; this method does not modify internal state.
     *
     * @return
     * A list containing all edges currently present in the graph. Returns
     * an empty list when the graph contains no edges. Never returns null.
     */
    public SinglyLinkedList<Edge> getEdges() {
        // Allocate a new list to accumulate discovered edges.
        SinglyLinkedList<Edge> result = new SinglyLinkedList<>();

        // Scan only the upper triangle of the matrix (j > i) to report
        // each undirected edge exactly once.
        for (int i = 0; i < vertexCount; i++) {
            for (int j = i + 1; j < vertexCount; j++) {
                if (matrix[i][j] != null) {
                    result.append(matrix[i][j]);
                }
            }
        }

        // Return the accumulated list of edges.
        return result;
    }

    /**
     * Retrieves all edges connected to the specified vertex.
     *
     * Detailed explanation of:
     * - Purpose: Provides the subset of edges that have the specified
     *   vertex as one of their endpoints, by scanning the vertex's row in
     *   the adjacency matrix.
     * - Business context: Used by traversal and neighbor-discovery
     *   algorithms that need to evaluate all connections originating from
     *   or arriving at a particular vertex.
     * - Processing steps: Locates the index of the target vertex; if not
     *   found, returns an empty list immediately. Otherwise, scans the
     *   corresponding matrix row across all active vertex columns,
     *   appending each non-null entry to the result list.
     * - Assumptions: Assumes vertex identity is determined by reference
     *   equality via indexOf.
     * - Side effects: None; this method does not modify internal state.
     *
     * @param pVertex
     * The vertex whose connected edges are to be retrieved.
     *
     * @return
     * A list containing all edges connected to the specified vertex.
     * Returns an empty list when the vertex has no connected edges or is
     * not present in the graph. Never returns null.
     */
    public SinglyLinkedList<Edge> getEdges(Vertex pVertex) {
        // Allocate a new list to accumulate matching edges.
        SinglyLinkedList<Edge> result = new SinglyLinkedList<>();

        // Locate the array index of the target vertex.
        int i = indexOf(pVertex);

        // If the vertex is not present in the graph, no edges can be
        // associated with it; return the empty result immediately.
        if (i == -1) {
            return result;
        }

        // Scan the target vertex's matrix row across all active columns,
        // collecting every non-null edge reference.
        for (int j = 0; j < vertexCount; j++) {
            if (matrix[i][j] != null) {
                result.append(matrix[i][j]);
            }
        }

        // Return the accumulated list of connected edges.
        return result;
    }

    /**
     * Retrieves the edge connecting the two specified vertices, if one
     * exists.
     *
     * Detailed explanation of:
     * - Purpose: Provides constant-time lookup of a specific connection
     *   between two known vertices by directly indexing into the
     *   adjacency matrix.
     * - Business context: Used by algorithms and client code that need to
     *   determine whether, and how, two vertices are connected, such as
     *   when validating adjacency or retrieving connection weight; also
     *   used internally to detect duplicate edges before insertion.
     * - Processing steps: Locates the array indices of both supplied
     *   vertices; if either is not found, returns null immediately.
     *   Otherwise, directly returns the matrix entry at the resulting row
     *   and column coordinates.
     * - Assumptions: Assumes vertex identity is determined by reference
     *   equality via indexOf, and that the matrix is maintained
     *   symmetrically for an undirected graph.
     * - Side effects: None; this method does not modify internal state.
     *
     * @param pVertex
     * The first endpoint vertex of the edge to retrieve.
     * @param pAnotherVertex
     * The second endpoint vertex of the edge to retrieve.
     *
     * @return
     * The edge connecting the two specified vertices, or null if either
     * vertex is not present in the graph or no such edge exists.
     */
    public Edge getEdge(Vertex pVertex, Vertex pAnotherVertex) {
        // Locate the array index of the first vertex.
        int i = indexOf(pVertex);

        // Locate the array index of the second vertex.
        int j = indexOf(pAnotherVertex);

        // If either vertex is not present in the graph, no valid matrix
        // coordinate exists; return null immediately.
        if (i == -1 || j == -1) {
            return null;
        }

        // Directly return the matrix entry at the resolved coordinates,
        // which is either the connecting edge or null if unconnected.
        return matrix[i][j];
    }

    /**
     * Adds the specified edge to the graph after validating its endpoints
     * and uniqueness.
     *
     * Detailed explanation of:
     * - Purpose: Expands the graph's edge set to include a new connection
     *   between two vertices, while enforcing structural integrity
     *   constraints on the endpoints via direct matrix indexing.
     * - Business context: Supports dynamic graph construction, allowing
     *   relationships between vertices to be introduced incrementally
     *   while preventing self-loops, references to non-member vertices,
     *   and duplicate connections.
     * - Processing steps: Returns immediately if the supplied edge is
     *   null. Extracts both endpoint vertices and resolves their array
     *   indices. Returns without action if either endpoint is not found
     *   in the graph, or if both endpoints resolve to the same index
     *   (self-loop). Returns without action if a matrix entry already
     *   exists at the resolved coordinates (duplicate edge). Otherwise,
     *   stores the edge symmetrically at both [i][j] and [j][i] to
     *   reflect the undirected nature of the connection.
     * - Assumptions: Assumes the graph is undirected and does not permit
     *   self-loops (an edge connecting a vertex to itself) or parallel
     *   edges (multiple edges connecting the same vertex pair).
     * - Side effects: Mutates two entries of the internal adjacency
     *   matrix when validation succeeds.
     *
     * @param pEdge
     * The edge to add to the graph. Edges failing any of the validation
     * checks (null edge, endpoints not present in the graph, identical
     * endpoints, or a pre-existing connection between the same vertices)
     * are silently ignored.
     */
    public void addEdge(Edge pEdge) {
        // Guard against a null edge reference.
        if (pEdge == null) {
            return;
        }

        // Extract the two endpoint vertices declared by the edge.
        Vertex v1 = pEdge.getVertices()[0];
        Vertex v2 = pEdge.getVertices()[1];

        // Resolve the array index of the first endpoint.
        int i = indexOf(v1);

        // Resolve the array index of the second endpoint.
        int j = indexOf(v2);

        // Reject the edge if either endpoint is not a member of this
        // graph, or if both endpoints resolve to the same vertex
        // (self-loop), since self-loops are not permitted.
        if (i == -1 || j == -1 || i == j) {
            return;
        }

        // Reject the edge if a connection already exists between these
        // two vertices, preventing duplicate/parallel edges.
        if (matrix[i][j] != null) {
            return;
        }

        // Store the edge in both symmetric matrix positions to reflect
        // the undirected connection between the two vertices.
        matrix[i][j] = pEdge;
        matrix[j][i] = pEdge;
    }

    /**
     * Removes the specified edge from the graph.
     *
     * Detailed explanation of:
     * - Purpose: Eliminates a connection between two vertices from the
     *   graph structure without removing the vertices themselves, by
     *   clearing the corresponding matrix entries.
     * - Business context: Supports dynamic graph maintenance, such as
     *   removing obsolete or invalid relationships from the model.
     * - Processing steps: Returns immediately if the supplied edge is
     *   null. Extracts both endpoint vertices and resolves their array
     *   indices. Returns without action if either endpoint is not found
     *   in the graph. Otherwise, clears the matrix entries at both
     *   symmetric coordinates.
     * - Assumptions: Assumes the endpoints stored within the supplied edge
     *   accurately reflect the connection to be removed; does not verify
     *   that matrix[i][j] currently equals the supplied edge reference
     *   before clearing it.
     * - Side effects: Mutates two entries of the internal adjacency
     *   matrix when both endpoints are found.
     *
     * @param pEdge
     * The edge to remove from the graph. If null, or if either endpoint
     * vertex is not present in the graph, the method performs no action.
     */
    public void removeEdge(Edge pEdge) {
        // Guard against a null edge reference.
        if (pEdge == null) {
            return;
        }

        // Extract the two endpoint vertices declared by the edge.
        Vertex v1 = pEdge.getVertices()[0];
        Vertex v2 = pEdge.getVertices()[1];

        // Resolve the array index of the first endpoint.
        int i = indexOf(v1);

        // Resolve the array index of the second endpoint.
        int j = indexOf(v2);

        // If either endpoint is not a member of this graph, there is no
        // valid matrix coordinate to clear.
        if (i == -1 || j == -1) {
            return;
        }

        // Clear both symmetric matrix positions, removing the connection
        // between the two vertices.
        matrix[i][j] = null;
        matrix[j][i] = null;
    }

    /**
     * Sets the mark state on all vertices within the graph.
     *
     * Detailed explanation of:
     * - Purpose: Provides a bulk operation to reset or set the visitation
     *   state of every active vertex, typically used prior to initiating a
     *   new traversal.
     * - Business context: Ensures algorithms operating on marks (e.g.,
     *   depth-first or breadth-first traversal) begin from a known,
     *   consistent state across all vertices.
     * - Processing steps: Iterates over the active portion of the
     *   vertices array (indices [0, vertexCount)), applying the specified
     *   mark value to each non-null vertex encountered.
     * - Assumptions: Assumes vertexCount accurately reflects the number of
     *   active, non-null vertex entries.
     * - Side effects: Mutates the mark state of every active vertex in the
     *   graph.
     *
     * @param pMark
     * The mark value to apply to all vertices. True indicates marked;
     * false indicates unmarked.
     */
    public void setAllVertexMarks(boolean pMark) {
        // Iterate only over the active portion of the vertices array.
        for (int i = 0; i < vertexCount; i++) {
            // Defensive null check before invoking setMark, in case of an
            // inconsistent internal state.
            if (vertices[i] != null) {
                vertices[i].setMark(pMark);
            }
        }
    }

    /**
     * Sets the mark state on all edges within the graph.
     *
     * Detailed explanation of:
     * - Purpose: Provides a bulk operation to reset or set the processing
     *   state of every unique edge, typically used prior to initiating
     *   algorithms such as minimum spanning tree construction.
     * - Business context: Ensures algorithms operating on marks (e.g.,
     *   spanning tree or cycle detection algorithms) begin from a known,
     *   consistent state across all edges.
     * - Processing steps: Iterates over all vertex index pairs (i, j)
     *   where j is strictly greater than i, scanning only the upper
     *   triangle of the matrix to avoid marking each undirected edge
     *   twice, and applies the specified mark value to each non-null
     *   entry encountered.
     * - Assumptions: Assumes the matrix is maintained symmetrically, such
     *   that marking the upper-triangle entry is sufficient to represent
     *   the edge's overall mark state (since matrix[i][j] and matrix[j][i]
     *   reference the same Edge instance).
     * - Side effects: Mutates the mark state of every edge in the graph.
     *
     * @param pMark
     * The mark value to apply to all edges. True indicates marked; false
     * indicates unmarked.
     */
    public void setAllEdgeMarks(boolean pMark) {
        // Scan only the upper triangle of the matrix (j > i) to mark each
        // undirected edge exactly once.
        for (int i = 0; i < vertexCount; i++) {
            for (int j = i + 1; j < vertexCount; j++) {
                if (matrix[i][j] != null) {
                    matrix[i][j].setMark(pMark);
                }
            }
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
     * - Processing steps: Assumes a true result by default, then iterates
     *   over the active portion of the vertices array, setting the result
     *   to false and terminating the scan early (via break) as soon as an
     *   unmarked vertex is encountered.
     * - Assumptions: Assumes vertexCount accurately reflects the number of
     *   active, non-null vertex entries. An empty graph (vertexCount of
     *   zero) yields true, since the result defaults to true and the loop
     *   body never executes.
     * - Side effects: None; this method does not modify internal state.
     *
     * @return
     * True if every vertex in the graph is currently marked, or if the
     * graph contains no vertices; false if at least one vertex is
     * unmarked.
     */
    public boolean allVerticesMarked() {
        // Assume all vertices are marked until proven otherwise by the
        // scan below.
        boolean result = true;

        // Iterate only over the active portion of the vertices array.
        for (int i = 0; i < vertexCount; i++) {
            if (!vertices[i].isMarked()) {
                // An unmarked vertex was found; the overall result becomes
                // false and further scanning is unnecessary.
                result = false;
                break;
            }
        }

        // Return the final determination.
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
     * - Processing steps: Assumes a true result by default, then iterates
     *   over the upper triangle of the matrix (j > i) to avoid evaluating
     *   each undirected edge twice, setting the result to false as soon as
     *   an unmarked edge is encountered. Note that the inner break only
     *   terminates the innermost loop; the outer loop continues to iterate
     *   over remaining vertex rows even after a negative result has been
     *   recorded.
     * - Assumptions: Assumes the matrix is maintained symmetrically, such
     *   that evaluating only the upper-triangle entry is sufficient to
     *   represent each edge's mark state. A graph with no edges yields
     *   true, since the result defaults to true and no matrix entry is
     *   ever found unmarked.
     * - Side effects: None; this method does not modify internal state.
     *
     * @return
     * True if every edge in the graph is currently marked, or if the graph
     * contains no edges; false if at least one edge is unmarked.
     */
    public boolean allEdgesMarked() {
        // Assume all edges are marked until proven otherwise by the scan
        // below.
        boolean result = true;

        // Scan only the upper triangle of the matrix (j > i) to evaluate
        // each undirected edge exactly once.
        for (int i = 0; i < vertexCount; i++) {
            for (int j = i + 1; j < vertexCount; j++) {
                if (matrix[i][j] != null && !matrix[i][j].isMarked()) {
                    // An unmarked edge was found; the overall result
                    // becomes false and the innermost scan is terminated
                    // early.
                    result = false;
                    break;
                }
            }
        }

        // Return the final determination.
        return result;
    }

    /**
     * Retrieves all vertices directly adjacent to the specified vertex.
     *
     * Detailed explanation of:
     * - Purpose: Identifies the set of vertices reachable from the
     *   specified vertex via a single edge, by scanning the vertex's row
     *   in the adjacency matrix.
     * - Business context: Used by graph traversal algorithms (e.g.,
     *   breadth-first search, depth-first search) to determine which
     *   vertices to visit next from a given position in the graph.
     * - Processing steps: Locates the index of the target vertex; if not
     *   found, returns an empty list immediately. Otherwise, scans the
     *   corresponding matrix row across all active vertex columns,
     *   appending the vertex at each column index where a non-null edge
     *   entry is found.
     * - Assumptions: Assumes vertex identity is determined by reference
     *   equality via indexOf, and that the matrix is maintained
     *   symmetrically for an undirected graph.
     * - Side effects: None; this method does not modify internal state.
     *
     * @param pVertex
     * The vertex whose neighbors are to be retrieved.
     *
     * @return
     * A list containing all vertices directly connected to the specified
     * vertex via an edge. Returns an empty list when the vertex has no
     * neighbors or is not present in the graph. Never returns null.
     */
    public SinglyLinkedList<Vertex> getNeighbours(Vertex pVertex) {
        // Allocate a new list to accumulate discovered neighboring
        // vertices.
        SinglyLinkedList<Vertex> result = new SinglyLinkedList<>();

        // Locate the array index of the target vertex.
        int i = indexOf(pVertex);

        // If the vertex is not present in the graph, it has no neighbors
        // to report; return the empty result immediately.
        if (i == -1) {
            return result;
        }

        // Scan the target vertex's matrix row across all active columns,
        // appending the vertex at each column where a connecting edge
        // exists.
        for (int j = 0; j < vertexCount; j++) {
            if (matrix[i][j] != null) {
                result.append(vertices[j]);
            }
        }

        // Return the accumulated list of neighboring vertices.
        return result;
    }

    /**
     * Determines whether the graph currently contains no vertices.
     *
     * Detailed explanation of:
     * - Purpose: Provides a convenient way to check whether the graph has
     *   any content without requiring the caller to inspect the vertex
     *   array directly.
     * - Business context: Used by algorithms and client code to guard
     *   against operating on an uninitialized or fully cleared graph.
     * - Processing steps: Directly compares the active vertex count
     *   against zero.
     * - Assumptions: Assumes vertexCount accurately reflects the number of
     *   active vertices at all times.
     * - Side effects: None; this method does not modify internal state.
     *
     * @return
     * True if the graph contains no vertices; false otherwise.
     */
    public boolean isEmpty() {
        return vertexCount == 0;
    }
}