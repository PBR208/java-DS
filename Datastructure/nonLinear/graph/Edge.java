package nonLinear.graph;

/**
 * Purpose:
 * Represents a connection (edge) between exactly two vertices within a graph
 * data structure, forming a core building block for graph-based algorithms
 * such as shortest-path computation, minimum spanning tree construction, and
 * network traversal. This class encapsulates the structural relationship
 * between two nodes, an associated numeric weight used for cost-based
 * calculations, and a marking flag used by traversal or algorithmic
 * processes to track visitation or inclusion state. It serves as a
 * fundamental unit within the broader non-linear graph package, enabling
 * higher-level graph structures to model weighted, undirected relationships
 * between vertices.
 *
 * Owner:
 * P.B.R. - https://github.com/PBR208/
 *
 * Version:
 * 1.0
 */

/**
 * Represents a weighted, undirected edge connecting two vertices in a graph.
 *
 * Responsibility: Encapsulates the pair of endpoint vertices, the numeric
 * weight associated with the connection, and a boolean marking flag used
 * by graph algorithms (e.g., minimum spanning tree or traversal algorithms)
 * to track processing state.
 *
 * Scope: Used exclusively within the nonLinear.graph package as a structural
 * component of larger graph implementations.
 *
 * Dependencies: Depends on the Vertex class to represent edge endpoints.
 *
 * Thread-safety: This class is not thread-safe. Concurrent modification of
 * the weight or mark fields from multiple threads without external
 * synchronization may result in inconsistent state.
 *
 * Lifecycle: An Edge instance is immutable with respect to its endpoint
 * vertices once constructed; only the weight and mark attributes may be
 * modified after instantiation.
 *
 * Architectural role: Acts as a foundational data unit consumed by graph
 * traversal, pathfinding, and spanning tree algorithms elsewhere in the
 * system.
 */
public class Edge {

    // Stores the two endpoint vertices that this edge connects.
    // Declared final because the identity of the connected vertices must
    // remain fixed for the lifetime of the edge; only weight and mark
    // are mutable after construction.
    private final Vertex[] vertices;

    // Stores the numeric cost or distance associated with traversing this
    // edge. Used by weighted graph algorithms such as shortest-path or
    // minimum spanning tree computations.
    private double weight;

    // Stores the processing/visitation state of this edge. Used by graph
    // algorithms to track whether the edge has already been considered,
    // included, or visited during traversal or construction of derived
    // structures (e.g., spanning trees).
    private boolean mark;

    /**
     * Constructs a new Edge connecting two specified vertices with a given
     * weight.
     *
     * Detailed explanation of:
     * - Purpose: Initializes the structural relationship between two
     *   vertices and assigns the initial cost of traversing the connection.
     * - Business context: Represents a single weighted connection within a
     *   graph model, forming the basis for algorithmic graph analysis.
     * - Processing steps: Allocates a fixed-size array to hold both
     *   endpoint vertices, assigns the provided weight, and initializes the
     *   mark flag to an unmarked state.
     * - Assumptions: Assumes both vertex references represent valid,
     *   distinct endpoints of the graph; no null-check is performed at
     *   construction time.
     * - Side effects: Allocates a new internal array to store the endpoint
     *   vertices.
     *
     * @param pVertex
     * The first endpoint vertex of this edge. Represents one side of the
     * connection.
     * @param pAnotherVertex
     * The second endpoint vertex of this edge. Represents the opposite side
     * of the connection.
     * @param pWeight
     * The initial numeric cost or distance associated with this edge. Used
     * by weighted graph algorithms for cost-based calculations.
     */
    public Edge(Vertex pVertex, Vertex pAnotherVertex, double pWeight) {
        // Allocate a fixed-size array to hold exactly two endpoint vertices,
        // reflecting the fact that an edge always connects precisely two
        // nodes.
        this.vertices = new Vertex[2];

        // Store the first endpoint vertex at index 0.
        this.vertices[0] = pVertex;

        // Store the second endpoint vertex at index 1.
        this.vertices[1] = pAnotherVertex;

        // Assign the initial weight representing the cost of this
        // connection.
        this.weight = pWeight;

        // Initialize the mark flag to false, indicating this edge has not
        // yet been processed or visited by any graph algorithm.
        this.mark = false;
    }

    /**
     * Retrieves a defensive copy of the two vertices connected by this edge.
     *
     * Detailed explanation of:
     * - Purpose: Provides external access to the edge's endpoint vertices
     *   without exposing the internal array reference directly.
     * - Business context: Allows graph algorithms to inspect the endpoints
     *   of an edge for traversal, comparison, or pathfinding purposes.
     * - Processing steps: Allocates a new array and copies the two vertex
     *   references from the internal array into it before returning.
     * - Assumptions: Assumes the internal vertices array always contains
     *   exactly two elements, as guaranteed by the constructor.
     * - Side effects: Allocates a new array on each invocation to preserve
     *   encapsulation; does not modify internal state.
     *
     * @return
     * A new two-element array containing references to the same vertex
     * instances stored internally. Modifying the returned array does not
     * affect the internal state of this edge. Never returns null.
     */
    public Vertex[] getVertices() {
        // Allocate a new array to avoid exposing a direct reference to the
        // internal vertices array, preserving encapsulation and preventing
        // external modification of internal state.
        Vertex[] copyOf = new Vertex[2];

        // Copy the first endpoint vertex reference into the new array.
        copyOf[0] = vertices[0];

        // Copy the second endpoint vertex reference into the new array.
        copyOf[1] = vertices[1];

        // Return the defensive copy to the caller.
        return copyOf;
    }


    /**
     * Updates the weight associated with this edge.
     *
     * Detailed explanation of:
     * - Purpose: Allows the cost or distance value of the edge to be
     *   modified after construction, supporting dynamic graph updates.
     * - Business context: Enables algorithms or external processes to
     *   adjust connection costs, for example when recalculating distances
     *   or applying updated cost models.
     * - Processing steps: Directly assigns the provided value to the
     *   internal weight field.
     * - Assumptions: Assumes the caller supplies a semantically valid
     *   weight value appropriate for the graph's cost model; no validation
     *   is performed within this method.
     * - Side effects: Mutates the internal state of this edge instance.
     *
     * @param pWeight
     * The new numeric cost or distance to associate with this edge.
     */
    public void setWeight(double pWeight) {
        // Overwrite the current weight with the newly supplied value.
        this.weight = pWeight;

    }

    /**
     * Retrieves the current weight associated with this edge.
     *
     * Detailed explanation of:
     * - Purpose: Exposes the numeric cost or distance value of the edge for
     *   use by graph algorithms.
     * - Business context: Used by weighted graph algorithms (e.g.,
     *   shortest-path or minimum spanning tree computations) to evaluate
     *   the cost of traversing this connection.
     * - Processing steps: Directly returns the internal weight field.
     * - Assumptions: Assumes the weight field has been properly initialized
     *   via the constructor or updated via setWeight.
     * - Side effects: None; this method does not modify internal state.
     *
     * @return
     * The current numeric cost or distance associated with this edge.
     */
    public double getWeight() {
        return weight;
    }

    /**
     * Sets the processing/visitation mark on this edge.
     *
     * Detailed explanation of:
     * - Purpose: Allows graph algorithms to flag this edge as processed,
     *   visited, or included during traversal or construction of derived
     *   structures such as spanning trees.
     * - Business context: Supports algorithmic bookkeeping where edges must
     *   be tracked to avoid redundant processing or to record inclusion in
     *   a result set.
     * - Processing steps: Directly assigns the provided boolean value to
     *   the internal mark field.
     * - Assumptions: Assumes the calling algorithm manages mark state
     *   consistently according to its own traversal logic.
     * - Side effects: Mutates the internal state of this edge instance.
     *
     * @param pMark
     * The new mark state to assign to this edge. True indicates the edge
     * has been processed, visited, or included; false indicates it has not.
     */
    public void setMark(boolean pMark) {
        // Overwrite the current mark state with the newly supplied value.
        mark = pMark;
    }

    /**
     * Indicates whether this edge has been marked by a graph algorithm.
     *
     * Detailed explanation of:
     * - Purpose: Exposes the current mark state for inspection by graph
     *   algorithms performing traversal or construction of derived
     *   structures.
     * - Business context: Used to determine whether this edge has already
     *   been processed, visited, or included in a result set, preventing
     *   redundant work.
     * - Processing steps: Directly returns the internal mark field.
     * - Assumptions: Assumes the mark field has been properly initialized
     *   via the constructor or updated via setMark.
     * - Side effects: None; this method does not modify internal state.
     *
     * @return
     * True if this edge has been marked as processed, visited, or included;
     * false otherwise.
     */
    public boolean isMarked() {
        return mark;
    }
}